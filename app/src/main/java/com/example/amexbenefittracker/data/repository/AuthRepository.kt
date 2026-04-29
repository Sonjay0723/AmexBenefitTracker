package com.example.amexbenefittracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    init {
        firebaseAuth.addAuthStateListener {
            _currentUser.value = it.currentUser
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    val isLoggedIn: Boolean
        get() = firebaseAuth.currentUser != null
}
