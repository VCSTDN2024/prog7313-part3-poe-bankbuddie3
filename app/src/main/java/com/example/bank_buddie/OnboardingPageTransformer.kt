package com.vcsma.bank_buddie

import android.view.View
import androidx.viewpager2.widget.ViewPager2

class OnboardingPageTransformer : ViewPager2.PageTransformer {
    private val MIN_SCALE = 0.85f
    private val MIN_ALPHA = 0.5f

    override fun transformPage(view: View, position: Float) {
        val pageWidth = view.width
        val pageHeight = view.height

        when {
            // Page is off-screen to the left
            position < -1 -> {
                view.alpha = 0f
            }
            // Page is transitioning off-screen to the left or coming in from the right
            position <= 1 -> {
                // Modify the default slide transition to shrink the page as well
                val scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position))
                val vertMargin = pageHeight * (1 - scaleFactor) / 2
                val horzMargin = pageWidth * (1 - scaleFactor) / 2

                // Center vertically
                if (position < 0) {
                    view.translationX = horzMargin - vertMargin / 2
                } else {
                    view.translationX = -horzMargin + vertMargin / 2
                }

                // Scale the page down (between MIN_SCALE and 1)
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor

                // Fade the page relative to its size.
                view.alpha = MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA)
            }
            // Page is off-screen to the right
            else -> {
                view.alpha = 0f
            }
        }
    }
}
