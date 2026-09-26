@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.graphics.Outline
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.get
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.os.Build
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.ClipboardImportMode
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.databinding.DialogClipboardImportBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.ThemeTransition
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.bookshelf.style2.BookshelfFragment2
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.homepage.HomepageFragment
import io.legado.app.ui.main.my.MyFragment
import io.legado.app.ui.main.rss.RssFragment
import io.legado.app.ui.widget.StableLiquidGlassView
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.isCreated
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.DevicePerformanceUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.help.update.AppUpdate
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.utils.StringUtils
import io.legado.app.utils.clearClip
import io.legado.app.utils.getClipText
import kotlin.time.Duration.Companion.hours

/**
 * 主界面
 */
@Suppress("PrivatePropertyName")
class MainActivity :
    VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener,
    MainViewModel.CallBack {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    override fun showReadAloudMiniBar(): Boolean = true

    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idBookshelf2 = 12
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private val idHomepage = 4
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 5
    private val EXIT_INTERVAL = 2000L
    private val realPositions = arrayOf(idHomepage, idBookshelf, idExplore, idRss, idMy)
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private var onUpBooksBadgeView: BadgeView? = null
    private var bottomNavigationConfigSignature: String? = null
    private var bottomNavigationInset = 0

    /** 背景图签名缓存，配合 [currentBackgroundSignature] 避免每次 onResume 重复解码 */
    private var backgroundImageSignature: String? = null

    /** 背景是否至少应用过一次（区分“尚未初始化”与“签名匹配跳过”） */
    private var backgroundImageApplied = false

    /** 主界面背景已消费的主题切换代数，见 [ThemeTransition.generation]（重建后实例重置） */
    private var contentTransitionGeneration = -1

    /** 底栏已消费的主题切换代数，见 [ThemeTransition.generation] */
    private var bottomBarTransitionGeneration = -1

    private fun bookshelfPosition(): Int = realPositions.indexOf(idBookshelf)

    private fun fragmentIdToMenuItemId(fragmentId: Int): Int = when (fragmentId) {
        idBookshelf, idBookshelf1, idBookshelf2 -> R.id.menu_bookshelf
        idHomepage -> R.id.menu_homepage
        idExplore -> R.id.menu_discovery
        idRss -> R.id.menu_rss
        idMy -> R.id.menu_my_config
        else -> R.id.menu_bookshelf
    }

    /**
     * 重写背景更新方法，在父类异步加载完成背景后，将相同背景同步到 content_container。
     *
     * 原因：底栏的玻璃/磨砂效果（[StableLiquidGlassView]）通过 [LiquidGlass] 采样
     * content_container 的像素来实现实时模糊。而 [LiquidGlass] 的采样机制是调用
     * `content_container.draw(canvas)`，只绘制该 View 及其子 View 的内容，
     * 不包含父级 decorView 的背景。因此如果背景仅设置在 decorView 上，
     * 采样区域在无 Fragment 内容覆盖处（如列表底部空白区域）得到的是透明像素，
     * 导致玻璃/磨砂效果不明显——无论不透明度设为多少，底栏下的文字都清晰可见。
     *
     * 将背景同步到 content_container 后，采样时能采到背景图的像素，
     * 玻璃/磨砂的折射和模糊效果即可正常显现，同时不影响沉浸式导航栏行为
     * （decorView 背景仍然覆盖全屏包括导航栏区域，content_container 背景仅在其自身范围内）。
     *
     * 约束（维护者注意）：本方法会**无条件**覆盖 content_container 的背景，
     * 因此 content_container 不得被其他逻辑单独设置背景（如分屏底色、特殊页面底色等），
     * 否则会被此处静默覆盖。当前代码中 content_container 仅作为 LiquidGlass 的采样源，
     * 不存在其他背景设置，故安全。若未来需要为 content_container 设独立背景，
     * 应改为在此处合并而非覆盖，或改用其他容器作为采样源。
     *
     * 其他注意点：
     * - 背景图解码在子线程异步执行，回调 [onBackgroundDrawableLoaded] 在主线程触发后
     *   才同步 content_container；decode 失败或无背景图配置时 drawable 为 null，
     *   此时 content_container 回退为**主题纯色底**而非置空——采样源若透明，
     *   玻璃/磨砂在纯色背景下完全失效（无像素可供折射/模糊）。
     * - mutate() 仅克隆 Drawable 状态对象，底层像素（bitmap/constantState）仍与 decorView
     *   共享，不会因双份背景导致像素内存翻倍，仅额外占用一份轻量状态对象。
     */
    override fun upBackgroundImage() {
        // 签名缓存：主题/背景路径/文件变更/模糊值均未变化时直接跳过，
        // 避免每次从其它界面返回 onResume 时都在主线程重新解码整张壁纸并高斯模糊。
        val signature = currentBackgroundSignature()
        if (backgroundImageApplied && backgroundImageSignature == signature) {
            return
        }
        backgroundImageSignature = signature
        // 注意：配置了背景图时解码是异步的，super 返回时背景尚未生效，
        // content_container 的同步统一在 onBackgroundDrawableLoaded 回调中完成；
        // 未配置背景图（纯色底）时 super 同步回调，使其与底栏在同一帧进入过渡
        super.upBackgroundImage()
    }

    override fun onBackgroundDrawableLoaded(drawable: Drawable?) {
        super.onBackgroundDrawableLoaded(drawable)
        // 将加载完成的背景同步到 content_container
        // 使用 constantState?.newDrawable()?.mutate() 创建独立副本，
        // 避免两个 View 共享同一 Drawable 状态导致绘制冲突
        // 注意：此处会无条件覆盖 content_container 背景，详见上方约束说明
        // 无背景图时回退主题纯色底：LiquidGlass 只能采到 content_container 自身
        // 的绘制内容，背景置 null 会使采样源透明，玻璃/磨砂效果随之失效
        val target = drawable?.constantState?.newDrawable()?.mutate()
            ?: backgroundColor.toDrawable()
        binding.contentContainer.background = themeContentBackground(target)
        backgroundImageApplied = true
    }

    /**
     * 主界面背景（content_container）的落地。
     *
     * 日/夜切换时与底栏共用 [ThemeTransition] 的时长与表现做交叉淡化：两者的数据来源与刷新时机
     * 本不相同（底栏色值读自 ThemeStore 同步可得，背景可能来自异步解码回调），若各自硬切换，
     * 底栏瞬变、背景随后才变，观感上就是割裂。
     *
     * 起点层按上一轮的实际形态取：上一轮是纯色就重建 ColorDrawable，是背景图就
     * 从进程级缓存取那张图（[ThemeConfig.getCachedBgImage] 返回共享像素的副本，
     * 不会多占一张整屏位图；且动画结束即换成终点 Drawable，不长期持有旧图）。
     * 两者都取不到时保持原有的直接切换。
     */
    private fun themeContentBackground(target: Drawable): Drawable {
        val signature = currentBackgroundSignature()
        val toColor = (target as? ColorDrawable)?.color
        ThemeTransition.rememberContentBackground(signature, toColor)
        val overlay = binding.contentContainer
        val enteredNewGeneration = contentTransitionGeneration != ThemeTransition.generation
        contentTransitionGeneration = ThemeTransition.generation
        if (!enteredNewGeneration) {
            // 同一轮过渡内再次落地（配置了背景图时会先落地占位图、再落地解码结果）：
            // 只换终点层，让过渡从占位图接着走到新背景，而不是被这次落地打断
            if (ThemeTransition.updateCrossFadeTarget(overlay, target)) {
                return overlay.background ?: target
            }
            return target
        }
        val startSignature = ThemeTransition.contentTransitionStartSignature
        val startColor = ThemeTransition.contentTransitionStartColor
        val unchanged = startSignature == signature &&
            (signature != null || startColor == toColor)
        if (unchanged) return target
        val from = startSignature?.let { ThemeConfig.getCachedBgImage(it) }
            ?: startColor?.toDrawable()
            ?: return target
        return ThemeTransition.crossFade(overlay, from, target)
    }

    /**
     * 计算当前主题背景的签名，统一委托 [ThemeConfig.getBackgroundSignature]，
     * 与 BaseActivity 的进程级背景缓存使用同一口径；未配置背景图时为 null。
     */
    private fun currentBackgroundSignature(): String? = ThemeConfig.getBackgroundSignature(this)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 清理已销毁 Fragment 的引用，避免 fragmentMap 持有导致内存泄漏
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(fm: FragmentManager, fragment: Fragment) {
                    fragmentMap.entries.removeIf { it.value === fragment }
                }
            },
            true,
        )
        upBottomMenu()
        initView()
        upHomePage()
        // setCurrentItem 到 position 0 时不会触发 onPageSelected 回调，
        // 需要显式更新底部导航栏的选中状态
        val position = binding.viewPagerMain.currentItem
        val menuItemId = fragmentIdToMenuItemId(realPositions[position])
        binding.bottomNavigationView.menu.findItem(menuItemId)?.isChecked = true
        onBackPressedDispatcher.addCallback(this) {
            val bsPos = bookshelfPosition()
            if (pagePosition != bsPos) {
                binding.viewPagerMain.currentItem = bsPos
                return@addCallback
            }
            (fragmentMap[getFragmentId(bsPos)] as? BookshelfFragment2)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                if (BaseReadAloudService.pause) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            // 隐私协议
            if (!privacyPolicy()) return@launch
            // 版本更新
            upVersion()
            // 设置本地密码
            setLocalPassword()
            notifyAppCrash()
            // 备份同步
            backupSync()
            // 设置回调
            viewModel.setActivityCallback(this@MainActivity)
            // 自动更新书源
            binding.viewPagerMain.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            // 自动更新书籍（recreate 走重启路径时标记通过 intent 传递，见 recreate()）
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook")
                ?: intent.getBooleanExtra("isAutoRefreshedBook", false)
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                // 每次进入书架后5秒自动更新书籍目录
                binding.viewPagerMain.postDelayed(5000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(bookshelfPosition(), false)

            R.id.menu_homepage ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idHomepage), false)

            R.id.menu_discovery ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_rss ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)

            R.id.menu_my_config ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
        return false
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(bookshelfPosition())] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[idExplore] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.setEdgeEffectColor(primaryColor)
        // offscreenPageLimit 设为 4，确保 5 个 Tab 互相切换时 Fragment 都不会被销毁重建。
        // 之前值为 3 时，从 position 4（我的）切到 position 0（书架）距离为 4 超过预加载范围，
        // 导致书架 Fragment 被销毁重建，重新走 upGroup 流程产生分组闪烁。
        viewPagerMain.offscreenPageLimit = 4
        viewPagerMain.adapter = adapter
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        refreshBottomNavigationConfig(force = true)
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
        }
        bottomNavigationGlass.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            bottomNavigationInset = windowInsets.navigationBarHeight
            view.bottomPadding = 0
            refreshBottomNavigationConfig(force = true)
            windowInsets
        }
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@sc
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            if (AppConfig.autoUpdateVariant) {
                if (LocalConfig.lastCheckUpdate + 24.hours.inWholeMilliseconds < System.currentTimeMillis()) {
                    AppUpdate.giteeUpdate.check(lifecycleScope)
                        .onSuccess {
                            showDialogFragment(
                                UpdateDialog(it),
                            )
                        }
                    LocalConfig.lastCheckUpdate = System.currentTimeMillis()
                }
            }
            block.resume(null)
            return@sc
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("web/help/md/updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD, "updateLog")
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 设置本地密码
     */
    private suspend fun setLocalPassword() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.password != null) {
            block.resume(null)
            return@sc
        }
        alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            onDismiss {
                block.resume(null)
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton {
                LocalConfig.password = ""
            }
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!AppConfig.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        releaseBottomNavigationGlassSampling()
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * App 已在前台时，外部应用通过 intent 跳入本界面（singleTask）只回调此处，
     * 不经过 onStart，需显式标记为"从外部进入"，确保外部分享的口令能被识别。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LifecycleHelp.markEnteredFromExternal()
    }

    override fun onResume() {
        super.onResume()
        // 口令识别只在从 App 外部进入主界面时进行（冷启动、后台切回、外部应用跳入），
        // 应用内子界面返回不再读取剪贴板，避免每次回到主界面都弹窗打扰。
        if (LifecycleHelp.consumeEnteredFromExternal()) {
            readShibboleth()
        }
        // 用户从设置页返回时，RECREATE 事件可能未送达后台的 Activity，
        // 或 recreate() 可能被 upSort() 异常阻断。
        // 在 onResume 中直接刷新背景图片，确保主题背景变更立即生效。
        upBackgroundImage()
        // 后台期间积压的 RECREATE 在此重建，保证只在本页回到前台时发生一次
        if (recreateOnResume) {
            recreateOnResume = false
            recreate()
        }
        // 签名缓存已修复内置配置时间戳问题，无需 force = true；
        // 若配置确实变更，签名不同会自动触发刷新；若未变更则跳过，避免重复重建。
        refreshBottomNavigationConfig(force = false)
    }

    override fun onStop() {
        super.onStop()
        // Activity 不可见时释放玻璃采样视图，避免持续采样整页内容
        releaseBottomNavigationGlassSampling()
    }

    /**
     * 释放底栏玻璃视图的采样源，停止实时模糊采样以节省 CPU/GPU 资源。
     * 在 Activity 不可见或销毁时调用。
     *
     * 注意：此处不能清空 [bottomNavigationConfigSignature] 配置签名缓存。
     * 配置本身并未变化，若清空会导致每次从其它界面返回时都触发
     * [applyNavigationBarPackage] 全量重建（自定义图标、布局、阴影、
     * Fragment 底部内边距、LiquidGlass 重建等），正是“设置底栏图标后
     * 回主界面卡顿”的来源。恢复可见时应由 [refreshBottomNavigationConfig]
     * 在签名一致的分支调用 [restoreBottomNavigationSamplingIfNeeded]，
     * 仅异步恢复采样源而跳过整包重建。
     */
    private fun releaseBottomNavigationGlassSampling() {
        if (!binding.bottomNavigationGlassView.isReleased()) {
            binding.bottomNavigationGlassView.release()
        }
    }

    /**
     * 后台收到 RECREATE 时置位，回到前台 onResume 时再重建。
     * 避免应用主题时本页与前台页面在同一主线程并发重建窗口（见 docs/archive/主题列表应用主题后UI卡死根因分析）。
     */
    private var recreateOnResume = false

    /** 一次重建只放行一个请求，避免同一实例周期内多次触发叠加 */
    private var recreatePending = false

    /**
     * 主界面重建统一走「清任务 + 全新启动」（theme-styles.md §7.8.1 强制）。
     *
     * 原地 `super.recreate()` 建立的新窗口里，Compose 实例首帧组合后重组/重绘调度即冻结
     * （证据链见 docs/archive/主题列表应用主题后UI卡死根因分析.md §15）：书架与首页列表
     * 现在都是 Compose，切书架布局配置后列表停在空态就是该缺陷的变现；View 侧不受影响，
     * 所以书架还是 RecyclerView 时问题一直不可见。全新 startActivity 建立的窗口全链路健康。
     *
     * 本页是 singleTask，实例还在任务栈里时直接 startActivity 只会回调本实例的
     * onNewIntent，必须带 FLAG_ACTIVITY_CLEAR_TASK 才能真正建出新窗口。
     */
    override fun recreate() {
        if (recreatePending || isFinishing || isDestroyed) return
        recreatePending = true
        instanceCreateTime = System.currentTimeMillis()
        startActivity(
            Intent(this, MainActivity::class.java)
                // 旧实例已做过自动更新目录，重启后不再重复（等价于原来 recreate 保留的
                // savedInstanceState 标记，见 onPostCreate）
                .putExtra("isAutoRefreshedBook", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        finish()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            if (onUpBooksBadgeView == null) {
                val menu = binding.bottomNavigationView.menu
                var bookshelfItemIndex = 0
                for (i in 0 until menu.size()) {
                    if (menu[i].itemId == R.id.menu_bookshelf) {
                        bookshelfItemIndex = i
                        break
                    }
                }
                onUpBooksBadgeView = binding.bottomNavigationView.addBadgeView(bookshelfItemIndex)
            }
            onUpBooksBadgeView!!.setBadgeCount(it)
        }
        observeEvent<String>(EventBus.RECREATE) {
            // 本实例刚由重启建立 → 该广播是同一次操作的迟到重复（如 ThemeConfig 的防抖广播），
            // 放行会在新窗口建立过程中触发二次重启
            if (System.currentTimeMillis() - instanceCreateTime < RECREATE_IGNORE_MS) {
                return@observeEvent
            }
            if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                // 前台：直接刷新背景并重建（即使 recreate 失败或被跳过也能生效）
                upBackgroundImage()
                recreate()
            } else {
                // 后台：延后到 onResume 重建，避免与前台页面并发重建窗口
                recreateOnResume = true
            }
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                if (it) {
                    onUpBooksBadgeView = null
                }
                upBottomMenu()
                if (it) {
                    upHomePage()
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
        // 底栏配置变更时，重新应用底栏配置
        observeEvent<Boolean>(EventBus.NAVIGATION_BAR_CHANGED) { isNightMode ->
            if (isNightMode == AppConfig.isNightTheme) {
                refreshBottomNavigationConfig(force = true)
            }
        }
    }

    private fun upBottomMenu() {
        val showHomepage = AppConfig.showHomepage
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        val navOrder = AppConfig.navItemOrder

        val menu = binding.bottomNavigationView.menu
        menu.clear()

        var index = 0
        for (itemKey in navOrder) {
            when (itemKey) {
                "homepage" -> if (showHomepage) {
                    realPositions[index] = idHomepage
                    menu.add(0, R.id.menu_homepage, index, R.string.homepage)
                        .setIcon(R.drawable.ic_bottom_home)
                    index++
                }

                "bookshelf" -> {
                    realPositions[index] = idBookshelf
                    menu.add(0, R.id.menu_bookshelf, index, R.string.bookshelf)
                        .setIcon(R.drawable.ic_bottom_books)
                    index++
                }

                "explore" -> if (showDiscovery) {
                    realPositions[index] = idExplore
                    menu.add(0, R.id.menu_discovery, index, R.string.discovery)
                        .setIcon(R.drawable.ic_bottom_explore)
                    index++
                }

                "rss" -> if (showRss) {
                    realPositions[index] = idRss
                    menu.add(0, R.id.menu_rss, index, R.string.rss)
                        .setIcon(R.drawable.ic_bottom_rss_feed)
                    index++
                }

                "my" -> {
                    realPositions[index] = idMy
                    menu.add(0, R.id.menu_my_config, index, R.string.my)
                        .setIcon(R.drawable.ic_bottom_person)
                    index++
                }
            }
        }
        bottomMenuCount = index

        // 根据当前 ViewPager 位置设置底部导航栏选中状态
        val currentPosition = binding.viewPagerMain.currentItem.coerceAtMost(bottomMenuCount - 1)
        val menuItemId = fragmentIdToMenuItemId(realPositions[currentPosition])
        menu.findItem(menuItemId)?.isChecked = true

        // 应用底栏导航栏配置（布局模式、效果、背景色、透明度、自定义图标）
        applyNavigationBarPackage()

        adapter.notifyDataSetChanged()
    }

    private fun upHomePage() {
        // 根据 defaultHomePage 找到对应的 ViewPager 位置
        val defaultHome = AppConfig.defaultHomePage
        val fragmentId = when (defaultHome) {
            "homepage" -> idHomepage
            "explore" -> idExplore
            "rss" -> idRss
            "my" -> idMy
            else -> idBookshelf
        }
        val position = realPositions.indexOf(fragmentId)
        if (position >= 0) {
            binding.viewPagerMain.setCurrentItem(position, false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1
        }
        return id
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
            val fragmentId = realPositions[position]
            val menuItemId = fragmentIdToMenuItemId(fragmentId)
            binding.bottomNavigationView.menu.findItem(menuItemId)?.isChecked = true
        }
    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private fun getId(position: Int): Int = getFragmentId(position)

        override fun getItemPosition(any: Any): Int {
            val position = (any as MainFragmentInterface).position
                ?: return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idBookshelf1 && any is BookshelfFragment1) ||
                (fragmentId == idBookshelf2 && any is BookshelfFragment2) ||
                (fragmentId == idHomepage && any is HomepageFragment) ||
                (fragmentId == idExplore && any is ExploreFragment) ||
                (fragmentId == idRss && any is RssFragment) ||
                (fragmentId == idMy && any is MyFragment)
            ) {
                return POSITION_UNCHANGED
            }
            return POSITION_NONE
        }

        override fun getItem(position: Int): Fragment = when (getId(position)) {
            idBookshelf1 -> BookshelfFragment1(position)
            idBookshelf2 -> BookshelfFragment2(position)
            idHomepage -> HomepageFragment(position)
            idExplore -> ExploreFragment(position)
            idRss -> RssFragment(position)
            else -> MyFragment(position)
        }

        override fun getCount(): Int = bottomMenuCount

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            return fragment
        }
    }

    override fun openImportUi(type: Int, source: String) {
        when (type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(source),
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(source),
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(source),
            )
        }
    }

    // ── 底栏导航栏配置 Shell 实现 ──

    /**
     * 计算底栏占用的总高度（导航栏高度 + 底部边距），
     * 用于设置 Fragment 内容的底部内边距，防止底栏遮挡内容。
     */
    fun mainContentBottomPadding(): Int {
        val bottomNav = binding.bottomNavigationGlass
        val layoutParams = bottomNav.layoutParams as? FrameLayout.LayoutParams
        val navHeight = bottomNav.height.takeIf { it > 0 } ?: bottomNav.minimumHeight
        val bottomMargin = layoutParams?.bottomMargin ?: 0
        return navHeight + bottomMargin
    }

    /**
     * 遍历所有 Fragment，通知它们更新底部内边距以适配底栏高度。
     */
    private fun refreshMainContentBottomPadding() {
        val bottomPadding = mainContentBottomPadding()
        fragmentMap.values.forEach { fragment ->
            if (fragment.view != null) {
                (fragment as? MainFragmentInterface)?.updateMainBottomPadding(bottomPadding)
            }
        }
    }

    /**
     * 刷新底栏配置（带签名缓存，避免重复刷新）
     *
     * 签名缓存机制：比较当前底栏配置的签名摘要，仅在配置实际变化时才重新应用。
     * 对于内置配置，updatedAt 固定为 0L，确保每次 loadConfigs 不会产生新签名。
     */
    private fun refreshBottomNavigationConfig(force: Boolean = false) {
        val signature = NavigationBarConfig.currentSignature(this, AppConfig.isNightTheme)
        if (!force && bottomNavigationConfigSignature == signature) {
            // 配置未变化：仅恢复可能已被 onStop 释放的玻璃采样，跳过整包重建
            restoreBottomNavigationSamplingIfNeeded()
            return
        }
        bottomNavigationConfigSignature = signature
        applyNavigationBarPackage()
    }

    /**
     * 底栏配置未变化时，异步恢复被 onStop 释放的玻璃/磨砂采样源。
     *
     * 回到主界面时配置签名一致即可复用既有底栏（自定义图标、布局均未被破坏），
     * 只需重新 [StableLiquidGlassView.bind] 采样源。使用 post 推迟到布局帧后
     * 执行，避免在 onResume 同步重建采样造成首帧卡顿。
     */
    private fun restoreBottomNavigationSamplingIfNeeded() {
        binding.run {
            val glassView = bottomNavigationGlassView
            if (!glassView.isReleased()) return@run
            val config = NavigationBarConfig.activeConfig(this@MainActivity, AppConfig.isNightTheme)
            if (!usesRealtimeGlassShell(config)) return@run
            glassView.visible()
            val bgColor = resolveNavigationBarBackground(config)
            val cornerRadius = if (config.layoutMode == NavigationBarConfig.LAYOUT_FLOATING) 24f.dpToPx() else 0f
            glassView.post {
                if (isFinishing || isDestroyed) return@post
                if (bottomNavigationGlassView.isReleased() && bottomNavigationGlassView.isAttachedToWindow) {
                    setupBottomLiquidGlass(bottomNavigationGlassView, config, cornerRadius, bgColor)
                }
            }
        }
    }

    /**
     * 应用底栏导航栏配置包：背景色、自定义图标、布局 Shell（浮动/标准/侧栏 + 玻璃/磨砂/实色）
     *
     * 性能优化：
     * - 低性能设备自动将玻璃/磨砂降级为静态材质背景，避免持续采样整页内容
     * - 高性能设备保留实时玻璃效果
     */
    private fun applyNavigationBarPackage() = binding.run {
        val config = NavigationBarConfig.activeConfig(this@MainActivity, AppConfig.isNightTheme)
        val bgColor = resolveNavigationBarBackground(config)
        val hasCustomIcons = NavigationBarConfig.applyToMenu(
            bottomNavigationView.menu,
            this@MainActivity,
            AppConfig.isNightTheme,
            bgColor,
        )
        if (hasCustomIcons) {
            bottomNavigationView.itemIconTintList = null
        } else {
            bottomNavigationView.restoreThemeIconTint(bgColor)
        }
        bottomNavigationView.itemBackground = Color.TRANSPARENT.toDrawable()
        applyBottomNavigationShell(config, bgColor)
        val textIsDark = ColorUtils.isColorLight(bgColor)
        bottomNavigationView.itemTextColor = io.legado.app.lib.theme.Selector.colorBuild()
            .setDefaultColor(getSecondaryTextColor(textIsDark))
            .setSelectedColor(accentColor)
            .create()
        bottomNavigationView.post {
            applyBottomNavigationSelectedIndicator()
            refreshMainContentBottomPadding()
        }
    }

    /** 解析底栏背景色：内置配置用主题色+配置透明度，自定义配置用配置中的颜色+透明度 */
    private fun resolveNavigationBarBackground(config: NavigationBarConfig): Int {
        val baseColor = if (config.isBuiltin) {
            bottomBackground
        } else if (AppConfig.isNightTheme) {
            getPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.default_night_bottom_background))
        } else {
            getPrefInt(PreferKey.cBBackground, getCompatColor(R.color.default_bottom_background))
        }
        return NavigationBarConfig.resolveBottomColor(baseColor, config)
    }

    /** 解析底栏边框颜色 */
    private fun resolveBottomNavigationBorderColor(config: NavigationBarConfig): Int? = config.borderColor?.let {
        if (Color.alpha(it) == 0) {
            Color.TRANSPARENT
        } else {
            ColorUtils.withAlpha(it, config.borderAlpha.coerceIn(0, 100) / 100f)
        }
    }

    /**
     * 应用底栏 Shell：根据布局模式（浮动/标准/侧栏）和效果模式（实色/玻璃/磨砂）
     * 设置容器尺寸、边距、圆角、阴影和背景 Drawable
     *
     * 性能优化：
     * - 低性能设备（旧系统/低内存）自动降级为静态材质背景，避免实时采样
     * - 高性能设备保留实时玻璃效果，但合并参数刷新为单次操作
     * - 切换为实色模式或标准布局时及时释放采样视图
     */
    private fun applyBottomNavigationShell(config: NavigationBarConfig, bgColor: Int) = binding.run {
        val floating = config.layoutMode == NavigationBarConfig.LAYOUT_FLOATING
        val standard = config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD
        val horizontalMargin = if (floating) 20.dpToPx() else 0
        val bottomMargin = if (floating) 10.dpToPx() + bottomNavigationInset else 0
        val shellHeight = if (floating) 48.dpToPx() else 50.dpToPx() + if (standard) bottomNavigationInset else 0
        bottomNavigationGlass.layoutParams = (bottomNavigationGlass.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = shellHeight
            gravity = Gravity.BOTTOM
            setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin)
        }
        bottomNavigationView.layoutParams = (bottomNavigationView.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
            setMargins(0, 0, 0, 0)
        }
        bottomNavigationGlass.minimumHeight = shellHeight
        bottomNavigationView.minimumHeight = if (floating) 48.dpToPx() else 50.dpToPx()
        bottomNavigationView.itemIconSize = if (floating) 23.dpToPx() else 22.dpToPx()
        bottomNavigationView.setPadding(
            if (floating) 6.dpToPx() else 0,
            0,
            if (floating) 6.dpToPx() else 0,
            if (standard) bottomNavigationInset else 0,
        )
        bottomNavigationView.alpha = 1f
        bottomNavigationView.elevation = 0f
        // 阴影 alpha 随不透明度变化：底栏越透明阴影越淡，高不透明度时完整保留深度感。
        // Android elevation 阴影颜色固定为深色，不受背景透明度影响。
        // 低不透明度时背景近乎透明，深色阴影直接投射在页面内容上对比度反而更高，
        // 所以必须控制阴影本身的 alpha，而非仅缩放 elevation 值。
        val opacityFactor = config.opacity.coerceIn(0, 100) / 100f
        val baseElevation = when (config.effectMode) {
            NavigationBarConfig.EFFECT_SOLID -> 8.dpToPx().toFloat()
            NavigationBarConfig.EFFECT_FROSTED -> 14.dpToPx().toFloat()
            else -> 12.dpToPx().toFloat()
        }
        if (floating && opacityFactor > 0f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+：保留固定 elevation 提供阴影形状，通过阴影颜色 alpha 精确控制强度
                bottomNavigationGlass.elevation = baseElevation
                val shadowAlpha = (opacityFactor * 255).toInt()
                bottomNavigationGlass.outlineSpotShadowColor =
                    Color.argb(shadowAlpha, 0, 0, 0)
                bottomNavigationGlass.outlineAmbientShadowColor =
                    Color.argb(shadowAlpha, 0, 0, 0)
            } else {
                // API < 28：无法控制阴影颜色，用平方缩放让低不透明度时阴影更快消失
                bottomNavigationGlass.elevation = baseElevation * opacityFactor * opacityFactor
            }
        } else {
            bottomNavigationGlass.elevation = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bottomNavigationGlass.outlineSpotShadowColor = Color.TRANSPARENT
                bottomNavigationGlass.outlineAmbientShadowColor = Color.TRANSPARENT
            }
        }
        bottomNavigationView.setBackgroundColor(Color.TRANSPARENT)
        bottomNavigationView.background = Color.TRANSPARENT.toDrawable()
        val liquid = usesRealtimeGlassShell(config)
        applyBottomNavigationGlassOutline(bottomNavigationGlass, if (floating) 24f.dpToPx() else 0f)
        if (liquid) {
            // 玻璃/磨砂效果：保留液态玻璃视图，即使不透明度为 0 也能反映页面滑动的折射/模糊效果
            bottomNavigationGlassView.visible()
            setupBottomLiquidGlass(bottomNavigationGlassView, config, if (floating) 24f.dpToPx() else 0f, bgColor)
            // 不透明度为 0 时 shell overlay 设为透明，避免静态底色/描边残留（如 strokeAlpha 基底 0.22）；
            // 液态玻璃视图本身的折射/模糊效果不受影响，仍能反映页面滑动
            bottomNavigationShellOverlay.background = themeBottomBarBackground(config, floating, bgColor)
        } else {
            // 非实时玻璃（实色效果或低性能设备降级）：释放采样视图以停止持续采样
            if (!bottomNavigationGlassView.isReleased()) {
                bottomNavigationGlassView.release()
            }
            bottomNavigationGlassView.invisible()
            // 不透明度为 0 时无静态背景，确保完全透明；
            // 否则用静态玻璃材质 Drawable 替代实时模糊（低性能设备降级）
            bottomNavigationShellOverlay.background = themeBottomBarBackground(config, floating, bgColor)
        }
    }

    /**
     * 底栏背景的落地。
     *
     * 与主界面背景封装成同一套过渡：日/夜切换时用 [ThemeTransition.bottomBarTransitionStartColor]
     * （切换前记录的旧颜色）重建「旧外观」作为起始层，与新外观在同一时长内交叉淡化，
     * 避免底栏瞬变而背景滞后一拍。
     *
     * 非主题切换（如拖动不透明度/圆角等配置）走代数判断直接落地，避免高频过渡互相打断。
     */
    private fun themeBottomBarBackground(
        config: NavigationBarConfig,
        floating: Boolean,
        bgColor: Int,
    ): Drawable {
        val target = buildBottomBarShellDrawable(config, floating, bgColor)
        val fromColor = ThemeTransition.bottomBarTransitionStartColor
        ThemeTransition.rememberBottomBarColor(bgColor)
        val enteredNewGeneration = bottomBarTransitionGeneration != ThemeTransition.generation
        bottomBarTransitionGeneration = ThemeTransition.generation
        if (!enteredNewGeneration) {
            // 同一轮过渡内再次落地（首个 insets 回调会让底栏内边距由 0 变为真实值，从而整包重建一次）：
            // 只把进行中的交叉淡化换上新的终点层，否则刚起步的过渡会被这次落地当场打断
            val overlay = binding.bottomNavigationShellOverlay
            if (ThemeTransition.updateCrossFadeTarget(overlay, target)) {
                // 返回当前的包装 Drawable；赋值同一实例不会重启动画
                return overlay.background ?: target
            }
            return target
        }
        if (fromColor == null || fromColor == bgColor) {
            return target
        }
        return ThemeTransition.crossFade(
            binding.bottomNavigationShellOverlay,
            buildBottomBarShellDrawable(config, floating, fromColor),
            target,
        )
    }

    /**
     * 判断是否使用实时液态玻璃：用户配置为玻璃/磨砂 且 非标准布局 且 设备支持实时模糊。
     *
     * 判定被「整包应用」与「构建 Shell 背景」共用，收敛成一处，避免两条路径的条件漂移
     * 导致同一配置下算出的外观不一致。
     */
    private fun usesRealtimeGlassShell(config: NavigationBarConfig): Boolean =
        config.layoutMode != NavigationBarConfig.LAYOUT_STANDARD &&
            config.effectMode != NavigationBarConfig.EFFECT_SOLID &&
            DevicePerformanceUtils.supportsRealtimeGlass

    /**
     * 构建底栏 Shell 背景 Drawable，与 [applyBottomNavigationShell] 的落地分支一一对应。
     * 独立成方法是因为过渡需要用「上一轮颜色」重建旧外观作为交叉淡化的起始层。
     */
    private fun buildBottomBarShellDrawable(
        config: NavigationBarConfig,
        floating: Boolean,
        bgColor: Int,
    ): Drawable {
        val opacityFactor = config.opacity.coerceIn(0, 100) / 100f
        if (opacityFactor <= 0f) {
            // 完全透明时无静态背景，避免静态底色/描边残留（如 strokeAlpha 基底 0.22）
            return Color.TRANSPARENT.toDrawable()
        }
        return if (usesRealtimeGlassShell(config)) {
            createLiquidGlassShellDrawable(
                glassLevel = opacityFactor,
                cornerRadius = if (floating) 24f.dpToPx() else 0f,
                effectMode = config.effectMode,
                bgColor = bgColor,
                strokeColor = resolveBottomNavigationBorderColor(config),
            )
        } else {
            createBottomNavigationShellDrawable(config, bgColor)
        }
    }

    /**
     * 配置液态玻璃视图的模糊参数。
     *
     * 玻璃效果：较小模糊半径 + 较高色散，产生清晰折射感。
     * 磨砂效果：较大模糊半径 + 较低色散，产生朦胧磨砂感。
     *
     * 性能优化：使用 [StableLiquidGlassView.beginBatchUpdate] / [endBatchUpdate]
     * 将 12+ 个参数设置合并为单次 updateParameters 调用，避免逐个 setter
     * 各自触发 GPU 参数上传。
     */
    private fun setupBottomLiquidGlass(
        liquidGlassView: StableLiquidGlassView,
        config: NavigationBarConfig,
        cornerRadius: Float,
        bgColor: Int,
    ) {
        val level = config.opacity.coerceIn(0, 100) / 100f
        val frosted = config.effectMode == NavigationBarConfig.EFFECT_FROSTED
        val tintColor = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        liquidGlassView.bind(binding.contentContainer)
        // 批量更新：合并所有参数设置为单次 updateParameters 调用
        liquidGlassView.beginBatchUpdate()
        liquidGlassView.setCornerRadius(cornerRadius)
        liquidGlassView.setRefractionHeight(if (frosted) 10f.dpToPx() else (14f + level * 10f).dpToPx())
        liquidGlassView.setRefractionOffset(if (frosted) 30f.dpToPx() else (42f + level * 18f).dpToPx())
        liquidGlassView.setBlurRadius(if (frosted) 22f + level * 20f else 8f + level * 14f)
        liquidGlassView.setDispersion(if (frosted) 0.06f else 0.24f + level * 0.24f)
        liquidGlassView.setTintAlpha(if (frosted) 0.012f + level * 0.268f else 0.004f + level * 0.156f)
        liquidGlassView.setTintColorRed(Color.red(tintColor) / 255f)
        liquidGlassView.setTintColorGreen(Color.green(tintColor) / 255f)
        liquidGlassView.setTintColorBlue(Color.blue(tintColor) / 255f)
        liquidGlassView.setDraggableEnabled(false)
        liquidGlassView.setElasticEnabled(false)
        liquidGlassView.setTouchEffectEnabled(false)
        liquidGlassView.endBatchUpdate()
    }

    /** 设置底栏容器的圆角裁剪 */
    private fun applyBottomNavigationGlassOutline(view: View, cornerRadius: Float) {
        view.clipToOutline = cornerRadius > 0f
        view.outlineProvider = if (cornerRadius > 0f) {
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        } else {
            ViewOutlineProvider.BOUNDS
        }
    }

    /** 创建玻璃/磨砂效果的 Shell 背景 Drawable（作为 StableLiquidGlassView 的补充层） */
    private fun createLiquidGlassShellDrawable(
        glassLevel: Float,
        cornerRadius: Float,
        effectMode: String,
        bgColor: Int,
        strokeColor: Int?,
    ): GradientDrawable {
        val baseColor = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        val isLight = ColorUtils.isColorLight(baseColor)
        val neutralSurface = if (isLight) Color.WHITE else Color.rgb(22, 24, 28)
        val surfaceColor = ColorUtils.blendColors(
            baseColor,
            neutralSurface,
            if (effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.26f else 0.14f,
        )
        val fallbackBoost = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) 0.08f else 0f
        val frosted = effectMode == NavigationBarConfig.EFFECT_FROSTED
        val startAlpha = if (frosted) {
            (0.025f + glassLevel * 0.615f + fallbackBoost).coerceIn(0f, 0.76f)
        } else {
            (0.004f + glassLevel * 0.386f + fallbackBoost * 0.55f).coerceIn(0f, 0.48f)
        }
        val centerAlpha = if (frosted) {
            (0.018f + glassLevel * 0.462f + fallbackBoost * 0.65f).coerceIn(0f, 0.58f)
        } else {
            (0.003f + glassLevel * 0.267f + fallbackBoost * 0.35f).coerceIn(0f, 0.34f)
        }
        val endAlpha = if (frosted) {
            (0.012f + glassLevel * 0.348f + fallbackBoost * 0.45f).coerceIn(0f, 0.46f)
        } else {
            (0.002f + glassLevel * 0.198f + fallbackBoost * 0.30f).coerceIn(0f, 0.26f)
        }
        val strokeAlpha = if (frosted) {
            (0.20f + glassLevel * 0.16f).coerceIn(0f, 0.44f)
        } else {
            (0.22f + glassLevel * 0.18f).coerceIn(0f, 0.46f)
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, startAlpha),
                ColorUtils.withAlpha(surfaceColor, centerAlpha),
                ColorUtils.withAlpha(surfaceColor, endAlpha),
            ),
        ).apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setStroke(1.dpToPx(), strokeColor ?: ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }

    /** 创建实色/标准 Shell 背景 Drawable，非实色效果使用多层玻璃 Drawable */
    private fun createBottomNavigationShellDrawable(config: NavigationBarConfig, bgColor: Int): Drawable {
        val standard = config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD
        val radius = when {
            standard -> 0f
            else -> 24f.dpToPx()
        }
        val strokeColor = resolveBottomNavigationBorderColor(config)
        if (!standard && config.effectMode != NavigationBarConfig.EFFECT_SOLID) {
            return createBottomNavigationGlassDrawable(config, bgColor, radius, strokeColor)
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bgColor)
            setStroke(
                if (!standard && strokeColor != null) 1.dpToPx() else 0,
                strokeColor ?: Color.TRANSPARENT,
            )
        }
    }

    /** 创建多层玻璃/磨砂 Drawable（阴影 + 主体 + 雾气 + 高光 + 底部阴影 + 边框） */
    private fun createBottomNavigationGlassDrawable(
        config: NavigationBarConfig,
        bgColor: Int,
        radius: Float,
        strokeColor: Int?,
    ): Drawable {
        val opacityFactor = config.opacity.coerceIn(0, 100) / 100f
        val glassBase = glassBaseColor(bgColor, config.effectMode, opacityFactor)
        val body = roundedGradient(
            radius = radius,
            colors = bottomNavigationMaterialColors(glassBase, config.effectMode),
        )
        val mist = roundedGradient(
            radius = radius,
            colors = intArrayOf(
                Color.TRANSPARENT,
                adjustAlpha(
                    if (ColorUtils.isColorLight(glassBase)) Color.WHITE else Color.rgb(90, 110, 136),
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.34f else 0.08f,
                ),
                Color.TRANSPARENT,
            ),
        )
        val highlight = roundedGradient(
            radius = radius,
            colors = intArrayOf(
                adjustAlpha(
                    getCompatColor(R.color.glass_bar_highlight),
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.36f else 1.00f,
                ),
                adjustAlpha(Color.WHITE, opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.06f else 0.20f),
                Color.TRANSPARENT,
                adjustAlpha(
                    getCompatColor(R.color.glass_overlay),
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.18f else 0.72f,
                ),
            ),
        )
        val bottomShade = roundedGradient(
            radius = radius,
            colors = intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                adjustAlpha(
                    if (ColorUtils.isColorLight(glassBase)) Color.rgb(20, 34, 54) else Color.BLACK,
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.06f else 0.18f,
                ),
            ),
        )
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(
                1.dpToPx(),
                strokeColor ?: adjustAlpha(getCompatColor(R.color.glass_stroke), opacityFactor),
            )
        }
        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(adjustAlpha(getCompatColor(R.color.glass_bar_shadow), opacityFactor))
        }
        return LayerDrawable(arrayOf(shadow, body, mist, highlight, bottomShade, border)).apply {
            val shadowInset = 2.dpToPx()
            setLayerInset(0, shadowInset, 2.dpToPx(), shadowInset, 0)
            setLayerInset(1, 0, 0, 0, 1.dpToPx())
            setLayerInset(2, 2.dpToPx(), 1.dpToPx(), 2.dpToPx(), 3.dpToPx())
            setLayerInset(3, 1.dpToPx(), 1.dpToPx(), 1.dpToPx(), 2.dpToPx())
            setLayerInset(4, 1.dpToPx(), 2.dpToPx(), 1.dpToPx(), 1.dpToPx())
            setLayerInset(5, 0, 0, 0, 1.dpToPx())
        }
    }

    /** 计算玻璃/磨砂材质的基础颜色 */
    private fun glassBaseColor(bgColor: Int, effectMode: String, opacityFactor: Float): Int {
        val baseRgb = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        val light = ColorUtils.isColorLight(baseRgb)
        val materialTint = when (effectMode) {
            NavigationBarConfig.EFFECT_FROSTED -> if (light) Color.WHITE else Color.rgb(52, 58, 68)
            else -> getCompatColor(R.color.glass_bar)
        }
        val ratio = if (effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.34f else 0.18f
        val alpha = opacityFactor * if (effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.96f else 0.78f
        val rgb = ColorUtils.blendColors(baseRgb, materialTint, ratio)
        return ColorUtils.withAlpha(rgb, alpha.coerceIn(0f, 1f))
    }

    /** 创建圆角渐变 Drawable */
    private fun roundedGradient(radius: Float, colors: IntArray): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
    }

    /** 调整颜色的 Alpha 通道 */
    private fun adjustAlpha(color: Int, factor: Float): Int = ColorUtils.withAlpha(
        Color.rgb(Color.red(color), Color.green(color), Color.blue(color)),
        (Color.alpha(color) / 255f * factor).coerceIn(0f, 1f),
    )

    /** 根据效果模式生成底栏材质渐变色数组 */
    private fun bottomNavigationMaterialColors(bgColor: Int, effectMode: String): IntArray {
        val alpha = Color.alpha(bgColor) / 255f
        val rgb = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        return if (effectMode == NavigationBarConfig.EFFECT_FROSTED) {
            val frost = if (ColorUtils.isColorLight(rgb)) Color.WHITE else Color.rgb(62, 70, 82)
            intArrayOf(
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.98f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(frost, (alpha * 0.38f).coerceIn(0f, 1f)),
                    0.48f,
                ),
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.94f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(frost, (alpha * 0.25f).coerceIn(0f, 1f)),
                    0.36f,
                ),
                ColorUtils.withAlpha(rgb, (alpha * 0.86f).coerceIn(0f, 1f)),
            )
        } else {
            val highlight = if (ColorUtils.isColorLight(rgb)) Color.WHITE else Color.rgb(56, 74, 96)
            intArrayOf(
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.76f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(highlight, 0.34f),
                    0.58f,
                ),
                ColorUtils.withAlpha(rgb, (alpha * 0.56f).coerceIn(0f, 1f)),
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.40f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(Color.WHITE, 0.10f),
                    0.18f,
                ),
            )
        }
    }

    /** 清除选中项的默认背景指示器 */
    private fun applyBottomNavigationSelectedIndicator() = binding.run {
        val menuView = bottomNavigationView.getChildAt(0) as? ViewGroup ?: return@run
        val visibleItems = NavigationBarConfig.items
            .filter { bottomNavigationView.menu.findItem(it.menuId)?.isVisible == true }
        visibleItems.forEachIndexed { index, _ ->
            val child = menuView.getChildAt(index) ?: return@forEachIndexed
            child.background = Color.TRANSPARENT.toDrawable()
            child.setPadding(0, 3.dpToPx(), 0, 3.dpToPx())
        }
    }

    /**
     * 读取剪贴板中的导入口令。
     *
     * 触发时机由 [onResume] 收敛为「从 App 外部进入主界面」，应用内子界面返回不再打扰用户。
     *
     * 采用先探测后询问：先在本地判断剪贴板是否真含 `#L:` 口令，确认有才继续，
     * 否则静默返回。绝大多数情况下剪贴板内容与导入无关，这样可避免无谓弹窗。
     * 前台读取剪贴板无需权限，探测过程也不联网、不落库。
     *
     * 探测到口令后的行为由设置项 [PreferKey.clipboardImportMode] 决定：
     * - ASK：弹窗询问，可勾选「记住我的选择」把决定固化为 ALWAYS / NEVER
     * - ALWAYS：直接导入并 Toast 提示
     * - NEVER：不读取剪贴板
     */
    private fun readShibboleth() {
        binding.viewPagerMain.postDelayed(500) {
            val mode = AppConfig.clipboardImportMode
            if (mode == ClipboardImportMode.NEVER) return@postDelayed
            val text = try {
                this@MainActivity.getClipText()
            } catch (e: Exception) {
                e.printOnDebug()
                null
            }
            if (text.isNullOrBlank() || "#L:" !in text) return@postDelayed
            if (mode == ClipboardImportMode.ALWAYS) {
                // 自动导入是用户「记住此选择」后的静默行为，导入对话框会毫无预兆地弹出，
                // 用 Toast 说明来源，避免用户困惑于这个框从哪来
                toastOnUi(R.string.clipboard_import_auto_toast)
                importShibboleth(text)
            } else {
                showClipboardImportConfirm(text)
            }
        }
    }

    /**
     * 询问是否导入剪贴板中探测到的口令。
     *
     * 勾选「记住我的选择」后把本次决定写入 [PreferKey.clipboardImportMode] 长期生效；
     * 未勾选则只对本次生效。
     */
    private fun showClipboardImportConfirm(text: String) {
        val dialogBinding = DialogClipboardImportBinding.inflate(layoutInflater)
        alert(R.string.clipboard_import_confirm, R.string.clipboard_import_confirm_summary) {
            customView { dialogBinding.root }
            okButton {
                if (dialogBinding.cbRemember.isChecked) {
                    AppConfig.clipboardImportMode = ClipboardImportMode.ALWAYS
                }
                importShibboleth(text)
            }
            cancelButton {
                if (dialogBinding.cbRemember.isChecked) {
                    AppConfig.clipboardImportMode = ClipboardImportMode.NEVER
                }
            }
        }
    }

    /**
     * 解析剪贴板中的 #L: 导入口令并弹出对应的导入对话框
     */
    private fun importShibboleth(text: String) {
        try {
            this@MainActivity.clearClip() // 清理一下防重复
            val (url, type, customWord) = StringUtils.unShibboleth(text)
            when (type) {
                StringUtils.BOOK_SOURCE ->
                    showDialogFragment(ImportBookSourceDialog(url))
                StringUtils.RSS_SOURCE ->
                    showDialogFragment(ImportRssSourceDialog(url))
                StringUtils.DICT_RULE ->
                    showDialogFragment(ImportDictRuleDialog(url))
                StringUtils.REPLACE_RULE ->
                    showDialogFragment(ImportReplaceRuleDialog(url))
                StringUtils.TOC_RULE ->
                    showDialogFragment(ImportTxtTocRuleDialog(url))
                StringUtils.TTS_RULE ->
                    showDialogFragment(ImportHttpTtsDialog(url))
                else -> showDialogFragment(ImportHttpTtsDialog(url))
            }
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    companion object {

        /**
         * 重启后迟到的 RECREATE 广播宽限窗。
         *
         * 一次配置变更可能同时由事件总线与 ThemeConfig 防抖（1500ms）两条路触发，
         * 窗口略大于防抖延迟即可兜住迟到广播，保证一次变更只经历一次重启。
         */
        private const val RECREATE_IGNORE_MS = 2000L

        /**
         * 最近一次由重启建立的主界面实例时刻。
         *
         * 必须是静态值：读取它的是重启后的**新**实例，实例字段无法跨实例传递。
         */
        @Volatile
        private var instanceCreateTime = 0L
    }
}
