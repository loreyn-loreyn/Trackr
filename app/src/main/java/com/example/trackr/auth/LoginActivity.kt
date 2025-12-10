package com.example.trackr.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.trackr.R
import com.example.trackr.main.MainActivity
import com.example.trackr.utils.FirebaseHelper
import com.example.trackr.utils.ValidationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var signInButton: MaterialButton
    private lateinit var signUpButton: MaterialButton
    private lateinit var progressBar: View

    private var loginAttempts = 0
    private var lastAttemptTime = 0L
    private val MAX_ATTEMPTS = 5
    private val LOCKOUT_DURATION = 60000L // 1 minute

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        emailInputLayout = findViewById(R.id.emailInputLayout)
        emailInput = findViewById(R.id.emailInput)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        passwordInput = findViewById(R.id.passwordInput)
        signInButton = findViewById(R.id.signInButton)
        signUpButton = findViewById(R.id.signUpButton)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        signInButton.setOnClickListener {
            handleSignIn()
        }

        signUpButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Clear errors when user starts typing
        emailInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) emailInputLayout.error = null
        }

        passwordInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) passwordInputLayout.error = null
        }
    }

    private fun handleSignIn() {
        // Check for rate limiting
        if (isLockedOut()) {
            val remainingTime = (LOCKOUT_DURATION - (System.currentTimeMillis() - lastAttemptTime)) / 1000
            showSnackbar("Too many failed attempts. Please try again in $remainingTime seconds")
            return
        }

        // Clear previous errors
        emailInputLayout.error = null
        passwordInputLayout.error = null

        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Client-side validation
        if (!validateInput(email, password)) {
            return
        }

        // Show loading
        setLoading(true)

        // Attempt login
        lifecycleScope.launch {
            try {
                android.util.Log.d("LoginActivity", "=== LOGIN ATTEMPT ===")
                android.util.Log.d("LoginActivity", "Email: $email")

                // First, check if account exists in database
                val accountExists = FirebaseHelper.checkAccountExistsInDatabase(email)

                if (!accountExists) {
                    android.util.Log.d("LoginActivity", "Account does not exist in database")
                    setLoading(false)
                    handleLoginFailure()
                    emailInputLayout.error = "No account found with this email"
                    return@launch
                }

                android.util.Log.d("LoginActivity", "Account exists, attempting login...")

                // Proceed with login
                val result = FirebaseHelper.loginUser(email, password)

                setLoading(false)

                result.onSuccess {
                    android.util.Log.d("LoginActivity", "✓ Login successful!")
                    loginAttempts = 0 // Reset attempts on success
                    navigateToMain()
                }.onFailure { exception ->
                    android.util.Log.e("LoginActivity", "✗ Login failed: ${exception.message}")
                    handleLoginFailure()
                    handleLoginError(exception)
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginActivity", "Unexpected error: ${e.message}", e)
                setLoading(false)
                handleLoginFailure()
                showSnackbar("An unexpected error occurred. Please try again")
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        // Validate email
        when {
            email.isEmpty() -> {
                emailInputLayout.error = "Email is required"
                isValid = false
            }
            !ValidationHelper.isValidEmail(email) -> {
                emailInputLayout.error = "Please enter a valid email address"
                isValid = false
            }
        }

        // Validate password
        when {
            password.isEmpty() -> {
                passwordInputLayout.error = "Password is required"
                isValid = false
            }
            password.length < 6 -> {
                passwordInputLayout.error = "Password must be at least 6 characters"
                isValid = false
            }
        }

        return isValid
    }

    private fun handleLoginError(exception: Throwable) {
        val errorMessage = exception.message?.lowercase() ?: ""

        when {
            // Wrong password or invalid credentials
            errorMessage.contains("password") ||
                    errorMessage.contains("wrong-password") ||
                    errorMessage.contains("invalid-credential") -> {
                passwordInputLayout.error = "Incorrect password"
                showSnackbar("Incorrect password. Please try again")
            }

            // Account disabled
            errorMessage.contains("disabled") ||
                    errorMessage.contains("user-disabled") -> {
                emailInputLayout.error = "This account has been disabled"
                showSnackbar("Account disabled. Please contact support")
            }

            // Too many requests
            errorMessage.contains("too-many-requests") -> {
                showSnackbar("Too many login attempts. Please try again later")
                emailInputLayout.error = "Account temporarily locked"
            }

            // Network error
            errorMessage.contains("network") ||
                    errorMessage.contains("unable to resolve host") ||
                    errorMessage.contains("timeout") -> {
                showSnackbar("Network error. Please check your connection")
                emailInputLayout.error = "Connection failed"
            }

            // User not found (backup check)
            errorMessage.contains("user") ||
                    errorMessage.contains("user-not-found") ||
                    errorMessage.contains("no user") ||
                    errorMessage.contains("no record") -> {
                emailInputLayout.error = "No account found with this email"
            }

            // Default fallback
            else -> {
                passwordInputLayout.error = "Login failed"
                showSnackbar("Unable to sign in. Please check your credentials")
                android.util.Log.e("LoginActivity", "Unhandled error: $errorMessage")
            }
        }
    }

    private fun handleLoginFailure() {
        loginAttempts++
        lastAttemptTime = System.currentTimeMillis()

        if (loginAttempts >= MAX_ATTEMPTS) {
            showSnackbar("Maximum login attempts reached. Please wait before trying again")
        } else {
            val remainingAttempts = MAX_ATTEMPTS - loginAttempts
            if (remainingAttempts <= 2) {
                showSnackbar("$remainingAttempts attempt(s) remaining")
            }
        }
    }

    private fun isLockedOut(): Boolean {
        if (loginAttempts >= MAX_ATTEMPTS) {
            val timeSinceLastAttempt = System.currentTimeMillis() - lastAttemptTime
            if (timeSinceLastAttempt < LOCKOUT_DURATION) {
                return true
            } else {
                // Reset after lockout period
                loginAttempts = 0
            }
        }
        return false
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        signInButton.isEnabled = !loading
        signUpButton.isEnabled = !loading
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading

        if (loading) {
            signInButton.text = "Signing in..."
        } else {
            signInButton.text = "Sign In"
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onPause() {
        super.onPause()
        // Clear sensitive data when app goes to background
        if (isFinishing) {
            passwordInput.text?.clear()
        }
    }
}