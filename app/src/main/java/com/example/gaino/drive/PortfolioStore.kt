package com.example.gaino.drive

import android.content.Context
import android.util.Log
import com.example.gaino.auth.AccessTokenProvider
import com.example.gaino.domain.Portfolio
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.File

class PortfolioStore(private val context: Context) {

    companion object {
        private const val TAG = "PortfolioStore"
        private const val FILE_NAME = "portfolio.json"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val CACHE_FILE = "portfolio_cache.json"
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(Portfolio::class.java)

    private var fileId: String? = null
    private var etag: String? = null

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE)

    private suspend fun bearer(): String? =
        AccessTokenProvider.getAccessToken(context)?.let { "Bearer $it" }

    /** Remote-first read → Portfolio; updates ETag & local cache; cache fallback offline. */
    suspend fun load(): Portfolio {
        val token = bearer()
        if (token != null) {
            try {
                // Find or create file
                val list = DriveHttp.api.listAppDataFiles(
                    bearer = token,
                    query = "name = '$FILE_NAME'"
                )
                val existingId = list.files.firstOrNull()?.id
                fileId = existingId ?: createEmpty(token)

                fileId?.let { id ->
                    // Fetch ETag (Drive may omit it)
                    val metaResp: Response<ResponseBody> = DriveHttp.api.getMetadata(token, id)
                    if (metaResp.isSuccessful) {
                        val h = metaResp.headers()
                        etag = h["ETag"] ?: h["Etag"] ?: h["etag"]
                        Log.d(TAG, "Fetched ETag: $etag")
                    }

                    // Download JSON
                    val dl = DriveHttp.api.downloadFile(token, id)
                    if (dl.isSuccessful) {
                        var body = dl.body()?.string()
                        if (!body.isNullOrBlank()) {
                            // Basic sanitize for earlier test JSON (remove trailing ",]")
                            val sanitized = body.replace(",]", "]")
                            cacheFile().writeText(sanitized)
                            return try {
                                val portfolio = adapter.fromJson(sanitized)!!
                                Log.d(TAG, "Loaded (remote): $sanitized")
                                portfolio
                            } catch (e: Exception) {
                                Log.e(
                                    TAG,
                                    "Remote JSON malformed; self-healing to empty portfolio",
                                    e
                                )
                                selfHealToEmpty(token, id) // overwrite remotely and cache
                                Portfolio()
                            }
                        }
                    } else {
                        Log.e(TAG, "Download failed: ${dl.code()}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Remote load failed", t)
            }
        } else {
            Log.w(TAG, "No bearer token; using cache")
        }

        // Cache fallback (also tolerant)
        val cached = cacheFile().takeIf { it.exists() }?.readText()
        if (!cached.isNullOrBlank()) {
            val sanitized = cached.replace(",]", "]")
            return try {
                val p = adapter.fromJson(sanitized)!!
                Log.d(TAG, "Loaded (cache): $sanitized")
                p
            } catch (e: Exception) {
                Log.e(TAG, "Cache JSON malformed; resetting to empty", e)
                cacheFile().writeText(adapter.toJson(Portfolio()))
                Portfolio()
            }
        }
        return Portfolio()
    }

    /** Overwrite remote + cache with an empty valid Portfolio (last-write-wins). */
    private suspend fun selfHealToEmpty(bearer: String, id: String) {
        val emptyJson = adapter.toJson(Portfolio())
        // metadata
        val metaJson = """{"name":"$FILE_NAME","mimeType":"application/json"}"""
        val metadataBody = metaJson.toRequestBody(JSON)
        // media
        val mediaBody = emptyJson.toRequestBody(JSON)
        val mediaPart = MultipartBody.Part.createFormData("media", FILE_NAME, mediaBody)
        try {
            val updated = DriveHttp.api.updateFileMultipart(
                bearer = bearer,
                fileId = id,
                ifMatch = null, // omit If-Match → last-write-wins to repair
                metadata = metadataBody,
                media = mediaPart
            )
            // refresh ETag after heal
            val metaResp = DriveHttp.api.getMetadata(bearer, updated.id ?: id)
            if (metaResp.isSuccessful) {
                val h = metaResp.headers()
                etag = h["ETag"] ?: h["Etag"] ?: h["etag"]
            }
            cacheFile().writeText(emptyJson)
            Log.d(TAG, "Self-heal complete; new ETag: $etag")
        } catch (healErr: Throwable) {
            Log.e(TAG, "Self-heal failed; continuing with empty in-memory", healErr)
            // We still proceed with an in-memory empty portfolio so UI won’t crash
        }
    }

    /** Conflict-safe write with optional If-Match. Returns true on success. */
    suspend fun save(portfolio: Portfolio): Boolean {
        val token = bearer() ?: return false
        val id = fileId ?: return false
        val metaJson = """{"name":"$FILE_NAME","mimeType":"application/json"}"""
        val metadataBody = metaJson.toRequestBody(JSON)

        val json = adapter.toJson(portfolio)
        val mediaBody = json.toRequestBody(JSON)
        val mediaPart = MultipartBody.Part.createFormData("media", FILE_NAME, mediaBody)

        var currentEtag: String? = etag
        try {
            val updated = DriveHttp.api.updateFileMultipart(
                bearer = token,
                fileId = id,
                ifMatch = currentEtag, // nullable → header omitted if null
                metadata = metadataBody,
                media = mediaPart
            )
            // Refresh ETag
            val metaResp = DriveHttp.api.getMetadata(token, updated.id ?: id)
            if (metaResp.isSuccessful) {
                val h = metaResp.headers()
                etag = h["ETag"] ?: h["Etag"] ?: h["etag"]
            }
            cacheFile().writeText(json)
            Log.d(TAG, "Save success; new ETag: $etag")
            return true
        } catch (t: Throwable) {
            val code = (t as? HttpException)?.code()
            Log.e(TAG, "Save failed (code=$code, etag=$currentEtag)", t)
            if (code == 412) { // ETag mismatch → refresh and retry once
                return try {
                    val metaResp2 = DriveHttp.api.getMetadata(token, id)
                    if (metaResp2.isSuccessful) {
                        val h = metaResp2.headers()
                        etag = h["ETag"] ?: h["Etag"] ?: h["etag"]
                        val retry = DriveHttp.api.updateFileMultipart(
                            bearer = token,
                            fileId = id,
                            ifMatch = etag,
                            metadata = metadataBody,
                            media = mediaPart
                        )
                        val metaResp3 = DriveHttp.api.getMetadata(token, retry.id ?: id)
                        if (metaResp3.isSuccessful) {
                            val h2 = metaResp3.headers()
                            etag = h2["ETag"] ?: h2["Etag"] ?: h2["etag"]
                        }
                        cacheFile().writeText(json)
                        Log.d(TAG, "Save success after retry; new ETag: $etag")
                        true
                    } else false
                } catch (retryErr: Throwable) {
                    Log.e(TAG, "Retry after 412 failed", retryErr)
                    false
                }
            }
            return false
        }
    }

    private suspend fun createEmpty(bearer: String): String? {
        val metadataJson =
            """{"name":"$FILE_NAME","parents":["appDataFolder"],"mimeType":"application/json"}"""
        val metadataBody = metadataJson.toRequestBody(JSON)

        val empty = adapter.toJson(Portfolio()) // {"version":1,"displayCurrency":"INR","holdings":[]...}
        val mediaBody = empty.toRequestBody(JSON)
        val mediaPart = MultipartBody.Part.createFormData("media", FILE_NAME, mediaBody)

        val created = DriveHttp.api.createFileMultipart(
            bearer = bearer,
            metadata = metadataBody,
            media = mediaPart
        )
        Log.d(TAG, "Created $FILE_NAME with id: ${created.id}")
        cacheFile().writeText(empty)
        return created.id
    }
}
