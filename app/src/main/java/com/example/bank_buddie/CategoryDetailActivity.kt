package com.vcsma.bank_buddie

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.ViewGroup

class CategoryDetailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var dateRangeText: TextView
    private lateinit var totalAmountText: TextView
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var trendChart: LineChart
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView

    private var categoryId: String = ""
    private var categoryName: String = ""
    private var startDate: Long = 0
    private var endDate: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_detail)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        dateRangeText = findViewById(R.id.dateRangeText)
        totalAmountText = findViewById(R.id.totalAmountText)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        trendChart = findViewById(R.id.trendChart)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        // Get parameters from intent
        categoryId = intent.getStringExtra("CATEGORY_ID") ?: ""
        categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Category"
        startDate = intent.getLongExtra("START_DATE", 0)
        endDate = intent.getLongExtra("END_DATE", 0)

        if (categoryId.isEmpty()) {
            Toast.makeText(this, "Invalid category", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup views
        titleText.text = categoryName
        setupDateRange()
        setupTrendChart()

        // Setup back button
        backButton.setOnClickListener {
            finish()
        }

        // Setup RecyclerView
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Load expenses
        loadCategoryExpenses()
    }

    private fun setupDateRange() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val startDateStr = dateFormat.format(Date(startDate))
        val endDateStr = dateFormat.format(Date(endDate))
        dateRangeText.text = "$startDateStr - $endDateStr"
    }

    private fun setupTrendChart() {
        // Configure the line chart appearance
        trendChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            setPinchZoom(true)
            legend.isEnabled = false

            // X-axis configuration
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)

            // Y-axis configuration
            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false

            // No data text
            setNoDataText("No expense data available")
        }
    }

    private fun loadCategoryExpenses() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        trendChart.visibility = View.GONE
        transactionsRecyclerView.visibility = View.GONE

        db.collection("users").document(currentUser.uid)
            .collection("expenses")
            .whereEqualTo("categoryId", categoryId)
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    emptyView.visibility = View.VISIBLE
                    trendChart.visibility = View.GONE
                    totalAmountText.text = getString(R.string.currency_format, 0.0f)
                    return@addOnSuccessListener
                }

                val expenses = documents.mapNotNull { document ->
                    try {
                        val id = document.id
                        val description = document.getString("description") ?: ""
                        val amount = document.getDouble("amount") ?: 0.0
                        val timestamp = document.getLong("timestamp") ?: 0L

                        Triple(id, description, Transaction(
                            id = id,
                            type = "expense",
                            category = categoryName,
                            amount = amount,
                            description = description,
                            date = timestamp
                        ))
                    } catch (e: Exception) {
                        null
                    }
                }

                // Calculate total
                val total = expenses.sumOf { it.third.amount }
                totalAmountText.text = getString(R.string.currency_value_negative, total.toFloat())

                // Prepare data for the line chart
                displayTrendChart(expenses.map { it.third })

                // Display transactions in the recycler view
                val adapter = CategoryTransactionAdapter(expenses.map { it.third }) { transaction ->
                    // Handle transaction click if needed
                    Toast.makeText(this, "Transaction: ${transaction.description}", Toast.LENGTH_SHORT).show()
                }
                transactionsRecyclerView.adapter = adapter
                transactionsRecyclerView.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                trendChart.visibility = View.GONE
                totalAmountText.text = getString(R.string.currency_format, 0.0f)
                Toast.makeText(this, "Error loading expenses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayTrendChart(transactions: List<Transaction>) {
        if (transactions.isEmpty()) {
            trendChart.visibility = View.GONE
            return
        }

        // Sort transactions by date in ascending order
        val sortedTransactions = transactions.sortedBy { it.date }

        // Group transactions by day
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val groupedByDay = sortedTransactions.groupBy { dateFormat.format(Date(it.date)) }

        // Create entries for the chart
        val entries = groupedByDay.entries.mapIndexed { index, (date, dayTransactions) ->
            // Sum amounts for this day
            val dayTotal = dayTransactions.sumOf { it.amount }
            Entry(index.toFloat(), dayTotal.toFloat())
        }

        if (entries.isEmpty()) {
            trendChart.visibility = View.GONE
            return
        }

        // Create dataset
        val dataSet = LineDataSet(entries, "Daily Expenses")
        dataSet.apply {
            color = Color.RED
            setCircleColor(Color.RED)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            setDrawValues(true)
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "R${value.toInt()}"
                }
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.RED
            fillAlpha = 30
        }

        // Set X-axis labels to dates
        val dates = groupedByDay.keys.toList()
        trendChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index >= 0 && index < dates.size) {
                    // Convert yyyy-MM-dd to dd/MM
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dates[index])
                    return SimpleDateFormat("dd/MM", Locale.getDefault()).format(date!!)
                }
                return ""
            }
        }

        // Create and set data
        val lineData = LineData(dataSet)
        trendChart.data = lineData

        // Refresh chart
        trendChart.invalidate()
        trendChart.visibility = View.VISIBLE

        // Set Y-axis maximum to slightly higher than the max value
        val maxAmount = entries.maxOf { it.y }
        trendChart.axisLeft.axisMaximum = maxAmount * 1.2f
    }

    // Transaction model (simplified version for this activity)
    data class Transaction(
        val id: String,
        val type: String,
        val category: String,
        val amount: Double,
        val description: String,
        val date: Long
    )

    // Custom adapter for category transactions
    inner class CategoryTransactionAdapter(
        private val transactions: List<Transaction>,
        private val onTransactionClick: (Transaction) -> Unit
    ) : RecyclerView.Adapter<CategoryTransactionAdapter.TransactionViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense, parent, false)
            return TransactionViewHolder(view)
        }

        override fun getItemCount(): Int = transactions.size

        override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
            holder.bind(transactions[position])
        }

        inner class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val descriptionText: TextView = view.findViewById(R.id.descriptionText)
            private val dateText: TextView = view.findViewById(R.id.dateText)
            private val amountText: TextView = view.findViewById(R.id.amountText)

            fun bind(transaction: Transaction) {
                descriptionText.text = transaction.description

                // Format date
                val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                dateText.text = dateFormat.format(Date(transaction.date))

                // Format amount
                amountText.text = getString(R.string.currency_value_negative, transaction.amount.toFloat())
                amountText.setTextColor(itemView.context.getColor(R.color.expense_red))

                // Set click listener
                itemView.setOnClickListener { onTransactionClick(transaction) }
            }
        }
    }
}