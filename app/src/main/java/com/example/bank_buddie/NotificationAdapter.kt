package com.vcsma.bank_buddie

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(
    private val notifications: List<NotificationItem>,
    private val onNotificationClick: ((NotificationItem.Notification) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_NOTIFICATION = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (notifications[position]) {
            is NotificationItem.Header -> VIEW_TYPE_HEADER
            is NotificationItem.Notification -> VIEW_TYPE_NOTIFICATION
            else -> throw IllegalArgumentException("Unknown item type at position $position: ${notifications[position]::class.simpleName}")
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_NOTIFICATION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification, parent, false)
                NotificationViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = notifications[position]) {
            is NotificationItem.Header -> {
                (holder as HeaderViewHolder).bind(item)
            }
            is NotificationItem.Notification -> {
                (holder as NotificationViewHolder).bind(item)
            }
            else -> {
                throw IllegalArgumentException("Unknown view type at position $position: ${item::class.simpleName}")
            }
        }
    }


    override fun getItemCount(): Int = notifications.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerText: TextView = itemView.findViewById(R.id.headerText)

        fun bind(header: NotificationItem.Header) {
            headerText.text = header.title
        }
    }

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.notificationIcon)
        private val titleText: TextView = itemView.findViewById(R.id.notificationTitle)
        private val messageText: TextView = itemView.findViewById(R.id.notificationMessage)
        private val detailsText: TextView = itemView.findViewById(R.id.notificationDetails)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val unreadIndicator: View = itemView.findViewById(R.id.unreadIndicator)

        fun bind(notification: NotificationItem.Notification) {
            titleText.text = notification.title
            messageText.text = notification.message
            timestampText.text = notification.timestamp

            // Set notification icon and color based on type
            val (iconRes, colorRes) = when (notification.type) {
                NotificationType.REMINDER -> Pair(R.drawable.ic_notifications, R.color.teal_700)
                NotificationType.UPDATE -> Pair(R.drawable.ic_notification_star, R.color.purple_500)
                NotificationType.TRANSACTION -> Pair(R.drawable.ic_notification_money, R.color.income_green)
                NotificationType.EXPENSE -> Pair(R.drawable.ic_notification_expense, R.color.expense_red)
                NotificationType.INCOME -> Pair(R.drawable.ic_income, R.color.income_green)
                NotificationType.BUDGET_ALERT -> Pair(R.drawable.ic_warning, R.color.orange)
                NotificationType.GOAL_ACHIEVED -> Pair(R.drawable.ic_achievement, R.color.green)
            }

            iconView.setImageResource(iconRes)
            iconView.setColorFilter(ContextCompat.getColor(itemView.context, colorRes))

            // Show or hide transaction details
            if (notification.details.isNullOrEmpty()) {
                detailsText.visibility = View.GONE
            } else {
                detailsText.visibility = View.VISIBLE
                detailsText.text = notification.details
            }

            // Show unread indicator
            unreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE

            // Set click listener
            itemView.setOnClickListener {
                onNotificationClick?.invoke(notification)
            }

            // Adjust opacity for read notifications
            itemView.alpha = if (notification.isRead) 0.7f else 1.0f
        }
    }
}
