package com.cesenahome.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cesenahome.domain.models.login.LoginResult
import com.cesenahome.domain.models.login.LoginScreenState
import com.cesenahome.domain.usecases.auth.GetCurrentUserUseCase
import com.cesenahome.domain.usecases.auth.LoginUseCase
import com.cesenahome.domain.usecases.auth.LogoutUseCase
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
        viewModelScope.launch {
            getCurrentUserUseCase().collect { currentUser ->
                _loginScreenState.update {
                    it.copy(
                        user = currentUser,
                        isLoading = false,
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
            _loginScreenState.update {
                it.copy(serverUrl = "", username = "", password = "", error = null)
            }
        }
    }
}