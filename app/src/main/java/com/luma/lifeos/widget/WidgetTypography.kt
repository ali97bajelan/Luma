package com.luma.lifeos.widget

import android.graphics.Typeface
import com.luma.lifeos.R
import com.luma.lifeos.language.AppLanguages

/** Keeps widget layout measurements aligned with the app's bundled sans family. */
internal object WidgetTypography {
    fun typeface(context: android.content.Context, bold: Boolean = false): Typeface {
        val font = if (AppLanguages.current(context).locale.language == "fa") {
            R.font.vazirmatn
        } else {
            R.font.plus_jakarta_sans
        }
        return Typeface.create(context.resources.getFont(font), if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
