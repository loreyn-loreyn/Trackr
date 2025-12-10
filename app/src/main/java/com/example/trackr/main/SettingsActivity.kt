@file:Suppress("DEPRECATION")

package com.example.trackr.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.trackr.R
import com.example.trackr.auth.LoginActivity
import com.example.trackr.utils.BottomNavHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.hide()
        setupCustomToolbar()
        setupBottomNavigation()
        setupMenuItems()
    }

    private fun setupCustomToolbar() {
        findViewById<android.widget.ImageButton>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(0, 0) // No animation when going back
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        overridePendingTransition(0, 0) // No animation
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0) // No animation on back press
    }

    private fun setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottomNav)
        BottomNavHelper.setupForSettings(this, bottomNav)
    }

    private fun setupMenuItems() {
        findViewById<CardView>(R.id.accountCard).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }

        findViewById<CardView>(R.id.notificationCard).setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        findViewById<CardView>(R.id.helpCard).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        findViewById<CardView>(R.id.aboutCard).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        findViewById<CardView>(R.id.privacyCard).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }

        findViewById<CardView>(R.id.logoutCard).setOnClickListener {
            confirmLogout()
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                startActivity(Intent(this, LogoutActivity::class.java))
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}