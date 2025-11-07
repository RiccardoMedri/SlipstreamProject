package com.cesenahome.ui.splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.login.LoginResult
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ActivitySplashBinding
import com.cesenahome.ui.homepage.HomepageActivity
import com.cesenahome.ui.login.LoginActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val restoreSessionUseCase = UseCaseProvider.restoreSessionUseCase
    private val splashDurationMs = 3000L

    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                handleNotificationPermissionDenied()
            }
            attemptSessionRestore()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        if (shouldRequestNotificationPermission()) {
            maybeShowNotificationPermissionRationale()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            attemptSessionRestore()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun maybeShowNotificationPermissionRationale() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            Snackbar.make(
                binding.root,
                R.string.notification_permission_rationale,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun handleNotificationPermissionDenied() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            Snackbar.make(
                binding.root,
                R.string.notification_permission_denied,
                Snackbar.LENGTH_LONG
            ).setAction(R.string.notification_permission_settings_action) {
                openNotificationSettings()
            }.show()
        } else {
            Snackbar.make(
                binding.root,
                R.string.notification_permission_rationale,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
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