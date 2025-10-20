package com.example.gaino.drive

import android.content.Context
import android.util.Log
import com.example.gaino.auth.AccessTokenProvider
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

    private var fileId: String? = null
    private var etag: String? = null

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE)

    private suspend fun bearer(): String? =
        AccessTokenProvider.getAccessToken(context)?.let { "Bearer $it" }

    /** Remote-first read; updates ETag & local cache. Falls back to cache when needed. */
    suspend fun ensureAndRead(): String? {
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
                    // Grab current ETag from metadata headers (Drive may omit it)
                    val metaResp: Response<ResponseBody> = DriveHttp.api.getMetadata(token, id)
                    if (metaResp.isSuccessful) {
                        val h = metaResp.headers()
                        etag = h["ETag"] ?: h["Etag"] ?: h["etag"]
                        Log.d(TAG, "Fetched ETag: $etag")
                    } else {
                        Log.w(TAG, "getMetadata failed: ${metaResp.code()}")
                    }

                    // Download JSON
                    val dl = DriveHttp.api.downloadFile(token, id)
                    if (dl.isSuccessful) {
                        val body = dl.body()?.string()
                        if (body != null) {
                            cacheFile().writeText(body)
                            Log.d(TAG, "Portfolio content (remote): $body")
                            return body
                        }
                    } else {
                        Log.e(TAG, "Download failed: ${dl.code()}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Remote read failed", t)
            }
        } else {
            Log.w(TAG, "No bearer token; using cache")
        }

        // Cache fallback
        return try {
            val cached = cacheFile().takeIf { it.exists() }?.readText()
            if (cached != null) Log.d(TAG, "Portfolio content (cache): $cached")
            cached
        } catch (t: Throwable) {
            Log.e(TAG, "Cache read failed", t)
            null
        }
    }

    /** Conflict-safe write with optional If-Match. Returns true on success. */
    suspend fun write(newJson: String): Boolean {
        val token = bearer()
        if (token == null) {
            Log.e(TAG, "write(): missing token")
            return false
        }
        val id = fileId
        if (id == null) {
            Log.e(TAG, "write(): missing fileId (call ensureAndRead() first)")
            return false
        }

        // metadata (required)
        val metadataJson = """{"name":"$FILE_NAME","mimeType":"application/json"}"""
        val metadataBody = metadataJson.toRequestBody(JSON)

        // media
        val mediaBody = newJson.toRequestBody(JSON)
        val mediaPart = MultipartBody.Part.createFormData("media", FILE_NAME, mediaBody)

        // Try with If-Match if we have it; otherwise, omit header (last-write-wins)
        var currentEtag: String? = etag
        try {
            val updated = DriveHttp.api.updateFileMultipart(
                bearer = token,
                fileId = id,
                ifMatch = currentEtag,   // may be null → Retrofit omits header
                metadata = metadataBody,
                media = mediaPart
            )
            // Refresh ETag after write (if available)
            val metaResp = DriveHttp.api.getMetadata(token, updated.id ?: id)
            if (metaResp.isSuccessful) {
                val h = metaResp.headers()
                etag = h["ETag"] ?: h["Etag"] ?: h["etag"]
            }
            cacheFile().writeText(newJson)
            Log.d(TAG, "Write success; new ETag: $etag")
            return true
        } catch (t: Throwable) {
            val code = (t as? HttpException)?.code()
            Log.e(TAG, "Write failed (code=$code, etag=$currentEtag)", t)

            // If we tried with an ETag and got 412, refresh ETag and retry once.
            if (code == 412) {
                try {
                    val metaResp2 = DriveHttp.api.getMetadata(token, id)
                    if (metaResp2.isSuccessful) {
                        val h = metaResp2.headers()
                        etag = h["ETag"] ?: h["Etag"] ?: h["etag"]
                        Log.d(TAG, "Refreshed ETag: $etag")
                        val retry = DriveHttp.api.updateFileMultipart(
                            bearer = token,
                            fileId = id,
                            ifMatch = etag,   // retry with new ETag
                            metadata = metadataBody,
                            media = mediaPart
                        )
                        val metaResp3 = DriveHttp.api.getMetadata(token, retry.id ?: id)
                        if (metaResp3.isSuccessful) {
                            val h2 = metaResp3.headers()
                            etag = h2["ETag"] ?: h2["Etag"] ?: h2["etag"]
                        }
                        cacheFile().writeText(newJson)
                        Log.d(TAG, "Write success after retry; new ETag: $etag")
                        return true
                    }
                } catch (retryErr: Throwable) {
                    Log.e(TAG, "Retry after 412 failed", retryErr)
                }
            }
            return false
        }
    }

    private suspend fun createEmpty(bearer: String): String? {
        val metadataJson =
            """{"name":"$FILE_NAME","parents":["appDataFolder"],"mimeType":"application/json"}"""
        val metadataBody = metadataJson.toRequestBody(JSON)

        val mediaBody = """{"holdings":[]}""".toRequestBody(JSON)
        val mediaPart = MultipartBody.Part.createFormData("media", FILE_NAME, mediaBody)

        val created = DriveHttp.api.createFileMultipart(
            bearer = bearer,
            metadata = metadataBody,
            media = mediaPart
        )
        Log.d(TAG, "Created $FILE_NAME with id: ${created.id}")
        return created.id
    }
}
