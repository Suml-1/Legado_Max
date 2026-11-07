package io.legado.app.base

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent

/**
 * Compose Activity 基类。
 *
 * 统一处理所有 Compose 页面必备的初始化流程，子类只需覆写 [ComposeContent]。
 *
 * ## 生命周期
 * ```
 * initLegadoComposeTheme()   // 必须在 super.onCreate 之前
 *   → super.onCreate()
 *     → observeLiveBus()     // 子类注册 LiveBus 事件
 *     → onActivityCreated()  // 子类创建 ViewModel、初始化状态
 *     → setLegadoContent { ComposeContent() }  // 包裹 LegadoTheme + 背景图 + 系统栏
 * ```
 *
 * ## 各层职责
 * - **initLegadoComposeTheme**: 根据全局主题配置设置 XML theme（暗色/亮色/跟随系统）
 * - **setLegadoContent**: 内部 → system bar 设置、背景图加载、edge-to-edge、
 *   LegadoTheme 包裹、ReadAloud mini bar、DebugFloatingBall 生命周期
 * - **onActivityCreated**: 创建 ViewModel、注册 ActivityResult 等
 * - **ComposeContent**: 纯 Compose UI，已在 LegadoTheme 内，直接用 MaterialTheme.colorScheme
 *
 * @see io.legado.app.ui.theme.setLegadoContent
 * @see io.legado.app.ui.theme.LegadoTheme
 */
abstract class BaseComposeActivity : AppCompatActivity() {

    private var recreateRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用 setTheme()
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        observeLiveBus()
        onActivityCreated(savedInstanceState)
        setLegadoContent(overlayAlpha = composeOverlayAlpha()) {
            ComposeContent()
        }
    }

    /**
     * 重建统一走「销毁 + 全新 startActivity」。
     *
     * 两点原因：
     * 1. 合并同一实例周期内的多次重建请求。一次配置变更（应用主题/顶栏/底栏等）可能同时
     *    被 AppCompat 模式切换、事件总线、onConfigurationChanged 等多路触发，竞态双重建
     *    会在部分 ROM 上破坏窗口/输入状态（见 docs/archive/主题列表应用主题后UI卡死根因分析.md）。
     * 2. 原地 `super.recreate()` 走的是 ActivityTaskManager 的「原地重启动」，重建出的
     *    Compose 实例存在缺陷：输入与状态写入正常，但重组/重绘调度不再触发
     *    （见 `docs/project-rules/compose/theme-styles.md` §7.8.1）。全新启动的实例链路健康，
     *    因此这里接管为「先启动新实例、再销毁旧实例」，并透传原 intent 以保留启动参数。
     */
    override fun recreate() {
        if (recreateRequested || isFinishing || isDestroyed) return
        recreateRequested = true
        startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        finish()
    }

    /**
     * 模板方法：在 super.onCreate 之后、setContent 之前调用。
     *
     * 适合创建 ViewModel、注册 ActivityResult launcher、初始化 LiveData 观察者等。
     * 此时 Activity 已完全初始化，但 Compose 尚未开始组合。
     */
    open fun onActivityCreated(savedInstanceState: Bundle?) {}

    /**
     * 背景图覆盖透明度。
     *
     * 返回 null 则使用默认值：亮色背景 0.10f，暗色背景 0.18f。
     * 覆盖此方法可自定义，例如半透明页面可能需要更低的 alpha。
     */
    open fun composeOverlayAlpha(): Float? = null

    /**
     * 事件订阅入口（模板方法）。
     *
     * 子类覆写此方法，调用 observeEvent() 注册感兴趣的事件。
     * 观察者与 Activity 生命周期绑定，销毁时自动移除。
     *
     * 示例：
     * ```
     * override fun observeLiveBus() {
     *     observeEvent<String>(EventBus.SOME_EVENT) { handle(it) }
     * }
     * ```
     */
    open fun observeLiveBus() {}

    /**
     * Compose 内容入口。
     *
     * 此方法在 [setLegadoContent] 的 lambda 中调用，已被 [io.legado.app.ui.theme.LegadoTheme]
     * 包裹，可直接使用 MaterialTheme.colorScheme 获取当前主题色。
     */
    @Composable
    abstract fun ComposeContent()
}
