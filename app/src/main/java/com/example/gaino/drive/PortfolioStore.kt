package com.example.gaino.drive

import android.content.Context
import android.util.Log
import com.example.gaino.auth.AccessTokenProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class PortfolioStore(private val context: Context) {

    companion object {
        private const val TAG = "PortfolioStore"
        private const val FILE_NAME = "portfolio.json"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun ensureAndRead(): String? {
        val token = AccessTokenProvider.getAccessToken(context)?.let { "Bearer $it" } ?: return null

        // 1) Look for the file
        val query = "name = '$FILE_NAME'"
        val list = DriveHttp.api.listAppDataFiles(token, query = query)
        val existingId = list.files.firstOrNull()?.id

        val fileId = existingId ?: createEmpty(token) ?: return null

        // 2) Download content
        val resp = DriveHttp.api.downloadFile(token, fileId)
        if (!resp.isSuccessful) {
            Log.e(TAG, "Download failed: ${resp.code()}")
            return null
        }
        val body = resp.body()?.string()
        Log.d(TAG, "Portfolio content: $body")
        return body
    }

    private suspend fun createEmpty(bearer: String): String? {
        // Metadata as named RequestBody (no filename)
        val metadataJson =
            """{"name":"$FILE_NAME","parents":["appDataFolder"],"mimeType":"application/json"}"""
        val metadataBody = metadataJson.toRequestBody(JSON)

        // Media as Part with filename
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
