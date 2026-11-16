package io.legado.app.ui.design.components.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Optional equivalent primitives for shapes with a small protrusion on a large body.
 * Coordinates remain in the full surface so every piece samples the same material.
 * Fill pieces must not overlap; stroke paths must describe only the exterior edge.
 */
internal interface NgSegmentedGlassShape : Shape {
    fun createContentOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline

    fun createGlassGeometry(size: Size, layoutDirection: LayoutDirection, density: Density): NgGlassGeometry
}

internal class NgGlassGeometry(
    val fillRects: List<Rect>,
    val fillPaths: List<Path>,
    val strokePaths: List<Path>
)

/** Content never occupies the pointer; avoid a concave clip around all its children. */
internal class NgGlassContentShape(private val shape: NgSegmentedGlassShape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        shape.createContentOutline(size, layoutDirection, density)
}
