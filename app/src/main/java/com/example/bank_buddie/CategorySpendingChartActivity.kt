package com.vcsma.bank_buddie

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CategorySpendingChartActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var periodSpinner: Spinner
    private lateinit var dateRangeText: TextView
    private lateinit var categorySpinner: Spinner
    private lateinit var spendingChart: BarChart
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var minGoalText: TextView
    private lateinit var maxGoalText: TextView
    private lateinit var currentSpendingText: TextView

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

    private var selectedCategoryId: String = ""
    private val categories = mutableListOf<Category>()
    private val timeLabels = mutableListOf<String>()

    // Chart colors
    private val CHART_COLORS = intArrayOf(
        Color.rgb(64, 89, 128), Color.rgb(149, 165, 124),
        Color.rgb(217, 184, 162), Color.rgb(191, 134, 134),
        Color.rgb(179, 48, 80)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_spending_chart)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        periodSpinner = findViewById(R.id.periodSpinner)
        dateRangeText = findViewById(R.id.dateRangeText)
        categorySpinner = findViewById(R.id.categorySpinner)
        spendingChart = findViewById(R.id.spendingChart)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        minGoalText = findViewById(R.id.minGoalText)
        maxGoalText = findViewById(R.id.maxGoalText)
        currentSpendingText = findViewById(R.id.currentSpendingText)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up period spinner
        setupPeriodSpinner()

        // Set up chart
        setupSpendingChart()

        // Update date range text
        updateDateRangeText()

        // Load categories for spinner
        loadCategories()
    }

    private fun setupSpendingChart() {
        spendingChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(true)
            setScaleEnabled(true)
            legend.isEnabled = true

            // X-axis setup
            val xAxis = xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setCenterAxisLabels(false)
            xAxis.setDrawGridLines(false)

            // Left Y-axis setup
            val leftAxis = axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.axisMinimum = 0f

            // Right Y-axis setup (disabled)
            axisRight.isEnabled = false

            // Animation
            animateY(1000)

            // Empty chart message
            setNoDataText("No spending data available")
        }
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
        if (selectedCategoryId.isNotEmpty()) {
            loadCategorySpendingData()
        }
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
        if (selectedCategoryId.isNotEmpty()) {
            loadCategorySpendingData()
        }
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
        if (selectedCategoryId.isNotEmpty()) {
            loadCategorySpendingData()
        }
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
        if (selectedCategoryId.isNotEmpty()) {
            loadCategorySpendingData()
        }
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
            if (selectedCategoryId.isNotEmpty()) {
                loadCategorySpendingData()
            }
        }

        dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun updateDateRangeText() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val rangeText = "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
        dateRangeText.text = rangeText
    }

    private fun loadCategories() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        spendingChart.visibility = View.GONE

        db.collection("users").document(currentUser.uid)
            .collection("categories")
            .get()
            .addOnSuccessListener { documents ->
                categories.clear()

                // Add "All Categories" option
                categories.add(Category("all", "All Categories"))

                for (document in documents) {
                    val id = document.id
                    val name = document.getString("name") ?: "Unknown"
                    categories.add(Category(id, name))
                }

                // If no categories found, add some default ones
                if (categories.size <= 1) {
                    categories.add(Category("food", "Food & Groceries"))
                    categories.add(Category("transport", "Transportation"))
                    categories.add(Category("utilities", "Utilities"))
                    categories.add(Category("entertainment", "Entertainment"))
                    categories.add(Category("shopping", "Shopping"))
                }

                // Set up category spinner
                setupCategorySpinner()

                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading categories: ${e.message}", Toast.LENGTH_SHORT).show()

                // Add default categories as fallback
                categories.clear()
                categories.add(Category("all", "All Categories"))
                categories.add(Category("food", "Food & Groceries"))
                categories.add(Category("transport", "Transportation"))
                categories.add(Category("utilities", "Utilities"))
                categories.add(Category("entertainment", "Entertainment"))
                categories.add(Category("shopping", "Shopping"))

                // Set up category spinner
                setupCategorySpinner()
            }
    }

    private fun setupCategorySpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCategoryId = categories[position].id
                loadCategorySpendingData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun loadCategorySpendingData() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        spendingChart.visibility = View.GONE

        // Convert dates to timestamps for Firestore query
        val startTimestamp = startDate.time
        val endTimestamp = endDate.time

        // Determine time intervals based on date range
        val timeIntervals = determineTimeIntervals(startDate, endDate)

        // Query for expenses in the date range
        val query = if (selectedCategoryId == "all") {
            db.collection("users").document(currentUser.uid)
                .collection("expenses")
                .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                .whereLessThanOrEqualTo("timestamp", endTimestamp)
        } else {
            db.collection("users").document(currentUser.uid)
                .collection("expenses")
                .whereEqualTo("categoryId", selectedCategoryId)
                .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                .whereLessThanOrEqualTo("timestamp", endTimestamp)
        }

        query.get().addOnSuccessListener { documents ->
            progressBar.visibility = View.GONE

            if (documents.isEmpty) {
                emptyView.visibility = View.VISIBLE
                spendingChart.visibility = View.GONE
                minGoalText.text = getString(R.string.currency_format, 0.0f)
                maxGoalText.text = getString(R.string.currency_format, 0.0f)
                currentSpendingText.text = getString(R.string.currency_format, 0.0f)
                return@addOnSuccessListener
            }

            // Process expenses and group by time interval
            val expensesByInterval = mutableMapOf<Int, Double>()
            for (interval in timeIntervals.indices) {
                expensesByInterval[interval] = 0.0
            }

            var totalSpending = 0.0

            for (document in documents) {
                try {
                    val amount = document.getDouble("amount") ?: 0.0
                    val timestamp = document.getLong("timestamp") ?: 0L

                    // Find which interval this expense belongs to
                    val expenseDate = Date(timestamp)
                    val intervalIndex = findIntervalIndex(expenseDate, timeIntervals)

                    if (intervalIndex >= 0) {
                        expensesByInterval[intervalIndex] = expensesByInterval[intervalIndex]!! + amount
                        totalSpending += amount
                    }
                } catch (e: Exception) {
                    // Skip this expense if there's an error
                }
            }

            // Load budget goals
            loadBudgetGoals(totalSpending, expensesByInterval, timeIntervals)

            // Update current spending text
            currentSpendingText.text = getString(R.string.currency_value_negative, totalSpending.toFloat())

        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error loading expenses: ${e.message}", Toast.LENGTH_SHORT).show()
            emptyView.visibility = View.VISIBLE
            spendingChart.visibility = View.GONE
        }
    }

    private fun determineTimeIntervals(start: Date, end: Date): List<Date> {
        val intervals = mutableListOf<Date>()
        val calendar = Calendar.getInstance()
        calendar.time = start

        // Determine interval type based on date range
        val rangeDays = ((end.time - start.time) / (24 * 60 * 60 * 1000)).toInt()

        when {
            rangeDays <= 14 -> {
                // Daily intervals for short ranges
                while (calendar.time.before(end) || calendar.time == end) {
                    intervals.add(calendar.time)
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
                timeLabels.clear()
                timeLabels.addAll(intervals.map { SimpleDateFormat("dd MMM", Locale.getDefault()).format(it) })
            }
            rangeDays <= 60 -> {
                // Weekly intervals for medium ranges
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                while (calendar.time.before(end)) {
                    intervals.add(calendar.time)
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
                timeLabels.clear()
                timeLabels.addAll(intervals.map {
                    val endOfWeek = Calendar.getInstance()
                    endOfWeek.time = it
                    endOfWeek.add(Calendar.DAY_OF_WEEK, 6)
                    "${SimpleDateFormat("dd MMM", Locale.getDefault()).format(it)} - ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(endOfWeek.time)}"
                })
            }
            else -> {
                // Monthly intervals for long ranges
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                while (calendar.time.before(end)) {
                    intervals.add(calendar.time)
                    calendar.add(Calendar.MONTH, 1)
                }
                timeLabels.clear()
                timeLabels.addAll(intervals.map { SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(it) })
            }
        }

        return intervals
    }

    private fun findIntervalIndex(date: Date, intervals: List<Date>): Int {
        if (intervals.isEmpty()) return -1

        // For the last interval, include the end date
        if (date.after(intervals.last()) && date.before(endDate) || date == endDate) {
            return intervals.size - 1
        }

        // For other intervals, find the appropriate one
        for (i in 0 until intervals.size - 1) {
            if ((date.after(intervals[i]) || date == intervals[i]) && date.before(intervals[i + 1])) {
                return i
            }
        }

        // If date is exactly the first interval start date
        if (date == intervals.first()) {
            return 0
        }

        return -1
    }

    private fun loadBudgetGoals(totalSpending: Double, expensesByInterval: Map<Int, Double>, timeIntervals: List<Date>) {
        val currentUser = auth.currentUser ?: return

        // Determine the budget type based on the selected period
        val budgetType = when {
            // Check if the date range is close to a month
            (endDate.time - startDate.time) > 21 * 24 * 60 * 60 * 1000 -> "monthly"
            // Check if the date range is close to a week
            (endDate.time - startDate.time) > 4 * 24 * 60 * 60 * 1000 -> "weekly"
            // Otherwise use daily budget
            else -> "daily"
        }

        // Get category-specific budget if available, otherwise use general budget
        val budgetRef = if (selectedCategoryId != "all") {
            db.collection("users").document(currentUser.uid)
                .collection("categoryBudgets")
                .document(selectedCategoryId)
        } else {
            db.collection("users").document(currentUser.uid)
                .collection("budgetGoals")
                .document(budgetType)
        }

        budgetRef.get().addOnSuccessListener { document ->
            var minGoal = 0.0
            var maxGoal = 0.0

            if (document != null && document.exists()) {
                minGoal = document.getDouble("minGoal") ?: 0.0
                maxGoal = document.getDouble("maxGoal") ?: 0.0
            } else {
                // Use default values if no budget is set
                minGoal = when (budgetType) {
                    "daily" -> 50.0
                    "weekly" -> 350.0
                    else -> 1500.0
                }
                maxGoal = when (budgetType) {
                    "daily" -> 100.0
                    "weekly" -> 700.0
                    else -> 3000.0
                }
            }

            // Adjust goals based on the number of intervals
            val intervalCount = timeIntervals.size
            if (intervalCount > 1) {
                minGoal *= intervalCount
                maxGoal *= intervalCount
            }

            // Update goal texts
            minGoalText.text = getString(R.string.currency_format, minGoal.toFloat())
            maxGoalText.text = getString(R.string.currency_format, maxGoal.toFloat())

            // Update chart with spending data and goals
            updateSpendingChart(expensesByInterval, minGoal, maxGoal)
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error loading budget goals: ${e.message}", Toast.LENGTH_SHORT).show()

            // Use default values if failed to load
            val minGoal = 1500.0
            val maxGoal = 3000.0

            // Update goal texts
            minGoalText.text = getString(R.string.currency_format, minGoal.toFloat())
            maxGoalText.text = getString(R.string.currency_format, maxGoal.toFloat())

            // Update chart with spending data and default goals
            updateSpendingChart(expensesByInterval, minGoal, maxGoal)
        }
    }

    private fun updateSpendingChart(expensesByInterval: Map<Int, Double>, minGoal: Double, maxGoal: Double) {
        // Create bar entries from expense data
        val entries = mutableListOf<BarEntry>()

        for ((index, amount) in expensesByInterval) {
            entries.add(BarEntry(index.toFloat(), amount.toFloat()))
        }

        if (entries.isEmpty()) {
            spendingChart.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }

        // Create dataset
        val dataSet = BarDataSet(entries, "Spending")
        dataSet.color = ContextCompat.getColor(this, R.color.expense_red)
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 10f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) "R${value.toInt()}" else ""
            }
        }

        // Create bar data
        val barData = BarData(dataSet)
        barData.barWidth = 0.6f

        // Set data to chart
        spendingChart.data = barData

        // Set X-axis labels
        spendingChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        spendingChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        spendingChart.xAxis.granularity = 1f
        spendingChart.xAxis.labelRotationAngle = 45f

        // Add limit lines for min and max goals
        val leftAxis = spendingChart.axisLeft
        leftAxis.removeAllLimitLines()

        // Only add limit lines if we have a single interval or aggregated view
        if (expensesByInterval.size == 1 || selectedCategoryId == "all") {
            val minLine = LimitLine(minGoal.toFloat(), "Min Goal")
            minLine.lineColor = Color.BLUE
            minLine.lineWidth = 2f
            minLine.textColor = Color.BLACK
            minLine.textSize = 12f

            val maxLine = LimitLine(maxGoal.toFloat(), "Max Goal")
            maxLine.lineColor = Color.RED
            maxLine.lineWidth = 2f
            maxLine.textColor = Color.BLACK
            maxLine.textSize = 12f

            leftAxis.addLimitLine(minLine)
            leftAxis.addLimitLine(maxLine)
        }

        // Set the max value of the y-axis to 120% of the max goal or highest expense, whichever is higher
        val maxExpense = expensesByInterval.values.maxOrNull() ?: 0.0
        val maxYValue = maxOf(maxGoal, maxExpense) * 1.2
        leftAxis.axisMaximum = maxYValue.toFloat()

        // Refresh chart
        spendingChart.invalidate()
        spendingChart.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
    }

    // Data class for category
    data class Category(val id: String, val name: String)
}