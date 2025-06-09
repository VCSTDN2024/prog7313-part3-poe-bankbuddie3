package com.vcsma.bank_buddie

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ExpenseDetailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var backButton: ImageView
    private lateinit var descriptionText: TextView
    private lateinit var amountText: TextView
    private lateinit var categoryText: TextView
    private lateinit var dateText: TextView
    private lateinit var timeText: TextView
    private lateinit var photoImage: ImageView
    private lateinit var progressBar: ProgressBar

    private val expenseId: String
        get() = intent.getStringExtra("EXPENSE_ID")
            ?: throw IllegalStateException("Expense ID missing in Intent extras")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_detail)

        // Initialize Firebase
        auth    = FirebaseAuth.getInstance()
        db      = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Bind views
        backButton      = findViewById(R.id.backButton)
        descriptionText = findViewById(R.id.descriptionText)
        amountText      = findViewById(R.id.amountText)
        categoryText    = findViewById(R.id.categoryText)
        dateText        = findViewById(R.id.dateText)
        timeText        = findViewById(R.id.timeText)
        photoImage      = findViewById(R.id.photoImage)
        progressBar     = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }

        loadExpenseDetails()
    }

    private fun loadExpenseDetails() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE

        db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(expenseId)
            .get()
            .addOnSuccessListener { doc ->
                progressBar.visibility = View.GONE
                val entry = doc.toObject(ExpenseEntry::class.java)
                if (doc.exists() && entry != null) {
                    bind(entry, uid)
                } else {
                    showMissingAndClose()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun bind(entry: ExpenseEntry, uid: String) {
        descriptionText.text = entry.description
        amountText.text      = getString(R.string.currency_value_negative, entry.amount.toFloat())
        categoryText.text    = entry.categoryName
        dateText.text        = entry.date
        timeText.text        = "${entry.startTime} - ${entry.endTime}"

        if (entry.hasPhoto) {
            val photoRef = storage.reference
                .child("users/$uid/expenses/${entry.id}.jpg")
            photoRef.downloadUrl
                .addOnSuccessListener { uri ->
                    photoImage.load(uri) {
                        crossfade(true)
                        placeholder(R.drawable.placeholder)  // optional
                    }
                    photoImage.visibility = View.VISIBLE
                }
                .addOnFailureListener {
                    photoImage.visibility = View.GONE
                }
        } else {
            photoImage.visibility = View.GONE
        }
    }

    private fun showMissingAndClose() {
        Toast.makeText(this, "Expense not found", Toast.LENGTH_SHORT).show()
        finish()
    }
}
