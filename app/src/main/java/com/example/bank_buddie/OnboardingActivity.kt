package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.vcsma.bank_buddie.DashboardActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var nextButton: Button
    private lateinit var skipButton: TextView

    private val onboardingPages = listOf(
        OnboardingPage(
            "THE MEMBERS OF THE\nTECH TITANS\nWELCOMES YOU...",
            R.drawable.coins_hand
        ),
        OnboardingPage(
            "ARE YOU READY TO\nTAKE CONTROL OF\nYOUR FINANCES?",
            R.drawable.phone_hand
        ),
        OnboardingPage(
            "TRACK YOUR SPENDING\nAND SAVE MORE\nEFFECTIVELY",
            R.drawable.wallet_mascot
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "OnboardingActivity onCreate started")
            setContentView(R.layout.activity_onboarding)

            viewPager = findViewById(R.id.viewPager)
            tabLayout = findViewById(R.id.tabLayout)
            nextButton = findViewById(R.id.nextButton)
            skipButton = findViewById(R.id.skipButton)

            // Set up the adapter
            val adapter = OnboardingAdapter(this, onboardingPages)
            viewPager.adapter = adapter
            Log.d(TAG, "Adapter set on ViewPager")

            // Connect the tab layout with the view pager
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                // No title for tabs, just indicators
            }.attach()
            Log.d(TAG, "TabLayoutMediator attached")

            // Set up the next button
            nextButton.setOnClickListener {
                if (viewPager.currentItem < onboardingPages.size - 1) {
                    viewPager.currentItem = viewPager.currentItem + 1
                } else {
                    // Last page, go to login/registration
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }

            // Set up the skip button
            skipButton.setOnClickListener {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }

            // Update button text on last page
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (position == onboardingPages.size - 1) {
                        nextButton.text = getString(R.string.get_started)
                    } else {
                        nextButton.text = getString(R.string.next)
                    }
                }
            })

            // Apply page transformer for animation
            viewPager.setPageTransformer(OnboardingPageTransformer())
            Log.d(TAG, "OnboardingActivity onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in OnboardingActivity onCreate", e)
            Toast.makeText(this, "Error initializing onboarding: ${e.message}", Toast.LENGTH_LONG).show()
            // Fallback to dashboard if onboarding fails
            try {
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e2: Exception) {
                Log.e(TAG, "Error in fallback navigation", e2)
            }
        }
    }

    private fun finishOnboarding() {
        // Navigate to login/registration
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "OnboardingActivity"
    }
}

data class OnboardingPage(
    val title: String,
    val imageResId: Int
)
