package com.workora.app.core.utils

import android.os.Build
import android.text.Html

/** Decodes HTML text and entities once, without altering already decoded Unicode text. */
object HtmlTextDecoder {
    fun decode(value: String): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    } else {
        @Suppress("DEPRECATION")
        Html.fromHtml(value).toString()
    }
}
