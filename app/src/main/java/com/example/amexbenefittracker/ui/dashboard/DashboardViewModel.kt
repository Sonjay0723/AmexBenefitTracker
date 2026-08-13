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
import com.example.amexbenefittracker.util.toSlug
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

    private val _plaidConnected = MutableStateFlow(false)
    val plaidConnected = _plaidConnected.asStateFlow()

    // slug (e.g. "the_platinum_card") -> Plaid account id, as held by the worker.
    private val _cardMappings = MutableStateFlow<Map<String, String>>(emptyMap())
    val cardMappings = _cardMappings.asStateFlow()

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
            repository.reprocessExistingTransactions(plaidManager)
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
            // One-time, idempotent: migrates a locally-held legacy Plaid
            // token (from before the worker became the sole token holder)
            // into the worker's KV store. Runs on every refresh so it also
            // retries after a network failure, but it's a no-op once the
            // local legacy prefs have been cleared.
            migrateLegacyPlaidDataIfNeeded()
            repository.refreshData()
            refreshPlaidStatusInternal()
            syncPlaidTransactionsInternal(attempt = 0)
            _isRefreshing.value = false
        }
    }

    private suspend fun migrateLegacyPlaidDataIfNeeded() {
        val legacyToken = plaidManager.getLegacyAccessToken() ?: return
        try {
            val legacyMappings = cards.value
                .associate { card -> card.name.toSlug() to (plaidManager.getLegacyCardMapping(card.id) ?: "") }
                .filterValues { it.isNotBlank() }
            plaidManager.migrate(legacyToken, legacyMappings)
            // Whether migrated=true (imported) or false (already connected
            // elsewhere), the worker is now authoritative either way, so the
            // local copy is no longer needed.
            plaidManager.clearLegacyPlaidPrefs()
        } catch (e: Exception) {
            e.printStackTrace()
            // Leave the legacy prefs in place so this retries on the next
            // refresh instead of losing the only copy of the access token.
        }
    }

    private suspend fun refreshPlaidStatusInternal() {
        try {
            val status = plaidManager.fetchStatus()
            _plaidConnected.value = status.connected
            _plaidAccounts.value = status.accounts ?: emptyList()
            _cardMappings.value = status.cardMappings ?: emptyMap()
        } catch (e: Exception) {
            e.printStackTrace()
            _plaidError.value = "Failed to fetch Plaid status: ${e.localizedMessage}"
        }
    }

    /** Public entry point for UI-triggered refreshes (e.g. opening the Plaid settings dialog). */
    fun refreshPlaidStatus() {
        viewModelScope.launch {
            refreshPlaidStatusInternal()
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
                // No client_user_id to generate anymore - the worker derives
                // it from the caller's verified Firebase uid.
                val token = plaidManager.createLinkToken()
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
                // The worker no longer hands back an access_token (it never
                // leaves KV) - refresh status to pick up connected/accounts.
                plaidManager.exchangePublicToken(publicToken)
                refreshPlaidStatusInternal()
            } catch (e: Exception) {
                e.printStackTrace()
                _plaidError.value = "Token exchange failed: ${e.localizedMessage}"
            }
        }
    }

    fun mapCardToPlaidAccount(cardId: Long, plaidAccountId: String) {
        val card = cards.value.find { it.id == cardId } ?: return
        viewModelScope.launch {
            try {
                _plaidError.value = null
                val slug = card.name.toSlug()
                val updates = mutableMapOf(slug to plaidAccountId)
                // Clear the mapping from any other card that previously
                // pointed at this same Plaid account, mirroring the old
                // one-account-per-card behavior.
                if (plaidAccountId.isNotBlank()) {
                    _cardMappings.value.forEach { (otherSlug, mappedAccountId) ->
                        if (otherSlug != slug && mappedAccountId == plaidAccountId) {
                            updates[otherSlug] = ""
                        }
                    }
                }
                _cardMappings.value = plaidManager.saveCardMappings(updates)
                repository.reprocessExistingTransactions(plaidManager)
            } catch (e: Exception) {
                e.printStackTrace()
                _plaidError.value = "Failed to update account mapping: ${e.localizedMessage}"
            }
        }
    }

    fun disconnectPlaid() {
        viewModelScope.launch {
            try {
                _plaidError.value = null
                plaidManager.disconnect()
                _plaidConnected.value = false
                _plaidAccounts.value = emptyList()
                _cardMappings.value = emptyMap()
            } catch (e: Exception) {
                e.printStackTrace()
                _plaidError.value = "Failed to disconnect: ${e.localizedMessage}"
            }
        }
    }

    fun syncPlaidTransactions() {
        viewModelScope.launch {
            syncPlaidTransactionsInternal(attempt = 0)
        }
    }

    // The worker's sync cursor is destructive-on-advance, so it's committed
    // explicitly only after this device has durably persisted the synced
    // transactions - and via compare-and-swap against from_cursor, so a
    // cursor advanced by another device in the meantime is detected (409)
    // rather than silently skipping transactions. A capped retry re-syncs
    // against the newer cursor in that case instead of looping forever.
    private suspend fun syncPlaidTransactionsInternal(attempt: Int) {
        if (!_plaidConnected.value || attempt >= 3) return
        try {
            _plaidError.value = null
            val result = plaidManager.syncTransactions()
            if (result.added.isNotEmpty()) {
                repository.processSyncedTransactions(result.added, plaidManager)
            }
            repository.reprocessExistingTransactions(plaidManager)
            val committed = plaidManager.commitCursor(result.nextCursor, result.fromCursor)
            if (!committed) {
                syncPlaidTransactionsInternal(attempt + 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _plaidError.value = "Sync failed: ${e.localizedMessage}"
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

