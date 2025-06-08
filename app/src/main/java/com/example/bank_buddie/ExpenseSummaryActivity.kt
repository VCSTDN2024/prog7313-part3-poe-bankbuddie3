package com.vcsma.bank_buddie

import android.content.Intent
import android.graphics.Color
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
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExpenseSummaryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var periodSpinner: Spinner
    private lateinit var dateRangeText: TextView
    private lateinit var totalAmountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyView: TextView
    private lateinit var categoryPieChart: PieChart
    private lateinit var budgetGoalChart: HorizontalBarChart
    private lateinit var categorySpendingChart: LineChart
    private lateinit var categorySpinner: Spinner

    private val categorySummaries = mutableListOf<CategorySummary>()
    private lateinit var adapter: CategorySummaryAdapter
    private var selectedCategoryId: String = "all"
    private val timeLabels = mutableListOf<String>()
    private val categories = mutableListOf<Category>()

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

    // Chart colors
    private val CHART_COLORS = intArrayOf(
        Color.rgb(64, 89, 128), Color.rgb(149, 165, 124),
        Color.rgb(217, 184, 162), Color.rgb(191, 134, 134),
        Color.rgb(179, 48, 80), Color.rgb(217, 80, 138),
        Color.rgb(254, 149, 7), Color.rgb(254, 247, 120),
        Color.rgb(106, 167, 134), Color.rgb(53, 194, 209)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_summary)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        periodSpinner = findViewById(R.id.periodSpinner)
        dateRangeText = findViewById(R.id.dateRangeText)
        totalAmountText = findViewById(R.id.totalAmountText)
        recyclerView = findViewById(R.id.categoriesRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        categoryPieChart = findViewById(R.id.categoryPieChart)
        budgetGoalChart = findViewById(R.id.budgetGoalChart)
        categorySpendingChart = findViewById(R.id.categorySpendingChart)
        categorySpinner = findViewById(R.id.categorySpinner)

        // Set up charts
        setupCategoryPieChart()
        setupBudgetGoalChart()
        setupCategorySpendingChart()

        // Set up RecyclerView
        adapter = CategorySummaryAdapter(categorySummaries) { category ->
            // Handle category click
            val intent = Intent(this, CategoryDetailActivity::class.java)
            intent.putExtra("CATEGORY_ID", category.categoryId)
            intent.putExtra("CATEGORY_NAME", category.categoryName)
            intent.putExtra("START_DATE", startDate.time)
            intent.putExtra("END_DATE", endDate.time)
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

        // Load categories for category spinner
        loadCategories()

        // Set up date range text
        updateDateRangeText()

        // Load category summaries
        loadCategorySummaries()
    }

    private fun setupCategoryPieChart() {
        // Setup pie chart appearance
        categoryPieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setExtraOffsets(5f, 10f, 5f, 5f)
            dragDecelerationFrictionCoef = 0.95f
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            animateY(1400)
            legend.isEnabled = true
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(12f)
            setNoDataText("No expense data available")
        }
    }

    private fun setupBudgetGoalChart() {
        // Setup bar chart appearance
        budgetGoalChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            setTouchEnabled(false)
            axisLeft.setDrawGridLines(false)
            axisLeft.axisMinimum = 0f // Start at 0
            axisRight.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(true)
            legend.isEnabled = false
            animateY(1400)
            setNoDataText("No budget goals found")
        }
    }

    private fun setupCategorySpendingChart() {
        categorySpendingChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            setPinchZoom(true)

            // X-axis setup
            val xAxis = xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.labelRotationAngle = 45f

            // Left Y-axis setup
            val leftAxis = axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.axisMinimum = 0f

            // Right Y-axis setup (disabled)
            axisRight.isEnabled = false

            // Legend setup
            legend.isEnabled = true

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

    private fun loadCategories() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

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
        loadCategorySummaries()
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
        loadCategorySummaries()
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
        loadCategorySummaries()
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
        loadCategorySummaries()
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
            loadCategorySummaries()
        }

        dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun updateDateRangeText() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val rangeText = "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
        dateRangeText.text = rangeText
    }

    private fun loadCategorySummaries() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        categoryPieChart.visibility = View.GONE
        budgetGoalChart.visibility = View.GONE

        // Convert dates to timestamps for Firestore query
        val startTimestamp = startDate.time
        val endTimestamp = endDate.time

        // Load budget goals for this period
        loadBudgetGoals()

        db.collection("users").document(currentUser.uid)
            .collection("expenses")
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThanOrEqualTo("timestamp", endTimestamp)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                // Group expenses by category
                val categoryMap = mutableMapOf<String, CategorySummary>()
                var totalAmount = 0.0

                for (document in documents) {
                    val expense = document.toObject(ExpenseEntry::class.java)
                    val categoryId = expense.id ?: continue
                    val categoryName = expense.categoryName ?: "Other"
                    val amount = expense.amount ?: 0.0

                    totalAmount += amount

                    if (categoryMap.containsKey(categoryId)) {
                        val summary = categoryMap[categoryId]!!
                        summary.totalAmount += amount
                        summary.expenseCount++
                    } else {
                        categoryMap[categoryId] = CategorySummary(
                            categoryId = categoryId,
                            categoryName = categoryName,
                            totalAmount = amount,
                            expenseCount = 1
                        )
                    }
                }

                // Update total amount
                totalAmountText.text = getString(R.string.currency_value_negative, totalAmount.toFloat())

                // Convert map to list and sort by amount (highest first)
                categorySummaries.clear()
                categorySummaries.addAll(categoryMap.values.sortedByDescending { it.totalAmount })

                if (categorySummaries.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    categoryPieChart.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    categoryPieChart.visibility = View.VISIBLE

                    // Update pie chart with category data
                    updateCategoryPieChart(categoryMap, totalAmount)
                }

                adapter.notifyDataSetChanged()

                // Load category spending data
                if (selectedCategoryId.isNotEmpty()) {
                    loadCategorySpendingData()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading category summaries: ${e.message}", Toast.LENGTH_SHORT).show()
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                categoryPieChart.visibility = View.GONE
                budgetGoalChart.visibility = View.GONE
            }
    }

    private fun updateCategoryPieChart(categoryMap: Map<String, CategorySummary>, totalAmount: Double) {
        // Create pie entries from category summaries
        val entries = mutableListOf<PieEntry>()

        // Get top 5 categories by expense amount
        val sortedCategories = categoryMap.values
            .sortedByDescending { it.totalAmount }
            .take(5)

        for (category in sortedCategories) {
            val percentage = (category.totalAmount / totalAmount).toFloat()
            entries.add(PieEntry(percentage, category.categoryName))
        }

        // Add "Other" entry if there are more than 5 categories
        if (categoryMap.size > 5) {
            val otherTotal = categoryMap.values
                .sortedByDescending { it.totalAmount }
                .drop(5)
                .sumOf { it.totalAmount }

            val otherPercentage = (otherTotal / totalAmount).toFloat()
            if (otherPercentage > 0) {
                entries.add(PieEntry(otherPercentage, "Other"))
            }
        }

        // Create dataset with entries
        val dataSet = PieDataSet(entries, "")
        dataSet.setColors(*CHART_COLORS)
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f

        // Format the data
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(categoryPieChart))
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.WHITE)

        // Update chart
        categoryPieChart.data = data
        categoryPieChart.centerText = "Expense\nBreakdown"
        categoryPieChart.invalidate()
    }

    private fun loadBudgetGoals() {
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

        db.collection("users").document(currentUser.uid)
            .collection("budgetGoals")
            .document(budgetType)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val minGoal = document.getDouble("minGoal") ?: 0.0
                    val maxGoal = document.getDouble("maxGoal") ?: 0.0

                    // Get total expenses for the period
                    val startTimestamp = startDate.time
                    val endTimestamp = endDate.time

                    db.collection("users").document(currentUser.uid)
                        .collection("expenses")
                        .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                        .whereLessThanOrEqualTo("timestamp", endTimestamp)
                        .get()
                        .addOnSuccessListener { documents ->
                            val totalExpenses = documents.sumOf { it.getDouble("amount") ?: 0.0 }

                            // Update budget goal chart
                            updateBudgetGoalChart(minGoal, maxGoal, totalExpenses)
                            budgetGoalChart.visibility = View.VISIBLE
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error loading expenses: ${e.message}", Toast.LENGTH_SHORT).show()
                            budgetGoalChart.visibility = View.GONE
                        }
                } else {
                    budgetGoalChart.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading budget goals: ${e.message}", Toast.LENGTH_SHORT).show()
                budgetGoalChart.visibility = View.GONE
            }
    }

    private fun updateBudgetGoalChart(minGoal: Double, maxGoal: Double, totalExpenses: Double) {
        // Create entries
        val entries = mutableListOf<BarEntry>()
        entries.add(BarEntry(0f, totalExpenses.toFloat())) // Current expense

        // Create dataset
        val dataSet = BarDataSet(entries, "Current Spending")

        // Set color based on spending vs goals
        val barColor = when {
            totalExpenses < minGoal -> Color.BLUE // Under the minimum goal (blue)
            totalExpenses <= maxGoal -> Color.GREEN // Within goal range (green)
            else -> Color.RED // Over the maximum goal (red)
        }
        dataSet.color = barColor

        // Create data object
        val data = BarData(dataSet)
        data.setValueTextSize(14f)
        data.setValueTextColor(Color.BLACK)
        data.setValueFormatter(object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "R${value.toInt()}"
            }
        })

        // Set up the chart
        budgetGoalChart.data = data

        // Add limit lines for min and max goals
        val leftAxis = budgetGoalChart.axisLeft
        leftAxis.removeAllLimitLines()

        val minLine = LimitLine(minGoal.toFloat(), "Min Goal: R${minGoal.toInt()}")
        minLine.lineColor = Color.BLUE
        minLine.lineWidth = 2f
        minLine.textColor = Color.BLACK
        minLine.textSize = 12f

        val maxLine = LimitLine(maxGoal.toFloat(), "Max Goal: R${maxGoal.toInt()}")
        maxLine.lineColor = Color.RED
        maxLine.lineWidth = 2f
        maxLine.textColor = Color.BLACK
        maxLine.textSize = 12f

        leftAxis.addLimitLine(minLine)
        leftAxis.addLimitLine(maxLine)

        // Set the max value of the y-axis to 120% of the max goal or total expenses, whichever is higher
        val maxYValue = maxOf(maxGoal, totalExpenses) * 1.2
        leftAxis.axisMaximum = maxYValue.toFloat()

        // Remove "Index" label on X axis and replace with "Current Spending"
        val xAxis = budgetGoalChart.xAxis
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "Current Spending"
            }
        }

        // Update chart
        budgetGoalChart.invalidate()
    }

    private fun loadCategorySpendingData() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        categorySpendingChart.visibility = View.GONE

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
                categorySpendingChart.visibility = View.GONE
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

            // Load budget goals and update chart
            loadCategoryBudgetGoals(totalSpending, expensesByInterval, timeIntervals)

        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error loading expenses: ${e.message}", Toast.LENGTH_SHORT).show()
            categorySpendingChart.visibility = View.GONE
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
                    "${SimpleDateFormat("dd MMM", Locale.getDefault()).format(it)}"
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

    private fun loadCategoryBudgetGoals(totalSpending: Double, expensesByInterval: Map<Int, Double>, timeIntervals: List<Date>) {
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

            // Adjust goals based on the number of intervals if showing all intervals
            val intervalCount = timeIntervals.size
            if (intervalCount > 1) {
                // For line chart showing spending over time, we don't adjust the goals
                // as each point represents spending for that specific interval
            }

            // Update chart with spending data and goals
            updateCategorySpendingChart(expensesByInterval, minGoal, maxGoal, timeIntervals)
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error loading budget goals: ${e.message}", Toast.LENGTH_SHORT).show()

            // Use default values if failed to load
            val minGoal = 1500.0
            val maxGoal = 3000.0

            // Update chart with spending data and default goals
            updateCategorySpendingChart(expensesByInterval, minGoal, maxGoal, timeIntervals)
        }
    }

    private fun updateCategorySpendingChart(expensesByInterval: Map<Int, Double>, minGoal: Double, maxGoal: Double, timeIntervals: List<Date>) {
        // Create entries from expense data
        val entries = mutableListOf<Entry>()

        for ((index, amount) in expensesByInterval) {
            entries.add(Entry(index.toFloat(), amount.toFloat()))
        }

        if (entries.isEmpty()) {
            categorySpendingChart.visibility = View.GONE
            return
        }

        // Sort entries by X value (time)
        entries.sortBy { it.x }

        // Create dataset for spending
        val spendingDataSet = LineDataSet(entries, "Spending")
        spendingDataSet.apply {
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

        // Create line data with spending dataset
        val lineData = LineData(spendingDataSet)

        // Set data to chart
        categorySpendingChart.data = lineData

        // Set X-axis labels to time intervals
        categorySpendingChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)

        // Add limit lines for min and max goals
        val leftAxis = categorySpendingChart.axisLeft
        leftAxis.removeAllLimitLines()

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

        // Set the max value of the y-axis to 120% of the max goal or highest expense, whichever is higher
        val maxExpense = expensesByInterval.values.maxOrNull() ?: 0.0
        val maxYValue = maxOf(maxGoal, maxExpense) * 1.2
        leftAxis.axisMaximum = maxYValue.toFloat()

        // Refresh chart
        categorySpendingChart.invalidate()
        categorySpendingChart.visibility = View.VISIBLE
    }
}



// Model class for category
data class Category(val id: String, val name: String)