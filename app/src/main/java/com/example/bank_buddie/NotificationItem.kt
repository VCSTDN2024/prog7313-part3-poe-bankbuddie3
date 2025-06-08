package com.vcsma.bank_buddie

sealed class NotificationItem {
    data class Header(val title: String) : NotificationItem()

    data class Notification(
        val id: String,
        val type: NotificationType,
        val title: String,
        val message: String,
        val timestamp: String,
        val details: String? = null,
        val isRead: Boolean = false
    ) : NotificationItem()
}

enum class NotificationType {
    REMINDER,
    UPDATE,
    TRANSACTION,
    EXPENSE,
    INCOME,
    BUDGET_ALERT,
    GOAL_ACHIEVED
}
