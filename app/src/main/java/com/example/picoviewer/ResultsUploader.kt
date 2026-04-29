package com.example.picoviewer

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * POSTs TXT (+ optional CSV) as JSON with base64 bodies to an HTTPS endpoint (no mail UI).
 * Pair with [scripts/google-apps-script-upload.gs] or any server that accepts the same JSON shape.
 */
object ResultsUploader {

    sealed class UploadResult {
        data object Success : UploadResult()
        data class Failure(val message: String) : UploadResult()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun upload(
        context: Context,
        endpointHttpsUrl: String,
        sharedSecret: String,
        txtUri: Uri,
        txtFileName: String,
        csvUri: Uri?,
        csvFileName: String?,
    ): UploadResult {
        val trimmed = endpointHttpsUrl.trim()
        if (!trimmed.startsWith("https://")) {
            return UploadResult.Failure("Upload URL must start with https://")
        }
        return withContext(Dispatchers.IO) {
            try {
                val txtBytes = context.contentResolver.openInputStream(txtUri)?.use { it.readBytes() }
                    ?: return@withContext UploadResult.Failure("Could not read TXT file")
                val json = JSONObject()
                json.put("secret", sharedSecret)
                json.put("txt_filename", txtFileName)
                json.put("txt_base64", Base64.encodeToString(txtBytes, Base64.NO_WRAP))
                if (csvUri != null && csvFileName != null) {
                    val csvBytes = context.contentResolver.openInputStream(csvUri)?.use { it.readBytes() }
                        ?: return@withContext UploadResult.Failure("Could not read CSV file")
                    json.put("csv_filename", csvFileName)
                    json.put("csv_base64", Base64.encodeToString(csvBytes, Base64.NO_WRAP))
                }
                val body = json.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder().url(trimmed).post(body).build()
                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string().orEmpty()
                    when {
                        !response.isSuccessful ->
                            UploadResult.Failure("HTTP ${response.code}: ${respBody.take(500)}")
                        else -> {
                            try {
                                val j = JSONObject(respBody)
                                if (j.optBoolean("ok", true)) UploadResult.Success
                                else UploadResult.Failure(j.optString("error", "Server reported failure"))
                            } catch (_: Exception) {
                                UploadResult.Success
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                UploadResult.Failure(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
