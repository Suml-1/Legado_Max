package io.legado.app.ui.video.player

import android.app.Dialog
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager


internal fun Dialog.applyVideoPanelWindow(widthFraction: Float) {
    val dialogWindow = window ?: return
    val metrics = context.resources.displayMetrics
    val width = (metrics.widthPixels * widthFraction).toInt()
    val height = metrics.heightPixels
    dialogWindow.setBackgroundDrawableResource(android.R.color.transparent)
    dialogWindow.setFlags(
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    )
    applyImmersive(dialogWindow.decorView)
    val lp = dialogWindow.attributes
    lp.width = width
    lp.height = height
    lp.gravity = Gravity.END or Gravity.TOP
    lp.y = 0
    dialogWindow.setAttributes(lp)
    dialogWindow.setLayout(width, height)
}

@Suppress("DEPRECATION")
private fun applyImmersive(decorView: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        decorView.windowInsetsController?.let { controller ->
            controller.hide(
                android.view.WindowInsets.Type.systemBars(),
            )
            controller.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
}
