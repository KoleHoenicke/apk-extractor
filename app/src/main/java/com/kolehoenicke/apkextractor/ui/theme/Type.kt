package com.kolehoenicke.apkextractor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kolehoenicke.apkextractor.R

private val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex_regular, weight = FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, weight = FontWeight.Medium),
)

private fun TextStyle.withGoogleSansFlex() = copy(fontFamily = GoogleSansFlex)

private val MaterialTypography = Typography()

internal val AppTypography = Typography(
    displayLarge = MaterialTypography.displayLarge.withGoogleSansFlex(),
    displayMedium = MaterialTypography.displayMedium.withGoogleSansFlex(),
    displaySmall = MaterialTypography.displaySmall.withGoogleSansFlex(),
    headlineLarge = MaterialTypography.headlineLarge.withGoogleSansFlex(),
    headlineMedium = MaterialTypography.headlineMedium.withGoogleSansFlex(),
    headlineSmall = MaterialTypography.headlineSmall.withGoogleSansFlex(),
    titleLarge = MaterialTypography.titleLarge.withGoogleSansFlex(),
    titleMedium = MaterialTypography.titleMedium.withGoogleSansFlex(),
    titleSmall = MaterialTypography.titleSmall.withGoogleSansFlex(),
    bodyLarge = MaterialTypography.bodyLarge.withGoogleSansFlex(),
    bodyMedium = MaterialTypography.bodyMedium.withGoogleSansFlex(),
    bodySmall = MaterialTypography.bodySmall.withGoogleSansFlex(),
    labelLarge = MaterialTypography.labelLarge.withGoogleSansFlex(),
    labelMedium = MaterialTypography.labelMedium.withGoogleSansFlex(),
    labelSmall = MaterialTypography.labelSmall.withGoogleSansFlex(),
    displayLargeEmphasized = MaterialTypography.displayLargeEmphasized.withGoogleSansFlex(),
    displayMediumEmphasized = MaterialTypography.displayMediumEmphasized.withGoogleSansFlex(),
    displaySmallEmphasized = MaterialTypography.displaySmallEmphasized.withGoogleSansFlex(),
    headlineLargeEmphasized = MaterialTypography.headlineLargeEmphasized.withGoogleSansFlex(),
    headlineMediumEmphasized = MaterialTypography.headlineMediumEmphasized.withGoogleSansFlex(),
    headlineSmallEmphasized = MaterialTypography.headlineSmallEmphasized.withGoogleSansFlex(),
    titleLargeEmphasized = MaterialTypography.titleLargeEmphasized.withGoogleSansFlex(),
    titleMediumEmphasized = MaterialTypography.titleMediumEmphasized.withGoogleSansFlex(),
    titleSmallEmphasized = MaterialTypography.titleSmallEmphasized.withGoogleSansFlex(),
    bodyLargeEmphasized = MaterialTypography.bodyLargeEmphasized.withGoogleSansFlex(),
    bodyMediumEmphasized = MaterialTypography.bodyMediumEmphasized.withGoogleSansFlex(),
    bodySmallEmphasized = MaterialTypography.bodySmallEmphasized.withGoogleSansFlex(),
    labelLargeEmphasized = MaterialTypography.labelLargeEmphasized.withGoogleSansFlex(),
    labelMediumEmphasized = MaterialTypography.labelMediumEmphasized.withGoogleSansFlex(),
    labelSmallEmphasized = MaterialTypography.labelSmallEmphasized.withGoogleSansFlex(),
)
