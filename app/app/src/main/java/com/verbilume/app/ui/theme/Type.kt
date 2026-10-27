package com.verbilume.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.verbilume.app.R

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: FontWeight): Font = Font(
    resId = R.font.fraunces,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight): Font = Font(
    resId = R.font.manrope,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val FrauncesFamily = FontFamily(
    fraunces(FontWeight.Light),
    fraunces(FontWeight.Normal),
    fraunces(FontWeight.Medium),
    fraunces(FontWeight.SemiBold),
    fraunces(FontWeight.Bold),
    fraunces(FontWeight.ExtraBold),
)

val ManropeFamily = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

val VerbiTypography = Typography(
    displayLarge = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 54.sp, lineHeight = 58.sp, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-0.9).sp),
    displaySmall = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 1.4.sp),
)
