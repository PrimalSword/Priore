package com.primalsword.priore

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

object UpdateManager {
    data class RemoteUpdate(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String,
    )

    private const val UPDATE_URL =
        "https://raw.githubusercontent.com/PrimalSword/Priore/main/releases/update.json"

    private val http = OkHttpClient.Builder().build()
    private val main = Handler(Looper.getMainLooper())

    fun check(
        context: Context,
        onResult: (RemoteUpdate?) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val request = Request.Builder()
            .url(UPDATE_URL)
            .header("Cache-Control", "no-cache")
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { onError(e.message ?: "Falha ao consultar atualização") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        main.post { onError("GitHub respondeu HTTP ${it.code}") }
                        return
                    }

                    val parsed = runCatching {
                        val json = JSONObject(it.body?.string().orEmpty())
                        RemoteUpdate(
                            versionCode = json.getInt("versionCode"),
                            versionName = json.getString("versionName"),
                            apkUrl = json.getString("apkUrl"),
                            notes = json.optString("notes"),
                        )
                    }.getOrElse { error ->
                        main.post { onError("Manifesto de atualização inválido: ${error.message}") }
                        return
                    }

                    val available = parsed.takeIf { update ->
                        update.versionCode > BuildConfig.VERSION_CODE
                    }
                    main.post { onResult(available) }
                }
            }
        })
    }

    fun downloadAndInstall(
        context: Context,
        update: RemoteUpdate,
        onStatus: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onStatus("Autorize o Priore a instalar atualizações e toque novamente em atualizar.")
            return
        }

        onStatus("Baixando Priore ${update.versionName}…")
        val request = Request.Builder().url(update.apkUrl).build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { onError(e.message ?: "Falha no download do APK") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        main.post { onError("Download respondeu HTTP ${it.code}") }
                        return
                    }

                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val apk = File(dir, "priore-${update.versionCode}.apk")
                    runCatching {
                        it.body?.byteStream()?.use { input ->
                            apk.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("APK vazio")
                    }.onFailure { error ->
                        main.post { onError("Não foi possível salvar o APK: ${error.message}") }
                        return
                    }

                    main.post {
                        runCatching { launchInstaller(context, apk) }
                            .onSuccess { onStatus("Atualização baixada · confirme a instalação no Android.") }
                            .onFailure { error ->
                                onError("Não foi possível abrir o instalador: ${error.message}")
                            }
                    }
                }
            }
        })
    }

    private fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
