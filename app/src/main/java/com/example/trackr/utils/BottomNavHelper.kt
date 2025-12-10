package com.example.trackr.utils

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.trackr.R
import com.example.trackr.main.MainActivity
import com.example.trackr.main.SettingsActivity

object BottomNavHelper {

    /**
     * Setup bottom navigation specifically for MainActivity with fragment management
     */
    fun setupForMainActivity(
        activity: MainActivity,
        bottomNav: BottomNavigationView,
        onFragmentChange: (String) -> Unit
    ) {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_deadline -> {
                    onFragmentChange("deadline")
                    true
                }
                R.id.nav_alarm -> {
                    onFragmentChange("alarm")
                    true
                }
                R.id.nav_timer -> {
                    onFragmentChange("timer")
                    true
                }
                R.id.nav_menu -> {
                    val intent = Intent(activity, SettingsActivity::class.java)
                    activity.startActivity(intent)
                    activity.overridePendingTransition(0, 0) // No animation
                    false // Don't select menu item in MainActivity
                }
                else -> false
            }
        }
    }

    /**
     * Setup bottom navigation for SettingsActivity
     */
    fun setupForSettings(
        activity: SettingsActivity,
        bottomNav: BottomNavigationView
    ) {
        // Set menu as selected item
        bottomNav.selectedItemId = R.id.nav_menu

        // Disable reselection on menu item
        bottomNav.setOnItemReselectedListener { /* Do nothing */ }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_deadline -> {
                    navigateToMainActivity(activity, "deadline")
                    false // Don't change selection in SettingsActivity
                }
                R.id.nav_alarm -> {
                    navigateToMainActivity(activity, "alarm")
                    false // Don't change selection in SettingsActivity
                }
                R.id.nav_timer -> {
                    navigateToMainActivity(activity, "timer")
                    false // Don't change selection in SettingsActivity
                }
                R.id.nav_menu -> {
                    // Already in settings, do nothing
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Navigate from SettingsActivity back to MainActivity with specific fragment
     */
    private fun navigateToMainActivity(activity: Activity, fragment: String) {
        val intent = Intent(activity, MainActivity::class.java)
        intent.putExtra("FRAGMENT", fragment)
        // Use CLEAR_TOP to reuse existing MainActivity instead of recreating it
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        // Start MainActivity first
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0) // No animation

        // Finish SettingsActivity immediately after
        activity.finish()
        activity.overridePendingTransition(0, 0) // No animation
    }
}