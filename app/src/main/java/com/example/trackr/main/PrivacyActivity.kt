package com.example.trackr.main

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.trackr.R

class PrivacyActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        // Hide default action bar since we're using custom toolbar
        supportActionBar?.hide()

        initViews()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }
    }
}