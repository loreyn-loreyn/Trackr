package com.example.trackr.models

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", 0L)

    fun toMap(): HashMap<String, Any> {
        return hashMapOf(
            "uid" to uid,
            "fullName" to fullName,
            "email" to email,
            "phoneNumber" to phoneNumber,
            "createdAt" to createdAt
        )
    }
}