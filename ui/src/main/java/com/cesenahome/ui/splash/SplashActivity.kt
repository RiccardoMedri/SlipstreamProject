package com.cesenahome.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ActivitySplashBinding
import com.cesenahome.ui.login.LoginActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        goToLogin()
    }

    private fun goToLogin () {
        Handler().postDelayed({
            intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }, 3000)
    }
}