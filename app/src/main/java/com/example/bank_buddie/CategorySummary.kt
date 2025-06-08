package com.vcsma.bank_buddie

data class CategorySummary(
    val categoryId: String = "",
    val categoryName: String = "",
    var totalAmount: Double = 0.0,
    var expenseCount: Int = 0,
    val iconRes: Int = R.drawable.ic_category_default
)
