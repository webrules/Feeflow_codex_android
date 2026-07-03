package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowDatabaseContract
import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.CookieMatcher
import com.webrules.feedflow.core.network.FeedflowCookie

enum class LoginCaptureFailure {
    UnsupportedSite,
    NoCookies,
    WrongDomain,
    MissingAuthCookie,
    RepeatedRejectedCookies,
}

sealed interface LoginCaptureResult {
    data class Success(val site: ForumSite, val cookies: List<FeedflowCookie>) : LoginCaptureResult
    data class Rejected(val reason: LoginCaptureFailure) : LoginCaptureResult
}

class AuthSessionCoordinator(
    private val store: FeedflowStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val rejectedSignatures = mutableMapOf<ForumSite, String>()

    fun captureSession(site: ForumSite, cookies: List<FeedflowCookie>): LoginCaptureResult {
        val config = SiteLoginConfig.forSite(site) ?: return LoginCaptureResult.Rejected(LoginCaptureFailure.UnsupportedSite)
        if (cookies.isEmpty()) return reject(site, cookies, LoginCaptureFailure.NoCookies)

        val siteCookies = config.siteCookies(cookies)
        if (siteCookies.isEmpty()) return reject(site, cookies, LoginCaptureFailure.WrongDomain)

        val signature = CookieMatcher.signature(siteCookies)
        if (rejectedSignatures[site] == signature) {
            return LoginCaptureResult.Rejected(LoginCaptureFailure.RepeatedRejectedCookies)
        }
        if (!config.hasAuthenticatedSession(siteCookies)) {
            rejectedSignatures[site] = signature
            return LoginCaptureResult.Rejected(LoginCaptureFailure.MissingAuthCookie)
        }

        val upgraded = siteCookies.map { CookieMatcher.withThirtyDayExpiry(it, nowMillis()) }
        store.replaceCookies(site.serviceId, upgraded)
        store.saveCommunities(emptyList(), site.serviceId)
        store.clearCachedTopicsForService(site.serviceId)
        rejectedSignatures.remove(site)
        return LoginCaptureResult.Success(site, upgraded)
    }

    fun captureHeaderSession(site: ForumSite, cookieHeader: String): LoginCaptureResult {
        val config = SiteLoginConfig.forSite(site) ?: return LoginCaptureResult.Rejected(LoginCaptureFailure.UnsupportedSite)
        return captureSession(site, WebCookieHeaderParser.parse(cookieHeader, config.cookieDomain))
    }

    fun restoreSession(site: ForumSite): Boolean {
        val config = SiteLoginConfig.forSite(site) ?: return false
        return config.hasAuthenticatedSession(store.getCookies(site.serviceId).orEmpty())
    }

    fun logout(site: ForumSite) {
        store.clearCookies(site.serviceId)
        store.removeSetting("${FeedflowDatabaseContract.cookieSettingPrefix}${site.serviceId}_username")
        store.removeSetting("${FeedflowDatabaseContract.cookieSettingPrefix}${site.serviceId}_password")
        rejectedSignatures.remove(site)
    }

    private fun reject(site: ForumSite, cookies: List<FeedflowCookie>, reason: LoginCaptureFailure): LoginCaptureResult.Rejected {
        if (cookies.isNotEmpty()) rejectedSignatures[site] = CookieMatcher.signature(cookies)
        return LoginCaptureResult.Rejected(reason)
    }
}

object WebCookieHeaderParser {
    fun parse(cookieHeader: String, domain: String, path: String = "/"): List<FeedflowCookie> =
        cookieHeader.split(';')
            .mapNotNull { rawCookie ->
                val name = rawCookie.substringBefore('=').trim()
                val value = rawCookie.substringAfter('=', missingDelimiterValue = "").trim()
                if (name.isBlank()) {
                    null
                } else {
                    FeedflowCookie(name = name, value = value, domain = domain, path = path, expiresAtMillis = null)
                }
            }
}
