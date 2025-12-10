package com.example.trackr.main

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.example.trackr.R
import com.example.trackr.adapters.TimerSetAdapter
import com.example.trackr.models.TimerSet
import com.example.trackr.utils.FirebaseHelper
import com.example.trackr.utils.TimeFormatter
import kotlinx.coroutines.launch
import java.util.*

class TimerFragment : Fragment() {

    private lateinit var normalTimerLabel: TextView
    private lateinit var normalTimerDisplay: TextView
    private lateinit var normalTimerStartButton: Button
    private lateinit var normalTimerPauseButton: Button
    private lateinit var normalTimerResetButton: Button
    private lateinit var sortButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var addFab: FloatingActionButton
    private lateinit var adapter: TimerSetAdapter

    private val timerSets = mutableListOf<TimerSet>()
    private val activeTimers = mutableMapOf<String, CountDownTimer>()

    // Normal timer
    private var normalTimerMillis: Long = 0
    private var normalTimerRemaining: Long = 0
    private var normalTimer: CountDownTimer? = null
    private var isNormalTimerRunning = false
    private var isNormalTimerPaused = false
    private var normalTimerLabelText: String = "Timer"
    private var normalTimerStartTimestamp: Long = 0L

    // SharedPreferences keys for normal timer persistence
    private val PREFS_NAME = "TimerPrefs"
    private val KEY_NORMAL_TIMER_LABEL = "normal_timer_label"
    private val KEY_NORMAL_TIMER_MILLIS = "normal_timer_millis"
    private val KEY_NORMAL_TIMER_REMAINING = "normal_timer_remaining"
    private val KEY_NORMAL_TIMER_IS_RUNNING = "normal_timer_is_running"
    private val KEY_NORMAL_TIMER_IS_PAUSED = "normal_timer_is_paused"
    private val KEY_NORMAL_TIMER_START_TIMESTAMP = "normal_timer_start_timestamp"

    // Sort options
    private enum class SortOption {
        ACTIVE_FIRST,
        ALPHABETICAL,
        DURATION_ASC,
        DURATION_DESC
    }
    private var currentSortOption = SortOption.ACTIVE_FIRST

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        normalTimerLabel = view.findViewById(R.id.normalTimerLabel)
        normalTimerDisplay = view.findViewById(R.id.normalTimerDisplay)
        normalTimerStartButton = view.findViewById(R.id.normalTimerStartButton)
        normalTimerPauseButton = view.findViewById(R.id.normalTimerPauseButton)
        normalTimerResetButton = view.findViewById(R.id.normalTimerResetButton)
        sortButton = view.findViewById(R.id.sortButton)
        recyclerView = view.findViewById(R.id.timerRecyclerView)
        addFab = view.findViewById(R.id.addTimerFab)

        setupRecyclerView()
        setupNormalTimer()
        setupSortButton()
        loadTimers()

        addFab.setOnClickListener {
            showAddTimerDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // Restore normal timer state
        restoreNormalTimer()
        // Restore active preset timers will be called after loadTimers() completes
    }

    private fun restoreNormalTimer() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

        normalTimerLabelText = prefs.getString(KEY_NORMAL_TIMER_LABEL, "Timer") ?: "Timer"
        normalTimerMillis = prefs.getLong(KEY_NORMAL_TIMER_MILLIS, 0L)
        normalTimerRemaining = prefs.getLong(KEY_NORMAL_TIMER_REMAINING, 0L)
        isNormalTimerRunning = prefs.getBoolean(KEY_NORMAL_TIMER_IS_RUNNING, false)
        isNormalTimerPaused = prefs.getBoolean(KEY_NORMAL_TIMER_IS_PAUSED, false)
        normalTimerStartTimestamp = prefs.getLong(KEY_NORMAL_TIMER_START_TIMESTAMP, 0L)

        normalTimerLabel.text = normalTimerLabelText

        if (isNormalTimerRunning && normalTimerStartTimestamp > 0) {
            // Calculate elapsed time
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - normalTimerStartTimestamp
            val remainingTime = normalTimerRemaining - elapsedTime

            if (remainingTime > 0) {
                // Timer still running, resume it
                normalTimerRemaining = remainingTime
                updateNormalTimerDisplay()
                startNormalTimer()
            } else {
                // Timer finished while away
                normalTimerRemaining = normalTimerMillis
                updateNormalTimerDisplay()
                isNormalTimerRunning = false
                isNormalTimerPaused = false
                normalTimerStartButton.visibility = View.VISIBLE
                normalTimerPauseButton.visibility = View.GONE
                normalTimerResetButton.visibility = View.GONE
                saveNormalTimerState()
                showSnackbar("$normalTimerLabelText finished!")
            }
        } else if (isNormalTimerPaused) {
            // Timer was paused, restore paused state
            updateNormalTimerDisplay()
            normalTimerStartButton.visibility = View.GONE
            normalTimerPauseButton.visibility = View.VISIBLE
            normalTimerPauseButton.text = "RESUME"

            // Set button appearance to match start button
            normalTimerPauseButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.button_start_background)
            normalTimerPauseButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.start_button_text))

            normalTimerResetButton.visibility = View.VISIBLE
        } else {
            // Timer was stopped
            updateNormalTimerDisplay()
            normalTimerStartButton.visibility = View.VISIBLE
            normalTimerPauseButton.visibility = View.GONE
            normalTimerResetButton.visibility = View.GONE
        }
    }

    private fun saveNormalTimerState() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_NORMAL_TIMER_LABEL, normalTimerLabelText)
            putLong(KEY_NORMAL_TIMER_MILLIS, normalTimerMillis)
            putLong(KEY_NORMAL_TIMER_REMAINING, normalTimerRemaining)
            putBoolean(KEY_NORMAL_TIMER_IS_RUNNING, isNormalTimerRunning)
            putBoolean(KEY_NORMAL_TIMER_IS_PAUSED, isNormalTimerPaused)
            putLong(KEY_NORMAL_TIMER_START_TIMESTAMP, normalTimerStartTimestamp)
            apply()
        }
    }

    private fun savePresetTimerState(timerSet: TimerSet) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val prefix = "preset_${timerSet.id}_"

        prefs.edit().apply {
            putBoolean("${prefix}is_active", timerSet.isActive)
            putBoolean("${prefix}is_paused", timerSet.isPaused)
            putLong("${prefix}remaining_millis", timerSet.remainingMillis)
            putLong("${prefix}start_timestamp", timerSet.startTimestamp)
            apply()
        }
    }

    private fun restorePresetTimerState(timerSet: TimerSet): TimerSet {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val prefix = "preset_${timerSet.id}_"

        val isActive = prefs.getBoolean("${prefix}is_active", false)
        val isPaused = prefs.getBoolean("${prefix}is_paused", false)
        val remainingMillis = prefs.getLong("${prefix}remaining_millis", timerSet.durationMillis)
        val startTimestamp = prefs.getLong("${prefix}start_timestamp", 0L)

        return timerSet.copy(
            isActive = isActive,
            isPaused = isPaused,
            remainingMillis = remainingMillis,
            startTimestamp = startTimestamp
        )
    }

    private fun clearPresetTimerState(timerId: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val prefix = "preset_${timerId}_"

        prefs.edit().apply {
            remove("${prefix}is_active")
            remove("${prefix}is_paused")
            remove("${prefix}remaining_millis")
            remove("${prefix}start_timestamp")
            apply()
        }
    }

    private fun restoreActiveTimers() {
        val currentTime = System.currentTimeMillis()

        // Create a copy of the list to avoid ConcurrentModificationException
        val timerSetsCopy = timerSets.toList()

        timerSetsCopy.forEach { timerSet ->
            if (timerSet.isActive && timerSet.startTimestamp > 0) {
                // Calculate elapsed time since timer started
                val elapsedTime = currentTime - timerSet.startTimestamp
                val remainingTime = timerSet.remainingMillis - elapsedTime

                val index = timerSets.indexOfFirst { it.id == timerSet.id }
                if (index != -1) {
                    if (remainingTime > 0) {
                        // Timer still running, update remaining time and restart UI timer
                        timerSets[index] = timerSet.copy(remainingMillis = remainingTime)
                        startTimer(timerSets[index], skipFirebaseSave = true, skipLocalSave = true)
                    } else {
                        // Timer finished while away
                        finishTimer(timerSets[index])
                    }
                }
            }
        }
    }

    private fun setupSortButton() {
        sortButton.setOnClickListener {
            showSortDialog()
        }
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "Active First",
            "Alphabetical (A-Z)",
            "Duration (Shortest)",
            "Duration (Longest)"
        )

        val currentSelection = when (currentSortOption) {
            SortOption.ACTIVE_FIRST -> 0
            SortOption.ALPHABETICAL -> 1
            SortOption.DURATION_ASC -> 2
            SortOption.DURATION_DESC -> 3
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Sort Timers By")
            .setSingleChoiceItems(sortOptions, currentSelection) { dialog, which ->
                currentSortOption = when (which) {
                    0 -> SortOption.ACTIVE_FIRST
                    1 -> SortOption.ALPHABETICAL
                    2 -> SortOption.DURATION_ASC
                    3 -> SortOption.DURATION_DESC
                    else -> SortOption.ACTIVE_FIRST
                }
                sortTimersList()
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupNormalTimer() {
        normalTimerLabel.text = normalTimerLabelText
        normalTimerDisplay.text = "00:00:00"
        normalTimerPauseButton.visibility = View.GONE
        normalTimerResetButton.visibility = View.GONE

        normalTimerDisplay.setOnClickListener {
            showTimePickerForNormalTimer()
        }

        normalTimerLabel.setOnClickListener {
            showTimePickerForNormalTimer()
        }

        normalTimerDisplay.setOnLongClickListener {
            showClearNormalTimerDialog()
            true
        }

        normalTimerLabel.setOnLongClickListener {
            showClearNormalTimerDialog()
            true
        }

        normalTimerStartButton.setOnClickListener {
            if (normalTimerRemaining > 0) {
                startNormalTimer()
            } else {
                showTimePickerForNormalTimer()
            }
        }

        normalTimerPauseButton.setOnClickListener {
            if (isNormalTimerPaused) {
                resumeNormalTimer()
            } else {
                pauseNormalTimer()
            }
        }

        normalTimerResetButton.setOnClickListener {
            resetNormalTimer()
        }
    }

    private fun showClearNormalTimerDialog() {
        if (normalTimerMillis == 0L) {
            showSnackbar("No timer to clear")
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Clear Timer?")
            .setMessage("This will delete \"$normalTimerLabelText\" and reset the timer.")
            .setPositiveButton("Clear") { _, _ ->
                clearNormalTimer()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearNormalTimer() {
        normalTimer?.cancel()
        normalTimerLabelText = "Timer"
        normalTimerMillis = 0L
        normalTimerRemaining = 0L
        isNormalTimerRunning = false
        isNormalTimerPaused = false
        normalTimerStartTimestamp = 0L

        normalTimerLabel.text = normalTimerLabelText
        normalTimerDisplay.text = "00:00:00"
        normalTimerStartButton.visibility = View.VISIBLE
        normalTimerPauseButton.visibility = View.GONE
        normalTimerResetButton.visibility = View.GONE

        saveNormalTimerState()
        showSnackbar("Timer cleared")
    }

    private fun showTimePickerForNormalTimer() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_timer, null)
        val labelInput = dialogView.findViewById<EditText>(R.id.timerLabelInput)
        val hoursPicker = dialogView.findViewById<NumberPicker>(R.id.hoursPicker)
        val minutesPicker = dialogView.findViewById<NumberPicker>(R.id.minutesPicker)
        val secondsPicker = dialogView.findViewById<NumberPicker>(R.id.secondsPicker)

        if (normalTimerLabelText != "Timer") {
            labelInput.setText(normalTimerLabelText)
        }

        hoursPicker.minValue = 0
        hoursPicker.maxValue = 99
        minutesPicker.minValue = 0
        minutesPicker.maxValue = 59
        secondsPicker.minValue = 0
        secondsPicker.maxValue = 59

        AlertDialog.Builder(requireContext())
            .setTitle("Set Timer")
            .setView(dialogView)
            .setPositiveButton("Set") { _, _ ->
                val label = labelInput.text.toString().trim()
                val hours = hoursPicker.value
                val minutes = minutesPicker.value
                val seconds = secondsPicker.value

                val totalMillis = (hours * 3600 + minutes * 60 + seconds) * 1000L

                if (totalMillis > 0) {
                    normalTimerLabelText = label.ifEmpty { "Timer" }
                    normalTimerLabel.text = normalTimerLabelText
                    normalTimerMillis = totalMillis
                    normalTimerRemaining = totalMillis
                    updateNormalTimerDisplay()
                    saveNormalTimerState()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startNormalTimer() {
        isNormalTimerRunning = true
        isNormalTimerPaused = false
        normalTimerStartTimestamp = System.currentTimeMillis()
        normalTimerStartButton.visibility = View.GONE
        normalTimerPauseButton.visibility = View.VISIBLE
        normalTimerPauseButton.text = "PAUSE"
        normalTimerResetButton.visibility = View.VISIBLE

        saveNormalTimerState()

        normalTimer = object : CountDownTimer(normalTimerRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                normalTimerRemaining = millisUntilFinished
                normalTimerDisplay.text = TimeFormatter.formatTimerDisplay(millisUntilFinished)
            }

            override fun onFinish() {
                normalTimerRemaining = normalTimerMillis
                updateNormalTimerDisplay()
                isNormalTimerRunning = false
                isNormalTimerPaused = false
                normalTimerStartTimestamp = 0L
                normalTimerStartButton.visibility = View.VISIBLE
                normalTimerPauseButton.visibility = View.GONE
                normalTimerResetButton.visibility = View.GONE
                saveNormalTimerState()
                showSnackbar("$normalTimerLabelText finished!")
            }
        }.start()
    }

    private fun pauseNormalTimer() {
        normalTimer?.cancel()
        isNormalTimerRunning = false
        isNormalTimerPaused = true
        normalTimerStartTimestamp = 0L
        normalTimerPauseButton.text = "RESUME"

        // Change button appearance to match start button
        normalTimerPauseButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.button_start_background)
        normalTimerPauseButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.start_button_text))

        saveNormalTimerState()
    }

    private fun resumeNormalTimer() {
        isNormalTimerRunning = true
        isNormalTimerPaused = false
        normalTimerStartTimestamp = System.currentTimeMillis()
        normalTimerPauseButton.text = "PAUSE"

        // Restore original pause button appearance
        normalTimerPauseButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.button_pause_background)
        normalTimerPauseButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.pause_button_text))

        saveNormalTimerState()

        normalTimer = object : CountDownTimer(normalTimerRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                normalTimerRemaining = millisUntilFinished
                normalTimerDisplay.text = TimeFormatter.formatTimerDisplay(millisUntilFinished)
            }

            override fun onFinish() {
                normalTimerRemaining = normalTimerMillis
                updateNormalTimerDisplay()
                isNormalTimerRunning = false
                isNormalTimerPaused = false
                normalTimerStartTimestamp = 0L
                normalTimerStartButton.visibility = View.VISIBLE
                normalTimerPauseButton.visibility = View.GONE
                normalTimerResetButton.visibility = View.GONE
                saveNormalTimerState()
                showSnackbar("$normalTimerLabelText finished!")
            }
        }.start()
    }

    private fun resetNormalTimer() {
        normalTimer?.cancel()
        normalTimerRemaining = normalTimerMillis
        updateNormalTimerDisplay()
        isNormalTimerRunning = false
        isNormalTimerPaused = false
        normalTimerStartTimestamp = 0L
        normalTimerStartButton.visibility = View.VISIBLE
        normalTimerPauseButton.visibility = View.GONE
        normalTimerResetButton.visibility = View.GONE
        saveNormalTimerState()
    }

    private fun updateNormalTimerDisplay() {
        normalTimerDisplay.text = TimeFormatter.formatTimerDisplay(normalTimerRemaining)
    }

    private fun setupRecyclerView() {
        adapter = TimerSetAdapter(
            timerSets = timerSets,
            onStart = { timerSet -> startTimer(timerSet) },
            onPause = { timerSet -> pauseTimer(timerSet) },
            onResume = { timerSet -> resumeTimer(timerSet) },
            onRestart = { timerSet -> restartTimer(timerSet) },
            onLongPress = { timerSet -> showTimerOptions(timerSet) },
            onTap = { timerSet -> setAsNormalTimer(timerSet) }
        )

        recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        recyclerView.adapter = adapter
    }

    private fun setAsNormalTimer(timerSet: TimerSet) {
        normalTimerLabelText = timerSet.label
        normalTimerLabel.text = normalTimerLabelText
        normalTimerMillis = timerSet.durationMillis
        normalTimerRemaining = timerSet.durationMillis
        updateNormalTimerDisplay()
        saveNormalTimerState()
        showSnackbar("Set as timer: ${timerSet.label}")
    }

    private fun sortTimersList() {
        when (currentSortOption) {
            SortOption.ACTIVE_FIRST -> {
                timerSets.sortWith(
                    compareByDescending<TimerSet> { it.isActive }
                        .thenByDescending { it.isPaused }
                )
            }
            SortOption.ALPHABETICAL -> {
                timerSets.sortWith(
                    compareByDescending<TimerSet> { it.isActive }
                        .thenBy { it.label.lowercase() }
                )
            }
            SortOption.DURATION_ASC -> {
                timerSets.sortWith(
                    compareByDescending<TimerSet> { it.isActive }
                        .thenBy { it.durationMillis }
                )
            }
            SortOption.DURATION_DESC -> {
                timerSets.sortWith(
                    compareByDescending<TimerSet> { it.isActive }
                        .thenByDescending { it.durationMillis }
                )
            }
        }
    }

    private fun loadTimers() {
        val userId = FirebaseHelper.getCurrentUserId() ?: return

        lifecycleScope.launch {
            val result = FirebaseHelper.getTimerSets(userId)

            result.onSuccess { timerList ->
                timerSets.clear()

                // Restore local state for each timer from SharedPreferences
                timerList.forEach { timerSet ->
                    val restoredTimer = restorePresetTimerState(timerSet)
                    timerSets.add(restoredTimer)
                }

                sortTimersList()
                adapter.notifyDataSetChanged()

                // Restore any active timers after loading
                restoreActiveTimers()
            }
        }
    }

    private fun showAddTimerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_timer, null)
        val labelInput = dialogView.findViewById<EditText>(R.id.timerLabelInput)
        val hoursPicker = dialogView.findViewById<NumberPicker>(R.id.hoursPicker)
        val minutesPicker = dialogView.findViewById<NumberPicker>(R.id.minutesPicker)
        val secondsPicker = dialogView.findViewById<NumberPicker>(R.id.secondsPicker)

        hoursPicker.minValue = 0
        hoursPicker.maxValue = 99
        minutesPicker.minValue = 0
        minutesPicker.maxValue = 59
        secondsPicker.minValue = 0
        secondsPicker.maxValue = 59

        AlertDialog.Builder(requireContext())
            .setTitle("Add Preset Timer")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val label = labelInput.text.toString().trim()
                val hours = hoursPicker.value
                val minutes = minutesPicker.value
                val seconds = secondsPicker.value

                val totalMillis = (hours * 3600 + minutes * 60 + seconds) * 1000L

                if (totalMillis > 0) {
                    addTimerSet(label.ifEmpty { "Timer" }, totalMillis)
                } else {
                    showSnackbar("Please set a valid time")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addTimerSet(label: String, durationMillis: Long) {
        val userId = FirebaseHelper.getCurrentUserId() ?: return

        val timerSet = TimerSet(
            id = UUID.randomUUID().toString(),
            userId = userId,
            label = label,
            durationMillis = durationMillis,
            remainingMillis = durationMillis
        )

        lifecycleScope.launch {
            val result = FirebaseHelper.saveTimerSet(timerSet)

            result.onSuccess {
                showSnackbar("Timer saved!")
                loadTimers()
            }.onFailure {
                showSnackbar("Failed to save timer")
            }
        }
    }

    private fun startTimer(timerSet: TimerSet, skipFirebaseSave: Boolean = false, skipLocalSave: Boolean = false) {
        // Cancel existing UI timer if running
        activeTimers[timerSet.id]?.cancel()
        activeTimers.remove(timerSet.id)

        val index = timerSets.indexOfFirst { it.id == timerSet.id }
        if (index == -1) return

        val currentTimerSet = timerSets[index]
        val startTime = System.currentTimeMillis()

        // Create UI countdown timer
        val timer = object : CountDownTimer(currentTimerSet.remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val currentIndex = timerSets.indexOfFirst { it.id == timerSet.id }
                if (currentIndex != -1) {
                    timerSets[currentIndex] = timerSets[currentIndex].copy(
                        remainingMillis = millisUntilFinished
                    )
                    adapter.notifyItemChanged(currentIndex, "UPDATE_TIME_ONLY")
                }
            }

            override fun onFinish() {
                val currentIndex = timerSets.indexOfFirst { it.id == timerSet.id }
                if (currentIndex != -1) {
                    finishTimer(timerSets[currentIndex])
                }
            }
        }.start()

        activeTimers[timerSet.id] = timer

        // Update timer state
        timerSets[index] = currentTimerSet.copy(
            isActive = true,
            isPaused = false,
            startTimestamp = startTime
        )

        // Save to local storage
        if (!skipLocalSave) {
            savePresetTimerState(timerSets[index])
        }

        // Save to Firebase (unless we're restoring from a previous session)
        if (!skipFirebaseSave) {
            lifecycleScope.launch {
                FirebaseHelper.saveTimerSet(timerSets[index])
            }
        }

        sortTimersList()
        adapter.notifyDataSetChanged()
    }

    private fun finishTimer(timerSet: TimerSet) {
        activeTimers[timerSet.id]?.cancel()
        activeTimers.remove(timerSet.id)

        val index = timerSets.indexOfFirst { it.id == timerSet.id }
        if (index != -1) {
            val resetTimer = timerSets[index].copy(
                isActive = false,
                isPaused = false,
                remainingMillis = timerSets[index].durationMillis,
                startTimestamp = 0L
            )
            timerSets[index] = resetTimer

            // Save to local storage
            savePresetTimerState(resetTimer)

            // Save reset state to Firebase
            lifecycleScope.launch {
                FirebaseHelper.saveTimerSet(resetTimer)
            }

            sortTimersList()
            adapter.notifyDataSetChanged()

            showSnackbar("${timerSets[index].label} finished!")
        }
    }

    private fun pauseTimer(timerSet: TimerSet) {
        activeTimers[timerSet.id]?.cancel()
        activeTimers.remove(timerSet.id)

        val index = timerSets.indexOfFirst { it.id == timerSet.id }
        if (index != -1) {
            val pausedTimer = timerSets[index].copy(
                isActive = false,
                isPaused = true,
                startTimestamp = 0L
            )
            timerSets[index] = pausedTimer

            // Save to local storage
            savePresetTimerState(pausedTimer)

            // Save paused state to Firebase
            lifecycleScope.launch {
                FirebaseHelper.saveTimerSet(pausedTimer)
            }

            sortTimersList()
            adapter.notifyDataSetChanged()
        }
    }

    private fun resumeTimer(timerSet: TimerSet) {
        startTimer(timerSet)
    }

    private fun restartTimer(timerSet: TimerSet) {
        activeTimers[timerSet.id]?.cancel()
        activeTimers.remove(timerSet.id)

        val index = timerSets.indexOfFirst { it.id == timerSet.id }
        if (index != -1) {
            val resetTimer = timerSets[index].copy(
                isActive = false,
                isPaused = false,
                remainingMillis = timerSets[index].durationMillis,
                startTimestamp = 0L
            )
            timerSets[index] = resetTimer

            // Save to local storage
            savePresetTimerState(resetTimer)

            // Save reset state to Firebase
            lifecycleScope.launch {
                FirebaseHelper.saveTimerSet(resetTimer)
            }

            sortTimersList()
            adapter.notifyDataSetChanged()
        }
    }

    private fun showTimerOptions(timerSet: TimerSet) {
        val options = arrayOf("Edit Timer", "Delete Timer")

        AlertDialog.Builder(requireContext())
            .setTitle(timerSet.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editTimer(timerSet)
                    1 -> deleteTimer(timerSet)
                }
            }
            .show()
    }

    private fun editTimer(timerSet: TimerSet) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_timer, null)
        val labelInput = dialogView.findViewById<EditText>(R.id.timerLabelInput)
        val hoursPicker = dialogView.findViewById<NumberPicker>(R.id.hoursPicker)
        val minutesPicker = dialogView.findViewById<NumberPicker>(R.id.minutesPicker)
        val secondsPicker = dialogView.findViewById<NumberPicker>(R.id.secondsPicker)

        labelInput.setText(timerSet.label)

        val hours = (timerSet.durationMillis / (1000 * 60 * 60)).toInt()
        val minutes = ((timerSet.durationMillis % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        val seconds = ((timerSet.durationMillis % (1000 * 60)) / 1000).toInt()

        hoursPicker.minValue = 0
        hoursPicker.maxValue = 99
        hoursPicker.value = hours

        minutesPicker.minValue = 0
        minutesPicker.maxValue = 59
        minutesPicker.value = minutes

        secondsPicker.minValue = 0
        secondsPicker.maxValue = 59
        secondsPicker.value = seconds

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Timer")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val label = labelInput.text.toString().trim()
                val newHours = hoursPicker.value
                val newMinutes = minutesPicker.value
                val newSeconds = secondsPicker.value

                val totalMillis = (newHours * 3600 + newMinutes * 60 + newSeconds) * 1000L

                if (totalMillis > 0) {
                    updateTimerSet(timerSet, label.ifEmpty { "Timer" }, totalMillis)
                } else {
                    showSnackbar("Please set a valid time")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTimerSet(timerSet: TimerSet, newLabel: String, newDuration: Long) {
        activeTimers[timerSet.id]?.cancel()
        activeTimers.remove(timerSet.id)

        val updatedTimer = timerSet.copy(
            label = newLabel,
            durationMillis = newDuration,
            remainingMillis = newDuration,
            isActive = false,
            isPaused = false,
            startTimestamp = 0L
        )

        // Clear local storage for this timer
        clearPresetTimerState(timerSet.id)

        lifecycleScope.launch {
            val result = FirebaseHelper.saveTimerSet(updatedTimer)

            result.onSuccess {
                showSnackbar("Timer updated!")
                loadTimers()
            }.onFailure {
                showSnackbar("Failed to update timer")
            }
        }
    }

    private fun deleteTimer(timerSet: TimerSet) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Timer")
            .setMessage("Delete \"${timerSet.label}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    activeTimers[timerSet.id]?.cancel()
                    activeTimers.remove(timerSet.id)

                    // Clear local storage for this timer
                    clearPresetTimerState(timerSet.id)

                    FirebaseHelper.deleteTimerSet(timerSet.id)
                    loadTimers()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun scrollToAndHighlightTimer(timerId: String) {
        val position = timerSets.indexOfFirst { it.id == timerId }

        if (position != -1) {
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
            showSnackbar("Timer not found")
        }
    }

    // Helper function to show Snackbar
    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        // Save current state of all timers
        timerSets.forEach { timerSet ->
            savePresetTimerState(timerSet)
        }
    }

    override fun onStop() {
        super.onStop()
        // Timers continue running - state is already saved
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel UI timers but states persist in SharedPreferences and Firebase
        normalTimer?.cancel()
        activeTimers.values.forEach { it.cancel() }
        activeTimers.clear()
    }
}