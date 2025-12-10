package com.example.trackr.main

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.trackr.R
import com.example.trackr.utils.BottomNavHelper
import com.example.trackr.utils.FirebaseHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var searchButton: ImageButton
    private var currentFragmentTag: String = "deadline"

    // Pending navigation data
    private var pendingScrollData: PendingScrollData? = null

    private data class PendingScrollData(
        val fragmentType: String,
        val itemId: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        bottomNav = findViewById(R.id.bottomNav)
        searchButton = findViewById(R.id.searchButton)

        // Extract target fragment BEFORE setting up navigation to prevent flicker
        val targetFragment = intent.getStringExtra("FRAGMENT") ?: "deadline"
        currentFragmentTag = targetFragment

        searchButton.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // Setup bottom navigation FIRST and set correct item immediately
        setupBottomNavigation()
        bottomNav.menu.findItem(getMenuItemId(targetFragment))?.isChecked = true

        // Load initial fragment only if not restoring state
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        } else {
            currentFragmentTag = savedInstanceState.getString("currentFragment", "deadline")
            // Restore bottom nav selection
            bottomNav.menu.findItem(getMenuItemId(currentFragmentTag))?.isChecked = true
        }

        showWelcomePopup()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Set bottom nav selection IMMEDIATELY before handling intent
        val targetFragment = intent.getStringExtra("FRAGMENT") ?: "deadline"
        bottomNav.menu.findItem(getMenuItemId(targetFragment))?.isChecked = true

        handleIncomingIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        // Clear bottom nav selection when leaving to prevent flash
        bottomNav.menu.setGroupCheckable(0, false, true)
    }

    override fun onResume() {
        super.onResume()
        // Re-enable selection and set correct item
        bottomNav.menu.setGroupCheckable(0, true, true)
        bottomNav.menu.findItem(getMenuItemId(currentFragmentTag))?.isChecked = true
    }

    private fun handleIncomingIntent(intent: Intent) {
        // Extract navigation intent data
        val targetFragment = intent.getStringExtra("FRAGMENT") ?: "deadline"
        val itemId = when (targetFragment) {
            "alarm" -> intent.getStringExtra("alarm_id")
            "deadline" -> intent.getStringExtra("activity_id")
            "timer" -> intent.getStringExtra("timer_id")
            else -> null
        }

        // Store pending scroll if needed
        if (itemId != null) {
            pendingScrollData = PendingScrollData(targetFragment, itemId)
        }

        // Always update currentFragmentTag and load fragment
        currentFragmentTag = targetFragment
        // Bottom nav selection already set in onCreate/onNewIntent, just load fragment
        loadFragmentDirectly(targetFragment)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentFragment", currentFragmentTag)
    }

    private fun setupBottomNavigation() {
        // Prevent reselection of same item
        bottomNav.setOnItemReselectedListener { /* Do nothing */ }

        BottomNavHelper.setupForMainActivity(this, bottomNav) { fragmentName ->
            // Only navigate if it's a different fragment
            if (currentFragmentTag != fragmentName) {
                currentFragmentTag = fragmentName
                loadFragmentDirectly(fragmentName)
            }
        }
    }

    private fun loadFragmentDirectly(fragmentName: String) {
        val fragment = createFragment(fragmentName)

        // Use commitNow() to execute immediately and prevent any race conditions
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, fragmentName)
            .commitNow()

        // Handle pending scroll after fragment is committed
        pendingScrollData?.let { data ->
            if (data.fragmentType == fragmentName) {
                scrollToItemWhenReady(data.fragmentType, data.itemId)
                pendingScrollData = null
            }
        }
    }

    private fun scrollToItemWhenReady(fragmentType: String, itemId: String) {
        // Post to ensure RecyclerView layout is complete
        bottomNav.post {
            val fragment = supportFragmentManager.findFragmentByTag(fragmentType)

            when (fragmentType) {
                "alarm" -> {
                    (fragment as? AlarmFragment)?.scrollToAndHighlightAlarm(itemId)
                }
                "deadline" -> {
                    (fragment as? DeadlineFragment)?.navigateToActivity(itemId)
                }
                "timer" -> {
                    (fragment as? TimerFragment)?.scrollToAndHighlightTimer(itemId)
                }
            }
        }
    }

    private fun createFragment(fragmentName: String): Fragment {
        return when (fragmentName) {
            "deadline" -> DeadlineFragment()
            "alarm" -> AlarmFragment()
            "timer" -> TimerFragment()
            else -> DeadlineFragment()
        }
    }

    private fun getMenuItemId(fragmentName: String): Int {
        return when (fragmentName) {
            "deadline" -> R.id.nav_deadline
            "alarm" -> R.id.nav_alarm
            "timer" -> R.id.nav_timer
            else -> R.id.nav_deadline
        }
    }

    private fun showWelcomePopup() {
        val userId = FirebaseHelper.getCurrentUserId() ?: return

        lifecycleScope.launch {
            val result = FirebaseHelper.getUserData(userId)

            result.onSuccess { user ->
                val message = """
                    Hello, ${user.fullName}!
                    
                    Track'r supports:
                    
                    SDG 3: Good Health and Well-being
                    Better time management leads to reduced stress and improved mental health.
                    
                    SDG 8: Decent Work and Economic Growth
                    Enhanced productivity and work-life balance for sustainable growth.
                """.trimIndent()

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Welcome!")
                    .setMessage(message)
                    .setPositiveButton("Continue") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(true)
                    .show()
            }
        }
    }
}