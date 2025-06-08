package com.vcsma.bank_buddie

import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(
    private val context: Context,
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.onboarding_page_item,
            parent,
            false
        )
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val page = pages[position]
        holder.bind(page)
    }

    override fun getItemCount(): Int = pages.size

    inner class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.titleText)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val logoImageView: ImageView = itemView.findViewById(R.id.logoImageView)
        private val subtitleTextView: TextView = itemView.findViewById(R.id.subtitleText)

        fun bind(page: OnboardingPage) {
            // Set the title and image
            titleTextView.text = page.title
            imageView.setImageResource(page.imageResId)

            // Animate the elements when they're bound
            animateItems()
        }

        private fun animateItems() {
            // Reset alpha and translation for animations
            titleTextView.alpha = 0f
            imageView.alpha = 0f
            logoImageView.alpha = 0f
            subtitleTextView.alpha = 0f

            // Logo animation
            ObjectAnimator.ofFloat(logoImageView, "alpha", 0f, 1f).apply {
                duration = 500
                start()
            }

            // Subtitle animation
            ObjectAnimator.ofFloat(subtitleTextView, "alpha", 0f, 1f).apply {
                duration = 500
                startDelay = 100
                start()
            }

            // Title animation
            ObjectAnimator.ofFloat(titleTextView, "alpha", 0f, 1f).apply {
                duration = 800
                startDelay = 300
                start()
            }

            // Image animation - slide up and fade in
            imageView.translationY = 100f
            ObjectAnimator.ofFloat(imageView, "translationY", 100f, 0f).apply {
                duration = 800
                interpolator = DecelerateInterpolator()
                startDelay = 500
                start()
            }

            ObjectAnimator.ofFloat(imageView, "alpha", 0f, 1f).apply {
                duration = 800
                startDelay = 500
                start()
            }
        }
    }
}
