package com.example.trackr.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.trackr.R
import com.example.trackr.models.TimerSet
import com.example.trackr.utils.TimeFormatter

class TimerSetAdapter(
    private val timerSets: MutableList<TimerSet>,
    private val onStart: (TimerSet) -> Unit,
    private val onPause: (TimerSet) -> Unit,
    private val onResume: (TimerSet) -> Unit,
    private val onRestart: (TimerSet) -> Unit,
    private val onLongPress: (TimerSet) -> Unit,
    private val onTap: (TimerSet) -> Unit
) : RecyclerView.Adapter<TimerSetAdapter.TimerViewHolder>() {

    // Color constants matching ActivityAdapter style
    companion object {
        // Reset button colors (overdue style - red)
        const val COLOR_RESET_BG = "#FFCDD2"       // Soft red/pink background
        const val COLOR_RESET_TEXT = "#C62828"     // Deep red text

        // Pause button colors (upcoming/within 24-48h style - yellow)
        const val COLOR_PAUSE_BG = "#FFF9C4"       // Soft yellow background
        const val COLOR_PAUSE_TEXT = "#F57F17"     // Deep yellow/gold text

        // Start/Resume button colors (safe/far style - green)
        const val COLOR_START_BG = "#C8E6C9"       // Soft green background
        const val COLOR_START_TEXT = "#2E7D32"     // Deep green text
    }

    inner class TimerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timerLabel: TextView = itemView.findViewById(R.id.timerLabel)
        val timerTime: TextView = itemView.findViewById(R.id.timerTime)
        val presetTimeText: TextView = itemView.findViewById(R.id.presetTimeText)
        val startButton: Button = itemView.findViewById(R.id.startButton)
        val pauseButton: Button = itemView.findViewById(R.id.pauseButton)
        val restartButton: Button = itemView.findViewById(R.id.restartButton)
        val activeButtonsLayout: LinearLayout = itemView.findViewById(R.id.activeButtonsLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timer_set, parent, false)
        return TimerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        if (position >= timerSets.size) return

        val timerSet = timerSets[position]

        // Debug: Print what we're binding
        android.util.Log.d("TimerAdapter", "Binding timer at position $position: label=${timerSet.label}, duration=${timerSet.durationMillis}, remaining=${timerSet.remainingMillis}, isActive=${timerSet.isActive}, isPaused=${timerSet.isPaused}")

        // Always set the text content with null checks
        holder.timerLabel.text = timerSet.label.ifEmpty { "Timer" }
        holder.timerTime.text = TimeFormatter.formatTimerDisplay(timerSet.remainingMillis)
        holder.presetTimeText.text = "Preset: ${timerSet.getDisplayDuration()}"

        // Make sure views are visible
        holder.timerLabel.visibility = View.VISIBLE
        holder.timerTime.visibility = View.VISIBLE
        holder.presetTimeText.visibility = View.VISIBLE

        // Update button visibility and text based on timer state
        updateButtonVisibility(holder, timerSet)

        // Remove previous listeners to avoid duplication
        holder.startButton.setOnClickListener(null)
        holder.pauseButton.setOnClickListener(null)
        holder.restartButton.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnClickListener(null)

        // Set up button click listeners
        holder.startButton.setOnClickListener {
            onStart(timerSet)
        }

        // Pause button handles both pause and resume based on current state
        holder.pauseButton.setOnClickListener {
            if (timerSet.isPaused) {
                // If currently paused, resume the timer
                onResume(timerSet)
            } else {
                // If currently running, pause the timer
                onPause(timerSet)
            }
        }

        holder.restartButton.setOnClickListener {
            onRestart(timerSet)
        }

        // Long press for options
        holder.itemView.setOnLongClickListener {
            onLongPress(timerSet)
            true
        }

        // Tap to set as normal timer
        holder.itemView.setOnClickListener {
            onTap(timerSet)
        }
    }

    override fun onBindViewHolder(
        holder: TimerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            // Partial update for time only
            if (payloads.contains("UPDATE_TIME_ONLY")) {
                val timerSet = timerSets[position]
                holder.timerTime.text = TimeFormatter.formatTimerDisplay(timerSet.remainingMillis)
                // Also update button visibility in case state changed
                updateButtonVisibility(holder, timerSet)
            }
        }
    }

    private fun updateButtonVisibility(holder: TimerViewHolder, timerSet: TimerSet) {
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density

        when {
            // Timer is stopped/not started - show START button only
            !timerSet.isActive && !timerSet.isPaused -> {
                holder.startButton.visibility = View.VISIBLE
                holder.activeButtonsLayout.visibility = View.GONE
            }
            // Timer is running - show PAUSE and RESET buttons
            timerSet.isActive && !timerSet.isPaused -> {
                holder.startButton.visibility = View.GONE
                holder.activeButtonsLayout.visibility = View.VISIBLE
                holder.pauseButton.text = "PAUSE"

                // Set PAUSE button style - create drawable programmatically since XML isn't applying
                val pauseBackground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(COLOR_PAUSE_BG))
                    cornerRadius = 8f * density
                }
                holder.pauseButton.background = pauseBackground
                holder.pauseButton.setTextColor(Color.parseColor(COLOR_PAUSE_TEXT))

                // Set RESET button style
                val resetBackground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(COLOR_RESET_BG))
                    cornerRadius = 8f * density
                }
                holder.restartButton.background = resetBackground
                holder.restartButton.setTextColor(Color.parseColor(COLOR_RESET_TEXT))
            }
            // Timer is paused - show RESUME and RESET buttons
            timerSet.isPaused -> {
                holder.startButton.visibility = View.GONE
                holder.activeButtonsLayout.visibility = View.VISIBLE
                holder.pauseButton.text = "RESUME"

                // Set RESUME button style
                val resumeBackground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(COLOR_START_BG))
                    cornerRadius = 8f * density
                }
                holder.pauseButton.background = resumeBackground
                holder.pauseButton.setTextColor(Color.parseColor(COLOR_START_TEXT))

                // Set RESET button style
                val resetBackground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(COLOR_RESET_BG))
                    cornerRadius = 8f * density
                }
                holder.restartButton.background = resetBackground
                holder.restartButton.setTextColor(Color.parseColor(COLOR_RESET_TEXT))
            }
        }
    }

    override fun getItemCount(): Int = timerSets.size
}