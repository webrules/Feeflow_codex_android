package com.webrules.feedflow.auth

import android.webkit.CookieManager
import android.webkit.WebView
import java.net.CookieHandler
import java.net.CookieManager as JavaCookieManager
import java.net.HttpCookie
import java.net.URI
import com.webrules.feedflow.core.data.AuthSessionCoordinator
import com.webrules.feedflow.core.data.LoginCaptureResult
import com.webrules.feedflow.core.data.SiteLoginConfig
import com.webrules.feedflow.core.data.WebCookieHeaderParser
import com.webrules.feedflow.core.data.FOURD4Y_MOBILE_UA
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.FeedflowCookie

class AndroidWebLoginCookieBridge(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {
    init {
        // Ensure CookieHandler is set so HttpURLConnection uses cookies automatically
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(JavaCookieManager())
        }
    }

    fun syncToSystemCookieHandler(config: SiteLoginConfig, cookies: List<FeedflowCookie>) {
        val handler = CookieHandler.getDefault() ?: return
        if (handler !is JavaCookieManager) return
        val cookieStore = handler.cookieStore
        // Remove existing cookies for this domain
        val uris = listOf(
            URI("https://www.${config.cookieDomain}/"),
            URI("https://${config.cookieDomain}/"),
            URI(config.loginUrl),
        )
        for (uri in uris) {
            cookieStore.get(uri).forEach { cookieStore.remove(uri, it) }
        }
        // Add captured cookies
        for (cookie in config.siteCookies(cookies)) {
            val httpCookie = HttpCookie(cookie.name, cookie.value).apply {
                domain = cookie.domain
                path = cookie.path
                secure = cookie.secure
                maxAge = cookie.expiresAtMillis?.let { 
                    ((it - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                } ?: -1
            }
            for (uri in uris) {
                cookieStore.add(uri, httpCookie)
            }
        }
    }

    fun configure(webView: WebView) {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.userAgentString = FOURD4Y_MOBILE_UA
        webView.settings.savePassword = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
        }
    }

    fun cookiesFor(config: SiteLoginConfig, currentUrl: String?): List<FeedflowCookie> {
        val authenticatedPageUrl = currentUrl?.takeIf { config.shouldCheckCookies(it) }
        val urls = linkedSetOf<String>()
        authenticatedPageUrl?.let(urls::add)
        urls += "https://www.${config.cookieDomain}/"
        urls += "https://${config.cookieDomain}/"
        urls += config.loginUrl
        if (config.site == ForumSite.FourD4Y) {
            urls += "https://www.4d4y.com/forum/index.php"
            urls += "https://www.4d4y.com/forum/forumdisplay.php?fid=2"
        }
        val result = urls.flatMap(::cookiesForUrl)
            .distinctBy { "${it.name}|${it.domain}|${it.path}" }
        return result
    }

    private fun cookiesForUrl(url: String): List<FeedflowCookie> {
        val host = runCatching { URI(url).host }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()
        val raw = cookieManager.getCookie(url).orEmpty()
        return WebCookieHeaderParser.parse(raw, host)
    }

    fun capture(
        site: ForumSite,
        config: SiteLoginConfig,
        currentUrl: String?,
        coordinator: AuthSessionCoordinator,
    ): LoginCaptureResult = coordinator.captureSession(site, cookiesFor(config, currentUrl))

    fun installCookies(config: SiteLoginConfig, cookies: List<FeedflowCookie>) {
        config.siteCookies(cookies).forEach { cookie ->
            val targetUrl = "https://${cookie.domain.trimStart('.')}${cookie.path}"
            cookieManager.setCookie(targetUrl, cookie.toSetCookieHeader())
        }
        cookieManager.flush()
    }

    fun clearSiteCookies(config: SiteLoginConfig) {
        // Check all the same URLs that cookiesFor checks
        val existing = cookiesFor(config, null)
        existing.forEach { cookie ->
            val targetUrl = "https://${cookie.domain.trimStart('.')}${cookie.path}"
            cookieManager.setCookie(targetUrl, "${cookie.name}=; Domain=${cookie.domain}; Path=${cookie.path}; Max-Age=0")
        }
        // Also clear via loginUrl as fallback
        cookieManager.setCookie(config.loginUrl, "")
        cookieManager.flush()
    }

    fun flush() {
        cookieManager.flush()
    }

    private fun FeedflowCookie.toSetCookieHeader(): String =
        buildString {
            append(name).append('=').append(value)
            append("; Domain=").append(domain)
            append("; Path=").append(path)
            expiresAtMillis?.let { expiresAt ->
                val maxAgeSeconds = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                append("; Max-Age=").append(maxAgeSeconds)
            }
            if (secure) append("; Secure")
            if (httpOnly) append("; HttpOnly")
        }
}
