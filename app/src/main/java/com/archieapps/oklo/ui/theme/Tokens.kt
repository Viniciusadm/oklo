package com.archieapps.oklo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screenH = 16.dp
    val screenBottom = 24.dp
}

object Radii {
    val control = RoundedCornerShape(10.dp)
    val surface = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(percent = 50)
    val small = RoundedCornerShape(6.dp)
    val sheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}
