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
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id,name,modifiedTime)"
    ): DriveFilesResponse

    // We only need headers here (ETag); body isn’t used.
    @GET("drive/v3/files/{fileId}")
    suspend fun getMetadata(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = "id,name,modifiedTime"
    ): Response<ResponseBody>

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

    // PATCH with If-Match (conflict-safe write)
    @Multipart
    @PATCH("upload/drive/v3/files/{fileId}")
    suspend fun updateFileMultipart(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("uploadType") uploadType: String = "multipart",
        @Header("If-Match") ifMatch: String?,
        @Part("metadata") metadata: RequestBody,
        @Part media: MultipartBody.Part
    ): DriveFile
}

data class DriveFilesResponse(val files: List<DriveFile> = emptyList())
data class DriveFile(val id: String?, val name: String?)
