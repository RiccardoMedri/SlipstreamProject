package com.cesenahome.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.databinding.ActivityLoginBinding
import com.cesenahome.ui.homepage.HomepageActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by lazy {
        LoginViewModel(
            loginUseCase = UseCaseProvider.loginUseCase,
            getCurrentUserUseCase = UseCaseProvider.getCurrentUserUseCase,
            logoutUseCase = UseCaseProvider.logoutUseCase,
            restoreSessionUseCase = UseCaseProvider.restoreSessionUseCase
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.etServerUrl.doOnTextChanged { text, _, _, _ ->
            viewModel.onServerUrlChanged(text.toString())
        }
        binding.etUsername.doOnTextChanged { text, _, _, _ ->
            viewModel.onUsernameChanged(text.toString())
        }
        binding.etPassword.doOnTextChanged { text, _, _, _ ->
            viewModel.onPasswordChanged(text.toString())
        }

        binding.btnLogin.setOnClickListener {
            viewModel.login()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginScreenState.collect { state ->
                    // Update input fields only if they differ (to avoid cursor jumps)
                    if (binding.etServerUrl.text.toString() != state.serverUrl) {
                        binding.etServerUrl.setText(state.serverUrl)
                    }
                    if (binding.etUsername.text.toString() != state.username) {
                        binding.etUsername.setText(state.username)
                    }
                    if (binding.etPassword.text.toString() != state.password) {
                        binding.etPassword.setText(state.password)
                    }

                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnLogin.isEnabled = !state.isLoading

                    if (state.user != null) {
                        binding.tvStatus.text = "WELCOME ${state.user.name ?: "User"}!"
                        // TODO: Navigate to the main part of your app
                        Handler(Looper.getMainLooper()).postDelayed({
                            val intent = Intent(this@LoginActivity, HomepageActivity::class.java)
                            startActivity(intent)
                            finish()
                        }, 300)
                    } else if (state.error != null) {
                        binding.tvStatus.text = state.error
                        binding.tvStatus.visibility = View.VISIBLE
                    } else if (!state.isLoading) {
                        binding.tvStatus.text = "Please log in."
                        binding.tvStatus.visibility = View.VISIBLE
                    } else {
                        binding.tvStatus.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }
}
