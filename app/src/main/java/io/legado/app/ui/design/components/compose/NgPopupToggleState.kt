package io.legado.app.ui.design.components.compose

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Keeps an anchored popup from reopening when one tap is delivered first as a
 * popup dismiss and then as a click on the popup's anchor.
 */
@Stable
internal class NgPopupToggleState(
    private val nowMillis: () -> Long = SystemClock::uptimeMillis,
    private val reopenGuardMillis: Long = ViewConfiguration.getDoubleTapTimeout().toLong(),
) {
    var expanded by mutableStateOf(false)
        private set

    private var popupDismissedAtMillis: Long? = null

    fun onAnchorClick() {
        if (expanded) {
            expanded = false
            popupDismissedAtMillis = null
            return
        }

        val dismissedAt = popupDismissedAtMillis
        popupDismissedAtMillis = null
        if (dismissedAt == null || nowMillis() - dismissedAt > reopenGuardMillis) {
            expanded = true
        }
    }

    fun onDismissRequest() {
        if (expanded) {
            expanded = false
            popupDismissedAtMillis = nowMillis()
        }
    }

    fun close() {
        expanded = false
        popupDismissedAtMillis = null
    }
}
