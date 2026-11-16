package io.legado.app.ui.design.components.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.content.res.use
import io.legado.app.R
import io.legado.app.ui.design.components.NgSurfaceVariant
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot

class NgSurfaceLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var appliedSnapshot: NgThemeSnapshot? = null

    var surfaceVariant: NgSurfaceVariant = NgSurfaceVariant.CARD
        set(value) {
            field = value
            if (isAttachedToWindow) applyNgTheme()
        }

    var emphasized: Boolean = false
        set(value) {
            field = value
            if (isAttachedToWindow) applyNgTheme()
        }

    init {
        orientation = attrs?.getAttributeIntValue(
            ANDROID_NAMESPACE,
            "orientation",
            VERTICAL
        ) ?: VERTICAL
        if (attrs != null) {
            context.obtainStyledAttributes(
                attrs,
                R.styleable.NgSurfaceLayout,
                defStyleAttr,
                0
            ).use { array ->
                surfaceVariant = NgSurfaceVariant.entries.getOrElse(
                    array.getInt(R.styleable.NgSurfaceLayout_ngSurfaceVariant, 1)
                ) { NgSurfaceVariant.CARD }
            }
        }
        clipToOutline = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyNgTheme(appliedSnapshot ?: NgThemeResolver.resolve(context))
    }

    fun applyNgTheme(snapshot: NgThemeSnapshot = NgThemeResolver.resolve(context)) {
        appliedSnapshot = snapshot
        val colors = snapshot.colors
        val baseColor = when (surfaceVariant) {
            NgSurfaceVariant.CANVAS -> colors.background
            NgSurfaceVariant.CARD,
            NgSurfaceVariant.BORDERLESS_CARD -> colors.cardContainer
            NgSurfaceVariant.PANEL -> colors.surfaceContainerHigh
            NgSurfaceVariant.OVERLAY -> colors.dialogContainer
        }
        val alpha = when (surfaceVariant) {
            NgSurfaceVariant.CANVAS -> 1f
            NgSurfaceVariant.CARD -> snapshot.effects.containerAlpha
            NgSurfaceVariant.BORDERLESS_CARD -> 1f
            NgSurfaceVariant.PANEL -> (snapshot.effects.containerAlpha + 0.14f).coerceAtMost(1f)
            NgSurfaceVariant.OVERLAY -> snapshot.effects.dialogAlpha
        }.takeIf { !snapshot.isEInk } ?: 1f
        val radiusDp = when (surfaceVariant) {
            NgSurfaceVariant.CANVAS -> 0
            NgSurfaceVariant.CARD -> snapshot.shapes.mediumDp
            NgSurfaceVariant.BORDERLESS_CARD -> snapshot.shapes.largeDp
            NgSurfaceVariant.PANEL -> snapshot.shapes.largeDp
            NgSurfaceVariant.OVERLAY -> snapshot.shapes.extraLargeDp
        }
        background = GradientDrawable().apply {
            cornerRadius = radiusDp.dp.toFloat()
            setColor(ColorUtils.setAlphaComponent(baseColor, (Color.alpha(baseColor) * alpha).toInt()))
            if (
                surfaceVariant != NgSurfaceVariant.CANVAS &&
                surfaceVariant != NgSurfaceVariant.BORDERLESS_CARD
            ) {
                val strokeColor = when {
                    snapshot.isEInk -> colors.outline
                    emphasized -> ColorUtils.setAlphaComponent(colors.primary, 150)
                    surfaceVariant == NgSurfaceVariant.OVERLAY ->
                        ColorUtils.setAlphaComponent(colors.outlineVariant, 100)
                    else -> ColorUtils.setAlphaComponent(colors.outlineVariant, 64)
                }
                setStroke(
                    1.dp,
                    strokeColor
                )
            }
        }
        elevation = when (surfaceVariant) {
            NgSurfaceVariant.OVERLAY -> snapshot.effects.overlayElevationDp.dp.toFloat()
            else -> snapshot.effects.cardElevationDp.dp.toFloat()
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
