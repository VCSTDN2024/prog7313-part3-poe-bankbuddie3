package com.vcsma.bank_buddie

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class CategorySummaryAdapter(
    private val categories: List<CategorySummary>,
    private val onItemClick: (CategorySummary) -> Unit
) : RecyclerView.Adapter<CategorySummaryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryNameText: TextView = view.findViewById(R.id.categoryNameText)
        val amountText: TextView = view.findViewById(R.id.amountText)
        val countText: TextView = view.findViewById(R.id.countText)
        val percentProgressBar: ProgressBar = view.findViewById(R.id.percentProgressBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_summary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]

        holder.categoryNameText.text = category.categoryName
        holder.amountText.text = holder.itemView.context.getString(
            R.string.currency_value_negative,
            category.totalAmount.toFloat()
        )
        holder.countText.text = "${category.expenseCount} expense${if (category.expenseCount > 1) "s" else ""}"

        // Calculate and set progress
        val totalAmount = categories.sumOf { it.totalAmount }
        val percentage = if (totalAmount > 0) {
            ((category.totalAmount / totalAmount) * 100).toInt()
        } else {
            0
        }
        holder.percentProgressBar.progress = percentage

        // Setup click listener
        holder.itemView.setOnClickListener {
            onItemClick(category)
        }
    }

    override fun getItemCount() = categories.size
}