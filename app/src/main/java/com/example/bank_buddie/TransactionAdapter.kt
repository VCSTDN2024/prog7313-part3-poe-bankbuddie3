package com.vcsma.bank_buddie

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * A single adapter class for both month headers and transaction items.
 */
class TransactionAdapter(
    private val items: List<Any>,
    private val onTransactionClick: (Transaction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TRANSACTION = 1
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is MonthHeader -> VIEW_TYPE_HEADER
            is Transaction -> VIEW_TYPE_TRANSACTION
            else -> throw IllegalArgumentException("Unknown item type")
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_month_header, parent, false)
            MonthHeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transaction, parent, false)
            TransactionViewHolder(view, onTransactionClick)
        }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MonthHeader -> (holder as MonthHeaderViewHolder).bind(item)
            is Transaction -> (holder as TransactionViewHolder).bind(item)
        }
    }

    private class MonthHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val monthText: TextView = view.findViewById(R.id.monthText)
        fun bind(header: MonthHeader) {
            monthText.text = header.month
        }
    }

    private class TransactionViewHolder(
        view: View,
        private val click: (Transaction) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val descriptionText: TextView = view.findViewById(R.id.descriptionText)
        private val dateText: TextView = view.findViewById(R.id.dateText)
        private val categoryText: TextView = view.findViewById(R.id.categoryText)
        private val amountText: TextView = view.findViewById(R.id.amountText)

        fun bind(tx: Transaction) {
            descriptionText.text = tx.title
            dateText.text = tx.date
            categoryText.text = tx.category

            if (tx.amount >= 0) {
                amountText.setTextColor(itemView.context.getColor(R.color.income_green))
                amountText.text = "R%.2f".format(tx.amount)
            } else {
                amountText.setTextColor(itemView.context.getColor(R.color.expense_red))
                amountText.text = "-R%.2f".format(-tx.amount)
            }

            itemView.setOnClickListener { click(tx) }
        }
    }
}

// Add this class to the same file
data class MonthHeader(val month: String)