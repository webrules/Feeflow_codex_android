package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowDatabaseContract
import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowCookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthSessionFoundationTest {
    private val now = 1_700_000_000_000L

    @Test fun webCookieHeaderParserCreatesSessionCookiesForDomain() {
        val cookies = WebCookieHeaderParser.parse("z_c0=abc; other=value", "zhihu.com")
        assertEquals(listOf("z_c0", "other"), cookies.map { it.name })
        assertEquals("zhihu.com", cookies.first().domain)
        assertNull(cookies.first().expiresAtMillis)
    }

    @Test fun captureRejectsEmptyWrongDomainMissingAuthAndRepeatedFailures() {
        val coordinator = AuthSessionCoordinator(InMemoryFeedflowStore(), nowMillis = { now })
        assertEquals(LoginCaptureFailure.NoCookies, assertIs<LoginCaptureResult.Rejected>(coordinator.captureSession(ForumSite.Zhihu, emptyList())).reason)

        val wrongDomain = listOf(FeedflowCookie("z_c0", "abc", "example.com", "/", null))
        assertEquals(LoginCaptureFailure.WrongDomain, assertIs<LoginCaptureResult.Rejected>(coordinator.captureSession(ForumSite.Zhihu, wrongDomain)).reason)

        val missingAuth = listOf(FeedflowCookie("q_c1", "abc", ".zhihu.com", "/", null))
        assertEquals(LoginCaptureFailure.MissingAuthCookie, assertIs<LoginCaptureResult.Rejected>(coordinator.captureSession(ForumSite.Zhihu, missingAuth)).reason)
        assertEquals(LoginCaptureFailure.RepeatedRejectedCookies, assertIs<LoginCaptureResult.Rejected>(coordinator.captureSession(ForumSite.Zhihu, missingAuth)).reason)
    }

    @Test fun captureAcceptsAuthCookieUpgradesSessionCookieAndClearsCachedTopics() {
        val store = InMemoryFeedflowStore(clockMillis = { now })
        store.saveCachedTopics("zhihu_hot_page1", listOf(sampleThread()))
        store.saveCachedTopics("v2ex_hot_page1", listOf(sampleThread().copy(id = "v2ex")))
        store.saveCommunities(listOf(Community("guest", "Guest", "", "Zhihu", 0, 0)), ForumSite.Zhihu.serviceId)
        val coordinator = AuthSessionCoordinator(store, nowMillis = { now })

        val result = coordinator.captureSession(
            ForumSite.Zhihu,
            listOf(FeedflowCookie("z_c0", "abc", ".zhihu.com", "/", expiresAtMillis = null, secure = true, httpOnly = true)),
        )

        val success = assertIs<LoginCaptureResult.Success>(result)
        assertEquals(now + 30L * 24 * 60 * 60 * 1000, success.cookies.single().expiresAtMillis)
        assertTrue(coordinator.restoreSession(ForumSite.Zhihu))
        assertNull(store.getCachedTopics("zhihu_hot_page1"))
        assertTrue(store.getCommunities(ForumSite.Zhihu.serviceId).isEmpty())
        assertEquals("v2ex", store.getCachedTopics("v2ex_hot_page1")?.single()?.id)
    }

    @Test fun logoutClearsOnlySelectedSiteAndLegacyCredentials() {
        val store: FeedflowStore = InMemoryFeedflowStore(clockMillis = { now })
        val coordinator = AuthSessionCoordinator(store, nowMillis = { now })
        coordinator.captureHeaderSession(ForumSite.HackerNews, "user=alice")
        coordinator.captureHeaderSession(ForumSite.Zhihu, "z_c0=abc")
        coordinator.captureHeaderSession(ForumSite.FourD4Y, "cdb_auth=abc")
        store.saveSetting("${FeedflowDatabaseContract.cookieSettingPrefix}${ForumSite.Zhihu.serviceId}_username", "alice")
        store.saveSetting("${FeedflowDatabaseContract.cookieSettingPrefix}${ForumSite.Zhihu.serviceId}_password", "secret")
        store.saveSetting("4d4y_sid", "sid")
        store.saveSetting("detected_4d4y_username", "alice")

        coordinator.logout(ForumSite.Zhihu)
        coordinator.logout(ForumSite.FourD4Y)

        assertFalse(coordinator.restoreSession(ForumSite.Zhihu))
        assertFalse(coordinator.restoreSession(ForumSite.FourD4Y))
        assertTrue(coordinator.restoreSession(ForumSite.HackerNews))
        assertNull(store.getSetting("${FeedflowDatabaseContract.cookieSettingPrefix}${ForumSite.Zhihu.serviceId}_username"))
        assertNull(store.getSetting("${FeedflowDatabaseContract.cookieSettingPrefix}${ForumSite.Zhihu.serviceId}_password"))
        assertNull(store.getSetting("4d4y_sid"))
        assertNull(store.getSetting("detected_4d4y_username"))
    }

    @Test fun zhihuServiceRestoreSessionRequiresSavedAuthCookies() = kotlinx.coroutines.runBlocking {
        assertFalse(ZhihuService(store = InMemoryFeedflowStore()).restoreSession())
    }

    private fun sampleThread(): FeedThread {
        val community = Community("hot", "Hot", "", "Zhihu", 0, 0)
        return FeedThread(
            id = "answer_1",
            title = "Recommended answer",
            content = "",
            author = User("u1", "alice", ""),
            community = community,
            timeAgo = "now",
            likeCount = 0,
            commentCount = 0,
        )
    }
}
