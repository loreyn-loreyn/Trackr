package com.example.trackr

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.trackr.auth.LoginActivity
import com.example.trackr.main.MainActivity
import com.example.trackr.utils.FirebaseHelper
import com.google.firebase.FirebaseApp

@SuppressLint("CustomSplashScreen")

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_splash)

        // Start coroutine to wait 2 seconds then check user
        lifecycleScope.launch {
            delay(2000)
            checkUserStatus()
        }
    }

    private fun checkUserStatus() {
        if (FirebaseHelper.isUserLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
