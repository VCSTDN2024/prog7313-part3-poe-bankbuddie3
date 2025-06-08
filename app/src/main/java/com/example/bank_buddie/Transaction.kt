package com.vcsma.bank_buddie

data class Transaction(
    val id: String,
    val title: String,
    val amount: Double,
    val category: String,
    val description: String = "",
    val date: String,
    val iconRes: Int,
    val monthHeader: String = "April" // Changed from MonthHeader to monthHeader (lowercase)
)