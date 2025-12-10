package com.example.trackr.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trackr.R
import com.example.trackr.adapters.SearchResultAdapter
import com.example.trackr.models.Alarm
import com.example.trackr.models.Activity
import com.example.trackr.models.TimerSet
import com.example.trackr.utils.FirebaseHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var searchView: SearchView
    private lateinit var searchCard: MaterialCardView
    private lateinit var backButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchResultAdapter
    private lateinit var chipGroup: ChipGroup
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var resultsCount: TextView

    private val allAlarms = mutableListOf<Alarm>()
    private val allActivities = mutableListOf<Activity>()
    private val allTimers = mutableListOf<TimerSet>()
    private val searchResults = mutableListOf<Any>()

    private var currentFilter = "All" // All, Alarms, Activities, Timers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Hide default action bar since we have custom back button
        supportActionBar?.hide()

        initViews()
        loadData()

        // Show empty view by default
        updateEmptyView(true, "Start typing to search...")
    }

    private fun initViews() {
        searchView = findViewById(R.id.searchView)
        searchCard = findViewById(R.id.searchCard)
        backButton = findViewById(R.id.backButton)
        recyclerView = findViewById(R.id.searchRecyclerView)
        chipGroup = findViewById(R.id.filterChipGroup)
        emptyView = findViewById(R.id.emptyView)
        emptyText = findViewById(R.id.emptyText)
        resultsCount = findViewById(R.id.resultsCount)

        adapter = SearchResultAdapter(searchResults, ::navigateToItem)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupBackButton()
        setupSearchView()
        setupFilterChips()
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            finish() // Go back to previous activity
        }
    }

    private fun setupSearchView() {
        searchView.queryHint = "Search alarms, activities, timers..."

        // Make the entire card clickable to focus on SearchView
        searchCard.setOnClickListener {
            searchView.isIconified = false
            searchView.requestFocus()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                performSearch(newText)
                return true
            }
        })

        // Configure SearchView to keep search icon and only show X when text exists
        searchView.isIconified = false
        searchView.requestFocus()

        // Find and configure the close button to only clear text
        val closeButton = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeButton?.setOnClickListener {
            searchView.setQuery("", false)
            searchView.clearFocus()
        }
    }

    private fun setupFilterChips() {
        val filters = listOf("All", "Alarms", "Activities", "Timers")

        filters.forEach { filter ->
            val chip = Chip(this)
            chip.text = filter
            chip.isCheckable = true
            chip.isChecked = filter == "All"

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currentFilter = filter
                    // Uncheck other chips
                    for (i in 0 until chipGroup.childCount) {
                        val otherChip = chipGroup.getChildAt(i) as Chip
                        if (otherChip != chip) {
                            otherChip.isChecked = false
                        }
                    }
                    performSearch(searchView.query.toString())
                }
            }

            chipGroup.addView(chip)
        }
    }

    private fun loadData() {
        val userId = FirebaseHelper.getCurrentUserId() ?: return

        lifecycleScope.launch {
            android.util.Log.d("SearchActivity", "=== LOADING DATA ===")

            // Load alarms
            FirebaseHelper.getAlarms(userId).onSuccess { alarms ->
                allAlarms.clear()
                allAlarms.addAll(alarms)
                android.util.Log.d("SearchActivity", "✓ Loaded ${alarms.size} alarms")
            }

            // Load active activities
            FirebaseHelper.getActivities(userId).onSuccess { activities ->
                allActivities.clear()
                allActivities.addAll(activities)
                android.util.Log.d("SearchActivity", "✓ Loaded ${activities.size} active activities")
            }

            // Load finished activities
            FirebaseHelper.getFinishedActivities(userId).onSuccess { finished ->
                allActivities.addAll(finished)
                android.util.Log.d("SearchActivity", "✓ Loaded ${finished.size} finished activities")
                android.util.Log.d("SearchActivity", "✓ TOTAL: ${allActivities.size} activities")

                // Log each finished activity
                finished.forEach { activity ->
                    android.util.Log.d("SearchActivity", "  - ${activity.title} (${activity.id})")
                }
            }.onFailure { e ->
                android.util.Log.e("SearchActivity", "✗ Failed to load finished: ${e.message}")
            }

            // Load timers
            FirebaseHelper.getTimerSets(userId).onSuccess { timers ->
                allTimers.clear()
                allTimers.addAll(timers)
                android.util.Log.d("SearchActivity", "✓ Loaded ${timers.size} timers")
            }
        }
    }

    private fun performSearch(query: String?) {
        searchResults.clear()

        if (query.isNullOrBlank()) {
            updateEmptyView(true, "Start typing to search...")
            adapter.notifyDataSetChanged()
            return
        }

        val lowerQuery = query.lowercase()

        // Search based on filter
        when (currentFilter) {
            "All" -> {
                searchAlarms(lowerQuery)
                searchActivities(lowerQuery)
                searchTimers(lowerQuery)
            }
            "Alarms" -> searchAlarms(lowerQuery)
            "Activities" -> searchActivities(lowerQuery)
            "Timers" -> searchTimers(lowerQuery)
        }

        // Sort results by relevance (exact matches first)
        searchResults.sortWith(compareBy<Any> { item ->
            when (item) {
                is Alarm -> !item.label.lowercase().startsWith(lowerQuery)
                is Activity -> !item.title.lowercase().startsWith(lowerQuery)
                is TimerSet -> !item.label.lowercase().startsWith(lowerQuery)
                else -> true
            }
        })

        updateEmptyView(searchResults.isEmpty(), "No results found for \"$query\"")
        updateResultsCount()
        adapter.notifyDataSetChanged()
    }

    private fun searchAlarms(query: String) {
        allAlarms.filter { alarm ->
            alarm.label.lowercase().contains(query) ||
                    alarm.getDisplayTime().lowercase().contains(query)
        }.forEach { searchResults.add(it) }
    }

    private fun searchActivities(query: String) {
        android.util.Log.d("SearchActivity", "--- Searching Activities ---")
        android.util.Log.d("SearchActivity", "Query: '$query', Available: ${allActivities.size}")

        // Filter and add matches
        allActivities.filter { activity ->
            activity.title.lowercase().contains(query) ||
                    activity.description.lowercase().contains(query) ||
                    activity.category.lowercase().contains(query)
        }.forEach { activity ->
            android.util.Log.d("SearchActivity", "  ✓ Match: ${activity.title} (finished: ${activity.isCompleted})")
            searchResults.add(activity)
        }

        android.util.Log.d("SearchActivity", "Total matches: ${searchResults.count { it is Activity }}")
    }

    private fun searchTimers(query: String) {
        allTimers.filter { timer ->
            timer.label.lowercase().contains(query) ||
                    timer.getDisplayDuration().lowercase().contains(query)
        }.forEach { searchResults.add(it) }
    }

    private fun updateEmptyView(show: Boolean, message: String) {
        if (show) {
            emptyView.visibility = View.VISIBLE
            emptyText.text = message
            recyclerView.visibility = View.GONE
            resultsCount.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            resultsCount.visibility = View.VISIBLE
        }
    }

    private fun updateResultsCount() {
        val count = searchResults.size
        resultsCount.text = when (count) {
            0 -> "No results"
            1 -> "1 result"
            else -> "$count results"
        }
    }

    private fun navigateToItem(item: Any) {
        when (item) {
            is Alarm -> {
                // Navigate to Alarms tab
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("FRAGMENT", "alarm")
                    putExtra("alarm_id", item.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
            is Activity -> {
                // Navigate to Activities tab
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("FRAGMENT", "deadline")
                    putExtra("activity_id", item.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
            is TimerSet -> {
                // Navigate to Timer tab
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("FRAGMENT", "timer")
                    putExtra("timer_id", item.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
        }
    }
}