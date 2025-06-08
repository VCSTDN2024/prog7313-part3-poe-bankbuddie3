package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class NewPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var changePasswordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_password)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        changePasswordButton = findViewById(R.id.changePasswordButton)

        // Set up change password button
        changePasswordButton.setOnClickListener {
            changePassword()
        }
    }

    private fun changePassword() {
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        if (!validateForm(password, confirmPassword)) {
            return
        }

        // Show loading indicator
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE

        // Get current user
        val user = auth.currentUser
        if (user == null) {
            // No user is signed in
            findViewById<View>(R.id.progressBar).visibility = View.GONE
            Toast.makeText(this, "Authentication error. Please sign in again.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        // Check if we have the old password from intent
        val oldPassword = intent.getStringExtra("oldPassword")

        if (oldPassword != null) {
            // Re-authenticate before changing password
            val credential = EmailAuthProvider.getCredential(user.email ?: "", oldPassword)

            user.reauthenticate(credential)
                .addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        // User re-authenticated, now change password
                        updatePassword(user.uid, password)
                    } else {
                        // Re-authentication failed
                        findViewById<View>(R.id.progressBar).visibility = View.GONE
                        Log.w(TAG, "reauthenticate:failure", reauthTask.exception)
                        Toast.makeText(baseContext, "Re-authentication failed: ${reauthTask.exception?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            // No old password, just update password (e.g., after reset email)
            updatePassword(user.uid, password)
        }
    }

    private fun updatePassword(userId: String, newPassword: String) {
        val user = auth.currentUser

        user?.updatePassword(newPassword)
            ?.addOnCompleteListener { task ->
                findViewById<View>(R.id.progressBar).visibility = View.GONE

                if (task.isSuccessful) {
                    Log.d(TAG, "Password updated successfully")

                    // Navigate to success screen
                    val intent = Intent(this, PasswordChangedActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.w(TAG, "updatePassword:failure", task.exception)
                    Toast.makeText(baseContext, "Failed to update password: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun validateForm(password: String, confirmPassword: String): Boolean {
        var valid = true

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

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "NewPasswordActivity"
    }
}
