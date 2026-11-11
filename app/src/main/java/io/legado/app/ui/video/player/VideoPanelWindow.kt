package io.legado.app.ui.video.player

import android.app.Dialog
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

// 播放器右侧面板（选集 / 倍速）的窗口尺寸：宽取屏宽比例、高取整屏，贴右贴顶。
// 面板是独立 Dialog，不继承播放器的沉浸式全屏状态，因此需要自己铺到物理屏幕边界
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
    // show() 后会重新下发 LayoutParams，onStart() 再调一次保证尺寸不被覆盖
    dialogWindow.setLayout(width, height)
}

// 让 Dialog 内容从屏幕物理边界开始绘制，与播放器的沉浸式全屏对齐
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
