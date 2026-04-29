package com.example.amexbenefittracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.data.local.entities.UsageHistory
import com.example.amexbenefittracker.data.repository.BenefitRepository
import com.example.amexbenefittracker.domain.model.CardSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class DashboardViewModel(private val repository: BenefitRepository) : ViewModel() {

    private val _selectedCardId = MutableStateFlow<Long?>(null)
    val selectedCardId = _selectedCardId.asStateFlow()

    val trackingYear: StateFlow<String> = flow {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        emit(calendar.get(Calendar.YEAR).toString())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).get(Calendar.YEAR).toString())

    val cards = repository.getAllCards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val cardSummary: StateFlow<CardSummary?> = selectedCardId
        .flatMapLatest { id ->
            if (id != null) repository.getCardSummary(id) else flowOf(null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val benefits: StateFlow<List<BenefitUiModel>> = selectedCardId
        .flatMapLatest { id ->
            if (id == null) return@flatMapLatest flowOf(emptyList())
            
            val benefitsFlow = repository.getBenefitsForCard(id)
            val usageFlow = repository.getUsageForCard(id)
            
            combine(benefitsFlow, usageFlow) { benefits, usages ->
                benefits.map { benefit ->
                    val period = getCurrentPeriod(benefit)
                    val relevantUsages = usages.filter { it.benefitId == benefit.id }
                    val currentPeriodUsage = relevantUsages.find { it.periodIdentifier == period }
                    
                    val totalClaimed = relevantUsages.sumOf { it.amountClaimed }
                    
                    BenefitUiModel(
                        benefit = benefit,
                        totalClaimedInPeriod = totalClaimed, // This should probably be total for the whole year vs totalValue
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

    fun getCurrentPeriod(benefit: Benefit): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        val year = calendar.get(Calendar.YEAR)
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
