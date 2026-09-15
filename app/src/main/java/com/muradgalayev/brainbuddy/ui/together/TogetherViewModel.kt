package com.muradgalayev.brainbuddy.ui.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.BuildConfig
import android.content.Context
import androidx.annotation.StringRes
import com.muradgalayev.brainbuddy.data.auth.authErrorMessage
import com.muradgalayev.brainbuddy.ui.utils.UiText
import com.muradgalayev.brainbuddy.ui.utils.asUiText
import com.muradgalayev.brainbuddy.ui.utils.resolve
import com.muradgalayev.brainbuddy.ui.utils.uiText
import dagger.hilt.android.qualifiers.ApplicationContext
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.TogetherRepository
import com.muradgalayev.brainbuddy.domain.model.ChallengeResult
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.domain.model.InviteLink
import com.muradgalayev.brainbuddy.domain.model.InvitePreview
import com.muradgalayev.brainbuddy.domain.model.MyndoraContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.muradgalayev.brainbuddy.R

// state of the 'share an invite link' card
data class InviteLinkState(
    val relation: ConnectionRelation = ConnectionRelation.FRIEND,
    val link: InviteLink? = null,
    val creating: Boolean = false,
    val error: String? = null,
)

// a tapped invite link, resolved and waiting for the user to confirm
data class PendingInvite(
    val token: String,
    val preview: InvitePreview? = null,
    val loading: Boolean = true,
    val accepting: Boolean = false,
    val error: String? = null,
)

// keyed by request id so two prompts don't share a field
data class AnswerState(
    val answer: String = "",
    val checking: Boolean = false,
    val error: String? = null,
    val accepted: Boolean = false,
)

data class PhoneVerificationState(
    val verifiedPhone: String? = null,
    val phoneInput: String = "",
    val pendingPhone: String? = null,
    val code: String = "",
    val sendingCode: Boolean = false,
    val codeSent: Boolean = false,
    val verifying: Boolean = false,
    val isDemo: Boolean = false,
    val error: String? = null,
) {
    val isVerified: Boolean get() = !verifiedPhone.isNullOrBlank()
}

data class ContactDiscoveryState(
    val contacts: List<MyndoraContact> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val permissionDenied: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TogetherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TogetherRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    // A local UI preview only. The server still requires a genuinely confirmed auth.users phone,
    // and this entry point is unavailable in release builds.
    val contactDemoAvailable: Boolean = BuildConfig.DEBUG

    val connections = repository.connections
    // someone accepted an invite this user sent
    val newlyConnected = repository.newlyConnected
    // a sign-in just brought this user's own deactivated account back
    val reactivated = authRepository.reactivated
    val incomingRequests = repository.incomingRequests
    val outgoingRequests = repository.outgoingRequests

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _phoneVerification = MutableStateFlow(
        PhoneVerificationState(
            verifiedPhone = authRepository.getVerifiedPhone(),
            phoneInput = authRepository.getCurrentUserPhone().orEmpty(),
        )
    )
    val phoneVerification: StateFlow<PhoneVerificationState> =
        _phoneVerification.asStateFlow()

    private val _contactDiscovery = MutableStateFlow(ContactDiscoveryState())
    val contactDiscovery: StateFlow<ContactDiscoveryState> = _contactDiscovery.asStateFlow()

    // answering a request
    private val _answers = MutableStateFlow<Map<String, AnswerState>>(emptyMap())
    val answers: StateFlow<Map<String, AnswerState>> = _answers.asStateFlow()

    init {
        // seed from whatever the last sync left behind, then revalidate: the list renders immediately
        // instead of flashing an empty state on every visit
        if (!repository.hasLoadedOnce) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            repository.refresh()
            _refreshing.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun onVerificationPhoneChange(value: String) {
        _phoneVerification.value = _phoneVerification.value.copy(
            phoneInput = value.take(32),
            codeSent = false,
            pendingPhone = null,
            code = "",
            isDemo = false,
            error = null,
        )
    }

    fun startContactConfirmationDemo() {
        if (!contactDemoAvailable) return
        val current = _phoneVerification.value
        val phone = normalizeVerificationPhone(current.phoneInput) ?: DEMO_PHONE
        _phoneVerification.value = current.copy(
            phoneInput = phone,
            pendingPhone = phone,
            code = "",
            sendingCode = false,
            codeSent = true,
            verifying = false,
            isDemo = true,
            error = null,
        )
    }

    fun requestPhoneVerification() {
        val current = _phoneVerification.value
        if (current.sendingCode) return
        val phone = normalizeVerificationPhone(current.phoneInput)
        if (phone == null) {
            _phoneVerification.value = current.copy(
                error = context.getString(R.string.together_phone_format),
            )
            return
        }

        viewModelScope.launch {
            _phoneVerification.value = current.copy(
                sendingCode = true,
                isDemo = false,
                error = null,
            )
            runCatching { authRepository.requestPhoneVerification(phone) }
                .onSuccess {
                    _phoneVerification.value = _phoneVerification.value.copy(
                        phoneInput = phone,
                        pendingPhone = phone,
                        sendingCode = false,
                        codeSent = true,
                    )
                }
                .onFailure { error ->
                    _phoneVerification.value = _phoneVerification.value.copy(
                        sendingCode = false,
                        error = authErrorMessage(
                            error,
                            R.string.together_code_send_failed,
                        ).resolve(context),
                    )
                }
        }
    }

    fun onVerificationCodeChange(value: String) {
        _phoneVerification.value = _phoneVerification.value.copy(
            code = value.filter(Char::isDigit).take(6),
            error = null,
        )
    }

    fun verifyPhone() {
        val current = _phoneVerification.value
        val phone = current.pendingPhone ?: return
        if (current.verifying || current.code.length != 6) {
            if (current.code.length != 6) {
                _phoneVerification.value = current.copy(error = context.getString(R.string.together_enter_code))
            }
            return
        }

        if (current.isDemo) {
            if (current.code != DEMO_OTP) {
                _phoneVerification.value = current.copy(
                    error = context.getString(R.string.together_demo_code, DEMO_OTP),
                )
                return
            }
            _phoneVerification.value = PhoneVerificationState(
                verifiedPhone = phone,
                phoneInput = phone,
                isDemo = true,
            )
            _contactDiscovery.value = ContactDiscoveryState(
                contacts = DEMO_CONTACTS,
                loaded = true,
            )
            return
        }

        viewModelScope.launch {
            _phoneVerification.value = current.copy(verifying = true, error = null)
            runCatching { authRepository.verifyPhoneChange(phone, current.code) }
                .onSuccess {
                    val verified = authRepository.getVerifiedPhone()
                    _phoneVerification.value = if (verified != null) {
                        PhoneVerificationState(verifiedPhone = verified, phoneInput = verified)
                    } else {
                        _phoneVerification.value.copy(
                            verifying = false,
                            error = context.getString(R.string.together_phone_not_confirmed),
                        )
                    }
                }
                .onFailure { error ->
                    _phoneVerification.value = _phoneVerification.value.copy(
                        verifying = false,
                        error = authErrorMessage(
                            error,
                            R.string.together_code_verify_failed,
                        ).resolve(context),
                    )
                }
        }
    }

    fun loadMyndoraContacts() {
        val current = _contactDiscovery.value
        if (current.loading || !_phoneVerification.value.isVerified) return
        if (_phoneVerification.value.isDemo) {
            _contactDiscovery.value = ContactDiscoveryState(
                contacts = DEMO_CONTACTS,
                loaded = true,
            )
            return
        }
        viewModelScope.launch {
            _contactDiscovery.value = current.copy(
                loading = true,
                permissionDenied = false,
                error = null,
            )
            repository.discoverMyndoraContacts()
                .onSuccess {
                    _contactDiscovery.value = ContactDiscoveryState(
                        contacts = it,
                        loaded = true,
                    )
                }
                .onFailure { error ->
                    _contactDiscovery.value = current.copy(
                        loading = false,
                        loaded = true,
                        error = error.friendlyMessage(context, R.string.together_contacts_failed),
                    )
                }
        }
    }

    fun onContactsPermissionDenied() {
        _contactDiscovery.value = _contactDiscovery.value.copy(
            loading = false,
            permissionDenied = true,
            error = null,
        )
    }

    private companion object {
        const val DEMO_PHONE = "+39 333 000 0000"
        const val DEMO_OTP = "123456"

        val DEMO_CONTACTS = listOf(
            MyndoraContact(
                userId = "demo-connected-contact",
                contactName = "Sofia · Demo",
                phoneNumber = "+39 ••• ••• 0101",
                isConnected = true,
            ),
            MyndoraContact(
                userId = "demo-new-contact",
                contactName = "Marco · Demo",
                phoneNumber = "+39 ••• ••• 0102",
            ),
        )
    }

    // invite links

    private val _invite = MutableStateFlow(InviteLinkState())
    val invite: StateFlow<InviteLinkState> = _invite.asStateFlow()

    private val _pendingInvite = MutableStateFlow<PendingInvite?>(null)
    val pendingInvite: StateFlow<PendingInvite?> = _pendingInvite.asStateFlow()

    // a tapped invite link waiting to be shown. same singleton across instances
    val incomingInviteToken = repository.incomingInviteToken

    fun openPastedInvite(text: String): Boolean = repository.openPastedInvite(text)

    fun setInviteRelation(relation: ConnectionRelation) {
        // a link already minted was labelled with the old relation, so drop it rather than let the
        // card show a link that means something else now
        _invite.value = InviteLinkState(relation = relation)
    }

    fun createInviteLink() {
        val current = _invite.value
        if (current.creating) return
        viewModelScope.launch {
            _invite.value = current.copy(creating = true, error = null)
            repository.createInviteLink(current.relation)
                .onSuccess { _invite.value = _invite.value.copy(creating = false, link = it) }
                .onFailure {
                    _invite.value = _invite.value.copy(
                        creating = false,
                        error = it.friendlyMessage(context, R.string.together_invite_create_failed),
                    )
                }
        }
    }

    // kills every unredeemed link this user has minted
    fun revokeInviteLinks() {
        viewModelScope.launch {
            repository.revokeInviteLinks()
                .onSuccess {
                    _invite.value = _invite.value.copy(link = null)
                    _message.value = context.getString(R.string.together_invites_off)
                }
                .onFailure { _message.value = it.friendlyMessage(context, R.string.together_revoke_failed) }
        }
    }

    // resolves a tapped invite link. shows who is inviting before redeeming, deliberately: an
    // invite is single-use, so accepting the wrong one silently burns it
    fun onInviteLinkOpened(token: String) {
        // clear the shared slot at once so a recomposition can't re-open it
        repository.consumeInviteToken()
        _pendingInvite.value = PendingInvite(token = token, loading = true)
        viewModelScope.launch {
            repository.peekInvite(token)
                .onSuccess { preview ->
                    _pendingInvite.value = PendingInvite(
                        token = token,
                        preview = preview,
                        loading = false,
                        error = preview.reason.takeIf { !preview.valid },
                    )
                }
                .onFailure {
                    _pendingInvite.value = PendingInvite(
                        token = token,
                        loading = false,
                        error = it.friendlyMessage(context, R.string.together_invite_open_failed),
                    )
                }
        }
    }

    fun acceptPendingInvite() {
        val pending = _pendingInvite.value ?: return
        if (pending.accepting || pending.preview?.valid != true) return
        viewModelScope.launch {
            _pendingInvite.value = pending.copy(accepting = true, error = null)
            repository.redeemInvite(pending.token)
                .onSuccess {
                    _pendingInvite.value = null
                    _message.value = context.getString(R.string.together_connected_with, pending.preview.name)
                }
                .onFailure {
                    _pendingInvite.value = _pendingInvite.value?.copy(
                        accepting = false,
                        error = it.friendlyMessage(context, R.string.together_accept_failed),
                    )
                }
        }
    }

    fun dismissPendingInvite() {
        _pendingInvite.value = null
    }

    // answering

    fun onAnswerChange(requestId: String, value: String) {
        _answers.value = _answers.value + (requestId to answerState(requestId).copy(
            answer = value,
            error = null,
        ))
    }

    private fun answerState(requestId: String): AnswerState =
        _answers.value[requestId] ?: AnswerState()

    fun submitAnswer(requestId: String) {
        val state = answerState(requestId)
        if (state.answer.isBlank() || state.checking) return
        viewModelScope.launch {
            _answers.value = _answers.value + (requestId to state.copy(checking = true, error = null))
            repository.answerChallenge(requestId, state.answer.trim())
                .onSuccess { result ->
                    when (result) {
                        ChallengeResult.Accepted -> {
                            _answers.value = _answers.value - requestId
                            _message.value = context.getString(R.string.together_connected)
                        }
                        is ChallengeResult.Wrong -> {
                            _answers.value = _answers.value + (requestId to state.copy(
                                checking = false,
                                error = if (result.attemptsLeft > 0) {
                                    if (result.attemptsLeft == 1) context.getString(R.string.together_wrong_one)
                                    else context.getString(R.string.together_wrong_many, result.attemptsLeft)
                                } else {
                                    context.getString(R.string.together_too_many_wrong)
                                },
                            ))
                        }
                        ChallengeResult.Locked -> {
                            _answers.value = _answers.value + (requestId to state.copy(
                                checking = false,
                                error = context.getString(R.string.together_locked),
                            ))
                        }
                    }
                }
                .onFailure { error ->
                    _answers.value = _answers.value + (requestId to state.copy(
                        checking = false,
                        error = error.friendlyMessage(context, R.string.together_check_failed),
                    ))
                }
        }
    }

    fun declineRequest(requestId: String) {
        viewModelScope.launch {
            repository.declineRequest(requestId)
                .onSuccess { _message.value = context.getString(R.string.together_request_declined) }
                .onFailure { _message.value = it.friendlyMessage(context, R.string.together_decline_failed) }
        }
    }

    fun cancelOutgoing(requestId: String) {
        viewModelScope.launch {
            repository.cancelRequest(requestId)
                .onSuccess { _message.value = context.getString(R.string.together_request_withdrawn) }
                .onFailure { _message.value = it.friendlyMessage(context, R.string.together_withdraw_failed) }
        }
    }

}

internal fun normalizeVerificationPhone(raw: String): String? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith('+')) return null
    if (trimmed.drop(1).any { !it.isDigit() && it !in " -()." }) return null
    val normalized = "+" + trimmed.drop(1).filter(Char::isDigit)
    return normalized.takeIf { Regex("^\\+[1-9]\\d{7,14}$").matches(it) }
}

// turns a Supabase or PostgREST failure into something worth reading. our RPC guard clauses
// raise messages already written for a human, so those are surfaced verbatim. two setup
// failures are named explicitly because the generic wording sent me chasing the wrong thing
// once already: a missing function means the migration hasn't been run, and a missing gen_salt
// or crypt means it ran but pgcrypto isn't on the function's search_path. Transport details are
// never logged because Ktor can place live Authorization headers inside exception messages.
internal fun Throwable.friendlyMessage(context: Context, @StringRes fallback: Int): String =
    friendlyMessage(fallback).resolve(context)

// guard-clause text raised by our own RPCs passes through as written, which means in English:
// the server has no idea which language the app is in
internal fun Throwable.friendlyMessage(@StringRes fallbackRes: Int): UiText {
    val fallback = uiText(fallbackRes)
    val raw = message?.trim().orEmpty()
    // Ktor exceptions can embed the full URL and request headers, including a live Bearer token.
    // Log only the exception type and never pass raw transport text or the Throwable itself.
    android.util.Log.w("MyndoraTogether", "Request failed (${this::class.java.simpleName})")
    if (raw.isEmpty()) return fallback

    val containsTransportSecret = raw.contains("authorization", ignoreCase = true) ||
        raw.contains("bearer ", ignoreCase = true) ||
        raw.contains("apikey", ignoreCase = true) ||
        raw.contains("https://", ignoreCase = true) ||
        raw.contains("http://", ignoreCase = true)
    if (containsTransportSecret) return fallback

    // PostgREST wraps a raise in JSON, so pull the inner message out when it's there. the
    // escape-aware pattern matters: Postgres quotes identifiers in its own errors, and a naive
    // negated character class stops at the first escaped quote, which sent real diagnostics
    // to the fallback
    val extracted = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        .find(raw)?.groupValues?.get(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\n", " ")
        ?.replace("\\\\", "\\")
        ?: raw

    return when {
        // schema cache couldn't find the function at all
        raw.contains("PGRST202") || extracted.contains("Could not find the function") ->
            uiText(R.string.together_err_not_setup)

        // pgcrypto lives in the extensions schema on Supabase, so a function pinned to
        // search_path=public can't see crypt() or gen_salt()
        extracted.contains("gen_salt") || extracted.contains("crypt(") ->
            uiText(R.string.together_err_pgcrypto)

        raw.contains("Unable to resolve host") || raw.contains("UnknownHost") ->
            uiText(R.string.together_err_offline)

        // a SQL defect, not something the user did. say so plainly rather than implying they typed
        // something wrong, and keep the detail for the report
        extracted.contains("is ambiguous") || extracted.contains("does not exist") ->
            uiText(R.string.together_err_db_update, extracted)

        // a raise from our own guard clauses: short, prose, already user-facing
        extracted.length <= 200 && !extracted.startsWith("{") && !extracted.contains("SQLSTATE") ->
            extracted.asUiText()

        else -> fallback
    }
}
