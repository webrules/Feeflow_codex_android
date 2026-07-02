package com.webrules.feedflow.auth

import android.webkit.CookieManager
import android.webkit.WebView
import com.webrules.feedflow.core.data.AuthSessionCoordinator
import com.webrules.feedflow.core.data.LoginCaptureResult
import com.webrules.feedflow.core.data.SiteLoginConfig
import com.webrules.feedflow.core.data.WebCookieHeaderParser
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.FeedflowCookie

class AndroidWebLoginCookieBridge(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {
    fun configure(webView: WebView) {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
    }

    fun cookiesFor(config: SiteLoginConfig, currentUrl: String?): List<FeedflowCookie> {
        val cookieUrl = currentUrl?.takeIf { config.shouldCheckCookies(it) } ?: config.loginUrl
        val header = cookieManager.getCookie(cookieUrl).orEmpty()
        return WebCookieHeaderParser.parse(header, config.cookieDomain)
    }

    fun capture(
        site: ForumSite,
        config: SiteLoginConfig,
        currentUrl: String?,
        coordinator: AuthSessionCoordinator,
    ): LoginCaptureResult = coordinator.captureSession(site, cookiesFor(config, currentUrl))

    fun clearSiteCookies(config: SiteLoginConfig) {
        val existing = cookiesFor(config, config.loginUrl)
        existing.forEach { cookie ->
            cookieManager.setCookie(config.loginUrl, "${cookie.name}=; Domain=${cookie.domain}; Path=${cookie.path}; Max-Age=0")
        }
        cookieManager.flush()
    }

    fun flush() {
        cookieManager.flush()
    }
}
