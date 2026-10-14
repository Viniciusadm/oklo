package com.archieapps.oklo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.archieapps.oklo.R

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: FontWeight, opticalSize: Float) = Font(
    R.font.fraunces,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", opticalSize),
        FontVariation.Setting("SOFT", 50f),
        FontVariation.Setting("WONK", 0f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun instrument(weight: FontWeight) = Font(
    R.font.instrument_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val SerifDisplay = FontFamily(
    fraunces(FontWeight.Normal, 72f),
    fraunces(FontWeight.Medium, 72f),
    fraunces(FontWeight.SemiBold, 72f),
)

val SerifText = FontFamily(
    fraunces(FontWeight.Normal, 24f),
    fraunces(FontWeight.Medium, 24f),
    fraunces(FontWeight.SemiBold, 24f),
)

val Sans = FontFamily(
    instrument(FontWeight.Normal),
    instrument(FontWeight.Medium),
    instrument(FontWeight.SemiBold),
    instrument(FontWeight.Bold),
)

val OkloTypography = Typography(
    displayLarge = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Normal, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Normal, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-0.02).em),
    displaySmall = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.015).em),
    headlineLarge = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.015).em),
    headlineMedium = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = SerifText, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.005).em),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.08.em),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.02.em),
)

val CardFrontStyle = TextStyle(fontFamily = SerifText, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.005).em)

val NumeralStyle = TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em)
