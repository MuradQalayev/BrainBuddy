package com.muradgalayev.brainbuddy.ui.splash

import androidx.lifecycle.ViewModel
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
) : ViewModel() {

    fun isLoggedIn(): Boolean {
        return authRepository.getCurrentUserId() != null
    }

    /** Network call — splash should call this off the main thread. */
    suspend fun isSurveyCompleted(): Boolean {
        return adhdProfileRepository.isSurveyCompleted()
    }
}
