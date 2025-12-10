package com.example.trackr.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.trackr.R
import com.example.trackr.auth.LoginActivity
import com.example.trackr.utils.FirebaseHelper
import com.example.trackr.utils.ValidationHelper
import com.example.trackr.utils.PhoneNumberValidator
import kotlinx.coroutines.launch

class AccountActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneInputLayout: TextInputLayout
    private lateinit var phoneInput: TextInputEditText
    private lateinit var recoveryEmailInputLayout: TextInputLayout
    private lateinit var recoveryEmailInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var changePasswordCard: CardView
    private lateinit var deleteAccountButton: MaterialButton
    private lateinit var progressBar: View

    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        supportActionBar?.hide()

        initViews()
        loadUserData()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        nameInputLayout = findViewById(R.id.nameInputLayout)
        nameInput = findViewById(R.id.nameInput)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        emailInput = findViewById(R.id.emailInput)
        phoneInputLayout = findViewById(R.id.phoneInputLayout)
        phoneInput = findViewById(R.id.phoneInput)
        recoveryEmailInputLayout = findViewById(R.id.recoveryEmailInputLayout)
        recoveryEmailInput = findViewById(R.id.recoveryEmailInput)
        saveButton = findViewById(R.id.saveButton)
        changePasswordCard = findViewById(R.id.changePasswordCard)
        deleteAccountButton = findViewById(R.id.deleteAccountButton)
        progressBar = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { handleSave() }
        changePasswordCard.setOnClickListener { showChangePasswordDialog() }
        deleteAccountButton.setOnClickListener { confirmDeleteAccount() }
    }

    private fun loadUserData() {
        userId = FirebaseHelper.getCurrentUserId() ?: return

        setLoading(true)

        lifecycleScope.launch {
            val result = FirebaseHelper.getUserData(userId)

            setLoading(false)

            result.onSuccess { user ->
                nameInput.setText(user.fullName)
                emailInput.setText(user.email)
                phoneInput.setText(user.phoneNumber)
            }
        }
    }

    private fun handleSave() {
        nameInputLayout.error = null
        emailInputLayout.error = null
        phoneInputLayout.error = null
        recoveryEmailInputLayout.error = null

        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val recoveryEmail = recoveryEmailInput.text.toString().trim()

        if (name.isEmpty()) {
            nameInputLayout.error = "Name is required"
            return
        }

        if (!ValidationHelper.isValidName(name)) {
            nameInputLayout.error = "Please enter a valid name"
            return
        }

        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            return
        }

        if (!ValidationHelper.isValidEmail(email)) {
            emailInputLayout.error = "Please enter a valid email address"
            return
        }

        if (phone.isEmpty()) {
            phoneInputLayout.error = "Phone number is required"
            return
        }

        if (!ValidationHelper.isValidPhoneNumber(phone)) {
            phoneInputLayout.error = "Please enter a valid PH number (09XX-XXX-XXXX)"
            return
        }

        if (recoveryEmail.isNotEmpty() && !ValidationHelper.isValidEmail(recoveryEmail)) {
            recoveryEmailInputLayout.error = "Please enter a valid recovery email"
            return
        }

        val formattedPhone = PhoneNumberValidator.formatPhilippineNumber(phone)

        val updates = hashMapOf<String, Any>(
            "fullName" to name,
            "email" to email,
            "phoneNumber" to formattedPhone,
            "recoveryEmail" to recoveryEmail
        )

        setLoading(true)

        lifecycleScope.launch {
            val result = FirebaseHelper.updateUserProfile(userId, updates)

            setLoading(false)

            result.onSuccess {
                showSnackbar("Profile updated successfully!")
            }.onFailure {
                showSnackbar("Failed to update profile")
            }
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordLayout = dialogView.findViewById<TextInputLayout>(R.id.currentPasswordInputLayout)
        val currentPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.currentPasswordInput)
        val newPasswordLayout = dialogView.findViewById<TextInputLayout>(R.id.newPasswordInputLayout)
        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.newPasswordInput)
        val confirmPasswordLayout = dialogView.findViewById<TextInputLayout>(R.id.confirmPasswordInputLayout)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordInput)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change") { _, _ ->
                currentPasswordLayout.error = null
                newPasswordLayout.error = null
                confirmPasswordLayout.error = null

                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (currentPassword.isEmpty()) {
                    showSnackbar("Please enter current password")
                    return@setPositiveButton
                }

                if (newPassword.length < 6) {
                    showSnackbar("Password must be at least 6 characters")
                    return@setPositiveButton
                }

                if (newPassword != confirmPassword) {
                    showSnackbar("Passwords do not match")
                    return@setPositiveButton
                }

                setLoading(true)

                lifecycleScope.launch {
                    val result = FirebaseHelper.changePassword(currentPassword, newPassword)

                    setLoading(false)

                    result.onSuccess {
                        showSnackbar("Password changed successfully!")
                    }.onFailure { exception ->
                        val errorMsg = if (exception.message?.contains("password", ignoreCase = true) == true) {
                            "Current password is incorrect"
                        } else {
                            "Failed to change password"
                        }
                        showSnackbar(errorMsg)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAccount() {
        // First confirmation dialog - simple warning
        AlertDialog.Builder(this)
            .setTitle("⚠️ Delete Account?")
            .setMessage("Are you sure you want to delete your account? This action is permanent and cannot be undone.")
            .setPositiveButton("Continue") { _, _ ->
                // Show the detailed password confirmation dialog
                showPasswordConfirmationDialog()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(true)
            .show()
    }

    private fun showPasswordConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account_confirmation, null)
        val passwordInputLayout = dialogView.findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordInput)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Delete Everything") { _, _ ->
                passwordInputLayout.error = null
                val password = passwordInput.text.toString()

                if (password.isEmpty()) {
                    showSnackbar("Password is required to delete account")
                    // Show the dialog again if password is empty
                    showPasswordConfirmationDialog()
                    return@setPositiveButton
                }

                deleteAccountWithPassword(password)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(true)
            .create()

        dialog.show()

        // Style the buttons after showing (makes them stand out better)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            textSize = 14f
            isAllCaps = false
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(resources.getColor(R.color.teal_primary, null))
            textSize = 14f
            isAllCaps = false
        }

        // Focus on password input and show keyboard
        passwordInput.requestFocus()
    }

    private fun deleteAccountWithPassword(password: String) {
        setLoading(true)

        lifecycleScope.launch {
            val result = FirebaseHelper.deleteUserAccountWithReauth(password)

            setLoading(false)

            result.onSuccess {
                showSnackbar("Account deleted successfully")
                navigateToLogin()
            }.onFailure { exception ->
                val errorMsg = when {
                    exception.message?.contains("Incorrect password", ignoreCase = true) == true ->
                        "❌ Incorrect password. Please try again."
                    exception.message?.contains("Authentication failed", ignoreCase = true) == true ->
                        "❌ Authentication failed. Please try again."
                    exception.message?.contains("network", ignoreCase = true) == true ->
                        "❌ Network error. Check your connection and try again."
                    else ->
                        "❌ Failed to delete account: ${exception.message}"
                }
                showSnackbar(errorMsg)

                // If password is incorrect, show the dialog again
                if (exception.message?.contains("Incorrect password", ignoreCase = true) == true) {
                    showPasswordConfirmationDialog()
                }
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        saveButton.isEnabled = !loading
        changePasswordCard.isClickable = !loading
        deleteAccountButton.isEnabled = !loading
        nameInput.isEnabled = !loading
        emailInput.isEnabled = !loading
        phoneInput.isEnabled = !loading
        recoveryEmailInput.isEnabled = !loading
    }
}