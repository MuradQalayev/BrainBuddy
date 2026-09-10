package com.muradgalayev.brainbuddy.ui.together

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.domain.model.MyndoraContact

// Telegram-style contact discovery, with a narrower server result: contacts are read locally,
// normalized and hashed; the server returns only verified Myndora accounts matching those hashes.
@Composable
fun AddConnectionScreen(
    onBack: () -> Unit,
    viewModel: TogetherViewModel = hiltViewModel(),
) {
    val inviteState by viewModel.invite.collectAsState()
    val phoneState by viewModel.phoneVerification.collectAsState()
    val contactState by viewModel.contactDiscovery.collectAsState()
    val context = LocalContext.current
    var shareError by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.loadMyndoraContacts()
        else viewModel.onContactsPermissionDenied()
    }

    val findContacts = {
        shareError = null
        if (phoneState.isDemo) {
            viewModel.loadMyndoraContacts()
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.loadMyndoraContacts()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    TogetherScaffold(title = "Add someone", onBack = onBack) {
        TogetherHero(
            title = "People you already know",
            description = "See which phone contacts have a verified Myndora account. " +
                "There is no public username or name search.",
            icon = Icons.Rounded.ContactPhone,
        )

        PhoneVerificationCard(
            state = phoneState,
            onPhoneChange = viewModel::onVerificationPhoneChange,
            onSendCode = viewModel::requestPhoneVerification,
            onCodeChange = viewModel::onVerificationCodeChange,
            onVerify = viewModel::verifyPhone,
            demoAvailable = viewModel.contactDemoAvailable,
            onStartDemo = viewModel::startContactConfirmationDemo,
        )

        if (phoneState.isVerified) {
            InviteLinkCard(
                state = inviteState,
                onRelationChange = viewModel::setInviteRelation,
                onCreate = viewModel::createInviteLink,
                onRevoke = viewModel::revokeInviteLinks,
            )

            MyndoraContactsCard(
                state = contactState,
                inviteUrl = inviteState.link?.url,
                shareError = shareError,
                demoMode = phoneState.isDemo,
                onFind = findContacts,
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                },
                onInvite = { phoneNumber, inviteUrl ->
                    shareError = if (openInviteSms(context, phoneNumber, inviteUrl)) {
                        null
                    } else {
                        "No SMS app is available on this device."
                    }
                },
            )

            PrivacyBoundaryCard()
        }
    }
}

@Composable
private fun PhoneVerificationCard(
    state: PhoneVerificationState,
    onPhoneChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    demoAvailable: Boolean,
    onStartDemo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (state.isVerified) colors.tertiary.copy(alpha = .14f)
                        else colors.primary.copy(alpha = .14f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isVerified) Icons.Rounded.VerifiedUser else Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = if (state.isVerified) colors.tertiary else colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        state.isVerified && state.isDemo -> "Confirmation preview"
                        state.isVerified -> "Phone verified"
                        else -> "Verify your phone first"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    when {
                        state.isVerified && state.isDemo ->
                            "${state.verifiedPhone.orEmpty()} · local demo only"
                        state.isVerified -> state.verifiedPhone.orEmpty()
                        else -> "Works for email and Google sign-in accounts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (!state.isVerified) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Only an SMS-verified number can make an account visible to phone contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.phoneInput,
                onValueChange = onPhoneChange,
                label = { Text("Phone with country code") },
                placeholder = { Text("+39 333 123 4567") },
                leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !state.sendingCode && !state.verifying,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSendCode,
                enabled = !state.sendingCode && !state.verifying,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.sendingCode) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.codeSent) "Send a new code" else "Send SMS code")
                }
            }

            if (demoAvailable && !state.isDemo) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onStartDemo,
                    enabled = !state.sendingCode && !state.verifying,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Preview without SMS")
                }
            }

            if (state.codeSent) {
                Spacer(Modifier.height(12.dp))
                if (state.isDemo) {
                    Text(
                        "Development preview: enter 123456. No SMS was sent and Supabase is unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    label = { Text("6-digit code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !state.verifying,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onVerify,
                    enabled = state.code.length == 6 && !state.verifying,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.verifying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Verify phone", fontWeight = FontWeight.Bold)
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(9.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primary.copy(alpha = .16f) else colors.surfaceVariant.copy(alpha = .45f))
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) colors.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
        )
    }
}

// the invite-link card, the primary way to connect. a one-time token sent over a channel the
// two people already trust replaced the old security question. the relation is chosen before
// minting because it's baked into the token, and changing it afterwards discards the link
// rather than leaving one that means something else
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteLinkCard(
    state: InviteLinkState,
    onRelationChange: (ConnectionRelation) -> Unit,
    onCreate: () -> Unit,
    onRevoke: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Link,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Send an invite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    "Single use · expires in 24 hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "How do you know them?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (relation in ConnectionRelation.entries) {
                ChoicePill(
                    text = relation.label,
                    selected = state.relation == relation,
                    onClick = { onRelationChange(relation) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        val link = state.link
        if (link == null) {
            Button(
                onClick = onCreate,
                enabled = !state.creating,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create invite link", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.tertiary.copy(alpha = .10f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Private passcode ready — choose a contact below",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Send this private passcode only to the contact you intend. It stops working " +
                    "after its first use or when it expires.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = onRevoke) { Text("Turn this link off") }
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

@Composable
private fun MyndoraContactsCard(
    state: ContactDiscoveryState,
    inviteUrl: String?,
    shareError: String?,
    demoMode: Boolean,
    onFind: () -> Unit,
    onOpenSettings: () -> Unit,
    onInvite: (phoneNumber: String, inviteUrl: String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.ContactPhone,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(23.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "People on Myndora",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    if (demoMode) "Sample contacts · nothing was uploaded"
                    else "Only verified accounts already in your contacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (state.loaded && !state.loading) {
                TextButton(onClick = onFind) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when {
            state.loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Matching contacts…", color = colors.onSurfaceVariant)
                }
            }

            state.permissionDenied -> {
                Text(
                    "Contacts access is needed to build this private matched list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFind, modifier = Modifier.weight(1f)) { Text("Try again") }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                        Text("Open settings")
                    }
                }
            }

            state.error != null -> {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = colors.error)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onFind, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }

            !state.loaded -> {
                Text(
                    "With your permission, Myndora reads phone numbers on this device, converts " +
                        "them to hashes, and sends only those hashes to check for verified accounts. " +
                        "Contact names and raw numbers are not uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onFind,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.ContactPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Find Myndora contacts", fontWeight = FontWeight.Bold)
                }
            }

            state.contacts.isEmpty() -> {
                Text(
                    "None of your contacts have a discoverable, verified Myndora phone yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            else -> {
                if (demoMode) {
                    Text(
                        "This is how confirmed contacts will look. These entries are local examples, not real users.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(9.dp))
                }
                if (!demoMode && inviteUrl == null && state.contacts.any { !it.isConnected }) {
                    Text(
                        "Create the private passcode above to invite someone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(9.dp))
                }
                state.contacts.forEachIndexed { index, contact ->
                    if (index > 0) Spacer(Modifier.height(9.dp))
                    MatchedContactRow(
                        contact = contact,
                        canInvite = inviteUrl != null && !demoMode,
                        onInvite = { inviteUrl?.let { onInvite(contact.phoneNumber, it) } },
                    )
                }
            }
        }

        shareError?.let {
            Spacer(Modifier.height(9.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

@Composable
private fun MatchedContactRow(
    contact: MyndoraContact,
    canInvite: Boolean,
    onInvite: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = .38f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TogetherAvatar(name = contact.contactName, avatarUrl = contact.avatarUrl, size = 44.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.contactName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Text(
                contact.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (contact.isConnected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.tertiary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Connected", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            }
        } else {
            Button(
                onClick = onInvite,
                enabled = canInvite,
                shape = RoundedCornerShape(13.dp),
            ) {
                Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Invite")
            }
        }
    }
}

@Composable
private fun PrivacyBoundaryCard() {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "How matching protects your contacts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Names and raw phone numbers stay on your device. Myndora sends normalized " +
                        "phone hashes for a rate-limited match and does not store your address book.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

private fun openInviteSms(context: Context, phoneNumber: String, inviteUrl: String): Boolean =
    runCatching {
        val sms = Intent(
            Intent.ACTION_SENDTO,
            Uri.fromParts("smsto", phoneNumber, null),
        ).apply {
            putExtra(
                "sms_body",
                "Let's connect privately on Myndora. This one-time invite expires in 24 hours: $inviteUrl",
            )
        }
        context.startActivity(sms)
    }.isSuccess
