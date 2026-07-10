package com.example.amexbenefittracker.ui.dashboard

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, authViewModel: AuthViewModel) {
    val cards by viewModel.cards.collectAsState()
    val selectedCardId by viewModel.selectedCardId.collectAsState()
    val cardSummary by viewModel.cardSummary.collectAsState()
    val benefits by viewModel.benefits.collectAsState()
    val trackingYear by viewModel.trackingYear.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val focusManager = LocalFocusManager.current

    if (android.os.Build.VERSION.SDK_INT >= 33) {
        val permissionState = rememberPermissionState(
            permission = "android.permission.POST_NOTIFICATIONS"
        )
        LaunchedEffect(Unit) {
            if (!permissionState.status.isGranted) {
                permissionState.launchPermissionRequest()
            }
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.refreshData()
        }
    }

    var showResetDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val selectedCard = cards.find { it.id == selectedCardId }
    val isPlatinum = selectedCard?.name?.contains("Platinum") == true
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
                accentBgColor = accentBgColor,
                trackingYear = trackingYear,
                isLandscape = isLandscape,
                onCardSelected = { viewModel.selectCard(it) },
                onResetClick = { showResetDialog = true },
                onSignOutClick = { showSignOutDialog = true }
            )
        },
        containerColor = Slate950,
        modifier = Modifier
            .safeDrawingPadding()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) { padding ->
        val contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshData() },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            AnimatedContent(
                targetState = selectedCardId,
                transitionSpec = {
                    val initialIndex = cards.indexOfFirst { it.id == initialState }
                    val targetIndex = cards.indexOfFirst { it.id == targetState }
                    
                    if (targetIndex > initialIndex) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut())
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                label = "TabSwitch"
            ) { targetId ->
                val selectedCard = cards.find { it.id == targetId }
                val isPlatinum = selectedCard?.name?.contains("Platinum") == true
                val accentTextColor = if (isPlatinum) Blue400 else Amber400
                val accentBgColor = if (isPlatinum) Blue600 else Amber600

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                                        trackingYear = trackingYear,
                                        accentBgColor = accentBgColor,
                                        accentTextColor = accentTextColor,
                                        onToggle = { period -> viewModel.toggleBenefit(benefitUi.benefit, period) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
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
                                trackingYear = trackingYear,
                                accentBgColor = accentBgColor,
                                accentTextColor = accentTextColor,
                                onToggle = { period -> viewModel.toggleBenefit(benefitUi.benefit, period) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardTopBar(
    cards: List<Card>,
    selectedCardId: Long?,
    accentBgColor: Color,
    trackingYear: String,
    isLandscape: Boolean,
    onCardSelected: (Long) -> Unit,
    onResetClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    EditableYearSubheader(trackingYear)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onResetClick) {
                    Icon(Icons.Default.History, contentDescription = "Reset Tracking", tint = Slate500)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSignOutClick) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = Slate500)
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        EditableYearSubheader(trackingYear)
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onResetClick) {
                        Icon(Icons.Default.History, contentDescription = "Reset Tracking", tint = Slate500)
                    }
                    IconButton(onClick = onSignOutClick) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = Slate500)
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
        title = { Text("Sign Out", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to sign out?", color = Slate400) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Red400)) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate500)
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
        title = { Text("Reset Progress", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to reset your tracking progress?", color = Slate400) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Red400)) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate500)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.cardName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = accentTextColor)
            }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("EFFECTIVE ANNUAL FEE", style = MaterialTheme.typography.labelSmall, color = Slate500, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = accentTextColor)
            }
            Spacer(Modifier.height(16.dp))
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
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, color: Color = TextWhite) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Slate500)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DetailRowWithToggle(label: String, value: String, isClaimed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(16.dp).border(1.dp, if (isClaimed) Emerald400 else Slate700, CircleShape), contentAlignment = Alignment.Center) {
                if (isClaimed) Icon(Icons.Default.Check, contentDescription = null, tint = Emerald400, modifier = Modifier.size(10.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (isClaimed) TextWhite else Slate600)
        }
        Text(value, color = if (isClaimed) Emerald400 else Slate600, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BenefitCard(uiModel: BenefitUiModel, trackingYear: String, accentBgColor: Color, accentTextColor: Color, onToggle: (String) -> Unit) {
    val benefit = uiModel.benefit
    Surface(
        color = Slate900.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Slate800, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(benefit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text(benefit.description, style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$${uiModel.totalClaimedInPeriod.toInt()} / $${benefit.totalValue.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentTextColor
                    )
                    LinearProgressIndicator(
                        progress = { uiModel.progress },
                        modifier = Modifier.width(80.dp).height(4.dp).padding(top = 4.dp),
                        color = accentBgColor,
                        trackColor = Slate800
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            when (benefit.type) {
                BenefitType.MONTHLY -> {
                    val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            months.take(6).forEachIndexed { index, name ->
                                val periodId = "$trackingYear-${(index + 1).toString().padStart(2, '0')}"
                                MonthChip(name, uiModel.history.any { it.periodIdentifier == periodId }, accentBgColor, Modifier.weight(1f)) { onToggle(periodId) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            months.drop(6).forEachIndexed { index, name ->
                                val periodId = "$trackingYear-${(index + 7).toString().padStart(2, '0')}"
                                MonthChip(name, uiModel.history.any { it.periodIdentifier == periodId }, accentBgColor, Modifier.weight(1f)) { onToggle(periodId) }
                            }
                        }
                    }
                }
                BenefitType.QUARTERLY -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Q1", "Q2", "Q3", "Q4").forEachIndexed { index, name ->
                            val periodId = "$trackingYear-Q${index + 1}"
                            QuarterChip(name, uiModel.history.any { it.periodIdentifier == periodId }, accentBgColor, Modifier.weight(1f)) { onToggle(periodId) }
                        }
                    }
                }
                BenefitType.SEMI_ANNUAL -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HalfChip("H1", uiModel.history.any { it.periodIdentifier == "$trackingYear-H1" }, accentBgColor, Modifier.weight(1f)) { onToggle("$trackingYear-H1") }
                        HalfChip("H2", uiModel.history.any { it.periodIdentifier == "$trackingYear-H2" }, accentBgColor, Modifier.weight(1f)) { onToggle("$trackingYear-H2") }
                    }
                }
                BenefitType.ANNUAL -> {
                    val periodId = "$trackingYear-Annual"
                    Surface(
                        onClick = { onToggle(periodId) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        color = if (uiModel.isClaimedInCurrentPeriod) accentBgColor else Slate900.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = if (uiModel.isClaimedInCurrentPeriod) null else BorderStroke(1.dp, Slate800)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("ANNUAL CREDIT", fontWeight = FontWeight.Bold, color = if (uiModel.isClaimedInCurrentPeriod) Color.White else Slate500)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthChip(label: String, isClaimed: Boolean, accentBgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (isClaimed) accentBgColor else Slate900.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(40.dp).clickable { onClick() },
        border = if (isClaimed) null else BorderStroke(1.dp, Slate800)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isClaimed) Color.White else Slate500)
        }
    }
}

@Composable
fun QuarterChip(label: String, isClaimed: Boolean, accentBgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (isClaimed) accentBgColor else Slate900.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(40.dp).clickable { onClick() },
        border = if (isClaimed) null else BorderStroke(1.dp, Slate800)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, color = if (isClaimed) Color.White else Slate500)
        }
    }
}

@Composable
fun HalfChip(label: String, isClaimed: Boolean, accentBgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (isClaimed) accentBgColor else Slate900.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(40.dp).clickable { onClick() },
        border = if (isClaimed) null else BorderStroke(1.dp, Slate800)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, color = if (isClaimed) Color.White else Slate500)
        }
    }
}

@Composable
fun EditableYearSubheader(trackingYear: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tracking ", style = MaterialTheme.typography.bodySmall, color = Slate500)
        Text(
            text = trackingYear,
            style = MaterialTheme.typography.bodySmall,
            color = Slate500,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(" Refreshed Benefits", style = MaterialTheme.typography.bodySmall, color = Slate500)
    }
}
