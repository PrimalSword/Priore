package com.primalsword.priore

import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object TokenRefresher {
    private val http = OkHttpClient()

    fun refresh(
        credentials: CTraderCredentials,
        callback: (Result<CTraderCredentials>) -> Unit,
    ) {
        if (credentials.refreshToken.isBlank()) {
            callback(Result.failure(IllegalStateException("Refresh token não informado.")))
            return
        }
        val url = "https://openapi.ctrader.com/apps/token".toHttpUrl().newBuilder()
            .addQueryParameter("grant_type", "refresh_token")
            .addQueryParameter("refresh_token", credentials.refreshToken)
            .addQueryParameter("client_id", credentials.clientId)
            .addQueryParameter("client_secret", credentials.clientSecret)
            .build()
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${it.code} ao renovar token")))
                        return
                    }
                    val json = runCatching { JSONObject(it.body?.string().orEmpty()) }.getOrElse { error ->
                        callback(Result.failure(error))
                        return
                    }
                    val errorCode = json.optString("errorCode")
                    if (errorCode.isNotBlank() && errorCode != "null") {
                        callback(Result.failure(IOException("$errorCode · ${json.optString("description")}")))
                        return
                    }
                    val access = json.optString("accessToken")
                    val refresh = json.optString("refreshToken")
                    if (access.isBlank()) {
                        callback(Result.failure(IOException("Resposta sem access token")))
                        return
                    }
                    callback(
                        Result.success(
                            credentials.copy(
                                accessToken = access,
                                refreshToken = refresh.ifBlank { credentials.refreshToken },
                            ),
                        ),
                    )
                }
            }
        })
    }
}
