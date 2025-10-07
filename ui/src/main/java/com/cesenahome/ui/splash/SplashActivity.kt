package com.cesenahome.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.login.LoginResult
import com.cesenahome.ui.databinding.ActivitySplashBinding
import com.cesenahome.ui.homepage.HomepageActivity
import com.cesenahome.ui.login.LoginActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val restoreSessionUseCase = UseCaseProvider.restoreSessionUseCase
    private val splashDurationMs = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        attemptSessionRestore()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun attemptSessionRestore() {
        val startTime = System.currentTimeMillis()
        lifecycleScope.launch {
            val result = restoreSessionUseCase()
            val elapsed = System.currentTimeMillis() - startTime
            val remainingDelay = splashDurationMs - elapsed
            if (remainingDelay > 0) {
                delay(remainingDelay)
            }

            when (result) {
                is LoginResult.Success -> navigateToHomepage()
                is LoginResult.Error -> navigateToLogin()
            }
        }
    }

    private fun navigateToHomepage() {
        startActivity(Intent(this, HomepageActivity::class.java))
        finish()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}