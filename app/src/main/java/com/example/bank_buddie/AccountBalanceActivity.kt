package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AccountBalanceActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var totalBalanceText: TextView
    private lateinit var totalExpenseText: TextView
    private lateinit var maxGoalText: TextView
    private lateinit var expensePercentText: TextView
    private lateinit var incomeAmountText: TextView
    private lateinit var expenseAmountText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var seeAllText: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var emptyStateText: TextView

    private val transactions = mutableListOf<Any>() // Can be Transaction or MonthHeader
    private lateinit var adapter: TransactionAdapter

    // Logging tag for debugging
    private val TAG = "AccountBalanceActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Set content view with error handling
            setContentView(R.layout.activity_account_balance)

            Log.d(TAG, "Activity created, initializing Firebase")

            // Initialize Firebase with error handling
            try {
                auth = FirebaseAuth.getInstance()
                db = FirebaseFirestore.getInstance()
                Log.d(TAG, "Firebase initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Firebase: ${e.message}")
                Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            Log.d(TAG, "Initializing views")

            // Initialize views with error handling
            try {
                initializeViews()
                Log.d(TAG, "Views initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing views: ${e.message}")
                Toast.makeText(this, "Error initializing views: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            Log.d(TAG, "Setting up RecyclerView")

            // RecyclerView setup with error handling
            try {
                setupRecyclerView()
                Log.d(TAG, "RecyclerView set up successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up RecyclerView: ${e.message}")
                Toast.makeText(this, "Error setting up transaction list: ${e.message}", Toast.LENGTH_LONG).show()
            }

            Log.d(TAG, "Loading account data")

            // Load data with error handling
            try {
                loadAccountData()
                Log.d(TAG, "Account data loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading account data: ${e.message}")
                Toast.makeText(this, "Error loading account data: ${e.message}", Toast.LENGTH_LONG).show()
                // Show error but don't crash
                emptyStateText.visibility = View.VISIBLE
                emptyStateText.text = "Could not load account data: ${e.message}"
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Fatal error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializeViews() {
        // Views - with null checks
        backButton = findViewById(R.id.backButton) ?:
                throw IllegalStateException("backButton view not found")

        totalBalanceText = findViewById(R.id.totalBalanceText) ?:
                throw IllegalStateException("totalBalanceText view not found")

        totalExpenseText = findViewById(R.id.totalExpenseText) ?:
                throw IllegalStateException("totalExpenseText view not found")

        maxGoalText = findViewById(R.id.maxGoalText) ?:
                throw IllegalStateException("maxGoalText view not found")

        expensePercentText = findViewById(R.id.expensePercentText) ?:
                throw IllegalStateException("expensePercentText view not found")

        incomeAmountText = findViewById(R.id.incomeAmountText) ?:
                throw IllegalStateException("incomeAmountText view not found")

        expenseAmountText = findViewById(R.id.expenseAmountText) ?:
                throw IllegalStateException("expenseAmountText view not found")

        progressBar = findViewById(R.id.progressBar) ?:
                throw IllegalStateException("progressBar view not found")

        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView) ?:
                throw IllegalStateException("transactionsRecyclerView view not found")

        seeAllText = findViewById(R.id.seeAllText) ?:
                throw IllegalStateException("seeAllText view not found")

        loadingProgressBar = findViewById(R.id.loadingProgressBar) ?:
                throw IllegalStateException("loadingProgressBar view not found")

        emptyStateText = findViewById(R.id.emptyStateText) ?:
                throw IllegalStateException("emptyStateText view not found")

        // Back button
        backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            finish()
        }

        // "See All" link
        seeAllText.setOnClickListener {
            Log.d(TAG, "See All button clicked")
            try {
                startActivity(Intent(this, TransactionListActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to TransactionListActivity: ${e.message}")
                Toast.makeText(this, "Could not open transaction list: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up adapter and RecyclerView")

        // Create safe adapter with empty click handler if needed
        adapter = TransactionAdapter(transactions) { tx ->
            Log.d(TAG, "Transaction clicked: ${tx.id}")
            try {
                val intent = Intent(this, TransactionDetailActivity::class.java)
                intent.putExtra("TRANSACTION_ID", tx.id)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to transaction details: ${e.message}")
                Toast.makeText(this, "Could not open transaction details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up RecyclerView with proper error handling
        transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AccountBalanceActivity)
            adapter = this@AccountBalanceActivity.adapter
        }
    }

    private fun loadAccountData() {
        // Show loading indicator
        loadingProgressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        try {
            // First try to get data from Firestore if user is logged in
            auth.currentUser?.let { user ->
                Log.d(TAG, "User logged in, loading data from Firestore for user: ${user.uid}")

                // Load transactions from Firestore (minimally required)
                db.collection("users").document(user.uid)
                    .collection("transactions")
                    .limit(5) // Limit to 5 transactions for quicker loading
                    .get()
                    .addOnSuccessListener { documents ->
                        Log.d(TAG, "Successfully retrieved transactions: ${documents.size()}")
                        loadingProgressBar.visibility = View.GONE

                        if (documents.isEmpty) {
                            Log.d(TAG, "No transactions found, showing sample data")
                            // If no transactions found, show sample data
                            createSampleData()
                        } else {
                            Log.d(TAG, "Processing transaction data")

                            // Process transactions and show real data
                            transactions.clear()
                            var totalIncome = 0.0
                            var totalExpense = 0.0

                            for (document in documents) {
                                try {
                                    // Basic fields needed for a transaction
                                    val id = document.id
                                    val title = document.getString("description") ?: ""
                                    val amount = document.getDouble("amount") ?: 0.0
                                    val category = document.getString("category") ?: ""
                                    val date = document.getString("date") ?: ""

                                    // Determine icon (simplified for safety)
                                    val iconResId = if (amount >= 0) {
                                        R.drawable.ic_transaction_salary
                                    } else {
                                        R.drawable.ic_transaction_groceries
                                    }

                                    // Create transaction object
                                    val transaction = Transaction(
                                        id = id,
                                        title = title,
                                        amount = amount,
                                        category = category,
                                        date = date,
                                        iconRes = iconResId
                                    )

                                    // Update totals
                                    if (amount >= 0) {
                                        totalIncome += amount
                                    } else {
                                        totalExpense += Math.abs(amount)
                                    }

                                    transactions.add(transaction)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing transaction: ${e.message}")
                                    // Skip this transaction but continue with others
                                }
                            }

                            // Update balance information
                            val totalBalance = totalIncome - totalExpense
                            val maxGoal = 5000.0 // Default goal
                            val expensePct = ((totalExpense / maxGoal) * 100).toInt()

                            // Update UI with real data
                            updateUI(totalBalance, totalExpense, maxGoal, expensePct, totalIncome)

                            // Notify adapter
                            adapter.notifyDataSetChanged()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error loading transactions: ${e.message}")
                        loadingProgressBar.visibility = View.GONE
                        Toast.makeText(this, "Error loading transactions: ${e.message}", Toast.LENGTH_SHORT).show()

                        // Show sample data as fallback
                        createSampleData()
                    }
            } ?: run {
                Log.d(TAG, "No user logged in, showing sample data")
                loadingProgressBar.visibility = View.GONE
                // No user logged in, use sample data
                createSampleData()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in loadAccountData: ${e.message}")
            loadingProgressBar.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = "Error: ${e.message}"

            // Show sample data as fallback
            createSampleData()
        }
    }

    private fun updateUI(totalBalance: Double, totalExpense: Double, maxGoal: Double, expensePct: Int, income: Double) {
        try {
            // Update UI with safe formatting
            totalBalanceText.text = getString(R.string.currency_format, totalBalance)
            totalExpenseText.text = getString(R.string.currency_negative_format, totalExpense)
            maxGoalText.text = getString(R.string.currency_format, maxGoal)
            expensePercentText.text = "$expensePct%"
            incomeAmountText.text = getString(R.string.currency_format, income)
            expenseAmountText.text = getString(R.string.currency_negative_format, totalExpense)

            // Progress bar (ensure it doesn't crash with invalid values)
            progressBar.max = 100
            progressBar.progress = expensePct.coerceIn(0, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI: ${e.message}")
            // If updating UI fails, at least the app shouldn't crash
        }
    }

    private fun createSampleData() {
        try {
            Log.d(TAG, "Creating sample data")

            // Sample figures
            val totalBalance = 7783.00
            val totalExpense = 1187.40
            val maxGoal = 20000.00
            val expensePct = ((totalExpense / maxGoal) * 100).toInt()
            val income = 4000.00

            // Populate views with safe formatting
            updateUI(totalBalance, totalExpense, maxGoal, expensePct, income)

            // Sample transactions
            transactions.clear()

            // Add a month header
            transactions.add(MonthHeader("April"))

            // Add sample transactions
            transactions.add(
                Transaction(
                    id = "1",
                    title = "Salary",
                    amount = 4000.00,
                    category = "Monthly",
                    date = "18:27 - April 30",
                    iconRes = R.drawable.ic_transaction_salary
                )
            )

            transactions.add(
                Transaction(
                    id = "2",
                    title = "Groceries",
                    amount = -100.00,
                    category = "Pantry",
                    date = "17:00 - April 24",
                    iconRes = R.drawable.ic_transaction_groceries
                )
            )

            transactions.add(
                Transaction(
                    id = "3",
                    title = "Rent",
                    amount = -674.40,
                    category = "Rent",
                    date = "08:30 - April 15",
                    iconRes = R.drawable.ic_transaction_rent
                )
            )

            transactions.add(
                Transaction(
                    id = "4",
                    title = "Transport",
                    amount = -4.13,
                    category = "Fuel",
                    date = "09:30 - April 08",
                    iconRes = R.drawable.ic_transaction_transport
                )
            )

            // Notify adapter
            adapter.notifyDataSetChanged()

            Log.d(TAG, "Sample data created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating sample data: ${e.message}")
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = "Error creating sample data: ${e.message}"
        }
    }
}