package com.vcsma.bank_buddie

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vcsma.bank_buddie.utils.DataSyncManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dataSyncManager: DataSyncManager

    // UI Elements
    private lateinit var welcomeText: TextView
    private lateinit var totalBalanceText: TextView
    private lateinit var totalExpensesText: TextView
    private lateinit var expensesPercentText: TextView
    private lateinit var financialGoalText: TextView
    private lateinit var expensesWeekText: TextView
    private lateinit var profileImage: ImageView
    private lateinit var expensesProgressBar: ProgressBar
    private lateinit var progressBar: ProgressBar
    private lateinit var timeFilterChipGroup: ChipGroup
    private lateinit var lastSyncText: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // Bottom navigation
    private lateinit var homeButton: ImageView
    private lateinit var searchButton: ImageView
    private lateinit var addButton: ImageView
    private lateinit var notificationsButton: ImageView
    private lateinit var profileButton: ImageView

    // Time filter
    private var currentTimeFilter = "daily"

    // FIXED: Reduced timeout for faster performance
    private val handler = Handler(Looper.getMainLooper())
    private val OPERATION_TIMEOUT = 3000L // 3 seconds timeout

    // Last sync timestamp
    private var lastSyncTime = 0L

    // Data values
    private var totalBalance = 0.0
    private var totalExpenses = 0.0
    private var budgetGoal = 0.0
    private var currentExpenses = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        dataSyncManager = DataSyncManager(this, db, auth)

        // Initialize views
        initializeViews()
        setupProfileMenu()
        setupSwipeRefresh()
        setupBottomNavigation()
        setupTimeFilterChips()
        loadUserData()
    }

    override fun onResume() {
        super.onResume()
        // FIXED: Always refresh when returning to dashboard for immediate updates
        loadUserData(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun initializeViews() {
        welcomeText = findViewById(R.id.welcomeText)
        totalBalanceText = findViewById(R.id.totalBalanceText)
        totalExpensesText = findViewById(R.id.totalExpensesText)
        expensesPercentText = findViewById(R.id.expensesPercentText)
        financialGoalText = findViewById(R.id.financialGoalText)
        expensesWeekText = findViewById(R.id.expensesWeekText)
        lastSyncText = findViewById(R.id.lastSyncText)
        expensesProgressBar = findViewById(R.id.expensesProgressBar)
        progressBar = findViewById(R.id.progressBar)
        profileImage = findViewById(R.id.profileImage)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        homeButton = findViewById(R.id.homeButton)
        searchButton = findViewById(R.id.searchButton)
        addButton = findViewById(R.id.addButton)
        notificationsButton = findViewById(R.id.notificationsButton)
        profileButton = findViewById(R.id.profileButton)
        timeFilterChipGroup = findViewById(R.id.timeFilterChipGroup)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.teal_700),
            ContextCompat.getColor(this, R.color.income_green),
            ContextCompat.getColor(this, R.color.purple_500)
        )

        swipeRefreshLayout.setOnRefreshListener {
            loadUserData(true)
        }
    }

    private fun setupProfileMenu() {
        profileImage.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.profile_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_expense_categories -> {
                        startActivity(Intent(this, ExpenseCategoryActivity::class.java))
                        true
                    }
                    R.id.menu_add_expense -> {
                        startActivity(Intent(this, ExpenseEntryActivity::class.java))
                        true
                    }
                    R.id.add_transaction_menu -> {
                        startActivity(Intent(this, TransactionListActivity::class.java))
                        true
                    }
                    R.id.menu_view_expenses -> {
                        startActivity(Intent(this, ExpenseListActivity::class.java))
                        true
                    }
                    R.id.menu_expense_summary -> {
                        startActivity(Intent(this, ExpenseSummaryActivity::class.java))
                        true
                    }
                    R.id.menu_budget_goals -> {
                        startActivity(Intent(this, BudgetGoalActivity::class.java))
                        true
                    }
                    R.id.menu_budget_visualization -> {
                        startActivity(Intent(this, BudgetVisualizationActivity::class.java))
                        true
                    }
                    R.id.menu_chart_visualization -> {
                        startActivity(Intent(this, CategorySpendingChartActivity::class.java))
                        true
                    }
                    R.id.menu_add_income -> {
                        startActivity(Intent(this, AddIncomeActivity::class.java))
                        true
                    }
                    R.id.menu_settings -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        true
                    }
                    R.id.menu_logout -> {
                        logout()
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
    }

    private fun setupBottomNavigation() {
        homeButton.setOnClickListener { loadUserData(true) }
        searchButton.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        addButton.setOnClickListener { startActivity(Intent(this, AIChatbotActivity::class.java)) }
        notificationsButton.setOnClickListener { startActivity(Intent(this, FinancialHealthActivity::class.java)) }
        profileButton.setOnClickListener { startActivity(Intent(this, NotificationActivity::class.java)) }
    }

    private fun setupTimeFilterChips() {
        val dailyChip = findViewById<Chip>(R.id.dailyChip)
        val weeklyChip = findViewById<Chip>(R.id.weeklyChip)
        val monthlyChip = findViewById<Chip>(R.id.monthlyChip)

        dailyChip.isChecked = true

        timeFilterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.dailyChip -> {
                    currentTimeFilter = "daily"
                    // FIXED: Show loading indicator when changing filter
                    progressBar.visibility = View.VISIBLE
                    loadFinancialData(auth.currentUser?.uid ?: "", "daily")
                }
                R.id.weeklyChip -> {
                    currentTimeFilter = "weekly"
                    progressBar.visibility = View.VISIBLE
                    loadFinancialData(auth.currentUser?.uid ?: "", "weekly")
                }
                R.id.monthlyChip -> {
                    currentTimeFilter = "monthly"
                    progressBar.visibility = View.VISIBLE
                    loadFinancialData(auth.currentUser?.uid ?: "", "monthly")
                }
            }
        }
    }

    private fun loadUserData(forceRefresh: Boolean = false) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE
        if (swipeRefreshLayout.isRefreshing) {
            progressBar.visibility = View.GONE
        }

        // Set timeout
        handler.postDelayed({
            if (progressBar.visibility == View.VISIBLE || swipeRefreshLayout.isRefreshing) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Data sync timed out. Please check your connection.",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") { loadUserData(true) }.show()
            }
        }, OPERATION_TIMEOUT)

        // Get user data
        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                handler.removeCallbacksAndMessages(null)

                if (document != null && document.exists()) {
                    val fullName = document.getString("fullName") ?: "User"
                    val firstName = fullName.split(" ").firstOrNull() ?: fullName
                    val greeting = getGreeting()
                    welcomeText.text = getString(R.string.welcome_user, greeting, firstName)
                } else {
                    welcomeText.text = getString(R.string.welcome_user, getGreeting(), "User")
                }

                loadFinancialData(currentUser.uid, currentTimeFilter)
                lastSyncTime = System.currentTimeMillis()
                updateLastSyncText()
            }
            .addOnFailureListener { e ->
                handler.removeCallbacksAndMessages(null)
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Failed to sync data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun updateLastSyncText() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentDateAndTime = sdf.format(Date(lastSyncTime))
        lastSyncText.text = "Last sync: $currentDateAndTime"
        lastSyncText.visibility = View.VISIBLE
    }

    private fun loadFinancialData(userId: String, timeFilter: String) {
        val (startDate, endDate) = getDateRangeForFilter(timeFilter)

        // FIXED: Load budget goal for the selected time period
        val budgetDocId = when (timeFilter) {
            "daily" -> "daily"
            "weekly" -> "weekly"
            "monthly" -> "monthly"
            else -> "monthly"
        }

        // First load the budget goal for the selected period
        db.collection("users").document(userId)
            .collection("budgetGoals")
            .document(budgetDocId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    budgetGoal = document.getDouble("maxGoal") ?: 0.0
                } else {
                    budgetGoal = 0.0
                }

                // Now load income data
                db.collection("users").document(userId)
                    .collection("income")
                    .whereGreaterThanOrEqualTo("timestamp", startDate)
                    .whereLessThanOrEqualTo("timestamp", endDate)
                    .get()
                    .addOnSuccessListener { incomeDocuments ->
                        val totalIncome = incomeDocuments.sumOf { it.getDouble("amount") ?: 0.0 }

                        // Load expenses for the same period
                        db.collection("users").document(userId)
                            .collection("expenses")
                            .whereGreaterThanOrEqualTo("timestamp", startDate)
                            .whereLessThanOrEqualTo("timestamp", endDate)
                            .get()
                            .addOnSuccessListener { expenseDocuments ->
                                val expenses = expenseDocuments.sumOf { it.getDouble("amount") ?: 0.0 }

                                this.totalBalance = totalIncome
                                this.totalExpenses = expenses
                                this.currentExpenses = expenses

                                // Update UI with totals
                                updateFinancialUI(totalIncome, expenses, timeFilter)

                                // FIXED: Update progress bar based on budget goal or income
                                if (budgetGoal > 0) {
                                    updateBudgetProgress(budgetGoal, expenses, timeFilter)
                                } else {
                                    updateExpensesProgress(totalIncome, expenses)
                                }

                                // Load recent transactions for the selected period
                                loadRecentTransactions(userId, startDate, endDate)

                                progressBar.visibility = View.GONE
                                swipeRefreshLayout.isRefreshing = false
                            }
                            .addOnFailureListener { e ->
                                progressBar.visibility = View.GONE
                                swipeRefreshLayout.isRefreshing = false
                                Snackbar.make(
                                    findViewById(android.R.id.content),
                                    "Failed to load expenses: ${e.message}",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        swipeRefreshLayout.isRefreshing = false
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            "Failed to load income: ${e.message}",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
            }
    }

    // FIXED: Update budget progress based on the selected time filter
    private fun updateBudgetProgress(goal: Double, spending: Double, timeFilter: String) {
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        formatter.maximumFractionDigits = 0

        // Update goal text based on time filter
        val periodText = when (timeFilter) {
            "daily" -> "Daily"
            "weekly" -> "Weekly"
            "monthly" -> "Monthly"
            else -> "Budget"
        }

        findViewById<TextView>(R.id.financialGoalLabel).text = "$periodText Budget Goal"
        findViewById<TextView>(R.id.expensesWeekLabel).text = "$periodText Expenses"

        if (goal > 0) {
            financialGoalText.text = formatter.format(goal)

            // Animate progress bar based on goal percentage
            val percentage = ((spending / goal) * 100).coerceAtMost(100.0).toInt()
            val animator = ValueAnimator.ofInt(0, percentage)
            animator.duration = 1000
            animator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Int
                expensesProgressBar.progress = animatedValue
            }
            animator.start()
        } else {
            financialGoalText.text = "Not set"
            expensesProgressBar.progress = 0
        }

        expensesWeekText.text = formatter.format(spending)

        val savingsText = findViewById<TextView>(R.id.savingsText)
        if (goal > 0) {
            if (spending <= goal) {
                val percentUsed = (spending / goal * 100).toInt()
                savingsText.text = "On track (${percentUsed}% used)"
                savingsText.setTextColor(Color.WHITE)
            } else {
                val overspent = spending - goal
                savingsText.text = "Over budget by ${formatter.format(overspent)}"
                savingsText.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
            }
        } else {
            savingsText.text = "Set $periodText budget goal"
            savingsText.setTextColor(Color.WHITE)
        }
    }

    private fun updateFinancialUI(income: Double, expenses: Double, timeFilter: String) {
        animateNumberChange(totalBalanceText, income, "R")
        animateNumberChange(totalExpensesText, expenses, "R")

        val timePeriod = when (timeFilter) {
            "daily" -> "Today"
            "weekly" -> "This Week"
            "monthly" -> "This Month"
            else -> "Period"
        }

        findViewById<TextView>(R.id.totalBalanceLabel).text = "$timePeriod's Income"
        findViewById<TextView>(R.id.totalExpensesLabel).text = "$timePeriod's Expenses"
    }

    private fun animateNumberChange(textView: TextView, targetValue: Double, prefix: String = "") {
        val currentText = textView.text.toString().replace(Regex("[^0-9]"), "")
        val currentValue = if (currentText.isEmpty()) 0.0 else currentText.toDouble()

        val valueAnimator = ValueAnimator.ofFloat(currentValue.toFloat(), targetValue.toFloat())
        valueAnimator.duration = 1000
        valueAnimator.interpolator = DecelerateInterpolator()
        valueAnimator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            textView.text = "$prefix${animatedValue.toInt()}"
        }
        valueAnimator.start()
    }

    private fun updateExpensesProgress(income: Double, expenses: Double) {
        if (income <= 0) {
            expensesProgressBar.progress = 0
            expensesPercentText.text = "No income data"
            return
        }

        val percentage = ((expenses / income) * 100).toInt()

        val animator = ValueAnimator.ofInt(0, percentage)
        animator.duration = 1000
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            expensesProgressBar.progress = animatedValue
        }
        animator.start()

        expensesPercentText.text = "$percentage% of income spent"

        val color = when {
            percentage < 50 -> ContextCompat.getColor(this, R.color.income_green)
            percentage < 80 -> ContextCompat.getColor(this, R.color.teal_700)
            else -> ContextCompat.getColor(this, R.color.expense_red)
        }
        expensesPercentText.setTextColor(color)
    }

    // FIXED: Pass date range directly to loadRecentTransactions
    private fun loadRecentTransactions(userId: String, startDate: Long, endDate: Long) {
        // FIXED: Load from both collections separately and combine properly
        val combinedTransactions = mutableListOf<Map<String, Any>>()

        // Load income
        db.collection("users").document(userId)
            .collection("income")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { incomeDocuments ->
                for (doc in incomeDocuments) {
                    val data = doc.data.toMutableMap()
                    data["type"] = "income"
                    data["id"] = doc.id
                    combinedTransactions.add(data)
                }

                // Load expenses
                db.collection("users").document(userId)
                    .collection("expenses")
                    .whereGreaterThanOrEqualTo("timestamp", startDate)
                    .whereLessThanOrEqualTo("timestamp", endDate)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(3)
                    .get()
                    .addOnSuccessListener { expenseDocuments ->
                        for (doc in expenseDocuments) {
                            val data = doc.data.toMutableMap()
                            data["type"] = "expense"
                            data["id"] = doc.id
                            combinedTransactions.add(data)
                        }

                        // Sort by timestamp and take top 3
                        combinedTransactions.sortByDescending { it["timestamp"] as Long }
                        val recentTransactions = combinedTransactions.take(3)
                        updateRecentTransactionsUI(recentTransactions)
                    }
            }
    }

    private fun updateRecentTransactionsUI(transactions: List<Map<String, Any>>) {
        try {
            // Hide all cards first
            findViewById<View>(R.id.transaction1Card).visibility = View.GONE
            findViewById<View>(R.id.transaction2Card).visibility = View.GONE
            findViewById<View>(R.id.transaction3Card).visibility = View.GONE

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

            transactions.forEachIndexed { index, transaction ->
                val cardId = when (index) {
                    0 -> R.id.transaction1Card
                    1 -> R.id.transaction2Card
                    2 -> R.id.transaction3Card
                    else -> return@forEachIndexed
                }

                val card = findViewById<View>(cardId)
                card.visibility = View.VISIBLE

                val timestamp = transaction["timestamp"] as Long
                val amount = transaction["amount"] as Double
                val type = transaction["type"] as String

                // FIXED: Use title field first, then category, then description
                val title = transaction["title"] as? String
                    ?: transaction["category"] as? String
                    ?: transaction["description"] as? String
                    ?: "Transaction"

                val date = Date(timestamp)

                val descriptionView = card.findViewById<TextView>(R.id.descriptionText)
                val dateView = card.findViewById<TextView>(R.id.dateText)
                val amountView = card.findViewById<TextView>(R.id.amountText)
                val categoryView = card.findViewById<TextView>(R.id.categoryText)

                // FIXED: Show proper title instead of "Other"
                descriptionView.text = title.replaceFirstChar { it.uppercase() }
                dateView.text = dateFormat.format(date)

                val amountText = if (type == "income") "+R${amount.toInt()}" else "-R${amount.toInt()}"
                amountView.text = amountText

                val color = if (type == "income")
                    ContextCompat.getColor(this, R.color.income_green)
                else
                    ContextCompat.getColor(this, R.color.expense_red)
                amountView.setTextColor(color)

                categoryView.text = title.replaceFirstChar { it.uppercase() }
                categoryView.setBackgroundColor(color)
            }

            val noTransactionsMessage = findViewById<TextView>(R.id.noTransactionsText)
            if (transactions.isEmpty()) {
                noTransactionsMessage.visibility = View.VISIBLE
            } else {
                noTransactionsMessage.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating transaction UI: ${e.message}")
        }
    }

    private fun getDateRangeForFilter(timeFilter: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()

        return when (timeFilter) {
            "daily" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfDay = calendar.timeInMillis

                Pair(startOfDay, endOfDay)
            }
            "weekly" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfWeek = calendar.timeInMillis

                calendar.add(Calendar.DAY_OF_WEEK, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfWeek = calendar.timeInMillis

                Pair(startOfWeek, endOfWeek)
            }
            "monthly" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfMonth = calendar.timeInMillis

                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfMonth = calendar.timeInMillis

                Pair(startOfMonth, endOfMonth)
            }
            else -> getDateRangeForFilter("daily")
        }
    }
}