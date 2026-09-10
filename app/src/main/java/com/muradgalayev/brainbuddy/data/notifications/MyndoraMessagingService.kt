package com.muradgalayev.brainbuddy.data.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.muradgalayev.brainbuddy.data.repository.DeviceTokenRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// receives pushes, and keeps the device's token current. messages arrive data-only rather than
// as a notification payload, deliberately: a notification payload is drawn by the system when
// the app is backgrounded, which means the app never sees it, can't word it the same way as
// the in-app card, and worse, can't take it back. an invite answered on another device would
// sit in the tray until tapped. building it here keeps one notification the app can cancel the
// moment the invite is dealt with
@AndroidEntryPoint
class MyndoraMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifier: FocusInviteNotifier
    @Inject lateinit var deviceTokens: DeviceTokenRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // fires when FCM mints a new token: first launch, reinstall, app data cleared, or a periodic
    // rotation. registering only at sign-in would leave long-lived installs pushing to a token the
    // server no longer has
    override fun onNewToken(token: String) {
        scope.launch { deviceTokens.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "focus_invite" -> {
                val sessionId = message.data["session_id"] ?: return
                notifier.notifyInvite(
                    hostName = message.data["host_name"] ?: "Someone",
                    minutes = message.data["minutes"]?.toIntOrNull() ?: 25,
                    sessionId = sessionId,
                )
            }
            else -> Log.d(TAG, "Ignoring push of type ${message.data["type"]}")
        }
    }

    private companion object {
        const val TAG = "MyndoraMessaging"
    }
}
