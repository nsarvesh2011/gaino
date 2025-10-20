package com.example.gaino.domain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.math.abs

@JsonClass(generateAdapter = true)
data class Portfolio(
    @Json(name = "version") val version: Int = 1,
    @Json(name = "displayCurrency") val displayCurrency: String = "INR",
    @Json(name = "holdings") val holdings: List<Holding> = emptyList(),
    @Json(name = "lastModifiedAt") val lastModifiedAt: String? = null,
    @Json(name = "lastModifiedByClient") val lastModifiedByClient: String = "android"
) {
    fun upsertLot(symbol: String, qty: Double, price: Double, dateIso: String): Portfolio {
        val updated = holdings.toMutableList()
        val idx = updated.indexOfFirst { it.symbol == symbol }
        val lot = Lot(qty = qty, price = price, date = dateIso)
        if (idx >= 0) {
            val h = updated[idx]
            updated[idx] = h.copy(lots = h.lots + lot)
        } else {
            updated += Holding(
                id = symbol, // keep stable
                kind = "stock",
                symbol = symbol,
                currency = "INR",
                lots = listOf(lot)
            )
        }
        return copy(holdings = updated)
    }
}

@JsonClass(generateAdapter = true)
data class Holding(
    @Json(name = "id") val id: String,
    @Json(name = "kind") val kind: String = "stock",
    @Json(name = "symbol") val symbol: String,
    @Json(name = "currency") val currency: String = "INR",
    @Json(name = "lots") val lots: List<Lot> = emptyList()
) {
    fun totalQty(): Double = lots.sumOf { it.qty }
    fun avgCost(): Double {
        val totalCost = lots.sumOf { it.qty * it.price }
        val q = totalQty()
        return if (q > 0.0) totalCost / q else 0.0
    }
    fun currentValue(lastPrice: Double): Double = totalQty() * lastPrice
    fun pnlAbs(lastPrice: Double): Double = currentValue(lastPrice) - lots.sumOf { it.qty * it.price }
    fun pnlPct(lastPrice: Double): Double {
        val invested = lots.sumOf { it.qty * it.price }
        return if (invested > 0.0) (pnlAbs(lastPrice) / invested) * 100.0 else 0.0
    }
}

@JsonClass(generateAdapter = true)
data class Lot(
    @Json(name = "qty") val qty: Double,
    @Json(name = "price") val price: Double,
    @Json(name = "date") val date: String // ISO yyyy-MM-dd
)
