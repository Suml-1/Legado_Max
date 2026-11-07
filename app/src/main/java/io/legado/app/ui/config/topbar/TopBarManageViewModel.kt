package io.legado.app.ui.config.topbar

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.help.config.TopBarConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import io.legado.app.utils.postEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import splitties.init.appCtx
import java.io.File

/**
 * 顶栏管理的数据层。
 *
 * 顶栏配置是「目录包」结构（每个包一个目录，内含 `top_bar.json` 与可选的壁纸），
 * 因此新增/编辑走 [TopBarConfig.addOrUpdate]、删除走 [TopBarConfig.deleteLocal]。
 * 置顶通过刷新包内 `updatedAt` 实现（列表按该字段倒序）。
 */
class TopBarManageViewModel(application: Application) : BaseViewModel(application) {

    private val _items = MutableStateFlow<List<TopBarConfig.Entry>>(emptyList())
    val items: StateFlow<List<TopBarConfig.Entry>> = _items.asStateFlow()

    private val _activeDirName = MutableStateFlow<String?>(null)
    val activeDirName: StateFlow<String?> = _activeDirName.asStateFlow()

    private val _draft = MutableStateFlow<TopBarConfig.Config?>(null)
    val draft: StateFlow<TopBarConfig.Config?> = _draft.asStateFlow()

    private val _events = Channel<TopBarEvent>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    private var isNight = false
    private var editingEntry: TopBarConfig.Entry? = null

    /** 当前草稿是否对应已有配置（决定编辑弹窗标题） */
    var isEditingExisting = false
        private set

    fun load(isNight: Boolean) {
        this.isNight = isNight
        execute {
            runCatching { TopBarConfig.loadEntries(appCtx, isNight) }
                .onSuccess {
                    _items.value = it
                    _activeDirName.value = TopBarConfig.activeDirName(isNight)
                }
                .onFailure { _events.trySend(TopBarEvent.ToastMsg(it.localizedMessage.orEmpty())) }
        }
    }

    fun startCreate() {
        val usedNames = _items.value.map { it.config.name }.toSet()
        _draft.value = TopBarConfig.defaultConfig(appCtx, isNight).copy(name = nextPackageName(usedNames))
        editingEntry = null
        isEditingExisting = false
    }

    fun startEdit(entry: TopBarConfig.Entry) {
        if (entry.dirName == TopBarConfig.DEFAULT_DIR_NAME) {
            _events.trySend(TopBarEvent.Toast(R.string.navigation_bar_default_readonly))
            return
        }
        _draft.value = entry.config.copy()
        editingEntry = entry
        isEditingExisting = true
    }

    fun clearDraft() {
        _draft.value = null
        editingEntry = null
    }

    fun updateDraft(transform: (TopBarConfig.Config) -> TopBarConfig.Config) {
        _draft.value = _draft.value?.let(transform)
    }

    fun saveDraft() {
        val config = _draft.value ?: return
        val name = config.name.trim()
        if (name.isBlank()) {
            _events.trySend(TopBarEvent.Toast(R.string.input_is_empty))
            return
        }
        val oldEntry = editingEntry
        execute {
            runCatching {
                TopBarConfig.addOrUpdate(config.copy(name = name), oldEntry)
            }.onSuccess { entry ->
                if (oldEntry?.dirName == TopBarConfig.activeDirName(entry.config.isNightMode)) {
                    TopBarConfig.apply(entry)
                    postEvent(EventBus.TOP_BAR_CHANGED, entry.config.isNightMode)
                }
                _draft.value = null
                editingEntry = null
                reload()
                _events.trySend(TopBarEvent.Toast(R.string.success))
            }.onFailure {
                _events.trySend(TopBarEvent.ToastMsg(it.localizedMessage.orEmpty()))
            }
        }
    }

    fun applyPackage(entry: TopBarConfig.Entry) {
        if (entry.dirName == _activeDirName.value) {
            _events.trySend(TopBarEvent.Toast(R.string.applied))
            return
        }
        execute {
            TopBarConfig.apply(entry)
            postEvent(EventBus.TOP_BAR_CHANGED, entry.config.isNightMode)
            _activeDirName.value = entry.dirName
            _events.trySend(
                TopBarEvent.ToastMsg(appCtx.getString(R.string.applied_top_bar_config, entry.config.name))
            )
        }
    }

    fun deletePackage(entry: TopBarConfig.Entry) {
        if (entry.dirName == TopBarConfig.DEFAULT_DIR_NAME) return
        execute {
            TopBarConfig.deleteLocal(entry)
            postEvent(EventBus.TOP_BAR_CHANGED, entry.config.isNightMode)
            reload()
        }
    }

    fun toTop(entry: TopBarConfig.Entry) {
        if (entry.dirName == TopBarConfig.DEFAULT_DIR_NAME) {
            _events.trySend(TopBarEvent.Toast(R.string.navigation_bar_default_readonly))
            return
        }
        execute {
            TopBarConfig.toTop(entry)
            reload()
            _events.trySend(TopBarEvent.Toast(R.string.success))
        }
    }

    fun toTopEntries(entries: List<TopBarConfig.Entry>) {
        val movable = entries.filter { it.dirName != TopBarConfig.DEFAULT_DIR_NAME }
        if (movable.isEmpty()) {
            _events.trySend(TopBarEvent.Toast(R.string.navigation_bar_default_readonly))
            return
        }
        execute {
            val now = System.currentTimeMillis()
            movable.forEachIndexed { index, entry ->
                TopBarConfig.toTop(entry, now - index)
            }
            reload()
        }
    }

    fun exportZip(entry: TopBarConfig.Entry) {
        execute {
            runCatching { TopBarConfig.exportZip(entry) }
                .onSuccess { _events.trySend(TopBarEvent.ExportZip(it)) }
                .onFailure { _events.trySend(TopBarEvent.ToastMsg(it.localizedMessage.orEmpty())) }
        }
    }

    fun exportJson(entries: List<TopBarConfig.Entry>, single: Boolean) {
        if (entries.isEmpty()) return
        val json = if (single) {
            entries.first().config.toJson()
        } else {
            GSON.toJson(entries.map { it.config })
        }
        _events.trySend(TopBarEvent.ShareJson(json))
    }

    fun importZip(file: File) {
        execute {
            runCatching { TopBarConfig.importZip(file) }
                .onSuccess {
                    reload()
                    _events.trySend(TopBarEvent.Toast(R.string.import_success))
                }
                .onFailure {
                    _events.trySend(TopBarEvent.ToastMsg(it.localizedMessage.orEmpty()))
                }
        }
    }

    fun importFromClipboard() {
        execute {
            val clipText = appCtx.getClipText()
            if (clipText.isNullOrBlank()) {
                _events.trySend(TopBarEvent.Toast(R.string.clipboard_empty))
                return@execute
            }
            runCatching { TopBarConfig.importJson(clipText, isNight) }
                .onSuccess {
                    reload()
                    _events.trySend(TopBarEvent.Toast(R.string.import_success))
                }
                .onFailure {
                    _events.trySend(TopBarEvent.Toast(R.string.import_failed))
                }
        }
    }

    fun deleteEntries(entries: List<TopBarConfig.Entry>) {
        // 内置包与当前生效包不允许批量删除，否则顶栏会失去唯一生效项
        val removable = entries.filter {
            it.dirName != TopBarConfig.DEFAULT_DIR_NAME && it.dirName != _activeDirName.value
        }
        if (removable.isEmpty()) return
        execute {
            removable.forEach { TopBarConfig.deleteLocal(it) }
            postEvent(EventBus.TOP_BAR_CHANGED, isNight)
            reload()
        }
    }

    private fun reload() {
        runCatching { TopBarConfig.loadEntries(appCtx, isNight) }
            .onSuccess {
                _items.value = it
                _activeDirName.value = TopBarConfig.activeDirName(isNight)
            }
    }

    private fun nextPackageName(usedNames: Set<String>): String {
        val base = appCtx.getString(R.string.custom_top_bar)
        if (base !in usedNames) return base
        for (index in 2..999) {
            val name = "$base $index"
            if (name !in usedNames) return name
        }
        return "$base ${System.currentTimeMillis()}"
    }
}

/** 顶栏管理的一次性事件。 */
sealed interface TopBarEvent {
    data class Toast(val resId: Int) : TopBarEvent
    data class ToastMsg(val msg: String) : TopBarEvent
    data class ShareJson(val json: String) : TopBarEvent
    data class ExportZip(val file: File) : TopBarEvent
}
