package com.example.trackr.models

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@Parcelize
@IgnoreExtraProperties
data class TimerSet(
    var id: String = "",
    var userId: String = "",
    var label: String = "",
    var durationMillis: Long = 0,
    var isActive: Boolean = false,
    var isPaused: Boolean = false,
    var remainingMillis: Long = 0,
    var startTimestamp: Long = 0L
) : Parcelable {
    // No-argument constructor required by Firestore
    constructor() : this("", "", "", 0, false, false, 0, 0L)

    fun toMap(): HashMap<String, Any> {
        return hashMapOf(
            "id" to id,
            "userId" to userId,
            "label" to label,
            "durationMillis" to durationMillis,
            "isActive" to isActive,
            "isPaused" to isPaused,
            "remainingMillis" to remainingMillis,
            "startTimestamp" to startTimestamp
        )
    }

    @Exclude
    fun getDisplayDuration(): String {
        val hours = (durationMillis / (1000 * 60 * 60)) % 24
        val minutes = (durationMillis % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (durationMillis % (1000 * 60)) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}