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
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }

    fun cookiesFor(config: SiteLoginConfig, currentUrl: String?): List<FeedflowCookie> {
        val urls = linkedSetOf(
            config.loginUrl,
            "https://${config.cookieDomain}/",
            "https://www.${config.cookieDomain}/",
        )
        currentUrl?.takeIf { config.shouldCheckCookies(it) }?.let(urls::add)
        if (config.site == ForumSite.FourD4Y) {
            urls += "https://www.4d4y.com/forum/index.php"
            urls += "https://www.4d4y.com/forum/forumdisplay.php?fid=2"
        }
        return urls.flatMap { url ->
            WebCookieHeaderParser.parse(cookieManager.getCookie(url).orEmpty(), config.cookieDomain)
        }.distinctBy { "${it.name}|${it.domain}|${it.path}" }
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
