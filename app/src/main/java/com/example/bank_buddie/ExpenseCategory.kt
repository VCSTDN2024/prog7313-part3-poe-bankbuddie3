package com.vcsma.bank_buddie

data class ExpenseCategory(
    val id: String = "",
    val name: String = "",
    val iconRes: Int = R.drawable.ic_category_default
) {
    // Required empty constructor for Firestore
    constructor() : this("", "")
}
