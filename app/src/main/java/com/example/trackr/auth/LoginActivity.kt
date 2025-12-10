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
import com.example.trackr.utils.FirebaseHelper
import com.example.trackr.utils.ValidationHelper
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var signInButton: MaterialButton
    private lateinit var signUpButton: MaterialButton
    private lateinit var progressBar: View

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
    }

    private fun handleSignIn() {
        // Clear previous errors
        emailInputLayout.error = null
        passwordInputLayout.error = null

        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Validation
        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            return
        }

        if (!ValidationHelper.isValidEmail(email)) {
            emailInputLayout.error = "Invalid email address"
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

        // Show loading
        setLoading(true)

        // Login with automatic password reset handling
        lifecycleScope.launch {
            android.util.Log.d("LoginActivity", "=== LOGIN ATTEMPT ===")
            android.util.Log.d("LoginActivity", "Email: $email")

            // Normal login (no pending reset)
            android.util.Log.d("LoginActivity", "Normal login (no pending reset)")
            val result = FirebaseHelper.loginUser(email, password)

            setLoading(false)

            result.onSuccess {
                android.util.Log.d("LoginActivity", "âœ“ Login successful!")
                navigateToMain()
            }.onFailure { exception ->
                android.util.Log.e("LoginActivity", "âœ— Login failed: ${exception.message}")
                handleLoginError(exception)
            }
        }
    }

    private fun handleLoginError(exception: Throwable) {
        val errorMessage = exception.message?.lowercase() ?: ""

        when {
            // Account doesn't exist - PRIORITY CHECK
            errorMessage.contains("user") ||
                    errorMessage.contains("no user") ||
                    errorMessage.contains("user-not-found") ||
                    errorMessage.contains("no record") -> {
                emailInputLayout.error = "Account does not exist"
            }
            // Wrong password
            errorMessage.contains("password") ||
                    errorMessage.contains("wrong-password") -> {
                passwordInputLayout.error = "Incorrect password"
            }
            // Invalid credentials (could be either wrong email or password)
            errorMessage.contains("invalid-credential") -> {
                // Firebase now uses "invalid-credential" for both cases
                // We'll default to showing it as password error
                passwordInputLayout.error = "Incorrect password"
            }
            // Account disabled
            errorMessage.contains("disabled") -> {
                emailInputLayout.error = "This account has been disabled"
            }
            // Network error
            errorMessage.contains("network") -> {
                emailInputLayout.error = "Network error. Check your connection"
            }
            // Default fallback - assume password error
            else -> {
                passwordInputLayout.error = "Incorrect password"
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        signInButton.isEnabled = !loading
        signUpButton.isEnabled = !loading
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
