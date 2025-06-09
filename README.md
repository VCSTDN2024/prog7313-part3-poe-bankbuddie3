# Bank Buddie: Financial Management Application

Bank Buddie is a comprehensive mobile financial management application built with Kotlin for Android. It provides intelligent and dynamic personal finance tracking with enhanced user experience features.

## Key Features

### Authentication
- **Login System**: Users can securely log in using username and password
- **Firebase Authentication**: All user credentials are securely managed through Firebase Auth
- **Profile Management**: Users can update their profile information(coming soon)

### Transaction Management
- **Expense Tracking**: Create and track expenses with detailed information
- **Income Recording**: Log income sources and amounts
- **Transaction History**: View complete transaction history with filtering options
- **Photo Attachments**: Optionally attach photos to expense entries for receipts

### Category Management
- **Custom Categories**: Create personalized expense and income categories
- **Category Analysis**: View total expenses per category during custom time periods
- **Category Filtering**: Filter transactions by category

### Budget Management
- **Budget Goals**: Set minimum and maximum monthly budget goals
- **Goal Tracking**: Visual indicators for progress toward spending goals(coming soon)
- **Dynamic Analysis**: Real-time calculation of spending relative to goals

### Reporting & Analysis
- **Period Selection**: View data across custom time periods (daily, weekly, monthly)
- **Spending Summary**: Visualize spending patterns by category
- **Balance Overview**: Dynamic account balance calculation

### Search Functionality
- **Dynamic Search**: Real-time search across all transactions
- **Multi-criteria Filtering**: Filter by date, category, amount, and type
- **Date-based Searches**: Find transactions within specific time periods

## Firebase Integration

The app uses Firebase as its backend with the following components:

- **Firebase Authentication**: Secure user login and session management
- **Firestore Database**: Real-time data storage with the following collections:
  - `users`: User profile information
  - `transactions`: All income and expense records
  - `expenseCategories`: Custom expense categories
  - `incomeCategories`: Custom income categories
  - `budgetGoals`: User-defined spending goals
  - `photos`: References to expense receipt images

All data is synced in real-time, allowing for immediate updates across the app when changes are made.

## Known Issues & Troubleshooting

### Navigation Issues
- **Bottom Navigation**: The bottom navigation bar may sometimes not respond to clicks. To resolve, restart the app or navigate back to the dashboard first.
- **Feature Access**: Some features may not appear immediately after being added. To ensure all features are visible, return to the dashboard and then navigate to the desired feature.

### UI Elements
- **Spinner Loading**: Occasionally, category spinners may show a loading indicator indefinitely. To fix:
  1. Navigate back to the previous screen
  2. Return to the screen with the spinner
  3. If the issue persists, restart the app

### Data Synchronization
- **Real-time Updates**: Changes made to transactions, categories, or budget goals may take a few seconds to reflect across all screens due to Firebase synchronization.
- **Transaction List**: The transaction list sometimes requires a manual refresh to show newly added entries. Pull down on the list to refresh.

### Photo Attachments
- **Image Loading**: When viewing expense entries with attached photos, images may load slowly depending on network conditions.
- **Camera Access**: If the camera doesn't open when trying to attach a photo, check the app permissions in your device settings.

## Using the App

### First-time Setup
1. Log in with your credentials
2. Create your first income and expense categories
3. Set up your budget goals

### Adding Transactions
1. Navigate to "Add Expense" or "Add Income" from the dashboard
2. Fill in the required details:
   - Date
   - Start/end times (for expenses)
   - Description
   - Category (select from your created categories)
   - Amount
   - Optional photo attachment
3. Save the transaction

### Viewing Financial Data
1. Use the "Account Balance" section to view your overall financial position
2. Navigate to "Categories" to see spending by category
3. Use "Search" to find specific transactions
4. Check "Budget Goals" to track your progress against spending targets

### Setting Budget Goals
1. Navigate to the "Budget Goals" section
2. Set your minimum and maximum monthly spending targets
3. The app will automatically track your progress against these goals

## Technical Architecture

Bank Buddie follows an MVVM (Model-View-ViewModel) architecture pattern with:

- **UI Layer**: Activities and Fragments for user interaction
- **ViewModel Layer**: Business logic and state management
- **Repository Layer**: Data access and manipulation
- **Firebase Services**: Backend data storage and authentication

All data is persistently stored in Firebase Firestore, ensuring it's available across devices and sessions.

## Best Practices for Use

- Regularly check your Account Balance to maintain awareness of your financial status
- Create specific categories to better track spending patterns
- Review category spending periodically to identify areas for budget optimization
- Take photos of receipts for important purchases to maintain accurate records
- Set realistic budget goals based on your income and necessary expenses
