package com.vcsma.bank_buddie

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ExpenseEntryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var descriptionInput: EditText
    private lateinit var amountInput: EditText
    private lateinit var dateInput: EditText
    private lateinit var startTimeInput: EditText
    private lateinit var endTimeInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var photoButton: Button
    private lateinit var photoPreview: ImageView
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerProgressBar: ProgressBar

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Keep track of categories and their IDs
    private val categoryIds = mutableListOf<String>()
    private val categoryNames = mutableListOf<String>()
    private var hasPhotoAttached = false

    // FIXED: Reduced timeouts for faster performance
    private val handler = Handler(Looper.getMainLooper())
    private val OPERATION_TIMEOUT = 2000L // 2 seconds timeout
    private val CATEGORY_LOAD_TIMEOUT = 1000L // 1 second for categories

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_entry)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        descriptionInput = findViewById(R.id.descriptionInput)
        amountInput = findViewById(R.id.amountInput)
        dateInput = findViewById(R.id.dateInput)
        startTimeInput = findViewById(R.id.startTimeInput)
        endTimeInput = findViewById(R.id.endTimeInput)
        categorySpinner = findViewById(R.id.categorySpinner)
        photoButton = findViewById(R.id.photoButton)
        photoPreview = findViewById(R.id.photoPreview)
        saveButton = findViewById(R.id.saveButton)
        progressBar = findViewById(R.id.progressBar)
        spinnerProgressBar = findViewById(R.id.spinnerProgressBar)

        // Set up back button
        backButton.setOnClickListener { finish() }

        // Set up date picker
        dateInput.setOnClickListener { showDatePicker() }

        // Set up time pickers
        startTimeInput.setOnClickListener { showTimePicker(startTimeInput) }
        endTimeInput.setOnClickListener { showTimePicker(endTimeInput) }

        // FIXED: Faster category loading
        setupCategorySpinner()

        // Set up photo button
        photoButton.setOnClickListener { openGallery() }

        // Set up save button
        saveButton.setOnClickListener { saveExpense() }

        // Set default date to today
        dateInput.setText(dateFormat.format(calendar.time))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun setupCategorySpinner() {
        // FIXED: Faster category loading with immediate defaults
        categoryIds.clear()
        categoryNames.clear()

        // Add default prompt
        categoryIds.add("")
        categoryNames.add("Select a category")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            setupDefaultCategories()
            return
        }

        // Show loading briefly
        spinnerProgressBar.visibility = View.VISIBLE

        // Quick timeout for categories
        val timeoutRunnable = Runnable {
            if (spinnerProgressBar.visibility == View.VISIBLE) {
                spinnerProgressBar.visibility = View.GONE
                setupDefaultCategories()
            }
        }
        handler.postDelayed(timeoutRunnable, CATEGORY_LOAD_TIMEOUT)

        // Load from Firestore
        db.collection("users").document(currentUser.uid)
            .collection("expenseCategories")
            .get()
            .addOnSuccessListener { documents ->
                handler.removeCallbacks(timeoutRunnable)
                spinnerProgressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    setupDefaultCategories()
                } else {
                    for (document in documents) {
                        val categoryId = document.id
                        val categoryName = document.getString("name") ?: "Unnamed Category"
                        categoryIds.add(categoryId)
                        categoryNames.add(categoryName)
                    }
                    updateSpinnerAdapter()
                }
            }
            .addOnFailureListener {
                handler.removeCallbacks(timeoutRunnable)
                spinnerProgressBar.visibility = View.GONE
                setupDefaultCategories()
            }
    }

    private fun setupDefaultCategories() {
        val defaultCategories = listOf("Food", "Transport", "Rent", "Groceries", "Entertainment")
        for (category in defaultCategories) {
            categoryIds.add(UUID.randomUUID().toString())
            categoryNames.add(category)
        }
        updateSpinnerAdapter()
    }

    private fun updateSpinnerAdapter() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categoryNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
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
        datePickerDialog.show()
    }

    private fun showTimePicker(timeField: EditText) {
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val timeCalendar = Calendar.getInstance()
                timeCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                timeCalendar.set(Calendar.MINUTE, minute)
                timeField.setText(timeFormat.format(timeCalendar.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) {
                photoPreview.setImageURI(imageUri)
                photoPreview.visibility = View.VISIBLE
                hasPhotoAttached = true
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun saveExpense() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "You must be logged in to save expenses", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button and show progress
        saveButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        // Set timeout
        val timeoutRunnable = Runnable {
            if (progressBar.visibility == View.VISIBLE) {
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
                Toast.makeText(this, "Operation timed out. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
        handler.postDelayed(timeoutRunnable, OPERATION_TIMEOUT)

        // Validate inputs
        val description = descriptionInput.text.toString().trim()
        if (description.isEmpty()) {
            descriptionInput.error = "Description is required"
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

        val categoryPosition = categorySpinner.selectedItemPosition
        if (categoryPosition == 0) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            saveButton.isEnabled = true
            progressBar.visibility = View.GONE
            handler.removeCallbacks(timeoutRunnable)
            return
        }

        val categoryName = if (categoryPosition < categoryNames.size) categoryNames[categoryPosition] else "Other"

        // FIXED: Create expense object with proper fields for dashboard display
        val expenseId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val expense = hashMapOf(
            "id" to expenseId,
            "title" to description, // FIXED: Use description as title for proper display
            "description" to description,
            "amount" to amount, // FIXED: Store as positive number
            "date" to dateInput.text.toString(),
            "startTime" to startTimeInput.text.toString(),
            "endTime" to endTimeInput.text.toString(),
            "category" to categoryName, // FIXED: Use category name directly
            "timestamp" to timestamp,
            "userId" to currentUser.uid,
            "type" to "expense", // FIXED: Add type field for dashboard filtering
            "hasPhoto" to hasPhotoAttached
        )

        // FIXED: Save to ONLY ONE collection to prevent duplicates
        db.collection("users").document(currentUser.uid)
            .collection("expenses").document(expenseId)
            .set(expense)
            .addOnSuccessListener {
                // Cancel timeout
                handler.removeCallbacks(timeoutRunnable)
                progressBar.visibility = View.GONE

                // FIXED: Show success and redirect to dashboard immediately
                Toast.makeText(this, "Expense saved successfully!", Toast.LENGTH_SHORT).show()

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

                Toast.makeText(this, "Failed to save expense: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}