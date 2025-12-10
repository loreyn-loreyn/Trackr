package com.example.trackr.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Activity(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Work",
    val dateTimeInMillis: Long = 0,
    val isCompleted: Boolean = false,
    val completedAt: Long = 0, // Timestamp when activity was marked as completed
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    constructor() : this("", "", "", "", "Work", 0, false, 0, 0L)

    fun toMap(): HashMap<String, Any> {
        return hashMapOf(
            "id" to id,
            "userId" to userId,
            "title" to title,
            "description" to description,
            "category" to category,
            "dateTimeInMillis" to dateTimeInMillis,
            "isCompleted" to isCompleted,
            "completedAt" to completedAt,
            "createdAt" to createdAt
        )
    }
}