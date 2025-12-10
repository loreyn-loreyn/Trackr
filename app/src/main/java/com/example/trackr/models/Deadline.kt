package com.example.trackr.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Deadline(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Work", // Work, Personal, School, Other
    val dateTimeInMillis: Long = 0,
    val notifyBefore: Int = 30, // minutes
    val isCompleted: Boolean = false,
    val collaborators: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    constructor() : this("", "", "", "", "Work", 0, 30, false, emptyList(), 0L)

    fun toMap(): HashMap<String, Any> {
        return hashMapOf(
            "id" to id,
            "userId" to userId,
            "title" to title,
            "description" to description,
            "category" to category,
            "dateTimeInMillis" to dateTimeInMillis,
            "notifyBefore" to notifyBefore,
            "isCompleted" to isCompleted,
            "collaborators" to collaborators,
            "createdAt" to createdAt
        )
    }
}