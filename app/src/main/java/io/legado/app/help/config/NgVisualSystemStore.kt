package io.legado.app.help.config

import android.content.Context
import androidx.core.content.edit
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NgVisualSystem(val storedValue: String) {
    TRANSPARENT_GLASS("transparent_glass"),
    LIQUID_GLASS("liquid_glass");

    companion object {
        val DEFAULT = LIQUID_GLASS

        fun fromStoredValue(value: String?): NgVisualSystem {
            return entries.firstOrNull { it.storedValue == value } ?: DEFAULT
        }
    }
}

/**
 * NG 视觉体系的独立持久化入口。
 *
 * 视觉体系只决定组件材质与反馈方式，不接管主题颜色、背景、字体或页面结构。
 */
object NgVisualSystemStore {

    private val lock = Any()
    private var initialized = false
    private val mutableState = MutableStateFlow<NgVisualSystem?>(null)

    fun observe(context: Context): StateFlow<NgVisualSystem?> {
        ensureInitialized(context)
        return mutableState.asStateFlow()
    }

    fun current(context: Context): NgVisualSystem {
        ensureInitialized(context)
        return requireNotNull(mutableState.value)
    }

    fun update(context: Context, visualSystem: NgVisualSystem) {
        ensureInitialized(context)
        if (mutableState.value == visualSystem) return
        context.defaultSharedPreferences.edit {
            putString(PreferKey.ngVisualSystem, visualSystem.storedValue)
        }
        mutableState.value = visualSystem
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            mutableState.value = NgVisualSystem.fromStoredValue(
                context.defaultSharedPreferences.getString(PreferKey.ngVisualSystem, null)
            )
            initialized = true
        }
    }
}
