package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExpenseListActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var periodSpinner: Spinner
    private lateinit var dateRangeText: TextView
    private lateinit var totalAmountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var addExpenseButton: FloatingActionButton
    private lateinit var progressBar: View
    private lateinit var emptyView: TextView

    private val expenses = mutableListOf<ExpenseEntry>()
    private lateinit var adapter: ExpenseAdapter

    private var startDate: Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private var endDate: Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_list)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        periodSpinner = findViewById(R.id.periodSpinner)
        dateRangeText = findViewById(R.id.dateRangeText)
        totalAmountText = findViewById(R.id.totalAmountText)
        recyclerView = findViewById(R.id.expensesRecyclerView)
        addExpenseButton = findViewById(R.id.addExpenseButton)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        // Set up RecyclerView
        adapter = ExpenseAdapter(expenses) { expense ->
            // Handle expense click - view details
            val intent = Intent(this, ExpenseDetailActivity::class.java)
            intent.putExtra("EXPENSE_ID", expense.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up period spinner
        setupPeriodSpinner()

        // Set up date range text
        updateDateRangeText()

        // Set up add expense button
        addExpenseButton.setOnClickListener {
            val intent = Intent(this, ExpenseEntryActivity::class.java)
            startActivity(intent)
        }

        // Load expenses
        loadExpenses()
    }

    private fun setupPeriodSpinner() {
        val periods = arrayOf("This Month", "Last Month", "This Week", "Last Week", "Custom Range")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        periodSpinner.adapter = adapter

        periodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> setThisMonthRange()
                    1 -> setLastMonthRange()
                    2 -> setThisWeekRange()
                    3 -> setLastWeekRange()
                    4 -> showDateRangePicker()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun setThisMonthRange() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.time

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        endDate = calendar.time

        updateDateRangeText()
        loadExpenses()
    }

    private fun setLastMonthRange() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.time

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        endDate = calendar.time

        updateDateRangeText()
        loadExpenses()
    }

    private fun setThisWeekRange() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.time

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        endDate = calendar.time

        updateDateRangeText()
        loadExpenses()
    }

    private fun setLastWeekRange() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.time

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        endDate = calendar.time

        updateDateRangeText()
        loadExpenses()
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setSelection(androidx.core.util.Pair(startDate.time, endDate.time))
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val selectedStartDate = Date(selection.first)
            val selectedEndDate = Date(selection.second)

            // Set time to start of day for start date
            val startCalendar = Calendar.getInstance()
            startCalendar.time = selectedStartDate
            startCalendar.set(Calendar.HOUR_OF_DAY, 0)
            startCalendar.set(Calendar.MINUTE, 0)
            startCalendar.set(Calendar.SECOND, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
            startDate = startCalendar.time

            // Set time to end of day for end date
            val endCalendar = Calendar.getInstance()
            endCalendar.time = selectedEndDate
            endCalendar.set(Calendar.HOUR_OF_DAY, 23)
            endCalendar.set(Calendar.MINUTE, 59)
            endCalendar.set(Calendar.SECOND, 59)
            endCalendar.set(Calendar.MILLISECOND, 999)
            endDate = endCalendar.time

            updateDateRangeText()
            loadExpenses()
        }

        dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun updateDateRangeText() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val rangeText = "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
        dateRangeText.text = rangeText
    }

    private fun loadExpenses() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        // Convert dates to timestamps for Firestore query
        val startTimestamp = startDate.time
        val endTimestamp = endDate.time

        // Get expenses from Firestore
        db.collection("users").document(currentUser.uid)
            .collection("expenses")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                expenses.clear()
                var totalAmount = 0.0

                // Filter expenses by date range
                for (document in documents) {
                    val timestamp = document.getLong("timestamp") ?: 0L

                    // Only include expenses within the date range
                    if (timestamp in startTimestamp..endTimestamp) {
                        val expense = document.toObject(ExpenseEntry::class.java).apply {
                            id = document.id // Make sure to set the document ID
                        }
                        expenses.add(expense)
                        totalAmount += Math.abs(expense.amount)
                    }
                }

                // Update total amount
                totalAmountText.text = getString(R.string.currency_value, totalAmount.toFloat())

                if (expenses.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                // Use more specific update methods when possible
                adapter.notifyItemRangeChanged(0, expenses.size)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading expenses: ${e.message}", Toast.LENGTH_SHORT).show()

                // Show empty state
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
        // Reload expenses when returning to this activity
        loadExpenses()
    }
}