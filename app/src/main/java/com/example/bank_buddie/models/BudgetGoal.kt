package com.vcsma.bank_buddie.models

/**
 * Data class representing a budget goal for a specific time period and category.
 *
 * @property minGoal Minimum spending goal (spending floor)
 * @property maxGoal Maximum spending goal (spending ceiling)
 * @property incomeGoal Target income goal for the period (optional)
 * @property category The expense category this goal applies to (or "All" for all categories)
 * @property timeframe The time period for this goal (Weekly, Monthly, Quarterly, Yearly)
 * @property timestamp When this goal was created/updated
 */
data class BudgetGoal(
    val minGoal: Double = 0.0,
    val maxGoal: Double = 0.0,
    val incomeGoal: Double = 0.0,
    val category: String = "All",
    val timeframe: String = "Monthly",
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Converts the budget goal to a map for Firestore storage
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "minGoal" to minGoal,
            "maxGoal" to maxGoal,
            "incomeGoal" to incomeGoal,
            "category" to category,
            "timeframe" to timeframe,
            "timestamp" to timestamp
        )
    }

    companion object {
        /**
         * Creates a BudgetGoal from a Firestore document map
         */
        fun fromMap(data: Map<String, Any>): BudgetGoal {
            return BudgetGoal(
                minGoal = data["minGoal"] as? Double ?: 0.0,
                maxGoal = data["maxGoal"] as? Double ?: 0.0,
                incomeGoal = data["incomeGoal"] as? Double ?: 0.0,
                category = data["category"] as? String ?: "All",
                timeframe = data["timeframe"] as? String ?: "Monthly",
                timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis()
            )
        }
    }
}
