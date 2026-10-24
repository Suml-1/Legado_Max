package io.legado.app.ui.video.player

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 让播放器的右侧面板（选集 / 倍速）真正贴顶铺满，不留状态栏空位。
 *
 * 只设 `gravity = TOP` 是不够的：Dialog 默认是**浮动窗口**，系统会把它摆进
 * "扣除状态栏与导航栏之后的可用区域"，于是无论怎么对齐，顶部都会空出一条状态栏的高度
 * （这就是"贴顶不生效"的实际原因）。这里做三件事：
 *  1. `FLAG_LAYOUT_NO_LIMITS`：允许窗口越出可用区域、一直贴到屏幕物理边缘；
 *  2. `FLAG_LAYOUT_IN_SCREEN` + decorView 的 LAYOUT_FULLSCREEN：内容延伸到系统栏下方；
 *  3. 背景透明 + 贴右贴顶，面板自己画底色，不依赖窗口装饰的位置。
 *
 * @param widthPx 面板宽度（px）
 * @param heightPx 面板高度（px）：调用方传播放器实测高度，退化时才用屏幕高度
 */
fun Dialog.applyVideoPanelWindow(widthPx: Int, heightPx: Int) {
    val dialogWindow = window ?: return
    dialogWindow.setBackgroundDrawableResource(android.R.color.transparent)
    dialogWindow.addFlags(
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    )
    @Suppress("DEPRECATION")
    dialogWindow.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    val lp = dialogWindow.attributes
    lp.width = widthPx
    lp.height = heightPx
    //贴右 + 贴顶：配合 NO_LIMITS，top 就是屏幕物理顶边，而不是状态栏下沿
    lp.gravity = Gravity.END or Gravity.TOP
    dialogWindow.setAttributes(lp)
}
