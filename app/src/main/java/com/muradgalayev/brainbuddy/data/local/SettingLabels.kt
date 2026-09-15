package com.muradgalayev.brainbuddy.data.local

import androidx.annotation.StringRes
import com.muradgalayev.brainbuddy.R

// display names for the appearance enums. the enum names themselves are stored in DataStore and
// Supabase, so they can't double as copy

@get:StringRes
val FontSize.labelRes: Int
    get() = when (this) {
        FontSize.Small -> R.string.common_small
        FontSize.Medium -> R.string.common_medium
        FontSize.Large -> R.string.common_large
    }

@get:StringRes
val TextSpacing.labelRes: Int
    get() = when (this) {
        TextSpacing.Normal -> R.string.spacing_normal
        TextSpacing.Relaxed -> R.string.spacing_relaxed
        TextSpacing.Loose -> R.string.spacing_loose
    }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.Light -> R.string.common_light
        ThemeMode.Dark -> R.string.common_dark
        ThemeMode.System -> R.string.theme_system
    }

@get:StringRes
val com.muradgalayev.brainbuddy.domain.model.RingerSetting.labelRes: Int
    get() = when (this) {
        com.muradgalayev.brainbuddy.domain.model.RingerSetting.NORMAL -> R.string.ringer_normal
        com.muradgalayev.brainbuddy.domain.model.RingerSetting.VIBRATE -> R.string.ringer_vibrate
        com.muradgalayev.brainbuddy.domain.model.RingerSetting.SILENT -> R.string.ringer_silent
    }
