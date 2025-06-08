package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var nextButton: Button
    private lateinit var signUpLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText)
        nextButton = findViewById(R.id.nextButton)
        signUpLink = findViewById(R.id.signUpLink)

        // Set up next button
        nextButton.setOnClickListener {
            resetPassword()
        }

        // Set up sign up link
        signUpLink.setOnClickListener {
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun resetPassword() {
        val email = emailEditText.text.toString()

        if (!validateForm(email)) {
            return
        }

        // Show loading indicator
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                findViewById<View>(R.id.progressBar).visibility = View.GONE

                if (task.isSuccessful) {
                    Log.d(TAG, "Password reset email sent.")

                    // Navigate to reset success screen
                    val intent = Intent(this, ResetSuccessActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.w(TAG, "Failed to send reset email.", task.exception)
                    Toast.makeText(baseContext, "Failed to send reset email: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun validateForm(email: String): Boolean {
        var valid = true

        if (TextUtils.isEmpty(email)) {
            emailEditText.error = "Required."
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Invalid email."
            valid = false
        } else {
            emailEditText.error = null
        }

        return valid
    }

    companion object {
        private const val TAG = "ForgotPasswordActivity"
    }
}
