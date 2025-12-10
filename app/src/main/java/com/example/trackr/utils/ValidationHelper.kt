package com.example.trackr.utils

object ValidationHelper {
    fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return email.matches(emailPattern.toRegex())
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun isValidName(name: String): Boolean {
        return name.trim().length >= 2
    }

    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return PhoneNumberValidator.isValidPhilippineNumber(phoneNumber)
    }

    fun isEmailFormat(input: String): Boolean {
        return input.contains("@")
    }
}