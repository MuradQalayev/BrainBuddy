package com.muradgalayev.brainbuddy.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.auth.authErrorMessage
import com.muradgalayev.brainbuddy.data.auth.PasswordRecoveryState
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val isSuccess: Boolean = false,
    val forgotPasswordOpen: Boolean = false,
    val forgotPasswordEmail: String = "",
    val forgotPasswordSending: Boolean = false,
    val forgotPasswordError: String? = null,
    val newPasswordOpen: Boolean = false,
    val newPasswordSaving: Boolean = false,
    val newPasswordError: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncCoordinator: SyncCoordinator,
    private val networkObserver: NetworkObserver,
    private val passwordRecoveryState: PasswordRecoveryState,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var awaitingExternalAuth = false

    init {
        // combine sessionStatus with the recovery flag because they can flip in either order:
        // MainActivity sets recovery synchronously from intent.data, but Supabase imports the session
        // on a later coroutine tick. either edge should trigger the dialog once both are true
        viewModelScope.launch {
            combine(
                authRepository.sessionStatus,
                passwordRecoveryState.active,
            ) { status, recovery -> (status is SessionStatus.Authenticated) to recovery }
                .collect { (authenticated, recovery) ->
                    when {
                        authenticated && recovery -> {
                            _uiState.update {
                                it.copy(isLoading = false, newPasswordOpen = true)
                            }
                        }
                        authenticated && awaitingExternalAuth -> {
                            awaitingExternalAuth = false
                            _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                            syncCoordinator.syncAll()
                        }
                    }
                }
        }
    }

    fun onFirstNameChanged(value: String) {
        _uiState.update { it.copy(firstName = value, error = null) }
    }

    fun onLastNameChanged(value: String) {
        _uiState.update { it.copy(lastName = value, error = null) }
    }

    fun onPhoneChanged(value: String) {
        _uiState.update { it.copy(phone = value, error = null) }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(isLoginMode = !it.isLoginMode, error = null, infoMessage = null)
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    // forgot password

    fun openForgotPassword() {
        _uiState.update {
            it.copy(
                forgotPasswordOpen = true,
                // pre-fill with the email they've already typed on the sign-in form
                forgotPasswordEmail = it.email,
                forgotPasswordError = null,
            )
        }
    }

    fun dismissForgotPassword() {
        _uiState.update {
            it.copy(
                forgotPasswordOpen = false,
                forgotPasswordSending = false,
                forgotPasswordError = null,
            )
        }
    }

    fun onForgotEmailChanged(value: String) {
        _uiState.update { it.copy(forgotPasswordEmail = value, forgotPasswordError = null) }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.forgotPasswordEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(forgotPasswordError = "Please enter a valid email") }
            return
        }
        _uiState.update { it.copy(forgotPasswordSending = true, forgotPasswordError = null) }
        viewModelScope.launch {
            if (!networkObserver.isOnline.first()) {
                _uiState.update {
                    it.copy(
                        forgotPasswordSending = false,
                        forgotPasswordError = "No internet connection",
                    )
                }
                return@launch
            }
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update {
                    it.copy(
                        forgotPasswordSending = false,
                        forgotPasswordOpen = false,
                        infoMessage = "Reset link sent to $email. Check your email to continue.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        forgotPasswordSending = false,
                        forgotPasswordError = mapAuthError(e),
                    )
                }
            }
        }
    }

    // set a new password, after clicking the reset link

    fun submitNewPassword(newPassword: String) {
        if (newPassword.length < 6) {
            _uiState.update {
                it.copy(newPasswordError = "Password must be at least 6 characters")
            }
            return
        }
        _uiState.update { it.copy(newPasswordSaving = true, newPasswordError = null) }
        viewModelScope.launch {
            if (!networkObserver.isOnline.first()) {
                _uiState.update {
                    it.copy(
                        newPasswordSaving = false,
                        newPasswordError = "No internet connection",
                    )
                }
                return@launch
            }
            try {
                authRepository.updatePassword(newPassword)
                passwordRecoveryState.clear()
                _uiState.update {
                    it.copy(
                        newPasswordSaving = false,
                        newPasswordOpen = false,
                        isSuccess = true,
                        infoMessage = null,
                    )
                }
                syncCoordinator.syncAll()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        newPasswordSaving = false,
                        newPasswordError = mapAuthError(e),
                    )
                }
            }
        }
    }

    // called when the Auth screen comes back to the foreground. catches the case where the user
    // tapped Google, landed in a Chrome Custom Tab, and hit Back without finishing: no deep link
    // fires on that path, so isLoading would otherwise spin forever. the grace period covers slow
    // callbacks that arrive just after ON_RESUME
    fun onScreenResumed() {
        if (!awaitingExternalAuth) return
        viewModelScope.launch {
            delay(800)
            if (awaitingExternalAuth && authRepository.getCurrentUserId() == null) {
                awaitingExternalAuth = false
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signInWithGoogle() {
        _uiState.update { it.copy(isLoading = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            if (!networkObserver.isOnline.first()) {
                _uiState.update {
                    it.copy(isLoading = false, error = "No internet connection")
                }
                return@launch
            }
            awaitingExternalAuth = true
            try {
                // launches the browser or CustomTab and returns immediately. the session only flips to
                // Authenticated when the deeplink callback fires, handled by the sessionStatus collector in init
                authRepository.signInWithGoogle()
            } catch (e: Exception) {
                awaitingExternalAuth = false
                _uiState.update {
                    it.copy(isLoading = false, error = mapAuthError(e))
                }
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }
        if (!state.isLoginMode && (state.firstName.isBlank() || state.lastName.isBlank())) {
            _uiState.update { it.copy(error = "Please enter your name") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, infoMessage = null) }

        viewModelScope.launch {
            // fail fast when offline, otherwise Ktor sits through the full 30s timeout
            if (!networkObserver.isOnline.first()) {
                _uiState.update {
                    it.copy(isLoading = false, error = "No internet connection")
                }
                return@launch
            }

            try {
                if (state.isLoginMode) {
                    authRepository.signIn(state.email.trim(), state.password)
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                    syncCoordinator.syncAll()
                } else {
                    authRepository.signUp(
                        email = state.email.trim(),
                        password = state.password,
                        firstName = state.firstName.trim(),
                        lastName = state.lastName.trim(),
                        phone = state.phone.trim()
                    )
                    // Supabase requires email confirmation by default, so signUp returns without a session and
                    // navigating to Home would land the user on an unauth screen. keep them here and say so
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginMode = true,
                            password = "",
                            infoMessage = "Account created. Check your email to confirm, then sign in."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = mapAuthError(e))
                }
            }
        }
    }

    private fun mapAuthError(e: Throwable): String = authErrorMessage(e)
}
