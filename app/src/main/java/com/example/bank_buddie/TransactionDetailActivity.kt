package com.vcsma.bank_buddie

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TransactionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "TRANSACTION_ID"
    }

    private lateinit var iconView     : ImageView
    private lateinit var titleText    : TextView
    private lateinit var dateText     : TextView
    private lateinit var categoryText : TextView
    private lateinit var amountText   : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        // Bind your views (make sure IDs match your XML)
        iconView     = findViewById(R.id.detailTransactionIcon)
        titleText    = findViewById(R.id.detailTransactionTitle)
        dateText     = findViewById(R.id.detailTransactionDate)
        categoryText = findViewById(R.id.detailTransactionCategory)
        amountText   = findViewById(R.id.detailTransactionAmount)

        // Pull the transaction ID from the Intent
        val txId = intent.getStringExtra(EXTRA_TRANSACTION_ID)
            ?: return finish()  // nothing to show

        // TODO: Replace this with your Firestore fetch or local lookup
        // For now, just show the ID in the title:
        titleText.text = "Transaction #$txId"

        // And you can populate the rest with placeholders or real data:
        dateText.text     = "Date:—"
        categoryText.text = "Category:—"
        amountText.text   = "Amount:—"
        iconView.setImageResource(R.drawable.ic_transaction_salary)
    }
}
