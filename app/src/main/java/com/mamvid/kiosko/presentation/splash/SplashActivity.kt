package com.mamvid.kiosko.presentation.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mamvid.kiosko.core.data.preferences.AppPreferences
import com.mamvid.kiosko.core.utils.Logger
import com.mamvid.kiosko.databinding.ActivitySplashBinding
import com.mamvid.kiosko.presentation.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val tag = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Logger.i(tag, "Splash started")
        initialize()
    }

    private fun initialize() {
        lifecycleScope.launch {
            try {
                val settings = AppPreferences(this@SplashActivity).settingsFlow.first()
                Logger.i(tag, "Settings loaded — URL: ${settings.url}")
                delay(1800)
            } catch (e: Exception) {
                Logger.e(tag, "Splash init error", e)
                delay(1200)
            } finally {
                navigateToMain()
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
