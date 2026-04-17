package com.lastasylum.alliance.ui.admin

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lastasylum.alliance.data.users.UsersRepository

class AdminViewModelFactory(
    private val application: Application,
    private val usersRepository: UsersRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
            return AdminViewModel(application, usersRepository) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}
