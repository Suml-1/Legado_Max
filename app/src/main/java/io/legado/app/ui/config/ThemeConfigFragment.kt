package io.legado.app.ui.config

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.AppContextWrapper
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.NgEffectThemeSelection
import io.legado.app.help.config.NgDrawerProfileStore
import io.legado.app.help.config.NgThemeDrawerAssets
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.help.config.NgSoftGradientColorMode
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.NgSoftGradientLightFieldPreset
import io.legado.app.help.config.NgSoftGradientTheme
import io.legado.app.help.config.NgThemeModeGroup
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.help.config.NgVisualSystemStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.startActivity
import io.legado.app.utils.statusBarHeight
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.applyAppNavigationBarVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

@Suppress("SameParameterValue")
class ThemeConfigFragment : BaseFragment(R.layout.fragment_theme_config) {

    private val requestCodeBgLight = 121
    private val requestCodeBgDark = 122
    private var screenState by mutableStateOf(ThemeConfigScreenState())
    private var launcherIconSelection by mutableStateOf<String?>(null)
    private var backgroundEditorState by mutableStateOf<ThemeBackgroundEditorState?>(null)
    private var fontScaleEditorState by mutableStateOf<ThemeFontScaleEditorState?>(null)
    private var drawerViewActive = false
    private var drawerRevision = 0L
    private var drawerDirty = false
    private var drawerWriterRunning = false
    private var pendingDrawerCommit: DrawerProfileCommit? = null
    private var committingDrawerCommit: DrawerProfileCommit? = null
    private val drawerDraftImages = linkedSetOf<String>()
    private var drawerImageRequest = 0L
    private var pendingDrawerImageNight: Boolean? = null
    private var pendingDrawerImageUri: String? = null
    private var drawerImageCopyOwner: String? = null

    private val selectImage = registerForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeBgLight -> copyBgFromUri(uri, PreferKey.bgImage) { path ->
                    updateBackgroundDraft(dark = false, path = path)
                }

                requestCodeBgDark -> copyBgFromUri(uri, PreferKey.bgImageN) { path ->
                    updateBackgroundDraft(dark = true, path = path)
                }
            }
        }
    }

    private val selectDrawerImage = registerForActivityResult(SelectFileContract()) { uri ->
        val night = pendingDrawerImageNight
        if (uri == null || night == null) {
            pendingDrawerImageNight = null
            pendingDrawerImageUri = null
        } else {
            pendingDrawerImageUri = uri.toString()
            importDrawerImage(uri, night, drawerImageRequest)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        drawerViewActive = true
        if (savedInstanceState?.containsKey(DRAWER_IMAGE_NIGHT) == true) {
            pendingDrawerImageNight = savedInstanceState.getBoolean(DRAWER_IMAGE_NIGHT)
            pendingDrawerImageUri = savedInstanceState.getString(DRAWER_IMAGE_URI)
            drawerImageRequest = savedInstanceState.getLong(DRAWER_IMAGE_REQUEST)
        }
        activity?.setTitle(titleRes())
        refreshContent()
        viewLifecycleOwner.lifecycleScope.launch {
            NgDrawerProfileStore.observe(requireContext()).collect { drawer ->
                if (!drawerDirty && !drawerWriterRunning) {
                    screenState = screenState.copy(drawerProfile = drawer.snapshot())
                }
            }
        }
        val pendingUri = pendingDrawerImageUri
        val pendingNight = pendingDrawerImageNight
        if (pendingUri != null && pendingNight != null) {
            importDrawerImage(Uri.parse(pendingUri), pendingNight, drawerImageRequest)
        }
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    ThemeConfigScreen(
                        state = screenState,
                        section = screenSection(),
                        onThemeModeGroupSelected = ::selectThemeModeGroup,
                        onStandardThemeModeSelected = ::selectStandardThemeMode,
                        onInternalThemeModeSelected = ::selectInternalThemeMode,
                        onSoftGradientColorSelected = ::selectSoftGradientColor,
                        onSoftGradientCustomColorSelected = ::selectSoftGradientCustomColor,
                        onSoftGradientLightFieldSelected = ::selectSoftGradientLightField,
                        onVisualSystemSelected = ::setVisualSystem,
                        onLauncherIconClick = ::showLauncherIconSelection,
                        onFloatingBottomBarChanged = ::setFloatingBottomBar,
                        onFloatingBottomBarBottomDistanceChanged =
                            ::setFloatingBottomBarBottomDistanceDraft,
                        onFloatingBottomBarBottomDistanceChangeFinished =
                            ::saveFloatingBottomBarBottomDistance,
                        onFloatingBottomBarTransparencyChanged =
                            ::setFloatingBottomBarTransparencyDraft,
                        onFloatingBottomBarTransparencyChangeFinished =
                            ::saveFloatingBottomBarTransparency,
                        onDrawerProfileChanged = ::setDrawerProfileDraft,
                        onDrawerProfileChangeFinished = ::saveDrawerProfile,
                        onSelectDrawerImage = ::selectCurrentDrawerImage,
                        onBookshelfTopBarStyleSelected = ::setBookshelfTopBarStyle,
                        onBookshelfFloatingDockTopDistanceChanged =
                            ::setBookshelfFloatingDockTopDistanceDraft,
                        onBookshelfFloatingDockTopDistanceChangeFinished =
                            ::saveBookshelfFloatingDockTopDistance,
                        onBookshelfFloatingDockTransparencyChanged =
                            ::setBookshelfFloatingDockTransparencyDraft,
                        onBookshelfFloatingDockTransparencyChangeFinished =
                            ::saveBookshelfFloatingDockTransparency,
                        onBookshelfFloatingDockSearchPositionSelected =
                            ::setBookshelfFloatingDockSearchPosition,
                        onHideSystemNavigationBarChanged = ::setHideSystemNavigationBar,
                        onAutoRefreshChanged = ::setAutoRefresh,
                        onOnlyUpdateReadChanged = ::setOnlyUpdateRead,
                        onDefaultToReadChanged = ::setDefaultToRead,
                        onShowHomeChanged = ::setShowHome,
                        onShowDiscoveryChanged = ::setShowDiscovery,
                        onShowRssChanged = ::setShowRss,
                        onDefaultHomePageSelected = ::setDefaultHomePage,
                        onOpenCustomColors = {
                            (activity as? ConfigActivity)?.openThemeColorConfigPage()
                        },
                        onOpenFontScale = ::openFontScaleEditor,
                        onOpenCoverConfig = {
                            startActivity<ConfigActivity> {
                                putExtra("configTag", ConfigTag.COVER_CONFIG)
                            }
                        },
                        onOpenThemeManager = {
                            (activity as? ConfigActivity)?.openThemeManagerPage()
                        },
                        onOpenDayBackground = { openBackgroundEditor(false) },
                        onOpenNightBackground = { openBackgroundEditor(true) }
                    )
                    launcherIconSelection?.let { currentValue ->
                        LauncherIconSelectionSheet(
                            currentValue = currentValue,
                            onDismissRequest = { launcherIconSelection = null },
                            onSelected = ::selectLauncherIcon,
                        )
                    }
                    backgroundEditorState?.let { editorState ->
                        ThemeBackgroundEditorSheet(
                            state = editorState,
                            onDismissRequest = { backgroundEditorState = null },
                            onSelectImage = { selectBackgroundImage(editorState.dark) },
                            onRemoveImage = {
                                backgroundEditorState = editorState.copy(path = null, blur = 0)
                            },
                            onBlurChanged = { blur ->
                                backgroundEditorState = editorState.copy(blur = blur)
                            },
                            onSave = ::saveBackgroundEditor
                        )
                    }
                    fontScaleEditorState?.let { editorState ->
                        ThemeFontScaleEditorSheet(
                            state = editorState,
                            onDismissRequest = { fontScaleEditorState = null },
                            onScaleChanged = { scale ->
                                fontScaleEditorState = editorState.copy(
                                    scale = scale,
                                    followSystem = false
                                )
                            },
                            onFollowSystem = {
                                fontScaleEditorState = editorState.copy(
                                    scale = systemFontScaleForEditor(),
                                    followSystem = true
                                )
                            },
                            onSave = ::saveFontScaleEditor
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(titleRes())
        if (view != null) refreshContent()
    }

    private fun screenSection(): ThemeConfigSection {
        return when (activity?.intent?.getStringExtra("configTag")) {
            ConfigTag.APPEARANCE_CONFIG -> ThemeConfigSection.APPEARANCE
            ConfigTag.INTERFACE_CONFIG -> ThemeConfigSection.INTERFACE
            else -> ThemeConfigSection.ALL
        }
    }

    private fun titleRes(): Int {
        return when (screenSection()) {
            ThemeConfigSection.APPEARANCE -> R.string.appearance_setting
            ThemeConfigSection.INTERFACE -> R.string.interface_layout_setting
            ThemeConfigSection.ALL -> R.string.theme_setting
        }
    }

    private fun refreshContent() {
        val launcherIcon = getPrefString(PreferKey.launcherIcon, DEFAULT_LAUNCHER_ICON)
            ?: DEFAULT_LAUNCHER_ICON
        val displayMetrics = resources.displayMetrics
        val statusBarHeightPx = requireContext().statusBarHeight
        screenState = ThemeConfigScreenState(
            themeModeGroup = NgThemeModeStore.currentGroup(requireContext()),
            presentationMode = NgThemeModeStore.current(requireContext()),
            standardThemeMode = NgThemeModeStore.standardThemeMode(requireContext()),
            internalThemeMode = NgThemeModeStore.lastInternalMode(requireContext()),
            softGradientColorMode = NgSoftGradientTheme.colorMode(requireContext()),
            softGradientColor = NgSoftGradientTheme.colorPreset(requireContext()),
            softGradientCustomColor = NgSoftGradientTheme.customColor(requireContext()),
            softGradientLightField = NgSoftGradientTheme.lightFieldPreset(requireContext()),
            effectThemeName = NgEffectThemeSelection.current(requireContext())?.name,
            installedThemeCount = NgThemeLibraryStore.current(requireContext()).savedThemes.size,
            visualSystem = NgVisualSystemStore.current(requireContext()),
            showLauncherIcon = Build.VERSION.SDK_INT >= 26,
            launcherIconRes = launcherIconResource(launcherIcon),
            floatingBottomBar = AppConfig.useFloatingBottomBar,
            floatingBottomBarBottomDistancePx =
                FloatingBottomBarConfig.resolveBottomDistancePx(
                    storedDistancePx = AppConfig.floatingBottomBarBottomDistancePx,
                    density = displayMetrics.density
                ),
            floatingBottomBarTransparency = AppConfig.floatingBottomBarTransparency,
            drawerProfile = if (drawerDirty || drawerWriterRunning) screenState.drawerProfile
                else NgDrawerProfileStore.snapshot(requireContext()),
            bookshelfTopBarStyle = AppConfig.bookshelfTopBarStyle,
            bookshelfFloatingDockMinTopDistancePx =
                BookshelfFloatingDockConfig.MIN_TOP_DISTANCE_PX,
            bookshelfFloatingDockTopDistancePx =
                BookshelfFloatingDockConfig.resolveTopDistancePx(
                    storedDistancePx = AppConfig.bookshelfFloatingDockTopDistancePx,
                    screenWidthPx = displayMetrics.widthPixels,
                    density = displayMetrics.density,
                    statusBarHeightPx = statusBarHeightPx
                ),
            bookshelfFloatingDockTransparency =
                AppConfig.bookshelfFloatingDockTransparency,
            bookshelfFloatingDockSearchPosition =
                AppConfig.bookshelfFloatingDockSearchPosition,
            hideSystemNavigationBar = AppConfig.hideSystemNavigationBar,
            autoRefresh = AppConfig.autoRefreshBook,
            onlyUpdateRead = AppConfig.onlyUpdateRead,
            defaultToRead = getPrefBoolean(PreferKey.defaultToRead, false),
            showHome = AppConfig.showHome,
            showDiscovery = AppConfig.showDiscovery,
            showRss = AppConfig.showRSS,
            defaultHomePage = AppConfig.defaultHomePage ?: "home",
            fontScaleSummary = getString(
                R.string.font_scale_summary,
                AppContextWrapper.getFontScale(requireContext())
            ),
            dayBackgroundSummary = backgroundSummary(
                imageKey = PreferKey.bgImage,
                blurKey = PreferKey.bgImageBlurring
            ),
            nightBackgroundSummary = backgroundSummary(
                imageKey = PreferKey.bgImageN,
                blurKey = PreferKey.bgImageNBlurring
            )
        )
    }

    private fun launcherIconResource(value: String): Int {
        return resources.getIdentifier(value, "mipmap", requireContext().packageName)
            .takeIf { it != 0 }
            ?: R.mipmap.ic_launcher
    }

    private fun backgroundSummary(imageKey: String, blurKey: String): String {
        if (getPrefString(imageKey).isNullOrBlank()) return getString(R.string.ng_theme_background_none)
        return getString(
            R.string.ng_theme_background_summary,
            getString(R.string.ng_theme_background_set),
            getPrefInt(blurKey, 0)
        )
    }

    private fun selectThemeModeGroup(group: NgThemeModeGroup) {
        if (group == screenState.themeModeGroup) return
        NgThemeModeStore.activateGroup(requireContext(), group)
        refreshContent()
        recreateActivities()
    }

    private fun selectStandardThemeMode(mode: String) {
        if (
            screenState.themeModeGroup == NgThemeModeGroup.STANDARD &&
            mode == screenState.standardThemeMode
        ) {
            return
        }
        NgThemeModeStore.activateStandard(requireContext(), mode)
        refreshContent()
        recreateActivities()
    }

    private fun selectInternalThemeMode(mode: NgThemePresentationMode) {
        if (
            screenState.themeModeGroup == NgThemeModeGroup.INTERNAL &&
            mode == screenState.presentationMode
        ) {
            return
        }
        screenState = screenState.copy(
            themeModeGroup = NgThemeModeGroup.INTERNAL,
            presentationMode = mode,
            internalThemeMode = mode,
        )
        NgThemeModeStore.activateInternal(requireContext(), mode)
        recreateActivities()
    }

    private fun selectSoftGradientColor(preset: NgSoftGradientColorPreset) {
        if (
            screenState.softGradientColorMode == NgSoftGradientColorMode.PRESET &&
            preset == screenState.softGradientColor
        ) return
        screenState = screenState.copy(
            softGradientColorMode = NgSoftGradientColorMode.PRESET,
            softGradientColor = preset,
        )
        NgSoftGradientTheme.selectColor(requireContext(), preset)
    }

    private fun selectSoftGradientCustomColor(color: Int) {
        val normalizedColor = color or 0xFF000000.toInt()
        if (
            screenState.softGradientColorMode == NgSoftGradientColorMode.CUSTOM &&
            normalizedColor == screenState.softGradientCustomColor
        ) return
        screenState = screenState.copy(
            softGradientColorMode = NgSoftGradientColorMode.CUSTOM,
            softGradientCustomColor = normalizedColor,
        )
        NgSoftGradientTheme.selectCustomColor(requireContext(), normalizedColor)
    }

    private fun selectSoftGradientLightField(preset: NgSoftGradientLightFieldPreset) {
        if (preset == screenState.softGradientLightField) return
        screenState = screenState.copy(softGradientLightField = preset)
        NgSoftGradientTheme.selectLightField(requireContext(), preset)
    }


    private fun setVisualSystem(visualSystem: NgVisualSystem) {
        if (visualSystem == screenState.visualSystem) return
        NgVisualSystemStore.update(requireContext(), visualSystem)
        screenState = screenState.copy(visualSystem = visualSystem)
    }

    private fun showLauncherIconSelection() {
        launcherIconSelection = getPrefString(
            PreferKey.launcherIcon,
            DEFAULT_LAUNCHER_ICON,
        ) ?: DEFAULT_LAUNCHER_ICON
    }

    private fun selectLauncherIcon(value: String) {
        putPrefString(PreferKey.launcherIcon, value)
        LauncherIconHelp.changeIcon(value)
        screenState = screenState.copy(launcherIconRes = launcherIconResource(value))
    }

    private fun setFloatingBottomBar(enabled: Boolean) {
        if (screenState.floatingBottomBar == enabled) return
        putPrefBoolean(PreferKey.useFloatingBottomBar, enabled)
        screenState = screenState.copy(floatingBottomBar = enabled)
    }

    private fun setFloatingBottomBarBottomDistanceDraft(value: Int) {
        val normalized = FloatingBottomBarConfig.normalizeBottomDistancePx(value)
        if (normalized == screenState.floatingBottomBarBottomDistancePx) return
        screenState = screenState.copy(floatingBottomBarBottomDistancePx = normalized)
    }

    private fun saveFloatingBottomBarBottomDistance() {
        AppConfig.floatingBottomBarBottomDistancePx =
            screenState.floatingBottomBarBottomDistancePx
    }

    private fun setFloatingBottomBarTransparencyDraft(value: Int) {
        val normalized = FloatingBottomBarConfig.normalizeTransparencyPercent(value)
        if (normalized == screenState.floatingBottomBarTransparency) return
        screenState = screenState.copy(floatingBottomBarTransparency = normalized)
    }

    private fun saveFloatingBottomBarTransparency() {
        AppConfig.floatingBottomBarTransparency =
            screenState.floatingBottomBarTransparency
    }

    private fun setDrawerProfileDraft(profile: NgThemeDrawerProfile) {
        val normalized = profile.normalized()
        if (normalized == screenState.drawerProfile) return
        val pendingNight = pendingDrawerImageNight
        if (pendingNight != null && normalized.source != "custom_image" &&
            normalized.source != screenState.drawerProfile.source) {
            drawerImageRequest += 1
            pendingDrawerImageNight = null
            pendingDrawerImageUri = null
        }
        drawerRevision += 1
        drawerDirty = true
        screenState = screenState.copy(drawerProfile = normalized)
    }

    private fun saveDrawerProfile() {
        val context = requireContext().applicationContext
        // A single writer retains only the latest confirmed change while IO is running.
        pendingDrawerCommit = DrawerProfileCommit(drawerRevision, screenState.drawerProfile)
        if (drawerWriterRunning) return
        drawerWriterRunning = true
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(NonCancellable) {
                    while (true) {
                        val commit = pendingDrawerCommit ?: break
                        pendingDrawerCommit = null
                        committingDrawerCommit = commit
                        try {
                            val saved = withContext(Dispatchers.IO) {
                                NgDrawerProfileStore.update(context, commit.profile)
                            }
                            // Store materializes and releases cache files. Remap only those paths,
                            // even when newer background settings must not be overwritten.
                            screenState = screenState.copy(drawerProfile = remapDrawerImages(
                                screenState.drawerProfile, commit.profile, saved,
                            ))
                            pendingDrawerCommit = pendingDrawerCommit?.let { queued ->
                                queued.copy(profile = remapDrawerImages(queued.profile, commit.profile, saved))
                            }
                            if (drawerRevision == commit.revision) {
                                screenState = screenState.copy(drawerProfile = saved)
                                drawerDirty = false
                            }
                        } catch (error: Exception) {
                            toastOnUi(context.getString(R.string.ng_drawer_theme_save_failed, error.message.orEmpty()))
                        } finally {
                            committingDrawerCommit = null
                            discardUnusedDrawerDraftImages(context)
                        }
                    }
                }
            } finally {
                drawerWriterRunning = false
                discardUnusedDrawerDraftImages(context)
            }
        }
    }

    private fun remapDrawerImages(
        value: NgThemeDrawerProfile,
        source: NgThemeDrawerProfile,
        saved: NgThemeDrawerProfile,
    ): NgThemeDrawerProfile = value.copy(
        light = if (source.light.imagePath != null && value.light.imagePath == source.light.imagePath) {
            value.light.copy(imagePath = saved.light.imagePath)
        } else value.light,
        dark = if (source.dark.imagePath != null && value.dark.imagePath == source.dark.imagePath) {
            value.dark.copy(imagePath = saved.dark.imagePath)
        } else value.dark,
    )

    private fun selectCurrentDrawerImage(night: Boolean) {
        drawerImageRequest += 1
        pendingDrawerImageNight = night
        pendingDrawerImageUri = null
        selectDrawerImage.launch(arrayOf("image/*"))
    }

    private fun importDrawerImage(uri: Uri, night: Boolean, request: Long) {
        if (drawerImageCopyOwner != null) return
        val context = requireContext().applicationContext
        val owner = UUID.randomUUID().toString()
        drawerImageCopyOwner = owner
        viewLifecycleOwner.lifecycleScope.launch {
            var copiedPath: String? = null
            try {
                val path = NgThemeDrawerAssets.copyDraft(context, uri)
                copiedPath = path
                if (drawerViewActive && drawerImageCopyOwner == owner && drawerImageRequest == request) {
                    drawerDraftImages += path
                    // The chooser owns the target day, but other fields come from the latest draft.
                    val current = screenState.drawerProfile
                    setDrawerProfileDraft(current.updated(
                        night, current.forNight(night).copy(imagePath = path),
                    ).copy(source = "custom_image"))
                    pendingDrawerImageNight = null
                    pendingDrawerImageUri = null
                    saveDrawerProfile()
                    copiedPath = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (drawerImageCopyOwner == owner && drawerImageRequest == request) {
                    pendingDrawerImageNight = null
                    pendingDrawerImageUri = null
                    toastOnUi(context.getString(R.string.ng_theme_background_copy_failed, error.message.orEmpty()))
                }
            } finally {
                NgThemeDrawerAssets.discardDraft(context, copiedPath)
                if (drawerImageCopyOwner == owner) {
                    drawerImageCopyOwner = null
                    val nextUri = pendingDrawerImageUri
                    val nextNight = pendingDrawerImageNight
                    if (drawerViewActive && nextUri != null && nextNight != null && drawerImageRequest != request) {
                        importDrawerImage(Uri.parse(nextUri), nextNight, drawerImageRequest)
                    }
                }
            }
        }
    }

    private fun discardUnusedDrawerDraftImages(context: android.content.Context) {
        val retained = buildSet {
            if (drawerViewActive) addDrawerImagePaths(screenState.drawerProfile)
            pendingDrawerCommit?.profile?.let { addDrawerImagePaths(it) }
            committingDrawerCommit?.profile?.let { addDrawerImagePaths(it) }
        }
        val disposable = drawerDraftImages.filterNot { it in retained }
        disposable.forEach { NgThemeDrawerAssets.discardDraft(context, it) }
        drawerDraftImages.removeAll(disposable.toSet())
    }

    private fun MutableSet<String>.addDrawerImagePaths(profile: NgThemeDrawerProfile) {
        profile.light.imagePath?.let { add(it) }
        profile.dark.imagePath?.let { add(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingDrawerImageNight?.let { outState.putBoolean(DRAWER_IMAGE_NIGHT, it) }
        outState.putString(DRAWER_IMAGE_URI, pendingDrawerImageUri)
        outState.putLong(DRAWER_IMAGE_REQUEST, drawerImageRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        drawerViewActive = false
        drawerImageCopyOwner = null
        // View recreation preserves confirmed work, never an unconfirmed draft.
        val confirmed = pendingDrawerCommit ?: committingDrawerCommit
        if (confirmed != null) {
            drawerRevision = confirmed.revision
            screenState = screenState.copy(drawerProfile = confirmed.profile)
        } else {
            context?.let { screenState = screenState.copy(drawerProfile = NgDrawerProfileStore.snapshot(it)) }
        }
        drawerDirty = false
        context?.let { discardUnusedDrawerDraftImages(it) }
        super.onDestroyView()
    }

    private data class DrawerProfileCommit(val revision: Long, val profile: NgThemeDrawerProfile)

    private fun setBookshelfTopBarStyle(style: BookshelfTopBarStyle) {
        if (style == screenState.bookshelfTopBarStyle) return
        AppConfig.bookshelfTopBarStyle = style
        screenState = screenState.copy(bookshelfTopBarStyle = style)
    }

    private fun setBookshelfFloatingDockTopDistanceDraft(value: Int) {
        val normalized = BookshelfFloatingDockConfig.normalizeTopDistancePx(value)
        if (normalized == screenState.bookshelfFloatingDockTopDistancePx) return
        screenState = screenState.copy(bookshelfFloatingDockTopDistancePx = normalized)
    }

    private fun saveBookshelfFloatingDockTopDistance() {
        AppConfig.bookshelfFloatingDockTopDistancePx =
            screenState.bookshelfFloatingDockTopDistancePx
    }

    private fun setBookshelfFloatingDockTransparencyDraft(value: Int) {
        val normalized = BookshelfFloatingDockConfig.normalizeTransparencyPercent(value)
        if (normalized == screenState.bookshelfFloatingDockTransparency) return
        screenState = screenState.copy(bookshelfFloatingDockTransparency = normalized)
    }

    private fun saveBookshelfFloatingDockTransparency() {
        AppConfig.bookshelfFloatingDockTransparency =
            screenState.bookshelfFloatingDockTransparency
    }

    private fun setBookshelfFloatingDockSearchPosition(
        position: BookshelfFloatingDockSearchPosition
    ) {
        if (position == screenState.bookshelfFloatingDockSearchPosition) return
        AppConfig.bookshelfFloatingDockSearchPosition = position
        screenState = screenState.copy(bookshelfFloatingDockSearchPosition = position)
    }

    private fun setHideSystemNavigationBar(enabled: Boolean) {
        if (screenState.hideSystemNavigationBar == enabled) return
        AppConfig.hideSystemNavigationBar = enabled
        screenState = screenState.copy(hideSystemNavigationBar = enabled)
        requireActivity().applyAppNavigationBarVisibility()
    }

    private fun setAutoRefresh(enabled: Boolean) {
        putPrefBoolean(PreferKey.autoRefresh, enabled)
        screenState = screenState.copy(autoRefresh = enabled)
    }

    private fun setOnlyUpdateRead(enabled: Boolean) {
        putPrefBoolean(PreferKey.onlyUpdateRead, enabled)
        screenState = screenState.copy(onlyUpdateRead = enabled)
    }

    private fun setDefaultToRead(enabled: Boolean) {
        putPrefBoolean(PreferKey.defaultToRead, enabled)
        screenState = screenState.copy(defaultToRead = enabled)
    }

    private fun setShowHome(enabled: Boolean) {
        putPrefBoolean(PreferKey.showHome, enabled)
        screenState = screenState.copy(showHome = enabled)
        postEvent(EventBus.NOTIFY_MAIN, true)
    }

    private fun setShowDiscovery(enabled: Boolean) {
        putPrefBoolean(PreferKey.showDiscovery, enabled)
        screenState = screenState.copy(showDiscovery = enabled)
        postEvent(EventBus.NOTIFY_MAIN, true)
    }

    private fun setShowRss(enabled: Boolean) {
        putPrefBoolean(PreferKey.showRss, enabled)
        screenState = screenState.copy(showRss = enabled)
        postEvent(EventBus.NOTIFY_MAIN, true)
    }

    private fun setDefaultHomePage(value: String) {
        putPrefString(PreferKey.defaultHomePage, value)
        screenState = screenState.copy(defaultHomePage = value)
    }

    private fun openFontScaleEditor() {
        val storedScale = getPrefInt(PreferKey.fontScale, 0)
        val followSystem = storedScale !in 8..16
        fontScaleEditorState = ThemeFontScaleEditorState(
            scale = if (followSystem) {
                systemFontScaleForEditor()
            } else {
                storedScale / 10f
            },
            followSystem = followSystem
        )
    }

    private fun saveFontScaleEditor() {
        val editorState = fontScaleEditorState ?: return
        putPrefInt(
            PreferKey.fontScale,
            if (editorState.followSystem) {
                0
            } else {
                (editorState.scale * 10f).roundToInt().coerceIn(8, 16)
            }
        )
        fontScaleEditorState = null
        recreateActivities()
    }

    private fun systemFontScaleForEditor(): Float {
        return (sysConfiguration.fontScale * 10f)
            .roundToInt()
            .coerceIn(8, 16) / 10f
    }

    private fun openBackgroundEditor(dark: Boolean) {
        val imageKey = if (dark) PreferKey.bgImageN else PreferKey.bgImage
        val blurKey = if (dark) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        backgroundEditorState = ThemeBackgroundEditorState(
            dark = dark,
            path = getPrefString(imageKey).takeUnless { it.isNullOrBlank() },
            blur = getPrefInt(blurKey, 0).coerceIn(0, 25)
        )
    }

    private fun selectBackgroundImage(dark: Boolean) {
        selectImage.launch(if (dark) requestCodeBgDark else requestCodeBgLight)
    }

    private fun updateBackgroundDraft(dark: Boolean, path: String) {
        lifecycleScope.launch {
            val editorState = backgroundEditorState ?: return@launch
            if (editorState.dark != dark) return@launch
            backgroundEditorState = editorState.copy(
                path = path,
                blur = if (path.endsWith(".9.png", ignoreCase = true)) 0 else editorState.blur
            )
        }
    }

    private fun saveBackgroundEditor() {
        val editorState = backgroundEditorState ?: return
        val imageKey = if (editorState.dark) PreferKey.bgImageN else PreferKey.bgImage
        val blurKey = if (editorState.dark) {
            PreferKey.bgImageNBlurring
        } else {
            PreferKey.bgImageBlurring
        }
        val changed = getPrefString(imageKey).takeUnless { it.isNullOrBlank() } != editorState.path ||
            getPrefInt(blurKey, 0) != editorState.blur
        val wasFoundation = NgThemeModeStore.isCustomFoundation(requireContext())
        editorState.path.takeUnless { it.isNullOrBlank() }?.let { path ->
            putPrefString(imageKey, path)
        } ?: removePref(imageKey)
        putPrefInt(
            blurKey,
            if (editorState.path?.endsWith(".9.png", ignoreCase = true) == true) {
                0
            } else {
                editorState.blur.coerceIn(0, 25)
            }
        )
        backgroundEditorState = null
        if (wasFoundation && (changed || !editorState.path.isNullOrBlank())) {
            NgThemeModeStore.activateManualAppearance(requireContext())
            refreshContent()
        } else {
            onBackgroundChanged(editorState.dark)
        }
    }

    private fun onBackgroundChanged(isNightTheme: Boolean) {
        view?.post {
            if (!isAdded) return@post
            refreshContent()
            if (AppConfig.isNightTheme == isNightTheme) {
                ThemeConfig.applyTheme(requireContext())
                recreateActivities()
            }
        }
    }

    private fun recreateActivities() {
        postEvent(EventBus.RECREATE, "")
    }

    private fun copyBgFromUri(uri: Uri, storageKey: String, success: (String) -> Unit) {
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
                        ".$imageType"
                    }
                    val fileName = MD5Utils.md5Encode(url) + suffix
                    file = FileUtils.createFileIfNotExist(file, storageKey, fileName)
                    res.body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    if (isAdded && context != null) success(file.absolutePath)
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
                file = FileUtils.createFileIfNotExist(file, storageKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                success(file.absolutePath)
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    private companion object {
        const val DEFAULT_LAUNCHER_ICON = "ic_launcher"
        const val DRAWER_IMAGE_NIGHT = "drawerImageNight"
        const val DRAWER_IMAGE_URI = "drawerImageUri"
        const val DRAWER_IMAGE_REQUEST = "drawerImageRequest"
    }
}
