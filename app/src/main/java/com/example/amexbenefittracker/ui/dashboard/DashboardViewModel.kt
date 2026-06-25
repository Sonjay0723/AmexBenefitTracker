package com.example.amexbenefittracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.repository.BenefitRepository
import com.example.amexbenefittracker.domain.model.CardSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class DashboardViewModel(private val repository: BenefitRepository) : ViewModel() {

    private val _selectedCardId = MutableStateFlow<Long?>(null)
    val selectedCardId = _selectedCardId.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

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

    fun setTrackingYear(year: String) {
        viewModelScope.launch {
            repository.updateTrackingYear(year)
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

    class Factory(private val repository: BenefitRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
