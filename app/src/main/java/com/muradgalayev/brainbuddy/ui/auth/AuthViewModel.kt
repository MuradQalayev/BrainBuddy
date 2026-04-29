package com.muradgalayev.brainbuddy.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val todoRepository: com.muradgalayev.brainbuddy.data.repository.TodoRepository,
    private val preferencesRepository: com.muradgalayev.brainbuddy.data.repository.PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

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
        _uiState.update { it.copy(isLoginMode = !it.isLoginMode, error = null) }
    }

    fun signInWithGoogle() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                authRepository.signInWithGoogle()
                // Browser OAuth returns via the deep link; wait for the session to flip.
                authRepository.isLoggedIn.first { it }
                todoRepository.sync()
                preferencesRepository.pullRemoteAndApply()
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Google sign-in failed")
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

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                if (state.isLoginMode) {
                    authRepository.signIn(state.email.trim(), state.password)
                } else {
                    authRepository.signUp(
                        email = state.email.trim(),
                        password = state.password,
                        firstName = state.firstName.trim(),
                        lastName = state.lastName.trim(),
                        phone = state.phone.trim()
                    )
                }
                // After successful authentication, pull remote tasks and preferences for this user
                todoRepository.sync()
                preferencesRepository.pullRemoteAndApply()
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("Invalid login", ignoreCase = true) == true ->
                        "Invalid email or password"
                    e.message?.contains("already registered", ignoreCase = true) == true ->
                        "This email is already registered"
                    e.message?.contains("valid email", ignoreCase = true) == true ->
                        "Please enter a valid email address"
                    else -> e.message ?: "Something went wrong"
                }
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
        }
    }
}
