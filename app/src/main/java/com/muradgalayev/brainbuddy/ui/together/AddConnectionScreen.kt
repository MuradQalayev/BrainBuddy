package com.muradgalayev.brainbuddy.ui.together

import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Radar
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
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// Myndora Together is invite-only. the private link is the way in: minted here, sent over
// whatever the two people already use, redeemed by tapping it or pasting it into 'Have an
// invite?'. finding people through phone contacts still exists, but tucked into a collapsed Beta
// section below, because it needs SMS verification and a contacts permission to do anything
@Composable
fun AddConnectionScreen(
    onBack: () -> Unit,
    onOpenNearby: () -> Unit = {},
    viewModel: TogetherViewModel = hiltViewModel(),
) {
    val inviteState by viewModel.invite.collectAsState()
    val phoneState by viewModel.phoneVerification.collectAsState()
    val contactState by viewModel.contactDiscovery.collectAsState()
    val context = LocalContext.current
    var shareError by rememberSaveable { mutableStateOf<String?>(null) }
    var betaOpen by rememberSaveable { mutableStateOf(false) }

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

    TogetherScaffold(title = stringResource(R.string.together_add_someone), onBack = onBack) {
        TogetherHero(
            title = stringResource(R.string.together_invite_hero_title),
            description = stringResource(R.string.together_invite_hero_desc),
            icon = Icons.Rounded.Link,
        )

        NearbyEntryCard(onOpen = onOpenNearby)

        InviteLinkCard(
            state = inviteState,
            onRelationChange = viewModel::setInviteRelation,
            onCreate = viewModel::createInviteLink,
            onShare = { url -> shareInvite(context, url) },
            onRevoke = viewModel::revokeInviteLinks,
        )

        HaveInviteCard(onOpen = viewModel::openPastedInvite)

        BetaContactsHeader(open = betaOpen, onToggle = { betaOpen = !betaOpen })

        AnimatedVisibility(
            visible = betaOpen,
            enter = expandVertically(tween(320)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(180)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                context.getString(R.string.together_no_sms_app)
                            }
                        },
                    )

                    PrivacyBoundaryCard()
                }
            }
        }
    }
}

// the in-person way in. both phones on the radar, one tap sends the same single-use invite a link would
@Composable
private fun NearbyEntryCard(onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TogetherCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Radar,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.together_nearby_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.together_nearby_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

// the collapsed entry to phone-contact discovery, badged Beta so nobody mistakes it for the main
// way in
@Composable
private fun BetaContactsHeader(open: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(260), label = "betaChevron")
    TogetherCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.secondary.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ContactPhone,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.together_beta_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.together_beta_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.secondaryContainer)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.together_beta_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (open) R.string.common_collapse else R.string.common_expand),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevron },
            )
        }
    }
}

// the receiving side. tapping the link works when the app it arrived in makes it tappable; this
// is for every other case. anything with our link in it works, so the whole forwarded message
// can be pasted as it is
@Composable
private fun HaveInviteCard(onOpen: (String) -> Boolean) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.tertiary.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MarkEmailRead,
                    contentDescription = null,
                    tint = colors.tertiary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.together_have_invite),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.together_have_invite_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                invalid = false
            },
            placeholder = { Text(stringResource(R.string.together_paste_hint)) },
            singleLine = true,
            isError = invalid,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                TextButton(onClick = {
                    clipboard.getText()?.text?.let {
                        text = it
                        invalid = false
                    }
                }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.together_paste))
                }
            },
            supportingText = if (invalid) {
                { Text(stringResource(R.string.together_not_invite)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { invalid = !onOpen(text) },
            enabled = text.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.together_open_invite), fontWeight = FontWeight.SemiBold)
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
                        state.isVerified && state.isDemo -> stringResource(R.string.together_confirmation_preview)
                        state.isVerified -> stringResource(R.string.together_phone_verified)
                        else -> stringResource(R.string.together_verify_phone_first)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    when {
                        state.isVerified && state.isDemo ->
                            stringResource(R.string.together_demo_only, state.verifiedPhone.orEmpty())
                        state.isVerified -> state.verifiedPhone.orEmpty()
                        else -> stringResource(R.string.together_works_for_all)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (!state.isVerified) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.together_sms_verified_only),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.phoneInput,
                onValueChange = onPhoneChange,
                label = { Text(stringResource(R.string.together_phone_with_code)) },
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
                    Text(if (state.codeSent) stringResource(R.string.together_send_new_code) else stringResource(R.string.together_send_sms_code))
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
                    Text(stringResource(R.string.together_preview_no_sms))
                }
            }

            if (state.codeSent) {
                Spacer(Modifier.height(12.dp))
                if (state.isDemo) {
                    Text(
                        stringResource(R.string.together_dev_preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(R.string.together_six_digit)) },
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
                        Text(stringResource(R.string.together_verify_phone), fontWeight = FontWeight.Bold)
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
internal fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
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
    onShare: (url: String) -> Unit,
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
                    stringResource(R.string.together_send_invite),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.together_single_use),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            stringResource(R.string.together_how_know),
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
                    text = stringResource(relation.labelRes),
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
                    Text(stringResource(R.string.together_create_invite), fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            InviteReady(url = link.url, onShare = { onShare(link.url) }, onRevoke = onRevoke)
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
                    stringResource(R.string.together_people_on_myndora),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    if (demoMode) stringResource(R.string.together_sample_contacts)
                    else stringResource(R.string.together_verified_in_contacts),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (state.loaded && !state.loading) {
                TextButton(onClick = onFind) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_refresh))
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
                    Text(stringResource(R.string.together_matching), color = colors.onSurfaceVariant)
                }
            }

            state.permissionDenied -> {
                Text(
                    stringResource(R.string.together_contacts_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFind, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_try_again)) }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.common_open_settings))
                    }
                }
            }

            state.error != null -> {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = colors.error)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onFind, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_try_again))
                }
            }

            !state.loaded -> {
                Text(
                    stringResource(R.string.together_privacy_body),
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
                    Text(stringResource(R.string.together_find_contacts), fontWeight = FontWeight.Bold)
                }
            }

            state.contacts.isEmpty() -> {
                Text(
                    stringResource(R.string.together_none_discoverable),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            else -> {
                if (demoMode) {
                    Text(
                        stringResource(R.string.together_sample_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(9.dp))
                }
                if (!demoMode && inviteUrl == null && state.contacts.any { !it.isConnected }) {
                    Text(
                        stringResource(R.string.together_create_passcode_first),
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
                Text(stringResource(R.string.together_stat_connected), style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            }
        } else {
            Button(
                onClick = onInvite,
                enabled = canInvite,
                shape = RoundedCornerShape(13.dp),
            ) {
                Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.together_invite))
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
                    stringResource(R.string.together_how_matching),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.together_matching_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

// the minted link: shown, copyable, and one tap from the system share sheet
@Composable
private fun InviteReady(url: String, onShare: () -> Unit, onRevoke: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_800)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tertiary.copy(alpha = .10f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.together_link_ready),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            url,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(url))
            copied = true
        }) {
            AnimatedContent(targetState = copied, label = "inviteCopied") { done ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(if (done) R.string.together_link_copied else R.string.together_copy_link))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onShare,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.together_share_invite), fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.together_link_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
    TextButton(onClick = onRevoke) { Text(stringResource(R.string.together_turn_off_link)) }
}

// the system share sheet: WhatsApp, Telegram, email, a text, whatever is installed. the message
// says how to paste it too, since most of those apps won't make a myndora:// link tappable
private fun shareInvite(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.together_share_message, url))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.together_share_via)))
}

private fun openInviteSms(context: Context, phoneNumber: String, inviteUrl: String): Boolean =
    runCatching {
        val sms = Intent(
            Intent.ACTION_SENDTO,
            Uri.fromParts("smsto", phoneNumber, null),
        ).apply {
            putExtra(
                "sms_body",
                context.getString(R.string.together_sms_body, inviteUrl),
            )
        }
        context.startActivity(sms)
    }.isSuccess
