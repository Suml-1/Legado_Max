package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.graphics.Point
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.base.BasePrefDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.TextMenuConfigDialog
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.removePref
import io.legado.app.utils.setEdgeEffectColor

/**
 * 阅读界面"更多设置"对话框
 * 
 * 显示在阅读界面底部的设置面板，包含阅读相关的各项配置：
 * - 屏幕方向、屏幕超时
 * - 状态栏/导航栏显示
 * - 双页模式、进度条行为
 * - 排版设置（两端对齐、底部对齐、中文排版）
 * - 翻页设置（音量键翻页、鼠标滚轮翻页、触摸灵敏度）
 * - 其他设置（文字选择、亮度显示、自定义按键等）
 * 
 * 使用 PreferenceFragment 加载 XML 配置，支持实时响应配置变更。
 */
class MoreConfigDialog : BasePrefDialogFragment() {
    private val readPreferTag = "readPreferenceFragment"

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            val height = (size.y * 0.6).toInt()
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        (activity as ReadBookActivity).bottomDialog++
        val view = LinearLayout(context)
        view.setBackgroundColor(requireContext().bottomBackground)
        view.id = R.id.tag1
        container?.addView(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var preferenceFragment = childFragmentManager.findFragmentByTag(readPreferTag)
        if (preferenceFragment == null) preferenceFragment = ReadPreferenceFragment()
        childFragmentManager.beginTransaction()
            .replace(view.id, preferenceFragment, readPreferTag)
            .commit()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    class ReadPreferenceFragment : PreferenceFragment(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        /** 系统默认触摸灵敏度阈值 */
        private val slopSquare by lazy { ViewConfiguration.get(requireContext()).scaledTouchSlop }

        /**
         * 加载偏好设置XML配置
         * 配置项定义在 res/xml/pref_config_read.xml 中
         */
        @SuppressLint("RestrictedApi")
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_config_read)
            upPreferenceSummary(PreferKey.pageTouchSlop, slopSquare.toString())
            if (!CanvasRecorderFactory.isSupport) {
                removePref(PreferKey.optimizeRender)
                preferenceScreen.removePreferenceRecursively(PreferKey.optimizeRender)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            listView.setEdgeEffectColor(primaryColor)
        }

        override fun onResume() {
            super.onResume()
            preferenceManager
                .sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            preferenceManager
                .sharedPreferences
                ?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        /**
         * 偏好设置变更监听
         * 当配置项改变时，根据不同的key执行相应的更新操作
         * 通过 EventBus 发送事件通知其他组件更新
         */
        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                PreferKey.readBodyToLh -> activity?.recreate()
                PreferKey.hideStatusBar -> {
                    ReadBookConfig.hideStatusBar = getPrefBoolean(PreferKey.hideStatusBar)
                    postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
                }

                PreferKey.hideNavigationBar -> {
                    ReadBookConfig.hideNavigationBar = getPrefBoolean(PreferKey.hideNavigationBar)
                    postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
                }

                PreferKey.keepLight -> postEvent(key, true)
                PreferKey.textSelectAble -> postEvent(key, getPrefBoolean(key))
                PreferKey.screenOrientation -> {
                    (activity as? ReadBookActivity)?.setOrientation()
                }

                PreferKey.textFullJustify,
                PreferKey.textBottomJustify,
                PreferKey.useZhLayout,
                PreferKey.allowPunctAtLineStart,
                PreferKey.adaptSpecialStyle,
                PreferKey.keepBlankLine -> {
                    postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                }

                PreferKey.showBrightnessView -> {
                    postEvent(PreferKey.showBrightnessView, "")
                }

                PreferKey.expandTextMenu -> {
                    (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
                }

                PreferKey.doublePageHorizontal -> {
                    ChapterProvider.upLayout()
                    ReadBook.loadContent(false)
                }

                PreferKey.showReadTitleAddition,
                PreferKey.readBarStyleFollowPage -> {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                PreferKey.progressBarBehavior -> {
                    postEvent(EventBus.UP_SEEK_BAR, true)
                }

                PreferKey.noAnimScrollPage -> {
                    ReadBook.callBack?.upPageAnim()
                }

                PreferKey.optimizeRender -> {
                    ChapterProvider.upStyle()
                    ReadBook.callBack?.upPageAnim(true)
                    ReadBook.loadContent(false)
                }

                PreferKey.paddingDisplayCutouts -> {
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                }
            }
        }

        /**
         * 偏好设置点击事件处理
         * 处理需要弹出对话框进行设置的配置项
         * 
         * 主要处理：
         * - customPageKey: 自定义翻页按键
         * - clickRegionalConfig: 点击区域配置
         * - pageTouchSlop: 触摸灵敏度设置
         * - pageTouchClick: 边缘点击阈值设置
         */
        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                "customPageKey" -> PageKeyDialog(requireContext()).show()
                "clickRegionalConfig" -> {
                    (activity as? ReadBookActivity)?.showClickRegionalConfig()
                }
                "textMenuConfig" -> {
                    TextMenuConfigDialog().show(childFragmentManager, "textMenuConfig")
                }

                PreferKey.pageTouchSlop -> {
                    NumberPickerDialog(requireContext())
                        .setTitle(getString(R.string.page_touch_slop_dialog_title))
                        .setMaxValue(9999)
                        .setMinValue(0)
                        .setValue(AppConfig.pageTouchSlop)
                        .show {
                            AppConfig.pageTouchSlop = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(4))
                        }
                }

                PreferKey.pageTouchClick -> {
                    NumberPickerDialog(requireContext())
                        .setTitle(getString(R.string.page_touch_click_dialog_title))
                        .setMaxValue(399)
                        .setMinValue(0)
                        .setValue(AppConfig.pageTouchClick)
                        .show {
                            AppConfig.pageTouchClick = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(12))
                        }
                }

                // 触摸翻页速度设置，范围50-1000ms，默认300ms
                PreferKey.touchPageAnimSpeed -> {
                    NumberPickerDialog(requireContext())
                        .setTitle(getString(R.string.touch_page_anim_speed_dialog_title))
                        .setMaxValue(1000)
                        .setMinValue(50)
                        .setValue(AppConfig.touchPageAnimSpeed)
                        .show {
                            AppConfig.touchPageAnimSpeed = it
                        }
                }

                // 按键翻页速度设置，范围30-500ms，默认100ms
                PreferKey.keyPageAnimSpeed -> {
                    NumberPickerDialog(requireContext())
                        .setTitle(getString(R.string.key_page_anim_speed_dialog_title))
                        .setMaxValue(500)
                        .setMinValue(30)
                        .setValue(AppConfig.keyPageAnimSpeed)
                        .show {
                            AppConfig.keyPageAnimSpeed = it
                        }
                }
            }
            return super.onPreferenceTreeClick(preference)
        }

        @Suppress("SameParameterValue")
        private fun upPreferenceSummary(preferenceKey: String, value: String?) {
            val preference = findPreference<Preference>(preferenceKey) ?: return
            when (preferenceKey) {
                PreferKey.pageTouchSlop -> preference.summary =
                    getString(R.string.page_touch_slop_summary, value)
            }
        }

    }
}
