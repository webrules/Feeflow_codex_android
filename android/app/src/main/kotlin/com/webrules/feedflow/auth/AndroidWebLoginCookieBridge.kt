package com.webrules.feedflow.auth

import android.webkit.CookieManager
import android.webkit.WebView
import com.webrules.feedflow.core.data.AuthSessionCoordinator
import com.webrules.feedflow.core.data.LoginCaptureResult
import com.webrules.feedflow.core.data.SiteLoginConfig
import com.webrules.feedflow.core.data.WebCookieHeaderParser
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.FeedflowCookie
import java.net.URI

class AndroidWebLoginCookieBridge(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {
    fun configure(webView: WebView) {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
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
        return urls.flatMap(::cookiesForUrl)
            .distinctBy { "${it.name}|${it.domain}|${it.path}" }
    }

    private fun cookiesForUrl(url: String): List<FeedflowCookie> {
        val host = runCatching { URI(url).host }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()
        return WebCookieHeaderParser.parse(cookieManager.getCookie(url).orEmpty(), host)
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
