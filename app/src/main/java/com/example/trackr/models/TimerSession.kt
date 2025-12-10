package com.example.trackr.models

data class TimerSession(
    val id: String = "",
    val userId: String = "",
    val duration: Long = 0, // in milliseconds
    val label: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0
) {
    constructor() : this("", "", 0, "", 0L, 0L)

    fun toMap(): HashMap<String, Any> {
        return hashMapOf(
            "id" to id,
            "userId" to userId,
            "duration" to duration,
            "label" to label,
            "startTime" to startTime,
            "endTime" to endTime
        )
    }
}