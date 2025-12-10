package com.example.trackr.utils

object PhoneNumberValidator {
    fun isValidPhilippineNumber(phoneNumber: String): Boolean {
        val cleaned = phoneNumber.replace("[\\s-]".toRegex(), "")

        return when {
            cleaned.matches("^09[0-9]{9}$".toRegex()) -> true
            cleaned.matches("^\\+639[0-9]{9}$".toRegex()) -> true
            cleaned.matches("^639[0-9]{9}$".toRegex()) -> true
            else -> false
        }
    }

    fun formatPhilippineNumber(phoneNumber: String): String {
        val cleaned = phoneNumber.replace("[\\s-]".toRegex(), "")

        return when {
            cleaned.startsWith("09") -> "+63${cleaned.substring(1)}"
            cleaned.startsWith("639") -> "+$cleaned"
            cleaned.startsWith("+639") -> cleaned
            else -> phoneNumber
        }
    }
}