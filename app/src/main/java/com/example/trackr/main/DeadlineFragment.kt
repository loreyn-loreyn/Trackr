package com.example.trackr.main

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.snackbar.Snackbar
import com.example.trackr.R
import com.example.trackr.adapters.ActivityAdapter
import com.example.trackr.models.Activity
import com.example.trackr.utils.FirebaseHelper
import com.example.trackr.utils.EventDecorator
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class DeadlineFragment : Fragment() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var monthYearText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var addFab: FloatingActionButton
    private lateinit var filterButton: ImageButton
    private lateinit var sortButton: ImageButton
    private lateinit var adapter: ActivityAdapter
    private lateinit var rootView: View

    private val activeActivities = mutableListOf<Activity>()
    private val finishedActivities = mutableListOf<Activity>()
    private val displayedActivities = mutableListOf<Activity>()
    private var selectedCalendarDate: CalendarDay? = null
    private var currentFilter = "All"
    private var currentSort = "Date (Ascending)"

    private var pendingActivityId: String? = null
    private val activityDates = mutableMapOf<CalendarDay, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_deadline, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarView = view.findViewById(R.id.calendarView)
        monthYearText = view.findViewById(R.id.monthYearText)
        recyclerView = view.findViewById(R.id.deadlineRecyclerView)
        addFab = view.findViewById(R.id.addDeadlineFab)
        filterButton = view.findViewById(R.id.filterButton)
        sortButton = view.findViewById(R.id.sortButton)

        setupCalendar()
        setupRecyclerView()
        loadActivities()

        addFab.setOnClickListener {
            showAddActivityDialog()
        }

        filterButton.setOnClickListener {
            showFilterDialog()
        }

        sortButton.setOnClickListener {
            showSortDialog()
        }
    }

    private fun setupCalendar() {
        updateMonthYearText()

        val today = CalendarDay.today()
        calendarView.addDecorator(TodayDecorator(today))

        calendarView.setOnDateChangedListener { widget, date, selected ->
            if (selectedCalendarDate == date) {
                selectedCalendarDate = null
                calendarView.clearSelection()
            } else {
                selectedCalendarDate = date
                currentFilter = "All"
            }
            filterAndDisplayActivities()
        }

        calendarView.setOnMonthChangedListener { widget, date ->
            updateMonthYearText()
        }
    }

    private fun updateMonthYearText() {
        val months = arrayOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        val currentMonth = calendarView.currentDate?.month ?: Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = calendarView.currentDate?.year ?: Calendar.getInstance().get(Calendar.YEAR)
        monthYearText.text = "${months[currentMonth - 1]} $currentYear"
    }

    private fun setupRecyclerView() {
        adapter = ActivityAdapter(
            activities = displayedActivities,
            onToggleComplete = { activity, isCompleted -> toggleComplete(activity, isCompleted) },
            onDelete = { activity -> deleteActivity(activity) },
            onLongPress = { activity -> showEditDeleteDialog(activity) },
            onClick = { activity -> showActivityDetails(activity) }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadActivities() {
        val userId = FirebaseHelper.getCurrentUserId() ?: return

        lifecycleScope.launch {
            // Load active activities
            val activeResult = FirebaseHelper.getActivities(userId, "All")
            activeResult.onSuccess { list ->
                activeActivities.clear()
                activeActivities.addAll(list.filter { !it.isCompleted })
                android.util.Log.d("DeadlineFragment", "Loaded ${activeActivities.size} active activities")
            }.onFailure { e ->
                android.util.Log.e("DeadlineFragment", "Failed to load active activities: ${e.message}")
            }

            // Load finished activities
            val finishedResult = FirebaseHelper.getFinishedActivities(userId)
            finishedResult.onSuccess { list ->
                finishedActivities.clear()
                finishedActivities.addAll(list)
                android.util.Log.d("DeadlineFragment", "Loaded ${finishedActivities.size} finished activities")
                finishedActivities.forEach { activity ->
                    android.util.Log.d("DeadlineFragment", "Finished: ${activity.title}, completed: ${activity.isCompleted}")
                }
            }.onFailure { e ->
                android.util.Log.e("DeadlineFragment", "Failed to load finished activities: ${e.message}")
            }

            updateCalendarBubbles(activeActivities)
            filterAndDisplayActivities()

            // Handle pending navigation after data is loaded
            pendingActivityId?.let { activityId ->
                android.util.Log.d("DeadlineFragment", "Processing pending navigation to: $activityId")
                recyclerView.postDelayed({
                    scrollToAndHighlightActivity(activityId)
                    pendingActivityId = null
                }, 300)
            }
        }
    }

    private fun filterAndDisplayActivities() {
        displayedActivities.clear()
        val currentTime = System.currentTimeMillis()
        val threeDaysInMillis = 3 * 24 * 60 * 60 * 1000L

        android.util.Log.d("DeadlineFragment", "=== FILTERING ===")
        android.util.Log.d("DeadlineFragment", "Current filter: $currentFilter")
        android.util.Log.d("DeadlineFragment", "Active activities count: ${activeActivities.size}")
        android.util.Log.d("DeadlineFragment", "Finished activities count: ${finishedActivities.size}")

        // FIXED: Properly separate active and finished activities based on filter
        val sourceList = when (currentFilter) {
            "Finished" -> {
                android.util.Log.d("DeadlineFragment", "Using FINISHED list only")
                finishedActivities
            }
            "All" -> {
                android.util.Log.d("DeadlineFragment", "Using COMBINED list (active + finished)")
                val combined = mutableListOf<Activity>()
                combined.addAll(activeActivities)
                combined.addAll(finishedActivities)
                combined
            }
            else -> {
                android.util.Log.d("DeadlineFragment", "Using ACTIVE list only")
                activeActivities
            }
        }

        android.util.Log.d("DeadlineFragment", "Source list size: ${sourceList.size}")

        // Filter by selected calendar date if any
        var filteredList = if (selectedCalendarDate != null) {
            sourceList.filter { activity ->
                val cal = Calendar.getInstance().apply {
                    timeInMillis = activity.dateTimeInMillis
                }
                val activityDay = CalendarDay.from(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                activityDay == selectedCalendarDate
            }
        } else {
            sourceList
        }

        android.util.Log.d("DeadlineFragment", "After calendar filter: ${filteredList.size}")

        // Apply category filter (only on active activities, not finished)
        filteredList = when (currentFilter) {
            "Finished" -> filteredList // Don't apply additional filters to finished

            "Missed" -> filteredList.filter {
                !it.isCompleted && it.dateTimeInMillis < currentTime
            }

            "Nearest" -> filteredList.filter {
                !it.isCompleted &&
                        it.dateTimeInMillis >= currentTime &&
                        (it.dateTimeInMillis - currentTime) <= threeDaysInMillis
            }

            "Farthest" -> filteredList.filter {
                !it.isCompleted &&
                        it.dateTimeInMillis >= currentTime &&
                        (it.dateTimeInMillis - currentTime) > threeDaysInMillis
            }

            "All" -> filteredList

            else -> filteredList
        }

        android.util.Log.d("DeadlineFragment", "After category filter: ${filteredList.size}")

        // Apply sorting
        val sortedList = when (currentSort) {
            "Name" -> filteredList.sortedWith(
                compareBy<Activity> { it.isCompleted }
                    .thenBy { it.title.lowercase() }
            )

            "Date (Ascending)" -> filteredList.sortedWith(
                compareBy<Activity> { it.isCompleted }
                    .thenBy { it.dateTimeInMillis }
            )

            "Date (Descending)" -> filteredList.sortedWith(
                compareBy<Activity> { it.isCompleted }
                    .thenByDescending { it.dateTimeInMillis }
            )

            else -> filteredList
        }

        android.util.Log.d("DeadlineFragment", "Final sorted list: ${sortedList.size}")

        displayedActivities.addAll(sortedList)
        adapter.notifyDataSetChanged()

        android.util.Log.d("DeadlineFragment", "Displayed activities: ${displayedActivities.size}")
    }

    private fun updateCalendarBubbles(activityList: List<Activity>) {
        activityDates.clear()
        val currentTime = System.currentTimeMillis()

        activityList.filter { !it.isCompleted }.forEach { activity ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = activity.dateTimeInMillis
            }

            val calendarDay = CalendarDay.from(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )

            val timeRemaining = activity.dateTimeInMillis - currentTime
            val color = when {
                timeRemaining < 0 -> Color.parseColor("#F44336")
                timeRemaining < 24 * 60 * 60 * 1000 -> Color.parseColor("#FFC107")
                timeRemaining < 48 * 60 * 60 * 1000 -> Color.parseColor("#FFE082")
                else -> Color.parseColor("#4CAF50")
            }

            val existingColor = activityDates[calendarDay]
            if (existingColor == null || getPriority(color) > getPriority(existingColor)) {
                activityDates[calendarDay] = color
            }
        }

        applyCalendarDecorators()
    }

    private fun getPriority(color: Int): Int {
        return when (color) {
            Color.parseColor("#F44336") -> 4
            Color.parseColor("#FFC107") -> 3
            Color.parseColor("#FFE082") -> 2
            Color.parseColor("#4CAF50") -> 1
            else -> 0
        }
    }

    private fun applyCalendarDecorators() {
        calendarView.removeDecorators()

        val today = CalendarDay.today()
        calendarView.addDecorator(TodayDecorator(today))

        val datesByColor = mutableMapOf<Int, MutableList<CalendarDay>>()
        activityDates.forEach { (date, color) ->
            datesByColor.getOrPut(color) { mutableListOf() }.add(date)
        }

        datesByColor.forEach { (color, dates) ->
            calendarView.addDecorator(EventDecorator(color, dates))
        }

        calendarView.invalidateDecorators()
    }

    private fun showFilterDialog() {
        val filters = arrayOf("All", "Nearest", "Farthest", "Missed", "Finished")

        AlertDialog.Builder(requireContext())
            .setTitle("Filter Activities")
            .setSingleChoiceItems(filters, filters.indexOf(currentFilter)) { dialog, which ->
                currentFilter = filters[which]
                selectedCalendarDate = null
                calendarView.clearSelection()
                filterAndDisplayActivities()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSortDialog() {
        val sorts = arrayOf("Date (Ascending)", "Date (Descending)", "Name")

        AlertDialog.Builder(requireContext())
            .setTitle("Sort By")
            .setSingleChoiceItems(sorts, sorts.indexOf(currentSort)) { dialog, which ->
                currentSort = sorts[which]
                filterAndDisplayActivities()
                dialog.dismiss()
            }
            .show()
    }

    private fun showAddActivityDialog(existingActivity: Activity? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_deadline, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.titleInput)
        val descInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.categorySpinner)
        val dateButton = dialogView.findViewById<Button>(R.id.selectDateButton)

        val categories = arrayOf("Work", "Personal", "School", "Other")
        categorySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        // Pre-fill existing activity data
        var selectedDateTime = existingActivity?.dateTimeInMillis ?: Calendar.getInstance().timeInMillis

        existingActivity?.let { activity ->
            titleInput.setText(activity.title)
            descInput.setText(activity.description)

            // Set category spinner
            val categoryIndex = categories.indexOf(activity.category)
            if (categoryIndex != -1) {
                categorySpinner.setSelection(categoryIndex)
            }

            // Set date button text
            dateButton.text = android.text.format.DateFormat.format("MMM dd, yyyy hh:mm a", activity.dateTimeInMillis)
        }

        dateButton.setOnClickListener {
            showDateTimePicker { timeInMillis ->
                selectedDateTime = timeInMillis
                dateButton.text = android.text.format.DateFormat.format("MMM dd, yyyy hh:mm a", timeInMillis)
            }
        }

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setTitle(if (existingActivity != null) "Edit Activity" else "Add Activity")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                val category = categorySpinner.selectedItem.toString()

                if (title.isNotEmpty()) {
                    saveActivity(
                        existingActivity?.id ?: "",
                        title,
                        desc,
                        category,
                        selectedDateTime,
                        existingActivity?.isCompleted ?: false,
                        existingActivity?.completedAt ?: 0L
                    )
                } else {
                    Snackbar.make(rootView, "Please enter a title", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)

        dialogBuilder.show()
    }

    private fun showDateTimePicker(onSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            R.style.DarkerDatePickerTheme,
            { _, year, month, day ->
                TimePickerDialog(
                    requireContext(),
                    R.style.DarkerTimePickerTheme,
                    { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute, 0)
                        onSelected(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveActivity(
        activityId: String,
        title: String,
        desc: String,
        category: String,
        dateTime: Long,
        isCompleted: Boolean,
        completedAt: Long
    ) {
        val userId = FirebaseHelper.getCurrentUserId() ?: return

        val activity = Activity(
            id = activityId,
            userId = userId,
            title = title,
            description = desc,
            category = category,
            dateTimeInMillis = dateTime,
            isCompleted = isCompleted,
            completedAt = completedAt
        )

        lifecycleScope.launch {
            val result = FirebaseHelper.saveActivity(activity)

            result.onSuccess {
                val message = if (activityId.isEmpty()) "Activity created!" else "Activity updated!"
                Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
                loadActivities()
            }.onFailure { e ->
                Snackbar.make(rootView, "Failed to save: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleComplete(activity: Activity, isCompleted: Boolean) {
        lifecycleScope.launch {
            android.util.Log.d("DeadlineFragment", "=== TOGGLE COMPLETE ===")
            android.util.Log.d("DeadlineFragment", "Activity: ${activity.title}")
            android.util.Log.d("DeadlineFragment", "Activity ID: '${activity.id}'")
            android.util.Log.d("DeadlineFragment", "IsCompleted: $isCompleted")

            // Validate activity has an ID
            if (activity.id.isEmpty()) {
                android.util.Log.e("DeadlineFragment", "❌ Cannot toggle: Activity ID is empty!")
                Snackbar.make(rootView, "Error: Activity has no ID", Snackbar.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged() // Revert checkbox
                return@launch
            }

            val result = if (isCompleted) {
                android.util.Log.d("DeadlineFragment", "Marking as finished...")
                FirebaseHelper.markActivityAsFinished(activity)
            } else {
                android.util.Log.d("DeadlineFragment", "Unmarking as finished...")
                FirebaseHelper.unmarkActivityAsFinished(activity)
            }

            result.onSuccess {
                android.util.Log.d("DeadlineFragment", "✅ Toggle successful! Reloading...")
                loadActivities()
            }.onFailure { e ->
                android.util.Log.e("DeadlineFragment", "❌ Toggle failed: ${e.message}", e)
                Snackbar.make(rootView, "Failed to update activity: ${e.message}", Snackbar.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showEditDeleteDialog(activity: Activity) {
        val options = arrayOf("Edit Activity", "Delete Activity")

        AlertDialog.Builder(requireContext())
            .setTitle(activity.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddActivityDialog(activity) // Edit
                    1 -> showDeleteConfirmation(activity) // Delete
                }
            }
            .show()
    }

    private fun showActivityDetails(activity: Activity) {
        val detailsView = layoutInflater.inflate(R.layout.dialog_activity_details, null)

        val titleText = detailsView.findViewById<TextView>(R.id.detailTitle)
        val descriptionText = detailsView.findViewById<TextView>(R.id.detailDescription)
        val categoryText = detailsView.findViewById<TextView>(R.id.detailCategory)
        val dateTimeText = detailsView.findViewById<TextView>(R.id.detailDateTime)
        val statusText = detailsView.findViewById<TextView>(R.id.detailStatus)
        val timeRemainingText = detailsView.findViewById<TextView>(R.id.detailTimeRemaining)
        val completedAtText = detailsView.findViewById<TextView>(R.id.detailCompletedAt)
        val createdAtText = detailsView.findViewById<TextView>(R.id.detailCreatedAt)

        titleText.text = activity.title
        descriptionText.text = if (activity.description.isNotEmpty()) {
            activity.description
        } else {
            "No description"
        }
        descriptionText.alpha = if (activity.description.isNotEmpty()) 1.0f else 0.5f

        categoryText.text = activity.category
        dateTimeText.text = android.text.format.DateFormat.format("MMM dd, yyyy hh:mm a", activity.dateTimeInMillis)

        // Status
        val currentTime = System.currentTimeMillis()
        val timeRemaining = activity.dateTimeInMillis - currentTime

        if (activity.isCompleted) {
            statusText.text = "Completed ✓"
            statusText.setTextColor(Color.parseColor("#1B5E20")) // Dark green text
            statusText.setBackgroundResource(R.drawable.status_badge_completed) // Light green background

            if (activity.completedAt > 0) {
                completedAtText.visibility = View.VISIBLE
                completedAtText.text = "Completed: ${android.text.format.DateFormat.format("MMM dd, yyyy hh:mm a", activity.completedAt)}"
            } else {
                completedAtText.visibility = View.GONE
            }

            timeRemainingText.visibility = View.GONE
        } else {
            completedAtText.visibility = View.GONE
            timeRemainingText.visibility = View.VISIBLE

            when {
                timeRemaining < 0 -> {
                    statusText.text = "Overdue"
                    statusText.setTextColor(Color.parseColor("#C62828")) // Dark red text
                    statusText.setBackgroundResource(R.drawable.status_badge_overdue) // Light red background
                    timeRemainingText.text = "Overdue by ${formatTimeRemaining(Math.abs(timeRemaining))}"
                    timeRemainingText.setTextColor(Color.WHITE)
                }
                timeRemaining < 24 * 60 * 60 * 1000 -> {
                    statusText.text = "Due Soon"
                    statusText.setTextColor(Color.parseColor("#F57C00")) // Dark orange text
                    statusText.setBackgroundResource(R.drawable.status_badge_due_soon) // Light orange background
                    timeRemainingText.text = "Due in ${formatTimeRemaining(timeRemaining)}"
                    timeRemainingText.setTextColor(Color.WHITE)
                }
                else -> {
                    statusText.text = "Active"
                    statusText.setTextColor(Color.parseColor("#00695C")) // Dark teal text
                    statusText.setBackgroundResource(R.drawable.status_badge_active) // Light teal background
                    timeRemainingText.text = "Due in ${formatTimeRemaining(timeRemaining)}"
                    timeRemainingText.setTextColor(Color.WHITE)
                }
            }
        }

        createdAtText.text = "${android.text.format.DateFormat.format("MMM dd, yyyy hh:mm a", activity.createdAt)}"

        AlertDialog.Builder(requireContext())
            .setView(detailsView)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun formatTimeRemaining(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days day${if (days != 1L) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours != 1L) "s" else ""}"
            minutes > 0 -> "$minutes minute${if (minutes != 1L) "s" else ""}"
            else -> "$seconds second${if (seconds != 1L) "s" else ""}"
        }
    }

    private fun showDeleteConfirmation(activity: Activity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Activity")
            .setMessage("Are you sure you want to delete \"${activity.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteActivity(activity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteActivity(activity: Activity) {
        lifecycleScope.launch {
            android.util.Log.d("DeadlineFragment", "=== DELETE ACTIVITY ===")
            android.util.Log.d("DeadlineFragment", "Activity: ${activity.title}")
            android.util.Log.d("DeadlineFragment", "Activity ID: '${activity.id}'")
            android.util.Log.d("DeadlineFragment", "Is Completed: ${activity.isCompleted}")

            if (activity.id.isEmpty()) {
                android.util.Log.e("DeadlineFragment", "❌ Cannot delete: Activity ID is empty!")
                Snackbar.make(rootView, "Error: Activity has no ID", Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            val result = if (activity.isCompleted) {
                android.util.Log.d("DeadlineFragment", "Deleting from FINISHED collection")
                FirebaseHelper.deleteFinishedActivity(activity.id)
            } else {
                android.util.Log.d("DeadlineFragment", "Deleting from ACTIVE collection")
                FirebaseHelper.deleteActivity(activity.id)
            }

            result.onSuccess {
                android.util.Log.d("DeadlineFragment", "✅ Delete successful!")
                Snackbar.make(rootView, "Activity deleted", Snackbar.LENGTH_SHORT).show()
                loadActivities()
            }.onFailure { e ->
                android.util.Log.e("DeadlineFragment", "❌ Delete failed: ${e.message}", e)
                Snackbar.make(rootView, "Failed to delete: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    fun scrollToAndHighlightActivity(activityId: String) {
        android.util.Log.d("DeadlineFragment", "=== SCROLL TO ACTIVITY ===")
        android.util.Log.d("DeadlineFragment", "Looking for activity ID: $activityId")
        android.util.Log.d("DeadlineFragment", "Current filter: $currentFilter")
        android.util.Log.d("DeadlineFragment", "Displayed activities: ${displayedActivities.size}")
        android.util.Log.d("DeadlineFragment", "Active activities: ${activeActivities.size}")
        android.util.Log.d("DeadlineFragment", "Finished activities: ${finishedActivities.size}")

        val position = displayedActivities.indexOfFirst { it.id == activityId }

        if (position != -1) {
            android.util.Log.d("DeadlineFragment", "✅ Found at position $position")
            recyclerView.smoothScrollToPosition(position)

            recyclerView.postDelayed({
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.let { view ->
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction {
                            view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                }
            }, 300)
        } else {
            android.util.Log.d("DeadlineFragment", "❌ Not found in displayed list")

            val inActive = activeActivities.any { it.id == activityId }
            val inFinished = finishedActivities.any { it.id == activityId }

            android.util.Log.d("DeadlineFragment", "In active list: $inActive")
            android.util.Log.d("DeadlineFragment", "In finished list: $inFinished")

            if (inActive || inFinished) {
                val targetFilter = when {
                    inFinished -> "Finished"
                    inActive -> "All"
                    else -> "All"
                }

                android.util.Log.d("DeadlineFragment", "Setting filter to: $targetFilter")

                currentFilter = targetFilter
                selectedCalendarDate = null
                calendarView.clearSelection()
                filterAndDisplayActivities()

                recyclerView.postDelayed({
                    val newPosition = displayedActivities.indexOfFirst { it.id == activityId }
                    android.util.Log.d("DeadlineFragment", "After filter change, position: $newPosition")

                    if (newPosition != -1) {
                        recyclerView.smoothScrollToPosition(newPosition)
                        recyclerView.postDelayed({
                            val viewHolder = recyclerView.findViewHolderForAdapterPosition(newPosition)
                            viewHolder?.itemView?.let { view ->
                                view.animate()
                                    .scaleX(0.95f)
                                    .scaleY(0.95f)
                                    .setDuration(100)
                                    .withEndAction {
                                        view.animate()
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(100)
                                            .start()
                                    }
                                    .start()
                            }
                        }, 300)
                    } else {
                        android.util.Log.e("DeadlineFragment", "Still not found after filter change!")
                        Snackbar.make(rootView, "Activity not found", Snackbar.LENGTH_SHORT).show()
                    }
                }, 200)
            } else {
                android.util.Log.e("DeadlineFragment", "Activity not found in any list!")
                Snackbar.make(rootView, "Activity not found", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    fun navigateToActivity(activityId: String) {
        android.util.Log.d("DeadlineFragment", "=== NAVIGATE TO ACTIVITY REQUEST ===")
        android.util.Log.d("DeadlineFragment", "Activity ID: $activityId")

        if (activeActivities.isNotEmpty() || finishedActivities.isNotEmpty()) {
            android.util.Log.d("DeadlineFragment", "Data already loaded, scrolling immediately")
            recyclerView.postDelayed({
                scrollToAndHighlightActivity(activityId)
            }, 100)
        } else {
            android.util.Log.d("DeadlineFragment", "Data not loaded yet, storing as pending")
            pendingActivityId = activityId
        }
    }

    inner class TodayDecorator(private val today: CalendarDay) : DayViewDecorator {
        override fun shouldDecorate(day: CalendarDay): Boolean {
            return day == today
        }

        override fun decorate(view: DayViewFacade) {
            view.setSelectionDrawable(
                requireContext().getDrawable(R.drawable.today_selector)!!
            )
        }
    }
}