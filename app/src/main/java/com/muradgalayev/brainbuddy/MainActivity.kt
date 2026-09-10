package com.muradgalayev.brainbuddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.muradgalayev.brainbuddy.data.auth.PasswordRecoveryState
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.ReadAloudSpeaker
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.ui.accessibility.LocalAnimationsEnabled
import com.muradgalayev.brainbuddy.ui.accessibility.LocalReadAloud
import com.muradgalayev.brainbuddy.ui.accessibility.readAloudHandler
import com.muradgalayev.brainbuddy.ui.navigation.NavGraph
import com.muradgalayev.brainbuddy.ui.theme.ThemeSelection
import com.muradgalayev.brainbuddy.ui.theme.MyndoraTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.TextSpacing

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val questionnaireRoute = MutableStateFlow<String?>(null)

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var passwordRecoveryState: PasswordRecoveryState

    @Inject
    lateinit var readAloudSpeaker: ReadAloudSpeaker

    @Inject
    lateinit var togetherRepository: com.muradgalayev.brainbuddy.data.repository.TogetherRepository

    @Inject
    lateinit var modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result is reflected in checkSelfPermission later */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        safeHandleDeeplinks(intent)
        maybeRequestNotificationPermission()
        enableEdgeToEdge()
        paintWindowBackground()
        setContent {
            // seeded from the warm cache rather than from the defaults. a rotation recreates this activity
            // and re-subscribes to DataStore, and the stored values arrive a few frames later, long enough
            // to paint the default theme first and then cross-fade to the real one, which looks like the
            // app changing its mind
            val themeMode by preferencesManager.themeMode
                .collectAsState(initial = preferencesManager.peekThemeMode() ?: ThemeMode.System)
            val fontMode by preferencesManager.fontMode
                .collectAsState(initial = preferencesManager.peekFontMode() ?: FontMode.DEFAULT)
            // through ModeManager, not straight from preferences: a mode may hide tabs or hold the app
            // still, and the answer has to be the overlay rather than the base
            val enabledNavItems by modeManager.effectiveEnabledNavItems.collectAsState(initial = emptySet())
            val fontSize by preferencesManager.fontSize
                .collectAsState(initial = preferencesManager.peekFontSize() ?: FontSize.Medium)
            val textSpacing by preferencesManager.textSpacing
                .collectAsState(initial = preferencesManager.peekTextSpacing() ?: TextSpacing.DEFAULT)
            val appTheme by preferencesManager.appTheme
                .collectAsState(initial = preferencesManager.peekAppTheme() ?: ThemeSelection.DEFAULT)
            val reduceMotion by modeManager.effectiveReduceMotion.collectAsState(initial = false)
            val modeAccentKey by modeManager.effectiveAccentKey.collectAsState(initial = null)
            val passwordRecoveryActive by passwordRecoveryState.active.collectAsState()
            val requestedQuestionnaireRoute by questionnaireRoute.collectAsState()

            // the theme cross-fade is for the user changing their theme. on a cold start the first
            // emission is the app finding out what the theme already is, and easing into it over 450ms
            // turns a one-frame correction into a visible morph. true immediately when the warm cache
            // already had the answer
            val themeSettled by produceState(preferencesManager.peekThemeMode() != null) {
                preferencesManager.themeMode.first()
                value = true
            }

            MyndoraTheme(
                themeMode = themeMode,
                appTheme = appTheme,
                fontMode = fontMode,
                fontSize = fontSize,
                textSpacing = textSpacing,
                modeAccentKey = modeAccentKey,
                animationsEnabled = !reduceMotion && themeSettled,
            ) {
                val navController = rememberNavController()

                // provided once at the root, so every screen can speak a tap without reaching for the speaker
                // itself. a no-op until the user turns 'Speak what I tap' on
                CompositionLocalProvider(
                    LocalReadAloud provides readAloudHandler(readAloudSpeaker),
                    LocalAnimationsEnabled provides !reduceMotion,
                ) {
                    NavGraph(
                        navController = navController,
                        enabledOptionalRoutes = enabledNavItems,
                        passwordRecoveryActive = passwordRecoveryActive,
                        requestedQuestionnaireRoute = requestedQuestionnaireRoute,
                        onQuestionnaireRouteConsumed = { questionnaireRoute.value = null },
                    )
                }
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        Log.d("FCM_TEST", "Token: $token")
                    }
                    .addOnFailureListener { error ->
                        Log.e("FCM_TEST", "Failed to get token", error)
                    }
            }
        }
    }

    // paints the window itself in the user's own theme before Compose gets a frame. the XML theme
    // can only follow the system night setting, which is the wrong answer for anyone who has
    // forced light or dark inside Myndora, and they'd get a flash of the opposite on every
    // rotation. the warm cache makes the real answer available synchronously here
    private fun paintWindowBackground() {
        val dark = when (preferencesManager.peekThemeMode() ?: ThemeMode.System) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.System ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        val palette = (preferencesManager.peekAppTheme() ?: ThemeSelection.DEFAULT).palette(dark)
        window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        safeHandleDeeplinks(intent)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Supabase's handleDeeplinks throws if the OAuth redirect URL has no access_token fragment
    // (PKCE code flow, a cancelled link, an error redirect). swallow it so the app doesn't crash
    // on a callback we can't parse
    private fun safeHandleDeeplinks(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(
            com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderContract.EXTRA_OPEN_QUESTIONNAIRE
        )?.takeIf {
            it == com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderContract.ROUTE_QUICK ||
                it == com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderContract.ROUTE_DEEP
        }?.let {
            questionnaireRoute.value = it
            // Avoid reopening the survey if Android recreates this activity after the tap.
            intent.removeExtra(
                com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderContract.EXTRA_OPEN_QUESTIONNAIRE
            )
        }
        // sniff for type=recovery before handing to Supabase: once handleDeeplinks consumes the
        // intent, intent.data may be cleared on some SDK paths
        passwordRecoveryState.onDeepLink(intent.data)
        // Together invite links, handled before Supabase gets the intent for the same reason,
        // handleDeeplinks can clear intent.data
        togetherRepository.onDeepLink(intent.data?.toString())
        try {
            supabaseClient.handleDeeplinks(intent)
        } catch (t: Throwable) {
            // Auth callbacks can contain access and refresh tokens, so never include the URI.
            Log.w("MainActivity", "Ignored unparseable auth deep link", t)
        }
    }
}
