package com.vcsma.bank_buddie

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class AIChatbotActivity : AppCompatActivity() {

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInputField: EditText
    private lateinit var sendButton: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var loadingContainer: View

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Chat Adapter & Data
    private lateinit var chatAdapter: ChatMessageAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // Timeout handler
    private val timeoutHandler = android.os.Handler()
    private val timeoutRunnable = Runnable {
        setLoading(false)
        addBotMessage("I'm having trouble processing your request. Please try again later.")
    }

    // Category creation pattern
    private val createCategoryPattern = Pattern.compile(
        "create(?:\\s+a)?(?:\\s+new)?\\s+category(?:\\s+called)?\\s+(?<name>[a-zA-Z\\s]+)",
        Pattern.CASE_INSENSITIVE
    )

    // Add transaction pattern
    private val addTransactionPattern = Pattern.compile(
        "add\\s+(?:an?\\s+)?(?<transactionType>expense|income)\\s+of\\s+\\$?(?<transactionAmount>[\\d.]+)(?:\\s+for\\s+(?<transactionDescription>[\\w\\s]+))?(?:\\s+in(?:\\s+the)?\\s+(?<transactionCategory>[\\w\\s]+)(?:\\s+category)?)?",
        Pattern.CASE_INSENSITIVE
    )

    // Generate report pattern
    private val generateReportPattern = Pattern.compile(
        "generate(?:\\s+a)?\\s+(?<format>pdf|csv)\\s+report(?:\\s+for\\s+(?<period>this month|last month|this year|last year|all time))?",
        Pattern.CASE_INSENSITIVE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chatbot)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize UI components
        initializeViews()

        // Set up RecyclerView
        setupRecyclerView()

        // Set up click listeners
        setupClickListeners()

        // Add welcome message
        addWelcomeMessage()
    }

    override fun onDestroy() {
        super.onDestroy()
        // FIXED: Clean up handler to prevent memory leaks
        timeoutHandler.removeCallbacks(timeoutRunnable)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInputField = findViewById(R.id.messageInputField)
        sendButton = findViewById(R.id.sendButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        statusText = findViewById(R.id.statusText)
        loadingContainer = findViewById(R.id.loadingContainer)

        // Disable send button initially
        sendButton.isEnabled = false

        // Add text change listener to enable/disable send button
        messageInputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.isNullOrBlank()
            }
        })
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatMessageAdapter(chatMessages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecyclerView.adapter = chatAdapter
    }

    private fun setupClickListeners() {
        // Back button click
        backButton.setOnClickListener {
            finish()
        }

        // Send button click
        sendButton.setOnClickListener {
            val message = messageInputField.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
    }

    private fun addWelcomeMessage() {
        val welcome = "Hi there! I'm your Bank Buddie financial assistant. I can help you with:\n\n" +
                "• Expense reports and insights\n" +
                "• Creating new categories (try 'Create a new category called Dining')\n" +
                "• Adding transactions (try 'Add an expense of \$45 for dinner in the Food category')\n" +
                "• Generating reports (try 'Generate a PDF report for this month')\n\n" +
                "What would you like to do today?"
        addBotMessage(welcome)
    }

    private fun sendMessage(message: String) {
        // Add user message to chat
        addUserMessage(message)

        // Clear input field
        messageInputField.setText("")

        // Show loading indicator
        setLoading(true)

        // Set timeout (10 seconds)
        timeoutHandler.postDelayed(timeoutRunnable, 10000)

        // Process the message and generate a response
        processUserMessage(message)
    }

    private fun addUserMessage(message: String) {
        val timestamp = System.currentTimeMillis()
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            timestamp = timestamp,
            isFromUser = true
        )
        chatMessages.add(chatMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()

        // Save to Firestore if user is logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            saveChatMessageToFirestore(chatMessage, currentUser.uid)
        }
    }

    private fun addBotMessage(message: String) {
        val timestamp = System.currentTimeMillis()
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            timestamp = timestamp,
            isFromUser = false
        )
        chatMessages.add(chatMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()

        // Save to Firestore if user is logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            saveChatMessageToFirestore(chatMessage, currentUser.uid)
        }
    }

    private fun saveChatMessageToFirestore(chatMessage: ChatMessage, userId: String) {
        db.collection("users")
            .document(userId)
            .collection("chat_messages")
            .document(chatMessage.id)
            .set(chatMessage)
            .addOnFailureListener { e ->
                Log.e("AIChatbot", "Error saving chat message", e)
            }
    }

    private fun processUserMessage(message: String) {
        // Check for category creation command
        val createCategoryMatcher = createCategoryPattern.matcher(message)
        if (createCategoryMatcher.find()) {
            val categoryName = createCategoryMatcher.group("name")?.trim()
            if (!categoryName.isNullOrEmpty()) {
                createCategory(categoryName)
                return
            }
        }

        // Check for add transaction command
        val addTransactionMatcher = addTransactionPattern.matcher(message)
        if (addTransactionMatcher.find()) {
            val type = try {
                addTransactionMatcher.group("transactionType")?.lowercase() ?: "expense"
            } catch (e: IllegalArgumentException) {
                "expense"
            }

            val amountStr = try {
                addTransactionMatcher.group("transactionAmount")
            } catch (e: IllegalArgumentException) {
                null
            }

            if (!amountStr.isNullOrEmpty()) {
                try {
                    val amount = amountStr.toDouble()
                    val description = addTransactionMatcher.group("transactionDescription") ?: "No description"
                    val category = addTransactionMatcher.group("transactionCategory") ?: "General"
                    addTransaction(type, amount, description, category)
                    return
                } catch (e: NumberFormatException) {
                    Log.e("AIChatbot", "Error parsing amount", e)
                }
            }
        }

        // Check for generate report command
        val generateReportMatcher = generateReportPattern.matcher(message)
        if (generateReportMatcher.find()) {
            val format = generateReportMatcher.group("format")?.lowercase() ?: "pdf"
            val period = generateReportMatcher.group("period")?.lowercase() ?: "this month"
            generateReport(format, period)
            return
        }

        // Get user information and transaction data as context
        val currentUser = auth.currentUser
        if (currentUser != null) {
            getUserData(currentUser.uid, message)
        } else {
            // No user data available
            generateGenericResponse(message)
        }
    }

    private fun createCategory(categoryName: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            setLoading(false)
            addBotMessage("You need to be logged in to create a category.")
            return
        }

        val userId = currentUser.uid
        val categoryId = UUID.randomUUID().toString()
        val categoryData = hashMapOf(
            "id" to categoryId,
            "name" to categoryName,
            "created_at" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("categories")
            .document(categoryId)
            .set(categoryData)
            .addOnSuccessListener {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                setLoading(false)
                addBotMessage("I've created a new category called \"$categoryName\". You can now use it when adding expenses.")
            }
            .addOnFailureListener { e ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                setLoading(false)
                Log.e("AIChatbot", "Error creating category", e)
                addBotMessage("I couldn't create the category. Please try again later.")
            }
    }

    private fun addTransaction(type: String, amount: Double, description: String, category: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            setLoading(false)
            addBotMessage("You need to be logged in to add a transaction.")
            return
        }

        val userId = currentUser.uid
        val transactionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // First, verify that the category exists or create it
        db.collection("users")
            .document(userId)
            .collection("categories")
            .whereEqualTo("name", category)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty && category != "Uncategorized") {
                    // Category doesn't exist, create it first
                    val categoryId = UUID.randomUUID().toString()
                    val categoryData = hashMapOf(
                        "id" to categoryId,
                        "name" to category,
                        "created_at" to now
                    )

                    db.collection("users")
                        .document(userId)
                        .collection("categories")
                        .document(categoryId)
                        .set(categoryData)
                        .addOnSuccessListener {
                            // Now add the transaction with the new category
                            addTransactionToFirestore(userId, transactionId, type, amount, description, category, now)
                        }
                        .addOnFailureListener { e ->
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            setLoading(false)
                            Log.e("AIChatbot", "Error creating category for transaction", e)
                            addBotMessage("I couldn't add the transaction because there was an error creating the category. Please try again.")
                        }
                } else {
                    // Category exists, add the transaction
                    addTransactionToFirestore(userId, transactionId, type, amount, description, category, now)
                }
            }
            .addOnFailureListener { e ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                setLoading(false)
                Log.e("AIChatbot", "Error checking category existence", e)
                addBotMessage("I couldn't add the transaction. Please try again later.")
            }
    }

    private fun addTransactionToFirestore(
        userId: String,
        transactionId: String,
        type: String,
        amount: Double,
        description: String,
        category: String,
        timestamp: Long
    ) {
        // FIXED: Add transaction to the correct collection based on type
        val collection = if (type == "income") "income" else "expenses"

        val transactionData = hashMapOf(
            "id" to transactionId,
            "type" to type,
            "amount" to amount,
            "description" to description,
            "category" to category,
            "timestamp" to timestamp,
            "created_at" to timestamp,
            "title" to description // FIXED: Add title field for better display in dashboard
        )

        // Add to specific collection (income or expenses)
        db.collection("users")
            .document(userId)
            .collection(collection)
            .document(transactionId)
            .set(transactionData)
            .addOnSuccessListener {
                // Also add to transactions collection for unified view
                db.collection("users")
                    .document(userId)
                    .collection("transactions")
                    .document(transactionId)
                    .set(transactionData)
                    .addOnSuccessListener {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        setLoading(false)

                        val amountFormatted = String.format("$%.2f", amount)
                        val typeCapitalized = type.replaceFirstChar { it.uppercase() }

                        addBotMessage("I've added a new $type: $amountFormatted for \"$description\" in the \"$category\" category.")
                    }
                    .addOnFailureListener { e ->
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        setLoading(false)
                        Log.e("AIChatbot", "Error adding transaction to unified collection", e)
                        addBotMessage("I added the transaction but couldn't sync it properly. It may not appear in all views.")
                    }
            }
            .addOnFailureListener { e ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                setLoading(false)
                Log.e("AIChatbot", "Error adding transaction", e)
                addBotMessage("I couldn't add the transaction. Please try again later.")
            }
    }

    private fun generateReport(format: String, period: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            setLoading(false)
            addBotMessage("You need to be logged in to generate a report.")
            return
        }

        val userId = currentUser.uid

        // Get date range based on period
        val (startDate, endDate) = getDateRangeForPeriod(period)

        // FIXED: Get transactions from both income and expenses collections
        val expensesTask = db.collection("users")
            .document(userId)
            .collection("expenses")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .get()

        val incomeTask = db.collection("users")
            .document(userId)
            .collection("income")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .get()

        // Use Tasks.whenAllComplete to wait for both queries
        com.google.android.gms.tasks.Tasks.whenAllComplete(expensesTask, incomeTask)
            .addOnSuccessListener {
                val expensesResult = expensesTask.result
                val incomeResult = incomeTask.result

                val allTransactions = mutableListOf<TransactionData>()

                // Process expenses
                for (doc in expensesResult.documents) {
                    try {
                        val amount = doc.getDouble("amount") ?: 0.0
                        val category = doc.getString("category") ?: "Uncategorized"
                        val date = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val description = doc.getString("description") ?: ""

                        allTransactions.add(TransactionData(
                            amount = amount,
                            category = category,
                            date = date,
                            description = description,
                            type = "expense"
                        ))
                    } catch (e: Exception) {
                        Log.e("AIChatbot", "Error parsing expense", e)
                    }
                }

                // Process income
                for (doc in incomeResult.documents) {
                    try {
                        val amount = doc.getDouble("amount") ?: 0.0
                        val category = doc.getString("category") ?: "Income"
                        val date = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val description = doc.getString("description") ?: ""

                        allTransactions.add(TransactionData(
                            amount = amount,
                            category = category,
                            date = date,
                            description = description,
                            type = "income"
                        ))
                    } catch (e: Exception) {
                        Log.e("AIChatbot", "Error parsing income", e)
                    }
                }

                if (allTransactions.isEmpty()) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    setLoading(false)
                    addBotMessage("I couldn't find any transactions for $period. Please try a different time period or add some transactions first.")
                    return@addOnSuccessListener
                }

                if (format == "pdf") {
                    createPdfReport(allTransactions, period)
                } else {
                    createCsvReport(allTransactions, period)
                }
            }
            .addOnFailureListener { e ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                setLoading(false)
                Log.e("AIChatbot", "Error generating report", e)
                addBotMessage("I couldn't generate the report. Please try again later.")
            }
    }

    private fun getDateRangeForPeriod(period: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis

        when (period) {
            "this month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                return Pair(startDate, endDate)
            }
            "last month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.MONTH, -1)
                val startDate = calendar.timeInMillis
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val lastDayOfLastMonth = calendar.timeInMillis
                return Pair(startDate, lastDayOfLastMonth)
            }
            "this year" -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                return Pair(startDate, endDate)
            }
            "last year" -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.YEAR, -1)
                val startDate = calendar.timeInMillis
                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfLastYear = calendar.timeInMillis
                return Pair(startDate, endOfLastYear)
            }
            else -> { // "all time" or any other value
                return Pair(0, endDate)
            }
        }
    }

    private fun createPdfReport(transactions: List<TransactionData>, period: String) {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page = document.startPage(pageInfo)
            var canvas = page.canvas
            val paint = Paint()

            // Draw header
            paint.color = Color.BLACK
            paint.textSize = 24f
            canvas.drawText("Bank Buddie Financial Report", 50f, 50f, paint)

            // Draw period
            paint.textSize = 16f
            canvas.drawText("Period: $period", 50f, 80f, paint)

            // Draw date
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            canvas.drawText("Generated on: ${dateFormat.format(Date())}", 50f, 110f, paint)

            // Draw separator
            paint.strokeWidth = 2f
            canvas.drawLine(50f, 130f, 550f, 130f, paint)

            // Draw table headers
            paint.textSize = 14f
            canvas.drawText("Date", 50f, 160f, paint)
            canvas.drawText("Type", 150f, 160f, paint)
            canvas.drawText("Category", 220f, 160f, paint)
            canvas.drawText("Description", 320f, 160f, paint)
            canvas.drawText("Amount", 480f, 160f, paint)

            // Draw separator
            paint.strokeWidth = 1f
            canvas.drawLine(50f, 170f, 550f, 170f, paint)

            // Draw transactions
            var y = 200f
            val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

            var totalIncome = 0.0
            var totalExpense = 0.0

            transactions.forEach { transaction ->
                val date = dateFormatter.format(Date(transaction.date))
                canvas.drawText(date, 50f, y, paint)

                val type = transaction.type.replaceFirstChar { it.uppercase() }
                canvas.drawText(type, 150f, y, paint)

                canvas.drawText(transaction.category, 220f, y, paint)

                // Truncate description if too long
                val description = if (transaction.description.length > 20) {
                    transaction.description.substring(0, 17) + "..."
                } else {
                    transaction.description
                }
                canvas.drawText(description, 320f, y, paint)

                val amount = String.format("$%.2f", transaction.amount)
                canvas.drawText(amount, 480f, y, paint)

                if (transaction.type == "income") {
                    totalIncome += transaction.amount
                } else {
                    totalExpense += transaction.amount
                }

                y += 30f

                // Check if we need a new page
                if (y > 800f) {
                    document.finishPage(page)
                    val newPage = document.startPage(pageInfo)
                    canvas = newPage.canvas
                    y = 50f
                }
            }

            // Draw separator
            paint.strokeWidth = 2f
            canvas.drawLine(50f, y, 550f, y, paint)
            y += 30f

            // Draw totals
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("Total Income: ${String.format("$%.2f", totalIncome)}", 50f, y, paint)
            y += 30f
            canvas.drawText("Total Expenses: ${String.format("$%.2f", totalExpense)}", 50f, y, paint)
            y += 30f
            canvas.drawText("Net: ${String.format("$%.2f", totalIncome - totalExpense)}", 50f, y, paint)

            document.finishPage(page)

            // Save the document
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val dateTimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "bank_buddie_report_$dateTimeStamp.pdf"
            val file = File(downloadsDir, fileName)

            document.writeTo(FileOutputStream(file))
            document.close()

            // Share the file
            val uri = FileProvider.getUriForFile(
                this,
                "com.vcsma.bank_buddie.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            timeoutHandler.removeCallbacks(timeoutRunnable)
            setLoading(false)

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                addBotMessage("I've generated a PDF report for $period. You can find it in your Downloads folder as $fileName.")
            } else {
                addBotMessage("I've generated a PDF report for $period, but I couldn't open it. You can find it in your Downloads folder as $fileName.")
            }

        } catch (e: IOException) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            setLoading(false)
            Log.e("AIChatbot", "Error creating PDF report", e)
            addBotMessage("I couldn't generate the PDF report due to an error. Please try again later.")
        }
    }

    private fun createCsvReport(transactions: List<TransactionData>, period: String) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val dateTimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "bank_buddie_report_$dateTimeStamp.csv"
            val file = File(downloadsDir, fileName)

            val fileWriter = FileOutputStream(file).writer()

            // Write header
            fileWriter.write("Date,Type,Category,Description,Amount\n")

            // Write transactions
            val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

            transactions.forEach { transaction ->
                val date = dateFormatter.format(Date(transaction.date))
                val type = transaction.type
                val category = transaction.category.replace(",", ";") // Avoid CSV issues
                val description = transaction.description.replace(",", ";") // Avoid CSV issues
                val amount = String.format("%.2f", transaction.amount)

                fileWriter.write("$date,$type,$category,$description,$amount\n")
            }

            fileWriter.close()

            // Share the file
            val uri = FileProvider.getUriForFile(
                this,
                "com.vcsma.bank_buddie.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "text/csv")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            timeoutHandler.removeCallbacks(timeoutRunnable)
            setLoading(false)

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                addBotMessage("I've generated a CSV report for $period. You can find it in your Downloads folder as $fileName.")
            } else {
                addBotMessage("I've generated a CSV report for $period, but I couldn't open it. You can find it in your Downloads folder as $fileName.")
            }

        } catch (e: IOException) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            setLoading(false)
            Log.e("AIChatbot", "Error creating CSV report", e)
            addBotMessage("I couldn't generate the CSV report due to an error. Please try again later.")
        }
    }

    private fun getUserData(userId: String, userMessage: String) {
        // Add debug log to check if this function is being called
        Log.d("AIChatbot", "Fetching user data for userId: $userId")

        // FIXED: Get data from both income and expenses collections
        val expensesTask = db.collection("users").document(userId)
            .collection("expenses")
            .get()

        val incomeTask = db.collection("users").document(userId)
            .collection("income")
            .get()

        // Use Tasks.whenAllComplete to wait for both queries
        com.google.android.gms.tasks.Tasks.whenAllComplete(expensesTask, incomeTask)
            .addOnSuccessListener {
                val expensesResult = expensesTask.result
                val incomeResult = incomeTask.result

                val allTransactions = mutableListOf<TransactionData>()

                // Process expenses
                for (doc in expensesResult.documents) {
                    try {
                        val amount = doc.getDouble("amount") ?: 0.0
                        val category = doc.getString("category") ?: "Uncategorized"
                        val date = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val description = doc.getString("description") ?: ""

                        allTransactions.add(TransactionData(
                            amount = amount,
                            category = category,
                            date = date,
                            description = description,
                            type = "expense"
                        ))
                    } catch (e: Exception) {
                        Log.e("AIChatbot", "Error parsing expense", e)
                    }
                }

                // Process income
                for (doc in incomeResult.documents) {
                    try {
                        val amount = doc.getDouble("amount") ?: 0.0
                        val category = doc.getString("category") ?: "Income"
                        val date = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val description = doc.getString("description") ?: ""

                        allTransactions.add(TransactionData(
                            amount = amount,
                            category = category,
                            date = date,
                            description = description,
                            type = "income"
                        ))
                    } catch (e: Exception) {
                        Log.e("AIChatbot", "Error parsing income", e)
                    }
                }

                // Add more debug logging
                Log.d("AIChatbot", "Parsed transactions: ${allTransactions.size}")
                if (allTransactions.isNotEmpty()) {
                    Log.d("AIChatbot", "First transaction: ${allTransactions[0].amount} for ${allTransactions[0].description}")
                }

                generateResponseWithContext(userMessage, allTransactions)
            }
            .addOnFailureListener { e ->
                // Handle failure to get transactions
                Log.e("AIChatbot", "Failed to get transactions", e)
                generateGenericResponse(userMessage)
            }
    }

    private fun generateResponseWithContext(userMessage: String, transactions: List<TransactionData>) {
        // Cancel timeout
        timeoutHandler.removeCallbacks(timeoutRunnable)

        // Check if there are any transactions at all
        if (transactions.isEmpty()) {
            setLoading(false)
            addBotMessage("I don't see any transactions in your account yet. " +
                    "Would you like me to help you add some? Try saying something like " +
                    "'Add an expense of \$45 for dinner in the Food category'.")
            return
        }

        val lowerCaseMessage = userMessage.lowercase(Locale.getDefault())
        val response = when {
            // Monthly expense summary
            lowerCaseMessage.contains("monthly expense") ||
                    lowerCaseMessage.contains("expense summary") ||
                    lowerCaseMessage.contains("monthly spending") -> {
                generateExpenseSummary(transactions)
            }

            // Category insights
            lowerCaseMessage.contains("category") &&
                    (lowerCaseMessage.contains("spending") || lowerCaseMessage.contains("expenses")) -> {
                generateCategoryInsights(transactions)
            }

            // Savings suggestions
            lowerCaseMessage.contains("save") ||
                    lowerCaseMessage.contains("saving") ||
                    lowerCaseMessage.contains("budget") -> {
                generateSavingsSuggestions(transactions)
            }

            // Income analysis
            lowerCaseMessage.contains("income") ||
                    lowerCaseMessage.contains("earning") -> {
                generateIncomeAnalysis(transactions)
            }

            // Default response with personalized touch
            else -> {
                "I'm not sure I understand. You can ask me about your monthly expenses, " +
                        "category insights, savings suggestions, or income analysis.\n\n" +
                        "I can also help you:\n" +
                        "• Create a new category (try 'Create a new category called Dining')\n" +
                        "• Add a transaction (try 'Add an expense of \$45 for dinner in the Food category')\n" +
                        "• Generate a report (try 'Generate a PDF report for this month')\n\n" +
                        "What would you like to do?"
            }
        }

        // Hide loading indicator
        setLoading(false)

        // Add bot response
        addBotMessage(response)
    }

    private fun generateGenericResponse(userMessage: String) {
        // Cancel timeout
        timeoutHandler.removeCallbacks(timeoutRunnable)

        val lowerCaseMessage = userMessage.lowercase(Locale.getDefault())
        val response = when {
            lowerCaseMessage.contains("expense") || lowerCaseMessage.contains("spending") -> {
                "To analyze your expenses, I'll need access to your transaction data. " +
                        "Please make sure you're logged in and have recorded some transactions. " +
                        "Would you like me to help you add a transaction now? Try saying something like " +
                        "'Add an expense of \$45 for dinner in the Food category'."
            }

            lowerCaseMessage.contains("save") || lowerCaseMessage.contains("budget") -> {
                "I can provide personalized savings advice based on your spending patterns. " +
                        "Please add some transactions so I can analyze your financial habits. " +
                        "Would you like me to help you add a transaction now? Try saying something like " +
                        "'Add an expense of \$45 for dinner in the Food category'."
            }

            lowerCaseMessage.contains("income") || lowerCaseMessage.contains("earning") -> {
                "To provide income analysis, I'll need to see your income transactions. " +
                        "Would you like me to help you add an income transaction? Try saying something like " +
                        "'Add an income of \$2000 for salary'."
            }

            else -> {
                "I'm here to help with your financial questions, but I need access to your " +
                        "transaction data. Please make sure you're logged in and have recorded some transactions. " +
                        "I can also help you:\n" +
                        "• Create a new category (try 'Create a new category called Dining')\n" +
                        "• Add a transaction (try 'Add an expense of \$45 for dinner in the Food category')\n" +
                        "• Generate a report (try 'Generate a PDF report for this month')\n\n" +
                        "What would you like to do?"
            }
        }

        // Hide loading indicator
        setLoading(false)

        // Add bot response
        addBotMessage(response)
    }

    private fun generateExpenseSummary(transactions: List<TransactionData>): String {
        // Filter expenses from the past 30 days
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
        val recentExpenses = transactions.filter {
            it.type == "expense" && it.date >= thirtyDaysAgo
        }

        if (recentExpenses.isEmpty()) {
            return "I don't see any expenses in the last 30 days. Would you like to add some?"
        }

        val totalSpent = recentExpenses.sumOf { it.amount }

        // Group by category and calculate percentage
        val categoryMap = mutableMapOf<String, Double>()
        recentExpenses.forEach {
            categoryMap[it.category] = (categoryMap[it.category] ?: 0.0) + it.amount
        }

        val categoryPercentages = categoryMap.entries.sortedByDescending { it.value }.map {
            val percentage = (it.value / totalSpent) * 100
            Pair(it.key, percentage)
        }

        val dateFormatter = SimpleDateFormat("MMMM d", Locale.getDefault())
        val startDate = dateFormatter.format(Date(thirtyDaysAgo))
        val endDate = dateFormatter.format(Date())

        val builder = StringBuilder()
        builder.append("📊 **Monthly Expense Summary**\n\n")
        builder.append("From $startDate to $endDate, you spent a total of ${String.format("$%.2f", totalSpent)}.\n\n")
        builder.append("Your top spending categories:\n")

        categoryPercentages.take(3).forEach {
            builder.append("• ${it.first}: ${String.format("%.1f", it.second)}% (${String.format("$%.2f", categoryMap[it.first])})\n")
        }

        if (categoryPercentages.size > 3) {
            val otherTotal = categoryPercentages.drop(3).sumOf { categoryMap[it.first] ?: 0.0 }
            val otherPercentage = (otherTotal / totalSpent) * 100
            builder.append("• Other: ${String.format("%.1f", otherPercentage)}% (${String.format("$%.2f", otherTotal)})\n")
        }

        // Add recent transactions
        builder.append("\nYour 3 most recent expenses:\n")
        recentExpenses.sortedByDescending { it.date }.take(3).forEach {
            val date = dateFormatter.format(Date(it.date))
            builder.append("• $date: ${String.format("$%.2f", it.amount)} for ${it.description} (${it.category})\n")
        }

        return builder.toString()
    }

    private fun generateCategoryInsights(transactions: List<TransactionData>): String {
        // Filter expenses from the past 90 days
        val ninetyDaysAgo = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000)
        val recentExpenses = transactions.filter {
            it.type == "expense" && it.date >= ninetyDaysAgo
        }

        if (recentExpenses.isEmpty()) {
            return "I don't see any categorized expenses in the last 90 days. Would you like to add some?"
        }

        // Group by category
        val categoryMap = mutableMapOf<String, MutableList<TransactionData>>()
        recentExpenses.forEach {
            if (!categoryMap.containsKey(it.category)) {
                categoryMap[it.category] = mutableListOf()
            }
            categoryMap[it.category]?.add(it)
        }

        // Calculate totals and averages
        val categoryStats = categoryMap.map { (category, transactions) ->
            val total = transactions.sumOf { it.amount }
            val average = total / 3 // 3 months
            Triple(category, total, average)
        }.sortedByDescending { it.second }

        val totalSpent = recentExpenses.sumOf { it.amount }

        val builder = StringBuilder()
        builder.append("📋 **Category Spending Analysis**\n\n")
        builder.append("Over the past 90 days, you've spent ${String.format("$%.2f", totalSpent)} across ${categoryMap.size} categories.\n\n")

        builder.append("Your top categories:\n")
        categoryStats.take(5).forEach { (category, total, monthly) ->
            val percentage = (total / totalSpent) * 100
            builder.append("• ${category}: ${String.format("$%.2f", total)} (${String.format("%.1f", percentage)}%)\n")
            builder.append("  Monthly average: ${String.format("$%.2f", monthly)}\n")
        }

        // Add insights for top category
        if (categoryStats.isNotEmpty()) {
            val topCategory = categoryStats[0].first
            val topTotal = categoryStats[0].second
            val topPercentage = (topTotal / totalSpent) * 100

            builder.append("\n💡 **Insight for ${topCategory}**\n")
            if (topPercentage > 30) {
                builder.append("${topCategory} accounts for a significant ${String.format("%.1f", topPercentage)}% of your expenses. ")
                builder.append("Consider reviewing these expenses to identify potential savings opportunities.\n")
            } else {
                builder.append("Your spending in ${topCategory} seems reasonable at ${String.format("%.1f", topPercentage)}% of total expenses. ")
                builder.append("Keep up the good work managing this category!\n")
            }
        }

        return builder.toString()
    }

    private fun generateSavingsSuggestions(transactions: List<TransactionData>): String {
        // Filter expenses from the past 30 days
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
        val recentExpenses = transactions.filter {
            it.type == "expense" && it.date >= thirtyDaysAgo
        }

        if (recentExpenses.isEmpty()) {
            return "I don't have enough transaction data to provide personalized savings advice. " +
                    "Please add some expenses first so I can analyze your spending patterns."
        }

        // Group by category
        val categoryTotals = mutableMapOf<String, Double>()
        recentExpenses.forEach {
            categoryTotals[it.category] = (categoryTotals[it.category] ?: 0.0) + it.amount
        }

        val topCategories = categoryTotals.entries.sortedByDescending { it.value }.take(3)

        val builder = StringBuilder()
        builder.append("💰 **Personalized Savings Suggestions**\n\n")

        // General advice
        builder.append("Based on your spending over the past 30 days, here are some personalized tips to help you save money:\n\n")

        // Category-specific advice
        topCategories.forEach { (category, total) ->
            builder.append("📌 **${category} (${String.format("$%.2f", total)})**\n")

            val suggestion = when (category.lowercase()) {
                "food", "groceries", "dining", "restaurants" ->
                    "Consider meal planning and cooking at home more often. Try batch cooking on weekends to save time and money during the week."

                "entertainment", "recreation" ->
                    "Look for free or low-cost entertainment options in your area. Many museums have free days, and parks offer free recreation."

                "shopping", "clothing" ->
                    "Try implementing a 24-hour rule before making non-essential purchases. This helps avoid impulse buying."

                "transportation", "gas", "fuel" ->
                    "Consider carpooling, using public transportation, or combining errands to save on fuel costs."

                "utilities", "bills" ->
                    "Review your subscriptions and cancel ones you rarely use. Also consider energy-saving measures at home."

                else ->
                    "Review your spending in this category to identify non-essential expenses that could be reduced."
            }

            builder.append("$suggestion\n\n")
        }

        // 50/30/20 rule
        builder.append("💡 **Budgeting Tip**\n")
        builder.append("Consider using the 50/30/20 rule: spend 50% of your income on needs, 30% on wants, and save 20%.\n\n")

        // Final encouragement
        builder.append("Would you like me to help you set up a specific savings goal or budget for any of these categories?")

        return builder.toString()
    }

    private fun generateIncomeAnalysis(transactions: List<TransactionData>): String {
        // Filter income from all time
        val incomeTransactions = transactions.filter { it.type == "income" }

        if (incomeTransactions.isEmpty()) {
            return "I don't see any income entries in your account. Would you like me to help you add some income? " +
                    "Try saying something like 'Add an income of \$2000 for salary'."
        }

        // Get the last 90 days for comparison
        val ninetyDaysAgo = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000)

        // Recent income and expenses
        val recentIncome = transactions.filter {
            it.type == "income" && it.date >= ninetyDaysAgo
        }.sumOf { it.amount }

        val recentExpenses = transactions.filter {
            it.type == "expense" && it.date >= ninetyDaysAgo
        }.sumOf { it.amount }

        // Group income by description (source)
        val incomeBySource = mutableMapOf<String, Double>()
        incomeTransactions.forEach {
            val source = if (it.description.isBlank()) "Other" else it.description
            incomeBySource[source] = (incomeBySource[source] ?: 0.0) + it.amount
        }

        val topSources = incomeBySource.entries.sortedByDescending { it.value }

        val builder = StringBuilder()
        builder.append("💵 **Income Analysis**\n\n")

        // Total income
        val totalIncome = incomeTransactions.sumOf { it.amount }
        builder.append("Your total recorded income is ${String.format("$%.2f", totalIncome)}.\n\n")

        // Income sources
        builder.append("**Income Sources:**\n")
        topSources.forEach { (source, amount) ->
            val percentage = (amount / totalIncome) * 100
            builder.append("• ${source}: ${String.format("$%.2f", amount)} (${String.format("%.1f", percentage)}%)\n")
        }

        // Income vs Expenses comparison (if data available)
        if (recentIncome > 0 && recentExpenses > 0) {
            builder.append("\n**Last 90 Days Comparison:**\n")
            builder.append("• Income: ${String.format("$%.2f", recentIncome)}\n")
            builder.append("• Expenses: ${String.format("$%.2f", recentExpenses)}\n")

            val difference = recentIncome - recentExpenses
            val savingsRate = (difference / recentIncome) * 100

            builder.append("• Net: ${String.format("$%.2f", difference)}\n")
            builder.append("• Savings Rate: ${String.format("%.1f", savingsRate)}%\n\n")

            // Financial health assessment
            builder.append("**Financial Health Assessment:**\n")
            when {
                savingsRate >= 20 -> {
                    builder.append("You're saving ${String.format("%.1f", savingsRate)}% of your income, which is excellent! ")
                    builder.append("Consider investing some of your savings for long-term growth.")
                }
                savingsRate > 0 -> {
                    builder.append("You're saving ${String.format("%.1f", savingsRate)}% of your income. ")
                    builder.append("The recommended savings rate is at least 20%. Consider reviewing your expenses to increase your savings.")
                }
                savingsRate == 0.0 -> {
                    builder.append("You're breaking even, spending exactly what you earn. ")
                    builder.append("Try to find ways to reduce expenses so you can build an emergency fund.")
                }
                else -> {
                    builder.append("You're spending more than you earn. ")
                    builder.append("Consider reviewing your budget to identify areas where you can cut back on expenses.")
                }
            }
        }

        return builder.toString()
    }

    private fun setLoading(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun scrollToBottom() {
        chatRecyclerView.post {
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}

data class ChatMessage(
    val id: String,
    val message: String,
    val timestamp: Long,
    val isFromUser: Boolean
)

data class TransactionData(
    val amount: Double,
    val category: String,
    val date: Long,
    val description: String,
    val type: String
)

class ChatMessageAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_user, parent, false)
                UserMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_bot, parent, false)
                BotMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is BotMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(message: ChatMessage) {
            messageText.text = message.message

            val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            timeText.text = dateFormat.format(Date(message.timestamp))
        }
    }

    class BotMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(message: ChatMessage) {
            messageText.text = message.message

            val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            timeText.text = dateFormat.format(Date(message.timestamp))
        }
    }
}