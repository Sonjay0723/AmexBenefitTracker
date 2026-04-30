package com.example.amexbenefittracker.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amexbenefittracker.R
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.domain.model.CardSummary
import com.example.amexbenefittracker.ui.auth.AuthViewModel
import com.example.amexbenefittracker.ui.theme.*
import java.util.*

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, authViewModel: AuthViewModel) {
    val cards by viewModel.cards.collectAsState()
    val selectedCardId by viewModel.selectedCardId.collectAsState()
    val cardSummary by viewModel.cardSummary.collectAsState()
    val benefits by viewModel.benefits.collectAsState()
    val trackingYear by viewModel.trackingYear.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val selectedCard = cards.find { it.id == selectedCardId }
    val isPlatinum = selectedCard?.name?.contains("Platinum") == true
    val accentTextColor = if (isPlatinum) Blue400 else Amber400
    val accentBgColor = if (isPlatinum) Blue600 else Amber600

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (showResetDialog) {
        ResetConfirmationDialog(
            onConfirm = {
                viewModel.resetAllTracking()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showSignOutDialog) {
        SignOutConfirmationDialog(
            onConfirm = {
                authViewModel.signOut()
                showSignOutDialog = false
            },
            onDismiss = { showSignOutDialog = false }
        )
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                cards = cards,
                selectedCardId = selectedCardId,
                accentTextColor = accentTextColor,
                accentBgColor = accentBgColor,
                trackingYear = trackingYear,
                isLandscape = isLandscape,
                onCardSelected = { viewModel.selectCard(it) },
                onRefreshClick = { showResetDialog = true },
                onSignOutClick = { showSignOutDialog = true }
            )
        },
        containerColor = Slate950,
        modifier = Modifier.safeDrawingPadding()
    ) { padding ->
        val contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp, // End padding matches start padding
            bottom = 16.dp
        )
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column - Now Scrollable
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    cardSummary?.let { summary ->
                        CardDetailsSection(summary, accentTextColor) {
                            viewModel.toggleCorporateCredit()
                        }
                        EffectiveFeeSection(summary, accentTextColor)
                    }
                }

                // Right Column
                Column(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(benefits) { benefitUi ->
                            BenefitCard(
                                uiModel = benefitUi,
                                accentBgColor = accentBgColor,
                                accentTextColor = accentTextColor,
                                onToggle = { period -> viewModel.toggleBenefit(benefitUi.benefit, period) }
                            )
                        }
                    }
                }
            }
        } else {
            // Vertical Layout for Mobile
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    cardSummary?.let { summary ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CardDetailsSection(summary, accentTextColor) {
                                viewModel.toggleCorporateCredit()
                            }
                            EffectiveFeeSection(summary, accentTextColor)
                        }
                    }
                }
                items(benefits) { benefitUi ->
                    BenefitCard(
                        uiModel = benefitUi,
                        accentBgColor = accentBgColor,
                        accentTextColor = accentTextColor,
                        onToggle = { period -> viewModel.toggleBenefit(benefitUi.benefit, period) }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardTopBar(
    cards: List<Card>,
    selectedCardId: Long?,
    accentTextColor: Color,
    accentBgColor: Color,
    trackingYear: String,
    isLandscape: Boolean,
    onCardSelected: (Long) -> Unit,
    onRefreshClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    if (isLandscape) {
        // Landscape TopBar layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // App Logo
                Surface(
                    modifier = Modifier.size(56.dp),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.amex_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Amex Benefit Tracker",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Tracking $trackingYear Refreshed Benefits",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefreshClick) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Slate500)
                }
                
                Spacer(Modifier.width(8.dp))

                IconButton(onClick = onSignOutClick) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Slate500)
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    modifier = Modifier
                        .background(Slate900.copy(alpha = 0.6f), CircleShape)
                        .border(1.dp, Slate800, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    cards.sortedByDescending { it.name.contains("Platinum") }.forEach { card ->
                        val isSelected = card.id == selectedCardId
                        Surface(
                            modifier = Modifier.clickable { onCardSelected(card.id) },
                            color = if (isSelected) accentBgColor else Color.Transparent,
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (card.name.contains("Platinum")) "Platinum" else "Gold",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = TextWhite,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Vertical (Portrait) TopBar layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.amex_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Amex Benefit Tracker",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tracking $trackingYear Refreshed Benefits",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefreshClick) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Slate500)
                    }
                    IconButton(onClick = onSignOutClick) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Slate500)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                cards.sortedByDescending { it.name.contains("Platinum") }.forEach { card ->
                    val isSelected = card.id == selectedCardId
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCardSelected(card.id) },
                        color = if (isSelected) accentBgColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (card.name.contains("Platinum")) "Platinum" else "Gold",
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = TextWhite,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SignOutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Text(
                "Amex Benefit Tracker",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                "Are you sure you want to sign out?",
                color = Slate400,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Red400),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel", color = Slate500, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, Slate800, RoundedCornerShape(16.dp))
    )
}

@Composable
fun ResetConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Text(
                "Amex Benefit Tracker",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                "Are you sure you want to reset your tracking progress?",
                color = Slate400,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Red400),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel", color = Slate500, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, Slate800, RoundedCornerShape(16.dp))
    )
}

@Composable
fun CardDetailsSection(summary: CardSummary, accentTextColor: Color, onToggleCorporateCredit: () -> Unit) {
    Surface(
        color = Slate900.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Slate800, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                 Icon(Icons.Default.CreditCard, contentDescription = null, tint = accentTextColor, modifier = Modifier.size(24.dp))
            }
            
            Text(summary.cardName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Standard Annual Fee", "$${summary.standardAnnualFee.toInt()}")
                if (summary.corporateCredit > 0) {
                    DetailRowWithToggle("Corporate Credit", "-$${summary.corporateCredit.toInt()}", summary.corporateCreditClaimed, onToggleCorporateCredit)
                }
                DetailRow("Total Benefits Claimed", "-$${summary.totalBenefitsClaimed.toInt()}", color = accentTextColor)
            }
        }
    }
}

@Composable
fun EffectiveFeeSection(summary: CardSummary, accentTextColor: Color) {
    val isProfit = summary.profit > 0
    Surface(
        color = if (isProfit) Emerald400.copy(alpha = 0.05f) else Slate900.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (isProfit) Emerald400.copy(alpha = 0.2f) else Slate800, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(Slate900.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = accentTextColor, modifier = Modifier.size(20.dp))
                }
                Text("EFFECTIVE ANNUAL FEE", style = MaterialTheme.typography.labelSmall, color = Slate500, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (isProfit) "$${summary.profit.toInt()}" else "$${summary.effectiveAnnualFee.toInt()}",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (isProfit) Emerald400 else TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isProfit) "Profit" else "Fee",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isProfit) Emerald400 else Slate500,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (isProfit) {
                Text(
                    "Excellent management. You've officially 'beaten' the annual fee for 2026.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    "Extract $${summary.effectiveAnnualFee.toInt()} more in value to reach break-even status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, color: Color = TextWhite) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Slate500, fontWeight = FontWeight.Medium)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DetailRowWithToggle(label: String, value: String, isClaimed: Boolean, onToggle: () -> Unit) {
    val labelColor = if (isClaimed) TextWhite else Slate600
    val valueColor = if (isClaimed) Emerald400 else Slate600
    val circleColor = if (isClaimed) Emerald400 else Slate700

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(1.dp, circleColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isClaimed) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(label, color = labelColor, fontWeight = FontWeight.Medium)
        }
        Text(value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BenefitCard(uiModel: BenefitUiModel, accentBgColor: Color, accentTextColor: Color, onToggle: (String) -> Unit) {
    val benefit = uiModel.benefit
    Surface(
        color = Slate900.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Slate800, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(benefit.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text(benefit.description, style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$${uiModel.totalClaimedInPeriod.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentTextColor
                        )
                        Text(
                            " / $${benefit.totalValue.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                    }
                    LinearProgressIndicator(
                        progress = { uiModel.progress },
                        modifier = Modifier.width(100.dp).height(8.dp).padding(top = 8.dp),
                        color = accentBgColor,
                        trackColor = Slate900.copy(alpha = 0.6f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            val year = Calendar.getInstance().get(Calendar.YEAR)
            
            when (benefit.type) {
                BenefitType.MONTHLY -> {
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

                    if (isLandscape) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            months.forEachIndexed { index, name ->
                                val periodId = "$year-${(index + 1).toString().padStart(2, '0')}"
                                val isClaimed = uiModel.history.any { it.periodIdentifier == periodId }
                                MonthChip(name, isClaimed, accentBgColor, modifier = Modifier.weight(1f)) { onToggle(periodId) }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val firstRow = months.take(6)
                            val secondRow = months.drop(6)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                firstRow.forEachIndexed { index, name ->
                                    val periodId = "$year-${(index + 1).toString().padStart(2, '0')}"
                                    val isClaimed = uiModel.history.any { it.periodIdentifier == periodId }
                                    MonthChip(name, isClaimed, accentBgColor, modifier = Modifier.weight(1f)) { onToggle(periodId) }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                secondRow.forEachIndexed { index, name ->
                                    val periodId = "$year-${(index + 7).toString().padStart(2, '0')}"
                                    val isClaimed = uiModel.history.any { it.periodIdentifier == periodId }
                                    MonthChip(name, isClaimed, accentBgColor, modifier = Modifier.weight(1f)) { onToggle(periodId) }
                                }
                            }
                        }
                    }
                }
                BenefitType.QUARTERLY -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val quarters = listOf("Q1", "Q2", "Q3", "Q4")
                        quarters.forEachIndexed { index, name ->
                            val periodId = "$year-Q${index + 1}"
                            val isClaimed = uiModel.history.any { it.periodIdentifier == periodId }
                            QuarterChip(name, isClaimed, accentBgColor, modifier = Modifier.weight(1f)) { onToggle(periodId) }
                        }
                    }
                }
                BenefitType.SEMI_ANNUAL -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val p1 = "$year-H1"
                        val p2 = "$year-H2"
                        HalfChip("HALF 1", uiModel.history.any { it.periodIdentifier == p1 }, accentBgColor, modifier = Modifier.weight(1f)) { onToggle(p1) }
                        HalfChip("HALF 2", uiModel.history.any { it.periodIdentifier == p2 }, accentBgColor, modifier = Modifier.weight(1f)) { onToggle(p2) }
                    }
                }
                BenefitType.ANNUAL -> {
                    val periodId = "$year-Annual"
                    val isClaimed = uiModel.isClaimedInCurrentPeriod
                    Surface(
                        onClick = { onToggle(periodId) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        color = if (isClaimed) accentBgColor else Slate900.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isClaimed) null else BorderStroke(1.dp, Slate800)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ANNUAL CREDIT", fontWeight = FontWeight.Bold, color = if (isClaimed) Color.White else Slate500)
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(if (isClaimed) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                    .border(1.dp, if (isClaimed) Color.White else Slate700, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isClaimed) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthChip(month: String, isClaimed: Boolean, accentBgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (isClaimed) accentBgColor else Slate900.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(72.dp).clickable { onClick() },
        border = if (isClaimed) null else BorderStroke(1.dp, Slate800)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(month, fontSize = 10.sp, color = if (isClaimed) TextWhite else Slate500, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(if (isClaimed) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                    .border(1.dp, if (isClaimed) Color.White else Slate700, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isClaimed) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun QuarterChip(label: String, isClaimed: Boolean, accentBgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (isClaimed) accentBgColor else Slate900.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(56.dp).clickable { onClick() },
        border = if (isClaimed) null else BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = if (isClaimed) TextWhite else Slate500, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (isClaimed) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                    .border(1.dp, if (isClaimed) Color.White else Slate700, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isClaimed) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun HalfChip(label: String, isClaimed: Boolean, accentBgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (isClaimed) accentBgColor else Slate900.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(56.dp).clickable { onClick() },
        border = if (isClaimed) null else BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = if (isClaimed) TextWhite else Slate500, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (isClaimed) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                    .border(1.dp, if (isClaimed) Color.White else Slate700, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isClaimed) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
