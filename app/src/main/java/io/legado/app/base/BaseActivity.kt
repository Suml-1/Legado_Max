package io.legado.app.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.AppLog
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgEffectThemeSelection
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.config.ReadAloudActivity
import io.legado.app.ui.widget.ReadAloudMiniBarController
import io.legado.app.ui.widget.ReadAloudMiniBarHost
import io.legado.app.ui.design.components.view.NgBackdropSourceLayout
import io.legado.app.ui.design.theme.NgEffectThemeHostView
import io.legado.app.ui.design.theme.NgThemeGradientHostView
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.applyTopBarConfig
import io.legado.app.ui.widget.topBarContentColor
import io.legado.app.ui.debuglog.DebugFloatingBallManager
import io.legado.app.ui.debuglog.DebugLogPanelDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyBackgroundTint
import io.legado.app.utils.applyOpenTint
import io.legado.app.utils.applyTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.windowSize

abstract class BaseActivity<VB : ViewBinding>(
    val fullScreen: Boolean = true,
    private val theme: Theme = Theme.Auto,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true,
    private val showOpenMenuIcon: Boolean = true,
) : AppCompatActivity(),
    ReadAloudMiniBarHost {

    protected abstract val binding: VB
    private var readAloudMiniBarController: ReadAloudMiniBarController? = null

    // NG 主题宿主视图（架子已挂入内容根；特效/渐变/海报层的喂图与激活在后续阶段接线）
    private var ngThemeSceneHost: NgEffectThemeHostView? = null
    private var ngThemeScenePoster: ImageView? = null
    private var ngThemeGradientBackground: NgThemeGradientHostView? = null

    val isInMultiWindow: Boolean
        @SuppressLint("ObsoleteSdkInt")
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isInMultiWindowMode
            } else {
                false
            }
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppContextWrapper.wrap(newBase))
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? {
        if (AppConst.menuViewNames.contains(name) && parent?.parent is FrameLayout) {
            (parent.parent as View).setBackgroundColor(backgroundColor)
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    private fun createContentRoot(content: View): View {
        if (!imageBg) return content
        val root = FrameLayout(this)
        val sceneSource = NgBackdropSourceLayout(this).apply {
            id = R.id.ng_liquid_glass_backdrop_source
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
        val scenePoster = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val gradientBackground = NgThemeGradientHostView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val sceneHost = NgEffectThemeHostView(this)
        sceneSource.addView(gradientBackground)
        sceneSource.addView(scenePoster)
        sceneSource.addView(
            sceneHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            sceneSource,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        ngThemeScenePoster = scenePoster
        ngThemeSceneHost = sceneHost
        ngThemeGradientBackground = gradientBackground
        return root
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.disableAutoFill()
        // 内置外观（柔光渐变/多色）没有手动保存动作，不会触发颜色投影；
        // 必须在 initTheme 读色之前主动投影一次，View 界面才能拿到内置配色。
        NgColorConfigStore.projectBuiltInToLegacy(this)
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        setContentView(createContentRoot(binding.root))
        findViewById<ViewGroup>(android.R.id.content)?.let {
            readAloudMiniBarController = ReadAloudMiniBarController(this, this, it)
        }
        upBackgroundImage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            findViewById<TitleBar>(R.id.title_bar)
                ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        }
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
        observeLiveBus() // 模板方法：子类覆写 observeLiveBus() 注册事件订阅，自动在 onCreate 中调用
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            refreshReadAloudMiniBar()
        }
        // 顶栏配置变更时，重新应用顶栏配置到当前 Activity 的 TitleBar
        observeEvent<Boolean>(EventBus.TOP_BAR_CHANGED) { isNightMode ->
            if (isNightMode == AppConfig.isNightTheme) {
                findViewById<TitleBar>(R.id.title_bar)?.applyTopBarConfig()
            }
        }
        onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        ngThemeGradientBackground?.setHostActive(true)
        ngThemeSceneHost?.run {
            val profile = if (
                NgThemeModeStore.current(this@BaseActivity) ==
                NgThemePresentationMode.EFFECT_THEME
            ) {
                NgEffectThemeSelection.current(this@BaseActivity)
            } else {
                null
            }
            bind(profile)
            setHostActive(true)
        }
        DebugFloatingBallManager.onActivityResumed(this)
        refreshReadAloudMiniBar()
    }

    override fun onPause() {
        ngThemeGradientBackground?.setHostActive(false)
        ngThemeSceneHost?.setHostActive(false)
        super.onPause()
        readAloudMiniBarController?.onPause()
        DebugFloatingBallManager.onActivityPaused(this)
    }

    override fun onDestroy() {
        ngThemeGradientBackground?.setHostActive(false)
        ngThemeSceneHost?.setHostActive(false)
        ngThemeScenePoster?.setImageDrawable(null)
        ngThemeScenePoster = null
        ngThemeGradientBackground?.setGradientDrawable(null)
        ngThemeGradientBackground = null
        super.onDestroy()
        DebugFloatingBallManager.onActivityDestroyed(this)
        DebugLogPanelDialog.onActivityDestroyed(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        setupSystemBar()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindow, fullScreen)
        setupSystemBar()
    }

    abstract fun onActivityCreated(savedInstanceState: Bundle?)

    final override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val bool = onCompatCreateOptionsMenu(menu)
        // 顶栏菜单图标必须与标题同色：这里传入顶栏的内容色（按顶栏实际底色取），而不是只传主题——
        // 只传主题时图标按"主题主色 / 背景色"取色，于是"我的"里的各个子界面（书源管理、目录规则、
        // 替换净化、字典规则、主题设置、其它设置等）在自定义顶栏背景色、深色壁纸、墨水屏纸白顶栏
        // 下会出现"标题深色、图标浅色"这类不一致
        val titleBar = findViewById<TitleBar>(R.id.title_bar)
        if (titleBar == null || titleBar.skipTopBarConfig) {
            // 有意跳过统一顶栏的页面（skipTopBarConfig，如阅读页菜单）自己决定顶栏颜色，
            // 这里不能替它按"顶栏实际底色"取色，否则会与页面自己给书名/图标上的色不一致
            menu.applyTint(this, toolBarTheme)
        } else {
            menu.applyTint(this, titleBar.topBarTheme, titleBar.topBarContentColor())
        }
        return bool
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.applyOpenTint(this, showOpenMenuIcon)
        return super.onMenuOpened(featureId, menu)
    }

    open fun onCompatCreateOptionsMenu(menu: Menu) = super.onCreateOptionsMenu(menu)

    final override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            supportFinishAfterTransition()
            return true
        }
        return onCompatOptionsItemSelected(item)
    }

    open fun onCompatOptionsItemSelected(item: MenuItem) = super.onOptionsItemSelected(item)

    open fun initTheme() {
        when (theme) {
            Theme.Transparent -> setTheme(R.style.AppTheme_Transparent)
            Theme.Dark -> {
                setTheme(R.style.AppTheme_Dark)
                window.decorView.applyBackgroundTint(backgroundColor)
            }

            Theme.Light -> {
                setTheme(R.style.AppTheme_Light)
                window.decorView.applyBackgroundTint(backgroundColor)
            }

            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(R.style.AppTheme_Light)
                } else {
                    setTheme(R.style.AppTheme_Dark)
                }
                window.decorView.applyBackgroundTint(backgroundColor)
            }
        }
    }

    /** 应用任何背景图之前 decorView 的原始背景（主题纯色底），无背景图时恢复它而非把窗口背景置空 */
    private var defaultDecorBackground: Drawable? = null

    open fun upBackgroundImage() {
        // 柔光渐变/多色属于内置外观，不依赖「背景图」开关，必须独立喂给渐变宿主；
        // 其它模式下 getGradientBgImage 返回 null，这里等同于原来的空操作。
        applyGradientBackground()
        if (!imageBg) return
        // 首次调用时记录原始窗口背景（initTheme 设置的纯色底），
        // 供背景图缺失/加载失败时恢复；此时必然还没应用过任何背景图
        if (defaultDecorBackground == null) {
            defaultDecorBackground = window.decorView.background
        }
        // 签名为 null 表示未配置背景图或图片文件不存在（应显示纯色底）。
        // 这条路径没有任何解码/模糊工作，必须同步落地：若仍绕一圈异步回调，
        // 主界面背景会比底栏晚一拍甚至更久，两者的过渡节奏就对不齐了
        // （底栏色值同步可读，见 MainActivity.applyNavigationBarPackage）。
        val signature = ThemeConfig.getBackgroundSignature(this)
        if (signature == null) {
            onBackgroundDrawableLoaded(null)
            return
        }
        // 命中进程级缓存：同步应用，Activity 重建/返回主界面时无需重新解码，无闪烁
        ThemeConfig.getCachedBgImage(signature)?.let {
            onBackgroundDrawableLoaded(it)
            return
        }
        // 未命中缓存且确实配置了背景图：先用最近一次应用的背景图占位，
        // 避免异步解码期间先显示纯色底再跳变成背景图
        var placeholderApplied = false
        ThemeConfig.getLastBgImage(signature)?.let {
            onBackgroundDrawableLoaded(it)
            placeholderApplied = true
        }
        val windowSize = windowManager.windowSize
        lifecycleScope.launch(Dispatchers.Default) {
            val drawable = try {
                ThemeConfig.getBgImage(this@BaseActivity, windowSize)
            } catch (_: OutOfMemoryError) {
                toastOnUi("背景图片太大,内存溢出")
                null
            } catch (e: Exception) {
                AppLog.put("加载背景出错\n${e.localizedMessage}", e)
                null
            }
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    if (drawable != null) {
                        ThemeConfig.cacheBgImage(signature, drawable)
                        onBackgroundDrawableLoaded(drawable)
                    } else if (!placeholderApplied) {
                        // 加载失败且无占位时回调 null（恢复纯色底，清除旧背景图），
                        // 有占位时保留占位图，避免闪回纯色
                        onBackgroundDrawableLoaded(null)
                    }
                }
            }
        }
    }

    /**
     * 背景图异步解码完成回调（主线程）。
     *
     * [drawable] 为 null 表示无背景图配置或加载失败，此时恢复 [defaultDecorBackground]
     * （进入界面时的主题纯色底），实现"清除背景图后回到纯色"。
     * 注意不能把窗口背景置 null：decorView 没有背景 drawable 时硬件渲染不会
     * 整帧覆盖窗口表面，滑动等局部重绘场景会残留上一帧像素，造成持续闪烁。
     *
     * 子类若需要在背景就绪后做同步处理（如同步到其他 View），
     * 必须覆写本方法而非在 [upBackgroundImage] 调用后同步取值——
     * 解码是异步的，[upBackgroundImage] 返回时背景尚未生效。
     */
    protected open fun onBackgroundDrawableLoaded(drawable: Drawable?) {
        window.decorView.background = drawable ?: defaultDecorBackground
        val scenePoster = ngThemeScenePoster
        if (drawable != null && scenePoster != null) {
            val cropWallpaper = drawable is BitmapDrawable &&
                NgThemeLibraryStore.activeTheme(this)?.centerCropBackground == true
            val usesDynamicScene = NgThemeModeStore.current(this) ==
                NgThemePresentationMode.EFFECT_THEME
            scenePoster.scaleType = if (usesDynamicScene || cropWallpaper) {
                ImageView.ScaleType.CENTER_CROP
            } else {
                ImageView.ScaleType.FIT_XY
            }
            scenePoster.setImageDrawable(drawable)
            scenePoster.visibility = View.VISIBLE
        } else {
            scenePoster?.setImageDrawable(null)
            scenePoster?.visibility = View.GONE
        }
    }

    private fun applyGradientBackground() {
        val gradientDrawable = ThemeConfig.getGradientBgImage(this)
        ngThemeGradientBackground?.run {
            setGradientDrawable(gradientDrawable)
            visibility = if (gradientDrawable != null) View.VISIBLE else View.GONE
        }
    }

    open fun setupSystemBar() {
        if (fullScreen && !isInMultiWindow) {
            fullScreen()
        }
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, fullScreen)
        if (toolBarTheme == Theme.Dark) {
            setLightStatusBar(false)
        } else if (toolBarTheme == Theme.Light) {
            setLightStatusBar(true)
        }
        upNavigationBarColor()
    }

    open fun upNavigationBarColor() {
        val nbColor = ThemeStore.navigationBarColor(this)
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(nbColor, transparent = true)
        } else {
            setNavigationBarColorAuto(ColorUtils.darkenColor(nbColor))
        }
    }

    /**
     * 事件订阅入口（模板方法）
     *
     * 子类覆写此方法，调用 observeEvent() / observeEventSticky() 注册感兴趣的事件。
     * 由 BaseActivity.onCreate() 自动调用，无需手动触发。
     * 观察者与 Activity 生命周期绑定，销毁时自动移除，无需手动注销。
     *
     * 示例：
     *   override fun observeLiveBus() {
     *       observeEvent<String>(EventBus.BOOKSHELF_REFRESH) { refreshBookshelf() }
     *   }
     */
    open fun observeLiveBus() {
    }

    protected fun refreshReadAloudMiniBar() {
        readAloudMiniBarController?.refresh()
    }

    protected fun hideReadAloudMiniBar() {
        readAloudMiniBarController?.hide()
    }

    open override fun showReadAloudMiniBar(): Boolean = AppConfig.readAloudFloatingUi

    open override fun lockReadAloudMiniBarPosition(): Boolean = false

    open override fun readAloudMiniBarBottomMarginDp(): Int = 76

    open override fun defaultReadAloudMiniBarColor(): Int = 0xFF665185.toInt()

    open override fun onReadAloudMiniBarClick() {
        BaseReadAloudService.activeBookUrl?.let { bookUrl ->
            startActivity<ReadBookActivity> {
                putExtra("bookUrl", bookUrl)
            }
        } ?: ReadBook.book?.let { book ->
            startActivity<ReadBookActivity> {
                putExtra("bookUrl", book.bookUrl)
            }
        } ?: startActivity<ReadAloudActivity>()
    }

    open override fun onReadAloudMiniBarLongClick(): Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = try {
        super.dispatchTouchEvent(ev)
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        false
    }

    override fun finish() {
        currentFocus?.hideSoftInput()
        super.finish()
    }
}
