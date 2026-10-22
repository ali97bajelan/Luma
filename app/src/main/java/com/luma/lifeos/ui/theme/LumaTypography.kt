package com.luma.lifeos.ui.theme

import androidx.annotation.FontRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luma.lifeos.R

// Bundled variable fonts; axis values are explicit so each requested weight is real.
// Android supplies its offline sans fallback for missing glyphs in mixed-script runs.
@OptIn(ExperimentalTextApi::class)
private fun bundledFamily(@FontRes resource: Int, opticalSize: Float? = null): FontFamily =
    FontFamily(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = resource,
            weight = weight,
            variationSettings = FontVariation.Settings(
                *buildList {
                    add(FontVariation.weight(weight.weight))
                    opticalSize?.let { add(FontVariation.Setting("opsz", it)) }
                }.toTypedArray(),
            ),
        )
    })

private val Editorial = bundledFamily(R.font.newsreader, opticalSize = 26f)
private val LatinSans = bundledFamily(R.font.plus_jakarta_sans)
private val PersianSans = bundledFamily(R.font.vazirmatn)

private fun typography(persian: Boolean): Typography {
    val sans = if (persian) PersianSans else LatinSans
    // Persian headings stay sans; Latin/French headings use Newsreader. In a Latin
    // locale, Persian glyphs missing from Newsreader fall back to Android's sans.
    val heading = if (persian) PersianSans else Editorial
    fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp,
            lineHeight = line.sp, letterSpacing = 0.sp)

    return Typography(
        displayLarge = style(heading, 40, 48),
        displayMedium = style(heading, 36, 44),
        displaySmall = style(heading, 32, 40),
        headlineLarge = style(heading, 30, 38),
        headlineMedium = style(heading, 26, 34),
        headlineSmall = style(heading, 22, 28),
        titleLarge = style(heading, 20, 26, FontWeight.Medium),
        // Values, dates and row controls also use titleMedium: keep it sans.
        titleMedium = style(sans, 16, 24, FontWeight.SemiBold),
        titleSmall = style(sans, 14, 20, FontWeight.SemiBold),
        bodyLarge = style(sans, 16, 26),
        bodyMedium = style(sans, 14, 22),
        bodySmall = style(sans, 12, 18),
        labelLarge = style(sans, 14, 20, FontWeight.SemiBold),
        labelMedium = style(sans, 12, 18, FontWeight.Medium),
        labelSmall = style(sans, 12, 18, FontWeight.Medium),
    )
}

private val LatinTypography = typography(persian = false)
private val PersianTypography = typography(persian = true)

internal fun lumaTypography(language: String): Typography =
    if (language == "fa") PersianTypography else LatinTypography

internal val LumaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
