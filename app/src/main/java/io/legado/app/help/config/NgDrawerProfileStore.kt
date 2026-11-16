package io.legado.app.help.config

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.WorkerThread
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class NgDrawerProfileState(
    val profile: NgThemeDrawerProfile? = null,
) {
    fun snapshot(): NgThemeDrawerProfile = profile ?: NgThemeDrawerProfile()
}

/** Current drawer settings are independent of the selected theme's saved snapshot. */
internal object NgDrawerProfileStore {
    private val lock = Any()
    private val state = MutableStateFlow(NgDrawerProfileState())
    private var preferences: SharedPreferences? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == null || key == PreferKey.ngDrawerBackground) state.value = read(prefs)
    }

    fun observe(context: Context): StateFlow<NgDrawerProfileState> {
        ensureInitialized(context)
        return state.asStateFlow()
    }

    fun current(context: Context): NgThemeDrawerProfile? {
        ensureInitialized(context)
        return state.value.profile
    }

    fun snapshot(context: Context): NgThemeDrawerProfile {
        ensureInitialized(context)
        return state.value.snapshot()
    }

    @WorkerThread
    fun update(context: Context, profile: NgThemeDrawerProfile): NgThemeDrawerProfile = synchronized(lock) {
        val prepared = prepare(context, profile)
        try {
            commit(context, prepared)
        } catch (error: Throwable) {
            prepared.discard()
            throw error
        }
    }

    /** Theme callers prepare images on IO before applying colors and preferences on Main. */
    @WorkerThread
    fun prepare(context: Context, profile: NgThemeDrawerProfile): NgPreparedDrawerProfile =
        NgThemeDrawerAssets.prepareCurrent(context, profile.normalized())

    fun commit(context: Context, prepared: NgPreparedDrawerProfile): NgThemeDrawerProfile = synchronized(lock) {
        ensureInitialized(context)
        val prefs = requireNotNull(preferences)
        val previous = read(prefs)
        val resolved = prepared.profile.normalized()
        try {
            commitDrawerPreferences(prefs, resolved)
        } catch (error: Throwable) {
            state.value = read(prefs)
            throw error
        }
        prepared.markCommitted()
        state.value = read(prefs)
        NgThemeDrawerAssets.discardDrafts(context, prepared.sourceProfile)
        NgThemeDrawerAssets.removeReplacedCurrent(context, previous.profile, resolved)
        resolved
    }

    fun reloadAfterRestore(context: Context) {
        synchronized(lock) {
            val prefs = context.applicationContext.defaultSharedPreferences
            if (preferences !== prefs) {
                preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                preferences = prefs
                prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
            }
            state.value = read(prefs)
        }
    }

    private fun ensureInitialized(context: Context) {
        if (preferences != null) return
        synchronized(lock) {
            if (preferences != null) return
            val prefs = context.applicationContext.defaultSharedPreferences
            state.value = read(prefs)
            preferences = prefs
            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        }
    }

    private fun read(prefs: SharedPreferences): NgDrawerProfileState {
        val profile = prefs.getString(PreferKey.ngDrawerBackground, null)?.let { raw ->
            runCatching { GSON.fromJson(raw, NgThemeDrawerProfile::class.java)?.normalized() }.getOrNull()
        }
        return NgDrawerProfileState(profile)
    }
}

/** Restore in-memory preferences after a failed disk commit before releasing prepared images. */
internal fun commitDrawerPreferences(prefs: SharedPreferences, profile: NgThemeDrawerProfile) {
    val key = PreferKey.ngDrawerBackground
    val existed = prefs.contains(key)
    val previous = prefs.getString(key, null)
    try {
        check(prefs.edit().putString(key, GSON.toJson(profile)).commit()) { "无法保存抽屉背景" }
    } catch (error: Throwable) {
        val rollback = prefs.edit()
        if (existed) rollback.putString(key, previous) else rollback.remove(key)
        rollback.apply()
        throw error
    }
}
