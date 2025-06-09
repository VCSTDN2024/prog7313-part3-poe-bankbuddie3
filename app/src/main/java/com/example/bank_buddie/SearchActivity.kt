package com.vcsma.bank_buddie

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SearchActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var searchField: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var dateField: EditText
    private lateinit var incomeRadio: RadioButton
    private lateinit var expenseRadio: RadioButton
    private lateinit var searchButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var noResultsText: TextView
    private lateinit var spinnerProgressBar: ProgressBar

    // Adapter and data
    private val searchResults = mutableListOf<Transaction>()
    private lateinit var resultsAdapter: TransactionAdapter

    // Date handling
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Handler for timeout
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = 10000L // 10 seconds (lowercase per naming conventions)

    // Categories from Firestore
    private val categories = mutableListOf<String>()

    // Tag for logging
    private val TAG = "SearchActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_dynamic)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        initializeViews()

        // Set up RecyclerView
        setupRecyclerView()

        // Set up category spinner with dynamic categories
        loadCategoriesWithTimeout()

        // Set up date picker
        dateField.setOnClickListener {
            showDatePicker()
        }

        // Set up search field text change listener for real-time search
        setupSearchFieldListener()

        // Set up search button
        searchButton.setOnClickListener {
            performSearch()
        }

        // Set default date to today
        dateField.setText(dateFormat.format(calendar.time))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove callbacks to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        searchField = findViewById(R.id.searchField)
        categorySpinner = findViewById(R.id.categorySpinner)
        dateField = findViewById(R.id.dateField)
        incomeRadio = findViewById(R.id.incomeRadio)
        expenseRadio = findViewById(R.id.expenseRadio)
        searchButton = findViewById(R.id.searchButton)
        progressBar = findViewById(R.id.progressBar)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)
        noResultsText = findViewById(R.id.noResultsText)
        spinnerProgressBar = findViewById(R.id.spinnerProgressBar)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        resultsAdapter = TransactionAdapter(searchResults) { transaction ->
            // Handle click on a search result
            val intent = Intent(this, TransactionDetailActivity::class.java)
            intent.putExtra("TRANSACTION_ID", transaction.id)
            startActivity(intent)
        }

        resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = resultsAdapter
        }
    }

    private fun setupSearchFieldListener() {
        // Add text change listener for real-time search
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Perform search after a short delay to avoid too many requests
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    if (s?.length ?: 0 >= 2) { // Only search if at least 2 characters
                        performSearch()
                    }
                }, 500)
            }
        })
    }

    private fun loadCategoriesWithTimeout() {
        // Show spinner progress
        spinnerProgressBar.visibility = View.VISIBLE

        // Set temporary adapter with loading message
        val tempAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Loading categories...")
        )
        tempAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = tempAdapter

        // Set timeout
        handler.postDelayed({
            if (spinnerProgressBar.visibility == View.VISIBLE) {
                // Timeout occurred, load default categories
                spinnerProgressBar.visibility = View.GONE
                setupDefaultCategories()
                Toast.makeText(this, "Loading categories timed out. Using defaults.", Toast.LENGTH_SHORT).show()
            }
        }, timeout)

        // Load categories from Firestore
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // No user logged in, use default categories
            handler.removeCallbacksAndMessages(null)
            spinnerProgressBar.visibility = View.GONE
            setupDefaultCategories()
            return
        }

        // Load expense categories
        db.collection("users").document(currentUser.uid)
            .collection("expenseCategories")
            .get()
            .addOnSuccessListener { documents ->
                categories.clear()

                // Add default "All categories" option
                categories.add("All categories")

                for (document in documents) {
                    val categoryName = document.getString("name") ?: continue
                    categories.add(categoryName)
                }

                // Also load income categories
                db.collection("users").document(currentUser.uid)
                    .collection("incomeCategories")
                    .get()
                    .addOnSuccessListener { incomeDocuments ->
                        for (document in incomeDocuments) {
                            val categoryName = document.getString("name") ?: continue
                            if (!categories.contains(categoryName)) {
                                categories.add(categoryName)
                            }
                        }

                        // Cancel timeout
                        handler.removeCallbacksAndMessages(null)
                        spinnerProgressBar.visibility = View.GONE

                        // Update spinner with loaded categories
                        updateCategorySpinner()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error loading income categories: ${e.message}")

                        // Only update with expense categories
                        handler.removeCallbacksAndMessages(null)
                        spinnerProgressBar.visibility = View.GONE
                        updateCategorySpinner()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading expense categories: ${e.message}")

                // Cancel timeout
                handler.removeCallbacksAndMessages(null)
                spinnerProgressBar.visibility = View.GONE

                // Use default categories
                setupDefaultCategories()
            }
    }

    private fun updateCategorySpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
    }

    private fun setupDefaultCategories() {
        categories.clear()
        categories.addAll(listOf(
            "All categories",
            "Food",
            "Transport",
            "Rent",
            "Groceries",
            "Entertainment",
            "Salary",
            "Other"
        ))
        updateCategorySpinner()
    }

    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                dateField.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun performSearch() {
        // Get search parameters
        val searchQuery = searchField.text.toString().trim()
        val selectedCategory = categorySpinner.selectedItem?.toString() ?: "All categories"
        val dateString = dateField.text.toString()
        val isIncome = incomeRadio.isChecked

        // Show loading indicator
        progressBar.visibility = View.VISIBLE
        noResultsText.visibility = View.GONE

        // Clear previous results
        searchResults.clear()
        resultsAdapter.notifyDataSetChanged()

        // Set timeout
        handler.postDelayed({
            if (progressBar.visibility == View.VISIBLE) {
                // Search timed out
                progressBar.visibility = View.GONE
                showTimeoutDialog()
            }
        }, timeout)

        // Get current user
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Not logged in
            handler.removeCallbacksAndMessages(null)
            progressBar.visibility = View.GONE
            showError("You must be logged in to search")
            return
        }

        // Parse date
        var startDateTimestamp: Long? = null
        var endDateTimestamp: Long? = null

        if (dateString.isNotEmpty()) {
            try {
                val date = dateFormat.parse(dateString)
                if (date != null) {
                    // Create start of day
                    val startCalendar = Calendar.getInstance()
                    startCalendar.time = date
                    startCalendar.set(Calendar.HOUR_OF_DAY, 0)
                    startCalendar.set(Calendar.MINUTE, 0)
                    startCalendar.set(Calendar.SECOND, 0)
                    startDateTimestamp = startCalendar.timeInMillis

                    // Create end of day
                    val endCalendar = Calendar.getInstance()
                    endCalendar.time = date
                    endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                    endCalendar.set(Calendar.MINUTE, 59)
                    endCalendar.set(Calendar.SECOND, 59)
                    endDateTimestamp = endCalendar.timeInMillis
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing date: ${e.message}")
            }
        }

        // Create query
        val transactionsRef = db.collection("users").document(currentUser.uid)
            .collection("transactions")

        // Create different queries based on filters
        val queries = mutableListOf<com.google.firebase.firestore.Query>()

        // Base query with transaction type filter
        var baseQuery = if (isIncome) {
            transactionsRef.whereGreaterThanOrEqualTo("amount", 0)
        } else {
            transactionsRef.whereLessThan("amount", 0)
        }

        // Filter by category if not "All categories"
        if (selectedCategory != "All categories") {
            baseQuery = baseQuery.whereEqualTo("category", selectedCategory)
        }

        // Filter by date if provided
        if (startDateTimestamp != null && endDateTimestamp != null) {
            baseQuery = baseQuery.whereGreaterThanOrEqualTo("timestamp", startDateTimestamp)
                .whereLessThanOrEqualTo("timestamp", endDateTimestamp)
        }

        // Execute query
        baseQuery.get()
            .addOnSuccessListener { documents ->
                // Cancel timeout
                handler.removeCallbacksAndMessages(null)
                progressBar.visibility = View.GONE

                processSearchResults(documents, searchQuery)
            }
            .addOnFailureListener { e ->
                // Cancel timeout
                handler.removeCallbacksAndMessages(null)
                progressBar.visibility = View.GONE

                // Show error
                showError("Search failed: ${e.message}")
            }
    }

    private fun processSearchResults(documents: QuerySnapshot, searchQuery: String) {
        if (documents.isEmpty) {
            // No results
            showNoResults()
            return
        }

        // Filter results by description if search query is provided
        val filteredResults = if (searchQuery.isNotEmpty()) {
            documents.filter { doc ->
                val description = doc.getString("description") ?: ""
                description.contains(searchQuery, ignoreCase = true)
            }
        } else {
            documents.toList()
        }

        if (filteredResults.isEmpty()) {
            // No results after filtering
            showNoResults()
            return
        }

        // Process results
        for (document in filteredResults) {
            try {
                val id = document.id
                val description = document.getString("description") ?: ""
                val amount = document.getDouble("amount") ?: 0.0
                val category = document.getString("category") ?: ""
                val timestamp = document.getLong("timestamp") ?: System.currentTimeMillis()

                // Convert timestamp to readable format
                val date = Date(timestamp)
                val dateTimeFormat = SimpleDateFormat("HH:mm - MMM dd", Locale.getDefault())
                val formattedTimestamp = dateTimeFormat.format(date)

                // Determine icon based on category or amount
                val iconRes = getIconForCategory(category, amount >= 0)

                // Create transaction object with named parameters to match the data class
                val transaction = Transaction(
                    id = id,
                    title = description, // Using description as title
                    amount = amount,
                    category = category,
                    description = description, // Same value for description
                    date = formattedTimestamp,
                    iconRes = iconRes,
                    monthHeader = getMonthFromDate(date) // Extract month from date
                )

                searchResults.add(transaction)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing search result: ${e.message}")
                // Skip this result but continue with others
            }
        }

        // Update UI with results
        if (searchResults.isEmpty()) {
            showNoResults()
        } else {
            resultsRecyclerView.visibility = View.VISIBLE
            noResultsText.visibility = View.GONE
            resultsAdapter.notifyDataSetChanged()
        }
    }

    // Helper method to extract month from date
    private fun getMonthFromDate(date: Date): String {
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        return monthFormat.format(date)
    }

    private fun showNoResults() {
        resultsRecyclerView.visibility = View.GONE
        noResultsText.visibility = View.VISIBLE
        noResultsText.text = "No results found"
    }

    private fun showError(message: String) {
        resultsRecyclerView.visibility = View.GONE
        noResultsText.visibility = View.VISIBLE
        noResultsText.text = message
    }

    private fun showTimeoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Search Timeout")
            .setMessage("The search is taking longer than expected. Would you like to retry?")
            .setPositiveButton("Retry") { _, _ -> performSearch() }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    // Helper method to assign icons based on transaction category
    private fun getIconForCategory(category: String, isIncome: Boolean): Int {
        if (isIncome) {
            return R.drawable.ic_transaction_salary
        }

        return when (category.lowercase()) {
            "food", "groceries", "grocery", "supermarket" -> R.drawable.ic_transaction_groceries
            "rent", "mortgage", "housing", "accommodation" -> R.drawable.ic_transaction_rent
            "transport", "transportation", "travel", "fuel", "gas" -> R.drawable.ic_transaction_transport
            else -> R.drawable.ic_transaction_transport // Default icon
        }
    }
}
