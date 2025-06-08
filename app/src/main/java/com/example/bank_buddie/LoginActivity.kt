package com.vcsma.bank_buddie

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vcsma.bank_buddie.DashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vcsma.bank_buddie.R.*

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Get references to views
        val logoImage = findViewById<ImageView>(id.mascotImage)
        val titleText = findViewById<TextView>(id.titleText)
        val subtitleText = findViewById<TextView>(id.subtitleText)
        val loginButton = findViewById<Button>(id.loginButton)
        val signupButton = findViewById<Button>(id.signupButton)
        val forgotPassword = findViewById<TextView>(id.forgotPasswordText)

        emailEditText = findViewById(id.emailEditText)
        passwordEditText = findViewById(id.passwordEditText)

        // Set initial states for animation
        logoImage.translationY = -200f
        titleText.translationY = -200f
        subtitleText.translationY = -200f
        loginButton.alpha = 0f
        signupButton.alpha = 0f
        forgotPassword.alpha = 0f

        // Create animations
        val logoAnimation = ObjectAnimator.ofFloat(logoImage, "translationY", -200f, 0f).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
        }

        val titleAnimation = ObjectAnimator.ofFloat(titleText, "translationY", -200f, 0f).apply {
            duration = 1000
            startDelay = 300
            interpolator = DecelerateInterpolator()
        }

        val subtitleAnimation = ObjectAnimator.ofFloat(subtitleText, "translationY", -200f, 0f).apply {
            duration = 1000
            startDelay = 400
            interpolator = DecelerateInterpolator()
        }

        val loginButtonAnimation = ObjectAnimator.ofFloat(loginButton, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 800
        }

        val signupButtonAnimation = ObjectAnimator.ofFloat(signupButton, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 1000
        }

        val forgotPasswordAnimation = ObjectAnimator.ofFloat(forgotPassword, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 1200
        }

        // Play animations together
        AnimatorSet().apply {
            play(logoAnimation)
            play(titleAnimation).after(logoAnimation)
            play(subtitleAnimation).after(titleAnimation)
            play(loginButtonAnimation).after(subtitleAnimation)
            play(signupButtonAnimation).after(loginButtonAnimation)
            play(forgotPasswordAnimation).after(signupButtonAnimation)
            start()
        }

        // Set click listeners
        loginButton.setOnClickListener {
            // Add button press animation
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                // Validate and sign in
                signIn()
            }.start()
        }

        signupButton.setOnClickListener {
            // Add button press animation
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                // Navigate to registration
                val intent = Intent(this, CreateAccountActivity::class.java)
                startActivity(intent)
                overridePendingTransition(anim.slide_in_right, anim.slide_out_left)
            }.start()
        }

        forgotPassword.setOnClickListener {
            // Navigate to forgot password
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
            overridePendingTransition(anim.slide_in_right, anim.slide_out_left)
        }
    }

    private fun signIn() {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (!validateForm(email, password)) {
            return
        }

        // Show loading indicator
        findViewById<View>(id.progressBar).visibility = View.VISIBLE

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                findViewById<View>(id.progressBar).visibility = View.GONE

                if (task.isSuccessful) {
                    // Sign in success
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // Sign in failed
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }
    }

    private fun validateForm(email: String, password: String): Boolean {
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

        if (TextUtils.isEmpty(password)) {
            passwordEditText.error = "Required."
            valid = false
        } else if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters."
            valid = false
        } else {
            passwordEditText.error = null
        }

        return valid
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // Navigate to dashboard activity
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
