package com.webrules.feedflow.core.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

data class FeedflowCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAtMillis: Long? = System.currentTimeMillis() + 3_600_000,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
)

interface FeedflowHttpClient {
    suspend fun get(url: String, cookies: List<FeedflowCookie> = emptyList()): String
    suspend fun get(
        url: String,
        cookies: List<FeedflowCookie>,
        headers: Map<String, String>,
    ): String = get(url, cookies)
    suspend fun get(
        url: String,
        cookies: List<FeedflowCookie>,
        headers: Map<String, String>,
        forcedCharset: String?,
    ): String = get(url, cookies, headers)
    suspend fun post(
        url: String,
        body: String,
        cookies: List<FeedflowCookie> = emptyList(),
        contentType: String = "application/x-www-form-urlencoded; charset=UTF-8",
    ): String
    suspend fun post(
        url: String,
        body: String,
        cookies: List<FeedflowCookie>,
        contentType: String,
        headers: Map<String, String>,
        forcedCharset: String?,
    ): String = post(url, body, cookies, contentType)
}

class UnimplementedFeedflowHttpClient : FeedflowHttpClient {
    override suspend fun get(url: String, cookies: List<FeedflowCookie>): String =
        error("Network client is not wired yet for $url")

    override suspend fun get(url: String, cookies: List<FeedflowCookie>, headers: Map<String, String>): String =
        error("Network client is not wired yet for $url")

    override suspend fun get(
        url: String,
        cookies: List<FeedflowCookie>,
        headers: Map<String, String>,
        forcedCharset: String?,
    ): String = error("Network client is not wired yet for $url")

    override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String =
        error("Network client is not wired yet for $url")

    override suspend fun post(
        url: String,
        body: String,
        cookies: List<FeedflowCookie>,
        contentType: String,
        headers: Map<String, String>,
        forcedCharset: String?,
    ): String = error("Network client is not wired yet for $url")
}

class UrlConnectionFeedflowHttpClient(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000,
    private val userAgent: String = "Feedflow Android/0.1",
) : FeedflowHttpClient {
    override suspend fun get(url: String, cookies: List<FeedflowCookie>): String =
        request(url = url, method = "GET", body = null, cookies = cookies)

    override suspend fun get(url: String, cookies: List<FeedflowCookie>, headers: Map<String, String>): String =
        request(url = url, method = "GET", body = null, cookies = cookies, headers = headers)

    override suspend fun get(
        url: String,
        cookies: List<FeedflowCookie>,
        headers: Map<String, String>,
        forcedCharset: String?,
    ): String = request(url = url, method = "GET", body = null, cookies = cookies, headers = headers, forcedCharset = forcedCharset)

    override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String =
        request(url = url, method = "POST", body = body, cookies = cookies, contentType = contentType)

    override suspend fun post(
        url: String,
        body: String,
        cookies: List<FeedflowCookie>,
        contentType: String,
        headers: Map<String, String>,
        forcedCharset: String?,
    ): String = request(
        url = url,
        method = "POST",
        body = body,
        cookies = cookies,
        contentType = contentType,
        headers = headers,
        forcedCharset = forcedCharset,
    )

    private fun request(
        url: String,
        method: String,
        body: String?,
        cookies: List<FeedflowCookie>,
        contentType: String = "application/x-www-form-urlencoded; charset=UTF-8",
        headers: Map<String, String> = emptyMap(),
        forcedCharset: String? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "text/html,application/json,application/xml,text/xml,*/*")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            CookieMatcher.matchingCookieHeader(url, cookies)?.let { setRequestProperty("Cookie", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val charset = forcedCharset?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: connection.responseCharset(bytes)
            val text = String(bytes, charset)
            if (status !in 200..299) {
                throw HttpStatusException(url, status, text.take(500))
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.responseCharset(body: ByteArray): Charset {
        val contentType = contentType.orEmpty()
        Regex("""charset=([^;\s]+)""", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)?.let { name ->
            runCatching { Charset.forName(name) }.getOrNull()?.let { return it }
        }
        val head = String(body, 0, minOf(body.size, 2048), Charsets.ISO_8859_1)
        Regex("""charset=["']?([\w-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)?.let { name ->
            runCatching { Charset.forName(name) }.getOrNull()?.let { return it }
        }
        return Charsets.UTF_8
    }
}

class HttpStatusException(
    val url: String,
    val statusCode: Int,
    val bodyPreview: String,
) : IOException("HTTP $statusCode for $url")

object CookieMatcher {
    fun signature(cookies: List<FeedflowCookie>): String =
        cookies.sortedWith(compareBy({ it.domain }, { it.path }, { it.name }, { it.value }))
            .joinToString("\n") { "${it.domain}|${it.path}|${it.name}|${it.value}" }

    fun matchingCookieHeader(url: String, cookies: List<FeedflowCookie>, nowMillis: Long = System.currentTimeMillis()): String? {
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase() ?: return null
        val requestPath = uri.path.ifEmpty { "/" }
        val matching = cookies.filter { cookie ->
            val cookieDomain = cookie.domain.lowercase().trimStart('.')
            val domainMatches = host == cookieDomain || host.endsWith(".$cookieDomain")
            val pathMatches = requestPath.startsWith(cookie.path)
            val unexpired = cookie.expiresAtMillis == null || cookie.expiresAtMillis >= nowMillis
            domainMatches && pathMatches && unexpired
        }
        val preferred = matching
            .groupBy { it.name }
            .mapNotNull { (_, candidates) ->
                candidates.maxWithOrNull(
                    compareBy<FeedflowCookie> { it.path.length }
                        .thenBy { it.domain.trimStart('.').length },
                )
            }
            .sortedWith(
                compareByDescending<FeedflowCookie> { it.path.length }
                    .thenByDescending { it.domain.trimStart('.').length }
                    .thenBy { it.name },
            )
        return preferred.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun withThirtyDayExpiry(cookie: FeedflowCookie, nowMillis: Long = System.currentTimeMillis()): FeedflowCookie =
        if (cookie.expiresAtMillis == null) cookie.copy(expiresAtMillis = nowMillis + 30L * 24 * 60 * 60 * 1000) else cookie
}
