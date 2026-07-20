package nl.jjt.vorfahrtfahrradcompanion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A self-contained bicycle glyph, so we get a bike icon without pulling in the whole
 * material-icons-extended artifact. Path traced on a 24×24 viewport; the black fill is
 * recolored by [androidx.compose.material3.Icon]'s tint like any Material icon.
 */
val BicycleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bicycle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 " +
                    "5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 " +
                    "3.5 3.5-1.6 3.5-3.5 3.5zM10.8 10.5l2.4-2.4.8.8c1.3 1.3 3 2.1 5.1 2.1V9c-1.5 " +
                    "0-2.7-.6-3.6-1.5l-1.9-1.9c-.5-.4-1-.6-1.6-.6s-1.1.2-1.4.6L7.8 8.4c-.4.4-.6.9-.6 " +
                    "1.4 0 .6.2 1.1.6 1.4L11 14v5h2v-6.2l-2.2-2.3zM19 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 " +
                    "5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 " +
                    "3.5-1.6 3.5-3.5 3.5z",
            ),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
