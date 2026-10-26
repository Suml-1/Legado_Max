package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isGone
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.databinding.ViewMangaMenuBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.getSourceType
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.ConstraintModify
import io.legado.app.utils.activity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.loadAnimation
import io.legado.app.utils.modifyBegin
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible

class MangaMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val binding = ViewMangaMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private val callBack: CallBack get() = activity as CallBack
    var canShowMenu: Boolean = false
    private val menuTopIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_in)
    }
    private val menuTopOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_out)
    }
    private val menuBottomIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_in)
    }
    private val menuBottomOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_out)
    }
    private var isMenuOutAnimating = false
    private var bgColor = context.bottomBackground
    private val sourceMenu by lazy {
        PopupMenu(context, binding.tvSourceAction).apply {
            inflate(R.menu.book_read_source)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_login -> callBack.showLogin()
                    R.id.menu_chapter_pay -> callBack.payAction()
                    R.id.menu_edit_source -> callBack.openSourceEditActivity()
                    R.id.menu_disable_source -> callBack.disableSource()
                }
                true
            }
        }
    }

    /**
     * 与小说模式 ReadMenu 保持一致：按书源配置和当前章节状态动态显隐登录/付费项
     */
    private fun upSourceMenuView() {
        val source = ReadManga.bookSource
        sourceMenu.menu.findItem(R.id.menu_login)?.isVisible =
            source != null && !source.loginUrl.isNullOrEmpty()
        val chapter = ReadManga.curMangaChapter?.chapter
        sourceMenu.menu.findItem(R.id.menu_chapter_pay)?.isVisible =
            source != null && chapter != null && chapter.isVip && !chapter.isPay &&
                    !source.ruleContent?.payAction.isNullOrEmpty()
    }

    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@MangaMenu.invisible()
            binding.titleBar.invisible()
            binding.bottomMenu.invisible()
            isMenuOutAnimating = false
            canShowMenu = false
            callBack.upSystemUiVisibility(false)
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            upBookView()
            callBack.upSystemUiVisibility(true)
        }

        @SuppressLint("RtlHardcoded")
        override fun onAnimationEnd(animation: Animation) {
            binding.run {
                vwMenuBg.setOnClickListener { runMenuOut() }
            }
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        initView()
        bindEvent()
    }

    /**
     * 顶栏（书名、返回/更多按钮、菜单项图标）的实际前景色。
     * 与 [initView] 给顶栏上色时是同一个值（非墨水屏按主题主色取深浅）。
     * 漫画页顶栏是 `skipTopBarConfig` 的有意例外，菜单图标要跟它同色，不能吃"顶栏统一取色"。
     */
    val titleBarContentColor: Int
        get() = context.primaryTextColor

    private fun initView() = binding.run {
        initAnimation()
        tvCustomBtn.setColorFilter(context.accentColor)
        val brightnessBackground = GradientDrawable()
        brightnessBackground.cornerRadius = 5F.dpToPx()
        brightnessBackground.setColor(ColorUtils.adjustAlpha(bgColor, 0.5f))
        if (AppConfig.isEInkMode) {
            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
            bottomMenu.setBackgroundResource(R.drawable.bg_eink_border_top)
        } else {
            val titleBgColor = context.primaryColor
            val titleTextColor = context.primaryTextColor
            titleBar.setBackgroundColor(titleBgColor)
            titleBar.setTextColor(titleTextColor)
            titleBar.setColorFilter(titleTextColor)
            bottomMenu.setBackgroundColor(bgColor)
        }
        if (AppConfig.showReadTitleBarAddition) {
            titleBarAddition.visible()
        } else {
            titleBarAddition.gone()
        }
        upBottomMenuTextColor()
        upBrightnessVwPos()
        /**
         * 确保视图不被导航栏遮挡
         */
        bottomMenu.applyNavigationBarPadding()
    }

    private fun upBottomMenuTextColor() = binding.run {
        val textColor = context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
        tvPre.setTextColor(textColor)
        tvNext.setTextColor(textColor)
        tvCatalog.setTextColor(textColor)
        tvAutoPage.setTextColor(textColor)
        tvBrightness.setTextColor(textColor)
        tvSetting.setTextColor(textColor)
        ivCatalog.setColorFilter(textColor)
        ivAutoPage.setColorFilter(textColor)
        ivBrightness.setColorFilter(textColor)
        ivSetting.setColorFilter(textColor)
    }

    private fun upBrightnessVwPos() {
        if (AppConfig.brightnessVwPos) {
            binding.root.modifyBegin()
                .clear(R.id.ll_brightness, ConstraintModify.Anchor.LEFT)
                .rightToRightOf(R.id.ll_brightness, R.id.vw_menu_root)
                .commit()
        } else {
            binding.root.modifyBegin()
                .clear(R.id.ll_brightness, ConstraintModify.Anchor.RIGHT)
                .leftToLeftOf(R.id.ll_brightness, R.id.vw_menu_root)
                .commit()
        }
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode) {
        if (isMenuOutAnimating) {
            return
        }
        if (this.isVisible) {
            if (anim) {
                binding.titleBar.startAnimation(menuTopOut)
                binding.bottomMenu.startAnimation(menuBottomOut)
            } else {
                menuOutListener.onAnimationStart(menuBottomOut)
                menuOutListener.onAnimationEnd(menuBottomOut)
            }
        }
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        this.visible()
        binding.titleBar.visible()
        binding.bottomMenu.visible()
        if (anim) {
            binding.titleBar.startAnimation(menuTopIn)
            binding.bottomMenu.startAnimation(menuBottomIn)
        } else {
            menuInListener.onAnimationStart(menuBottomIn)
            menuInListener.onAnimationEnd(menuBottomIn)
        }
    }


    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { runMenuOut() }
        titleBar.toolbar.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        val chapterViewClickListener = OnClickListener {
            if (ReadManga.book?.isLocal == true) {
                return@OnClickListener
            }
            if (AppConfig.readUrlInBrowser) {
                context.openUrl(tvChapterUrl.text.toString().substringBefore(",{"))
            } else {
                context.startActivity<WebViewActivity> {
                    val url = tvChapterUrl.text.toString()
                    val bookSource = ReadManga.bookSource
                    putExtra("title", tvChapterName.text)
                    putExtra("url", url)
                    putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                    putExtra("sourceName", bookSource?.bookSourceName)
                    putExtra("sourceType", bookSource?.getSourceType())
                }
            }
        }
        val chapterViewLongClickListener = OnLongClickListener {
            if (ReadManga.book?.isLocal == true) {
                return@OnLongClickListener true
            }
            context.alert(R.string.open_fun) {
                setMessage(R.string.use_browser_open)
                okButton {
                    AppConfig.readUrlInBrowser = true
                }
                noButton {
                    AppConfig.readUrlInBrowser = false
                }
            }
            true
        }
        tvChapterName.setOnClickListener(chapterViewClickListener)
        tvChapterName.setOnLongClickListener(chapterViewLongClickListener)
        tvChapterUrl.setOnClickListener(chapterViewClickListener)
        tvChapterUrl.setOnLongClickListener(chapterViewLongClickListener)
        tvSourceAction.setOnClickListener {
            if (ReadManga.book?.isLocal == true) return@setOnClickListener
            upSourceMenuView()
            sourceMenu.show()
        }
        tvCustomBtn.setOnClickListener {
            runCustomButtonEvent(SourceCallBack.CLICK_CUSTOM_BUTTON)
        }
        tvCustomBtn.setOnLongClickListener {
            runCustomButtonEvent(SourceCallBack.LONG_CLICK_CUSTOM_BUTTON)
            true
        }

        tvNext.setOnClickListener {
            ReadManga.moveToNextChapter(true)
        }
        tvPre.setOnClickListener {
            ReadManga.moveToPrevChapter(true)
        }

        llCatalog.setOnClickListener {
            runMenuOut()
            callBack.openChapterList()
        }
        llAutoPage.setOnClickListener {
            runMenuOut()
            callBack.toggleAutoPage()
        }
        llBrightness.setOnClickListener {
            runMenuOut()
            callBack.showBrightness()
        }
        llSetting.setOnClickListener {
            callBack.showMangaSetting(it)
        }

        seekReadPage.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    callBack.skipToPage(seekBar.progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                binding.vwMenuBg.setOnClickListener(null)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                binding.vwMenuBg.setOnClickListener { runMenuOut() }
            }
        })
    }

    /**
     * 执行书源的定制按钮回调（点击 / 长按）。
     * 与小说模式 [io.legado.app.ui.book.read.ReadMenu] 保持一致，仅把书籍类型换成漫画（image）。
     */
    private fun runCustomButtonEvent(event: String) {
        val book = ReadManga.book ?: return
        if (book.isLocal) return
        val act = activity ?: return
        val source = ReadManga.bookSource
        val chapter = ReadManga.curMangaChapter?.chapter
        val callBack = {
            SourceCallBack.callBackBtn(
                act,
                event,
                source,
                book,
                chapter,
                BookType.image
            )
        }
        if (chapter != null) {
            callBack()
        } else {
            // 章节信息尚未加载时从数据库取，避免在主线程做 IO
            Coroutine.async {
                appDb.bookChapterDao.getChapter(book.bookUrl, ReadManga.durChapterIndex)
            }.onSuccess {
                it ?: return@onSuccess
                SourceCallBack.callBackBtn(
                    act,
                    event,
                    source,
                    book,
                    it,
                    BookType.image
                )
            }
        }
    }

    fun upSeekBar(value: Int, count: Int) {
        binding.seekReadPage.apply {
            max = count.minus(1)
            progress = value
        }
    }

    fun upBookView() {
        binding.titleBar.title = ReadManga.book?.name
        binding.tvSourceAction.text =
            ReadManga.bookSource?.bookSourceName ?: context.getString(R.string.book_source)
        binding.tvSourceAction.isGone = ReadManga.book?.isLocal == true
        // 定制按钮：与书源的自定义按钮开关联动，本地书籍不显示
        binding.tvCustomBtn.isVisible =
            ReadManga.book?.isLocal != true && ReadManga.bookSource?.customButton == true

        ReadManga.curMangaChapter?.let {
            binding.tvChapterName.text = it.chapter.title
            binding.tvChapterName.visible()
            if (ReadManga.book?.isLocal == true) {
                binding.tvChapterUrl.gone()
            } else {
                binding.tvChapterUrl.text = it.chapter.getAbsoluteURL()
                binding.tvChapterUrl.visible()
            }
            binding.tvPre.isEnabled = ReadManga.durChapterIndex != 0
            binding.tvNext.isEnabled =
                ReadManga.durChapterIndex != ReadManga.simulatedChapterSize - 1
        } ?: run {
            binding.tvChapterName.gone()
            binding.tvChapterUrl.gone()
            binding.tvPre.isEnabled = false
            binding.tvNext.isEnabled = false
        }
        refreshTitleBarAdditionLayout()
    }

    private fun refreshTitleBarAdditionLayout() {
        binding.titleBarAddition.isVisible = AppConfig.showReadTitleBarAddition
        binding.titleBarAddition.requestLayout()
        binding.titleBar.requestLayout()
        binding.titleBar.invalidate()
        binding.titleBarAddition.post {
            binding.titleBarAddition.requestLayout()
            binding.titleBar.requestLayout()
            binding.titleBar.invalidate()
        }
    }

    interface CallBack {
        fun showLogin()
        fun payAction()
        fun openSourceEditActivity()
        fun openBookInfoActivity()
        fun upSystemUiVisibility(menuIsVisible: Boolean)
        fun skipToPage(index: Int)
        fun disableSource()
        fun openChapterList()
        fun toggleAutoPage()
        fun showBrightness()
        fun showMangaSetting(anchor: android.view.View)
    }

}
