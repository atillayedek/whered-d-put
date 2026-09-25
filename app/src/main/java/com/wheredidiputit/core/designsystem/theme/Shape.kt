package com.wheredidiputit.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object WdipiShapes {
    val card = RoundedCornerShape(18.dp)
    val input = RoundedCornerShape(14.dp)
    val button = RoundedCornerShape(16.dp)
    val thumbnail = RoundedCornerShape(12.dp)
    val photo = RoundedCornerShape(20.dp)
}

internal val WdipiMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
