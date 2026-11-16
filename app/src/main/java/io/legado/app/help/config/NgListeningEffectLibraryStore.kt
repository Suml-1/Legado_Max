package io.legado.app.help.config

import android.content.Context
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** A listening package never owns App theme settings, cover albums or the App selection. */
internal object NgListeningEffectLibraryStore {
    // Listening data follows the existing listening backup policy, never the App appearance module.
    private const val KEY = "listeningEffectPackages.v1"
    private val lock = Any()
    private var initialized = false
    private val state = MutableStateFlow(NgThemeLibraryState())

    fun observe(context: Context): StateFlow<NgThemeLibraryState> {
        ensureInitialized(context)
        return state.asStateFlow()
    }

    fun current(context: Context): NgThemeLibraryState {
        ensureInitialized(context)
        return state.value
    }

    fun allEffects(context: Context): List<NgManagedTheme> = current(context).savedThemes

    fun withInstalledEffect(context: Context, id: String, action: () -> Unit) = synchronized(lock) {
        ensureInitialized(context)
        require(state.value.savedThemes.any { it.id == id }) { "请先导入听书特效" }
        action()
    }

    fun uniqueName(context: Context, requested: String): String {
        val base = requested.trim().ifBlank { "听书特效" }
        val names = allEffects(context).map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in names) return base
        return generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { it.lowercase() !in names }
    }

    fun addOrReplace(context: Context, effect: NgManagedTheme, matchName: Boolean = false): NgManagedTheme = synchronized(lock) {
        ensureInitialized(context)
        NgEffectPackageKind.LISTENING_EFFECT.requireContent(effect)
        var candidate = effect.normalized()
        require(candidate.id.isNotBlank() && candidate.name.isNotBlank() && !candidate.isBuiltIn) { "听书特效数据不完整" }
        val root = candidate.packageRootPath?.let(::File)?.canonicalFile ?: error("听书特效包缺失")
        val owned = File(context.filesDir, NgEffectPackageKind.LISTENING_EFFECT.directory).canonicalFile
        require(root.parentFile == owned && root.isDirectory) { "听书特效包目录无效" }
        if (state.value.savedThemes.any { it.id != candidate.id && it.packageRootPath == candidate.packageRootPath }) {
            candidate = NgThemePackageManager.copyOwnedTheme(context, candidate)
        }
        val current = state.value
        val replaced = current.savedThemes.filter { it.id == candidate.id || matchName && it.name.equals(candidate.name, true) }
        val updated = (current.savedThemes - replaced.toSet() + candidate).sortedBy { it.name.lowercase() }
        try {
            persist(context, updated)
        } catch (error: Throwable) {
            if (candidate.packageRootPath != effect.packageRootPath) {
                candidate.packageRootPath?.let { NgThemeArchive.deleteOwnedRoot(context, it, NgEffectPackageKind.LISTENING_EFFECT) }
            }
            throw error
        }
        state.value = NgThemeLibraryState(updated)
        EffectThemeRuntime.invalidate(candidate.id, "player")
        candidate
    }

    fun saveResourceEffect(context: Context, effect: NgManagedTheme, expected: NgManagedTheme?): NgManagedTheme = synchronized(lock) {
        ensureInitialized(context)
        check(state.value.savedThemes.firstOrNull { it.id == effect.id } == expected) { "听书特效已变化，请重新检查" }
        addOrReplace(context, effect)
    }

    suspend fun removeAsync(context: Context, id: String): NgManagedTheme? = withContext(NonCancellable + Dispatchers.IO) {
        synchronized(lock) {
            ensureInitialized(context)
            val removed = state.value.savedThemes.firstOrNull { it.id == id } ?: return@synchronized null
            val updated = state.value.savedThemes.filterNot { it.id == id }
            persist(context, updated)
            state.value = NgThemeLibraryState(updated)
            EffectThemeRuntime.invalidate(id, "player")
            removed.packageRootPath?.takeIf { path -> updated.none { it.packageRootPath == path } }
                ?.let { NgThemeArchive.deleteOwnedRoot(context, it, NgEffectPackageKind.LISTENING_EFFECT) }
            removed
        }
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val type = object : TypeToken<List<NgManagedTheme>>() {}.type
            val saved = runCatching {
                GSON.fromJson<List<NgManagedTheme>>(context.defaultSharedPreferences.getString(KEY, null), type)
                    .orEmpty().filter { !it.isBuiltIn && it.id.isNotBlank() && it.name.isNotBlank() &&
                        it.effect?.targets == listOf("player") }.map(NgManagedTheme::normalized)
            }.getOrDefault(emptyList())
            state.value = NgThemeLibraryState(saved)
            initialized = true
        }
    }

    private fun persist(context: Context, effects: List<NgManagedTheme>) {
        check(context.defaultSharedPreferences.edit().putString(KEY, GSON.toJson(effects)).commit()) {
            "无法保存听书特效列表"
        }
    }
}

/** Shared transaction plumbing, with explicit independent storage owners. */
internal object NgEffectPackageLibrary {
    fun current(context: Context, kind: NgEffectPackageKind): NgThemeLibraryState = when (kind) {
        NgEffectPackageKind.THEME -> NgThemeLibraryStore.current(context)
        NgEffectPackageKind.LISTENING_EFFECT -> NgListeningEffectLibraryStore.current(context)
    }
    fun observe(context: Context, kind: NgEffectPackageKind): StateFlow<NgThemeLibraryState> = when (kind) {
        NgEffectPackageKind.THEME -> NgThemeLibraryStore.observe(context)
        NgEffectPackageKind.LISTENING_EFFECT -> NgListeningEffectLibraryStore.observe(context)
    }
    fun uniqueName(context: Context, kind: NgEffectPackageKind, name: String): String = when (kind) {
        NgEffectPackageKind.THEME -> NgThemeLibraryStore.uniqueName(context, name)
        NgEffectPackageKind.LISTENING_EFFECT -> NgListeningEffectLibraryStore.uniqueName(context, name)
    }
    fun addOrReplace(context: Context, kind: NgEffectPackageKind, value: NgManagedTheme, matchName: Boolean = false): NgManagedTheme {
        kind.requireContent(value)
        return when (kind) {
            NgEffectPackageKind.THEME -> NgThemeLibraryStore.addOrReplace(context, value, matchName)
            NgEffectPackageKind.LISTENING_EFFECT -> NgListeningEffectLibraryStore.addOrReplace(context, value, matchName)
        }
    }
    fun saveResource(context: Context, kind: NgEffectPackageKind, value: NgManagedTheme, expected: NgManagedTheme?): NgManagedTheme {
        kind.requireContent(value)
        return when (kind) {
            NgEffectPackageKind.THEME -> NgThemeLibraryStore.saveResourceTheme(context, value, expected)
            NgEffectPackageKind.LISTENING_EFFECT -> NgListeningEffectLibraryStore.saveResourceEffect(context, value, expected)
        }
    }
}
