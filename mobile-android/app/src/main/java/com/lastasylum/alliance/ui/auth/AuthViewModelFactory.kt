package com.lastasylum.alliance.ui.auth

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lastasylum.alliance.data.auth.AuthRepository

class AuthViewModelFactory(
    private val application: Application,
    private val authRepository: AuthRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(application, authRepository) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}
