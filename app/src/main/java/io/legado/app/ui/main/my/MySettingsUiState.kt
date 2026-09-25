package io.legado.app.ui.main.my

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * 「我的」页面板行的行为类型。
 *
 * 只有需要特殊交互（弹窗、开关）的行才单列类型，其余同等对待为跳转动作，
 * 这样新增一个普通设置项不需要改 Screen。
 */
internal enum class MySettingsRowKind {
    /** 点击后执行动作（跳转页面或打开子配置） */
    Action,

    /** 主题模式：副标题展示当前选项名，点击弹出单选列表 */
    ThemeMode,

    /** Web 服务：副标题展示访问地址，尾部开关控制启停，长按弹出地址操作 */
    WebService
}

/**
 * 面板行定义。
 *
 * 文案一律持有资源 id 而不是已解析的字符串：字符串的解析时机属于 UI 层，
 * 提前解析会把语言切换、主题字体的响应性丢掉（theme-styles.md §7.5）。
 */
internal data class MySettingsRow(
    /** 稳定标识，用于回调时区分动作；改动会导致点击行为错位 */
    val key: String,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int? = null,
    val kind: MySettingsRowKind = MySettingsRowKind.Action,
    /** 危险操作行，标题与图标转为错误色 */
    val danger: Boolean = false,
    /** 仅在调试模式下可见 */
    val debugOnly: Boolean = false
)

/**
 * 面板分组。
 *
 * @param titleRes 分组标题；为 `null` 表示该组不显示标题（如首屏常用项、末尾的关于/退出）
 */
internal data class MySettingsSection(
    @StringRes val titleRes: Int?,
    val rows: List<MySettingsRow>
)

/**
 * 弹窗状态。
 *
 * 显隐收进 [MySettingsUiState] 由 Screen 条件渲染，而不是在 Fragment 里单独持有一个开关：
 * 开关散落后返回键拦截与重建恢复都要各写一遍（state-events.md §4.5）。
 */
internal sealed interface MySettingsDialog {

    /** 主题模式单选 */
    data object ThemeMode : MySettingsDialog

    /** Web 服务地址的长按操作（复制地址 / 浏览器打开） */
    data object WebServiceActions : MySettingsDialog
}

/**
 * 「我的」页 UI 状态。
 *
 * 只放**动态**部分：静态的行的定义在 [MySettingsCatalog]，避免每次状态变化都重建整棵列表结构。
 */
internal data class MySettingsUiState(
    /** 主题模式数组下标，取值对应 `R.array.theme_mode` */
    val themeModeIndex: Int = 0,
    val webServiceEnabled: Boolean = false,
    /** Web 服务运行时的访问地址；为 `null` 表示未运行，副标题回退到说明文案 */
    val webServiceAddress: String? = null,
    /** 调试工具入口是否可见，跟随 `AppConfig.debugMode` */
    val debugToolsVisible: Boolean = false,
    val dialog: MySettingsDialog? = null
)

/**
 * Screen 向上抛的一次性动作。
 *
 * 平台操作（跳转、启停服务、剪贴板）全部由 Fragment 执行，Screen 不持有任何平台依赖
 * （state-events.md §4.1）。
 */
internal sealed interface MySettingsAction {

    /** 普通设置项点击，[key] 为 [MySettingsRow.key] */
    data class OpenRow(val key: String) : MySettingsAction

    /** 打开主题模式单选弹窗 */
    data object OpenThemeModeDialog : MySettingsAction

    /** 选定主题模式，[index] 为 `R.array.theme_mode_v` 下标 */
    data class SetThemeMode(val index: Int) : MySettingsAction

    /** 点击 Web 服务行：与开关等价地切换启停（保持旧版整行可点即切换的行为） */
    data object ToggleWebService : MySettingsAction

    /** 开关直接切换启停 */
    data class SetWebServiceEnabled(val enabled: Boolean) : MySettingsAction

    /** 长按 Web 服务行，弹出地址操作菜单 */
    data object OpenWebServiceActions : MySettingsAction

    /** 复制 Web 服务地址 */
    data object CopyWebServiceAddress : MySettingsAction

    /** 用浏览器打开 Web 服务地址 */
    data object OpenWebServiceAddressInBrowser : MySettingsAction

    /** 关闭当前弹窗 */
    data object DismissDialog : MySettingsAction
}
