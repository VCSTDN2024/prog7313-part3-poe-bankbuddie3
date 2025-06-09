package com.vcsma.bank_buddie

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.vcsma.bank_buddie.utils.ChartUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetVisualizationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var categorySpinner: Spinner
    private lateinit var timeframeSpinner: Spinner
    private lateinit var chartTypeSpinner: Spinner
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var currentSpendingText: TextView
    private lateinit var currentIncomeText: TextView
    private lateinit var goalStatusText: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var lineChartCard: CardView
    private lateinit var barChartCard: CardView
    private lateinit var pieChartCard: CardView
    private lateinit var timelineChart: LineChart
    private lateinit var categoryBarChart: BarChart
    private lateinit var distributionPieChart: PieChart

    // Handler for timeout
    private val handler = Handler(Looper.getMainLooper())
    private val OPERATION_TIMEOUT = 6000L // 6 seconds timeout

    // Spinner data
    private var selectedCategory = "All"
    private var selectedTimeframe = "Monthly"
    private var selectedChartType = "Timeline"

    // Data lists
    private val categories = mutableListOf<String>()
    private val timeframes = listOf("Weekly", "Monthly", "Quarterly", "Yearly")
    private val chartTypes = listOf("Timeline", "Categories", "Distribution")

    // Budget values
    private var budgetMin = 0.0
    private var budgetMax = 0.0
    private var incomeGoal = 0.0
    private var currentSpending = 0.0
    private var currentIncome = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_visualization)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        initializeViews()

        // Setup spinners
        setupSpinners()

        // Setup charts
        setupCharts()

        // Setup back button
        backButton.setOnClickListener { finish() }

        // Get initial data from intent if available
        intent.getStringExtra("CATEGORY")?.let { selectedCategory = it }
        intent.getStringExtra("TIMEFRAME")?.let { selectedTimeframe = it }

        // Set spinner selections to match intent data
        val categoryIndex = categories.indexOf(selectedCategory).takeIf { it >= 0 } ?: 0
        val timeframeIndex = timeframes.indexOf(selectedTimeframe).takeIf { it >= 0 } ?: 0

        // Load categories and then data
        loadCategories(categoryIndex, timeframeIndex)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        categorySpinner = findViewById(R.id.categorySpinner)
        timeframeSpinner = findViewById(R.id.timeframeSpinner)
        chartTypeSpinner = findViewById(R.id.chartTypeSpinner)
        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        currentSpendingText = findViewById(R.id.currentSpendingText)
        currentIncomeText = findViewById(R.id.currentIncomeText)
        goalStatusText = findViewById(R.id.goalStatusText)
        progressBar = findViewById(R.id.progressBar)

        lineChartCard = findViewById(R.id.lineChartCard)
        barChartCard = findViewById(R.id.barChartCard)
        pieChartCard = findViewById(R.id.pieChartCard)
        timelineChart = findViewById(R.id.timelineChart)
        categoryBarChart = findViewById(R.id.categoryBarChart)
        distributionPieChart = findViewById(R.id.distributionPieChart)
    }

    private fun setupSpinners() {
        // Initialize with default "All" category
        categories.add("All")

        // Setup timeframe spinner
        val timeframeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeframes)
        timeframeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timeframeSpinner.adapter = timeframeAdapter

        // Setup chart type spinner
        val chartTypesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, chartTypes)
        chartTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        chartTypeSpinner.adapter = chartTypesAdapter

        // Setup change listeners
        setupSpinnerListeners()
    }

    private fun setupSpinnerListeners() {
        timeframeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedTimeframe = timeframes[position]
                loadBudgetData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCategory = categories[position]
                loadBudgetData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        chartTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedChartType = chartTypes[position]
                updateChartVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun setupCharts() {
        // Setup Timeline Chart
        ChartUtils.setupMonthlySpendingChart(timelineChart)

        // Setup Category Bar Chart
        with(categoryBarChart) {
            description.isEnabled = false
            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            legend.orientation = Legend.LegendOrientation.VERTICAL
            legend.setDrawInside(false)

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(true)

            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false

            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            setNoDataText(getString(R.string.no_category_data_available))
        }

        // Setup Distribution Pie Chart
        with(distributionPieChart) {
            description.isEnabled = false
            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            legend.orientation = Legend.LegendOrientation.VERTICAL
            legend.setDrawInside(false)

            setUsePercentValues(true)
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f

            setDrawCenterText(true)
            centerText = getString(R.string.expense_distribution)
            setCenterTextSize(16f)

            setTouchEnabled(true)
            rotationAngle = 0f
            isRotationEnabled = true

            setNoDataText(getString(R.string.no_distribution_data_available))
        }
    }

    private fun updateChartVisibility() {
        // Hide all charts initially
        lineChartCard.visibility = View.GONE
        barChartCard.visibility = View.GONE
        pieChartCard.visibility = View.GONE

        // Show the selected chart
        when (selectedChartType) {
            "Timeline" -> lineChartCard.visibility = View.VISIBLE
            "Categories" -> barChartCard.visibility = View.VISIBLE
            "Distribution" -> pieChartCard.visibility = View.VISIBLE
        }
    }

    private fun loadCategories(preSelectedCategoryIndex: Int = 0, preSelectedTimeframeIndex: Int = 0) {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        // Clear and add default "All" category
        categories.clear()
        categories.add("All")

        db.collection("users").document(currentUser.uid)
            .collection("expenseCategories")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val categoryName = document.getString("name")
                    if (!categoryName.isNullOrEmpty() && !categories.contains(categoryName)) {
                        categories.add(categoryName)
                    }
                }

                // Update the spinner
                val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
                categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = categoryAdapter

                // Set pre-selected values
                if (preSelectedCategoryIndex in 0 until categories.size) {
                    categorySpinner.setSelection(preSelectedCategoryIndex)
                }

                if (preSelectedTimeframeIndex in 0 until timeframes.size) {
                    timeframeSpinner.setSelection(preSelectedTimeframeIndex)
                }

                // Initial chart type is Timeline
                chartTypeSpinner.setSelection(0)
                updateChartVisibility()

                // Load budget data
                loadBudgetData()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE

                // Show error dialog
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.error_loading_categories))
                    .setMessage(getString(R.string.failed_to_load_categories, e.message))
                    .setPositiveButton(getString(R.string.retry)) { _, _ -> loadCategories() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()

                // Still setup the spinner with default "All" option
                val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
                categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = categoryAdapter
            }
    }

    private fun loadBudgetData() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        // Set timeout for loading
        handler.postDelayed({
            if (progressBar.visibility == View.VISIBLE) {
                // If still loading after timeout
                progressBar.visibility = View.GONE

                // Show error dialog
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.data_loading_timeout))
                    .setMessage(getString(R.string.loading_data_timeout_message))
                    .setPositiveButton(getString(R.string.retry)) { _, _ -> loadBudgetData() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }, OPERATION_TIMEOUT)

        // Create the document path based on category and timeframe
        val goalDocPath = when (selectedCategory) {
            "All" -> selectedTimeframe.lowercase()
            else -> "${selectedTimeframe.lowercase()}_${selectedCategory.lowercase()}"
        }

        // Update view title
        updateViewTitle()

        // Load budget goals
        db.collection("users").document(currentUser.uid)
            .collection("budgetGoals")
            .document(goalDocPath)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    budgetMin = doc.getDouble("minGoal") ?: 0.0
                    budgetMax = doc.getDouble("maxGoal") ?: 0.0
                    incomeGoal = doc.getDouble("incomeGoal") ?: 0.0

                    // Load spending data
                    loadTimeframeData(currentUser.uid)
                } else {
                    // No goals found
                    budgetMin = 0.0
                    budgetMax = 0.0
                    incomeGoal = 0.0

                    // Still load spending data
                    loadTimeframeData(currentUser.uid)
                }
            }
            .addOnFailureListener { e ->
                handler.removeCallbacksAndMessages(null)
                progressBar.visibility = View.GONE

                // Show error message
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.error_loading_budget_goal))
                    .setMessage(getString(R.string.failed_to_load_budget_goal, e.message))
                    .setPositiveButton(getString(R.string.retry)) { _, _ -> loadBudgetData() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
    }

    private fun loadTimeframeData(userId: String) {
        // Get date range based on timeframe
        val (startTime, endTime) = getTimeframeDateRange(selectedTimeframe)

        // Create the query - filter by category if needed
        var query = db.collection("users").document(userId)
            .collection("expenses")
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .whereLessThanOrEqualTo("timestamp", endTime)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        // If a specific category is selected, add category filter
        if (selectedCategory != "All") {
            query = query.whereEqualTo("category", selectedCategory)
        }

        // Execute the query
        query.get()
            .addOnSuccessListener { expenses ->
                // Get income data for the same period
                val incomeQuery = db.collection("users").document(userId)
                    .collection("income")
                    .whereGreaterThanOrEqualTo("timestamp", startTime)
                    .whereLessThanOrEqualTo("timestamp", endTime)

                incomeQuery.get().addOnSuccessListener { incomeData ->
                    // Cancel timeout
                    handler.removeCallbacksAndMessages(null)
                    progressBar.visibility = View.GONE

                    // Calculate totals
                    currentSpending = expenses.sumOf { it.getDouble("amount") ?: 0.0 }
                    currentIncome = incomeData.sumOf { it.getDouble("amount") ?: 0.0 }

                    // Update summary info
                    updateSummaryInfo()

                    // Update charts based on selected type
                    updateCharts(expenses, incomeData)
                }
                    .addOnFailureListener { e ->
                        // Cancel timeout
                        handler.removeCallbacksAndMessages(null)
                        progressBar.visibility = View.GONE

                        // Show error for income data, but still process expenses
                        currentIncome = 0.0
                        updateSummaryInfo()
                        updateCharts(expenses, null)
                    }
            }
            .addOnFailureListener { e ->
                // Cancel timeout
                handler.removeCallbacksAndMessages(null)
                progressBar.visibility = View.GONE

                // Show error message
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.error_loading_data))
                    .setMessage(getString(R.string.failed_to_load_expense_data, e.message))
                    .setPositiveButton(getString(R.string.retry)) { _, _ -> loadTimeframeData(userId) }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
    }

    private fun updateViewTitle() {
        val categoryDisplay = if (selectedCategory == "All") "" else " - $selectedCategory"
        titleText.text = getString(R.string.budget_visualization_title, selectedTimeframe, categoryDisplay)

        // Set subtitle with date range
        val (startTime, endTime) = getTimeframeDateRange(selectedTimeframe)
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        val startDate = dateFormat.format(Date(startTime))
        val endDate = dateFormat.format(Date(endTime))
        subtitleText.text = getString(R.string.date_range_format, startDate, endDate)
    }

    private fun updateSummaryInfo() {
        // Format currency
        val currencyFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        currencyFormat.maximumFractionDigits = 0

        // Update spending text
        currentSpendingText.text = getString(R.string.spending_format, currencyFormat.format(currentSpending))

        // Update income text
        val incomeText = if (incomeGoal > 0) {
            getString(R.string.income_with_goal_format, currencyFormat.format(currentIncome), currencyFormat.format(incomeGoal))
        } else {
            getString(R.string.income_format, currencyFormat.format(currentIncome))
        }
        currentIncomeText.text = incomeText

        // Update goal status
        if (budgetMax > 0) {
            val status = when {
                currentSpending < budgetMin -> {
                    val remaining = budgetMin - currentSpending
                    getString(R.string.under_minimum_goal, currencyFormat.format(remaining))
                }
                currentSpending <= budgetMax -> {
                    val remaining = budgetMax - currentSpending
                    getString(R.string.within_budget_format, currencyFormat.format(remaining))
                }
                else -> {
                    val over = currentSpending - budgetMax
                    getString(R.string.exceeded_budget, currencyFormat.format(over))
                }
            }
            goalStatusText.text = status

            // Set color based on status
            val statusColor = when {
                currentSpending < budgetMin -> ContextCompat.getColor(this, R.color.teal_700)
                currentSpending <= budgetMax -> ContextCompat.getColor(this, R.color.income_green)
                else -> ContextCompat.getColor(this, R.color.expense_red)
            }
            goalStatusText.setTextColor(statusColor)
        } else {
            goalStatusText.text = getString(R.string.no_budget_goal_set)
            goalStatusText.setTextColor(Color.GRAY)
        }
    }

    private fun updateCharts(expenses: QuerySnapshot, incomeData: QuerySnapshot?) {
        when (selectedChartType) {
            "Timeline" -> {
                // Convert to QueryDocumentSnapshot list for chart
                val expenseDocuments = expenses.documents.filterIsInstance<QueryDocumentSnapshot>()

                // Create timeline chart
                ChartUtils.createTimelineChart(
                    expenseDocuments,
                    timelineChart,
                    selectedTimeframe,
                    budgetMin,
                    budgetMax
                )

                // Add income data to timeline if available
                if (incomeData != null && !incomeData.isEmpty) {
                    addIncomeToTimelineChart(incomeData.documents.filterIsInstance<QueryDocumentSnapshot>())
                }
            }
            "Categories" -> {
                // Create category bar chart
                createCategoryBarChart(expenses)
            }
            "Distribution" -> {
                // Create distribution pie chart
                createDistributionPieChart(expenses)
            }
        }
    }

    private fun addIncomeToTimelineChart(incomeData: List<QueryDocumentSnapshot>) {
        // Only proceed if we have income data
        if (incomeData.isEmpty()) return

        // Get time divisions based on timeframe
        val (_, groupingFunction) = getTimeDivisions(selectedTimeframe)

        // Group income by time period
        val incomeByPeriod = mutableMapOf<String, Double>()
        incomeData.forEach { doc ->
            val timestamp = doc.getLong("timestamp") ?: 0L
            val amount = doc.getDouble("amount") ?: 0.0
            val periodKey = groupingFunction(timestamp)

            incomeByPeriod[periodKey] = (incomeByPeriod[periodKey] ?: 0.0) + amount
        }

        // Convert to entries for chart
        val entries = mutableListOf<Entry>()
        val periodKeys = incomeByPeriod.keys.sorted()

        periodKeys.forEachIndexed { index, key ->
            entries.add(Entry(index.toFloat(), incomeByPeriod[key]?.toFloat() ?: 0f))
        }

        // Create income dataset
        val incomeDataSet = LineDataSet(entries, getString(R.string.income))
        incomeDataSet.color = ContextCompat.getColor(this, R.color.income_green)
        incomeDataSet.setCircleColor(ContextCompat.getColor(this, R.color.income_green))
        incomeDataSet.lineWidth = 2f
        incomeDataSet.circleRadius = 4f
        incomeDataSet.setDrawCircleHole(false)
        incomeDataSet.valueTextSize = 10f
        incomeDataSet.setDrawValues(false)
        incomeDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        // Get existing data from chart
        val data = timelineChart.data ?: return

        // Add the new dataset
        val dataSets = mutableListOf<ILineDataSet>()
        for (i in 0 until data.dataSetCount) {
            dataSets.add(data.getDataSetByIndex(i))
        }
        dataSets.add(incomeDataSet)

        // Update chart with both datasets
        val lineData = LineData(dataSets)
        timelineChart.data = lineData

        // Format x-axis with period labels if needed
        val xAxis = timelineChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(periodKeys.toTypedArray())

        timelineChart.invalidate()
    }

    private fun createCategoryBarChart(expenses: QuerySnapshot) {
        // Group expenses by category
        val expensesByCategory = mutableMapOf<String, Double>()

        expenses.forEach { doc ->
            val category = doc.getString("category") ?: getString(R.string.other)
            val amount = doc.getDouble("amount") ?: 0.0

            expensesByCategory[category] = (expensesByCategory[category] ?: 0.0) + amount
        }

        // Sort categories by amount (descending)
        val sortedCategories = expensesByCategory.entries
            .sortedByDescending { it.value }
            .take(10)  // Limit to top 10 categories

        // Create bar entries
        val entries = mutableListOf<BarEntry>()
        val categoryLabels = mutableListOf<String>()

        sortedCategories.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
            categoryLabels.add(entry.key)
        }

        // Create dataset
        val dataSet = BarDataSet(entries, getString(R.string.expenses_by_category))
        dataSet.colors = getColorList(entries.size)
        dataSet.valueTextSize = 12f

        // Create bar data
        val data = BarData(dataSet)
        data.barWidth = 0.9f

        // Set data to chart
        categoryBarChart.data = data

        // Format x-axis with category labels
        val xAxis = categoryBarChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(categoryLabels.toTypedArray())
        xAxis.labelRotationAngle = 45f

        // Update chart
        categoryBarChart.invalidate()
    }

    private fun createDistributionPieChart(expenses: QuerySnapshot) {
        // Group expenses by category
        val expensesByCategory = mutableMapOf<String, Double>()

        expenses.forEach { doc ->
            val category = doc.getString("category") ?: getString(R.string.other)
            val amount = doc.getDouble("amount") ?: 0.0

            expensesByCategory[category] = (expensesByCategory[category] ?: 0.0) + amount
        }

        // Create pie entries
        val entries = mutableListOf<PieEntry>()

        // Add entries - combine small categories into "Other"
        val threshold = currentSpending * 0.03  // Categories less than 3% go to "Other"
        var otherTotal = 0.0

        expensesByCategory.forEach { (category, amount) ->
            if (amount >= threshold) {
                entries.add(PieEntry(amount.toFloat(), category))
            } else {
                otherTotal += amount
            }
        }

        // Add "Other" category if needed
        if (otherTotal > 0) {
            entries.add(PieEntry(otherTotal.toFloat(), getString(R.string.other)))
        }

        // Create dataset
        val dataSet = PieDataSet(entries, getString(R.string.expense_distribution))
        dataSet.colors = getColorList(entries.size)
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.valueTextSize = 12f
        dataSet.valueFormatter = PercentFormatter(distributionPieChart)

        // Create pie data
        val data = PieData(dataSet)

        // Set data to chart
        distributionPieChart.data = data

        // Update chart
        distributionPieChart.invalidate()
    }

    private fun getColorList(size: Int): List<Int> {
        val colors = mutableListOf(
            Color.rgb(64, 89, 128),  // Dark blue
            Color.rgb(149, 165, 124),  // Sage green
            Color.rgb(217, 184, 162),  // Beige
            Color.rgb(191, 134, 134),  // Dusty rose
            Color.rgb(179, 48, 80),   // Dark raspberry
            Color.rgb(193, 37, 82),   // Berry
            Color.rgb(255, 102, 0),   // Orange
            Color.rgb(245, 199, 0),   // Yellow
            Color.rgb(106, 150, 31),  // Green
            Color.rgb(179, 100, 53)   // Brown
        )

        // Add more colors if needed by cycling through
        while (colors.size < size) {
            colors.addAll(colors.take(size - colors.size))
        }

        return colors.take(size)
    }

    private fun getTimeDivisions(timeframe: String): Pair<SimpleDateFormat, (Long) -> String> {
        return when (timeframe) {
            "Weekly" -> {
                val format = SimpleDateFormat("EEE", Locale.getDefault())
                val groupingFunction: (Long) -> String = { timestamp ->
                    format.format(Date(timestamp))
                }
                Pair(format, groupingFunction)
            }
            "Monthly" -> {
                val format = SimpleDateFormat("d MMM", Locale.getDefault())
                val groupingFunction: (Long) -> String = { timestamp ->
                    // Group by weeks in month
                    val date = Date(timestamp)
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
                    getString(R.string.week_format, weekOfMonth)
                }
                Pair(format, groupingFunction)
            }
            "Quarterly" -> {
                val format = SimpleDateFormat("MMM", Locale.getDefault())
                val groupingFunction: (Long) -> String = { timestamp ->
                    format.format(Date(timestamp))
                }
                Pair(format, groupingFunction)
            }
            "Yearly" -> {
                val format = SimpleDateFormat("MMM", Locale.getDefault())
                val groupingFunction: (Long) -> String = { timestamp ->
                    format.format(Date(timestamp))
                }
                Pair(format, groupingFunction)
            }
            else -> {
                // Default to daily format
                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                val groupingFunction: (Long) -> String = { timestamp ->
                    format.format(Date(timestamp))
                }
                Pair(format, groupingFunction)
            }
        }
    }

    private fun getTimeframeDateRange(timeframe: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()

        when (timeframe) {
            "Weekly" -> {
                // Start from the beginning of the current week
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfWeek = calendar.timeInMillis

                // End at the end of the current week
                calendar.add(Calendar.DAY_OF_WEEK, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfWeek = calendar.timeInMillis

                return Pair(startOfWeek, endOfWeek)
            }
            "Monthly" -> {
                // Start from the beginning of the current month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfMonth = calendar.timeInMillis

                // End at the end of the current month
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfMonth = calendar.timeInMillis

                return Pair(startOfMonth, endOfMonth)
            }
            "Quarterly" -> {
                // Determine the current quarter
                val month = calendar.get(Calendar.MONTH)
                val quarter = month / 3

                // Set to the start of the quarter
                calendar.set(Calendar.MONTH, quarter * 3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfQuarter = calendar.timeInMillis

                // Set to the end of the quarter
                calendar.set(Calendar.MONTH, quarter * 3 + 2)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfQuarter = calendar.timeInMillis

                return Pair(startOfQuarter, endOfQuarter)
            }
            "Yearly" -> {
                // Start from the beginning of the current year
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfYear = calendar.timeInMillis

                // End at the end of the current year
                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfYear = calendar.timeInMillis

                return Pair(startOfYear, endOfYear)
            }
            else -> {
                // Default to monthly if unknown timeframe
                return getTimeframeDateRange("Monthly")
            }
        }
    }
}