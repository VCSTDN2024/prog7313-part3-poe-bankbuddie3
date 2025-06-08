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
import com.vcsma.bank_buddie.DashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var fullNameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var mobileEditText: EditText
    private lateinit var dobEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var signUpButton: Button
    private lateinit var loginLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        fullNameEditText = findViewById(R.id.fullNameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        mobileEditText = findViewById(R.id.mobileEditText)
        dobEditText = findViewById(R.id.dobEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        signUpButton = findViewById(R.id.signUpButton)
        loginLink = findViewById(R.id.loginLink)

        // Set up date picker for DOB field
        dobEditText.setOnClickListener {
            showDatePickerDialog()
        }

        // Set up sign up button
        signUpButton.setOnClickListener {
            createAccount()
        }

        // Set up login link
        loginLink.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun showDatePickerDialog() {
        val datePickerFragment = DatePickerFragment { year, month, day ->
            // Format the date
            val selectedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Date(year - 1900, month, day))
            dobEditText.setText(selectedDate)
        }
        datePickerFragment.show(supportFragmentManager, "datePicker")
    }

    private fun createAccount() {
        try {
            val fullName = fullNameEditText.text.toString()
            val email = emailEditText.text.toString()
            val mobile = mobileEditText.text.toString()
            val dob = dobEditText.text.toString()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (!validateForm(fullName, email, mobile, dob, password, confirmPassword)) {
                return
            }

            // Show loading indicator
            findViewById<View>(R.id.progressBar).visibility = View.VISIBLE

            // Set a timeout to hide the progress bar if Firebase Auth takes too long
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (findViewById<View>(R.id.progressBar).visibility == View.VISIBLE) {
                    findViewById<View>(R.id.progressBar).visibility = View.GONE
                    Toast.makeText(baseContext, "Registration is taking longer than expected. Please try again.",
                        Toast.LENGTH_LONG).show()
                }
            }
            handler.postDelayed(timeoutRunnable, 10000) // 10 second timeout

            // Create user with email and password
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Remove the timeout handler
                    handler.removeCallbacks(timeoutRunnable)

                    if (task.isSuccessful) {
                        // Sign up success
                        Log.d(TAG, "createUserWithEmail:success")
                        val user = auth.currentUser

                        // Save additional user data to Firestore and navigate directly to dashboard
                        saveUserDataAndNavigate(user?.uid, fullName, email, mobile, dob)
                    } else {
                        // Sign up failed
                        findViewById<View>(R.id.progressBar).visibility = View.GONE
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        Toast.makeText(baseContext, "Registration failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            // Handle any unexpected exceptions
            findViewById<View>(R.id.progressBar).visibility = View.GONE
            Log.e(TAG, "Unexpected error in createAccount", e)
            Toast.makeText(baseContext, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveUserDataAndNavigate(userId: String?, fullName: String, email: String, mobile: String, dob: String) {
        // Always ensure we hide the progress bar eventually
        try {
            if (userId == null) {
                findViewById<View>(R.id.progressBar).visibility = View.GONE
                Toast.makeText(baseContext, "Registration error: User ID is null", Toast.LENGTH_SHORT).show()
                return
            }

            val user = hashMapOf(
                "fullName" to fullName,
                "email" to email,
                "mobile" to mobile,
                "dob" to dob,
                "createdAt" to System.currentTimeMillis()
            )

            // Set a timeout to hide the progress bar if Firestore takes too long
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (findViewById<View>(R.id.progressBar).visibility == View.VISIBLE) {
                    findViewById<View>(R.id.progressBar).visibility = View.GONE
                    Toast.makeText(baseContext, "Operation timed out, but registration was successful", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                }
            }
            handler.postDelayed(timeoutRunnable, 5000) // 5 second timeout

            db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener {
                    // Remove the timeout handler since we succeeded
                    handler.removeCallbacks(timeoutRunnable)
                    findViewById<View>(R.id.progressBar).visibility = View.GONE
                    Log.d(TAG, "User data saved successfully, navigating to dashboard")
                    Toast.makeText(baseContext, "Registration successful!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                }
                .addOnFailureListener { e ->
                    // Remove the timeout handler since we failed
                    handler.removeCallbacks(timeoutRunnable)
                    findViewById<View>(R.id.progressBar).visibility = View.GONE
                    Log.w(TAG, "Error saving user data", e)
                    Toast.makeText(baseContext, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Still navigate to dashboard since authentication was successful
                    navigateToDashboard()
                }
        } catch (e: Exception) {
            // Catch any unexpected exceptions
            findViewById<View>(R.id.progressBar).visibility = View.GONE
            Log.e(TAG, "Unexpected error in saveUserDataAndNavigate", e)
            Toast.makeText(baseContext, "An unexpected error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
            // Still try to navigate to dashboard
            navigateToDashboard()
        }
    }

    private fun navigateToDashboard() {
        try {
            val intent = Intent(this@CreateAccountActivity, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            Log.d(TAG, "Starting DashboardActivity")
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to dashboard", e)
            Toast.makeText(baseContext, "Error navigating to dashboard: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun validateForm(fullName: String, email: String, mobile: String, dob: String,
                             password: String, confirmPassword: String): Boolean {
        var valid = true

        if (TextUtils.isEmpty(fullName)) {
            fullNameEditText.error = "Required."
            valid = false
        } else {
            fullNameEditText.error = null
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.error = "Required."
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Invalid email."
            valid = false
        } else {
            emailEditText.error = null
        }

        if (TextUtils.isEmpty(mobile)) {
            mobileEditText.error = "Required."
            valid = false
        } else {
            mobileEditText.error = null
        }

        if (TextUtils.isEmpty(dob)) {
            dobEditText.error = "Required."
            valid = false
        } else {
            dobEditText.error = null
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.error = "Required."
            valid = false
        } else if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters."
            valid = false
        } else {
            passwordEditText.error = null
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordEditText.error = "Required."
            valid = false
        } else if (confirmPassword != password) {
            confirmPasswordEditText.error = "Passwords do not match."
            valid = false
        } else {
            confirmPasswordEditText.error = null
        }

        return valid
    }

    companion object {
        private const val TAG = "CreateAccountActivity"
    }
}
