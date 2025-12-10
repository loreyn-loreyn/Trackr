package com.example.trackr.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trackr.R
import com.example.trackr.models.Deadline
import com.example.trackr.utils.TimeFormatter

class DeadlineAdapter(
    private val deadlines: List<Deadline>,
    private val onClick: (Deadline) -> Unit,
    private val onToggleComplete: (Deadline, Boolean) -> Unit,
    private val onDelete: (Deadline) -> Unit
) : RecyclerView.Adapter<DeadlineAdapter.DeadlineViewHolder>() {

    inner class DeadlineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.titleText)
        val categoryText: TextView = view.findViewById(R.id.categoryText)
        val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        val remainingText: TextView = view.findViewById(R.id.remainingText)
        val completeCheckbox: CheckBox = view.findViewById(R.id.completeCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeadlineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deadline, parent, false)
        return DeadlineViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeadlineViewHolder, position: Int) {
        val deadline = deadlines[position]

        holder.titleText.text = deadline.title
        holder.categoryText.text = deadline.category
        holder.dateTimeText.text = TimeFormatter.formatDateTime(deadline.dateTimeInMillis)
        holder.remainingText.text = TimeFormatter.getTimeRemaining(deadline.dateTimeInMillis)
        holder.completeCheckbox.isChecked = deadline.isCompleted

        // Set strikethrough if completed
        if (deadline.isCompleted) {
            holder.titleText.paintFlags = holder.titleText.paintFlags or
                    android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.titleText.paintFlags = holder.titleText.paintFlags and
                    android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Color code based on time remaining
        val currentTime = System.currentTimeMillis()
        val timeRemaining = deadline.dateTimeInMillis - currentTime
        val color = when {
            deadline.isCompleted -> holder.itemView.context.getColor(android.R.color.darker_gray)
            timeRemaining < 0 -> holder.itemView.context.getColor(android.R.color.holo_red_dark)
            timeRemaining < 24 * 60 * 60 * 1000 -> holder.itemView.context.getColor(android.R.color.holo_orange_dark)
            else -> holder.itemView.context.getColor(android.R.color.holo_green_dark)
        }
        holder.remainingText.setTextColor(color)

        holder.itemView.setOnClickListener {
            onClick(deadline)
        }

        holder.completeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            onToggleComplete(deadline, isChecked)
        }
    }

    override fun getItemCount(): Int = deadlines.size
}