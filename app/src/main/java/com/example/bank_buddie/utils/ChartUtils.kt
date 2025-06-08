package com.vcsma.bank_buddie.utils

import android.graphics.Color
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.vcsma.bank_buddie.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap

object ChartUtils {

    fun setupMonthlySpendingChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            setPinchZoom(true)
            legend.isEnabled = true

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.labelRotationAngle = 45f

            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false

            setNoDataText("No spending data available")
        }
    }

    /**
     * Creates a timeline chart from Firestore QueryDocumentSnapshot objects
     */
    fun createTimelineChart(
        expenses: List<QueryDocumentSnapshot>,
        chart: LineChart,
        timeframe: String,
        minGoal: Double,
        maxGoal: Double
    ) {
        if (expenses.isEmpty()) {
            chart.setNoDataText("No spending data available for this period")
            chart.invalidate()
            return
        }

        val (_, groupingFunction) = getTimeDivisions(timeframe)
        val expensesByPeriod = TreeMap<String, Double>()

        // Process Firestore documents
        expenses.forEach { document ->
            val timestamp = document.getLong("timestamp") ?: 0L
            val amount = document.getDouble("amount") ?: 0.0
            val periodKey = groupingFunction(timestamp)
            expensesByPeriod[periodKey] = (expensesByPeriod[periodKey] ?: 0.0) + amount
        }

        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        expensesByPeriod.entries.forEachIndexed { index, (period, total) ->
            entries.add(Entry(index.toFloat(), total.toFloat()))
            labels.add(period)
        }

        val dataSet = LineDataSet(entries, "Spending")
        styleLineDataSet(dataSet, chart)

        chart.data = LineData(dataSet)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels.toTypedArray())
        updateChartGoalLines(chart, minGoal, maxGoal)
        chart.invalidate()
    }

    fun updateChartGoalLines(chart: LineChart, minGoal: Double, maxGoal: Double) {
        chart.axisLeft.removeAllLimitLines()

        if (minGoal > 0) {
            val minLimitLine = LimitLine(minGoal.toFloat(), "Min Goal")
            minLimitLine.lineColor = Color.BLUE
            minLimitLine.lineWidth = 1f
            minLimitLine.enableDashedLine(10f, 10f, 0f)
            minLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            minLimitLine.textSize = 10f
            chart.axisLeft.addLimitLine(minLimitLine)
        }

        if (maxGoal > 0) {
            val maxLimitLine = LimitLine(maxGoal.toFloat(), "Max Goal")
            maxLimitLine.lineColor = Color.RED
            maxLimitLine.lineWidth = 1f
            maxLimitLine.enableDashedLine(10f, 10f, 0f)
            maxLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            maxLimitLine.textSize = 10f
            chart.axisLeft.addLimitLine(maxLimitLine)
        }

        chart.invalidate()
    }

    private fun styleLineDataSet(dataSet: LineDataSet, chart: LineChart) {
        val ctx = chart.context

        dataSet.color = ContextCompat.getColor(ctx, R.color.teal_700)
        dataSet.setCircleColor(ContextCompat.getColor(ctx, R.color.teal_700))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)

        dataSet.setDrawFilled(true)
        dataSet.fillColor = ContextCompat.getColor(ctx, R.color.teal_200)
        dataSet.fillAlpha = 50

        dataSet.valueTextSize = 10f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawValues(true)
    }

    private fun getTimeDivisions(timeframe: String): Pair<SimpleDateFormat, (Long) -> String> {
        return when (timeframe) {
            "Weekly" -> {
                val fmt = SimpleDateFormat("EEE", Locale.getDefault())
                fmt to { ts -> fmt.format(Date(ts)) }
            }
            "Monthly" -> {
                val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
                fmt to { ts ->
                    Calendar.getInstance().run {
                        time = Date(ts)
                        "Week ${get(Calendar.WEEK_OF_MONTH)}"
                    }
                }
            }
            "Quarterly" -> {
                val fmt = SimpleDateFormat("MMM", Locale.getDefault())
                fmt to { ts -> fmt.format(Date(ts)) }
            }
            "Yearly" -> {
                val fmt = SimpleDateFormat("MMM", Locale.getDefault())
                fmt to { ts -> fmt.format(Date(ts)) }
            }
            else -> {
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                fmt to { ts -> fmt.format(Date(ts)) }
            }
        }
    }
}