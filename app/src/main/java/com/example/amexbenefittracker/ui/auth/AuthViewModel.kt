package com.example.amexbenefittracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.amexbenefittracker.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val currentUser = authRepository.currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun signOut() {
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
                                    FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap)
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
                    
                    FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap)
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

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
