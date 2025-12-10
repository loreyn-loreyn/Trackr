package com.example.trackr.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.trackr.R
import com.example.trackr.main.MainActivity
import com.example.trackr.models.User
import com.example.trackr.utils.FirebaseHelper
import com.example.trackr.utils.ValidationHelper
import com.example.trackr.utils.PhoneNumberValidator
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var fullNameInputLayout: TextInputLayout
    private lateinit var fullNameInput: TextInputEditText
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneInputLayout: TextInputLayout
    private lateinit var phoneInput: TextInputEditText
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var signUpButton: MaterialButton
    private lateinit var signInButton: MaterialButton
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        fullNameInputLayout = findViewById(R.id.fullNameInputLayout)
        fullNameInput = findViewById(R.id.fullNameInput)
        emailInputLayout = findViewById(R.id.emailInputLayout)
        emailInput = findViewById(R.id.emailInput)
        phoneInputLayout = findViewById(R.id.phoneInputLayout)
        phoneInput = findViewById(R.id.phoneInput)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        signUpButton = findViewById(R.id.signUpButton)
        signInButton = findViewById(R.id.signInButton)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        signUpButton.setOnClickListener {
            handleRegister()
        }

        signInButton.setOnClickListener {
            finish()
        }
    }

    private fun handleRegister() {
        // Clear all errors
        fullNameInputLayout.error = null
        emailInputLayout.error = null
        phoneInputLayout.error = null
        passwordInputLayout.error = null
        confirmPasswordInputLayout.error = null

        val fullName = fullNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()

        // Validation
        if (fullName.isEmpty()) {
            fullNameInputLayout.error = "Full name is required"
            return
        }

        if (!ValidationHelper.isValidName(fullName)) {
            fullNameInputLayout.error = "Please enter your full name"
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
            phoneInputLayout.error = "Please enter a valid PH (+639) number"
            return
        }

        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            return
        }

        if (!ValidationHelper.isValidPassword(password)) {
            passwordInputLayout.error = "Password must be at least 6 characters"
            return
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.error = "Please confirm your password"
            return
        }

        if (password != confirmPassword) {
            confirmPasswordInputLayout.error = "Passwords do not match"
            return
        }

        // Create user object
        val formattedPhone = PhoneNumberValidator.formatPhilippineNumber(phone)
        val user = User(
            fullName = fullName,
            email = email,
            phoneNumber = formattedPhone
        )

        // Show loading
        setLoading(true)

        // Register
        lifecycleScope.launch {
            val result = FirebaseHelper.registerUser(email, password, user)

            setLoading(false)

            result.onSuccess {
                navigateToMain()
            }.onFailure { exception ->
                handleRegistrationError(exception)
            }
        }
    }

    private fun handleRegistrationError(exception: Throwable) {
        val errorMessage = exception.message?.lowercase() ?: ""

        android.util.Log.e("RegisterActivity", "Registration error: ${exception.message}")

        when {
            // Email already in use - check multiple variations
            errorMessage.contains("already in use") ||
                    errorMessage.contains("email-already-in-use") ||
                    errorMessage.contains("email already exists") -> {
                emailInputLayout.error = "This email is already registered"
            }
            // Invalid email format
            errorMessage.contains("invalid-email") ||
                    errorMessage.contains("badly formatted") -> {
                emailInputLayout.error = "Please enter a valid email address"
            }
            // Weak password
            errorMessage.contains("weak-password") ||
                    errorMessage.contains("password should be at least") -> {
                passwordInputLayout.error = "Password is too weak"
            }
            // Network error
            errorMessage.contains("network") ||
                    errorMessage.contains("unable to resolve host") -> {
                emailInputLayout.error = "Network error. Check your connection"
            }
            // Default fallback
            else -> {
                emailInputLayout.error = "Registration failed: ${exception.message}"
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        signUpButton.isEnabled = !loading
        signInButton.isEnabled = !loading
        fullNameInput.isEnabled = !loading
        emailInput.isEnabled = !loading
        phoneInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        confirmPasswordInput.isEnabled = !loading
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}