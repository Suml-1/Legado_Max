package io.legado.app.ui.config.theme.application.edit

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.TopBarConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import splitties.init.appCtx
import java.util.UUID

/**
 * 应用主题编辑页的数据层。
 *
 * 八个可选项（日/夜 × 主题/顶栏/底栏/封面）的候选项在 IO 线程一次取齐，
 * 界面只做展示与选择，不在组合期访问数据库。
 */
class ApplicationThemeEditViewModel(application: Application) : BaseViewModel(application) {

    private val _draft = MutableStateFlow<ApplicationThemeManager.Config?>(null)
    val draft: StateFlow<ApplicationThemeManager.Config?> = _draft.asStateFlow()

    private val _candidates = MutableStateFlow(EditCandidates())
    val candidates: StateFlow<EditCandidates> = _candidates.asStateFlow()

    private val _events = Channel<ApplicationThemeEditEvent>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    var isNew = true
        private set

    fun load(id: String?) {
        execute {
            val existing = id?.takeIf { it.isNotBlank() }?.let { ApplicationThemeManager.find(it) }
            isNew = existing == null
            _draft.value = existing?.copy() ?: ApplicationThemeManager.captureCurrent(
                appCtx,
                appCtx.getString(R.string.application_theme_manage),
            ).copy(id = UUID.randomUUID().toString())
            _candidates.value = loadCandidates()
        }
    }

    fun updateDraft(transform: (ApplicationThemeManager.Config) -> ApplicationThemeManager.Config) {
        _draft.value = _draft.value?.let(transform)
    }

    fun save() {
        val draft = _draft.value ?: return
        val name = draft.name.trim()
        if (name.isBlank()) {
            _events.trySend(ApplicationThemeEditEvent.Toast(R.string.input_is_empty))
            return
        }
        execute {
            val config = draft.copy(name = name, updatedAt = System.currentTimeMillis())
            if (isNew) ApplicationThemeManager.add(config) else ApplicationThemeManager.replace(config)
            _events.trySend(ApplicationThemeEditEvent.Saved)
        }
    }

    fun delete() {
        val draft = _draft.value ?: return
        if (isNew) return
        execute {
            ApplicationThemeManager.delete(appCtx, draft.id)
            _events.trySend(ApplicationThemeEditEvent.Deleted)
        }
    }

    private fun loadCandidates(): EditCandidates {
        val themes = runCatching { ThemeConfig.configList }.getOrDefault(emptyList())
        val dayTopBars = runCatching { TopBarConfig.loadEntries(appCtx, false) }.getOrDefault(emptyList())
        val nightTopBars = runCatching { TopBarConfig.loadEntries(appCtx, true) }.getOrDefault(emptyList())
        val bottomBars = runCatching { NavigationBarConfig.loadConfigs(appCtx) }.getOrDefault(emptyList())
        val covers = runCatching {
            CoverGalleryRepository().allGroupsWithImages().map { it.group.id to it.group.name }
        }.getOrDefault(emptyList())
        return EditCandidates(
            dayThemes = themes.filter { !it.isNightTheme },
            nightThemes = themes.filter { it.isNightTheme },
            dayTopBars = dayTopBars,
            nightTopBars = nightTopBars,
            dayBottomBars = bottomBars.filter { !it.isNight },
            nightBottomBars = bottomBars.filter { it.isNight },
            covers = covers,
        )
    }
}

/** 编辑页可选的候选项 */
data class EditCandidates(
    val dayThemes: List<ThemeConfig.Config> = emptyList(),
    val nightThemes: List<ThemeConfig.Config> = emptyList(),
    val dayTopBars: List<TopBarConfig.Entry> = emptyList(),
    val nightTopBars: List<TopBarConfig.Entry> = emptyList(),
    val dayBottomBars: List<NavigationBarConfig> = emptyList(),
    val nightBottomBars: List<NavigationBarConfig> = emptyList(),
    val covers: List<Pair<Long, String>> = emptyList(),
)

sealed interface ApplicationThemeEditEvent {
    data class Toast(val resId: Int) : ApplicationThemeEditEvent
    data object Saved : ApplicationThemeEditEvent
    data object Deleted : ApplicationThemeEditEvent
}
