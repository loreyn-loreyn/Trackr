package com.example.trackr

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.trackr.models.Alarm
import com.example.trackr.utils.FirebaseHelper
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AlarmSettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var labelInput: TextInputEditText
    private lateinit var repeatSpinner: Spinner
    private lateinit var ringtoneSpinner: Spinner
    private lateinit var daysLayout: LinearLayout
    private lateinit var dateLayout: LinearLayout
    private lateinit var selectDateButton: Button
    private lateinit var selectedDateText: TextView
    private lateinit var saveButton: Button

    // Day checkboxes
    private lateinit var checkSun: CheckBox
    private lateinit var checkMon: CheckBox
    private lateinit var checkTue: CheckBox
    private lateinit var checkWed: CheckBox
    private lateinit var checkThu: CheckBox
    private lateinit var checkFri: CheckBox
    private lateinit var checkSat: CheckBox

    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0
    private var selectedDateMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_settings)

        // Get time from intent
        selectedHour = intent.getIntExtra("HOUR", 0)
        selectedMinute = intent.getIntExtra("MINUTE", 0)

        initializeViews()
        setupToolbar()
        setupSpinners()
        setupDatePicker()
        setupSaveButton()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        labelInput = findViewById(R.id.labelInput)
        repeatSpinner = findViewById(R.id.repeatSpinner)
        ringtoneSpinner = findViewById(R.id.ringtoneSpinner)
        daysLayout = findViewById(R.id.daysLayout)
        dateLayout = findViewById(R.id.dateLayout)
        selectDateButton = findViewById(R.id.selectDateButton)
        selectedDateText = findViewById(R.id.selectedDateText)
        saveButton = findViewById(R.id.saveButton)

        checkSun = findViewById(R.id.checkSun)
        checkMon = findViewById(R.id.checkMon)
        checkTue = findViewById(R.id.checkTue)
        checkWed = findViewById(R.id.checkWed)
        checkThu = findViewById(R.id.checkThu)
        checkFri = findViewById(R.id.checkFri)
        checkSat = findViewById(R.id.checkSat)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupSpinners() {
        // Repeat options
        val repeatOptions = arrayOf("Once", "Daily", "Weekdays", "Weekends", "Custom", "Specific Date")
        val repeatAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, repeatOptions)
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        repeatSpinner.adapter = repeatAdapter

        repeatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    4 -> { // Custom
                        daysLayout.visibility = View.VISIBLE
                        dateLayout.visibility = View.GONE
                    }
                    5 -> { // Specific Date
                        daysLayout.visibility = View.GONE
                        dateLayout.visibility = View.VISIBLE
                    }
                    else -> {
                        daysLayout.visibility = View.GONE
                        dateLayout.visibility = View.GONE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Ringtone options
        val ringtoneOptions = arrayOf("Default", "Gentle", "Loud", "Beep", "Classical")
        val ringtoneAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, ringtoneOptions)
        ringtoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ringtoneSpinner.adapter = ringtoneAdapter
    }

    private fun setupDatePicker() {
        selectDateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val selectedCalendar = Calendar.getInstance()
                    selectedCalendar.set(
                        selectedYear,
                        selectedMonth,
                        selectedDay,
                        selectedHour,
                        selectedMinute
                    )
                    selectedDateMillis = selectedCalendar.timeInMillis

                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    selectedDateText.text = dateFormat.format(selectedCalendar.time)
                    selectedDateText.visibility = View.VISIBLE
                },
                year,
                month,
                day
            )

            datePickerDialog.datePicker.minDate = System.currentTimeMillis()
            datePickerDialog.show()
        }
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            saveAlarm()
        }
    }

    private fun saveAlarm() {
        val userId = FirebaseHelper.getCurrentUserId()
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val label = labelInput.text.toString().trim().ifEmpty { "Alarm" }
        val repeatOption = repeatSpinner.selectedItem.toString()

        // Build repeat string
        val repeat = when (repeatOption) {
            "Custom" -> {
                val selectedDays = mutableListOf<String>()
                if (checkSun.isChecked) selectedDays.add("Sun")
                if (checkMon.isChecked) selectedDays.add("Mon")
                if (checkTue.isChecked) selectedDays.add("Tue")
                if (checkWed.isChecked) selectedDays.add("Wed")
                if (checkThu.isChecked) selectedDays.add("Thu")
                if (checkFri.isChecked) selectedDays.add("Fri")
                if (checkSat.isChecked) selectedDays.add("Sat")

                if (selectedDays.isEmpty()) {
                    Toast.makeText(this, "Please select at least one day", Toast.LENGTH_SHORT).show()
                    return
                }
                selectedDays.joinToString(", ")
            }
            "Specific Date" -> {
                if (selectedDateMillis == 0L) {
                    Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
                    return
                }
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                dateFormat.format(Date(selectedDateMillis))
            }
            else -> repeatOption
        }

        val ringtone = ringtoneSpinner.selectedItem.toString()

        // Calculate time in milliseconds
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // If specific date, use that date
        if (repeatOption == "Specific Date" && selectedDateMillis != 0L) {
            calendar.timeInMillis = selectedDateMillis
        } else if (calendar.timeInMillis <= System.currentTimeMillis()) {
            // If the time has passed today, set it for tomorrow
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarm = Alarm(
            id = "",
            userId = userId,
            label = label,
            timeInMillis = calendar.timeInMillis,
            hour = selectedHour,
            minute = selectedMinute,
            repeat = repeat,
            ringtone = ringtone,
            isEnabled = true,
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            val result = FirebaseHelper.saveAlarm(alarm)

            result.onSuccess {
                Toast.makeText(this@AlarmSettingsActivity, "Alarm saved!", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { exception ->
                Toast.makeText(
                    this@AlarmSettingsActivity,
                    "Failed to save alarm: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}