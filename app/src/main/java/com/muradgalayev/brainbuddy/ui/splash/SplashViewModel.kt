package com.muradgalayev.brainbuddy.ui.splash

import androidx.lifecycle.ViewModel
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    fun isLoggedIn(): Boolean {
        return authRepository.getCurrentUserId() != null
    }
}
