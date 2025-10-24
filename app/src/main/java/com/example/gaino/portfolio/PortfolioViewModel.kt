package com.example.gaino.portfolio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gaino.domain.Portfolio
import com.example.gaino.drive.PortfolioStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HoldingUi(
    val symbol: String,
    val qty: Double,
    val avgCost: Double,
    val lastPrice: Double,
    val pnlAbs: Double,
    val pnlPct: Double
)

data class PortfolioUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val holdings: List<HoldingUi> = emptyList(),
    val raw: Portfolio = Portfolio()
)

class PortfolioViewModel(app: Application) : AndroidViewModel(app) {

    private val store by lazy { PortfolioStore(app.applicationContext) }
    private val _state = MutableStateFlow(PortfolioUiState())

    private val priceRepo by lazy { com.example.gaino.market.PriceRepo(getApplication()) }

    val state: StateFlow<PortfolioUiState> = _state

    // Stub price map for now (Step 8 will fetch real prices)
    private var prices: Map<String, Double> = emptyMap()

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val p = store.load()
            // fetch prices (TTL cache inside)
            prices = priceRepo.getPrices(force = false)
            _state.value = PortfolioUiState(
                isLoading = false,
                error = null,
                holdings = p.holdings.map {
                    val last = prices[it.symbol] ?: 0.0
                    HoldingUi(
                        symbol = it.symbol,
                        qty = it.totalQty(),
                        avgCost = it.avgCost(),
                        lastPrice = last,
                        pnlAbs = it.pnlAbs(last),
                        pnlPct = it.pnlPct(last)
                    )
                },
                raw = p
            )
        }
    }

    fun addLot(symbol: String, qty: Double, price: Double) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val updated = _state.value.raw.upsertLot(symbol.trim(), qty, price, today)
            val ok = store.save(updated)
            if (ok) {
                // re-emit UI with recomputed P&L
                val p = updated
                val nextPrices = prices + (symbol to (prices[symbol] ?: 0.0))
                _state.value = PortfolioUiState(
                    isLoading = false,
                    error = null,
                    holdings = p.holdings.map {
                        val last = nextPrices[it.symbol] ?: 0.0
                        HoldingUi(
                            symbol = it.symbol,
                            qty = it.totalQty(),
                            avgCost = it.avgCost(),
                            lastPrice = last,
                            pnlAbs = it.pnlAbs(last),
                            pnlPct = it.pnlPct(last)
                        )
                    },
                    raw = p
                )
            } else {
                _state.value = _state.value.copy(error = "Save failed (conflict or offline)")
            }
        }
    }
}
