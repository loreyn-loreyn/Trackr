package com.example.trackr.main

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.trackr.R
import com.example.trackr.auth.LoginActivity
import com.example.trackr.utils.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogoutActivity : AppCompatActivity() {

    private lateinit var logoutProgressBar: ProgressBar
    private lateinit var logoutText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logout)

        // Hide action bar
        supportActionBar?.hide()

        logoutProgressBar = findViewById(R.id.logoutProgressBar)
        logoutText = findViewById(R.id.logoutText)

        performLogout()
    }

    private fun performLogout() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(1500)  // Show loading for 1.5 seconds

            FirebaseHelper.logoutUser()

            withContext(Dispatchers.Main) {
                navigateToLogin()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}