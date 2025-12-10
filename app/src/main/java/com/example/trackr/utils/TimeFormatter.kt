package com.example.trackr.utils

import java.text.SimpleDateFormat
import java.util.*

object TimeFormatter {
    fun formatTime(hour: Int, minute: Int): String {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format("%02d:%02d %s", h, minute, amPm)
    }

    fun formatDate(timeInMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timeInMillis))
    }

    fun formatDateTime(timeInMillis: Long): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(timeInMillis))
    }

    fun formatTimeOnly(timeInMillis: Long): String {
        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(timeInMillis))
    }

    fun getTimeRemaining(targetTimeMillis: Long): String {
        val currentTime = System.currentTimeMillis()
        val difference = targetTimeMillis - currentTime

        if (difference < 0) return "Overdue"

        val days = difference / (1000 * 60 * 60 * 24)
        val hours = (difference % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        val minutes = (difference % (1000 * 60 * 60)) / (1000 * 60)

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun formatTimerDisplay(milliseconds: Long): String {
        val hours = (milliseconds / (1000 * 60 * 60)) % 24
        val minutes = (milliseconds / (1000 * 60)) % 60
        val seconds = (milliseconds / 1000) % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}