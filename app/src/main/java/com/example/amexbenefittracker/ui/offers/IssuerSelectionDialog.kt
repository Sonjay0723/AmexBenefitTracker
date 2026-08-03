package com.example.amexbenefittracker.ui.offers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amexbenefittracker.ui.theme.Slate800
import com.example.amexbenefittracker.ui.theme.Slate900

@Composable
fun IssuerSelectionDialog(
    onDismissRequest: () -> Unit,
    onIssuerSelected: (CardIssuer) -> Unit
) {
    var selectedIssuer by remember { mutableStateOf(CardIssuer.AMEX) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = {
                    onIssuerSelected(selectedIssuer)
                },
                enabled = selectedIssuer.enabled
            ) {
                Text("Launch Activator")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = Color.Gray)
            }
        },
        title = {
            Column {
                Text(
                    text = "Auto-Activate Offers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Select your credit card issuer to launch the offer auto-activator engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CardIssuer.entries.forEach { issuer ->
                    val isSelected = (selectedIssuer == issuer)
                    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Slate800
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(backgroundColor)
                            .clickable(enabled = issuer.enabled) {
                                selectedIssuer = issuer
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = if (issuer.enabled) { { selectedIssuer = issuer } } else null,
                                enabled = issuer.enabled
                            )
                            Column {
                                Text(
                                    text = issuer.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (issuer.enabled) contentColor else Color.Gray
                                )
                                if (!issuer.enabled) {
                                    Text(
                                        text = "Coming Soon",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        if (issuer == CardIssuer.AMEX) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "DEFAULT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Slate900,
        titleContentColor = Color.White
    )
}

