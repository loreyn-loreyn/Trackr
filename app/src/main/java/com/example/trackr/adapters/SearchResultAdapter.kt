package com.example.trackr.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trackr.R
import com.example.trackr.models.Alarm
import com.example.trackr.models.Activity
import com.example.trackr.models.TimerSet
import com.example.trackr.utils.TimeFormatter

class SearchResultAdapter(
    private val results: List<Any>,
    private val onItemClick: (Any) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.resultTitle)
        val subtitleText: TextView = view.findViewById(R.id.resultSubtitle)
        val typeIcon: ImageView = view.findViewById(R.id.resultTypeIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val item = results[position]

        when (item) {
            is Alarm -> {
                holder.titleText.text = item.label
                holder.subtitleText.text = item.getDisplayTime()
                holder.typeIcon.setImageResource(R.drawable.ic_alarm)
            }
            is Activity -> {
                holder.titleText.text = item.title
                holder.subtitleText.text = TimeFormatter.formatDateTime(item.dateTimeInMillis)
                holder.typeIcon.setImageResource(R.drawable.ic_activity)
            }
            is TimerSet -> {
                holder.titleText.text = item.label
                holder.subtitleText.text = item.getDisplayDuration()
                holder.typeIcon.setImageResource(R.drawable.ic_timer)
            }
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = results.size
}