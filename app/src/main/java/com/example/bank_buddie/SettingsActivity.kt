package com.vcsma.bank_buddie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var storageRef: StorageReference

    // UI elements
    private lateinit var profileImageView: ImageView
    private lateinit var changePhotoButton: Button
    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var changePasswordButton: Button
    private lateinit var saveChangesButton: Button
    private lateinit var backButton: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var notificationsSwitch: SwitchMaterial
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var biometricAuthSwitch: SwitchMaterial

    private var selectedImageUri: Uri? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        storageRef = storage.reference

        // Initialize UI elements
        initViews()

        // Setup image picker using Activity Result API
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                profileImageView.load(it) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile)
                    transformations(CircleCropTransformation())
                }
            }
        }

        setupListeners()
        loadUserData()
    }

    private fun initViews() {
        profileImageView = findViewById(R.id.profileImageView)
        changePhotoButton = findViewById(R.id.changePhotoButton)
        usernameEditText = findViewById(R.id.usernameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        changePasswordButton = findViewById(R.id.changePasswordButton)
        saveChangesButton = findViewById(R.id.saveChangesButton)
        backButton = findViewById(R.id.backButton)
        progressBar = findViewById(R.id.progressBar)
        notificationsSwitch = findViewById(R.id.notificationsSwitch)
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        biometricAuthSwitch = findViewById(R.id.biometricAuthSwitch)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        changePhotoButton.setOnClickListener { pickImageLauncher.launch("image/*") }
        saveChangesButton.setOnClickListener { saveUserChanges() }
        changePasswordButton.setOnClickListener { showChangePasswordDialog() }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE

        // Load basic user info
        usernameEditText.setText(user.displayName)
        emailEditText.setText(user.email)

        // Load profile image if exists
        user.photoUrl?.let { uri ->
            profileImageView.load(uri) {
                crossfade(true)
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
                transformations(CircleCropTransformation())
            }
        } ?: profileImageView.setImageResource(R.drawable.ic_profile)

        // Load additional settings from Firestore
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document.exists()) {
                    notificationsSwitch.isChecked = document.getBoolean("notificationsEnabled") ?: true
                    darkModeSwitch.isChecked = document.getBoolean("darkModeEnabled") ?: false
                    biometricAuthSwitch.isChecked = document.getBoolean("biometricAuthEnabled") ?: false
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading user data", e)
                Toast.makeText(this, "Failed to load user settings", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImage(callback: (Uri?) -> Unit) {
        val uri = selectedImageUri
        if (uri == null) {
            callback(null)
            return
        }

        progressBar.visibility = View.VISIBLE
        val imageRef = storageRef.child("profile_images/${UUID.randomUUID()}")

        imageRef.putFile(uri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    progressBar.visibility = View.GONE
                    callback(downloadUri)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Failed to upload image", e)
                Toast.makeText(this, "Failed to upload profile image", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    private fun saveUserChanges() {
        val user = auth.currentUser ?: return
        val newUsername = usernameEditText.text.toString().trim()
        val newEmail = emailEditText.text.toString().trim()

        if (!validateForm(newUsername, newEmail)) return

        progressBar.visibility = View.VISIBLE

        uploadImage { imageUri ->
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newUsername)
                .apply { imageUri?.let { setPhotoUri(it) } }
                .build()

            user.updateProfile(profileUpdates)
                .addOnCompleteListener { profileTask ->
                    if (profileTask.isSuccessful) {
                        if (newEmail != user.email) {
                            updateEmail(newEmail)
                        } else {
                            saveUserSettings()
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun updateEmail(newEmail: String) {
        val user = auth.currentUser ?: return
        user.updateEmail(newEmail)
            .addOnCompleteListener { emailTask ->
                if (emailTask.isSuccessful) {
                    Toast.makeText(this, "Email updated. Verification sent.", Toast.LENGTH_SHORT).show()
                    user.sendEmailVerification()
                    saveUserSettings()
                } else {
                    progressBar.visibility = View.GONE
                    showReauthenticationDialog(newEmail)
                }
            }
    }

    private fun saveUserSettings() {
        val user = auth.currentUser ?: return
        val settings = mapOf(
            "notificationsEnabled" to notificationsSwitch.isChecked,
            "darkModeEnabled" to darkModeSwitch.isChecked,
            "biometricAuthEnabled" to biometricAuthSwitch.isChecked,
            "lastUpdated" to System.currentTimeMillis()
        )

        db.collection("users").document(user.uid)
            .update(settings)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error saving settings", e)
                Toast.makeText(this, "Failed to save settings", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val current = view.findViewById<EditText>(R.id.currentPasswordEditText)
        val newPass = view.findViewById<EditText>(R.id.newPasswordEditText)
        val confirm = view.findViewById<EditText>(R.id.confirmPasswordEditText)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(view)
            .setPositiveButton("Change") { dialog, _ ->
                val curr = current.text.toString()
                val np = newPass.text.toString()
                val cp = confirm.text.toString()
                if (validatePasswordChange(curr, np, cp)) changePassword(curr, np)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReauthenticationDialog(newEmail: String? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_reauthenticate, null)
        val pwd = view.findViewById<EditText>(R.id.passwordEditText)

        AlertDialog.Builder(this)
            .setTitle("Re-authenticate")
            .setMessage("Enter current password to continue.")
            .setView(view)
            .setPositiveButton("Authenticate") { dialog, _ ->
                val pass = pwd.text.toString()
                if (pass.isNotEmpty()) reauthenticate(pass, newEmail)
                else Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reauthenticate(password: String, newEmail: String? = null) {
        val user = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE
        val credential = EmailAuthProvider.getCredential(user.email ?: "", password)
        user.reauthenticate(credential)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    newEmail?.let { updateEmail(it) } ?:
                    Toast.makeText(this, "Re-authentication successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Re-authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE
        val credential = EmailAuthProvider.getCredential(user.email ?: "", currentPassword)
        user.reauthenticate(credential)
            .addOnCompleteListener { reAuthTask ->
                if (reAuthTask.isSuccessful) {
                    user.updatePassword(newPassword)
                        .addOnCompleteListener { pwdTask ->
                            progressBar.visibility = View.GONE
                            if (pwdTask.isSuccessful) {
                                Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Error: ${pwdTask.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Current password incorrect", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun validateForm(username: String, email: String): Boolean {
        var valid = true
        if (TextUtils.isEmpty(username)) {
            usernameEditText.error = "Required"
            valid = false
        } else usernameEditText.error = null

        if (TextUtils.isEmpty(email)) {
            emailEditText.error = "Required"
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Invalid email"
            valid = false
        } else emailEditText.error = null

        return valid
    }

    private fun validatePasswordChange(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Boolean {
        if (currentPassword.isEmpty()) {
            Toast.makeText(this, "Current password required", Toast.LENGTH_SHORT).show()
            return false
        }
        if (newPassword.isEmpty() || newPassword.length < 6) {
            Toast.makeText(this, "Password must be ≥6 chars", Toast.LENGTH_SHORT).show()
            return false
        }
        if (newPassword != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}
