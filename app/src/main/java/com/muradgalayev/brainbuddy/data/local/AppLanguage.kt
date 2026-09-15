package com.muradgalayev.brainbuddy.data.local

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

// the languages Myndora ships. anything else on the device falls back to English, which is also
// what the unqualified res/values holds
enum class AppLanguage(val tag: String) {
    English("en"),
    Italian("it");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            if (tag?.startsWith("it", ignoreCase = true) == true) Italian else English
    }
}

// one language change in flight. applied flips once the locale has actually been set, which
// is also how a pre-33 recreate knows to come back already covered
data class LanguageSwitch(
    val from: AppLanguage,
    val to: AppLanguage,
    val applied: Boolean = false,
)

// per-app language. 33+ hands it to the framework's LocaleManager, which persists it,
// relocalises the application context and lists the choice in system Settings; MainActivity
// handles the resulting configuration change in place rather than being recreated.
// older versions keep the tag in plain SharedPreferences so attachBaseContext can read it
// synchronously, before DataStore or Hilt exist
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"

    // process-wide rather than composition state, so it outlives the recreate older versions need
    private val _switch = MutableStateFlow<LanguageSwitch?>(null)
    val switch: StateFlow<LanguageSwitch?> = _switch.asStateFlow()

    // what every picker calls. LanguageSwitchOverlay covers the screen, commits underneath and then
    // reveals the app in the new language. a second tap while one is running is ignored
    fun requestSwitch(context: Context, language: AppLanguage) {
        val from = current(context)
        if (language == from) return
        _switch.compareAndSet(null, LanguageSwitch(from, language))
    }

    fun commitSwitch(context: Context) {
        val pending = _switch.value?.takeIf { !it.applied } ?: return
        _switch.value = pending.copy(applied = true)
        set(context, pending.to)
    }

    fun finishSwitch() {
        _switch.value = null
    }

    // what the app is rendering in right now, explicit choice or not
    fun current(context: Context): AppLanguage =
        AppLanguage.fromTag(context.resources.configuration.locales[0].language)

    // the locale for anything spoken or heard: the microphone, the assistant's voice, read-aloud.
    //
    // deliberately the app's language rather than Locale.getDefault(). the two differ whenever the
    // phone is set to a language Myndora doesn't ship — a Russian phone renders the app in English,
    // and the system default would then have the recognizer listening in Russian and the voice
    // reading English words with a Russian mouth. the app's language is the one the user is
    // actually reading, so it is the one they will speak and expect to hear.
    // region-qualified because speech engines want 'en-US', not 'en', and the phone's own region is
    // the better accent when the languages already agree
    fun voiceLocale(context: Context): Locale {
        val app = current(context)
        val device = Locale.getDefault()
        if (device.language.equals(app.tag, ignoreCase = true) && device.country.isNotBlank()) {
            return Locale.forLanguageTag("${app.tag}-${device.country}")
        }
        return when (app) {
            AppLanguage.Italian -> Locale.forLanguageTag("it-IT")
            AppLanguage.English -> Locale.forLanguageTag("en-US")
        }
    }

    fun set(context: Context, language: AppLanguage) {
        val activity = context.findActivity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.tag)
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TAG, language.tag).commit()
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        // notifications and workers read the application context, which was wrapped at launch with
        // the old language and isn't recreated with the activity
        val app = context.applicationContext
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(
            Configuration(app.resources.configuration).apply { setLocale(locale) },
            app.resources.displayMetrics,
        )
        // 33+ does this through Application.onConfigurationChanged; here nothing else will
        com.muradgalayev.brainbuddy.data.notifications.NotificationChannels.createAll(app)
        activity?.recreate()
    }

    // pre-33 only: the base context in the stored language. a no-op until someone picks one, so the
    // device language still decides by default
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
            ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
