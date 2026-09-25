package io.legado.app.ui.main.my

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.FragmentMyConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.service.WebService
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.readRecord.ReadRecordActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.storage.StorageManageActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.debug.DebugToolsActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.urlRecord.UrlRecordActivity
import io.legado.app.utils.getPrefString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 「我的」Fragment。
 *
 * 页面结构（顶栏）仍由 View 体系承担——主界面是 `ViewPager` + `TitleBar` 的 View 宿主，
 * 顶栏颜色必须继续走 `TopBarConfig` 统一体系，换成 Compose 顶栏反而会被排除在主题配置外。
 * 顶栏以下的设置列表整体 Compose 化，本类是它的宿主：持有状态、执行平台操作、把动作翻译成跳转。
 */
class MyFragment() : BaseFragment(R.layout.fragment_my_config), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentMyConfigBinding::bind)

    /** 页面状态；Compose 侧直接读这个快照状态，写入即触发重组 */
    private var uiState by mutableStateOf(MySettingsUiState())

    /** 底栏占用的高度（px），由 MainActivity 下发 */
    private var bottomPaddingPx by mutableIntStateOf(0)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        bottomPaddingPx = (activity as? MainActivity)?.mainContentBottomPadding() ?: 0
        // 开关的初始值以服务真实运行状态为准：进程被回收后偏好里可能残留着上次的 true，
        // 但服务并没有起来，直接用偏好值会显示成"已开启但地址为空"的假状态
        putPrefBoolean(PreferKey.webService, WebService.isRun)
        syncUiState()
        binding.composeSettings.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeSettings.setContent {
            LegadoTheme {
                MySettingsScreen(
                    state = uiState,
                    bottomPaddingPx = bottomPaddingPx,
                    onAction = ::handleAction
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从「其他设置」返回时调试模式可能已被改动，事件丢失时靠这里兜底对齐
        syncUiState()
    }

    override fun observeLiveBus() {
        observeEventSticky<String>(EventBus.WEB_SERVICE) { hostAddress ->
            uiState = uiState.copy(
                webServiceEnabled = WebService.isRun,
                webServiceAddress = hostAddress?.takeIf { it.isNotBlank() }
            )
        }
        observeEvent<Boolean>(EventBus.DEBUG_MODE_CHANGED) {
            uiState = uiState.copy(debugToolsVisible = AppConfig.debugMode)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_my, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_help -> showHelp("appHelp")
        }
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        bottomPaddingPx = bottomPadding
    }

    /** 把外部可变来源（偏好、服务状态）对齐进 Compose 状态 */
    private fun syncUiState() {
        uiState = uiState.copy(
            themeModeIndex = currentThemeModeIndex(),
            webServiceEnabled = WebService.isRun,
            webServiceAddress = WebService.hostAddress.takeIf { WebService.isRun },
            debugToolsVisible = AppConfig.debugMode
        )
    }

    private fun currentThemeModeIndex(): Int {
        val values = resources.getStringArray(R.array.theme_mode_v)
        val current = requireContext().getPrefString(PreferKey.themeMode, "0")
        return values.indexOf(current).takeIf { it >= 0 } ?: 0
    }

    private fun handleAction(action: MySettingsAction) {
        when (action) {
            is MySettingsAction.OpenRow -> openRow(action.key)

            MySettingsAction.OpenThemeModeDialog ->
                uiState = uiState.copy(dialog = MySettingsDialog.ThemeMode)

            is MySettingsAction.SetThemeMode -> {
                uiState = uiState.copy(dialog = null)
                applyThemeMode(action.index)
            }

            MySettingsAction.ToggleWebService -> setWebServiceEnabled(!uiState.webServiceEnabled)

            is MySettingsAction.SetWebServiceEnabled -> setWebServiceEnabled(action.enabled)

            MySettingsAction.OpenWebServiceActions -> {
                // 服务没起来就没有地址可复制/打开，此时不弹菜单，避免给出一个空操作
                if (uiState.webServiceAddress.isNullOrBlank()) {
                    uiState = uiState.copy(dialog = null)
                } else {
                    uiState = uiState.copy(dialog = MySettingsDialog.WebServiceActions)
                }
            }

            MySettingsAction.CopyWebServiceAddress -> {
                uiState = uiState.copy(dialog = null)
                uiState.webServiceAddress?.let { requireContext().sendToClip(it) }
            }

            MySettingsAction.OpenWebServiceAddressInBrowser -> {
                uiState = uiState.copy(dialog = null)
                uiState.webServiceAddress?.let { requireContext().openUrl(it) }
            }

            MySettingsAction.DismissDialog -> uiState = uiState.copy(dialog = null)
        }
    }

    private fun applyThemeMode(index: Int) {
        val values = resources.getStringArray(R.array.theme_mode_v)
        val value = values.getOrNull(index) ?: return
        requireContext().putPrefString(PreferKey.themeMode, value)
        uiState = uiState.copy(themeModeIndex = index)
        // 日夜模式切换要同步 XML 主题资源（弹窗样式、系统栏），因此仍走统一的日夜应用入口
        ThemeConfig.applyDayNight(requireContext())
    }

    private fun setWebServiceEnabled(enabled: Boolean) {
        putPrefBoolean(PreferKey.webService, enabled)
        val context = requireContext()
        if (enabled) {
            WebService.start(context)
        } else {
            WebService.stop(context)
        }
        uiState = uiState.copy(
            webServiceEnabled = WebService.isRun,
            webServiceAddress = WebService.hostAddress.takeIf { WebService.isRun }
        )
    }

    private fun openRow(key: String) {
        when (key) {
            "bookSourceManage" -> startActivity<BookSourceActivity>()
            "replaceManage" -> startActivity<ReplaceRuleActivity>()
            "dictRuleManage" -> startActivity<DictRuleActivity>()
            "txtTocRuleManage" -> startActivity<TxtTocRuleActivity>()
            "debugTools" -> startActivity<DebugToolsActivity>()
            "urlRecord" -> startActivity<UrlRecordActivity>()
            "bookmark" -> startActivity<AllBookmarkActivity>()
            "preciseManage" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.PRECISE_MANAGE)
            }

            "setting" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.OTHER_CONFIG)
            }

            "web_dav_setting" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.BACKUP_CONFIG)
            }

            "theme_setting" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.THEME_CONFIG)
            }

            "readRecord" -> startActivity<ReadRecordActivity>()
            "storageManage" -> startActivity<StorageManageActivity>()
            "about" -> startActivity<AboutActivity>()
            "exit" -> activity?.finish()
        }
    }
}
