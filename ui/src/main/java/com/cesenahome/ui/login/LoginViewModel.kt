package com.cesenahome.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cesenahome.domain.models.LoginResult
import com.cesenahome.domain.models.LoginScreenState
import com.cesenahome.domain.models.User
import com.cesenahome.domain.usecases.GetCurrentUserUseCase
import com.cesenahome.domain.usecases.LoginUseCase
import com.cesenahome.domain.usecases.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase
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