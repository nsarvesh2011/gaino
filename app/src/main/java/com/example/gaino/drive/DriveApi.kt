package com.example.gaino.drive

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface DriveApi {

    @GET("drive/v3/files")
    suspend fun listAppDataFiles(
        @Header("Authorization") bearer: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("q") query: String, // e.g., "name = 'portfolio.json'"
        @Query("fields") fields: String = "files(id,name,modifiedTime)"
    ): DriveFilesResponse

    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): Response<ResponseBody>

    @Multipart
    @POST("upload/drive/v3/files")
    suspend fun createFileMultipart(
        @Header("Authorization") bearer: String,
        @Query("uploadType") uploadType: String = "multipart",
        @Part("metadata") metadata: RequestBody,
        @Part media: MultipartBody.Part
    ): DriveFile
}

data class DriveFilesResponse(val files: List<DriveFile> = emptyList())
data class DriveFile(val id: String?, val name: String?)
