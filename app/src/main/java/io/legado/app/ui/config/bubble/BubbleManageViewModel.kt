package io.legado.app.ui.config.bubble

import android.app.Application
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.ImageProvider
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.postEvent
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import splitties.init.appCtx
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 气泡管理的数据层。
 *
 * 气泡包同样是目录结构，配置里保存 SVG 模板与四组颜色（日/夜 × 常规/强调）。
 * 保存前会实际渲染一次 SVG 做校验，避免写入无法解析的模板导致阅读页气泡消失。
 */
class BubbleManageViewModel(application: Application) : BaseViewModel(application) {

    private val _items = MutableStateFlow<List<BubblePackageManager.Entry>>(emptyList())
    val items: StateFlow<List<BubblePackageManager.Entry>> = _items.asStateFlow()

    private val _activeDirName = MutableStateFlow(BubblePackageManager.BUILTIN_DIR_NAME)
    val activeDirName: StateFlow<String> = _activeDirName.asStateFlow()

    private val _draft = MutableStateFlow<BubblePackageManager.Config?>(null)
    val draft: StateFlow<BubblePackageManager.Config?> = _draft.asStateFlow()

    private val _urlHistory = MutableStateFlow<List<String>>(emptyList())
    val urlHistory: StateFlow<List<String>> = _urlHistory.asStateFlow()

    private val _events = Channel<BubbleEvent>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    private var editingEntry: BubblePackageManager.Entry? = null

    var isEditingExisting = false
        private set

    fun load() {
        execute {
            _items.value = BubblePackageManager.loadEntries()
            _activeDirName.value = BubblePackageManager.activeDirName()
            _urlHistory.value = loadCachedUrls()
        }
    }

    fun startCreate() {
        _draft.value = BubblePackageManager.builtinConfig().copy(
            name = appCtx.getString(R.string.bubble_custom_name),
            dirName = "",
            updatedAt = System.currentTimeMillis()
        )
        editingEntry = null
        isEditingExisting = false
    }

    fun startEdit(entry: BubblePackageManager.Entry) {
        if (entry.source == BubblePackageManager.Source.BUILTIN) return
        _draft.value = entry.config.copy(dirName = entry.dirName)
        editingEntry = entry
        isEditingExisting = true
    }

    fun clearDraft() {
        _draft.value = null
        editingEntry = null
    }

    fun updateDraft(transform: (BubblePackageManager.Config) -> BubblePackageManager.Config) {
        _draft.value = _draft.value?.let(transform)
    }

    fun saveDraft() {
        val config = _draft.value ?: return
        val name = config.name.trim()
        if (name.isBlank()) {
            _events.trySend(BubbleEvent.Toast(R.string.input_is_empty))
            return
        }
        val oldEntry = editingEntry
        execute {
            runCatching {
                requireValidSvg(config)
                BubblePackageManager.addOrUpdate(
                    config.copy(name = name, dirName = oldEntry?.dirName.orEmpty()),
                    oldEntry
                )
            }.onSuccess { entry ->
                if (oldEntry?.dirName == BubblePackageManager.activeDirName()) {
                    notifyBubbleChanged()
                }
                _draft.value = null
                editingEntry = null
                reload()
            }.onFailure {
                _events.trySend(BubbleEvent.ToastMsg(it.localizedMessage.orEmpty()))
            }
        }
    }

    fun applyEntry(entry: BubblePackageManager.Entry) {
        if (entry.dirName == _activeDirName.value) {
            _events.trySend(BubbleEvent.Toast(R.string.success))
            return
        }
        execute {
            BubblePackageManager.apply(entry)
            notifyBubbleChanged()
            reload()
            _events.trySend(BubbleEvent.Toast(R.string.success))
        }
    }

    fun deleteEntries(entries: List<BubblePackageManager.Entry>) {
        val removable = entries.filter {
            it.source != BubblePackageManager.Source.BUILTIN && it.dirName != _activeDirName.value
        }
        if (removable.isEmpty()) return
        execute {
            removable.forEach { BubblePackageManager.deleteLocal(it) }
            notifyBubbleChanged()
            reload()
        }
    }

    fun toTopEntries(entries: List<BubblePackageManager.Entry>) {
        val movable = entries.filter { it.source != BubblePackageManager.Source.BUILTIN }
        if (movable.isEmpty()) {
            _events.trySend(BubbleEvent.Toast(R.string.bubble_select_at_least_one))
            return
        }
        execute {
            BubblePackageManager.toTop(movable)
            reload()
        }
    }

    fun exportZip(entries: List<BubblePackageManager.Entry>) {
        if (entries.isEmpty()) {
            _events.trySend(BubbleEvent.Toast(R.string.bubble_select_at_least_one))
            return
        }
        execute {
            runCatching { BubblePackageManager.exportZip(entries) }
                .onSuccess { _events.trySend(BubbleEvent.ExportZip(it)) }
                .onFailure { _events.trySend(BubbleEvent.ToastMsg(it.localizedMessage.orEmpty())) }
        }
    }

    fun shareJson(entry: BubblePackageManager.Entry) {
        _events.trySend(BubbleEvent.ShareJson(GSON.toJson(entry.config)))
    }

    fun importZip(file: File) {
        execute {
            runCatching { BubblePackageManager.importZip(file) }
                .onSuccess { entries ->
                    reload()
                    _events.trySend(
                        if (entries.size > 1) {
                            BubbleEvent.ToastMsg(
                                appCtx.getString(R.string.bubble_import_count_success, entries.size)
                            )
                        } else {
                            BubbleEvent.Toast(R.string.import_success)
                        }
                    )
                }
                .onFailure { _events.trySend(BubbleEvent.ToastMsg(it.localizedMessage.orEmpty())) }
        }
    }

    fun importFromUrl(url: String) {
        execute {
            runCatching {
                val bytes = okHttpClient.newCallResponseBody { url(url) }.bytes()
                val file = appCtx.externalFiles
                    .getFile("bubbleImports", "import_${System.currentTimeMillis()}.zip")
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { it.write(bytes) }
                BubblePackageManager.importZip(file)
            }.onSuccess { entries ->
                rememberUrl(url)
                reload()
                _events.trySend(
                    if (entries.size > 1) {
                        BubbleEvent.ToastMsg(
                            appCtx.getString(R.string.bubble_import_count_success, entries.size)
                        )
                    } else {
                        BubbleEvent.Toast(R.string.import_success)
                    }
                )
            }.onFailure { _events.trySend(BubbleEvent.ToastMsg(it.localizedMessage.orEmpty())) }
        }
    }

    private fun reload() {
        _items.value = BubblePackageManager.loadEntries()
        _activeDirName.value = BubblePackageManager.activeDirName()
    }

    private fun notifyBubbleChanged() {
        ImageProvider.clear()
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }

    private fun requireValidSvg(config: BubblePackageManager.Config) {
        val color = config.dayNormalColor?.takeIf { it.isNotBlank() }
            ?: BubblePackageManager.DEFAULT_NORMAL_COLOR
        color.toColorInt()
        val svg = config.svgTemplate
            .replace("\${color}", color)
            .replace("\${num}", "1")
        require(SvgUtils.createBitmap(ByteArrayInputStream(svg.toByteArray()), 128, 128) != null) {
            appCtx.getString(R.string.error_decode_bitmap)
        }
    }

    private fun cacheKey() = "bubbleImportUrls"

    private fun loadCachedUrls(): List<String> =
        ACache.get(cacheDir = false).getAsString(cacheKey())?.splitNotBlank(",")?.toList()
            ?: emptyList()

    private fun rememberUrl(url: String) {
        val next = (listOf(url) + loadCachedUrls().filterNot { it == url }).take(MAX_CACHED_URLS)
        ACache.get(cacheDir = false).put(cacheKey(), next.joinToString(","))
        _urlHistory.value = next
    }

    companion object {
        private const val MAX_CACHED_URLS = 10
    }
}

/** 气泡管理的一次性事件。 */
sealed interface BubbleEvent {
    data class Toast(val resId: Int) : BubbleEvent
    data class ToastMsg(val msg: String) : BubbleEvent
    data class ShareJson(val json: String) : BubbleEvent
    data class ExportZip(val file: File) : BubbleEvent
}
