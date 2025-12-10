package com.example.trackr.main

import android.app.TimePickerDialog
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.example.trackr.R
import com.example.trackr.adapters.AlarmAdapter
import com.example.trackr.models.Alarm
import com.example.trackr.utils.FirebaseHelper
import kotlinx.coroutines.launch
import java.util.*

class AlarmFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addAlarmFab: FloatingActionButton
    private lateinit var adapter: AlarmAdapter
    private lateinit var rootView: View

    private val alarms = mutableListOf<Alarm>()

    // Map day numbers to abbreviations
    private val dayAbbreviations = mapOf(
        1 to "S",  // Sunday
        2 to "M",  // Monday
        3 to "T",  // Tuesday
        4 to "W",  // Wednesday
        5 to "Th", // Thursday
        6 to "F",  // Friday
        7 to "Sa"  // Saturday
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_alarm, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.alarmRecyclerView)
        addAlarmFab = view.findViewById(R.id.addAlarmFab)

        setupRecyclerView()
        loadAlarms()

        addAlarmFab.setOnClickListener {
            showAddAlarmDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = AlarmAdapter(
            alarms = alarms,
            onToggle = { alarm, isEnabled -> toggleAlarm(alarm, isEnabled) },
            onLongPress = { alarm -> showEditDialog(alarm) },
            onClick = { alarm -> showAlarmInfoDialog(alarm) }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadAlarms() {
        val userId = FirebaseHelper.getCurrentUserId()
        if (userId == null) {
            Snackbar.make(rootView, "User not logged in", Snackbar.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = FirebaseHelper.getAlarms(userId)

            result.onSuccess { alarmList ->
                android.util.Log.d("AlarmFragment", "Loaded ${alarmList.size} alarms")
                alarms.clear()
                alarms.addAll(alarmList)
                adapter.notifyDataSetChanged()
            }.onFailure { exception ->
                Snackbar.make(
                    rootView,
                    "Failed to load alarms: ${exception.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAddAlarmDialog(existingAlarm: Alarm? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_alarm, null)
        val labelInput = dialogView.findViewById<TextInputEditText>(R.id.labelInput)
        val selectTimeButton = dialogView.findViewById<Button>(R.id.selectTimeButton)
        val repeatSpinner = dialogView.findViewById<Spinner>(R.id.repeatSpinner)
        val ringtoneSpinner = dialogView.findViewById<Spinner>(R.id.ringtoneSpinner)
        val daysLayout = dialogView.findViewById<LinearLayout>(R.id.daysLayout)

        // Day checkboxes
        val checkSun = dialogView.findViewById<CheckBox>(R.id.checkSun)
        val checkMon = dialogView.findViewById<CheckBox>(R.id.checkMon)
        val checkTue = dialogView.findViewById<CheckBox>(R.id.checkTue)
        val checkWed = dialogView.findViewById<CheckBox>(R.id.checkWed)
        val checkThu = dialogView.findViewById<CheckBox>(R.id.checkThu)
        val checkFri = dialogView.findViewById<CheckBox>(R.id.checkFri)
        val checkSat = dialogView.findViewById<CheckBox>(R.id.checkSat)

        // Initialize time
        var selectedHour = existingAlarm?.hour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        var selectedMinute = existingAlarm?.minute ?: Calendar.getInstance().get(Calendar.MINUTE)

        // Setup repeat spinner
        val repeatOptions = arrayOf("Once", "Daily", "Weekdays", "Weekends", "Custom")
        repeatSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, repeatOptions)

        // Set existing repeat value and parse custom days
        existingAlarm?.let { alarm ->
            if (alarm.repeat.startsWith("Custom:")) {
                repeatSpinner.setSelection(repeatOptions.indexOf("Custom"))
                // Parse custom days
                val daysPart = alarm.repeat.substringAfter("Custom:")
                val dayNumbers = daysPart.split(",").mapNotNull { it.trim().toIntOrNull() }

                checkSun.isChecked = dayNumbers.contains(1)
                checkMon.isChecked = dayNumbers.contains(2)
                checkTue.isChecked = dayNumbers.contains(3)
                checkWed.isChecked = dayNumbers.contains(4)
                checkThu.isChecked = dayNumbers.contains(5)
                checkFri.isChecked = dayNumbers.contains(6)
                checkSat.isChecked = dayNumbers.contains(7)
            } else {
                val repeatIndex = repeatOptions.indexOf(alarm.repeat)
                if (repeatIndex != -1) {
                    repeatSpinner.setSelection(repeatIndex)
                }
            }
        }

        // Setup ringtone spinner
        val ringtoneOptions = arrayOf("Default", "Beep", "Chime", "Classic", "Digital")
        ringtoneSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ringtoneOptions)

        // Set existing ringtone value
        existingAlarm?.let { alarm ->
            val ringtoneIndex = ringtoneOptions.indexOf(alarm.ringtone)
            if (ringtoneIndex != -1) {
                ringtoneSpinner.setSelection(ringtoneIndex)
            }
        }

        // Show/hide days layout based on repeat selection
        repeatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                daysLayout.visibility = if (repeatOptions[position] == "Custom") View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Pre-fill existing alarm data
        existingAlarm?.let { alarm ->
            labelInput.setText(alarm.label)
            selectTimeButton.text = alarm.getDisplayTime()

            // If custom repeat, show days layout
            if (alarm.repeat.startsWith("Custom:")) {
                daysLayout.visibility = View.VISIBLE
            }
        }

        // Time picker button
        selectTimeButton.text = if (existingAlarm != null) {
            existingAlarm.getDisplayTime()
        } else {
            "Select Time"
        }

        selectTimeButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                R.style.DarkerTimePickerTheme,
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute

                    // Format time for display
                    val h = if (hourOfDay == 0) 12 else if (hourOfDay > 12) hourOfDay - 12 else hourOfDay
                    val amPm = if (hourOfDay < 12) "AM" else "PM"
                    selectTimeButton.text = String.format("%02d:%02d %s", h, minute, amPm)
                },
                selectedHour,
                selectedMinute,
                false
            ).show()
        }

        // Build dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingAlarm != null) "Edit Alarm" else "Add Alarm")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val label = labelInput.text.toString().trim()
                val repeat = repeatSpinner.selectedItem.toString()
                val ringtone = ringtoneSpinner.selectedItem.toString()

                // Get selected days if custom
                val customDays = if (repeat == "Custom") {
                    val days = mutableListOf<Int>()
                    if (checkSun.isChecked) days.add(1)
                    if (checkMon.isChecked) days.add(2)
                    if (checkTue.isChecked) days.add(3)
                    if (checkWed.isChecked) days.add(4)
                    if (checkThu.isChecked) days.add(5)
                    if (checkFri.isChecked) days.add(6)
                    if (checkSat.isChecked) days.add(7)
                    "Custom:${days.joinToString(",")}"
                } else {
                    repeat
                }

                if (selectTimeButton.text == "Select Time") {
                    Snackbar.make(rootView, "Please select a time", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                saveAlarm(
                    existingAlarm?.id ?: "",
                    label.ifEmpty { "Alarm" },
                    selectedHour,
                    selectedMinute,
                    customDays,
                    ringtone
                )
            }
            .setNegativeButton("Cancel", null)

        dialog.show()
    }

    private fun saveAlarm(
        alarmId: String,
        label: String,
        hour: Int,
        minute: Int,
        repeat: String,
        ringtone: String
    ) {
        val userId = FirebaseHelper.getCurrentUserId() ?: return

        // Calculate time in millis for next occurrence
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time is in the past today, move to tomorrow
            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarm = Alarm(
            id = alarmId,
            userId = userId,
            label = label,
            timeInMillis = calendar.timeInMillis,
            hour = hour,
            minute = minute,
            repeat = repeat,
            ringtone = ringtone,
            isEnabled = true
        )

        lifecycleScope.launch {
            val result = FirebaseHelper.saveAlarm(alarm)

            result.onSuccess {
                val message = if (alarmId.isEmpty()) "Alarm created!" else "Alarm updated!"
                Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
                loadAlarms()
            }.onFailure { e ->
                Snackbar.make(rootView, "Failed to save: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleAlarm(alarm: Alarm, isEnabled: Boolean) {
        lifecycleScope.launch {
            val result = FirebaseHelper.updateAlarm(alarm.id, isEnabled)

            result.onSuccess {
                // Update local list
                val index = alarms.indexOfFirst { it.id == alarm.id }
                if (index != -1) {
                    alarms[index] = alarm.copy(isEnabled = isEnabled)
                    adapter.notifyItemChanged(index)
                }

                val message = if (isEnabled) "Alarm enabled" else "Alarm disabled"
                Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
            }.onFailure { exception ->
                Snackbar.make(
                    rootView,
                    "Failed to update alarm: ${exception.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
                loadAlarms()
            }
        }
    }

    private fun showAlarmInfoDialog(alarm: Alarm) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_alarm_detail, null)

        val detailLabelText = dialogView.findViewById<TextView>(R.id.detailLabelText)
        val detailTimeText = dialogView.findViewById<TextView>(R.id.detailTimeText)
        val detailStatusText = dialogView.findViewById<TextView>(R.id.detailStatusText)
        val detailRepeatText = dialogView.findViewById<TextView>(R.id.detailRepeatText)
        val detailRingtoneText = dialogView.findViewById<TextView>(R.id.detailRingtoneText)
        val detailStatusIcon = dialogView.findViewById<ImageView>(R.id.detailStatusIcon)
        val detailStatusDescription = dialogView.findViewById<TextView>(R.id.detailStatusDescription)
        // Set alarm details
        detailLabelText.text = alarm.label.ifEmpty { "Alarm" }
        detailTimeText.text = alarm.getDisplayTime()

        val repeatDisplay = if (alarm.repeat.startsWith("Custom:")) {
            formatCustomDays(alarm.repeat)
        } else {
            alarm.repeat
        }
        detailRepeatText.text = repeatDisplay
        detailRingtoneText.text = alarm.ringtone

        // Set status with color and icon
        if (alarm.isEnabled) {
            detailStatusText.text = "Enabled"
            detailStatusDescription.text = "Active"
            detailStatusDescription.setTextColor(resources.getColor(R.color.green_500, null))
            detailStatusIcon.setImageResource(R.drawable.ic_check_circle)
            detailStatusIcon.setColorFilter(resources.getColor(R.color.green_500, null))
        } else {
            detailStatusText.text = "Disabled"
            detailStatusDescription.text = "Inactive"
            detailStatusDescription.setTextColor(resources.getColor(R.color.gray_500, null))
            detailStatusIcon.setColorFilter(resources.getColor(R.color.gray_500, null))
        }

        // Create and show dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
    }

    private fun showEditDialog(alarm: Alarm) {
        val options = arrayOf("Edit", "Delete")

        AlertDialog.Builder(requireContext())
            .setTitle(alarm.label.ifEmpty { "Alarm at ${alarm.getDisplayTime()}" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddAlarmDialog(alarm) // Edit
                    1 -> showDeleteConfirmation(alarm) // Delete
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(alarm: Alarm) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Alarm")
            .setMessage("Delete alarm \"${alarm.label}\" at ${alarm.getDisplayTime()}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteAlarm(alarm)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAlarm(alarm: Alarm) {
        lifecycleScope.launch {
            val result = FirebaseHelper.deleteAlarm(alarm.id)

            result.onSuccess {
                Snackbar.make(rootView, "Alarm deleted", Snackbar.LENGTH_SHORT).show()
                loadAlarms()
            }.onFailure { exception ->
                Snackbar.make(
                    rootView,
                    "Failed to delete alarm: ${exception.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAlarms()
    }

    fun scrollToAndHighlightAlarm(alarmId: String) {
        val position = alarms.indexOfFirst { alarm -> alarm.id == alarmId }

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
            Snackbar.make(rootView, "Alarm not found", Snackbar.LENGTH_SHORT).show()
        }
    }

    // Helper function to convert day numbers to abbreviations
    fun formatCustomDays(repeat: String): String {
        if (!repeat.startsWith("Custom:")) return repeat

        val daysPart = repeat.substringAfter("Custom:")
        val dayNumbers = daysPart.split(",").mapNotNull { it.trim().toIntOrNull() }
        val dayAbbrevs = dayNumbers.mapNotNull { dayAbbreviations[it] }

        return "Custom: ${dayAbbrevs.joinToString(", ")}"
    }
}