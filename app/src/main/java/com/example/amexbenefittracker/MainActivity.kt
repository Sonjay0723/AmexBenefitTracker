package com.example.amexbenefittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.amexbenefittracker.ui.auth.AuthScreen
import com.example.amexbenefittracker.ui.auth.AuthViewModel
import com.example.amexbenefittracker.ui.dashboard.DashboardScreen
import com.example.amexbenefittracker.ui.dashboard.DashboardViewModel
import com.example.amexbenefittracker.ui.theme.AmexBenefitTrackerTheme
import com.example.amexbenefittracker.ui.theme.Slate950

class MainActivity : ComponentActivity() {
    
    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModel.Factory((application as AmexApplication).repository)
    }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory((application as AmexApplication).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmexBenefitTrackerTheme {
                val currentUser by authViewModel.currentUser.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate950
                ) {
                    if (currentUser == null) {
                        AuthScreen(authViewModel)
                    } else {
                        DashboardScreen(dashboardViewModel, authViewModel)
                    }
                }
            }
        }
    }
}
