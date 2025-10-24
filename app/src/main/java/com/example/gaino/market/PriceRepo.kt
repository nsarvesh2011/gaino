package com.example.gaino.market

import android.content.Context
import android.util.Log
import com.example.gaino.network.PricesHttp
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types as MoshiTypesLib
import java.lang.reflect.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PriceRepo(private val context: Context) {
    companion object {
        private const val TAG = "PriceRepo"
        private const val TTL_MS = 90_000L
        private const val PREF = "prices_cache"
        private const val KEY_JSON = "json"
        private const val KEY_TS = "ts"
    }

    private val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // Reuse one Moshi + adapter
    private val moshi = Moshi.Builder().build()
    private val mapType: Type = MoshiTypes.mapStringDouble
    private val mapAdapter = moshi.adapter<Map<String, Double>>(mapType)

    suspend fun getPrices(force: Boolean = false): Map<String, Double> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val ts = prefs.getLong(KEY_TS, 0L)
        val cached = prefs.getString(KEY_JSON, null)

        // Serve fresh-enough cache
        if (!force && cached != null && (now - ts) < TTL_MS) {
            try {
                mapAdapter.fromJson(cached)?.let { return@withContext it }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse cached prices", e)
            }
        }

        // Fetch fresh
        return@withContext try {
            Log.d(TAG, "Fetching prices from API...")
            val payload = PricesHttp.api.getStocks("stocks")
            Log.d(TAG, "API Response - tab: ${payload.tab}, asOf: ${payload.asOf}")
            Log.d(TAG, "Prices received: ${payload.prices}")
            val map = payload.prices

            prefs.edit()
                .putString(KEY_JSON, mapAdapter.toJson(map))
                .putLong(KEY_TS, now)
                .apply()

            Log.d(TAG, "Successfully cached ${map.size} prices")
            map
        } catch (t: Throwable) {
            Log.e(TAG, "Network fetch failed; falling back to stale cache", t)
            t.printStackTrace()
            if (cached != null) {
                try {
                    mapAdapter.fromJson(cached) ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
        }
    }
}

/** Moshi helper types (avoid name clash with com.squareup.moshi.Types) */
private object MoshiTypes {
    val mapStringDouble: Type = MoshiTypesLib.newParameterizedType(
        Map::class.java,
        String::class.java,
        java.lang.Double::class.java
    )
}
