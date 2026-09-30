package com.fastdownloader.app

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class AddRequest(val url: String)

data class DownloadFile(val path: String = "")

data class DownloadItem(
    val gid: String = "",
    val status: String = "",
    val totalLength: String = "0",
    val completedLength: String = "0",
    val downloadSpeed: String = "0",
    val files: List<DownloadFile> = emptyList()
)

data class DownloadsResponse(
    val active: List<DownloadItem> = emptyList(),
    val waiting: List<DownloadItem> = emptyList(),
    val stopped: List<DownloadItem> = emptyList()
)

data class AddResponse(val gid: String = "")

data class FileItem(
    val name: String = "",
    val size: Long = 0,
    val url: String = ""
)

data class FilesResponse(val files: List<FileItem> = emptyList())

interface Api {
    @GET("api/downloads")
    suspend fun downloads(@Header("Authorization") token: String): DownloadsResponse

    @POST("api/downloads")
    suspend fun add(
        @Header("Authorization") token: String,
        @Body request: AddRequest
    ): AddResponse

    @POST("api/pause/{gid}")
    suspend fun pause(
        @Header("Authorization") token: String,
        @Path("gid") gid: String
    )

    @POST("api/resume/{gid}")
    suspend fun resume(
        @Header("Authorization") token: String,
        @Path("gid") gid: String
    )

    @DELETE("api/downloads/{gid}")
    suspend fun remove(
        @Header("Authorization") token: String,
        @Path("gid") gid: String
    )

    @GET("api/files")
    suspend fun files(@Header("Authorization") token: String): FilesResponse
}
