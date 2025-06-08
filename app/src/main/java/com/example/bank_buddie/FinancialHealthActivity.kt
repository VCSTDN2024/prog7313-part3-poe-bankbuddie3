package com.vcsma.bank_buddie

import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.ViewGroup
import android.Manifest
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FinancialHealthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var scoreCircularProgress: CircularProgressIndicator
    private lateinit var scoreText: TextView
    private lateinit var scoreLabel: TextView
    private lateinit var trendChart: LineChart
    private lateinit var insightsRecyclerView: RecyclerView
    private lateinit var recommendationsRecyclerView: RecyclerView
    private lateinit var challengesRecyclerView: RecyclerView
    private lateinit var generateReportButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var lastUpdatedText: TextView

    // Financial metrics
    private var financialHealthScore = 0
    private var savingsRate = 0.0
    private var debtToIncomeRatio = 0.0
    private var budgetAdherence = 0.0
    private var emergencyFundStatus = 0.0
    private var expensesDiversification = 0.0

    // Permission request code
    private val STORAGE_PERMISSION_CODE = 101

    // FIXED: Add timeout handler
    private val handler = Handler(Looper.getMainLooper())
    private val OPERATION_TIMEOUT = 5000L // 5 seconds timeout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_financial_health)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        scoreCircularProgress = findViewById(R.id.scoreCircularProgress)
        scoreText = findViewById(R.id.scoreText)
        scoreLabel = findViewById(R.id.scoreLabel)
        trendChart = findViewById(R.id.trendChart)
        insightsRecyclerView = findViewById(R.id.insightsRecyclerView)
        recommendationsRecyclerView = findViewById(R.id.recommendationsRecyclerView)
        challengesRecyclerView = findViewById(R.id.challengesRecyclerView)
        generateReportButton = findViewById(R.id.generateReportButton)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        lastUpdatedText = findViewById(R.id.lastUpdatedText)

        // Setup back button
        backButton.setOnClickListener { finish() }

        // Setup RecyclerViews
        setupRecyclerViews()

        // Setup chart
        setupTrendChart()

        // Setup generate report button
        generateReportButton.setOnClickListener {
            if (checkStoragePermission()) {
                Toast.makeText(this, "Generating your financial health report...", Toast.LENGTH_SHORT).show()
                generateFinancialHealthReport()
            } else {
                requestStoragePermission()
            }
        }

        // Load financial health data
        loadFinancialHealthData()
    }

    override fun onDestroy() {
        super.onDestroy()
        // FIXED: Clean up handler to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // For Android 10+ we don't need storage permission for app-specific files
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Generating your financial health report...", Toast.LENGTH_SHORT).show()
                generateFinancialHealthReport()
            } else {
                Toast.makeText(this, "Storage permission is required to save the report", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerViews() {
        // Insights RecyclerView
        insightsRecyclerView.layoutManager = LinearLayoutManager(this)
        insightsRecyclerView.setHasFixedSize(true)

        // Recommendations RecyclerView
        recommendationsRecyclerView.layoutManager = LinearLayoutManager(this)
        recommendationsRecyclerView.setHasFixedSize(true)

        // Challenges RecyclerView
        challengesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        challengesRecyclerView.setHasFixedSize(true)
    }

    private fun setupTrendChart() {
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
            axisLeft.axisMaximum = 100f
            axisRight.isEnabled = false

            // No data text
            setNoDataText("No financial health history available")
        }
    }

    private fun loadFinancialHealthData() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        // FIXED: Set timeout for loading
        handler.postDelayed({
            if (progressBar.visibility == View.VISIBLE) {
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Loading timed out. Please try again."
                Toast.makeText(this, "Loading timed out. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }, OPERATION_TIMEOUT)

        // First, check if we have a stored financial health score
        db.collection("users").document(currentUser.uid)
            .collection("financialHealth")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                // FIXED: Cancel timeout
                handler.removeCallbacksAndMessages(null)

                if (!documents.isEmpty) {
                    // We have a stored score, use it
                    val doc = documents.documents[0]
                    financialHealthScore = doc.getLong("score")?.toInt() ?: 0
                    savingsRate = doc.getDouble("savingsRate") ?: 0.0
                    debtToIncomeRatio = doc.getDouble("debtToIncomeRatio") ?: 0.0
                    budgetAdherence = doc.getDouble("budgetAdherence") ?: 0.0
                    emergencyFundStatus = doc.getDouble("emergencyFundStatus") ?: 0.0
                    expensesDiversification = doc.getDouble("expensesDiversification") ?: 0.0

                    // Format last updated date
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    lastUpdatedText.text = "Last updated: ${dateFormat.format(Date(timestamp))}"

                    // FIXED: Update UI immediately with existing score
                    updateUI()

                    // Load historical data for the chart
                    loadHistoricalScores()
                } else {
                    // No stored score, calculate a new one
                    calculateFinancialHealthScore()
                }
            }
            .addOnFailureListener { e ->
                // FIXED: Cancel timeout
                handler.removeCallbacksAndMessages(null)

                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Error loading financial health data: ${e.message}"
                Toast.makeText(this, "Error loading financial health data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateFinancialHealthScore() {
        val currentUser = auth.currentUser ?: return

        // Get income data
        val incomeTask = db.collection("users").document(currentUser.uid)
            .collection("income")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .get()

        // Get expenses data
        val expensesTask = db.collection("users").document(currentUser.uid)
            .collection("expenses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get()

        // Get budget goals
        val budgetGoalsTask = db.collection("users").document(currentUser.uid)
            .collection("budgetGoals")
            .document("monthly")
            .get()

        // Get savings data
        val savingsTask = db.collection("users").document(currentUser.uid)
            .collection("savings")
            .get()

        // When all tasks complete
        com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(
            incomeTask, expensesTask, budgetGoalsTask, savingsTask
        ).addOnSuccessListener { results ->
            val incomeSnapshot = results[0] as com.google.firebase.firestore.QuerySnapshot
            val expensesSnapshot = results[1] as com.google.firebase.firestore.QuerySnapshot
            val budgetGoalsSnapshot = results[2] as com.google.firebase.firestore.DocumentSnapshot
            val savingsSnapshot = results[3] as com.google.firebase.firestore.QuerySnapshot

            // Calculate total income
            val totalIncome = incomeSnapshot.documents.sumOf { it.getDouble("amount") ?: 0.0 }

            // Calculate total expenses
            val totalExpenses = expensesSnapshot.documents.sumOf { it.getDouble("amount") ?: 0.0 }

            // Calculate savings rate (if income > 0)
            savingsRate = if (totalIncome > 0) {
                val savings = totalIncome - totalExpenses
                (savings / totalIncome) * 100
            } else {
                0.0
            }

            // Calculate debt-to-income ratio (mock data for now)
            // In a real app, you would get actual debt data
            debtToIncomeRatio = 30.0 // Assuming 30% for now

            // Calculate budget adherence
            budgetAdherence = if (budgetGoalsSnapshot.exists()) {
                val minGoal = budgetGoalsSnapshot.getDouble("minGoal") ?: 0.0
                val maxGoal = budgetGoalsSnapshot.getDouble("maxGoal") ?: 0.0

                if (maxGoal > 0 && totalExpenses > 0) {
                    val adherenceScore = if (totalExpenses < minGoal) {
                        // Under minimum goal
                        70.0 + (totalExpenses / minGoal) * 30.0
                    } else if (totalExpenses <= maxGoal) {
                        // Within budget goals (best score)
                        100.0
                    } else {
                        // Over maximum goal
                        max(0.0, 100.0 - ((totalExpenses - maxGoal) / maxGoal) * 100.0)
                    }
                    min(100.0, adherenceScore)
                } else {
                    50.0 // Default if no proper goals are set
                }
            } else {
                50.0 // Default if no budget goals
            }

            // Calculate emergency fund status
            val totalSavings = savingsSnapshot.documents.sumOf { it.getDouble("amount") ?: 0.0 }
            val monthlyExpenses = totalExpenses / 3 // Assuming the expenses are for 3 months

            emergencyFundStatus = if (monthlyExpenses > 0) {
                val monthsCovered = totalSavings / monthlyExpenses
                min(100.0, (monthsCovered / 6.0) * 100.0) // 6 months is ideal
            } else {
                0.0
            }

            // Calculate expenses diversification
            val categories = expensesSnapshot.documents.mapNotNull { it.getString("categoryId") }
            val categoryCount = categories.distinct().size
            expensesDiversification = min(100.0, (categoryCount / 10.0) * 100.0) // 10+ categories is ideal

            // Calculate overall financial health score (weighted average)
            financialHealthScore = (
                    (savingsRate * 0.25) +
                            ((100 - debtToIncomeRatio) * 0.2) +
                            (budgetAdherence * 0.25) +
                            (emergencyFundStatus * 0.2) +
                            (expensesDiversification * 0.1)
                    ).toInt()

            // FIXED: Ensure score is within 0-100 range
            financialHealthScore = financialHealthScore.coerceIn(0, 100)

            // Store the calculated score
            storeFinancialHealthScore()

            // Update UI
            updateUI()
        }.addOnFailureListener { e ->
            // FIXED: Cancel timeout
            handler.removeCallbacksAndMessages(null)

            progressBar.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = "Error calculating financial health: ${e.message}"
            Toast.makeText(this, "Error calculating financial health", Toast.LENGTH_SHORT).show()

            // FIXED: Set default score and update UI anyway
            financialHealthScore = 50
            updateUI()
        }
    }

    private fun storeFinancialHealthScore() {
        val currentUser = auth.currentUser ?: return
        val now = System.currentTimeMillis()

        val financialHealthData = hashMapOf(
            "score" to financialHealthScore,
            "savingsRate" to savingsRate,
            "debtToIncomeRatio" to debtToIncomeRatio,
            "budgetAdherence" to budgetAdherence,
            "emergencyFundStatus" to emergencyFundStatus,
            "expensesDiversification" to expensesDiversification,
            "timestamp" to now
        )

        db.collection("users").document(currentUser.uid)
            .collection("financialHealth")
            .add(financialHealthData)
            .addOnSuccessListener {
                // Format last updated date
                val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                lastUpdatedText.text = "Last updated: ${dateFormat.format(Date(now))}"

                // Load historical data for the chart
                loadHistoricalScores()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error storing financial health data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadHistoricalScores() {
        val currentUser = auth.currentUser ?: return

        // Get the last 6 months of scores
        val sixMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -6)
        }.timeInMillis

        db.collection("users").document(currentUser.uid)
            .collection("financialHealth")
            .whereGreaterThanOrEqualTo("timestamp", sixMonthsAgo)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // No historical data yet
                    trendChart.setNoDataText("Not enough historical data yet")
                    trendChart.invalidate()
                } else {
                    // Process historical data for chart
                    val entries = mutableListOf<Entry>()
                    val dateLabels = mutableListOf<String>()
                    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

                    documents.forEachIndexed { index, document ->
                        val score = document.getLong("score")?.toFloat() ?: 0f
                        val timestamp = document.getLong("timestamp") ?: 0L

                        entries.add(Entry(index.toFloat(), score))
                        dateLabels.add(dateFormat.format(Date(timestamp)))
                    }

                    // Create dataset
                    val dataSet = LineDataSet(entries, "Financial Health Score")
                    dataSet.apply {
                        color = ContextCompat.getColor(this@FinancialHealthActivity, R.color.teal_700)
                        setCircleColor(ContextCompat.getColor(this@FinancialHealthActivity, R.color.teal_700))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(true)
                        valueTextSize = 10f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        setDrawFilled(true)
                        fillColor = ContextCompat.getColor(this@FinancialHealthActivity, R.color.teal_700)
                        fillAlpha = 30
                    }

                    // Set X-axis labels to dates
                    trendChart.xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < dateLabels.size) dateLabels[index] else ""
                        }
                    }

                    // Create and set data
                    val lineData = LineData(dataSet)
                    trendChart.data = lineData

                    // Refresh chart
                    trendChart.invalidate()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading historical data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI() {
        progressBar.visibility = View.GONE

        // FIXED: Make sure score is set on the progress indicator
        scoreCircularProgress.max = 100

        // Animate the score
        animateScore(financialHealthScore)

        // Update score label based on score value
        updateScoreLabel()

        // Generate insights
        val insights = generateInsights()
        insightsRecyclerView.adapter = InsightsAdapter(insights)

        // Generate recommendations
        val recommendations = generateRecommendations()
        recommendationsRecyclerView.adapter = RecommendationsAdapter(recommendations)

        // Generate challenges
        val challenges = generateChallenges()
        challengesRecyclerView.adapter = ChallengesAdapter(challenges)
    }

    private fun animateScore(targetScore: Int) {
        // FIXED: Ensure score text is visible immediately
        scoreText.text = "0"

        val animator = ValueAnimator.ofInt(0, targetScore)
        animator.duration = 1500
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            scoreText.text = animatedValue.toString()
            scoreCircularProgress.progress = animatedValue
        }
        animator.start()
    }

    private fun updateScoreLabel() {
        when (financialHealthScore) {
            in 0..20 -> {
                scoreLabel.text = "Critical"
                scoreLabel.setTextColor(Color.RED)
                scoreCircularProgress.setIndicatorColor(Color.RED)
            }
            in 21..40 -> {
                scoreLabel.text = "Poor"
                scoreLabel.setTextColor(ContextCompat.getColor(this, R.color.orange))
                scoreCircularProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.orange))
            }
            in 41..60 -> {
                scoreLabel.text = "Fair"
                scoreLabel.setTextColor(ContextCompat.getColor(this, R.color.yellow))
                scoreCircularProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.yellow))
            }
            in 61..80 -> {
                scoreLabel.text = "Good"
                scoreLabel.setTextColor(ContextCompat.getColor(this, R.color.light_green))
                scoreCircularProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.light_green))
            }
            else -> {
                scoreLabel.text = "Excellent"
                scoreLabel.setTextColor(ContextCompat.getColor(this, R.color.green))
                scoreCircularProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.green))
            }
        }
    }

    private fun generateInsights(): List<Insight> {
        val insights = mutableListOf<Insight>()

        // Savings Rate Insight
        val savingsRateInsight = when {
            savingsRate < 0 -> Insight(
                "Negative Savings Rate",
                "You're spending more than you earn. This is unsustainable long-term.",
                R.drawable.ic_savings_rate,
                InsightType.CRITICAL
            )
            savingsRate < 10 -> Insight(
                "Low Savings Rate",
                "Your savings rate of ${savingsRate.toInt()}% is below the recommended 15-20%.",
                R.drawable.ic_savings_rate,
                InsightType.WARNING
            )
            savingsRate < 20 -> Insight(
                "Moderate Savings Rate",
                "Your savings rate of ${savingsRate.toInt()}% is good, but could be improved.",
                R.drawable.ic_savings_rate,
                InsightType.GOOD
            )
            else -> Insight(
                "Excellent Savings Rate",
                "Your savings rate of ${savingsRate.toInt()}% is excellent! Keep it up!",
                R.drawable.ic_savings_rate,
                InsightType.EXCELLENT
            )
        }
        insights.add(savingsRateInsight)

        // Debt-to-Income Ratio Insight
        val dtiInsight = when {
            debtToIncomeRatio > 50 -> Insight(
                "High Debt-to-Income Ratio",
                "Your DTI ratio of ${debtToIncomeRatio.toInt()}% is concerning. Focus on reducing debt.",
                R.drawable.ic_debt_ratio,
                InsightType.CRITICAL
            )
            debtToIncomeRatio > 36 -> Insight(
                "Elevated Debt-to-Income Ratio",
                "Your DTI ratio of ${debtToIncomeRatio.toInt()}% is above the recommended 36%.",
                R.drawable.ic_debt_ratio,
                InsightType.WARNING
            )
            debtToIncomeRatio > 20 -> Insight(
                "Moderate Debt-to-Income Ratio",
                "Your DTI ratio of ${debtToIncomeRatio.toInt()}% is within acceptable limits.",
                R.drawable.ic_debt_ratio,
                InsightType.GOOD
            )
            else -> Insight(
                "Low Debt-to-Income Ratio",
                "Your DTI ratio of ${debtToIncomeRatio.toInt()}% is excellent!",
                R.drawable.ic_debt_ratio,
                InsightType.EXCELLENT
            )
        }
        insights.add(dtiInsight)

        // Budget Adherence Insight
        val budgetInsight = when {
            budgetAdherence < 50 -> Insight(
                "Poor Budget Adherence",
                "You're significantly exceeding your budget goals.",
                R.drawable.ic_budget_adherence,
                InsightType.CRITICAL
            )
            budgetAdherence < 80 -> Insight(
                "Moderate Budget Adherence",
                "You're somewhat following your budget, but there's room for improvement.",
                R.drawable.ic_budget_adherence,
                InsightType.WARNING
            )
            budgetAdherence < 95 -> Insight(
                "Good Budget Adherence",
                "You're following your budget well!",
                R.drawable.ic_budget_adherence,
                InsightType.GOOD
            )
            else -> Insight(
                "Excellent Budget Adherence",
                "You're sticking to your budget perfectly!",
                R.drawable.ic_budget_adherence,
                InsightType.EXCELLENT
            )
        }
        insights.add(budgetInsight)

        // Emergency Fund Insight
        val emergencyFundInsight = when {
            emergencyFundStatus < 25 -> Insight(
                "Insufficient Emergency Fund",
                "Your emergency fund covers less than 1.5 months of expenses.",
                R.drawable.ic_emergency_fund,
                InsightType.CRITICAL
            )
            emergencyFundStatus < 50 -> Insight(
                "Building Emergency Fund",
                "Your emergency fund covers about 3 months of expenses.",
                R.drawable.ic_emergency_fund,
                InsightType.WARNING
            )
            emergencyFundStatus < 75 -> Insight(
                "Good Emergency Fund",
                "Your emergency fund covers about 4.5 months of expenses.",
                R.drawable.ic_emergency_fund,
                InsightType.GOOD
            )
            else -> Insight(
                "Robust Emergency Fund",
                "Your emergency fund covers 6+ months of expenses. Well done!",
                R.drawable.ic_emergency_fund,
                InsightType.EXCELLENT
            )
        }
        insights.add(emergencyFundInsight)

        return insights
    }

    private fun generateRecommendations(): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        // Based on Savings Rate
        if (savingsRate < 15) {
            recommendations.add(
                Recommendation(
                    "Increase Savings Rate",
                    "Aim to save at least 15-20% of your income. Review your expenses to find areas to cut back.",
                    R.drawable.ic_increase_savings
                )
            )
        }

        // Based on Debt-to-Income Ratio
        if (debtToIncomeRatio > 36) {
            recommendations.add(
                Recommendation(
                    "Reduce Debt",
                    "Your debt-to-income ratio is high. Focus on paying down high-interest debt first.",
                    R.drawable.ic_reduce_debt
                )
            )
        }

        // Based on Budget Adherence
        if (budgetAdherence < 80) {
            recommendations.add(
                Recommendation(
                    "Improve Budget Adherence",
                    "You're frequently exceeding your budget. Consider adjusting your budget or finding ways to reduce spending.",
                    R.drawable.ic_budget_improvement
                )
            )
        }

        // Based on Emergency Fund
        if (emergencyFundStatus < 50) {
            recommendations.add(
                Recommendation(
                    "Build Emergency Fund",
                    "Aim to have 3-6 months of expenses saved in an easily accessible account.",
                    R.drawable.ic_build_emergency_fund
                )
            )
        }

        // Based on Expenses Diversification
        if (expensesDiversification < 60) {
            recommendations.add(
                Recommendation(
                    "Diversify Expenses",
                    "Your spending is concentrated in few categories. A more balanced budget can help identify areas to save.",
                    R.drawable.ic_diversify_expenses
                )
            )
        }

        // Add general recommendations if we have few specific ones
        if (recommendations.size < 3) {
            recommendations.add(
                Recommendation(
                    "Review Subscriptions",
                    "Audit your recurring subscriptions and cancel those you don't use regularly.",
                    R.drawable.ic_subscription_review
                )
            )

            recommendations.add(
                Recommendation(
                    "Automate Savings",
                    "Set up automatic transfers to your savings account on payday.",
                    R.drawable.ic_automate_savings
                )
            )
        }

        return recommendations
    }

    private fun generateChallenges(): List<Challenge> {
        val challenges = mutableListOf<Challenge>()

        // Savings Challenge
        challenges.add(
            Challenge(
                "30-Day Savings Boost",
                "Increase your savings rate by 5% for the next 30 days",
                "Ongoing",
                R.drawable.ic_savings_challenge
            )
        )

        // No-Spend Challenge
        challenges.add(
            Challenge(
                "No-Spend Weekend",
                "Go an entire weekend without spending any money",
                "Not Started",
                R.drawable.ic_no_spend_challenge
            )
        )

        // Budget Challenge
        challenges.add(
            Challenge(
                "Perfect Budget Month",
                "Stay within your budget for all categories for a full month",
                "Not Started",
                R.drawable.ic_budget_challenge
            )
        )

        // Debt Reduction Challenge
        challenges.add(
            Challenge(
                "Debt Crusher",
                "Pay off an extra 10% of your smallest debt this month",
                "Not Started",
                R.drawable.ic_debt_challenge
            )
        )

        // Emergency Fund Challenge
        challenges.add(
            Challenge(
                "Emergency Fund Builder",
                "Add one week's worth of expenses to your emergency fund",
                "Not Started",
                R.drawable.ic_emergency_fund_challenge
            )
        )

        return challenges
    }

    private fun generateFinancialHealthReport() {
        val currentUser = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE

        // Get the user's name
        val userName = currentUser.displayName ?: "User"

        // Get transactions data for the report
        db.collection("users").document(currentUser.uid)
            .collection("transactions")
            .get()
            .addOnSuccessListener { documents ->
                // Process expenses by category
                val expensesByCategory = mutableMapOf<String, Double>()
                var totalExpenses = 0.0
                var totalIncome = 0.0

                for (document in documents) {
                    val amount = document.getDouble("amount") ?: 0.0
                    val category = document.getString("category") ?: "Uncategorized"

                    if (amount < 0) {
                        // This is an expense
                        val absAmount = abs(amount)
                        expensesByCategory[category] = (expensesByCategory[category] ?: 0.0) + absAmount
                        totalExpenses += absAmount
                    } else {
                        // This is income
                        totalIncome += amount
                    }
                }

                // Generate the PDF report
                val period = "Last 3 Months" // You can make this dynamic based on your data
                val pdfGenerator = ExpenseReportGenerator(this)
                val reportFile = pdfGenerator.generateExpenseReport(
                    userName,
                    financialHealthScore,
                    expensesByCategory,
                    totalIncome,
                    totalExpenses,
                    period
                )

                progressBar.visibility = View.GONE

                if (reportFile != null) {
                    // Show success dialog
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Financial Health Report")
                        .setMessage("Your financial health report has been generated and saved to your Downloads folder.\n\nWould you like to view it now?")
                        .setPositiveButton("View Report") { _, _ ->
                            pdfGenerator.sharePdfReport(this, reportFile)
                        }
                        .setNegativeButton("Later", null)
                        .show()
                } else {
                    Toast.makeText(this, "Error generating report", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading transaction data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Data classes for RecyclerView adapters
    data class Insight(
        val title: String,
        val description: String,
        val iconResId: Int,
        val type: InsightType
    )

    enum class InsightType {
        CRITICAL, WARNING, GOOD, EXCELLENT
    }

    data class Recommendation(
        val title: String,
        val description: String,
        val iconResId: Int
    )

    data class Challenge(
        val title: String,
        val description: String,
        val status: String,
        val iconResId: Int
    )

    // RecyclerView Adapters
    inner class InsightsAdapter(private val insights: List<Insight>) :
        RecyclerView.Adapter<InsightsAdapter.InsightViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InsightViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_insight, parent, false)
            return InsightViewHolder(view)
        }

        override fun onBindViewHolder(holder: InsightViewHolder, position: Int) {
            val insight = insights[position]
            holder.bind(insight)
        }

        override fun getItemCount(): Int = insights.size

        inner class InsightViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val cardView: CardView = view.findViewById(R.id.insightCardView)
            private val titleText: TextView = view.findViewById(R.id.insightTitleText)
            private val descriptionText: TextView = view.findViewById(R.id.insightDescriptionText)
            private val iconView: ImageView = view.findViewById(R.id.insightIconView)

            fun bind(insight: Insight) {
                titleText.text = insight.title
                descriptionText.text = insight.description
                iconView.setImageResource(insight.iconResId)

                // Set card color based on insight type
                val cardColor = when (insight.type) {
                    InsightType.CRITICAL -> ContextCompat.getColor(itemView.context, R.color.expense_red)
                    InsightType.WARNING -> ContextCompat.getColor(itemView.context, R.color.orange)
                    InsightType.GOOD -> ContextCompat.getColor(itemView.context, R.color.light_green)
                    InsightType.EXCELLENT -> ContextCompat.getColor(itemView.context, R.color.green)
                }
                cardView.setCardBackgroundColor(cardColor)
            }
        }
    }

    inner class RecommendationsAdapter(private val recommendations: List<Recommendation>) :
        RecyclerView.Adapter<RecommendationsAdapter.RecommendationViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recommendation, parent, false)
            return RecommendationViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
            val recommendation = recommendations[position]
            holder.bind(recommendation)
        }

        override fun getItemCount(): Int = recommendations.size

        inner class RecommendationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val titleText: TextView = view.findViewById(R.id.recommendationTitleText)
            private val descriptionText: TextView = view.findViewById(R.id.recommendationDescriptionText)
            private val iconView: ImageView = view.findViewById(R.id.recommendationIconView)

            fun bind(recommendation: Recommendation) {
                titleText.text = recommendation.title
                descriptionText.text = recommendation.description
                iconView.setImageResource(recommendation.iconResId)
            }
        }
    }

    inner class ChallengesAdapter(private val challenges: List<Challenge>) :
        RecyclerView.Adapter<ChallengesAdapter.ChallengeViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChallengeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_challenge, parent, false)
            return ChallengeViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChallengeViewHolder, position: Int) {
            val challenge = challenges[position]
            holder.bind(challenge)
        }

        override fun getItemCount(): Int = challenges.size

        inner class ChallengeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val cardView: CardView = view.findViewById(R.id.challengeCardView)
            private val titleText: TextView = view.findViewById(R.id.challengeTitleText)
            private val descriptionText: TextView = view.findViewById(R.id.challengeDescriptionText)
            private val statusText: TextView = view.findViewById(R.id.challengeStatusText)
            private val iconView: ImageView = view.findViewById(R.id.challengeIconView)

            fun bind(challenge: Challenge) {
                titleText.text = challenge.title
                descriptionText.text = challenge.description
                statusText.text = challenge.status
                iconView.setImageResource(challenge.iconResId)

                // Set status text color based on status
                val statusColor = when (challenge.status) {
                    "Completed" -> ContextCompat.getColor(itemView.context, R.color.income_green)
                    "Ongoing" -> ContextCompat.getColor(itemView.context, R.color.teal_700)
                    else -> ContextCompat.getColor(itemView.context, R.color.gray)
                }
                statusText.setTextColor(statusColor)

                // Set click listener
                cardView.setOnClickListener {
                    Toast.makeText(this@FinancialHealthActivity,
                        "Starting challenge: ${challenge.title}",
                        Toast.LENGTH_SHORT).show()

                    // In a real app, this would start the challenge
                }
            }
        }
    }
}