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
import androidx.lifecycle.lifecycleScope
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.login.LoginResult
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ActivitySplashBinding
import com.cesenahome.ui.homepage.HomepageActivity
import com.cesenahome.ui.login.LoginActivity
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val restoreSessionUseCase = UseCaseProvider.restoreSessionUseCase
    private val splashDurationMs = 3000L

    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                attemptSessionRestore()
            } else {
                handleNotificationPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (shouldRequestNotificationPermission()) {
            // Two choices only: allow (system prompt) or deny (system prompt)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Already granted or not needed on this Android version
            attemptSessionRestore()
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    private fun handleNotificationPermissionDenied() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Not applicable below 33; just proceed
            attemptSessionRestore()
            return
        }

        val needsSettings = !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        val snackbar = if (needsSettings) {
            Snackbar.make(binding.root, R.string.notification_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.notification_permission_settings_action) {
                    openNotificationSettings()
                }
        } else {
            Snackbar.make(binding.root, R.string.notification_permission_rationale, Snackbar.LENGTH_SHORT)
        }

        // App can't function without this permission per requirements: close after informing user
        snackbar.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                finishAffinity()
            }
        })
        snackbar.show()
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
    }

    private fun attemptSessionRestore() {
        val startTime = System.currentTimeMillis()
        lifecycleScope.launch {
            val result = restoreSessionUseCase()
            val elapsed = System.currentTimeMillis() - startTime
            val remainingDelay = splashDurationMs - elapsed
            if (remainingDelay > 0) delay(remainingDelay)

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
