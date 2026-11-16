package io.legado.effect.api

import android.content.Context
import android.view.View
import java.io.InputStream

/** Stable API 1. A complete theme owns its renderer; no theme identity is hard-coded here. */
interface EffectThemePlugin {
    /** Called on the UI thread. Return a new, unattached session for every use position. */
    fun createSession(context: Context, assets: EffectThemeAssets, callbacks: EffectThemeCallbacks): EffectThemeSession
}

interface EffectThemeAssets {
    /** Opens an asset-relative path from this immutable installed theme revision. */
    fun open(path: String): InputStream
}

interface EffectThemeCallbacks {
    /** First complete scene frame is available. May be called off the UI thread. */
    fun onReady()
    /** Rendering cannot continue. Host must marshal to UI and ignore obsolete sessions. */
    fun onError(message: String)
}

interface EffectThemeSession {
    /** UI thread; the same unattached View for this session. Host owns attaching and sizing. */
    fun getView(): View

    /** UI thread. Parameters are complete snapshots. Null timeline uses a local clock. */
    fun update(parameters: Map<String, String>, animationAllowed: Boolean, timelineOriginNanos: Long?)

    /** Idempotent. Run completion exactly once for this call, after all GL/decoder resources stop.
     * Completion may be off the UI thread. The host must not start a successor before it fires.
     */
    fun release(onReleased: Runnable)
}
