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
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import com.plaid.link.OpenPlaidLink
import com.plaid.link.linkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkSuccess
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import java.text.SimpleDateFormat
import com.example.amexbenefittracker.R
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.data.local.entities.Transaction
import com.example.amexbenefittracker.data.remote.PlaidAccount
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
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showPlaidSettingsDialog by remember { mutableStateOf(false) }
    val transactions by viewModel.transactions.collectAsState()

    val plaidLauncher = rememberLauncherForActivityResult(
        contract = OpenPlaidLink(),
        onResult = { result ->
            if (result is LinkSuccess) {
                viewModel.handlePlaidSuccess(result.publicToken)
                showPlaidSettingsDialog = true
            }
        }
    )


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

    if (showPlaidSettingsDialog) {
        PlaidSettingsDialog(
            viewModel = viewModel,
            plaidLauncher = plaidLauncher,
            onDismiss = { showPlaidSettingsDialog = false }
        )
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                DashboardTopBar(
                    cards = cards,
                    selectedCardId = selectedCardId,
                    accentBgColor = accentBgColor,
                    trackingYear = trackingYear,
                    isLandscape = isLandscape,
                    onCardSelected = { viewModel.selectCard(it) },
                    onSettingsClick = { showSettingsPanel = true }
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
                                    RecentTransactionsSection(
                                        transactions = transactions,
                                        isRefreshing = isRefreshing,
                                        onSyncClick = { viewModel.syncPlaidTransactions() },
                                        accentTextColor = accentTextColor
                                    )
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
                                        RecentTransactionsSection(
                                            transactions = transactions,
                                            isRefreshing = isRefreshing,
                                            onSyncClick = { viewModel.syncPlaidTransactions() },
                                            accentTextColor = accentTextColor
                                        )
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

        // Settings Overlay (Darkened Background)
        AnimatedVisibility(
            visible = showSettingsPanel,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showSettingsPanel = false })
                    }
            )
        }

        // Settings Drawer Panel
        AnimatedVisibility(
            visible = showSettingsPanel,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .clickable(enabled = false) { }
                    .border(
                        BorderStroke(1.dp, Slate800),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    ),
                color = Slate950,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .safeDrawingPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showSettingsPanel = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Settings",
                                tint = Slate500
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Slate800, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(24.dp))

                    // Plaid Sync Settings Option Card
                    Surface(
                        onClick = {
                            if (viewModel.plaidManager.hasAccessToken()) {
                                showPlaidSettingsDialog = true
                            } else {
                                viewModel.getLinkToken { linkToken ->
                                    val config = linkTokenConfiguration {
                                        token = linkToken
                                    }
                                    plaidLauncher.launch(config)
                                }
                            }
                            showSettingsPanel = false
                        },
                        color = Slate900,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Bank Connection",
                                tint = Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Bank Connection",
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Link Amex Accounts",
                                    color = Slate400,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset Progress Option Card
                    Surface(
                        onClick = {

                            showResetDialog = true
                            showSettingsPanel = false
                        },
                        color = Slate900,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Reset Tracking",
                                tint = Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Reset Progress",
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Reset all tracking progress",
                                    color = Slate400,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign Out Option Card
                    Surface(
                        onClick = {
                            showSignOutDialog = true
                            showSettingsPanel = false
                        },
                        color = Slate900,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Log Out",
                                tint = Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Log Out",
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Sign out of your account",
                                    color = Slate400,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
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
    onSettingsClick: () -> Unit
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
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Slate500)
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
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Slate500)
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
                DetailRow("Standard Annual Fee", formatAmount(summary.standardAnnualFee))
                if (summary.corporateCredit > 0) {
                    DetailRowWithToggle("Corporate Credit", "-${formatAmount(summary.corporateCredit)}", summary.corporateCreditClaimed, onToggleCorporateCredit)
                }
                DetailRow("Total Benefits Claimed", "-${formatAmount(summary.totalBenefitsClaimed)}", color = accentTextColor)
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
                    text = if (isProfit) formatAmount(summary.profit) else formatAmount(summary.effectiveAnnualFee),
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
                        text = "${formatAmount(uiModel.totalClaimedInPeriod)} / ${formatAmount(benefit.totalValue)}",
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

@Composable
fun RecentTransactionsSection(
    transactions: List<Transaction>,
    isRefreshing: Boolean,
    onSyncClick: () -> Unit,
    accentTextColor: Color
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    
    Surface(
        color = Slate900.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                
                IconButton(
                    onClick = onSyncClick,
                    enabled = !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Transactions",
                        tint = accentTextColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (transactions.isEmpty()) {
                Text(
                    text = "No recent transactions linked yet. Open Settings and connect your Amex card via Plaid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    transactions.take(5).forEachIndexed { index, tx ->
                        if (index > 0) {
                            HorizontalDivider(color = Slate800, thickness = 1.dp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description,
                                    color = TextWhite,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormat.format(Date(tx.date)),
                                        color = Slate500,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (tx.matchedBenefitName != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = Emerald400.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "✓ ${tx.matchedBenefitName}",
                                                color = Emerald400,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            
                            val isCredit = tx.amount < 0
                            val amtText = if (isCredit) {
                                "-$${String.format("%.2f", -tx.amount)}"
                            } else {
                                "$${String.format("%.2f", tx.amount)}"
                            }
                            Text(
                                text = amtText,
                                color = if (isCredit) Emerald400 else TextWhite,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaidSettingsDialog(
    viewModel: DashboardViewModel,
    plaidLauncher: androidx.activity.result.ActivityResultLauncher<com.plaid.link.configuration.LinkTokenConfiguration>,
    onDismiss: () -> Unit
) {
    val plaidManager = viewModel.plaidManager
    val plaidAccounts by viewModel.plaidAccounts.collectAsState()
    val plaidError by viewModel.plaidError.collectAsState()
    val cards by viewModel.cards.collectAsState()
    
    LaunchedEffect(Unit) {
        if (plaidManager.hasAccessToken()) {
            viewModel.fetchPlaidAccounts(plaidManager.getAccessToken()!!)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Text(
                text = "Link Amex Accounts",
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (plaidError != null) {
                    Surface(
                        color = Red400.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Red400.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = plaidError!!, color = Red400, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { viewModel.clearPlaidError() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Red400)
                            ) {
                                Text("Dismiss", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Surface(
                    color = Slate950.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Slate800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Status: Connected",
                            color = Emerald400,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "To associate transactions with tracking dashboards, select which Plaid account maps to each card below.",
                            color = Slate500,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (plaidAccounts.isEmpty()) {
                            Text(
                                text = "Fetching accounts from Plaid...",
                                color = Slate500,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            cards.forEach { card ->
                                Column {
                                    Text(
                                        text = card.name,
                                        color = TextWhite,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    val mappedId = plaidManager.getCardMapping(card.id)
                                    val selectedAccount = plaidAccounts.find { it.accountId == mappedId }
                                    val selectorText = selectedAccount?.let {
                                        "${it.name} (ending in ${it.mask ?: "xxxx"})"
                                    } ?: "Select Plaid Account"
                                    
                                    var menuExpanded by remember { mutableStateOf(false) }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                                            .clickable { menuExpanded = true }
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = selectorText,
                                            color = if (selectedAccount != null) TextWhite else Slate500,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                            modifier = Modifier.background(Slate900).border(1.dp, Slate800)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("None", color = Slate500) },
                                                onClick = {
                                                    viewModel.mapCardToPlaidAccount(card.id, "")
                                                    menuExpanded = false
                                                }
                                            )
                                            plaidAccounts.forEach { acc ->
                                                val accLabel = "${acc.name} (ending in ${acc.mask ?: "xxxx"})"
                                                DropdownMenuItem(
                                                    text = { Text(accLabel, color = TextWhite) },
                                                    onClick = {
                                                        viewModel.mapCardToPlaidAccount(card.id, acc.accountId)
                                                        menuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = {
                                plaidManager.clearAll()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Red400),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Account", color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Blue400, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.border(1.dp, Slate800, RoundedCornerShape(24.dp))
    )
}

private fun formatAmount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        "$${value.toInt()}"
    } else {
        String.format(java.util.Locale.US, "$%.2f", value)
    }
}


