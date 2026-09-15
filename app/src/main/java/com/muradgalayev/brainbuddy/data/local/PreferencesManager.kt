package com.muradgalayev.brainbuddy.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muradgalayev.brainbuddy.data.notifications.PomodoroNudgeFrequency
import com.muradgalayev.brainbuddy.data.notifications.ReminderKind
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncFrequency
import com.muradgalayev.brainbuddy.domain.model.ModeSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import com.muradgalayev.brainbuddy.ui.theme.AppTheme
import com.muradgalayev.brainbuddy.ui.theme.CustomThemeSpec
import com.muradgalayev.brainbuddy.ui.theme.DEFAULT_ACCENT_HUE
import com.muradgalayev.brainbuddy.ui.theme.DEFAULT_SUPPORT_HUE
import com.muradgalayev.brainbuddy.ui.theme.ThemeSelection
import com.muradgalayev.brainbuddy.ui.theme.Vividness
import com.muradgalayev.brainbuddy.ui.theme.toSelection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { Light, Dark, System }
// typefaces the user can pick. short on purpose: every option is one people with dyslexia or
// low vision are known to read better, not a style menu. Arial renders as Arimo, which is
// metric-identical and actually shippable. older Classic/Modern/Rounded values map onto it
enum class FontMode { Arial, OpenDyslexic, Atkinson;

    companion object {
        val DEFAULT = Arial

        // tolerant parse. values arrive from DataStore and from the Supabase preferences row, which
        // may still hold Classic/Modern/Rounded from an older build, and valueOf() would throw
        fun fromStored(raw: String?): FontMode = when (raw) {
            "OpenDyslexic" -> OpenDyslexic
            "Atkinson" -> Atkinson
            // retired style options all resolve to the new default
            else -> Arial
        }
    }
}

enum class FontSize { Small, Medium, Large }

// how much air the text has: line height and letter spacing, moved together. separate from
// FontSize because they solve different problems, size is for people who can't resolve small
// glyphs and spacing is for people who lose their place between lines. one control rather
// than two sliders, because letter spacing on its own starts pulling the OpenDyslexic and
// Atkinson letterforms apart. the multipliers live in the theme layer
enum class TextSpacing { Normal, Relaxed, Loose;

    companion object {
        val DEFAULT = Normal

        // tolerant parse, the value can arrive from another device's Supabase row
        fun fromStored(raw: String?): TextSpacing =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val appThemeKey = stringPreferencesKey("app_theme")
    private val customAccentHueKey = floatPreferencesKey("custom_accent_hue")
    private val customSupportHueKey = floatPreferencesKey("custom_support_hue")
    private val customVividnessKey = stringPreferencesKey("custom_vividness")
    private val fontSizeKey = stringPreferencesKey("font_size")
    private val textSpacingKey = stringPreferencesKey("text_spacing")
    private val fontKey = stringPreferencesKey("font_mode")
    private val enabledNavKey = stringSetPreferencesKey("enabled_nav_items")
    private val focusModeKey = booleanPreferencesKey("focus_mode_enabled")
    private val simplifiedWorkspaceKey = booleanPreferencesKey("simplified_workspace")
    private val wellnessStepsGoalKey = intPreferencesKey("wellness_steps_goal")
    private val wellnessExerciseGoalKey = intPreferencesKey("wellness_exercise_goal")
    private val wellnessEnergyGoalKey = intPreferencesKey("wellness_energy_goal")
    private val wellnessCardOrderKey = stringPreferencesKey("wellness_card_order")
    private val wellnessPinnedCardsKey = stringSetPreferencesKey("wellness_pinned_cards")
    private val medicationDoseLogsKey = stringSetPreferencesKey("medication_dose_logs")

    // stored as what's hidden rather than what's shown, so a tile added in a later release turns
    // up for everyone instead of being invisible to every existing user
    private val hiddenHomeWidgetsKey = stringSetPreferencesKey("hidden_home_widgets")

    // comma-joined strings rather than string sets, because a set has no order and order is the
    // whole point of the first one. unknown ids are dropped on read and unlisted ones fall back
    // to their declared position, so a layout saved by a newer build doesn't strand a tile
    private val homeWidgetOrderKey = stringPreferencesKey("home_widget_order")
    private val homeWidgetSpansKey = stringPreferencesKey("home_widget_spans")

    // warm snapshot of simplified-workspace so the Workspace screen can seed its first frame
    // synchronously instead of flashing the full content before DataStore arrives
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var simplifiedWorkspaceCache = false

    // wellness summary layout, mirrored synchronously. without it the screen paints the default
    // order and visibly reshuffles a frame later, which is the 'it changes right after I open
    // it' flicker
    @Volatile private var wellnessCardOrderCache: List<String>? = null
    @Volatile private var wellnessPinnedCardsCache: Set<String>? = null

    // the look of the app, mirrored synchronously. every rotation recreates the activity and
    // re-subscribes to DataStore, so without this the first frames after a rotate are painted in
    // the default theme and cross-fade to the stored one. read once, before the first composition
    @Volatile private var themeModeCache: ThemeMode? = null
    @Volatile private var appThemeCache: ThemeSelection? = null
    @Volatile private var fontModeCache: FontMode? = null
    @Volatile private var fontSizeCache: FontSize? = null
    @Volatile private var textSpacingCache: TextSpacing? = null

    init {
        // read DataStore directly rather than the val below, so this can't race constructor init
        cacheScope.launch {
            context.dataStore.data
                .map { it[simplifiedWorkspaceKey] ?: false }
                .collect { simplifiedWorkspaceCache = it }
        }
        cacheScope.launch {
            context.dataStore.data.collect { prefs ->
                wellnessCardOrderCache = (
                    prefs[wellnessCardOrderKey]?.split(',')
                        ?.filter { it in DEFAULT_WELLNESS_CARDS }.orEmpty() + DEFAULT_WELLNESS_CARDS
                    ).distinct()
                wellnessPinnedCardsCache =
                    prefs[wellnessPinnedCardsKey] ?: DEFAULT_WELLNESS_CARDS.toSet()
            }
        }
        cacheScope.launch {
            context.dataStore.data.collect { prefs ->
                themeModeCache = when (prefs[themeKey]) {
                    "Light" -> ThemeMode.Light
                    "Dark" -> ThemeMode.Dark
                    else -> ThemeMode.System
                }
                appThemeCache = prefs[appThemeKey].let { id ->
                    if (id == ThemeSelection.CUSTOM_ID) {
                        ThemeSelection.custom(
                            accentHue = prefs[customAccentHueKey] ?: DEFAULT_ACCENT_HUE,
                            supportHue = prefs[customSupportHueKey] ?: DEFAULT_SUPPORT_HUE,
                            vividness = Vividness.fromStored(prefs[customVividnessKey]),
                        )
                    } else {
                        AppTheme.fromStored(id).toSelection()
                    }
                }
                fontModeCache = FontMode.fromStored(prefs[fontKey])
                fontSizeCache = when (prefs[fontSizeKey]) {
                    "Small" -> FontSize.Small
                    "Large" -> FontSize.Large
                    else -> FontSize.Medium
                }
                textSpacingCache = TextSpacing.fromStored(prefs[textSpacingKey])
            }
        }
    }

    // null before DataStore has emitted in this process, and that's meaningful: it separates
    // 'the user chose the default' from 'we don't know yet', and only the second one should
    // suppress the theme transition
    fun peekThemeMode(): ThemeMode? = themeModeCache
    fun peekAppTheme(): ThemeSelection? = appThemeCache
    fun peekFontMode(): FontMode? = fontModeCache
    fun peekFontSize(): FontSize? = fontSizeCache
    fun peekTextSpacing(): TextSpacing? = textSpacingCache

    // synchronous read of the last-known value
    fun peekSimplifiedWorkspace(): Boolean = simplifiedWorkspaceCache

    // null before DataStore has ever emitted
    fun peekWellnessCardOrder(): List<String>? = wellnessCardOrderCache

    // null before DataStore has ever emitted
    fun peekWellnessPinnedCards(): Set<String>? = wellnessPinnedCardsCache
    // notification frequency. reminder kinds are stored as a set of ReminderKind names, and
    // absent means all kinds, so existing users keep today's behaviour
    private val todoReminderKindsKey = stringSetPreferencesKey("todo_reminder_kinds")
    private val calendarReminderKindsKey = stringSetPreferencesKey("calendar_reminder_kinds")
    private val dailySummaryEnabledKey = booleanPreferencesKey("daily_summary_enabled")
    private val pomodoroNudgeFrequencyKey = stringPreferencesKey("pomodoro_nudge_frequency")
    private val pomodoroNudgeTimeKey = stringPreferencesKey("pomodoro_nudge_time")
    private val pomodoroBreakRemindersKey = booleanPreferencesKey("pomodoro_break_reminders")
    // cached so Splash never blocks on a Supabase round-trip
    private fun surveyCompletedKey(userId: String) =
        booleanPreferencesKey("survey_completed_$userId")

    // per-user 'I'll do the survey later'. lets Splash send them into the app unfinished while
    // the account screen keeps nudging
    private fun onboardingSkippedKey(userId: String) =
        booleanPreferencesKey("onboarding_skipped_$userId")

    // null means no active chat, the next message starts a new conversation
    private fun activeConversationKey(userId: String) =
        stringPreferencesKey("active_ai_conversation_$userId")

    // preference fields changed locally that Supabase hasn't accepted yet, by PreferencesDto
    // column name. written when a push fails, cleared once it lands. two jobs: tell the sync
    // coordinator what to re-push, and stop the remote pull handing back the stale value and
    // silently undoing the change
    private val pendingPrefsPushKey = stringSetPreferencesKey("pending_prefs_push")

    val pendingPrefsPush: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[pendingPrefsPushKey] ?: emptySet()
    }

    suspend fun pendingPrefsPushSnapshot(): Set<String> = pendingPrefsPush.first()

    suspend fun markPrefsPending(fields: Set<String>) {
        if (fields.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[pendingPrefsPushKey] = (prefs[pendingPrefsPushKey] ?: emptySet()) + fields
        }
    }

    suspend fun clearPrefsPending(fields: Set<String>) {
        if (fields.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[pendingPrefsPushKey] = (prefs[pendingPrefsPushKey] ?: emptySet()) - fields
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "Light" -> ThemeMode.Light
            "Dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }
    // already resolved to a built-in or the user's own hues. independent of light/dark
    val appTheme: Flow<ThemeSelection> = context.dataStore.data.map { prefs ->
        val id = prefs[appThemeKey]
        if (id == ThemeSelection.CUSTOM_ID) {
            ThemeSelection.custom(
                accentHue = prefs[customAccentHueKey] ?: DEFAULT_ACCENT_HUE,
                supportHue = prefs[customSupportHueKey] ?: DEFAULT_SUPPORT_HUE,
                vividness = Vividness.fromStored(prefs[customVividnessKey]),
            )
        } else {
            AppTheme.fromStored(id).toSelection()
        }
    }

    // the custom theme's own settings, so the editor can show what's stored
    val customThemeSpec: Flow<CustomThemeSpec> = context.dataStore.data.map { prefs ->
        CustomThemeSpec(
            accentHue = prefs[customAccentHueKey] ?: DEFAULT_ACCENT_HUE,
            supportHue = prefs[customSupportHueKey] ?: DEFAULT_SUPPORT_HUE,
            vividness = Vividness.fromStored(prefs[customVividnessKey]),
        )
    }

    val fontSize: Flow<FontSize> = context.dataStore.data.map { prefs ->
        when (prefs[fontSizeKey]) {
            "Small" -> FontSize.Small
            "Large" -> FontSize.Large
            else -> FontSize.Medium
        }
    }

    val textSpacing: Flow<TextSpacing> = context.dataStore.data.map { prefs ->
        TextSpacing.fromStored(prefs[textSpacingKey])
    }

    val fontMode: Flow<FontMode> = context.dataStore.data.map { prefs ->
        FontMode.fromStored(prefs[fontKey])
    }

    val enabledNavItems: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[enabledNavKey] ?: emptySet()
    }

    val focusModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[focusModeKey] ?: false
    }

    val simplifiedWorkspace: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[simplifiedWorkspaceKey] ?: false
    }
    val wellnessStepsGoal: Flow<Int> = context.dataStore.data.map { it[wellnessStepsGoalKey] ?: 8_000 }
    val wellnessExerciseGoal: Flow<Int> = context.dataStore.data.map { it[wellnessExerciseGoalKey] ?: 30 }
    val wellnessEnergyGoal: Flow<Int> = context.dataStore.data.map { it[wellnessEnergyGoalKey] ?: 500 }
    val wellnessCardOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val saved = prefs[wellnessCardOrderKey]
            ?.split(',')
            ?.filter { it in DEFAULT_WELLNESS_CARDS }
            .orEmpty()
        (saved + DEFAULT_WELLNESS_CARDS).distinct()
    }
    val wellnessPinnedCards: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[wellnessPinnedCardsKey] ?: DEFAULT_WELLNESS_CARDS.toSet()
    }
    val medicationDoseLogs: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[medicationDoseLogsKey] ?: emptySet()
    }

    // null until the user has customised, so callers can apply their own defaults
    val hiddenHomeWidgets: Flow<Set<String>?> = context.dataStore.data.map { prefs ->
        prefs[hiddenHomeWidgetsKey]
    }

    suspend fun setHiddenHomeWidgets(hidden: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[hiddenHomeWidgetsKey] = hidden
        }
    }

    // null until the user has dragged something, callers fall back to the declared order
    val homeWidgetOrder: Flow<List<String>?> = context.dataStore.data.map { prefs ->
        prefs[homeWidgetOrderKey]?.split(',')?.filter { it.isNotBlank() }
    }

    suspend fun setHomeWidgetOrder(order: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[homeWidgetOrderKey] = order.joinToString(",")
        }
    }

    // widget id to span name, for the tiles whose width the user has changed
    val homeWidgetSpans: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[homeWidgetSpansKey]
            ?.split(',')
            ?.mapNotNull { entry ->
                val id = entry.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val span = entry.substringAfter('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to span
            }
            ?.toMap()
            .orEmpty()
    }

    suspend fun setHomeWidgetSpans(spans: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[homeWidgetSpansKey] = spans.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
    }

    suspend fun toggleMedicationDose(logKey: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[medicationDoseLogsKey] ?: emptySet()
            prefs[medicationDoseLogsKey] = if (logKey in current) current - logKey else current + logKey
        }
    }

    suspend fun setMedicationDoseLogged(logKey: String, logged: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[medicationDoseLogsKey] ?: emptySet()
            prefs[medicationDoseLogsKey] = if (logged) current + logKey else current - logKey
        }
    }

    suspend fun setWellnessGoals(steps: Int, exerciseMinutes: Int, energyKcal: Int) {
        context.dataStore.edit {
            it[wellnessStepsGoalKey] = steps.coerceAtLeast(1)
            it[wellnessExerciseGoalKey] = exerciseMinutes.coerceAtLeast(1)
            it[wellnessEnergyGoalKey] = energyKcal.coerceAtLeast(1)
        }
    }

    suspend fun setWellnessCardOrder(order: List<String>) {
        context.dataStore.edit { it[wellnessCardOrderKey] = order.distinct().joinToString(",") }
    }

    suspend fun setWellnessCardPinned(card: String, pinned: Boolean) {
        if (card !in DEFAULT_WELLNESS_CARDS) return
        context.dataStore.edit { prefs ->
            val current = prefs[wellnessPinnedCardsKey] ?: DEFAULT_WELLNESS_CARDS.toSet()
            prefs[wellnessPinnedCardsKey] = if (pinned) current + card else current - card
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }
    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { prefs ->
            prefs[appThemeKey] = theme.id
        }
    }

    // switches to the custom theme and stores the hues behind it in one write
    suspend fun setCustomTheme(spec: CustomThemeSpec) {
        context.dataStore.edit { prefs ->
            prefs[appThemeKey] = ThemeSelection.CUSTOM_ID
            prefs[customAccentHueKey] = spec.accentHue
            prefs[customSupportHueKey] = spec.supportHue
            prefs[customVividnessKey] = spec.vividness.id
        }
    }

    suspend fun setFontSize(size: FontSize) {
        context.dataStore.edit { prefs ->
            prefs[fontSizeKey] = size.name
        }
    }

    suspend fun setTextSpacing(spacing: TextSpacing) {
        context.dataStore.edit { prefs ->
            prefs[textSpacingKey] = spacing.name
        }
    }

    suspend fun setFontMode(mode: FontMode) {
        context.dataStore.edit { prefs ->
            prefs[fontKey] = mode.name
        }
    }

    suspend fun setNavItemEnabled(route: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[enabledNavKey] ?: emptySet()
            prefs[enabledNavKey] = if (enabled) current + route else current - route
        }
    }

    suspend fun setEnabledNavItems(items: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[enabledNavKey] = items
        }
    }

    suspend fun setFocusModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[focusModeKey] = enabled
        }
    }

    // counts how many times the calendar AI intro has played. plays the first few visits
    // (see CalendarViewModel), then stops for good
    private val calendarAiIntroCountKey = intPreferencesKey("calendar_ai_intro_count")

    suspend fun getCalendarAiIntroCount(): Int =
        context.dataStore.data.first()[calendarAiIntroCountKey] ?: 0

    suspend fun incrementCalendarAiIntroCount() {
        context.dataStore.edit { prefs ->
            prefs[calendarAiIntroCountKey] = (prefs[calendarAiIntroCountKey] ?: 0) + 1
        }
    }

    private val calendarVoiceTipDismissedKey = booleanPreferencesKey("calendar_voice_tip_dismissed")
    private val modesIntroSeenKey = booleanPreferencesKey("modes_intro_seen")
    private val dndStartPromptsShownKey = intPreferencesKey("dnd_start_prompts_shown")
    private val calendarVoiceHandlePositionKey = floatPreferencesKey("calendar_voice_handle_position")
    private val aiSpokenResponsesKey = booleanPreferencesKey("ai_spoken_responses")
    private val aiChatReadAloudKey = booleanPreferencesKey("ai_chat_read_aloud")

    // where medication doses show up besides the Medications screen. to-do defaults to true
    // because that's what the app already did, defaulting it off would silently remove doses
    // for everyone relying on them being there
    private val medicationInTodoKey = booleanPreferencesKey("medication_in_todo")
    private val medicationInCalendarKey = booleanPreferencesKey("medication_in_calendar")
    private val aiVoiceNameKey = stringPreferencesKey("ai_voice_name")
    private val readAloudTapsKey = booleanPreferencesKey("read_aloud_taps")
    private val aiHealthPersonalizationKey = booleanPreferencesKey("ai_health_personalization")
    private val reduceMotionKey = booleanPreferencesKey("reduce_motion")

    // how many times Home has been opened, and whether the motion tip has had its answer.
    // 'this moves on its own, you can stop it' means nothing to someone who hasn't watched it
    // move yet, so the tip waits for a handful of visits and is never asked twice
    private val homeOpensKey = intPreferencesKey("home_opens")
    private val motionTipDoneKey = booleanPreferencesKey("motion_tip_done")

    // AI consent bookkeeping. the flag above is the runtime gate, these two are what make it a
    // record. see AiConsentRepository
    private val aiHealthConsentVersionKey = intPreferencesKey("ai_health_consent_version")
    private val aiHealthConsentDecidedAtKey = stringPreferencesKey("ai_health_consent_decided_at")
    private val aiConsentPendingPushKey = booleanPreferencesKey("ai_consent_pending_push")

    // the mode the user picked by hand. device-local on purpose: which mode you're in is about
    // where this phone is, while the definitions themselves sync. a schedule firing on the
    // tablet shouldn't switch the phone in your pocket
    private val activeModeIdKey = stringPreferencesKey("active_mode_id")

    // defaults live here so onboarding can set the same values later
    val aiSpokenResponses: Flow<Boolean> = context.dataStore.data.map {
        it[aiSpokenResponsesKey] ?: true
    }
    val medicationInTodo: Flow<Boolean> = context.dataStore.data.map {
        it[medicationInTodoKey] ?: true
    }

    val medicationInCalendar: Flow<Boolean> = context.dataStore.data.map {
        it[medicationInCalendarKey] ?: false
    }

    suspend fun setMedicationInTodo(enabled: Boolean) {
        context.dataStore.edit { it[medicationInTodoKey] = enabled }
    }

    suspend fun setMedicationInCalendar(enabled: Boolean) {
        context.dataStore.edit { it[medicationInCalendarKey] = enabled }
    }

    val aiChatReadAloud: Flow<Boolean> = context.dataStore.data.map {
        it[aiChatReadAloudKey] ?: false
    }
    val aiVoiceName: Flow<String?> = context.dataStore.data.map { it[aiVoiceNameKey] }

    // off by default: it's a big change in how the app feels and it overlaps with the system's
    // own Select to Speak, so it has to be asked for
    val readAloudTaps: Flow<Boolean> = context.dataStore.data.map {
        it[readAloudTapsKey] ?: false
    }
    val aiHealthPersonalization: Flow<Boolean> = context.dataStore.data.map {
        it[aiHealthPersonalizationKey] ?: false
    }

    // off by default, the motion is doing work and muting it for everyone would leave a lot of
    // the UI silent. on is for when movement is itself the distraction. stored as what the switch
    // says rather than its opposite, so nothing in between has to remember to invert it
    val reduceMotion: Flow<Boolean> = context.dataStore.data.map {
        it[reduceMotionKey] ?: false
    }

    suspend fun setAiSpokenResponses(enabled: Boolean) {
        context.dataStore.edit { it[aiSpokenResponsesKey] = enabled }
    }

    suspend fun setAiChatReadAloud(enabled: Boolean) {
        context.dataStore.edit { it[aiChatReadAloudKey] = enabled }
    }

    suspend fun setReadAloudTaps(enabled: Boolean) {
        context.dataStore.edit { it[readAloudTapsKey] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[reduceMotionKey] = enabled }
    }

    val homeOpens: Flow<Int> = context.dataStore.data.map { it[homeOpensKey] ?: 0 }

    val motionTipDone: Flow<Boolean> = context.dataStore.data.map { it[motionTipDoneKey] ?: false }

    // capped, so a long-running install isn't writing a bigger number to disk every visit for
    // the rest of its life. nothing above the threshold means anything
    suspend fun noteHomeOpened() {
        context.dataStore.edit {
            val seen = it[homeOpensKey] ?: 0
            if (seen <= HOME_OPENS_CAP) it[homeOpensKey] = seen + 1
        }
    }

    suspend fun setMotionTipDone() {
        context.dataStore.edit { it[motionTipDoneKey] = true }
    }

    suspend fun setAiVoiceName(name: String?) {
        context.dataStore.edit {
            if (name == null) it.remove(aiVoiceNameKey) else it[aiVoiceNameKey] = name
        }
    }

    // no bare setter for aiHealthPersonalization on purpose. writing the flag without a version
    // and a timestamp is what made consent unaccountable in the first place, so go through
    // AiConsentRepository, which calls recordAiHealthConsent below

    // the wording the stored decision was given against. 0 means never decided here, which is
    // not the same as a decision of no (version >= 1 with the flag false)
    val aiHealthConsentVersion: Flow<Int> = context.dataStore.data.map {
        it[aiHealthConsentVersionKey] ?: 0
    }

    val aiHealthConsentDecidedAt: Flow<String?> = context.dataStore.data.map {
        it[aiHealthConsentDecidedAtKey]
    }

    // set when a decision hasn't reached Supabase yet, so the sync pass owes it a push
    val aiConsentPendingPush: Flow<Boolean> = context.dataStore.data.map {
        it[aiConsentPendingPushKey] ?: false
    }

    // one edit, so a decision and its provenance can never disagree
    suspend fun recordAiHealthConsent(
        granted: Boolean,
        version: Int,
        decidedAt: String,
        pendingPush: Boolean,
    ) {
        context.dataStore.edit {
            it[aiHealthPersonalizationKey] = granted
            it[aiHealthConsentVersionKey] = version
            it[aiHealthConsentDecidedAtKey] = decidedAt
            it[aiConsentPendingPushKey] = pendingPush
        }
    }

    suspend fun setAiConsentPendingPush(pending: Boolean) {
        context.dataStore.edit { it[aiConsentPendingPushKey] = pending }
    }

    // an absent value keeps older installs working, where null meant schedules decide. the
    // reserved value tells an explicit 'no mode' apart from that absence
    val modeSelection: Flow<ModeSelection> = context.dataStore.data.map {
        ModeSelection.fromStoredValue(it[activeModeIdKey])
    }

    // legacy view for code that only understands a manual id
    val activeModeId: Flow<String?> = modeSelection.map { (it as? ModeSelection.Manual)?.modeId }

    suspend fun setModeSelection(selection: ModeSelection) {
        context.dataStore.edit { preferences ->
            val stored = selection.toStoredValue()
            if (stored == null) preferences.remove(activeModeIdKey)
            else preferences[activeModeIdKey] = stored
        }
    }

    // legacy writer: null keeps its original meaning of returning to schedules
    suspend fun setActiveModeId(id: String?) {
        setModeSelection(id?.let(ModeSelection::Manual) ?: ModeSelection.Automatic)
    }

    // normalised vertical edge position in -1..1, independent of screen size
    val calendarVoiceHandlePosition: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[calendarVoiceHandlePositionKey] ?: 0f).coerceIn(-1f, 1f)
    }

    suspend fun setCalendarVoiceHandlePosition(position: Float) {
        context.dataStore.edit {
            it[calendarVoiceHandlePositionKey] = position.coerceIn(-1f, 1f)
        }
    }

    suspend fun isCalendarVoiceTipDismissed(): Boolean =
        context.dataStore.data.first()[calendarVoiceTipDismissedKey] ?: false

    suspend fun dismissCalendarVoiceTip() {
        context.dataStore.edit { it[calendarVoiceTipDismissedKey] = true }
    }

    // the one-time 'what is a mode' card on the home tile
    val modesIntroSeen: Flow<Boolean> = context.dataStore.data.map { it[modesIntroSeenKey] ?: false }

    suspend fun setModesIntroSeen() {
        context.dataStore.edit { it[modesIntroSeenKey] = true }
    }

    // how many times Pomodoro has asked about Do Not Disturb before a session
    suspend fun dndStartPromptsShown(): Int =
        context.dataStore.data.first()[dndStartPromptsShownKey] ?: 0

    suspend fun incrementDndStartPrompts() {
        context.dataStore.edit { it[dndStartPromptsShownKey] = (it[dndStartPromptsShownKey] ?: 0) + 1 }
    }

    // google calendar sync cadence
    private val calendarSyncFrequencyKey = stringPreferencesKey("calendar_sync_frequency")

    val calendarSyncFrequency: Flow<CalendarSyncFrequency> = context.dataStore.data.map { prefs ->
        CalendarSyncFrequency.fromName(prefs[calendarSyncFrequencyKey])
    }

    suspend fun getCalendarSyncFrequency(): CalendarSyncFrequency =
        CalendarSyncFrequency.fromName(
            context.dataStore.data.map { it[calendarSyncFrequencyKey] }.first()
        )

    suspend fun setCalendarSyncFrequency(freq: CalendarSyncFrequency) {
        context.dataStore.edit { it[calendarSyncFrequencyKey] = freq.name }
    }

    // google calendar sync outcome, recorded so the user can tell whether background sync ran.
    // without it a sync that never fires and one that fires with nothing to push look identical
    private val calendarLastSyncedAtKey = longPreferencesKey("calendar_last_synced_at")
    private val calendarLastSyncResultKey = stringPreferencesKey("calendar_last_sync_result")

    // epoch millis of the last completed sync, null if it has never run
    val calendarLastSyncedAt: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[calendarLastSyncedAtKey]?.takeIf { it > 0L }
    }

    // short outcome of that last sync, e.g. 'Pushed 3 events'
    val calendarLastSyncResult: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[calendarLastSyncResultKey]?.takeIf { it.isNotBlank() }
    }

    suspend fun recordCalendarSync(atMillis: Long, result: String) {
        context.dataStore.edit { prefs ->
            prefs[calendarLastSyncedAtKey] = atMillis
            prefs[calendarLastSyncResultKey] = result
        }
    }

    suspend fun setSimplifiedWorkspace(enabled: Boolean) {
        // update the synchronous cache first so a screen opened right after the toggle already
        // reflects it, the collector lags by a frame
        simplifiedWorkspaceCache = enabled
        context.dataStore.edit { prefs ->
            prefs[simplifiedWorkspaceKey] = enabled
        }
    }

    // notification frequency

    private fun decodeKinds(raw: Set<String>?): Set<ReminderKind> =
        raw?.mapNotNull { ReminderKind.fromName(it) }?.toSet()
            ?: ReminderKind.entries.toSet() // absent = all enabled (legacy default)

    val todoReminderKinds: Flow<Set<ReminderKind>> = context.dataStore.data.map {
        decodeKinds(it[todoReminderKindsKey])
    }

    val calendarReminderKinds: Flow<Set<ReminderKind>> = context.dataStore.data.map {
        decodeKinds(it[calendarReminderKindsKey])
    }

    val dailySummaryEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[dailySummaryEnabledKey] ?: true
    }

    val pomodoroNudgeFrequency: Flow<PomodoroNudgeFrequency> = context.dataStore.data.map {
        runCatching { PomodoroNudgeFrequency.valueOf(it[pomodoroNudgeFrequencyKey] ?: "") }
            .getOrDefault(PomodoroNudgeFrequency.OFF)
    }

    // nudge time as HH:mm, defaults to 10:00
    val pomodoroNudgeTime: Flow<String> = context.dataStore.data.map {
        it[pomodoroNudgeTimeKey] ?: DEFAULT_NUDGE_TIME
    }

    val pomodoroBreakReminders: Flow<Boolean> = context.dataStore.data.map {
        it[pomodoroBreakRemindersKey] ?: true
    }

    // one-shot reads for schedulers and receivers that aren't collectors
    suspend fun reminderKindsSnapshot(category: com.muradgalayev.brainbuddy.data.notifications.ReminderCategory): Set<ReminderKind> {
        val key = when (category) {
            com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.TODO -> todoReminderKindsKey
            com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.CALENDAR -> calendarReminderKindsKey
        }
        return decodeKinds(context.dataStore.data.first()[key])
    }

    suspend fun dailySummaryEnabledSnapshot(): Boolean =
        context.dataStore.data.first()[dailySummaryEnabledKey] ?: true

    suspend fun pomodoroNudgeFrequencySnapshot(): PomodoroNudgeFrequency =
        runCatching {
            PomodoroNudgeFrequency.valueOf(
                context.dataStore.data.first()[pomodoroNudgeFrequencyKey] ?: ""
            )
        }.getOrDefault(PomodoroNudgeFrequency.OFF)

    suspend fun pomodoroNudgeTimeSnapshot(): String =
        context.dataStore.data.first()[pomodoroNudgeTimeKey] ?: DEFAULT_NUDGE_TIME

    suspend fun pomodoroBreakRemindersSnapshot(): Boolean =
        context.dataStore.data.first()[pomodoroBreakRemindersKey] ?: true

    suspend fun setReminderKinds(
        category: com.muradgalayev.brainbuddy.data.notifications.ReminderCategory,
        kinds: Set<ReminderKind>
    ) {
        val key = when (category) {
            com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.TODO -> todoReminderKindsKey
            com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.CALENDAR -> calendarReminderKindsKey
        }
        context.dataStore.edit { it[key] = kinds.map { k -> k.name }.toSet() }
    }

    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dailySummaryEnabledKey] = enabled }
    }

    suspend fun setPomodoroNudgeFrequency(freq: PomodoroNudgeFrequency) {
        context.dataStore.edit { it[pomodoroNudgeFrequencyKey] = freq.name }
    }

    suspend fun setPomodoroNudgeTime(hhmm: String) {
        context.dataStore.edit { it[pomodoroNudgeTimeKey] = hhmm }
    }

    suspend fun setPomodoroBreakReminders(enabled: Boolean) {
        context.dataStore.edit { it[pomodoroBreakRemindersKey] = enabled }
    }

    // three-state: true, false, or null for never resolved on this device. Splash uses null as
    // the signal to consult Supabase before routing
    suspend fun getSurveyCompletedCached(userId: String): Boolean? {
        val key = surveyCompletedKey(userId)
        return context.dataStore.data.map { it[key] }.first()
    }

    suspend fun setSurveyCompletedCached(userId: String, completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[surveyCompletedKey(userId)] = completed
        }
    }

    suspend fun isOnboardingSkipped(userId: String): Boolean {
        val key = onboardingSkippedKey(userId)
        return context.dataStore.data.map { it[key] }.first() ?: false
    }

    suspend fun setOnboardingSkipped(userId: String, skipped: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[onboardingSkippedKey(userId)] = skipped
        }
    }

    fun activeConversationIdFlow(userId: String): Flow<String?> {
        val key = activeConversationKey(userId)
        return context.dataStore.data.map { it[key] }
    }

    suspend fun setActiveConversationId(userId: String, id: String?) {
        context.dataStore.edit { prefs ->
            val key = activeConversationKey(userId)
            if (id == null) prefs.remove(key) else prefs[key] = id
        }
    }

    companion object {
        // visits before the motion tip is allowed to appear, and where the counter stops
        const val MOTION_TIP_AFTER_OPENS = 6
        private const val HOME_OPENS_CAP = MOTION_TIP_AFTER_OPENS

        const val DEFAULT_NUDGE_TIME = "10:00"
        // 'medications' is deliberately absent: it moved to its own page under Health, and a saved
        // order containing it is filtered against this list on read, so existing users lose the
        // duplicate card without needing a migration
        val DEFAULT_WELLNESS_CARDS = listOf("activity", "sleep", "body")
    }
}
