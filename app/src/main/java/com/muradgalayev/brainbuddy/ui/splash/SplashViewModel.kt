package com.muradgalayev.brainbuddy.ui.splash

import androidx.lifecycle.ViewModel
import com.muradgalayev.brainbuddy.data.auth.PasswordRecoveryState
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val passwordRecoveryState: PasswordRecoveryState,
) : ViewModel() {

    fun isLoggedIn(): Boolean {
        return authRepository.getCurrentUserId() != null
    }

    /**
     * True when the app was just launched via a password-reset deep link. In that case
     * the user must go through the "Set new password" flow on Auth, even though they
     * technically have an authenticated recovery session.
     */
    fun isInPasswordRecovery(): Boolean = passwordRecoveryState.active.value

    /** Local DataStore read. Splash never blocks on Supabase. */
    suspend fun isSurveyCompleted(): Boolean {
        return adhdProfileRepository.isSurveyCompletedCached()
    }
}
