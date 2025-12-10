package com.example.trackr.adapters

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trackr.R
import com.example.trackr.models.Activity
import com.example.trackr.utils.TimeFormatter

class ActivityAdapter(
    private val activities: List<Activity>,
    private val onToggleComplete: (Activity, Boolean) -> Unit,
    private val onDelete: (Activity) -> Unit,
    private val onLongPress: (Activity) -> Unit,
    private val onClick: (Activity) -> Unit
) : RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

    companion object {
        const val COLOR_COMPLETED_BG = "#E0E0E0"
        const val COLOR_COMPLETED_TEXT = "#616161"
        const val COLOR_OVERDUE_BG = "#FFCDD2"
        const val COLOR_OVERDUE_TEXT = "#C62828"
        const val COLOR_URGENT_BG = "#FFE0B2"
        const val COLOR_URGENT_TEXT = "#E65100"
        const val COLOR_UPCOMING_BG = "#FFF9C4"
        const val COLOR_UPCOMING_TEXT = "#F57F17"
        const val COLOR_SAFE_BG = "#C8E6C9"
        const val COLOR_SAFE_TEXT = "#2E7D32"
    }

    class ActivityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.titleText)
        val categoryText: TextView = view.findViewById(R.id.categoryText)
        val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        val remainingText: TextView = view.findViewById(R.id.remainingText)
        val completeCheckbox: CheckBox = view.findViewById(R.id.completeCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deadline, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = activities[position]

        android.util.Log.d("ActivityAdapter", "Binding activity: ${activity.title}, isCompleted: ${activity.isCompleted}")

        holder.titleText.text = activity.title
        holder.categoryText.text = activity.category
        holder.dateTimeText.text = TimeFormatter.formatDateTime(activity.dateTimeInMillis)

        // CRITICAL FIX: Remove listener BEFORE setting checked state
        holder.completeCheckbox.setOnCheckedChangeListener(null)
        holder.completeCheckbox.isChecked = activity.isCompleted

        // Strikethrough if completed
        if (activity.isCompleted) {
            holder.titleText.paintFlags = holder.titleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            // FIXED: Show completion date for finished activities
            if (activity.completedAt > 0) {
                holder.remainingText.text = "Finished: ${TimeFormatter.formatDateTime(activity.completedAt)}"
            } else {
                holder.remainingText.text = "Completed"
            }
        } else {
            holder.titleText.paintFlags = holder.titleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.remainingText.text = TimeFormatter.getTimeRemaining(activity.dateTimeInMillis)
        }

        // Apply colors - completed activities retain their original status color
        val currentTime = System.currentTimeMillis()
        val timeRemaining = activity.dateTimeInMillis - currentTime

        val (bgColor, textColor) = if (activity.isCompleted) {
            // For completed: show what status it was when completed
            when {
                activity.completedAt > 0 && activity.dateTimeInMillis < activity.completedAt -> {
                    // Was overdue when completed
                    Pair(COLOR_OVERDUE_BG, COLOR_OVERDUE_TEXT)
                }
                activity.completedAt > 0 && (activity.completedAt - activity.dateTimeInMillis) < 24 * 60 * 60 * 1000 -> {
                    // Was urgent when completed
                    Pair(COLOR_URGENT_BG, COLOR_URGENT_TEXT)
                }
                activity.completedAt > 0 && (activity.completedAt - activity.dateTimeInMillis) < 48 * 60 * 60 * 1000 -> {
                    // Was upcoming when completed
                    Pair(COLOR_UPCOMING_BG, COLOR_UPCOMING_TEXT)
                }
                else -> {
                    // Was safe when completed or no completion time
                    Pair(COLOR_SAFE_BG, COLOR_SAFE_TEXT)
                }
            }
        } else {
            // For active activities: show current status
            when {
                timeRemaining < 0 -> Pair(COLOR_OVERDUE_BG, COLOR_OVERDUE_TEXT)
                timeRemaining < 24 * 60 * 60 * 1000 -> Pair(COLOR_URGENT_BG, COLOR_URGENT_TEXT)
                timeRemaining < 48 * 60 * 60 * 1000 -> Pair(COLOR_UPCOMING_BG, COLOR_UPCOMING_TEXT)
                else -> Pair(COLOR_SAFE_BG, COLOR_SAFE_TEXT)
            }
        }

        // Create rounded background with color
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(bgColor))
            cornerRadius = 12f * holder.itemView.resources.displayMetrics.density
        }

        holder.remainingText.background = background
        holder.remainingText.setTextColor(Color.parseColor(textColor))

        // CRITICAL FIX: Set listener AFTER setting checked state
        // FIXED: Allow unchecking for finished activities
        holder.completeCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            android.util.Log.d("ActivityAdapter", "Checkbox changed for ${activity.title}: $isChecked")
            // Prevent triggering during bind
            if (buttonView.isPressed) {
                android.util.Log.d("ActivityAdapter", "User clicked checkbox, calling onToggleComplete")
                onToggleComplete(activity, isChecked)
            }
        }

        // Click listener for viewing details
        holder.itemView.setOnClickListener {
            onClick(activity)
        }

        // Long press listener for edit/delete options
        holder.itemView.setOnLongClickListener {
            onLongPress(activity)
            true
        }
    }

    override fun getItemCount(): Int = activities.size
}