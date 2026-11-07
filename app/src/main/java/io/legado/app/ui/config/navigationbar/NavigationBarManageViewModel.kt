package io.legado.app.ui.config.navigationbar

import android.app.Application
import android.graphics.Color
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.utils.getClipText
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import splitties.init.appCtx

/**
 * 底栏管理的数据层。
 *
 * 只负责读盘/落盘与一次性事件，UI 状态（Tab、多选、编辑草稿的可见性）由
 * `ConfigManageState` 与 Screen 持有。应用配置时**不再顺带切换全局日夜**
 * （旧实现会在日夜不一致时调用 `ThemeConfig.applyDayNight`，应用一套夜间底栏
 * 会把整个应用切到夜间，与顶栏/气泡页的语义不一致）。
 */
class NavigationBarManageViewModel(application: Application) : BaseViewModel(application) {

    private val _items = MutableStateFlow<List<NavigationBarConfig>>(emptyList())
    val items: StateFlow<List<NavigationBarConfig>> = _items.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _draft = MutableStateFlow<NavigationBarConfig?>(null)
    val draft: StateFlow<NavigationBarConfig?> = _draft.asStateFlow()

    private val _events = Channel<NavigationBarEvent>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    private val allConfigs = mutableListOf<NavigationBarConfig>()
    private var isNight = false

    /** 编辑弹窗里「跟随主题」底色，用于预览图与透明度换算 */
    val defaultBottomColor: Int
        get() = if (isNight) {
            appCtx.getPrefInt(
                PreferKey.cNBBackground,
                appCtx.getCompatColor(R.color.default_night_bottom_background)
            )
        } else {
            appCtx.getPrefInt(
                PreferKey.cBBackground,
                appCtx.getCompatColor(R.color.default_bottom_background)
            )
        }

    fun load(isNight: Boolean) {
        this.isNight = isNight
        execute {
            val configs = NavigationBarConfig.loadConfigs(appCtx)
            allConfigs.clear()
            allConfigs.addAll(configs)
            var active = NavigationBarConfig.activeId(appCtx, isNight)
            if (active.isNullOrEmpty()) {
                active = configs.firstOrNull { it.isNight == isNight }?.id
                NavigationBarConfig.setActiveId(appCtx, isNight, active)
            }
            _activeId.value = active
            // 日/夜两组一次性返回，由 UI 按 Tab 过滤，避免切换 Tab 时出现空列表闪动
            _items.value = configs.toList()
        }
    }

    fun startCreate() {
        val usedNames = allConfigs.map { it.name }.toSet()
        _draft.value = NavigationBarConfig(
            id = "custom_${System.currentTimeMillis()}",
            name = nextConfigName(usedNames),
            isNight = isNight,
            isBuiltin = false,
            layoutMode = NavigationBarConfig.LAYOUT_FLOATING,
            effectMode = NavigationBarConfig.EFFECT_GLASS,
            opacity = 100,
            borderColor = Color.TRANSPARENT
        )
    }

    fun startEdit(config: NavigationBarConfig) {
        _draft.value = config.copySelf()
    }

    fun clearDraft() {
        _draft.value = null
    }

    fun updateDraft(transform: (NavigationBarConfig) -> NavigationBarConfig) {
        _draft.value = _draft.value?.let(transform)
    }

    fun updateDraftIcon(itemKey: String, state: String, path: String) {
        updateDraft { config ->
            config.copySelf().apply {
                icons = icons.toMutableMap().apply {
                    put(NavigationBarConfig.iconKey(itemKey, state), path)
                }
            }
        }
    }

    fun removeDraftIcon(itemKey: String, state: String) {
        updateDraft { config ->
            config.copySelf().apply {
                icons = icons.toMutableMap().apply {
                    remove(NavigationBarConfig.iconKey(itemKey, state))
                }
            }
        }
    }

    /** 保存草稿：`id` 已存在于列表则为更新，否则为新增。 */
    fun saveDraft() {
        val draft = _draft.value ?: return
        val name = draft.name.trim()
        if (name.isBlank()) {
            _events.trySend(NavigationBarEvent.Toast(R.string.input_is_empty))
            return
        }
        execute {
            val wasActive = draft.id == _activeId.value
            val next = draft.copy(name = name, updatedAt = System.currentTimeMillis())
            val index = allConfigs.indexOfFirst { it.id == next.id }
            if (index >= 0) allConfigs[index] = next else allConfigs.add(next)
            persist()
            if (wasActive) {
                NavigationBarConfig.applyConfig(appCtx, next)
            }
            reloadItems()
            _draft.value = null
        }
    }

    fun applyConfig(config: NavigationBarConfig) {
        if (config.id == _activeId.value) {
            // 已是当前配置：重复应用无副作用，但给出明确反馈，避免用户以为没生效
            _events.trySend(NavigationBarEvent.Toast(R.string.applied))
            return
        }
        execute {
            NavigationBarConfig.setActiveId(appCtx, config.isNight, config.id)
            NavigationBarConfig.applyConfig(appCtx, config)
            _activeId.value = config.id
            _events.trySend(NavigationBarEvent.ToastMsg(
                appCtx.getString(R.string.applied_nav_bar_config, config.name)
            ))
        }
    }

    fun deleteConfig(config: NavigationBarConfig) {
        if (config.isBuiltin) return
        execute {
            allConfigs.removeAll { it.id == config.id }
            if (_activeId.value == config.id) {
                _activeId.value = allConfigs.firstOrNull { it.isNight == isNight }?.id
                NavigationBarConfig.setActiveId(appCtx, isNight, _activeId.value)
            }
            persist()
            reloadItems()
        }
    }

    fun toTop(config: NavigationBarConfig) {
        execute {
            NavigationBarConfig.toTop(appCtx, config)
            if (_activeId.value == config.id) {
                // toTop 会重写 updatedAt，激活项无需重新 apply（内容未变）
            }
            allConfigs.clear()
            allConfigs.addAll(NavigationBarConfig.loadConfigs(appCtx))
            reloadItems()
            _events.trySend(NavigationBarEvent.Toast(R.string.success))
        }
    }

    fun exportConfig(config: NavigationBarConfig) {
        _events.trySend(NavigationBarEvent.ShareJson(config.toJson()))
    }

    fun exportConfigs(configs: List<NavigationBarConfig>) {
        if (configs.isEmpty()) return
        _events.trySend(NavigationBarEvent.ShareJson(io.legado.app.utils.GSON.toJson(configs)))
    }

    fun deleteConfigs(configs: List<NavigationBarConfig>) {
        val removable = configs.filterNot { it.isBuiltin }
        if (removable.isEmpty()) return
        execute {
            val ids = removable.map { it.id }.toSet()
            allConfigs.removeAll { it.id in ids }
            if (_activeId.value in ids) {
                _activeId.value = allConfigs.firstOrNull { it.isNight == isNight }?.id
                NavigationBarConfig.setActiveId(appCtx, isNight, _activeId.value)
            }
            persist()
            reloadItems()
        }
    }

    fun toTopConfigs(configs: List<NavigationBarConfig>) {
        if (configs.isEmpty()) return
        execute {
            val ids = configs.map { it.id }.toSet()
            val moved = allConfigs.filter { it.id in ids && !it.isBuiltin }
                .map { it.copy(updatedAt = System.currentTimeMillis()) }
            if (moved.isEmpty()) return@execute
            allConfigs.removeAll { it.id in ids }
            allConfigs.addAll(0, moved)
            persist()
            reloadItems()
        }
    }

    fun importFromClipboard() {
        execute {
            val clipText = appCtx.getClipText()
            if (clipText.isNullOrBlank()) {
                _events.trySend(NavigationBarEvent.Toast(R.string.clipboard_empty))
                return@execute
            }
            runCatching {
                val config = NavigationBarConfig.fromJson(clipText).apply {
                    isBuiltin = false
                    isNight = this@NavigationBarManageViewModel.isNight
                    if (id.startsWith("builtin_")) {
                        id = "custom_${System.currentTimeMillis()}"
                    }
                    updatedAt = System.currentTimeMillis()
                }
                allConfigs.add(config)
                persist()
                reloadItems()
            }.onSuccess {
                _events.trySend(NavigationBarEvent.Toast(R.string.import_success))
            }.onFailure {
                _events.trySend(NavigationBarEvent.Toast(R.string.import_failed))
            }
        }
    }

    private fun persist() {
        NavigationBarConfig.saveConfigs(appCtx, allConfigs)
    }

    private fun reloadItems() {
        _items.value = allConfigs.toList()
        _activeId.value = NavigationBarConfig.activeId(appCtx, isNight)
    }

    private fun nextConfigName(usedNames: Set<String>): String {
        val base = appCtx.getString(R.string.custom_nav_bar)
        if (base !in usedNames) return base
        for (index in 2..999) {
            val name = "$base $index"
            if (name !in usedNames) return name
        }
        return "$base ${System.currentTimeMillis()}"
    }
}

/** 底栏管理的一次性事件。 */
sealed interface NavigationBarEvent {
    data class Toast(val resId: Int) : NavigationBarEvent
    data class ToastMsg(val msg: String) : NavigationBarEvent
    data class ShareJson(val json: String) : NavigationBarEvent
}
