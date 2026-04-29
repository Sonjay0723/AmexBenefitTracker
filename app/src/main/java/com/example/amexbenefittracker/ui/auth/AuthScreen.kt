package com.example.amexbenefittracker.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amexbenefittracker.R
import com.example.amexbenefittracker.ui.theme.*

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Slate900.copy(alpha = 0.4f),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .border(1.dp, Slate800, RoundedCornerShape(32.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.amex_logo),
                    contentDescription = "Amex Logo",
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Amex Benefit Tracker",
                    color = TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = if (isSignUp) "Create an account to start tracking" else "Sign in to access your data",
                    color = Slate400,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Email Field
                AuthTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email address",
                    icon = Icons.Default.Email
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password Field
                AuthTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onVisibilityToggle = { passwordVisible = !passwordVisible }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Sign In Button
                Button(
                    onClick = { viewModel.signInWithEmail(email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isSignUp) "Sign Up" else "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // OR Separator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Divider(modifier = Modifier.weight(1f), color = Slate800)
                    Text(
                        text = "or",
                        color = Slate500,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontSize = 12.sp
                    )
                    Divider(modifier = Modifier.weight(1f), color = Slate800)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Google Button
                OutlinedButton(
                    onClick = { /* Google Sign In */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Slate700),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Assuming you have a google icon, for now placeholder
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_help), // Placeholder
                            contentDescription = "Google",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Continue with Google", fontWeight = FontWeight.Medium)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Toggle Sign In / Sign Up
                Row {
                    Text(
                        text = if (isSignUp) "Already have an account? " else "Don't have an account? ",
                        color = Slate400,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isSignUp) "Sign In" else "Sign Up",
                        color = Blue400,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { isSignUp = !isSignUp }
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = errorMessage!!, color = Red400, fontSize = 12.sp)
                    LaunchedEffect(errorMessage) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearError()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onVisibilityToggle: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label, color = Slate500) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = Slate500) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = onVisibilityToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Slate500
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Blue400,
            unfocusedBorderColor = Slate800,
            cursorColor = Blue400,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            focusedContainerColor = Slate900.copy(alpha = 0.4f),
            unfocusedContainerColor = Slate900.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}
