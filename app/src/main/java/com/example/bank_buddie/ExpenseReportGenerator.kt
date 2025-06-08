package com.vcsma.bank_buddie

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ExpenseReportGenerator(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 595 // A4 width in points (72 points = 1 inch)
        private const val PAGE_HEIGHT = 842 // A4 height in points
        private const val MARGIN = 50
        private const val TAG = "ExpenseReportGenerator"
    }

    // Paint objects for styling
    private val titlePaint = Paint().apply {
        color = Color.rgb(0, 121, 107) // Teal color
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val headingPaint = Paint().apply {
        color = Color.rgb(33, 33, 33) // Dark gray
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subheadingPaint = Paint().apply {
        color = Color.rgb(66, 66, 66) // Medium gray
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val bodyPaint = Paint().apply {
        color = Color.rgb(66, 66, 66) // Medium gray
        textSize = 12f
    }

    private val linePaint = Paint().apply {
        color = Color.rgb(189, 189, 189) // Light gray
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance(Locale.getDefault())
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /**
     * Generate a PDF report of expenses by category
     */
    fun generateExpenseReport(
        userName: String,
        financialHealthScore: Int,
        expensesByCategory: Map<String, Double>,
        totalIncome: Double,
        totalExpenses: Double,
        period: String
    ): File? {
        val pdfDocument = PdfDocument()
        var currentPage = 1
        var yPosition = 0f

        try {
            // Create first page
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPage).create()
            var page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            yPosition = MARGIN.toFloat()

            // Add header
            drawHeader(canvas, userName, period, yPosition)
            yPosition += 100f

            // Add financial health summary
            drawFinancialSummary(canvas, financialHealthScore, totalIncome, totalExpenses, yPosition)
            yPosition += 150f

            // Add expense breakdown title
            canvas.drawText("Expense Breakdown by Category", MARGIN.toFloat(), yPosition, headingPaint)
            yPosition += 30f

            // Draw line separator
            canvas.drawLine(MARGIN.toFloat(), yPosition, PAGE_WIDTH - MARGIN.toFloat(), yPosition, linePaint)
            yPosition += 20f

            // Draw column headers
            canvas.drawText("Category", MARGIN.toFloat(), yPosition, subheadingPaint)
            canvas.drawText("Amount", PAGE_WIDTH - MARGIN - 150f, yPosition, subheadingPaint)
            canvas.drawText("% of Total", PAGE_WIDTH - MARGIN - 50f, yPosition, subheadingPaint)
            yPosition += 20f

            // Draw line separator
            canvas.drawLine(MARGIN.toFloat(), yPosition, PAGE_WIDTH - MARGIN.toFloat(), yPosition, linePaint)
            yPosition += 20f

            // Sort categories by amount (descending)
            val sortedExpenses = expensesByCategory.entries.sortedByDescending { it.value }

            // Draw expense rows
            for (expense in sortedExpenses) {
                // Check if we need a new page
                if (yPosition > PAGE_HEIGHT - MARGIN) {
                    // Finish current page
                    pdfDocument.finishPage(page)

                    // Start new page
                    currentPage++
                    val newPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPage).create()
                    page = pdfDocument.startPage(newPageInfo)
                    val canvas2 = page.canvas
                    yPosition = MARGIN + 30f // Start with some margin on new page

                    // Add continuation header
                    canvas2.drawText("Expense Breakdown (continued)", MARGIN.toFloat(), MARGIN.toFloat(), headingPaint)
                    yPosition += 30f
                }

                val categoryName = expense.key
                val amount = expense.value
                val percentage = if (totalExpenses > 0) (amount / totalExpenses) * 100 else 0.0

                // Draw category name (truncate if too long)
                val truncatedName = if (categoryName.length > 25) categoryName.substring(0, 22) + "..." else categoryName
                canvas.drawText(truncatedName, MARGIN.toFloat(), yPosition, bodyPaint)

                // Draw amount
                canvas.drawText(currencyFormat.format(abs(amount)), PAGE_WIDTH - MARGIN - 150f, yPosition, bodyPaint)

                // Draw percentage
                canvas.drawText(String.format("%.1f%%", percentage), PAGE_WIDTH - MARGIN - 50f, yPosition, bodyPaint)

                yPosition += 25f
            }

            // Draw line separator
            canvas.drawLine(MARGIN.toFloat(), yPosition, PAGE_WIDTH - MARGIN.toFloat(), yPosition, linePaint)
            yPosition += 20f

            // Draw total
            canvas.drawText("Total Expenses", MARGIN.toFloat(), yPosition, subheadingPaint)
            canvas.drawText(currencyFormat.format(abs(totalExpenses)), PAGE_WIDTH - MARGIN - 150f, yPosition, subheadingPaint)
            canvas.drawText("100%", PAGE_WIDTH - MARGIN - 50f, yPosition, subheadingPaint)
            yPosition += 40f

            // Add recommendations section
            canvas.drawText("Recommendations", MARGIN.toFloat(), yPosition, headingPaint)
            yPosition += 30f

            // Add some generic recommendations
            val recommendations = generateRecommendations(expensesByCategory, totalIncome, totalExpenses)
            for (recommendation in recommendations) {
                // Check if we need a new page
                if (yPosition > PAGE_HEIGHT - MARGIN) {
                    // Finish current page
                    pdfDocument.finishPage(page)

                    // Start new page
                    currentPage++
                    val newPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPage).create()
                    page = pdfDocument.startPage(newPageInfo)
                    val canvas2 = page.canvas
                    yPosition = MARGIN + 30f // Start with some margin on new page

                    // Add continuation header
                    canvas2.drawText("Recommendations (continued)", MARGIN.toFloat(), MARGIN.toFloat(), headingPaint)
                    yPosition += 30f
                }

                canvas.drawText("• ${recommendation}", MARGIN.toFloat(), yPosition, bodyPaint)
                yPosition += 25f
            }

            // Add footer with date
            val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val currentDate = dateFormat.format(Date())
            canvas.drawText("Report generated on: $currentDate", MARGIN.toFloat(), PAGE_HEIGHT - MARGIN.toFloat(), bodyPaint)
            canvas.drawText("Bank Buddie Financial Report", PAGE_WIDTH - MARGIN - 200f, PAGE_HEIGHT - MARGIN.toFloat(), bodyPaint)

            // Finish the page
            pdfDocument.finishPage(page)

            // Save the document to Downloads folder for easy access
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "BankBuddie_ExpenseReport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val filePath = File(downloadsDir, fileName)

            FileOutputStream(filePath).use { out ->
                pdfDocument.writeTo(out)
            }

            return filePath
        } catch (e: IOException) {
            Log.e(TAG, "Error generating PDF: ${e.message}")
            e.printStackTrace()

            // Fallback to app-specific directory if public directory fails
            try {
                val fileName = "BankBuddie_ExpenseReport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
                val filePath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

                FileOutputStream(filePath).use { out ->
                    pdfDocument.writeTo(out)
                }

                return filePath
            } catch (e2: IOException) {
                Log.e(TAG, "Error in fallback PDF generation: ${e2.message}")
                e2.printStackTrace()
                return null
            }
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawHeader(canvas: Canvas, userName: String, period: String, yPosition: Float) {
        // Draw app name
        canvas.drawText("Bank Buddie", MARGIN.toFloat(), yPosition, titlePaint)

        // Draw report title
        canvas.drawText("Expense Report", MARGIN.toFloat(), yPosition + 30f, headingPaint)

        // Draw user name and period
        canvas.drawText("For: $userName", MARGIN.toFloat(), yPosition + 60f, bodyPaint)
        canvas.drawText("Period: $period", MARGIN.toFloat(), yPosition + 80f, bodyPaint)
    }

    private fun drawFinancialSummary(
        canvas: Canvas,
        financialHealthScore: Int,
        totalIncome: Double,
        totalExpenses: Double,
        yPosition: Float
    ) {
        // Draw financial health score
        canvas.drawText("Financial Health Score: $financialHealthScore/100", MARGIN.toFloat(), yPosition, subheadingPaint)

        // Draw income
        canvas.drawText("Total Income: ${currencyFormat.format(totalIncome)}", MARGIN.toFloat(), yPosition + 30f, bodyPaint)

        // Draw expenses
        canvas.drawText("Total Expenses: ${currencyFormat.format(abs(totalExpenses))}", MARGIN.toFloat(), yPosition + 50f, bodyPaint)

        // Draw savings
        val savings = totalIncome - abs(totalExpenses)
        val savingsText = if (savings >= 0) "Total Savings: ${currencyFormat.format(savings)}"
        else "Net Loss: ${currencyFormat.format(savings)}"
        canvas.drawText(savingsText, MARGIN.toFloat(), yPosition + 70f, bodyPaint)

        // Draw savings rate
        val savingsRate = if (totalIncome > 0) (savings / totalIncome) * 100 else 0.0
        val savingsRateText = String.format("Savings Rate: %.1f%%", savingsRate)
        canvas.drawText(savingsRateText, MARGIN.toFloat(), yPosition + 90f, bodyPaint)
    }

    private fun generateRecommendations(
        expensesByCategory: Map<String, Double>,
        totalIncome: Double,
        totalExpenses: Double
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // Calculate savings rate
        val savings = totalIncome - abs(totalExpenses)
        val savingsRate = if (totalIncome > 0) (savings / totalIncome) * 100 else 0.0

        // Add recommendations based on savings rate
        if (savingsRate < 0) {
            recommendations.add("Your expenses exceed your income. Consider reducing expenses or finding additional income sources.")
        } else if (savingsRate < 10) {
            recommendations.add("Your savings rate is below 10%. Aim to save at least 15-20% of your income.")
        } else if (savingsRate < 20) {
            recommendations.add("Your savings rate is good but could be improved. Try to increase it to 20% or more.")
        } else {
            recommendations.add("Great job! Your savings rate is excellent. Consider investing your surplus for long-term growth.")
        }

        // Find the largest expense categories
        val sortedExpenses = expensesByCategory.entries.sortedByDescending { it.value }
        if (sortedExpenses.isNotEmpty()) {
            val topCategory = sortedExpenses[0]
            val topCategoryPercentage = (abs(topCategory.value) / abs(totalExpenses)) * 100

            if (topCategoryPercentage > 30) {
                recommendations.add("Your ${topCategory.key} expenses account for ${String.format("%.1f", topCategoryPercentage)}% of your total expenses. Consider ways to reduce this category.")
            }
        }

        // Add some general recommendations
        recommendations.add("Review your subscription services and cancel those you don't use regularly.")
        recommendations.add("Consider using cash for discretionary spending to be more mindful of your expenses.")
        recommendations.add("Set up automatic transfers to your savings account on payday.")

        return recommendations
    }

    /**
     * Share the generated PDF report
     */
    fun sharePdfReport(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Check if there's an app that can handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // If no PDF viewer is available, offer to share the file
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Financial Report"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing PDF: ${e.message}")
            e.printStackTrace()
        }
    }
}
