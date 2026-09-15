package com.muradgalayev.brainbuddy.ui.utils

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

// the fields of a skeleton in the order the current language puts them: 'MMMMd' is
// 'September 10' in English and '10 settembre' in Italian. a fixed ofPattern() hard-codes one
// language's word order into every other
fun localizedDateFormatter(skeleton: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}
