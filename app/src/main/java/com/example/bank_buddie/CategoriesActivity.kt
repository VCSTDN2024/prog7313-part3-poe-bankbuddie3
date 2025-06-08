package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale

class CategoriesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var totalBalanceText: TextView
    private lateinit var totalExpenseText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var maxGoalText: TextView
    private lateinit var expensePercentText: TextView

    private lateinit var categoriesRecyclerView: RecyclerView
    private val categorySummaries = mutableListOf<CategorySummary>()
    private lateinit var categoryAdapter: CategorySummaryAdapter

    // Handler for timeout
    private val handler = Handler(Looper.getMainLooper())
    private val OPERATION_TIMEOUT = 10000L // 10 seconds timeout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        totalBalanceText = findViewById(R.id.totalBalanceText)
        totalExpenseText = findViewById(R.id.totalExpenseText)
        progressBar = findViewById(R.id.progressBar)
        maxGoalText = findViewById(R.id.maxGoalText)
        expensePercentText = findViewById(R.id.expensePercentText)
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up recycler view
        categoryAdapter = CategorySummaryAdapter(categorySummaries) { category ->
            // Handle category click - now show detailed view for category expenses
            if (category.categoryId == "more") {
                // If "More" is clicked, navigate to add category screen
                startActivity(Intent(this, ExpenseCategoryActivity::class.java))
            } else {
                // For regular categories, show the category detail view
                val intent = Intent(this, CategoryDetailActivity::class.java)
                intent.putExtra("CATEGORY_ID", category.categoryId)
                intent.putExtra("CATEGORY_NAME", category.categoryName)
                // Get the icon resource based on category name
                val iconRes = getCategoryIcon(category.categoryName)
                intent.putExtra("CATEGORY_ICON", iconRes)
                startActivity(intent)
            }
        }
        categoriesRecyclerView.layoutManager = GridLayoutManager(this, 3)
        categoriesRecyclerView.adapter = categoryAdapter

        // Load categories
        loadCategoriesWithTimeout()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to activity
        loadCategoriesWithTimeout()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending callbacks to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }

    private fun loadCategoriesWithTimeout() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // If not logged in, redirect to login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Show loading indicator
        progressBar.visibility = View.VISIBLE
        findViewById<View>(R.id.emptyView)?.visibility = View.GONE
        categoriesRecyclerView.visibility = View.GONE

        // Set timeout for loading
        handler.postDelayed({
            if (progressBar.visibility == View.VISIBLE) {
                // If still loading after timeout
                progressBar.visibility = View.GONE

                // Show timeout dialog
                AlertDialog.Builder(this)
                    .setTitle("Loading Timeout")
                    .setMessage("Loading categories is taking longer than expected. Would you like to retry?")
                    .setPositiveButton("Retry") { _, _ -> loadCategoriesWithTimeout() }
                    .setNegativeButton("Cancel") { _, _ -> finish() }
                    .show()
            }
        }, OPERATION_TIMEOUT)

        // Load financial summary
        loadFinancialSummary(currentUser.uid)

        // Get categories from Firestore
        db.collection("users").document(currentUser.uid)
            .collection("expenseCategories")
            .get()
            .addOnSuccessListener { documents ->
                // Cancel timeout for categories loading
                handler.removeCallbacksAndMessages(null)

                // Hide loading indicator
                progressBar.visibility = View.GONE

                // Clear existing categories
                categorySummaries.clear()

                // Process categories
                for (document in documents) {
                    val id = document.id
                    val name = document.getString("name") ?: "Category"

                    // Determine icon based on category name
                    val iconRes = getCategoryIcon(name)

                    categorySummaries.add(
                        CategorySummary(
                            categoryId = id,
                            categoryName = name,
                            totalAmount = 0.0, // Will be populated later
                            expenseCount = 0   // Will be populated later
                        )
                    )
                }

                // Add "More" category
                categorySummaries.add(
                    CategorySummary(
                        categoryId = "more",
                        categoryName = "More",
                        totalAmount = 0.0,
                        expenseCount = 0
                    )
                )

                // Show empty state if no categories
                if (categorySummaries.size <= 1) { // Only "More" category
                    findViewById<View>(R.id.emptyView)?.visibility = View.VISIBLE
                    categoriesRecyclerView.visibility = View.GONE
                } else {
                    findViewById<View>(R.id.emptyView)?.visibility = View.GONE
                    categoriesRecyclerView.visibility = View.VISIBLE
                }

                // Update adapter
                categoryAdapter.notifyDataSetChanged()

                // Load expense counts and totals for each category
                loadCategoryStats(currentUser.uid)

                // Show success toast
                if (categorySummaries.size > 1) {
                    Toast.makeText(this, "Categories loaded successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                // Cancel timeout
                handler.removeCallbacksAndMessages(null)

                // Hide loading indicator
                progressBar.visibility = View.GONE

                // Show error dialog with retry option
                AlertDialog.Builder(this)
                    .setTitle("Error Loading Categories")
                    .setMessage("Failed to load categories: ${e.message}\n\nWould you like to retry?")
                    .setPositiveButton("Retry") { _, _ -> loadCategoriesWithTimeout() }
                    .setNegativeButton("Cancel") { _, _ -> }
                    .show()

                // Show empty state
                findViewById<View>(R.id.emptyView)?.visibility = View.VISIBLE
                categoriesRecyclerView.visibility = View.GONE
            }
    }

    // Load expense statistics for each category
    private fun loadCategoryStats(userId: String) {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.set(currentYear, currentMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        val endOfMonth = calendar.timeInMillis

        db.collection("users").document(userId)
            .collection("categorySummaries")
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val categoryId = doc.id
                    val totalAmount = doc.getDouble("totalAmount") ?: 0.0
                    val expenseCount = doc.getLong("expenseCount")?.toInt() ?: 0

                    // Find matching category and update stats
                    val categoryIndex = categorySummaries.indexOfFirst { it.categoryId == categoryId }
                    if (categoryIndex >= 0 && categoryIndex < categorySummaries.size) {
                        categorySummaries[categoryIndex].totalAmount = totalAmount
                        categorySummaries[categoryIndex].expenseCount = expenseCount
                    }
                }

                // Update adapter with new data
                categoryAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading category statistics: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Helper method to get icon based on category name
    private fun getCategoryIcon(category: String): Int {
        return when (category.lowercase(Locale.ROOT)) {
            "food" -> R.drawable.ic_category_food
            "transport" -> R.drawable.ic_category_transport
            "medicine" -> R.drawable.ic_category_medicine
            "groceries" -> R.drawable.ic_category_groceries
            "rent" -> R.drawable.ic_category_rent
            "gifts" -> R.drawable.ic_category_gifts
            "savings" -> R.drawable.ic_category_savings
            "entertainment" -> R.drawable.ic_category_entertainment
            "more" -> R.drawable.ic_category_more
            else -> R.drawable.ic_category_default
        }
    }

    // Add method to load financial summary
    private fun loadFinancialSummary(userId: String) {
        // Get current month's start and end dates
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.set(currentYear, currentMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        val endOfMonth = calendar.timeInMillis

        // Set a separate timeout for financial summary loading
        val financialHandler = Handler(Looper.getMainLooper())
        financialHandler.postDelayed({
            // If still loading after timeout, show default values
            maxGoalText.text = "R0.00"
            totalExpenseText.text = "-R0.00"
            expensePercentText.text = "0% of Budget Used"
            progressBar.progress = 0
            totalBalanceText.text = "R0.00"
        }, OPERATION_TIMEOUT)

        // Get budget goals
        db.collection("users").document(userId)
            .collection("budgetGoals")
            .document("monthly")
            .get()
            .addOnSuccessListener { document ->
                // Cancel timeout
                financialHandler.removeCallbacksAndMessages(null)

                val maxGoal = if (document.exists()) document.getDouble("maxGoal") ?: 0.0 else 0.0
                maxGoalText.text = String.format("R%.2f", maxGoal)

                // Get expenses for current month
                db.collection("users").document(userId)
                    .collection("expenses")
                    .get()
                    .addOnSuccessListener { documents ->
                        var totalExpenses = 0.0
                        for (doc in documents) {
                            val timestamp = doc.getLong("timestamp") ?: 0
                            if (timestamp in startOfMonth..endOfMonth) {
                                val amount = doc.getDouble("amount") ?: 0.0
                                if (amount < 0) { // Only negative values are expenses
                                    totalExpenses += Math.abs(amount)
                                }
                            }
                        }

                        // Calculate expense percentage
                        val expensePercent = if (maxGoal > 0) ((totalExpenses / maxGoal) * 100).toInt() else 0

                        // Update UI
                        totalExpenseText.text = String.format("-R%.2f", totalExpenses)
                        expensePercentText.text = "$expensePercent% of Budget Used"

                        // Set progress bar - cap at 100%
                        progressBar.max = 100
                        progressBar.progress = minOf(expensePercent, 100)

                        // Get income for current month
                        db.collection("users").document(userId)
                            .collection("income")
                            .get()
                            .addOnSuccessListener { incomeDocuments ->
                                var totalIncome = 0.0
                                for (doc in incomeDocuments) {
                                    val timestamp = doc.getLong("timestamp") ?: 0
                                    if (timestamp in startOfMonth..endOfMonth) {
                                        val amount = doc.getDouble("amount") ?: 0.0
                                        if (amount > 0) { // Only positive values are income
                                            totalIncome += amount
                                        }
                                    }
                                }

                                // Calculate total balance
                                val totalBalance = totalIncome - totalExpenses
                                totalBalanceText.text = String.format("R%.2f", totalBalance)
                            }
                            .addOnFailureListener { e ->
                                // Just set balance to negative expenses
                                totalBalanceText.text = String.format("-R%.2f", totalExpenses)
                            }
                    }
                    .addOnFailureListener { e ->
                        // Set default values on error
                        totalExpenseText.text = "-R0.00"
                        expensePercentText.text = "0% of Budget Used"
                        progressBar.progress = 0
                    }
            }
            .addOnFailureListener { e ->
                // Cancel timeout
                financialHandler.removeCallbacksAndMessages(null)

                // Set default values on error
                maxGoalText.text = "R0.00"
                totalExpenseText.text = "-R0.00"
                expensePercentText.text = "0% of Budget Used"
                progressBar.progress = 0
                totalBalanceText.text = "R0.00"
            }
    }
}