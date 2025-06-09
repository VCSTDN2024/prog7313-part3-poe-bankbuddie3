package com.vcsma.bank_buddie

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.vcsma.bank_buddie.R.*

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Sign out any existing user to ensure login is required
        auth.signOut()

        // Get references to views
        val logoImage = findViewById<ImageView>(R.id.mascotImage)
        val titleText = findViewById<TextView>(R.id.titleText)
        val subtitleText = findViewById<TextView>(R.id.subtitleText)

        // Set initial alpha to 0 (invisible)
        logoImage.alpha = 0f
        titleText.alpha = 0f
        subtitleText.alpha = 0f

        // Create animations
        val logoAnimation = ObjectAnimator.ofFloat(logoImage, "alpha", 0f, 1f).apply {
            duration = 1000
            interpolator = AnticipateOvershootInterpolator()
        }

        val bounceAnimation = ObjectAnimator.ofFloat(logoImage, "translationY", -30f, 0f).apply {
            duration = 1000
            repeatCount = 3
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val titleAnimation = ObjectAnimator.ofFloat(titleText, "alpha", 0f, 1f).apply {
            duration = 800
            startDelay = 500
        }

        val subtitleAnimation = ObjectAnimator.ofFloat(subtitleText, "alpha", 0f, 1f).apply {
            duration = 800
            startDelay = 800
        }

        // Play animations together
        AnimatorSet().apply {
            play(logoAnimation).before(bounceAnimation)
            play(titleAnimation).after(logoAnimation)
            play(subtitleAnimation).after(titleAnimation)
            start()
        }

        // Navigate to onboarding screen after delay
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }, 3500)
    }
}
