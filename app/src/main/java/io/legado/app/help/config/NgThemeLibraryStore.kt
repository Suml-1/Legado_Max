package io.legado.app.help.config

import android.content.Context
import io.legado.app.R
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.constant.AppLog
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.statusBarHeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

internal const val NG_MANAGED_THEME_SCHEMA_VERSION = 1
internal const val NG_BUILT_IN_THEME_ID_PREFIX = "builtin."

@Keep
internal data class NgThemeBackground(
    @SerializedName("path") val path: String? = null,
    @SerializedName("blur") val blur: Int = 0
)

@Keep
internal data class NgThemeBarProfile(
    @SerializedName("useFloatingBottomBar")
    val useFloatingBottomBar: Boolean? = null,
    @SerializedName("floatingBottomBarBottomDistancePx")
    val floatingBottomBarBottomDistancePx: Int? = null,
    @SerializedName("floatingBottomBarTransparency")
    val floatingBottomBarTransparency: Int? = null,
    @SerializedName("bookshelfTopBarStyle")
    val bookshelfTopBarStyle: Int? = null,
    @SerializedName("bookshelfFloatingDockTopDistancePx")
    val bookshelfFloatingDockTopDistancePx: Int? = null,
    @SerializedName("bookshelfFloatingDockTransparency")
    val bookshelfFloatingDockTransparency: Int? = null,
    @SerializedName("bookshelfFloatingDockSearchPosition")
    val bookshelfFloatingDockSearchPosition: Int? = null,
) {
    fun normalized(): NgThemeBarProfile = copy(
        floatingBottomBarBottomDistancePx = floatingBottomBarBottomDistancePx?.let {
            FloatingBottomBarConfig.normalizeBottomDistancePx(it)
        },
        floatingBottomBarTransparency = floatingBottomBarTransparency?.let {
            FloatingBottomBarConfig.normalizeTransparencyPercent(it)
        },
        bookshelfTopBarStyle = bookshelfTopBarStyle?.let {
            BookshelfTopBarStyle.fromValue(it).value
        },
        bookshelfFloatingDockTopDistancePx = bookshelfFloatingDockTopDistancePx?.let {
            BookshelfFloatingDockConfig.normalizeTopDistancePx(it)
        },
        bookshelfFloatingDockTransparency = bookshelfFloatingDockTransparency?.let {
            BookshelfFloatingDockConfig.normalizeTransparencyPercent(it)
        },
        bookshelfFloatingDockSearchPosition = bookshelfFloatingDockSearchPosition?.let {
            BookshelfFloatingDockSearchPosition.fromValue(it).value
        },
    )

    companion object {
        const val EDITOR_DEFAULT_BOTTOM_DISTANCE_PX = 40
        const val EDITOR_DEFAULT_TOP_DISTANCE_PX = 360
    }
}

internal fun NgThemeBarProfile?.withFallback(
    fallback: NgThemeBarProfile
): NgThemeBarProfile {
    val profile = this
    return NgThemeBarProfile(
        useFloatingBottomBar = profile?.useFloatingBottomBar
            ?: fallback.useFloatingBottomBar,
        floatingBottomBarBottomDistancePx = profile?.floatingBottomBarBottomDistancePx
            ?: fallback.floatingBottomBarBottomDistancePx,
        floatingBottomBarTransparency = profile?.floatingBottomBarTransparency
            ?: fallback.floatingBottomBarTransparency,
        bookshelfTopBarStyle = profile?.bookshelfTopBarStyle
            ?: fallback.bookshelfTopBarStyle,
        bookshelfFloatingDockTopDistancePx = profile?.bookshelfFloatingDockTopDistancePx
            ?: fallback.bookshelfFloatingDockTopDistancePx,
        bookshelfFloatingDockTransparency = profile?.bookshelfFloatingDockTransparency
            ?: fallback.bookshelfFloatingDockTransparency,
        bookshelfFloatingDockSearchPosition =
            profile?.bookshelfFloatingDockSearchPosition
                ?: fallback.bookshelfFloatingDockSearchPosition,
    ).normalized()
}

@Keep
internal data class NgThemeNavigationAssets(
    @SerializedName("home") val home: String? = null,
    @SerializedName("bookshelf") val bookshelf: String? = null,
    @SerializedName("explore") val explore: String? = null,
    @SerializedName("rss") val rss: String? = null,
    @SerializedName("my") val my: String? = null,
) {
    fun normalized(): NgThemeNavigationAssets = copy(
        home = home.normalizedPackageRelativePath(),
        bookshelf = bookshelf.normalizedPackageRelativePath(),
        explore = explore.normalizedPackageRelativePath(),
        rss = rss.normalizedPackageRelativePath(),
        my = my.normalizedPackageRelativePath(),
    )
}

@Keep
internal data class NgThemeResourceProfile(
    @SerializedName("navigation")
    val navigation: NgThemeNavigationAssets = NgThemeNavigationAssets(),
    @SerializedName("appFont") val appFont: String? = null,
) {
    fun normalized(): NgThemeResourceProfile = copy(
        navigation = navigation.normalized(),
        appFont = appFont.normalizedPackageRelativePath(),
    )
}

@Keep
internal data class NgThemeCoverProfile(
    @SerializedName("applyAlbumSelection")
    val applyAlbumSelection: Boolean = false,
    @SerializedName("albumId") val albumId: String? = null,
    @SerializedName("loadOnlyWifi") val loadOnlyWifi: Boolean? = null,
    @SerializedName("useDefault") val useDefault: Boolean? = null,
    @SerializedName("showName") val showName: Boolean? = null,
    @SerializedName("showAuthor") val showAuthor: Boolean? = null,
    @SerializedName("showNameDark") val showNameDark: Boolean? = null,
    @SerializedName("showAuthorDark") val showAuthorDark: Boolean? = null,
) {
    fun normalized(): NgThemeCoverProfile = copy(
        albumId = albumId?.trim()?.takeIf(String::isNotEmpty),
    )
}

@Keep
internal data class NgManagedTheme(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = NG_MANAGED_THEME_SCHEMA_VERSION,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("colors") val colors: NgColorSystem,
    @SerializedName("lightBackground")
    val lightBackground: NgThemeBackground = NgThemeBackground(),
    @SerializedName("darkBackground")
    val darkBackground: NgThemeBackground = NgThemeBackground(),
    @SerializedName("transparentAppBars")
    val transparentAppBars: Boolean = true,
    @SerializedName("barProfile")
    val barProfile: NgThemeBarProfile? = null,
    @SerializedName("packageRootPath") val packageRootPath: String? = null,
    @SerializedName("resourceProfile")
    val resourceProfile: NgThemeResourceProfile? = null,
    @SerializedName("coverProfile")
    val coverProfile: NgThemeCoverProfile? = null,
    @SerializedName("ownedCoverAlbumIds")
    val ownedCoverAlbumIds: List<String>? = null,
    @SerializedName("effect") val effect: NgEffectThemeDescriptor? = null,
    @SerializedName("effectValues") val effectValues: Map<String, String> = emptyMap(),
    @SerializedName("publication") val publication: NgThemePublication? = null,
    @SerializedName("drawerProfile")
    val drawerProfile: NgThemeDrawerProfile? = null,
    @SerializedName("centerCropBackground")
    val centerCropBackground: Boolean? = null,
) {
    fun normalized(): NgManagedTheme = copy(
        schemaVersion = NG_MANAGED_THEME_SCHEMA_VERSION,
        id = id.trim(),
        name = name.trim(),
        colors = colors.normalized(),
        lightBackground = lightBackground.copy(blur = lightBackground.blur.coerceIn(0, 25)),
        darkBackground = darkBackground.copy(blur = darkBackground.blur.coerceIn(0, 25)),
        transparentAppBars = true,
        barProfile = barProfile?.normalized(),
        resourceProfile = resourceProfile?.normalized() ?: NgThemeResourceProfile(),
        coverProfile = coverProfile?.normalized(),
        ownedCoverAlbumIds = ownedCoverAlbumIds.orEmpty()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .takeIf { it.isNotEmpty() },
        drawerProfile = drawerProfile?.normalized(),
    )

    fun resolvePackageAsset(relativePath: String?): File? {
        val normalizedPath = relativePath.normalizedPackageRelativePath() ?: return null
        val root = packageRootPath?.let(::File)?.canonicalFile ?: return null
        val target = File(root, normalizedPath).canonicalFile
        return target.takeIf {
            it.toPath().startsWith(root.toPath()) && it.isFile
        }
    }
}

internal val NgManagedTheme.isBuiltIn: Boolean
    get() = id.startsWith(NG_BUILT_IN_THEME_ID_PREFIX)

private fun String?.normalizedPackageRelativePath(): String? {
    val normalized = this?.trim()?.replace('\\', '/')
        ?.takeIf { it.isNotEmpty() && !it.startsWith('/') }
        ?: return null
    val segments = normalized.split('/')
    return normalized.takeIf {
        ':' !in it && segments.none { segment ->
            segment.isEmpty() || segment == "." || segment == ".."
        }
    }
}

internal data class NgThemeLibraryState(
    val savedThemes: List<NgManagedTheme> = emptyList(),
    val activeThemeId: String? = null
)

/** 新主题管理只使用 NG v1 记录，不读取或迁移旧 themeConfig.json。 */
internal object NgThemeLibraryStore {

    private const val THEMES_KEY = "ngManagedThemes.v1"
    private const val ACTIVE_THEME_KEY = "ngActiveManagedThemeId.v1"
    private val lock = Any()
    private var initialized = false
    private val mutableState = MutableStateFlow(NgThemeLibraryState())

    fun observe(context: Context): StateFlow<NgThemeLibraryState> {
        ensureInitialized(context)
        return mutableState.asStateFlow()
    }

    fun current(context: Context): NgThemeLibraryState {
        ensureInitialized(context)
        return mutableState.value
    }

    fun allThemes(context: Context): List<NgManagedTheme> = current(context).savedThemes

    fun activeTheme(context: Context): NgManagedTheme? {
        val state = current(context)
        return allThemes(context).firstOrNull { it.id == state.activeThemeId }
    }

    /** Fresh installs use the asset-free base appearance; existing explicit choices are untouched. */
    fun applyDefaultThemeIfNeeded(context: Context) {
        val prefs = context.defaultSharedPreferences
        if (prefs.contains(ACTIVE_THEME_KEY) || prefs.contains(PreferKey.ngThemePresentationMode)) return
        useDefaultAppearance(context)
    }

    private fun useDefaultAppearance(context: Context) {
        synchronized(lock) {
            ensureInitialized(context)
            persistActive(context, null)
            mutableState.value = mutableState.value.copy(activeThemeId = null)
        }
        if (NgThemeModeStore.currentGroup(context) == NgThemeModeGroup.STANDARD) {
            NgThemeModeStore.activateCustomFoundation(context)
        } else {
            NgThemeModeStore.activateInternal(context, NgThemePresentationMode.MULTICOLOR)
        }
    }

    fun snapshotCurrent(context: Context, name: String): NgManagedTheme {
        val state = current(context)
        val active = allThemes(context).firstOrNull { it.id == state.activeThemeId }
        val existing = state.savedThemes.firstOrNull { it.name.equals(name.trim(), true) }
        return NgManagedTheme(
            id = existing?.id ?: "local.${UUID.randomUUID()}",
            name = name.trim(),
            colors = NgColorConfigStore.current(context),
            lightBackground = NgThemeBackground(
                path = context.getPrefString(PreferKey.bgImage),
                blur = context.getPrefInt(PreferKey.bgImageBlurring, 0)
            ),
            darkBackground = NgThemeBackground(
                path = context.getPrefString(PreferKey.bgImageN),
                blur = context.getPrefInt(PreferKey.bgImageNBlurring, 0)
            ),
            transparentAppBars = true,
            barProfile = currentBarProfile(context),
            packageRootPath = active?.packageRootPath,
            resourceProfile = active?.resourceProfile ?: NgThemeResourceProfile(),
            effect = active?.effect,
            effectValues = active?.effectValues.orEmpty(),
            publication = active?.publication,
            ownedCoverAlbumIds = active?.ownedCoverAlbumIds,
            drawerProfile = NgDrawerProfileStore.snapshot(context),
            centerCropBackground = active?.centerCropBackground,
            coverProfile = NgThemeCoverProfile(
                applyAlbumSelection = true,
                albumId = NgCoverAlbumStore.current(context).selectedAlbumId,
                loadOnlyWifi = context.getPrefBoolean(PreferKey.loadCoverOnlyWifi, false),
                useDefault = context.getPrefBoolean(PreferKey.useDefaultCover, false),
                showName = context.getPrefBoolean(PreferKey.coverShowName, true),
                showAuthor = context.getPrefBoolean(PreferKey.coverShowAuthor, true),
                showNameDark = context.getPrefBoolean(PreferKey.coverShowNameN, true),
                showAuthorDark = context.getPrefBoolean(PreferKey.coverShowAuthorN, true),
            ),
        ).normalized()
    }

    fun editableBarProfile(
        context: Context,
        profile: NgThemeBarProfile?
    ): NgThemeBarProfile = profile.withFallback(currentBarProfile(context))

    fun editableDrawerProfile(
        context: Context,
        profile: NgThemeDrawerProfile?,
    ): NgThemeDrawerProfile = (profile ?: NgDrawerProfileStore.snapshot(context)).normalized()

    fun currentThemeName(context: Context): String {
        when (NgThemeModeStore.current(context)) {
            NgThemePresentationMode.MULTICOLOR -> return context.getString(R.string.ng_theme_mode_multicolor)
            NgThemePresentationMode.SOFT_GRADIENT -> return context.getString(R.string.ng_theme_mode_soft_gradient)
            NgThemePresentationMode.EINK -> return context.getString(R.string.theme_mode_eink_short)
            else -> Unit
        }
        val state = current(context)
        allThemes(context).firstOrNull { it.id == state.activeThemeId }?.let { return it.name }
        val dayName = context.getPrefString(PreferKey.dThemeName)
        return when (dayName) {
            null, "", "默认", "经典主题" -> context.getString(R.string.ng_theme_mode_standard)
            else -> dayName
        }
    }

    fun saveCurrent(context: Context, name: String): NgManagedTheme {
        require(name.isNotBlank()) { "主题名称不能为空" }
        val saved = addOrReplace(context, snapshotCurrent(context, name))
        synchronized(lock) {
            persistActive(context, saved.id)
            mutableState.value = mutableState.value.copy(activeThemeId = saved.id)
        }
        return saved
    }

    fun addOrReplace(
        context: Context,
        theme: NgManagedTheme,
        matchName: Boolean = true,
    ): NgManagedTheme = synchronized(lock) {
        ensureInitialized(context)
        NgEffectPackageKind.THEME.requireContent(theme)
        var candidate = theme.normalized()
        if (candidate.effect != null && candidate.packageRootPath != null &&
            allThemes(context).any { it.id != candidate.id && it.packageRootPath == candidate.packageRootPath }) {
            candidate = NgThemePackageManager.copyOwnedTheme(context, candidate)
        }
        require(candidate.id.isNotEmpty() && candidate.name.isNotEmpty()) { "主题数据不完整" }
        require(!candidate.isBuiltIn) { "内置主题不能被覆盖" }
        fun discardCopiedPackage() {
            candidate.packageRootPath?.takeIf { it != theme.packageRootPath }?.let { deleteOwnedPackageRoot(context, it) }
        }
        val prepared = try { NgThemeDrawerAssets.prepare(context, candidate) }
        catch (error: Throwable) { discardCopiedPackage(); throw error }
        val normalized = prepared.theme
        val current = mutableState.value
        val replacedIds = current.savedThemes
            .filter { it.id == normalized.id || (matchName && it.name.equals(normalized.name, true)) }
            .mapTo(hashSetOf()) { it.id }
        val updated = buildList {
            addAll(current.savedThemes.filterNot { it.id in replacedIds })
            add(normalized)
        }.sortedBy { it.name.lowercase() }
        try {
            persistThemes(context, updated)
        } catch (error: Throwable) {
            prepared.discard()
            discardCopiedPackage()
            throw error
        }
        mutableState.value = current.copy(savedThemes = updated)
        EffectThemeRuntime.invalidate(normalized.id)
        NgThemeDrawerAssets.discardDrafts(context, candidate.drawerProfile)
        NgThemeDrawerAssets.removeUnreferenced(
            context,
            current.savedThemes.filter { it.id in replacedIds },
            updated,
        )
        normalized
    }

    /** 资源更新只认原生 ID，并在持久化锁内再次检查用户是否编辑了目标。 */
    internal fun saveResourceTheme(
        context: Context,
        theme: NgManagedTheme,
        expected: NgManagedTheme?,
    ): NgManagedTheme = synchronized(lock) {
        ensureInitialized(context)
        check(mutableState.value.savedThemes.firstOrNull { it.id == theme.id } == expected) {
            "主题已发生变化，请重新检查后更新"
        }
        addOrReplace(context, theme, matchName = false)
    }

    fun rename(context: Context, themeId: String, newName: String): Boolean = synchronized(lock) {
        ensureInitialized(context)
        if (themeId.startsWith(NG_BUILT_IN_THEME_ID_PREFIX)) return@synchronized false
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@synchronized false
        val current = mutableState.value
        if (current.savedThemes.any { it.id != themeId && it.name.equals(trimmed, true) }) {
            return@synchronized false
        }
        val updated = current.savedThemes.map {
            if (it.id == themeId) it.copy(name = trimmed) else it
        }
        if (updated == current.savedThemes) return@synchronized false
        persistThemes(context, updated)
        mutableState.value = current.copy(savedThemes = updated)
        true
    }

    /** Delete owned files off Main; restore the standard appearance only after removal completes. */
    suspend fun removeAsync(context: Context, themeId: String): NgManagedTheme? = withContext(NonCancellable) {
        val applicationContext = context.applicationContext
        val (removed, restoreFallback) = withContext(Dispatchers.IO) {
            synchronized(lock) {
                ensureInitialized(applicationContext)
                val wasActive = (mutableState.value.activeThemeId == themeId || mutableState.value.savedThemes.size == 1) &&
                    NgThemeModeStore.currentGroup(applicationContext) == NgThemeModeGroup.STANDARD
                remove(applicationContext, themeId, restoreFallback = false) to wasActive
            }
        }
        if (removed != null && restoreFallback) {
            withContext(Dispatchers.Main.immediate) {
                // Do not override another selection made while the filesystem operation was running.
                if (current(applicationContext).activeThemeId == null &&
                    NgThemeModeStore.currentGroup(applicationContext) == NgThemeModeGroup.STANDARD
                ) useDefaultAppearance(applicationContext)
            }
        }
        removed
    }

    fun remove(
        context: Context,
        themeId: String,
        restoreFallback: Boolean = true,
    ): NgManagedTheme? = synchronized(lock) {
        ensureInitialized(context)
        if (themeId.startsWith(NG_BUILT_IN_THEME_ID_PREFIX)) return@synchronized null
        val current = mutableState.value
        val removed = current.savedThemes.firstOrNull { it.id == themeId }
            ?: return@synchronized null
        val updated = current.savedThemes.filterNot { it.id == themeId }
        val nextActive = current.activeThemeId.takeUnless { it == themeId }
        persistThemes(context, updated)
        persistActive(context, nextActive)
        mutableState.value = NgThemeLibraryState(updated, nextActive)
        if (current.activeThemeId == themeId) forgetAppliedThemeBackgrounds(context, removed)
        if (updated.isEmpty()) {
            context.defaultSharedPreferences.edit().putBoolean(PreferKey.ngCustomAppearanceEdited, false).apply()
        }
        val orphanedAlbumIds = orphanedCoverAlbumIds(removed, updated)
        NgCoverAlbumStore.removeImported(context, orphanedAlbumIds)
        NgThemeDrawerAssets.removeUnreferenced(context, listOf(removed), updated)
        EffectThemeRuntime.invalidate(removed.id)
        if (restoreFallback && (current.activeThemeId == themeId || updated.isEmpty()) &&
            NgThemeModeStore.currentGroup(context) == NgThemeModeGroup.STANDARD
        ) useDefaultAppearance(context)
        removed.packageRootPath
            ?.takeIf { root -> updated.none { it.packageRootPath == root } }
            ?.let { deleteOwnedPackageRoot(context, it) }
        removed
    }

    /** Removing the selected package also releases its applied image references, including while an internal mode is visible. */
    private fun forgetAppliedThemeBackgrounds(context: Context, removed: NgManagedTheme) {
        val prefs = context.defaultSharedPreferences
        val editor = prefs.edit()
            .putString(PreferKey.ngCustomThemePresentationMode, "standard")
            .putBoolean(PreferKey.ngCustomAppearanceEdited, false)
        listOf(
            PreferKey.bgImage to PreferKey.bgImageBlurring,
            PreferKey.bgImageN to PreferKey.bgImageNBlurring,
        ).forEach { (imageKey, blurKey) ->
            if (removed.ownsBackground(prefs.getString(imageKey, null))) {
                editor.remove(imageKey).remove(blurKey)
            }
        }
        listOf(PreferKey.dThemeName, PreferKey.dNThemeName).forEach { key ->
            if (prefs.getString(key, null) == removed.name) editor.remove(key)
        }
        editor.apply()
    }

    fun detachCoverAlbum(context: Context, albumId: String): Boolean = synchronized(lock) {
        ensureInitialized(context)
        if (albumId.isBlank()) return@synchronized false
        val current = mutableState.value
        var changed = false
        val updated = current.savedThemes.map { theme ->
            val nextOwnedAlbumIds = theme.ownedCoverAlbumIds.orEmpty()
                .filterNot { it == albumId }
                .takeIf { it.isNotEmpty() }
            val nextCoverProfile = theme.coverProfile?.let { profile ->
                if (profile.albumId == albumId) {
                    profile.copy(applyAlbumSelection = false, albumId = null)
                } else {
                    profile
                }
            }
            if (
                nextOwnedAlbumIds != theme.ownedCoverAlbumIds ||
                nextCoverProfile != theme.coverProfile
            ) {
                changed = true
                theme.copy(
                    coverProfile = nextCoverProfile,
                    ownedCoverAlbumIds = nextOwnedAlbumIds,
                )
            } else {
                theme
            }
        }
        if (!changed) return@synchronized false
        persistThemes(context, updated)
        mutableState.value = current.copy(savedThemes = updated)
        true
    }

    /** User-confirmed theme application finishes resource preparation before Main-thread apply. */
    suspend fun applyAsync(context: Context, theme: NgManagedTheme): Boolean = applyWithModeAsync(context, theme, true)

    suspend fun applyStaticAsync(context: Context, theme: NgManagedTheme): Boolean = applyWithModeAsync(context, theme, false)

    internal suspend fun refreshInstalledTheme(context: Context, theme: NgManagedTheme): Boolean {
        val mode = NgThemeModeStore.current(context)
        if (mode != NgThemePresentationMode.STANDARD &&
            !(mode == NgThemePresentationMode.EFFECT_THEME && theme.effect != null)
        ) return true
        fun isCurrent(): Boolean {
            val state = current(context)
            return state.activeThemeId == theme.id && state.savedThemes.firstOrNull { it.id == theme.id } == theme &&
                NgThemeModeStore.current(context) == mode
        }
        return !isCurrent() || applyWithModeAsync(context, theme, mode == NgThemePresentationMode.EFFECT_THEME, ::isCurrent)
    }

    private suspend fun applyWithModeAsync(
        context: Context,
        theme: NgManagedTheme,
        renderEffect: Boolean,
        shouldApply: (() -> Boolean)? = null,
    ): Boolean {
        val applicationContext = context.applicationContext
        var prepared: NgPreparedDrawerProfile? = null
        return try {
            withContext(NonCancellable) {
                prepared = withContext(Dispatchers.IO) {
                    if (theme.effect != null && !renderEffect) {
                        check(EffectThemeRuntime.isIntact(theme)) { "特效主题文件缺失或损坏" }
                    } else if (theme.effect != null) {
                        var status = EffectThemeRuntime.status(applicationContext, theme, "app")
                        if (status == NgEffectThemeStatus.LOAD_ERROR) {
                            EffectThemeRuntime.invalidate(theme.id)
                            status = EffectThemeRuntime.status(applicationContext, theme, "app")
                        }
                        check(status.canRun) { status.message ?: "特效主题不可用" }
                    }
                    theme.drawerProfile?.let { NgDrawerProfileStore.prepare(applicationContext, it) }
                }
                withContext(Dispatchers.Main.immediate) {
                    shouldApply?.invoke() == false || apply(applicationContext, theme, prepared, renderEffect)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.put("设置主题出错\n$error", error, true)
            false
        } finally {
            prepared?.discard()
        }
    }

    fun apply(
        context: Context,
        theme: NgManagedTheme,
        preparedDrawer: NgPreparedDrawerProfile? = null,
        renderEffect: Boolean = true,
    ): Boolean {
        NgEffectPackageKind.THEME.requireContent(theme)
        // Capture display-mode memory before publishing a newly selected package.
        NgThemeModeStore.captureCustomMode(context)
        val previousActiveId = synchronized(lock) {
            ensureInitialized(context)
            val previous = mutableState.value.activeThemeId
            persistActive(context, theme.id)
            mutableState.value = mutableState.value.copy(activeThemeId = theme.id)
            previous
        }
        if (ThemeConfig.applyManagedTheme(context, theme, preparedDrawer, renderEffect)) return true
        synchronized(lock) {
            persistActive(context, previousActiveId)
            mutableState.value = mutableState.value.copy(activeThemeId = previousActiveId)
        }
        return false
    }

    private fun currentBarProfile(context: Context): NgThemeBarProfile = NgThemeBarProfile(
        useFloatingBottomBar = AppConfig.useFloatingBottomBar,
        floatingBottomBarBottomDistancePx = FloatingBottomBarConfig.resolveBottomDistancePx(
            storedDistancePx = AppConfig.floatingBottomBarBottomDistancePx,
            density = context.resources.displayMetrics.density,
        ),
        floatingBottomBarTransparency = AppConfig.floatingBottomBarTransparency,
        bookshelfTopBarStyle = AppConfig.bookshelfTopBarStyle.value,
        bookshelfFloatingDockTopDistancePx = BookshelfFloatingDockConfig.resolveTopDistancePx(
            storedDistancePx = AppConfig.bookshelfFloatingDockTopDistancePx,
            screenWidthPx = context.resources.displayMetrics.widthPixels,
            density = context.resources.displayMetrics.density,
            statusBarHeightPx = context.statusBarHeight,
        ),
        bookshelfFloatingDockTransparency = AppConfig.bookshelfFloatingDockTransparency,
        bookshelfFloatingDockSearchPosition =
            AppConfig.bookshelfFloatingDockSearchPosition.value,
    )

    fun uniqueName(context: Context, requestedName: String): String {
        val base = requestedName.trim().ifEmpty { "导入主题" }
        val names = allThemes(context).mapTo(hashSetOf()) { it.name.lowercase() }
        if (base.lowercase() !in names) return base
        var index = 2
        while ("$base $index".lowercase() in names) index++
        return "$base $index"
    }

    internal fun reloadAfterRestore(context: Context) {
        synchronized(lock) { initialized = false }
        ensureInitialized(context)
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val prefs = context.defaultSharedPreferences
            val saved = prefs.getString(THEMES_KEY, null)?.let { raw ->
                runCatching {
                    GSON.fromJson(raw, Array<NgManagedTheme>::class.java)
                        .orEmpty()
                        .filter { it.schemaVersion == NG_MANAGED_THEME_SCHEMA_VERSION }
                        .map(NgManagedTheme::normalized)
                        .filter {
                            it.id.isNotEmpty() && it.name.isNotEmpty() && !it.isBuiltIn
                        }
                }.getOrDefault(emptyList())
            }.orEmpty()
            mutableState.value = NgThemeLibraryState(
                savedThemes = saved,
                activeThemeId = prefs.getString(ACTIVE_THEME_KEY, null)
            )
            initialized = true
        }
    }

    private fun persistThemes(context: Context, themes: List<NgManagedTheme>) {
        check(
            context.defaultSharedPreferences.edit()
                .putString(THEMES_KEY, GSON.toJson(themes))
                .commit()
        ) { "无法保存主题列表" }
    }

    private fun persistActive(context: Context, themeId: String?) {
        context.defaultSharedPreferences.edit()
            .putString(ACTIVE_THEME_KEY, themeId)
            .apply()
    }

    private fun deleteOwnedPackageRoot(context: Context, path: String) {
        runCatching {
            val root = File(path).canonicalFile
            val owned = File(context.filesDir, NgThemePackageManager.PACKAGE_DIR).canonicalFile
            if (root.parentFile == owned && root.isDirectory) root.deleteRecursively()
        }
    }
}

private fun NgManagedTheme.coverAlbumReferences(): Set<String> = buildSet {
    addAll(ownedCoverAlbumIds.orEmpty())
    coverProfile?.albumId?.let(::add)
}

internal fun orphanedCoverAlbumIds(
    removed: NgManagedTheme,
    remaining: List<NgManagedTheme>,
): Set<String> {
    val retainedAlbumIds = remaining.flatMapTo(hashSetOf()) { it.coverAlbumReferences() }
    return removed.ownedCoverAlbumIds.orEmpty().toSet() - retainedAlbumIds
}

/** A removed package must not retain its background; unrelated manual images remain independent. */
internal fun NgManagedTheme.ownsBackground(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    if (path == lightBackground.path || path == darkBackground.path) return true
    val rootPath = packageRootPath ?: return false
    return runCatching {
        val root = File(rootPath).canonicalFile.toPath()
        val candidate = File(path).canonicalFile.toPath()
        candidate != root && candidate.startsWith(root)
    }.getOrDefault(false)
}
