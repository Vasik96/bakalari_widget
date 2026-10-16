package com.vasik96.bakalariwidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Chyba přihlášení / neplatný token – nemá smysl opakovat bez nového přihlášení. */
class AuthException(message: String) : Exception(message)

object BakalariApi {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** "skola.bakalari.cz/login" -> "https://skola.bakalari.cz" */
    fun normalizeBase(input: String): String {
        val raw = input.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val url = raw.toHttpUrlOrNull() ?: throw IllegalArgumentException("Neplatná adresa školy")
        val port = if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        return "${url.scheme}://${url.host}$port"
    }

    suspend fun login(base: String, user: String, pass: String): TokenResponse =
        token(
            base,
            FormBody.Builder()
                .add("client_id", "ANDR")
                .add("grant_type", "password")
                .add("username", user)
                .add("password", pass)
                .build(),
        )

    suspend fun refresh(base: String, refreshToken: String): TokenResponse =
        token(
            base,
            FormBody.Builder()
                .add("client_id", "ANDR")
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build(),
        )

    private suspend fun token(base: String, body: FormBody): TokenResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/api/login").post(body).build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (r.code == 400 || r.code == 401) throw AuthException("Špatné jméno nebo heslo")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.decodeFromString<TokenResponse>(txt)
        }
    }

    /** GET {base}/api/3/{path} */
    suspend fun get(base: String, path: String, token: String, query: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val url = "$base/api/3/$path".toHttpUrl().newBuilder()
                .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { r ->
                if (r.code == 401) throw AuthException("Platnost přihlášení vypršela")
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        }
}
