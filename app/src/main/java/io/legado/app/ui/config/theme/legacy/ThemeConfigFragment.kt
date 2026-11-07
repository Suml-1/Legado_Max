package io.legado.app.ui.config.theme.legacy

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogImageBlurringBinding
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.ShareNoteTemplateManageActivity
import io.legado.app.ui.config.bubble.BubbleManageActivity
import io.legado.app.ui.config.navigationbar.NavigationBarManageActivity
import io.legado.app.ui.config.topbar.TopBarManageActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.config.theme.manage.ThemeManageActivity
import io.legado.app.ui.config.theme.application.ApplicationThemeActivity
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.applyTint
import io.legado.app.utils.applyTopBarTint
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.FileOutputStream

/**
 * 主题配置 Fragment（旧版 PreferenceFragment 实现）。
 *
 * 通过 XML PreferenceScreen 展示主题色、背景图、虚化、导航栏透明等底层偏好配置项，
 * 同时作为枢纽跳转至应用主题管理、顶栏/底栏/气泡管理、封面配置、欢迎页配置等子页面。
 * 入口：我的 → 主题设置（MyFragment → ConfigActivity → ThemeConfigFragment）。
 * 保留为兼容入口，新增/编辑主题列表的入口已迁移至 ThemeManageActivity（Compose 版）。
 */
@Suppress("SameParameterValue")
class ThemeConfigFragment :
    PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MenuProvider {

    private val requestCodeBgLight = 121
    private val requestCodeBgDark = 122
    private val selectImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeBgLight -> setBgFromUri(uri, PreferKey.bgImage) {
                    upTheme(false)
                }

                requestCodeBgDark -> setBgFromUri(uri, PreferKey.bgImageN) {
                    upTheme(true)
                }
            }
        }
    }

    /**
     * 初始化偏好设置界面。
     * 加载 XML 配置、移除低版本不支持的启动器图标选项、设置各配置项摘要，
     * 并为日间/夜间背景色设置合法性校验回调。
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_theme)
        if (Build.VERSION.SDK_INT < 26) {
            preferenceScreen.removePreferenceRecursively(PreferKey.launcherIcon)
        }
        upPreferenceSummary(PreferKey.bgImage, getPrefString(PreferKey.bgImage))
        upPreferenceSummary(PreferKey.bgImageN, getPrefString(PreferKey.bgImageN))
        upPreferenceSummary(PreferKey.barElevation, AppConfig.elevation.toString())
        upPreferenceSummary(PreferKey.fontScale)
        findPreference<ColorPreference>(PreferKey.cBackground)?.let {
            it.onSaveColor = { color ->
                if (!ColorUtils.isColorLight(color)) {
                    toastOnUi(R.string.day_background_too_dark)
                    true
                } else {
                    false
                }
            }
        }
        findPreference<ColorPreference>(PreferKey.cNBackground)?.let {
            it.onSaveColor = { color ->
                if (ColorUtils.isColorLight(color)) {
                    toastOnUi(R.string.night_background_too_light)
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * View 创建完成后的初始化。
     * 设置标题、列表边缘效果颜色、导航栏内边距，并注册菜单提供者。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.theme_setting)
        listView.setEdgeEffectColor(primaryColor)
        listView.applyNavigationBarPadding(withInitialPadding = true)
        activity?.addMenuProvider(this, viewLifecycleOwner)
    }

    /**
     * Fragment 创建时注册 SharedPreferences 变化监听。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    /**
     * Fragment 销毁时注销 SharedPreferences 变化监听。
     */
    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    /**
     * 创建选项菜单。
     * 加载主题配置菜单并更新主题模式切换菜单项的图标和标题。
     */
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.theme_config, menu)
        updateThemeModeMenuItem(menu)
        // 与页面顶栏标题同色（见 Menu.applyTopBarTint）：此前按主题取色，日夜切换图标
        // 会在浅色顶栏上停留成白色，和标题对不上
        menu.applyTopBarTint(requireContext())
    }

    /**
     * 根据当前日间/夜间模式更新菜单项的图标和标题。
     * 夜间时显示太阳图标（切换到日间），日间时显示月亮图标（切换到夜间）。
     */
    private fun updateThemeModeMenuItem(menu: Menu) {
        val themeModeItem = menu.findItem(R.id.menu_theme_mode) ?: return
        val iconRes = if (AppConfig.isNightTheme) {
            R.drawable.ic_daytime
        } else {
            R.drawable.ic_moon
        }
        val titleRes = if (AppConfig.isNightTheme) {
            R.string.day
        } else {
            R.string.night
        }
        themeModeItem.setIcon(iconRes)
        themeModeItem.setTitle(titleRes)
    }

    /**
     * 处理菜单项选择事件。
     * 切换日间/夜间模式后刷新菜单。
     */
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_theme_mode -> {
                AppConfig.isNightTheme = !AppConfig.isNightTheme
                ThemeConfig.applyDayNight(requireContext())
                activity?.invalidateMenu()
                return true
            }
        }
        return false
    }

    /**
     * SharedPreferences 变化回调。
     * 根据变化的 key 执行对应操作：切换启动器图标、重建 Activity、更新主题或刷新摘要。
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        when (key) {
            PreferKey.launcherIcon -> LauncherIconHelp.changeIcon(getPrefString(key))
            PreferKey.transparentStatusBar -> recreateActivities()
            PreferKey.immNavigationBar -> recreateActivities()
            PreferKey.bookshelfIconStyle -> recreateActivities()
            PreferKey.enableEInk -> {
                // 墨水屏渲染开关：色板按当前日夜重新推导，随后重建界面生效
                listView.post {
                    ThemeConfig.applyTheme(requireContext())
                    recreateActivities()
                }
            }

            PreferKey.cPrimary,
            PreferKey.cAccent,
            PreferKey.cBackground,
            PreferKey.cBBackground,
            PreferKey.tNavBar,
            -> {
                upTheme(false)
            }

            PreferKey.cNPrimary,
            PreferKey.cNAccent,
            PreferKey.cNBackground,
            PreferKey.cNBBackground,
            PreferKey.tNavBarN,
            -> {
                upTheme(true)
            }

            PreferKey.bgImage,
            PreferKey.bgImageN,
            -> {
                upPreferenceSummary(key, getPrefString(key))
            }
        }
    }

    /**
     * 处理偏好设置项点击事件。
     * 根据 key 分发到工具栏阴影、字体缩放、背景图选择、主题管理、
     * 顶栏/底栏/气泡管理、封面配置、欢迎页配置等操作。
     */
    @SuppressLint("PrivateResource")
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (val key = preference.key) {
            PreferKey.barElevation -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.bar_elevation))
                .setMaxValue(32)
                .setMinValue(0)
                .setValue(AppConfig.elevation)
                .setCustomButton((R.string.btn_default_s)) {
                    AppConfig.elevation = AppConst.sysElevation
                    recreateActivities()
                }
                .show {
                    AppConfig.elevation = it
                    recreateActivities()
                }

            PreferKey.fontScale -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.font_scale))
                .setMaxValue(16)
                .setMinValue(8)
                .setValue(requireContext().getPrefInt(PreferKey.fontScale, 10))
                .setCustomButton((R.string.btn_default_s)) {
                    putPrefInt(PreferKey.fontScale, 0)
                    recreateActivities()
                }
                .show {
                    putPrefInt(PreferKey.fontScale, it)
                    recreateActivities()
                }

            PreferKey.bgImage -> selectBgAction(false)
            PreferKey.bgImageN -> selectBgAction(true)
            "applicationThemeManage" -> startActivity<ApplicationThemeActivity>()
            "themeList" -> startActivity<ThemeManageActivity>()
            "saveDayTheme",
            "saveNightTheme",
            -> alertSaveTheme(key)

            "coverConfig" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.COVER_CONFIG)
            }

            "shareNoteTemplateManage" -> startActivity<ShareNoteTemplateManageActivity>()

            "navigationBarManage" -> startActivity<NavigationBarManageActivity>()

            "topBarManage" -> startActivity<TopBarManageActivity>()

            "bubbleManage" -> startActivity<BubbleManageActivity>()

            "welcomeStyle" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.WELCOME_CONFIG)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    /**
     * 显示保存主题对话框。
     * 输入主题名称后将当前日间或夜间主题保存为可切换的主题方案。
     *
     * @param key 区分日间/夜间（"saveDayTheme" 或 "saveNightTheme"）
     */
    @SuppressLint("InflateParams")
    private fun alertSaveTheme(key: String) {
        alert(R.string.theme_name) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "name"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { themeName ->
                    lifecycleScope.launch {
                        when (key) {
                            "saveDayTheme" -> {
                                ThemeConfig.saveDayTheme(requireContext(), themeName)
                            }

                            "saveNightTheme" -> {
                                ThemeConfig.saveNightTheme(requireContext(), themeName)
                            }
                        }
                    }
                }
            }
            cancelButton()
        }
    }

    /**
     * 显示背景图操作选择器。
     * 提供虚化调节、选择图片、删除背景图等操作。
     *
     * @param isNight 是否为夜间背景图
     */
    private fun selectBgAction(isNight: Boolean) {
        val bgKey = if (isNight) PreferKey.bgImageN else PreferKey.bgImage
        val blurringKey = if (isNight) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        val actions = arrayListOf(
            getString(R.string.background_image_blurring),
            getString(R.string.select_image),
        )
        if (!getPrefString(bgKey).isNullOrEmpty()) {
            actions.add(getString(R.string.delete))
        }
        context?.selector(items = actions) { _, i ->
            when (i) {
                0 -> alertImageBlurring(blurringKey) {
                    upTheme(isNight)
                }

                1 -> {
                    if (isNight) {
                        selectImage.launch {
                            requestCode = requestCodeBgDark
                            mode = HandleFileContract.IMAGE
                        }
                    } else {
                        selectImage.launch {
                            requestCode = requestCodeBgLight
                            mode = HandleFileContract.IMAGE
                        }
                    }
                }

                2 -> {
                    removePref(bgKey)
                    upTheme(isNight)
                }
            }
        }
    }

    /**
     * 显示背景图虚化调节对话框。
     * 通过 SeekBar 调节虚化程度，确认后保存到 SharedPreferences。
     *
     * @param preferKey 虚化值的偏好键名
     * @param success   保存成功后的回调
     */
    private fun alertImageBlurring(preferKey: String, success: () -> Unit) {
        alert(R.string.background_image_blurring) {
            val alertBinding = DialogImageBlurringBinding.inflate(layoutInflater).apply {
                getPrefInt(preferKey, 0).let {
                    seekBar.progress = it
                    textViewValue.text = it.toString()
                }
                seekBar.setOnSeekBarChangeListener(object : SeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean,
                    ) {
                        textViewValue.text = progress.toString()
                    }
                })
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.seekBar.progress.let {
                    putPrefInt(preferKey, it)
                    success.invoke()
                }
            }
            cancelButton()
        }
    }

    /**
     * 更新主题配置。
     * 仅当变化的主题类型与当前模式匹配时才重建 Activity 刷新界面。
     *
     * @param isNightTheme 是否为夜间主题变化
     */
    private fun upTheme(isNightTheme: Boolean) {
        if (AppConfig.isNightTheme == isNightTheme) {
            listView.post {
                ThemeConfig.applyTheme(requireContext())
                recreateActivities()
            }
        }
    }

    /**
     * 发送重建事件以刷新所有 Activity。
     */
    private fun recreateActivities() {
        ThemeConfig.notifyRecreate()
    }

    /**
     * 更新偏好设置项的摘要文本。
     * 根据不同的配置项类型格式化摘要内容。
     *
     * @param preferenceKey 偏好键名
     * @param value         当前值，为 null 时从 SharedPreferences 读取
     */
    private fun upPreferenceSummary(preferenceKey: String, value: String? = null) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.barElevation ->
                preference.summary =
                    getString(R.string.bar_elevation_s, value)

            PreferKey.fontScale -> {
                val fontScale = requireContext().getPrefInt(PreferKey.fontScale, 10)
                preference.summary = getString(R.string.font_scale_summary, fontScale)
            }

            PreferKey.bgImage,
            PreferKey.bgImageN,
            -> preference.summary = if (value.isNullOrBlank()) {
                getString(R.string.select_image)
            } else {
                value
            }

            else -> preference.summary = value
        }
    }

    /**
     * 从 URI 设置背景图。
     * 支持 HTTP(S) 远程下载和本地文件两种来源，
     * 下载/复制到外部文件目录后保存绝对路径到 SharedPreferences。
     *
     * @param uri          图片 URI（远程 URL 或本地 content URI）
     * @param preferenceKey 背景图的偏好键名
     * @param success      设置成功后的回调
     */
    private fun setBgFromUri(uri: Uri, preferenceKey: String, success: () -> Unit) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            lifecycleScope.launch {
                kotlin.runCatching {
                    appCtx.toastOnUi("下载背景图片中...")
                    val analyzeUrl = AnalyzeUrl(uri.toString())
                    val url = analyzeUrl.urlNoQuery
                    var file = requireContext().externalFiles
                    val res = okHttpClient.newCallResponse(0) {
                        addHeaders(analyzeUrl.headerMap)
                        url(url)
                    }
                    val contentType = res.header("Content-Type") ?: "image/jpeg"
                    val imageType = when {
                        contentType.contains("png", ignoreCase = true) -> "png"
                        contentType.contains("gif", ignoreCase = true) -> "gif"
                        contentType.contains("webp", ignoreCase = true) -> "webp"
                        else -> "jpg"
                    }
                    val suffix = if (url.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        "." + imageType
                    }
                    val fileName = MD5Utils.md5Encode(url) + suffix
                    file = FileUtils.createFileIfNotExist(file, preferenceKey, fileName)
                    res.body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    putPrefString(preferenceKey, file.absolutePath)
                    if (isAdded && context != null) {
                        success()
                    }
                }.onSuccess {
                    appCtx.toastOnUi("设定成功")
                }.onFailure {
                    appCtx.toastOnUi(it.localizedMessage)
                }
            }
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, preferenceKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                success()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}
