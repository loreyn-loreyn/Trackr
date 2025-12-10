package com.example.trackr.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.trackr.R
import com.example.trackr.models.Alarm

class AlarmAdapter(
    private val alarms: MutableList<Alarm>,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onLongPress: (Alarm) -> Unit,
    private val onClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    inner class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.timeText)
        val labelText: TextView = view.findViewById(R.id.labelText)
        val repeatText: TextView = view.findViewById(R.id.repeatText)
        val enableSwitch: Switch = view.findViewById(R.id.enableSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        if (position >= alarms.size) return

        val alarm = alarms[position]

        // Debug log
        android.util.Log.d("AlarmAdapter", "Binding alarm at position $position: ${alarm.label}, ${alarm.hour}:${alarm.minute}")

        holder.timeText.text = alarm.getDisplayTime()
        holder.labelText.text = alarm.label.ifEmpty { "Alarm" }
        holder.repeatText.text = alarm.getDisplayRepeat() // Use the new method

        // Make sure views are visible
        holder.timeText.visibility = View.VISIBLE
        holder.labelText.visibility = View.VISIBLE
        holder.repeatText.visibility = View.VISIBLE
        holder.enableSwitch.visibility = View.VISIBLE

        // Remove listener before setting checked state to avoid triggering it
        holder.enableSwitch.setOnCheckedChangeListener(null)
        holder.enableSwitch.isChecked = alarm.isEnabled

        // Set text colors and alpha based on enabled state
        if (alarm.isEnabled) {
            holder.timeText.alpha = 1.0f
            holder.labelText.alpha = 1.0f
            holder.repeatText.alpha = 1.0f
        } else {
            holder.timeText.alpha = 0.5f
            holder.labelText.alpha = 0.5f
            holder.repeatText.alpha = 0.5f
        }

        // Set listener after setting checked state
        holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggle(alarm, isChecked)
        }

        // Regular tap - show info
        holder.itemView.setOnClickListener {
            onClick(alarm)
        }

        // Long press to edit/delete
        holder.itemView.setOnLongClickListener {
            onLongPress(alarm)
            true
        }
    }

    override fun getItemCount(): Int = alarms.size
}