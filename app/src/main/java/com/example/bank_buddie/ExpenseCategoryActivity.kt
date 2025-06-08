package com.vcsma.bank_buddie

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class ExpenseCategoryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var addCategoryButton: FloatingActionButton
    private lateinit var backButton: ImageView
    private lateinit var progressBar: View
    private lateinit var emptyView: TextView

    private val categories = mutableListOf<ExpenseCategory>()
    private lateinit var adapter: ExpenseCategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_category)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        recyclerView = findViewById(R.id.categoriesRecyclerView)
        addCategoryButton = findViewById(R.id.addCategoryButton)
        backButton = findViewById(R.id.backButton)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        // Set up RecyclerView
        adapter = ExpenseCategoryAdapter(categories) { category ->
            // Handle category click (if needed)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up add category button
        addCategoryButton.setOnClickListener {
            showAddCategoryDialog()
        }

        // Load categories
        loadCategories()
    }

    // Update loadCategories method to handle empty state
    private fun loadCategories() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        db.collection("users").document(currentUser.uid)
            .collection("expenseCategories")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                categories.clear()
                for (document in documents) {
                    val category = document.toObject(ExpenseCategory::class.java)
                    categories.add(category)
                }

                if (categories.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading categories: ${e.message}", Toast.LENGTH_SHORT).show()

                // Show empty state
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    private fun showAddCategoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = dialogView.findViewById<EditText>(R.id.categoryNameInput)

        AlertDialog.Builder(this)
            .setTitle("Add Category")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val categoryName = categoryNameInput.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    addCategory(categoryName)
                } else {
                    Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun addCategory(name: String) {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        val categoryId = UUID.randomUUID().toString()
        val category = ExpenseCategory(categoryId, name)

        db.collection("users").document(currentUser.uid)
            .collection("expenseCategories")
            .document(categoryId)
            .set(category)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                categories.add(category)
                adapter.notifyItemInserted(categories.size - 1)

                if (categories.size == 1) {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                Toast.makeText(this, "Category added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error adding category: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
