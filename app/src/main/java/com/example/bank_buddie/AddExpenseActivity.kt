package com.vcsma.bank_buddie

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddExpenseActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var dateField: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var amountField: EditText
    private lateinit var titleField: EditText
    private lateinit var descriptionField: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: View

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

    // Store category IDs for proper selection
    private val categoryIds = mutableListOf<String>()
    private val categoryNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        backButton = findViewById(R.id.backButton)
        dateField = findViewById(R.id.dateField)
        categorySpinner = findViewById(R.id.categorySpinner)
        amountField = findViewById(R.id.amountField)
        titleField = findViewById(R.id.titleField)
        descriptionField = findViewById(R.id.descriptionField)
        saveButton = findViewById(R.id.saveButton)
        progressBar = findViewById(R.id.progressBar)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up date picker
        dateField.setOnClickListener {
            showDatePicker()
        }

        // Set default date to today
        dateField.setText(dateFormat.format(calendar.time))

        // Set up category spinner - load dynamic categories
        setupCategorySpinner()

        // Set up save button
        saveButton.setOnClickListener {
            saveExpense()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh categories when returning to activity
        setupCategorySpinner()
    }

    private fun setupCategorySpinner() {
        progressBar.visibility = View.VISIBLE

        // Clear previous data
        categoryIds.clear()
        categoryNames.clear()

        // Default first option
        categoryIds.add("")
        categoryNames.add("Select the category")

        // Get current user
        val currentUser = auth.currentUser
        if (currentUser == null) {
            setupDefaultCategories()
            progressBar.visibility = View.GONE
            return
        }

        // Load from Firestore
        db.collection("users").document(currentUser.uid)
            .collection("expenseCategories")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    // No categories found, add some defaults and save them
                    addDefaultCategories(currentUser.uid)
                } else {
                    // Process categories from Firestore
                    for (document in documents) {
                        val id = document.id
                        val name = document.getString("name") ?: "Category"

                        categoryIds.add(id)
                        categoryNames.add(name)
                    }

                    // Create and set adapter
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        categoryNames
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    categorySpinner.adapter = adapter
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading categories: ${e.message}", Toast.LENGTH_SHORT).show()
                setupDefaultCategories()
            }
    }

    private fun setupDefaultCategories() {
        // Add default categories to the lists
        categoryNames.addAll(listOf("Food", "Transport", "Rent", "Groceries", "Entertainment"))

        // Add placeholder IDs - these would only be used if we can't connect to Firestore
        for (i in 1..5) {
            categoryIds.add("default_$i")
        }

        // Create and set adapter
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categoryNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
    }

    private fun addDefaultCategories(userId: String) {
        val defaultCategories = listOf("Food", "Transport", "Rent", "Groceries", "Entertainment")
        val batch = db.batch()

        for (category in defaultCategories) {
            val categoryId = UUID.randomUUID().toString()
            val categoryRef = db.collection("users").document(userId)
                .collection("expenseCategories").document(categoryId)

            val categoryData = hashMapOf(
                "id" to categoryId,
                "name" to category
            )

            batch.set(categoryRef, categoryData)

            // Add to our local lists
            categoryIds.add(categoryId)
            categoryNames.add(category)
        }

        batch.commit()
            .addOnSuccessListener {
                // Create and set adapter
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    categoryNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding default categories: ${e.message}", Toast.LENGTH_SHORT).show()
                setupDefaultCategories()
            }
    }

    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                dateField.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun saveExpense() {
        progressBar.visibility = View.VISIBLE
        saveButton.isEnabled = false

        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "You must be logged in to save expenses", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            return
        }

        // Validate inputs
        val title = titleField.text.toString().trim()
        if (title.isEmpty()) {
            titleField.error = "Title is required"
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            return
        }

        val amountStr = amountField.text.toString().trim()
        if (amountStr.isEmpty()) {
            amountField.error = "Amount is required"
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            amountField.error = "Please enter a valid amount"
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            return
        }

        val categoryPosition = categorySpinner.selectedItemPosition
        if (categoryPosition == 0) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            return
        }

        // Get the selected category
        val categoryId = if (categoryPosition < categoryIds.size) categoryIds[categoryPosition] else ""
        val category = if (categoryPosition < categoryNames.size) categoryNames[categoryPosition] else "Unknown"

        if (categoryId.isEmpty()) {
            Toast.makeText(this, "Invalid category selection", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            return
        }

        val date = dateField.text.toString()
        val description = descriptionField.text.toString().trim()

        // Create expense object
        val expenseId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val expense = hashMapOf(
            "id" to expenseId,
            "title" to title,
            "amount" to -amount, // Negative for expense
            "categoryId" to categoryId,
            "categoryName" to category,
            "date" to date,
            "description" to description,
            "timestamp" to timestamp,
            "userId" to currentUser.uid
        )

        // Save to Firestore - Add to both collections for consistency
        val batch = db.batch()

        // Add to user-specific collection
        val userExpenseRef = db.collection("users").document(currentUser.uid)
            .collection("transactions").document(expenseId)
        batch.set(userExpenseRef, expense)

        // Add to expenses collection
        val expenseRef = db.collection("expenses").document(expenseId)
        batch.set(expenseRef, expense)

        batch.commit()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
                Toast.makeText(this, "Expense saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
                Toast.makeText(this, "Error saving expense: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}