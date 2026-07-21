package com.example.amexbenefittracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.local.entities.Transaction
import com.example.amexbenefittracker.data.remote.PlaidManager
import com.example.amexbenefittracker.data.remote.PlaidAccount
import com.example.amexbenefittracker.data.repository.BenefitRepository
import com.example.amexbenefittracker.domain.model.CardSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class DashboardViewModel(
    private val repository: BenefitRepository,
    val plaidManager: PlaidManager
) : ViewModel() {

    private val _selectedCardId = MutableStateFlow<Long?>(null)
    val selectedCardId = _selectedCardId.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _plaidAccounts = MutableStateFlow<List<PlaidAccount>>(emptyList())
    val plaidAccounts = _plaidAccounts.asStateFlow()

    private val _plaidError = MutableStateFlow<String?>(null)
    val plaidError = _plaidError.asStateFlow()

    val trackingYear = repository.trackingYear

    val cards = repository.getAllCards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val cardSummary: StateFlow<CardSummary?> = selectedCardId
        .flatMapLatest { id ->
            if (id != null) repository.getCardSummary(id) else flowOf(null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val benefits: StateFlow<List<BenefitUiModel>> = combine(selectedCardId, trackingYear) { id, year ->
        id to year
    }.flatMapLatest { (id, year) ->
        if (id == null) return@flatMapLatest flowOf(emptyList())
        
        val benefitsFlow = repository.getBenefitsForCard(id)
        val usageFlow = repository.getUsageForCard(id)
        
        combine(benefitsFlow, usageFlow) { benefitsList, usages ->
            benefitsList.map { benefit ->
                val period = getCurrentPeriod(benefit)
                val relevantUsages = usages.filter { it.benefitId == benefit.id }
                val currentPeriodUsage = relevantUsages.find { it.periodIdentifier == period }
                
                val totalClaimed = relevantUsages.filter { it.periodIdentifier.startsWith(year) }.sumOf { it.amountClaimed }
                
                BenefitUiModel(
                    benefit = benefit,
                    totalClaimedInPeriod = totalClaimed,
                    isClaimedInCurrentPeriod = currentPeriodUsage != null,
                    progress = (totalClaimed / benefit.totalValue).coerceIn(0.0, 1.0).toFloat(),
                    history = relevantUsages
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = selectedCardId
        .flatMapLatest { id ->
            if (id != null) repository.getTransactionsForCard(id) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            cards.collect { list ->
                if (_selectedCardId.value == null && list.isNotEmpty()) {
                    _selectedCardId.value = list.find { it.isDefault }?.id ?: list.first().id
                }
            }
        }
    }

    fun selectCard(cardId: Long) {
        _selectedCardId.value = cardId
    }

    fun toggleCorporateCredit() {
        val cardId = _selectedCardId.value
        if (cardId != null) {
            viewModelScope.launch {
                repository.toggleCorporateCredit(cardId)
            }
        }
    }

    fun toggleBenefit(benefit: Benefit, periodIdentifier: String? = null) {
        viewModelScope.launch {
            val period = periodIdentifier ?: getCurrentPeriod(benefit)
            repository.toggleUsage(benefit, period, System.currentTimeMillis())
        }
    }

    fun resetAllTracking() {
        viewModelScope.launch {
            repository.resetAllTracking()
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshData()
            syncPlaidTransactions()
            _isRefreshing.value = false
        }
    }

    fun getCurrentPeriod(benefit: Benefit): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        val year = trackingYear.value
        return when (benefit.type) {
            BenefitType.MONTHLY -> {
                val month = calendar.get(Calendar.MONTH) + 1
                "$year-${month.toString().padStart(2, '0')}"
            }
            BenefitType.QUARTERLY -> {
                val quarter = (calendar.get(Calendar.MONTH) / 3) + 1
                "$year-Q$quarter"
            }
            BenefitType.SEMI_ANNUAL -> {
                val half = if (calendar.get(Calendar.MONTH) < 6) "H1" else "H2"
                "$year-$half"
            }
            BenefitType.ANNUAL -> {
                "$year-Annual"
            }
        }
    }

    // Plaid Integration Methods
    fun saveCloudFunctionUrl(url: String) {
        plaidManager.saveCloudFunctionUrl(url)
    }


    fun getLinkToken(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _plaidError.value = null
            try {
                val clientUserId = UUID.randomUUID().toString()
                val token = plaidManager.createLinkToken(clientUserId)
                onSuccess(token)
            } catch (e: Exception) {
                e.printStackTrace()
                _plaidError.value = "Failed to create Link token: ${e.localizedMessage}"
            }
        }
    }

    fun handlePlaidSuccess(publicToken: String) {
        viewModelScope.launch {
            _plaidError.value = null
            try {
                val accessToken = plaidManager.exchangePublicToken(publicToken)
                fetchPlaidAccounts(accessToken)
            } catch (e: Exception) {
                e.printStackTrace()
                _plaidError.value = "Token exchange failed: ${e.localizedMessage}"
            }
        }
    }

    fun fetchPlaidAccounts(accessToken: String) {
        viewModelScope.launch {
            try {
                val accounts = plaidManager.getAccounts(accessToken)
                _plaidAccounts.value = accounts
            } catch (e: Exception) {
                e.printStackTrace()
                _plaidError.value = "Failed to fetch accounts: ${e.localizedMessage}"
            }
        }
    }

    fun mapCardToPlaidAccount(cardId: Long, plaidAccountId: String) {
        plaidManager.saveCardMapping(cardId, plaidAccountId)
        // Clear mapping from other cards if they had it
        cards.value.forEach { card ->
            if (card.id != cardId && plaidManager.getCardMapping(card.id) == plaidAccountId) {
                plaidManager.saveCardMapping(card.id, "")
            }
        }
        // Force refresh accounts list
        _plaidAccounts.value = _plaidAccounts.value
    }

    fun syncPlaidTransactions() {
        val token = plaidManager.getAccessToken() ?: return
        viewModelScope.launch {
            try {
                _plaidError.value = null
                val newTx = plaidManager.syncTransactions(token)
                repository.processSyncedTransactions(newTx, plaidManager)
            } catch (e: Exception) {
                e.printStackTrace()
                _plaidError.value = "Sync failed: ${e.localizedMessage}"
            }
        }
    }

    fun clearPlaidError() {
        _plaidError.value = null
    }

    class Factory(
        private val repository: BenefitRepository,
        private val plaidManager: PlaidManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(repository, plaidManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

