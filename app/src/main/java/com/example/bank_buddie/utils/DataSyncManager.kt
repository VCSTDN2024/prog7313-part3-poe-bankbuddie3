package com.vcsma.bank_buddie.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Manager class for handling data synchronization with Firestore
 * Provides optimized methods for retrieving and caching data to improve performance
 */
class DataSyncManager(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val TAG = "DataSyncManager"
    private val preferences: SharedPreferences = context.getSharedPreferences("data_sync_prefs", Context.MODE_PRIVATE)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Cache durations
    private val SHORT_CACHE_DURATION = TimeUnit.MINUTES.toMillis(1) // 1 minute
    private val MEDIUM_CACHE_DURATION = TimeUnit.MINUTES.toMillis(5) // 5 minutes
    private val LONG_CACHE_DURATION = TimeUnit.HOURS.toMillis(1) // 1 hour

    // In-memory cache for faster access
    private val memoryCache = mutableMapOf<String, Pair<Long, Any>>()

    init {
        // Schedule periodic cache cleanup
        scheduler.scheduleAtFixedRate({
            cleanupExpiredCache()
        }, 30, 30, TimeUnit.MINUTES)
    }

    /**
     * Synchronizes user data with the latest from Firestore
     * Uses caching for improved performance
     *
     * @param userId The ID of the user to synchronize data for
     * @param forceRefresh Whether to force a refresh from the server
     * @param callback Callback to receive the user data or error
     */
    fun syncUserData(userId: String, forceRefresh: Boolean = false, callback: (Map<String, Any>?, Exception?) -> Unit) {
        val cacheKey = "user_data_$userId"

        // Check cache first if not forcing refresh
        if (!forceRefresh) {
            val cachedData = getFromCache(cacheKey)
            if (cachedData != null) {
                @Suppress("UNCHECKED_CAST")
                callback(cachedData as? Map<String, Any>, null)
                return
            }
        }

        // Fetch from Firestore
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userData = document.data
                    if (userData != null) {
                        // Store in cache
                        saveToCache(cacheKey, userData, MEDIUM_CACHE_DURATION)
                        callback(userData, null)
                    } else {
                        callback(null, Exception("User data is null"))
                    }
                } else {
                    callback(null, Exception("User document doesn't exist"))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error syncing user data", e)
                callback(null, e)
            }
    }

    /**
     * Gets financial data for a specific time period
     *
     * @param userId The ID of the user to get data for
     * @param startDate The start timestamp of the period
     * @param endDate The end timestamp of the period
     * @param forceRefresh Whether to force a refresh from the server
     * @param callback Callback with income and expenses data
     */
    fun getFinancialData(
        userId: String,
        startDate: Long,
        endDate: Long,
        forceRefresh: Boolean = false,
        callback: (income: List<Map<String, Any>>?, expenses: List<Map<String, Any>>?, Exception?) -> Unit
    ) {
        val cacheKey = "financial_${userId}_${startDate}_${endDate}"

        // Check cache first if not forcing refresh
        if (!forceRefresh) {
            val cachedData = getFromCache(cacheKey)
            if (cachedData != null) {
                @Suppress("UNCHECKED_CAST")
                val data = cachedData as? Pair<List<Map<String, Any>>, List<Map<String, Any>>>
                if (data != null) {
                    callback(data.first, data.second, null)
                    return
                }
            }
        }

        // Fetch income and expenses in parallel
        var income: List<Map<String, Any>>? = null
        var expenses: List<Map<String, Any>>? = null
        var incomeError: Exception? = null
        var expensesError: Exception? = null

        // Track completion
        var incomeComplete = false
        var expensesComplete = false

        // Function to check if both operations are complete
        val checkCompletion = {
            if (incomeComplete && expensesComplete) {
                if (incomeError != null || expensesError != null) {
                    callback(income, expenses, incomeError ?: expensesError)
                } else {
                    // Cache the results
                    if (income != null && expenses != null) {
                        saveToCache(cacheKey, Pair(income, expenses), SHORT_CACHE_DURATION)
                    }
                    callback(income, expenses, null)
                }
            }
        }

        // Fetch income
        db.collection("users").document(userId)
            .collection("income")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .get()
            .addOnSuccessListener { documents ->
                income = documents.documents.mapNotNull { it.data }
                incomeComplete = true
                checkCompletion()
            }
            .addOnFailureListener { e ->
                incomeError = e
                incomeComplete = true
                checkCompletion()
            }

        // Fetch expenses
        db.collection("users").document(userId)
            .collection("expenses")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .get()
            .addOnSuccessListener { documents ->
                expenses = documents.documents.mapNotNull { it.data }
                expensesComplete = true
                checkCompletion()
            }
            .addOnFailureListener { e ->
                expensesError = e
                expensesComplete = true
                checkCompletion()
            }
    }

    /**
     * Gets budget goals for a user
     *
     * @param userId The ID of the user to get goals for
     * @param timeframe The timeframe (weekly, monthly, etc.)
     * @param category Optional category filter
     * @param callback Callback with the budget goal data
     */
    fun getBudgetGoals(
        userId: String,
        timeframe: String,
        category: String = "All",
        callback: (Map<String, Any>?, Exception?) -> Unit
    ) {
        val docPath = if (category == "All") {
            timeframe.lowercase()
        } else {
            "${timeframe.lowercase()}_${category.lowercase()}"
        }

        val cacheKey = "budget_goal_${userId}_${docPath}"

        // Check cache
        val cachedData = getFromCache(cacheKey)
        if (cachedData != null) {
            @Suppress("UNCHECKED_CAST")
            callback(cachedData as? Map<String, Any>, null)
            return
        }

        // Fetch from Firestore
        db.collection("users").document(userId)
            .collection("budgetGoals")
            .document(docPath)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val goalData = document.data
                    if (goalData != null) {
                        // Cache the data
                        saveToCache(cacheKey, goalData, MEDIUM_CACHE_DURATION)
                        callback(goalData, null)
                    } else {
                        callback(null, null)
                    }
                } else {
                    callback(null, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting budget goals", e)
                callback(null, e)
            }
    }

    /**
     * Gets expense categories for a user
     */
    fun getExpenseCategories(
        userId: String,
        callback: (List<String>, Exception?) -> Unit
    ) {
        val cacheKey = "categories_$userId"

        // Check cache
        val cachedData = getFromCache(cacheKey)
        if (cachedData != null) {
            @Suppress("UNCHECKED_CAST")
            callback(cachedData as List<String>, null)
            return
        }

        // Fetch from Firestore
        db.collection("users").document(userId)
            .collection("expenseCategories")
            .get()
            .addOnSuccessListener { documents ->
                val categories = mutableListOf<String>()
                categories.add("All") // Always include "All" category

                for (document in documents) {
                    val categoryName = document.getString("name")
                    if (!categoryName.isNullOrEmpty() && !categories.contains(categoryName)) {
                        categories.add(categoryName)
                    }
                }

                // Cache the results
                saveToCache(cacheKey, categories, LONG_CACHE_DURATION)
                callback(categories, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting categories", e)
                callback(listOf("All"), e)
            }
    }

    /**
     * Saves data to both memory and persistent cache
     */
    private fun saveToCache(key: String, data: Any, duration: Long) {
        val expiryTime = System.currentTimeMillis() + duration

        // Save to memory cache
        memoryCache[key] = Pair(expiryTime, data)

        // Save to persistent cache (just the expiry time)
        preferences.edit().putLong(key + "_expiry", expiryTime).apply()

        // The actual data is only stored in memory to avoid serialization overhead
    }

    /**
     * Retrieves data from cache if it's still valid
     */
    private fun getFromCache(key: String): Any? {
        // Check memory cache first (faster)
        val memoryCached = memoryCache[key]
        if (memoryCached != null) {
            if (memoryCached.first > System.currentTimeMillis()) {
                return memoryCached.second
            } else {
                // Expired, remove from memory cache
                memoryCache.remove(key)
            }
        }

        // Check if we have an entry in persistent cache
        val expiryTime = preferences.getLong(key + "_expiry", 0)
        if (expiryTime > System.currentTimeMillis()) {
            // We know it should exist but isn't in memory - data was likely cleared from memory
            // Return null as we only store expiry times in persistent cache, not the data itself
            return null
        }

        return null
    }

    /**
     * Cleans up expired cache entries
     */
    private fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()

        // Clean memory cache
        val expiredKeys = memoryCache.filter { it.value.first < currentTime }.keys
        expiredKeys.forEach { memoryCache.remove(it) }

        // Clean persistent cache (would need to iterate through all keys)
        // This is simplified and less efficient than it could be
        val allKeys = preferences.all.keys
        val expiredPrefKeys = allKeys
            .filter { it.endsWith("_expiry") }
            .filter { preferences.getLong(it, 0) < currentTime }

        val editor = preferences.edit()
        expiredPrefKeys.forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * Clears all cached data
     */
    fun clearCache() {
        memoryCache.clear()
        preferences.edit().clear().apply()
    }

    /**
     * Shutdown the scheduler when no longer needed
     */
    fun shutdown() {
        scheduler.shutdown()
    }
}
