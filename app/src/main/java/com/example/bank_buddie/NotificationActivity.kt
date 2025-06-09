package com.vcsma.bank_buddie

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var recyclerView: RecyclerView
    private lateinit var backButton: ImageView
    private lateinit var markAllReadButton: ImageView
    private lateinit var emptyView: View
    private lateinit var progressBar: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: NotificationAdapter
    private val notifications = mutableListOf<NotificationItem>()

    // Handler for timeout
    private val handler = Handler(Looper.getMainLooper())
    private val OPERATION_TIMEOUT = 10000L // 10 seconds timeout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        try {
            // Initialize Firebase
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            // Initialize views
            initializeViews()

            // Set up UI
            setupSwipeRefresh()
            setupRecyclerView()
            setupClickListeners()
            setupBottomNavigation()

            // Load notifications
            loadNotifications()
        } catch (e: Exception) {
            Log.e("NotificationActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing notifications: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up handler to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }

    private fun initializeViews() {
        try {
            recyclerView = findViewById(R.id.notificationsRecyclerView)
            backButton = findViewById(R.id.backButton)
            markAllReadButton = findViewById(R.id.markAllReadButton)
            emptyView = findViewById(R.id.emptyView)
            progressBar = findViewById(R.id.progressBar)
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

            // Initialize adapter
            adapter = NotificationAdapter(notifications) { notification ->
                // Handle notification click
                markNotificationAsRead(notification)
            }
        } catch (e: Exception) {
            Log.e("NotificationActivity", "Error initializing views: ${e.message}")
            throw e
        }
    }

    private fun setupSwipeRefresh() {
        try {
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this, R.color.teal_700),
                ContextCompat.getColor(this, R.color.income_green),
                ContextCompat.getColor(this, R.color.purple_500)
            )

            swipeRefreshLayout.setOnRefreshListener {
                loadNotifications()
            }
        } catch (e: Exception) {
            Log.e("NotificationActivity", "Error setting up SwipeRefresh: ${e.message}")
            throw e
        }
    }

    private fun setupRecyclerView() {
        try {
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
        } catch (e: Exception) {
            Log.e("NotificationActivity", "Error setting up RecyclerView: ${e.message}")
            throw e
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        markAllReadButton.setOnClickListener {
            markAllNotificationsAsRead()
        }
    }

    private fun setupBottomNavigation() {
        // Find all bottom navigation buttons
        val homeButton = findViewById<ImageView>(R.id.homeButton)
        val searchButton = findViewById<ImageView>(R.id.searchButton)
        val addButton = findViewById<ImageView>(R.id.addButton)
        val notificationsButton = findViewById<ImageView>(R.id.notificationsButton)
        val profileButton = findViewById<ImageView>(R.id.profileButton)

        // Set up navigation
        homeButton.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }

        searchButton.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        addButton.setOnClickListener {
            val intent = Intent(this, AIChatbotActivity::class.java)
            startActivity(intent)
        }

        // Current activity - highlight notifications button
        notificationsButton.setImageResource(R.drawable.ic_notifications_filled)

        profileButton.setOnClickListener {
            val intent = Intent(this, FinancialHealthActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadNotifications() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // If not logged in, show sample notifications
            createSampleNotifications()
            return
        }

        try {
            // Show loading indicator
            if (!swipeRefreshLayout.isRefreshing) {
                progressBar.visibility = View.VISIBLE
            }
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.GONE

            // Set timeout for loading
            handler.postDelayed({
                if (progressBar.visibility == View.VISIBLE || swipeRefreshLayout.isRefreshing) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    emptyView.visibility = View.VISIBLE
                    Toast.makeText(this, "Loading notifications timed out. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }, OPERATION_TIMEOUT)

            // Get notifications from Firestore with error handling
            db.collection("users").document(currentUser.uid)
                .collection("notifications")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50) // Limit to recent notifications
                .get()
                .addOnSuccessListener { documents ->
                    try {
                        // Cancel timeout
                        handler.removeCallbacksAndMessages(null)

                        progressBar.visibility = View.GONE
                        swipeRefreshLayout.isRefreshing = false

                        // Clear existing notifications
                        notifications.clear()

                        if (documents.isEmpty) {
                            // Show empty state
                            emptyView.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        } else {
                            processNotifications(documents)
                            emptyView.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                        }

                        // Update adapter
                        adapter.notifyDataSetChanged()
                    } catch (e: Exception) {
                        handleError("Error processing notifications: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    handleError("Error loading notifications: ${e.message}")
                }
        } catch (e: Exception) {
            handleError("Unexpected error: ${e.message}")
        }
    }

    private fun handleError(errorMessage: String) {
        // Cancel timeout
        handler.removeCallbacksAndMessages(null)

        // Hide loading indicators
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false

        // Show error message
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()

        // Show sample notifications as fallback
        createSampleNotifications()
    }

    private fun processNotifications(documents: com.google.firebase.firestore.QuerySnapshot) {
        try {
            // Group notifications by day
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val thisWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }

            val todayStart = getStartOfDay(today.timeInMillis)
            val yesterdayStart = getStartOfDay(yesterday.timeInMillis)
            val thisWeekStart = getStartOfDay(thisWeek.timeInMillis)

            var hasToday = false
            var hasYesterday = false
            var hasThisWeek = false

            // Process notifications
            for (document in documents) {
                try {
                    val timestamp = document.getLong("timestamp") ?: 0
                    val title = document.getString("title") ?: "Notification"
                    val message = document.getString("message") ?: ""
                    val type = document.getString("type") ?: "REMINDER"
                    val details = document.getString("details")
                    val isRead = document.getBoolean("isRead") ?: false

                    // Determine which day this notification belongs to
                    when {
                        timestamp >= todayStart -> {
                            if (!hasToday) {
                                notifications.add(NotificationItem.Header("Today"))
                                hasToday = true
                            }
                        }
                        timestamp >= yesterdayStart -> {
                            if (!hasYesterday) {
                                notifications.add(NotificationItem.Header("Yesterday"))
                                hasYesterday = true
                            }
                        }
                        timestamp >= thisWeekStart -> {
                            if (!hasThisWeek) {
                                notifications.add(NotificationItem.Header("This Week"))
                                hasThisWeek = true
                            }
                        }
                        else -> {
                            // Skip older notifications
                            continue
                        }
                    }

                    // Format timestamp
                    val date = Date(timestamp)
                    val formattedTime = formatTime(date)
                    val formattedDate = formatDate(date)

                    // Add notification
                    notifications.add(
                        NotificationItem.Notification(
                            id = document.id,
                            type = getNotificationType(type),
                            title = title,
                            message = message,
                            timestamp = "$formattedTime • $formattedDate",
                            details = details,
                            isRead = isRead
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid notifications
                    continue
                }
            }
        } catch (e: Exception) {
            throw Exception("Error processing notifications: ${e.message}")
        }
    }

    private fun createSampleNotifications() {
        // Clear existing notifications
        notifications.clear()

        // Get current date for timestamps
        val now = System.currentTimeMillis()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val thisWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }

        // Create header for Today
        notifications.add(NotificationItem.Header("Today"))

        // Add today's notifications
        notifications.add(
            NotificationItem.Notification(
                id = "1",
                type = NotificationType.BUDGET_ALERT,
                title = "Budget Alert",
                message = "You've spent 80% of your monthly food budget",
                timestamp = formatTime(today.time) + " • " + formatDate(today.time),
                isRead = false
            )
        )

        notifications.add(
            NotificationItem.Notification(
                id = "2",
                type = NotificationType.REMINDER,
                title = "Savings Reminder",
                message = "Don't forget to transfer money to your savings account",
                timestamp = formatTime(today.time) + " • " + formatDate(today.time),
                isRead = false
            )
        )

        // Create header for Yesterday
        notifications.add(NotificationItem.Header("Yesterday"))

        // Add yesterday's notifications
        notifications.add(
            NotificationItem.Notification(
                id = "3",
                type = NotificationType.TRANSACTION,
                title = "New Transaction",
                message = "A new expense has been recorded",
                details = "Groceries • Food • -R150.00",
                timestamp = formatTime(yesterday.time) + " • " + formatDate(yesterday.time),
                isRead = true
            )
        )

        notifications.add(
            NotificationItem.Notification(
                id = "4",
                type = NotificationType.INCOME,
                title = "Income Added",
                message = "Your salary has been recorded",
                details = "Salary • Income • +R5,000.00",
                timestamp = formatTime(yesterday.time) + " • " + formatDate(yesterday.time),
                isRead = true
            )
        )

        // Create header for This Week
        notifications.add(NotificationItem.Header("This Week"))

        // Add week notifications
        notifications.add(
            NotificationItem.Notification(
                id = "5",
                type = NotificationType.GOAL_ACHIEVED,
                title = "Goal Achieved!",
                message = "Congratulations! You've reached your savings goal",
                timestamp = formatTime(thisWeek.time) + " • " + formatDate(thisWeek.time),
                isRead = true
            )
        )

        notifications.add(
            NotificationItem.Notification(
                id = "6",
                type = NotificationType.UPDATE,
                title = "App Update",
                message = "New features available in the latest version",
                timestamp = formatTime(thisWeek.time) + " • " + formatDate(thisWeek.time),
                isRead = true
            )
        )

        // Show notifications
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false

        // Notify adapter of data change
        adapter.notifyDataSetChanged()
    }

    private fun markNotificationAsRead(notification: NotificationItem.Notification) {
        val currentUser = auth.currentUser ?: return

        // Update in Firestore
        db.collection("users").document(currentUser.uid)
            .collection("notifications")
            .document(notification.id)
            .update("isRead", true)
            .addOnSuccessListener {
                // Update local data
                val index = notifications.indexOfFirst {
                    it is NotificationItem.Notification && it.id == notification.id
                }
                if (index != -1) {
                    val updatedNotification = notification.copy(isRead = true)
                    notifications[index] = updatedNotification
                    adapter.notifyItemChanged(index)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating notification: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markAllNotificationsAsRead() {
        val currentUser = auth.currentUser ?: return

        // Update all unread notifications in Firestore
        val batch = db.batch()
        notifications.filterIsInstance<NotificationItem.Notification>()
            .filter { !it.isRead }
            .forEach { notification ->
                val docRef = db.collection("users").document(currentUser.uid)
                    .collection("notifications")
                    .document(notification.id)
                batch.update(docRef, "isRead", true)
            }

        batch.commit()
            .addOnSuccessListener {
                // Update local data
                for (i in notifications.indices) {
                    val item = notifications[i]
                    if (item is NotificationItem.Notification && !item.isRead) {
                        notifications[i] = item.copy(isRead = true)
                    }
                }
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating notifications: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun formatTime(date: Date): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(date)
    }

    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("MMM dd", Locale.getDefault())
        return formatter.format(date)
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getNotificationType(type: String): NotificationType {
        return when (type.lowercase(Locale.ROOT)) {
            "reminder" -> NotificationType.REMINDER
            "update" -> NotificationType.UPDATE
            "transaction" -> NotificationType.TRANSACTION
            "expense" -> NotificationType.EXPENSE
            "income" -> NotificationType.INCOME
            "budget_alert" -> NotificationType.BUDGET_ALERT
            "goal_achieved" -> NotificationType.GOAL_ACHIEVED
            else -> NotificationType.REMINDER
        }
    }
}
