package com.example.amexbenefittracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.amexbenefittracker.data.remote.PlaidManager
import com.example.amexbenefittracker.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val plaidManager: PlaidManager
) : ViewModel() {

    val currentUser = authRepository.currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun signOut() {
        // Plaid's access token now lives only in the worker's KV store, but
        // clear whatever local UI cache remains (connected flag, mapping
        // cache) so the next account signed into this device doesn't
        // briefly see the previous user's Plaid state.
        plaidManager.clearLocalState()
        authRepository.signOut()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun signInWithEmail(email: String, pass: String) {
        if (email.isEmpty() || pass.isEmpty()) {
            _errorMessage.value = "Please fill in all fields"
            return
        }
        _isLoading.value = true
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid ?: return@addOnCompleteListener
                    // Check if user exists in Firestore
                    FirebaseFirestore.getInstance().collection("users").document(uid).get()
                        .addOnCompleteListener { dbTask ->
                            _isLoading.value = false
                            if (dbTask.isSuccessful) {
                                if (!dbTask.result!!.exists()) {
                                    // Auto-create missing user profile if auth is successful
                                    val userMap = hashMapOf(
                                        "email" to email,
                                        "createdAt" to System.currentTimeMillis()
                                    )
                                    FirebaseFirestore.getInstance().collection("users").document(uid)
                                        .set(userMap, SetOptions.merge())
                                }
                                // If exists or created, AuthRepository's StateFlow will update the UI
                            } else {
                                authRepository.signOut()
                                _errorMessage.value = "Database connection failed: ${dbTask.exception?.message}"
                            }
                        }
                } else {
                    _isLoading.value = false
                    _errorMessage.value = task.exception?.message ?: "Sign in failed"
                }
            }
    }

    fun signUpWithEmail(email: String, pass: String) {
        if (email.isEmpty() || pass.isEmpty()) {
            _errorMessage.value = "Please fill in all fields"
            return
        }
        _isLoading.value = true
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid ?: return@addOnCompleteListener
                    val userMap = hashMapOf(
                        "email" to email,
                        "createdAt" to System.currentTimeMillis()
                    )
                    
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .set(userMap, SetOptions.merge())
                        .addOnCompleteListener { dbTask ->
                            _isLoading.value = false
                            if (!dbTask.isSuccessful) {
                                _errorMessage.value = "Failed to create user profile: ${dbTask.exception?.message}"
                            }
                        }
                } else {
                    _isLoading.value = false
                    _errorMessage.value = task.exception?.message ?: "Sign up failed"
                }
            }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val plaidManager: PlaidManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(authRepository, plaidManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
