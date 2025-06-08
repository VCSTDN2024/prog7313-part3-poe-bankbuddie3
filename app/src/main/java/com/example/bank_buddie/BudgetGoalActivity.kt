package com.vcsma.bank_buddie

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.Source
import com.vcsma.bank_buddie.models.BudgetGoal
import com.vcsma.bank_buddie.utils.ChartUtils
import com.vcsma.bank_buddie.utils.DataSyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class BudgetGoalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dataSyncManager: DataSyncManager

    private lateinit var backButton: ImageView
    private lateinit var minGoalInput: EditText
    private lateinit var maxGoalInput: EditText
    private lateinit var incomeGoalInput: EditText
    private lateinit var saveButton: Button
    private lateinit var visualizeButton: Button
    private lateinit var currentGoalText: TextView
    private lateinit var currentSpendingText: TextView
    private lateinit var incomeStatusText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var budgetProgressBar: ProgressBar
    private lateinit var minGoalLine: View
    private lateinit var maxGoalLine: View
    private lateinit var progressBarContainer: FrameLayout
    private lateinit var monthlySpendingChart: LineChart
    private lateinit var categorySpinner: Spinner
    private lateinit var timeframeSpinner: Spinner

    // Coroutine scope for managing async operations
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Reduced timeout for faster loading
    private val OPERATION_TIMEOUT = 2000L // 2 seconds timeout

    // Budget goal values
    private var minGoal = 0.0
    private var maxGoal = 0.0
    private var incomeGoal = 0.0
    private var currentSpending = 0.0
    private var currentIncome = 0.0

    // Selected category and timeframe
    private var selectedCategory = "All"
    private var selectedTimeframe = "Monthly"

    // Category list
    private val categories = mutableListOf<String>()

    // Timeframe options
    private val timeframes = listOf("Weekly", "Monthly", "Quarterly", "Yearly")

    // Cache for faster subsequent loads
    private var cachedExpenseData: List<QueryDocumentSnapshot>? = null
    private var cachedIncomeData: List<QueryDocumentSnapshot>? = null
    private var lastLoadTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_goal)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        dataSyncManager = DataSyncManager(this, db, auth)

        // Initialize views
        initializeViews()

        // Set up spinners
        setupSpinners()

        // Ensure progressBar is initially hidden
        progressBar.visibility = View.GONE

        // Setup chart with improved styling
        setupChart()

        // Setup click listeners
        setupClickListeners()

        // Setup input validation
        setupInputValidation()

        // Load data with priority on speed
        loadDataFast()
    }

    override fun onResume() {
        super.onResume()
        // Quick refresh if data is older than 30 seconds
        if (System.currentTimeMillis() - lastLoadTime > 30000) {
            refreshDataQuick()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines to prevent memory leaks
        activityScope.cancel()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        minGoalInput = findViewById(R.id.minGoalInput)
        maxGoalInput = findViewById(R.id.maxGoalInput)
        incomeGoalInput = findViewById(R.id.incomeGoalInput)
        saveButton = findViewById(R.id.saveButton)
        visualizeButton = findViewById(R.id.visualizeButton)
        currentGoalText = findViewById(R.id.currentGoalText)
        currentSpendingText = findViewById(R.id.currentSpendingText)
        incomeStatusText = findViewById(R.id.incomeStatusText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        budgetProgressBar = findViewById(R.id.budgetProgressBar)
        minGoalLine = findViewById(R.id.minGoalLine)
        maxGoalLine = findViewById(R.id.maxGoalLine)
        progressBarContainer = findViewById(R.id.progressBarContainer)
        monthlySpendingChart = findViewById(R.id.monthlySpendingChart)
        categorySpinner = findViewById(R.id.categorySpinner)
        timeframeSpinner = findViewById(R.id.timeframeSpinner)
    }

    private fun setupChart() {
        ChartUtils.setupMonthlySpendingChart(monthlySpendingChart)

        // Enhanced chart styling
        monthlySpendingChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // Improved legend
            legend.apply {
                isEnabled = true
                textColor = ContextCompat.getColor(this@BudgetGoalActivity, R.color.black)
                textSize = 12f
            }

            // Enhanced axes
            xAxis.apply {
                textColor = ContextCompat.getColor(this@BudgetGoalActivity, R.color.gray)
                setDrawGridLines(false)
                setDrawAxisLine(true)
            }

            axisLeft.apply {
                textColor = ContextCompat.getColor(this@BudgetGoalActivity, R.color.gray)
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@BudgetGoalActivity, R.color.gray_light)
            }

            axisRight.isEnabled = false
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        saveButton.setOnClickListener {
            saveBudgetGoalFast()
        }

        visualizeButton.setOnClickListener {
            val intent = Intent(this, BudgetVisualizationActivity::class.java)
            intent.putExtra("CATEGORY", selectedCategory)
            intent.putExtra("TIMEFRAME", selectedTimeframe)
            startActivity(intent)
        }
    }

    private fun setupSpinners() {
        // Setup timeframe spinner with improved styling
        val timeframeAdapter = ArrayAdapter(this, R.layout.spinner_item, timeframes)
        timeframeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        timeframeSpinner.adapter = timeframeAdapter

        // Setup listeners with debouncing for better performance
        var timeframeChangeJob: Job? = null
        timeframeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedTimeframe = timeframes[position]
                timeframeChangeJob?.cancel()
                timeframeChangeJob = activityScope.launch {
                    delay(300) // Debounce
                    loadBudgetGoalFast()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        var categoryChangeJob: Job? = null
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (categories.isNotEmpty() && position < categories.size) {
                    selectedCategory = categories[position]
                    categoryChangeJob?.cancel()
                    categoryChangeJob = activityScope.launch {
                        delay(300) // Debounce
                        loadBudgetGoalFast()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadDataFast() {
        activityScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                // Load categories and budget goals in parallel
                val categoriesDeferred = async { loadCategoriesFast() }
                val budgetGoalsDeferred = async { loadBudgetGoalFast() }

                // Wait for both to complete
                categoriesDeferred.await()
                budgetGoalsDeferred.await()

                lastLoadTime = System.currentTimeMillis()

            } catch (e: Exception) {
                showErrorMessage("Failed to load data: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun loadCategoriesFast(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext false

            categories.clear()
            categories.add("All")

            // Use cache source first for speed, then server
            val snapshot = db.collection("users").document(currentUser.uid)
                .collection("expenseCategories")
                .get(Source.CACHE)
                .await()

            // If cache is empty, try server
            val finalSnapshot = if (snapshot.isEmpty) {
                db.collection("users").document(currentUser.uid)
                    .collection("expenseCategories")
                    .get(Source.SERVER)
                    .await()
            } else snapshot

            finalSnapshot.documents.forEach { document ->
                val categoryName = document.getString("name")
                if (!categoryName.isNullOrEmpty() && !categories.contains(categoryName)) {
                    categories.add(categoryName)
                }
            }

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                val categoryAdapter = ArrayAdapter(this@BudgetGoalActivity, R.layout.spinner_item, categories)
                categoryAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                categorySpinner.adapter = categoryAdapter
            }

            true
        } catch (e: Exception) {
            // Fallback: use default categories
            withContext(Dispatchers.Main) {
                val categoryAdapter = ArrayAdapter(this@BudgetGoalActivity, R.layout.spinner_item, categories)
                categoryAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                categorySpinner.adapter = categoryAdapter
            }
            false
        }
    }

    private suspend fun loadBudgetGoalFast() = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser ?: return@withContext

            // Create the document path
            val goalDocPath = when (selectedCategory) {
                "All" -> selectedTimeframe.lowercase()
                else -> "${selectedTimeframe.lowercase()}_${selectedCategory.lowercase()}"
            }

            // Try cache first, then server
            var doc = db.collection("users")
                .document(user.uid)
                .collection("budgetGoals")
                .document(goalDocPath)
                .get(Source.CACHE)
                .await()

            if (!doc.exists()) {
                doc = db.collection("users")
                    .document(user.uid)
                    .collection("budgetGoals")
                    .document(goalDocPath)
                    .get(Source.SERVER)
                    .await()
            }

            withContext(Dispatchers.Main) {
                if (doc.exists()) {
                    minGoal = doc.getDouble("minGoal") ?: 0.0
                    maxGoal = doc.getDouble("maxGoal") ?: 0.0
                    incomeGoal = doc.getDouble("incomeGoal") ?: 0.0

                    updateGoalInputs()
                    updateGoalDisplay()
                } else {
                    clearGoalInputs()
                }

                // Load timeframe data
                loadTimeframeDataFast(user.uid)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                clearGoalInputs()
                showErrorMessage("Failed to load budget goals: ${e.message}")
            }
        }
    }

    private fun updateGoalInputs() {
        minGoalInput.setText(if (minGoal > 0) String.format("%.2f", minGoal) else "")
        maxGoalInput.setText(if (maxGoal > 0) String.format("%.2f", maxGoal) else "")
        incomeGoalInput.setText(if (incomeGoal > 0) String.format("%.2f", incomeGoal) else "")
    }

    private fun clearGoalInputs() {
        minGoalInput.setText("")
        maxGoalInput.setText("")
        incomeGoalInput.setText("")
        minGoal = 0.0
        maxGoal = 0.0
        incomeGoal = 0.0
    }

    private fun updateGoalDisplay() {
        val categoryDisplay = if (selectedCategory == "All") "" else " for $selectedCategory"
        if (minGoal > 0 || maxGoal > 0) {
            currentGoalText.text = getString(R.string.goal_display_format, selectedTimeframe, categoryDisplay, minGoal.toInt(), maxGoal.toInt())
            currentGoalText.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
        } else {
            currentGoalText.text = getString(R.string.goal_not_set_format, selectedTimeframe, categoryDisplay)
            currentGoalText.setTextColor(ContextCompat.getColor(this, R.color.gray))
        }
    }

    private fun loadTimeframeDataFast(userId: String) {
        activityScope.launch {
            try {
                val (startTime, endTime) = getTimeframeDateRange(selectedTimeframe)

                // Load expenses and income in parallel
                val expensesDeferred = async(Dispatchers.IO) {
                    loadExpensesData(userId, startTime, endTime)
                }

                val incomeDeferred = async(Dispatchers.IO) {
                    loadIncomeData(userId, startTime, endTime)
                }

                // Wait for both to complete
                val expenses = expensesDeferred.await()
                val income = incomeDeferred.await()

                // Update UI
                currentSpending = expenses.sumOf { it.getDouble("amount") ?: 0.0 }
                currentIncome = income.sumOf { it.getDouble("amount") ?: 0.0 }

                updateUI()
                updateChartWithData(expenses)

            } catch (e: Exception) {
                showErrorMessage("Failed to load spending data: ${e.message}")
            }
        }
    }

    private suspend fun loadExpensesData(userId: String, startTime: Long, endTime: Long): List<QueryDocumentSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val query = db.collection("users").document(userId)
                    .collection("expenses")
                    .whereGreaterThanOrEqualTo("timestamp", startTime)
                    .whereLessThanOrEqualTo("timestamp", endTime)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .let { baseQuery ->
                        if (selectedCategory != "All") {
                            baseQuery.whereEqualTo("category", selectedCategory)
                        } else baseQuery
                    }

                // Try cache first
                var snapshot = query.get(Source.CACHE).await()
                if (snapshot.isEmpty) {
                    snapshot = query.get(Source.SERVER).await()
                }

                snapshot.documents.filterIsInstance<QueryDocumentSnapshot>()
            } catch (e: Exception) {
                emptyList()
            }
        }

    private suspend fun loadIncomeData(userId: String, startTime: Long, endTime: Long): List<QueryDocumentSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val query = db.collection("users").document(userId)
                    .collection("income")
                    .whereGreaterThanOrEqualTo("timestamp", startTime)
                    .whereLessThanOrEqualTo("timestamp", endTime)

                // Try cache first
                var snapshot = query.get(Source.CACHE).await()
                if (snapshot.isEmpty) {
                    snapshot = query.get(Source.SERVER).await()
                }

                snapshot.documents.filterIsInstance<QueryDocumentSnapshot>()
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun updateChartWithData(expenses: List<QueryDocumentSnapshot>) {
        if (expenses.isNotEmpty()) {
            ChartUtils.createTimelineChart(
                expenses,
                monthlySpendingChart,
                selectedTimeframe,
                minGoal,
                maxGoal
            )
        }
    }

    private fun refreshDataQuick() {
        activityScope.launch {
            // Quick refresh without showing loading indicator
            try {
                val user = auth.currentUser ?: return@launch
                loadTimeframeDataFast(user.uid)
                lastLoadTime = System.currentTimeMillis()
            } catch (e: Exception) {
                // Silently fail for quick refresh
            }
        }
    }

    private fun setupInputValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateInputs()
            }
        }

        minGoalInput.addTextChangedListener(textWatcher)
        maxGoalInput.addTextChangedListener(textWatcher)
        incomeGoalInput.addTextChangedListener(textWatcher)
    }

    private fun validateInputs() {
        val minGoalStr = minGoalInput.text.toString()
        val maxGoalStr = maxGoalInput.text.toString()

        if (minGoalStr.isNotEmpty() && maxGoalStr.isNotEmpty()) {
            try {
                val minGoal = minGoalStr.toDouble()
                val maxGoal = maxGoalStr.toDouble()

                if (minGoal > maxGoal) {
                    minGoalInput.error = getString(R.string.min_goal_error)
                    saveButton.isEnabled = false
                    saveButton.alpha = 0.5f
                } else {
                    minGoalInput.error = null
                    maxGoalInput.error = null
                    saveButton.isEnabled = true
                    saveButton.alpha = 1.0f
                }
            } catch (e: NumberFormatException) {
                saveButton.isEnabled = false
                saveButton.alpha = 0.5f
            }
        } else {
            saveButton.isEnabled = minGoalStr.isNotEmpty() || maxGoalStr.isNotEmpty()
            saveButton.alpha = if (saveButton.isEnabled) 1.0f else 0.5f
        }
    }

    private fun saveBudgetGoalFast() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, getString(R.string.please_log_in_first), Toast.LENGTH_SHORT).show()
            return
        }

        val minGoalStr = minGoalInput.text.toString()
        val maxGoalStr = maxGoalInput.text.toString()
        val incomeGoalStr = incomeGoalInput.text.toString()

        if (minGoalStr.isEmpty() && maxGoalStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_min_max_goals), Toast.LENGTH_SHORT).show()
            return
        }

        activityScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                saveButton.isEnabled = false

                val minGoalValue = if (minGoalStr.isEmpty()) 0.0 else minGoalStr.toDouble()
                val maxGoalValue = if (maxGoalStr.isEmpty()) 0.0 else maxGoalStr.toDouble()
                val incomeGoalValue = if (incomeGoalStr.isEmpty()) 0.0 else incomeGoalStr.toDouble()

                if (minGoalValue > 0 && maxGoalValue > 0 && minGoalValue > maxGoalValue) {
                    Toast.makeText(this@BudgetGoalActivity, getString(R.string.min_goal_greater_than_max), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Save to Firestore
                val goalDocPath = when (selectedCategory) {
                    "All" -> selectedTimeframe.lowercase()
                    else -> "${selectedTimeframe.lowercase()}_${selectedCategory.lowercase()}"
                }

                val budgetGoal = BudgetGoal(
                    minGoal = minGoalValue,
                    maxGoal = maxGoalValue,
                    incomeGoal = incomeGoalValue,
                    category = selectedCategory,
                    timeframe = selectedTimeframe,
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    db.collection("users").document(user.uid)
                        .collection("budgetGoals")
                        .document(goalDocPath)
                        .set(budgetGoal)
                        .await()
                }

                // Update local values immediately
                minGoal = minGoalValue
                maxGoal = maxGoalValue
                incomeGoal = incomeGoalValue

                // Show success and refresh data immediately
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.budget_goal_saved_success),
                    Snackbar.LENGTH_SHORT
                ).show()

                // Immediately update UI and refresh chart data
                updateGoalDisplay()
                updateStatusText()
                updateProgressVisualization()

                // Reload data to reflect changes in chart
                loadTimeframeDataFast(user.uid)

            } catch (e: Exception) {
                showErrorMessage("Failed to save budget goal: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
            }
        }
    }

    private fun updateUI() {
        val categoryText = if (selectedCategory == "All") "" else " for $selectedCategory"

        // Enhanced currency formatting
        val currencyFormat = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "ZA"))
        currencyFormat.maximumFractionDigits = 0

        currentSpendingText.text = getString(R.string.current_spending_format, categoryText, currencyFormat.format(currentSpending))

        val incomeText = if (incomeGoal > 0) {
            "${currencyFormat.format(currentIncome)} of ${currencyFormat.format(incomeGoal)} goal"
        } else {
            currencyFormat.format(currentIncome)
        }
        incomeStatusText.text = getString(R.string.current_income_format, incomeText)

        updateStatusText()
        updateProgressVisualization()
    }

    private fun updateStatusText() {
        when {
            minGoal == 0.0 && maxGoal == 0.0 -> {
                statusText.text = getString(R.string.status_no_goals)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
            maxGoal == 0.0 && minGoal > 0 -> {
                if (currentSpending >= minGoal) {
                    statusText.text = getString(R.string.status_above_minimum, (currentSpending - minGoal).toInt())
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.income_green))
                } else {
                    statusText.text = getString(R.string.status_under_minimum, (minGoal - currentSpending).toInt())
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
                }
            }
            minGoal == 0.0 && maxGoal > 0 -> {
                if (currentSpending <= maxGoal) {
                    statusText.text = getString(R.string.status_within_budget, (maxGoal - currentSpending).toInt())
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.income_green))
                } else {
                    statusText.text = getString(R.string.status_exceeded_budget, (currentSpending - maxGoal).toInt())
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
                }
            }
            currentSpending < minGoal -> {
                statusText.text = getString(R.string.status_under_minimum, (minGoal - currentSpending).toInt())
                statusText.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            }
            currentSpending <= maxGoal -> {
                statusText.text = getString(R.string.status_within_budget, (maxGoal - currentSpending).toInt())
                statusText.setTextColor(ContextCompat.getColor(this, R.color.income_green))
            }
            else -> {
                statusText.text = getString(R.string.status_exceeded_budget, (currentSpending - maxGoal).toInt())
                statusText.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
            }
        }

        // Update income status color with enhanced logic
        updateIncomeStatusColor()
    }

    private fun updateIncomeStatusColor() {
        if (incomeGoal > 0) {
            val percentage = (currentIncome / incomeGoal * 100).toInt()
            val color = when {
                currentIncome >= incomeGoal -> ContextCompat.getColor(this, R.color.income_green)
                percentage >= 80 -> ContextCompat.getColor(this, R.color.teal_700)
                percentage >= 60 -> ContextCompat.getColor(this, R.color.orange)
                else -> ContextCompat.getColor(this, R.color.expense_red)
            }
            incomeStatusText.setTextColor(color)
        } else {
            incomeStatusText.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun updateProgressVisualization() {
        val effectiveMaxGoal = if (maxGoal > 0) maxGoal else minGoal

        if (effectiveMaxGoal <= 0) {
            budgetProgressBar.visibility = View.GONE
            minGoalLine.visibility = View.GONE
            maxGoalLine.visibility = View.GONE
            return
        }

        budgetProgressBar.visibility = View.VISIBLE

        progressBarContainer.post {
            val containerWidth = progressBarContainer.width.toFloat()
            if (containerWidth <= 0) return@post

            // Show min goal line only if min goal is set
            if (minGoal > 0) {
                minGoalLine.visibility = View.VISIBLE
                val minGoalPosition = (minGoal / effectiveMaxGoal * containerWidth).toInt()
                val minGoalParams = minGoalLine.layoutParams as FrameLayout.LayoutParams
                minGoalParams.leftMargin = minGoalPosition.coerceAtMost(containerWidth.toInt() - 4)
                minGoalLine.layoutParams = minGoalParams
            } else {
                minGoalLine.visibility = View.GONE
            }

            // Show max goal line only if max goal is set
            if (maxGoal > 0) {
                maxGoalLine.visibility = View.VISIBLE
                val maxGoalParams = maxGoalLine.layoutParams as FrameLayout.LayoutParams
                maxGoalParams.leftMargin = (containerWidth - 4).toInt()
                maxGoalLine.layoutParams = maxGoalParams
            } else {
                maxGoalLine.visibility = View.GONE
            }

            // Update progress
            val progress = ((currentSpending / effectiveMaxGoal) * 100).toInt().coerceAtMost(100)
            budgetProgressBar.progress = progress

            // Enhanced color coding
            val progressDrawable = when {
                minGoal > 0 && maxGoal > 0 -> {
                    when {
                        currentSpending < minGoal -> ContextCompat.getDrawable(this, R.drawable.goal_indicator_blue)
                        currentSpending <= maxGoal -> ContextCompat.getDrawable(this, R.drawable.goal_indicator_green)
                        else -> ContextCompat.getDrawable(this, R.drawable.goal_indicator_red)
                    }
                }
                minGoal > 0 -> {
                    if (currentSpending >= minGoal) {
                        ContextCompat.getDrawable(this, R.drawable.goal_indicator_green)
                    } else {
                        ContextCompat.getDrawable(this, R.drawable.goal_indicator_blue)
                    }
                }
                maxGoal > 0 -> {
                    if (currentSpending <= maxGoal) {
                        ContextCompat.getDrawable(this, R.drawable.goal_indicator_green)
                    } else {
                        ContextCompat.getDrawable(this, R.drawable.goal_indicator_red)
                    }
                }
                else -> ContextCompat.getDrawable(this, R.drawable.goal_indicator_green)
            }

            progressDrawable?.let { budgetProgressBar.progressDrawable = it }
        }
    }

    private fun showErrorMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getTimeframeDateRange(timeframe: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()

        when (timeframe) {
            "Weekly" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfWeek = calendar.timeInMillis

                calendar.add(Calendar.DAY_OF_WEEK, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfWeek = calendar.timeInMillis

                return Pair(startOfWeek, endOfWeek)
            }
            "Monthly" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfMonth = calendar.timeInMillis

                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfMonth = calendar.timeInMillis

                return Pair(startOfMonth, endOfMonth)
            }
            "Quarterly" -> {
                val month = calendar.get(Calendar.MONTH)
                val quarter = month / 3

                calendar.set(Calendar.MONTH, quarter * 3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfQuarter = calendar.timeInMillis

                calendar.set(Calendar.MONTH, quarter * 3 + 2)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfQuarter = calendar.timeInMillis

                return Pair(startOfQuarter, endOfQuarter)
            }
            "Yearly" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfYear = calendar.timeInMillis

                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfYear = calendar.timeInMillis

                return Pair(startOfYear, endOfYear)
            }
            else -> return getTimeframeDateRange("Monthly")
        }
    }
}