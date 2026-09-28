package com.likkapet.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object LikkaShapes {
    val s = RoundedCornerShape(12.dp)
    val m = RoundedCornerShape(20.dp)
    val l = RoundedCornerShape(28.dp)
    val full = RoundedCornerShape(50)
}

val LikkaMaterialShapes = Shapes(small = LikkaShapes.s, medium = LikkaShapes.m, large = LikkaShapes.l)
