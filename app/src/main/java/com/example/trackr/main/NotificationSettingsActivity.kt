package com.example.trackr.main

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.trackr.R

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var alarmNotifSwitch: SwitchCompat
    private lateinit var deadlineNotifSwitch: SwitchCompat
    private lateinit var timerNotifSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        // Hide default action bar since we're using custom toolbar
        supportActionBar?.hide()

        initViews()
        loadPreferences()
        setupListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        alarmNotifSwitch = findViewById(R.id.alarmNotifSwitch)
        deadlineNotifSwitch = findViewById(R.id.deadlineNotifSwitch)
        timerNotifSwitch = findViewById(R.id.timerNotifSwitch)

        backButton.setOnClickListener { finish() }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        alarmNotifSwitch.isChecked = prefs.getBoolean("alarm_notif", true)
        deadlineNotifSwitch.isChecked = prefs.getBoolean("deadline_notif", true)
        timerNotifSwitch.isChecked = prefs.getBoolean("timer_notif", true)
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)

        alarmNotifSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alarm_notif", isChecked).apply()
        }

        deadlineNotifSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("deadline_notif", isChecked).apply()
        }

        timerNotifSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("timer_notif", isChecked).apply()
        }
    }
}