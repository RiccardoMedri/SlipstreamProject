package com.cesenahome.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cesenahome.domain.models.LoginResult
import com.cesenahome.domain.models.User
import com.cesenahome.domain.usecases.GetCurrentUserUseCase
import com.cesenahome.domain.usecases.LoginUseCase
import com.cesenahome.domain.usecases.LogoutUseCase
import com.cesenahome.domain.usecases.RestoreSessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginScreenState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val restoreSessionUseCase: RestoreSessionUseCase
) : ViewModel() {

    private val _loginScreenState = MutableStateFlow(LoginScreenState())
    val loginScreenState: StateFlow<LoginScreenState> = _loginScreenState.asStateFlow()

    init {
        // Observe current user status
        viewModelScope.launch {
            getCurrentUserUseCase().collect { currentUser ->
                _loginScreenState.update {
                    it.copy(
                        user = currentUser,
                        // If user becomes non-null, it implies a successful login/restore
                        isLoading = false,
                        // Clear error if user is now logged in, or if it was a generic non-auth error
                        error = if (currentUser != null) null else it.error
                    )
                }
            }
        }
        // Attempt to restore session when ViewModel is created
        attemptRestoreSession()
    }

    fun onServerUrlChanged(url: String) {
        _loginScreenState.update { it.copy(serverUrl = url, error = null) }
    }

    fun onUsernameChanged(username: String) {
        _loginScreenState.update { it.copy(username = username, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _loginScreenState.update { it.copy(password = password, error = null) }
    }

    fun login() {
        viewModelScope.launch {
            _loginScreenState.update { it.copy(isLoading = true, error = null) }
            when (val result = loginUseCase(
                serverUrl = _loginScreenState.value.serverUrl,
                username = _loginScreenState.value.username,
                password = _loginScreenState.value.password
            )) {
                is LoginResult.Success -> {
                    // reflect success immediately in UI
                    _loginScreenState.update {
                        it.copy(isLoading = false, user = result.user, error = null)
                    }
                }
                is LoginResult.Error -> {
                    _loginScreenState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    private fun attemptRestoreSession() {
        viewModelScope.launch {
            _loginScreenState.update { it.copy(isLoading = true, error = null) }
            when (val result = restoreSessionUseCase()) {
                is LoginResult.Success -> {
                    _loginScreenState.update {
                        it.copy(isLoading = false, user = result.user, error = null)
                    }
                }
                is LoginResult.Error -> {
                    _loginScreenState.update { it.copy(isLoading = false, error = null) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            // User flow will update state to null. Clear input fields.
            _loginScreenState.update {
                it.copy(serverUrl = "", username = "", password = "", error = null)
            }
        }
    }
}

class LoginViewModelFactory(
    private val loginUseCase: LoginUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val restoreSessionUseCase: RestoreSessionUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(
                loginUseCase,
                getCurrentUserUseCase,
                logoutUseCase,
                restoreSessionUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}