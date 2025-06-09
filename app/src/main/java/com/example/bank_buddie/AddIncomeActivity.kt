package com.vcsma.bank_buddie

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddIncomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var titleInput: EditText
    private lateinit var amountInput: EditText
    private lateinit var dateInput: EditText
    private lateinit var sourceInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: View

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

    // FIXED: Reduced timeout to 2 seconds for faster performance
    private val handler = Handler(Looper.getMainLooper())
    private val OPERATION_TIMEOUT = 2000L // 2 seconds timeout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_income)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        titleInput = findViewById(R.id.titleInput)
        amountInput = findViewById(R.id.amountInput)
        dateInput = findViewById(R.id.dateInput)
        sourceInput = findViewById(R.id.sourceInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        saveButton = findViewById(R.id.saveButton)
        progressBar = findViewById(R.id.progressBar)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up date picker
        dateInput.setOnClickListener {
            showDatePicker()
        }

        // Set default date to today
        dateInput.setText(dateFormat.format(calendar.time))

        // Set up save button
        saveButton.setOnClickListener {
            saveIncome()
        }
    }

    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                dateInput.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun saveIncome() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "You must be logged in to save income", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable the save button to prevent multiple clicks
        saveButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        // FIXED: Set timeout with proper cleanup
        val timeoutRunnable = Runnable {
            if (progressBar.visibility == View.VISIBLE) {
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
                Toast.makeText(this, "Operation timed out. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
        handler.postDelayed(timeoutRunnable, OPERATION_TIMEOUT)

        // Validate inputs
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = "Title is required"
            saveButton.isEnabled = true
            progressBar.visibility = View.GONE
            handler.removeCallbacks(timeoutRunnable)
            return
        }

        val amountStr = amountInput.text.toString().trim()
        if (amountStr.isEmpty()) {
            amountInput.error = "Amount is required"
            saveButton.isEnabled = true
            progressBar.visibility = View.GONE
            handler.removeCallbacks(timeoutRunnable)
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            amountInput.error = "Please enter a valid amount"
            saveButton.isEnabled = true
            progressBar.visibility = View.GONE
            handler.removeCallbacks(timeoutRunnable)
            return
        }

        val date = dateInput.text.toString()
        val source = sourceInput.text.toString().trim()
        val description = descriptionInput.text.toString().trim()

        // FIXED: Create income object with proper fields for dashboard display
        val incomeId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val income = hashMapOf(
            "id" to incomeId,
            "title" to title, // FIXED: Added title field for proper display
            "amount" to amount, // Positive for income
            "date" to date,
            "source" to source,
            "description" to description,
            "timestamp" to timestamp,
            "userId" to currentUser.uid,
            "type" to "income", // FIXED: Add type field for dashboard filtering
            "category" to (source.ifEmpty { title }) // FIXED: Use source or title as category for display
        )

        // FIXED: Save to ONLY ONE collection to prevent duplicates
        db.collection("users").document(currentUser.uid)
            .collection("income").document(incomeId)
            .set(income)
            .addOnSuccessListener {
                // Cancel timeout
                handler.removeCallbacks(timeoutRunnable)
                progressBar.visibility = View.GONE

                // FIXED: Show quick success message and redirect to dashboard
                Toast.makeText(this, "Income added successfully!", Toast.LENGTH_SHORT).show()

                // FIXED: Immediate redirect to dashboard
                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                // Cancel timeout
                handler.removeCallbacks(timeoutRunnable)
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true

                // FIXED: Simple error message instead of dialog
                Toast.makeText(this, "Failed to save income: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // FIXED: Clean up handler to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }
}