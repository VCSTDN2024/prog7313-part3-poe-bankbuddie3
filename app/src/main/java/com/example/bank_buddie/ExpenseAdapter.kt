package com.vcsma.bank_buddie

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExpenseAdapter(
    private val expenses: List<ExpenseEntry>,
    private val onExpenseClick: (ExpenseEntry) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryIcon: ImageView = view.findViewById(R.id.categoryIcon)
        val descriptionText: TextView = view.findViewById(R.id.descriptionText)
        val categoryText: TextView = view.findViewById(R.id.categoryText)
        val dateText: TextView = view.findViewById(R.id.dateText)
        val amountText: TextView = view.findViewById(R.id.amountText)
        val photoIndicator: ImageView = view.findViewById(R.id.photoIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]

        holder.descriptionText.text = expense.description
        holder.categoryText.text = expense.categoryName
        holder.dateText.text = expense.date
        holder.amountText.text = holder.itemView.context.getString(
            R.string.currency_value_negative,
            Math.abs(expense.amount).toFloat()
        )

        // Set category icon
        holder.categoryIcon.setImageResource(getCategoryIcon(expense.categoryName))

        // Show photo indicator if expense has photo
        holder.photoIndicator.visibility = if (expense.hasPhoto) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onExpenseClick(expense)
        }
    }

    private fun getCategoryIcon(categoryName: String): Int {
        // Map category names to icon resources
        return when (categoryName.lowercase()) {
            "food" -> R.drawable.ic_category_food
            "transport" -> R.drawable.ic_category_transport
            "shopping" -> R.drawable.ic_category_groceries
            "bills" -> R.drawable.ic_category_rent
            "entertainment" -> R.drawable.ic_category_entertainment
            else -> R.drawable.ic_category_default
        }
    }

    override fun getItemCount() = expenses.size
}