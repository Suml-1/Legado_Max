package io.legado.app.ui.config.theme.application

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.ui.config.theme.application.components.ThemeComponentSelection
import io.legado.app.utils.GSON
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import splitties.init.appCtx
import java.io.File

/**
 * 应用主题（主题包）列表的数据层。
 *
 * 一个「应用主题」= 日/夜的 [io.legado.app.help.config.ThemeConfig.Config] +
 * 顶栏目录名 + 底栏配置 ID + 封面图集组 ID，因此导入/删除都要按组件勾选范围执行。
 */
class ApplicationThemeViewModel(application: Application) : BaseViewModel(application) {

    private val _items = MutableStateFlow<List<ApplicationThemeManager.Config>>(emptyList())
    val items: StateFlow<List<ApplicationThemeManager.Config>> = _items.asStateFlow()

    private val _currentId = MutableStateFlow("")
    val currentId: StateFlow<String> = _currentId.asStateFlow()

    /** 摘要文本（含图集名称查询），在 IO 线程预先生成，避免组合期读库 */
    private val _summaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val summaries: StateFlow<Map<String, String>> = _summaries.asStateFlow()

    private val _events = Channel<ApplicationThemeEvent>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    fun load() {
        execute { refresh() }
    }

    fun apply(config: ApplicationThemeManager.Config) {
        execute {
            runCatching { ApplicationThemeManager.apply(appCtx, config) }
                .onSuccess {
                    _currentId.value = config.id
                    _events.trySend(ApplicationThemeEvent.Applied)
                }
                .onFailure {
                    _events.trySend(
                        ApplicationThemeEvent.ToastMsg(it.localizedMessage ?: appCtx.getString(R.string.error)),
                    )
                }
        }
    }

    fun rename(id: String, name: String) {
        execute {
            ApplicationThemeManager.rename(id, name)
            refresh()
        }
    }

    /** 用当前生效的主题/顶栏/底栏/封面覆盖该主题包 */
    fun updateFromCurrent(config: ApplicationThemeManager.Config) {
        execute {
            runCatching {
                val captured = ApplicationThemeManager.captureCurrent(appCtx, config.name, config.id)
                ApplicationThemeManager.replace(captured)
            }.onSuccess {
                refresh()
                _events.trySend(ApplicationThemeEvent.Toast(R.string.success))
            }.onFailure {
                _events.trySend(
                    ApplicationThemeEvent.ToastMsg(it.localizedMessage ?: appCtx.getString(R.string.error)),
                )
            }
        }
    }

    fun copyJson(config: ApplicationThemeManager.Config) {
        _events.trySend(ApplicationThemeEvent.CopyText(GSON.toJson(config)))
    }

    fun export(
        config: ApplicationThemeManager.Config,
        format: ApplicationThemeManager.ExportFormat,
    ) {
        execute {
            runCatching { ApplicationThemeManager.exportConfig(appCtx, config, format) }
                .onSuccess { _events.trySend(ApplicationThemeEvent.ExportFile(it)) }
                .onFailure {
                    _events.trySend(
                        ApplicationThemeEvent.ToastMsg(it.localizedMessage ?: appCtx.getString(R.string.error)),
                    )
                }
        }
    }

    /** 导入文件；勾选范围取用户上次在导入弹窗里的选择（已持久化） */
    fun importFile(file: File) {
        execute {
            runCatching {
                ApplicationThemeManager.importFile(
                    file,
                    ApplicationThemeManager.getImportOptions(appCtx).toSelection().toImportOptions(),
                )
            }
                .onSuccess {
                    refresh()
                    _events.trySend(ApplicationThemeEvent.Toast(R.string.import_success))
                }
                .onFailure {
                    _events.trySend(
                        ApplicationThemeEvent.ToastMsg(it.localizedMessage ?: appCtx.getString(R.string.import_failed)),
                    )
                }
        }
    }

    fun delete(id: String, selection: ThemeComponentSelection) {
        execute {
            runCatching { ApplicationThemeManager.deleteWithComponents(appCtx, id, selection.toDeleteOptions()) }
                .onSuccess {
                    refresh()
                    _events.trySend(ApplicationThemeEvent.Toast(R.string.success))
                }
                .onFailure {
                    _events.trySend(
                        ApplicationThemeEvent.ToastMsg(it.localizedMessage ?: appCtx.getString(R.string.error)),
                    )
                }
        }
    }

    /** 多选删除：只删主题包本身，不连带删除关联的顶栏/底栏/封面包 */
    fun deleteMany(configs: List<ApplicationThemeManager.Config>) {
        val removable = configs.filterNot { ApplicationThemeManager.isBuiltin(it) }
        if (removable.isEmpty()) return
        execute {
            removable.forEach { ApplicationThemeManager.delete(appCtx, it.id) }
            refresh()
        }
    }

    fun toTop(configs: List<ApplicationThemeManager.Config>) {
        val ids = configs.filterNot { ApplicationThemeManager.isBuiltin(it) }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        execute {
            ApplicationThemeManager.toTop(ids)
            refresh()
        }
    }

    fun saveImportOptions(selection: ThemeComponentSelection) {
        ApplicationThemeManager.saveImportOptions(appCtx, selection.toImportOptions())
    }

    fun saveDeleteOptions(selection: ThemeComponentSelection) {
        ApplicationThemeManager.saveDeleteOptions(appCtx, selection.toDeleteOptions())
    }

    fun importSelection(): ThemeComponentSelection = ApplicationThemeManager.getImportOptions(appCtx).toSelection()

    fun deleteSelection(): ThemeComponentSelection = ApplicationThemeManager.getDeleteOptions(appCtx).toSelection()

    /** 多选导出：把若干主题序列化为一个 JSON 数组写入剪贴板 */
    fun copyJsonList(configs: List<ApplicationThemeManager.Config>) {
        if (configs.isEmpty()) return
        _events.trySend(ApplicationThemeEvent.CopyText(GSON.toJson(configs)))
    }

    private fun refresh() {
        val list = ApplicationThemeManager.load()
        _items.value = list
        _summaries.value = list.associate { it.id to ApplicationThemeManager.summary(appCtx, it) }
        _currentId.value = ApplicationThemeManager.currentId(appCtx)
    }
}

/** 应用主题列表的一次性事件。 */
sealed interface ApplicationThemeEvent {
    data class Toast(val resId: Int) : ApplicationThemeEvent
    data class ToastMsg(val msg: String) : ApplicationThemeEvent
    data class CopyText(val text: String) : ApplicationThemeEvent
    data class ExportFile(val file: File) : ApplicationThemeEvent

    /** 应用成功后需要重建当前页面（主题颜色已变） */
    data object Applied : ApplicationThemeEvent
}

private fun ThemeComponentSelection.toImportOptions() = ApplicationThemeManager.ImportOptions(
    importDayTheme = dayTheme,
    importNightTheme = nightTheme,
    importDayTopBar = dayTopBar,
    importNightTopBar = nightTopBar,
    importDayBottomBar = dayBottomBar,
    importNightBottomBar = nightBottomBar,
    importDayCover = dayCover,
    importNightCover = nightCover,
)

private fun ThemeComponentSelection.toDeleteOptions() = ApplicationThemeManager.DeleteOptions(
    deleteDayTheme = dayTheme,
    deleteNightTheme = nightTheme,
    deleteDayTopBar = dayTopBar,
    deleteNightTopBar = nightTopBar,
    deleteDayBottomBar = dayBottomBar,
    deleteNightBottomBar = nightBottomBar,
    deleteDayCover = dayCover,
    deleteNightCover = nightCover,
)

private fun ApplicationThemeManager.ImportOptions.toSelection() = ThemeComponentSelection(
    dayTheme = importDayTheme,
    nightTheme = importNightTheme,
    dayTopBar = importDayTopBar,
    nightTopBar = importNightTopBar,
    dayBottomBar = importDayBottomBar,
    nightBottomBar = importNightBottomBar,
    dayCover = importDayCover,
    nightCover = importNightCover,
)

private fun ApplicationThemeManager.DeleteOptions.toSelection() = ThemeComponentSelection(
    dayTheme = deleteDayTheme,
    nightTheme = deleteNightTheme,
    dayTopBar = deleteDayTopBar,
    nightTopBar = deleteNightTopBar,
    dayBottomBar = deleteDayBottomBar,
    nightBottomBar = deleteNightBottomBar,
    dayCover = deleteDayCover,
    nightCover = deleteNightCover,
)
