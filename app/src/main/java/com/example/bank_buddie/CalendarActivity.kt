package com.vcsma.bank_buddie

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CalendarActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var monthText: TextView
    private lateinit var yearText: TextView
    private lateinit var prevMonthButton: ImageView
    private lateinit var nextMonthButton: ImageView
    private lateinit var calendarGrid: RecyclerView
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var spendButton: TextView
    private lateinit var categoriesButton: TextView

    private val transactions = mutableListOf<Transaction>()
    private lateinit var transactionsAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        monthText = findViewById(R.id.monthText)
        yearText = findViewById(R.id.yearText)
        prevMonthButton = findViewById(R.id.prevMonthButton)
        nextMonthButton = findViewById(R.id.nextMonthButton)
        calendarGrid = findViewById(R.id.calendarGrid)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        spendButton = findViewById(R.id.spendButton)
        categoriesButton = findViewById(R.id.categoriesButton)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up month navigation
        monthText.text = "April"
        yearText.text = "2023"

        prevMonthButton.setOnClickListener {
            // In a real app, this would change the month
            monthText.text = "March"
            loadTransactions()
        }

        nextMonthButton.setOnClickListener {
            // In a real app, this would change the month
            monthText.text = "May"
            loadTransactions()
        }

        // Set up segment control
        spendButton.setOnClickListener {
            spendButton.setBackgroundResource(R.drawable.button_segment_selected)
            categoriesButton.setBackgroundResource(R.drawable.button_segment_unselected)
        }

        categoriesButton.setOnClickListener {
            categoriesButton.setBackgroundResource(R.drawable.button_segment_selected)
            spendButton.setBackgroundResource(R.drawable.button_segment_unselected)
        }

        // Set up recycler view
        transactionsAdapter = TransactionAdapter(transactions) { transaction ->
            // Handle transaction click
        }
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionsRecyclerView.adapter = transactionsAdapter

        // Load transactions
        loadTransactions()
    }

    private fun loadTransactions() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // If not logged in, show sample data
            createSampleData()
            return
        }

        // In a real app, you would fetch transactions from Firestore
        // For now, we'll just create sample data
        createSampleData()
    }

    private fun createSampleData() {
        // Sample transactions
        transactions.clear()

        // April transactions
        transactions.add(
            Transaction(
                id = "2",
                title = "Groceries",
                amount = -100.00,
                category = "Pantry",
                date = "17:00 - April 24",
                iconRes = R.drawable.ic_groceries,
                monthHeader = "April"
            )
        )

        transactions.add(
            Transaction(
                id = "7",
                title = "Others",
                amount = 120.00,
                category = "Payments",
                date = "17:00 - April 24",
                iconRes = R.drawable.ic_car,
                monthHeader  = "April"
            )
        )

        // Notify adapter of data change
        transactionsAdapter.notifyDataSetChanged()
    }
}