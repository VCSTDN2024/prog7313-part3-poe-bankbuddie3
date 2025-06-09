package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.view.LayoutInflater
import java.util.Calendar
import java.util.Locale

class TransactionListActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var totalBalanceText: TextView
    private lateinit var incomeAmountText: TextView
    private lateinit var expenseAmountText: TextView
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var calendarButton: ImageView

    private val allTransactions = mutableListOf<Transaction>()
    private val groupedTransactions = mutableListOf<Any>() // Can be MonthHeader or Transaction
    private lateinit var adapter: TransactionAdapter // Changed from TransactionListAdapter to TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_list)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        totalBalanceText = findViewById(R.id.totalBalanceText)
        incomeAmountText = findViewById(R.id.incomeAmountText)
        expenseAmountText = findViewById(R.id.expenseAmountText)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        calendarButton = findViewById(R.id.calendarButton)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up calendar button
        calendarButton.setOnClickListener {
            val intent = Intent(this, CalendarActivity::class.java)
            startActivity(intent)
        }

        // Set up recycler view
        adapter = TransactionAdapter(groupedTransactions) { transaction ->
            // Handle transaction click
            val intent = Intent(this, TransactionDetailActivity::class.java)
            intent.putExtra("TRANSACTION_ID", transaction.id)
            startActivity(intent)
        }
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionsRecyclerView.adapter = adapter

        // Load transactions
        loadTransactions()
    }

    // Update loadTransactions method to fetch real data
    private fun loadTransactions() {
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
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE

        // Get financial summary
        loadFinancialSummary(currentUser.uid)

        // Get transactions
        allTransactions.clear()

        // Get expenses
        db.collection("users").document(currentUser.uid)
            .collection("expenses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val id = doc.id
                    val title = doc.getString("description") ?: "Expense"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val category = doc.getString("categoryName") ?: "General"
                    val date = doc.getString("date") ?: "Today"

                    // Extract month from date
                    val month = extractMonth(date)

                    // Determine icon based on category
                    val iconRes = getCategoryIcon(category)

                    allTransactions.add(
                        Transaction(
                            id = id,
                            title = title,
                            amount = -amount, // Make expense amount negative
                            category = category,
                            date = date,
                            iconRes = iconRes,
                            monthHeader = month // Changed from MonthHeader to monthHeader
                        )
                    )
                }

                // Get income transactions
                db.collection("users").document(currentUser.uid)
                    .collection("income")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { incomeDocuments ->
                        // Hide loading indicator
                        findViewById<View>(R.id.progressBar).visibility = View.GONE

                        for (doc in incomeDocuments) {
                            val id = doc.id
                            val title = doc.getString("description") ?: "Income"
                            val amount = doc.getDouble("amount") ?: 0.0
                            val category = doc.getString("category") ?: "Income"
                            val date = doc.getString("date") ?: "Today"

                            // Extract month from date
                            val month = extractMonth(date)

                            allTransactions.add(
                                Transaction(
                                    id = id,
                                    title = title,
                                    amount = amount, // Income is positive
                                    category = category,
                                    date = date,
                                    iconRes = R.drawable.ic_transaction_salary,
                                    monthHeader = month // Changed from MonthHeader to monthHeader
                                )
                            )
                        }

                        // Group transactions by month
                        if (allTransactions.isEmpty()) {
                            // Show empty state
                            findViewById<View>(R.id.emptyTransactionsView).visibility = View.VISIBLE
                            transactionsRecyclerView.visibility = View.GONE
                        } else {
                            findViewById<View>(R.id.emptyTransactionsView).visibility = View.GONE
                            transactionsRecyclerView.visibility = View.VISIBLE
                            groupTransactionsByMonth()
                        }
                    }
                    .addOnFailureListener { e ->
                        // Hide loading indicator
                        findViewById<View>(R.id.progressBar).visibility = View.GONE

                        Toast.makeText(this, "Error loading income transactions: ${e.message}", Toast.LENGTH_SHORT).show()

                        // Group transactions by month
                        if (allTransactions.isEmpty()) {
                            // Show empty state
                            findViewById<View>(R.id.emptyTransactionsView).visibility = View.VISIBLE
                            transactionsRecyclerView.visibility = View.GONE
                        } else {
                            findViewById<View>(R.id.emptyTransactionsView).visibility = View.GONE
                            transactionsRecyclerView.visibility = View.VISIBLE
                            groupTransactionsByMonth()
                        }
                    }
            }
            .addOnFailureListener { e ->
                // Hide loading indicator
                findViewById<View>(R.id.progressBar).visibility = View.GONE

                Toast.makeText(this, "Error loading expense transactions: ${e.message}", Toast.LENGTH_SHORT).show()

                // Show empty state
                findViewById<View>(R.id.emptyTransactionsView).visibility = View.VISIBLE
                transactionsRecyclerView.visibility = View.GONE
            }
    }

    // Helper method to extract month from date string
    private fun extractMonth(date: String): String {
        // Example date format: "18:27 - April 30"
        val parts = date.split(" - ")
        if (parts.size > 1) {
            val dateParts = parts[1].split(" ")
            if (dateParts.isNotEmpty()) {
                return dateParts[0] // Return month name
            }
        }
        return "Unknown"
    }

    // Helper method to get icon based on category
    private fun getCategoryIcon(category: String): Int {
        return when (category.lowercase(Locale.ROOT)) {
            "food" -> R.drawable.ic_category_food
            "groceries" -> R.drawable.ic_transaction_groceries
            "rent" -> R.drawable.ic_category_rent
            "transport" -> R.drawable.ic_transaction_transport
            "salary" -> R.drawable.ic_transaction_salary
            else -> R.drawable.ic_category_more
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

        // Get expenses for current month
        db.collection("users").document(userId)
            .collection("expenses")
            .get()
            .addOnSuccessListener { documents ->
                var totalExpenses = 0.0
                for (doc in documents) {
                    val amount = doc.getDouble("amount") ?: 0.0
                    totalExpenses += Math.abs(amount)
                }

                // Update UI
                val expenseFormatted = getString(R.string.currency_format, totalExpenses)
                expenseAmountText.text = expenseFormatted

                // Get income for current month
                db.collection("users").document(userId)
                    .collection("income")
                    .get()
                    .addOnSuccessListener { incomeDocuments ->
                        var totalIncome = 0.0
                        for (doc in incomeDocuments) {
                            val amount = doc.getDouble("amount") ?: 0.0
                            totalIncome += amount
                        }

                        // Update UI
                        incomeAmountText.text = getString(R.string.currency_format, totalIncome)

                        // Calculate total balance
                        val totalBalance = totalIncome - totalExpenses
                        totalBalanceText.text = getString(R.string.currency_format, totalBalance)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error loading income data: ${e.message}", Toast.LENGTH_SHORT).show()
                        incomeAmountText.text = getString(R.string.currency_zero)
                        totalBalanceText.text = getString(R.string.currency_negative_format, totalExpenses)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading expense data: ${e.message}", Toast.LENGTH_SHORT).show()
                expenseAmountText.text = getString(R.string.currency_zero)
                incomeAmountText.text = getString(R.string.currency_zero)
                totalBalanceText.text = getString(R.string.currency_zero)
            }
    }

    private fun groupTransactionsByMonth() {
        // Clear existing grouped items
        groupedTransactions.clear()

        // Track the current month being processed
        var currentMonth: String? = null

        // Group transactions by month
        for (transaction in allTransactions) {
            if (transaction.monthHeader != currentMonth) { // Changed from MonthHeader to monthHeader
                // Add month header if it's a new month
                currentMonth = transaction.monthHeader // Changed from MonthHeader to monthHeader
                groupedTransactions.add(MonthHeader(currentMonth))
            }

            // Add transaction
            groupedTransactions.add(transaction)
        }

        // Notify adapter of data change
        adapter.notifyDataSetChanged()
    }
}