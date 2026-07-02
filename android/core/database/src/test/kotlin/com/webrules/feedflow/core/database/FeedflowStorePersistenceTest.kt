package com.webrules.feedflow.core.database

import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowCookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeedflowStorePersistenceTest {
    private val community = Community(
        id = "swift",
        name = "Swift & Kotlin",
        description = "Cross-platform <dev>",
        category = "Mobile",
        activeToday = 42,
        onlineNow = 7,
    )

    private val thread = FeedThread(
        id = "thread-1",
        title = "Port Feedflow",
        content = "Line 1\nLine 2",
        author = User(id = "u1", username = "joey", avatar = "avatar.png", role = "admin"),
        community = community,
        timeAgo = "1h",
        likeCount = 12,
        commentCount = 2,
        isLiked = true,
        tags = listOf("android", "parity"),
        lastPostTime = "2m",
        lastPosterName = "alice",
    )

    @Test fun codecsRoundTripThreadCommentsAndCookies() {
        val comments = listOf(
            Comment(
                id = "c1",
                author = User("u2", "alice", "alice.png"),
                content = "Hello & welcome",
                timeAgo = "now",
                likeCount = 3,
                replies = listOf(Comment("c1-r1", User("u3", "bob", "bob.png"), "nested", "now", 0)),
            ),
        )
        val cached = FeedflowPersistenceCodecs.decodeCachedThread(
            FeedflowPersistenceCodecs.encodeCachedThread(thread, comments),
        )
        assertEquals(thread, cached.thread)
        assertEquals(comments, cached.comments)

        val cookies = listOf(
            FeedflowCookie("z_c0", "abc=123", ".zhihu.com", "/", 4_102_444_800_000L, secure = true, httpOnly = true),
        )
        val encodedCookies = FeedflowPersistenceCodecs.encodeCookies(cookies)
        assertTrue(encodedCookies.startsWith("[{"))
        assertTrue(encodedCookies.contains("\"expires\":"))
        assertEquals(cookies, FeedflowPersistenceCodecs.decodeCookies(encodedCookies, nowMillis = 1_000L))
        assertEquals(emptyList(), FeedflowPersistenceCodecs.decodeCookies(encodedCookies, nowMillis = 4_102_444_800_001L))
    }

    @Test fun inMemoryStoreSupportsOfflineCacheBookmarkSummaryAndRssFlows() {
        var now = 1_000_000L
        val store = InMemoryFeedflowStore(clockMillis = { now })
        store.saveCommunities(listOf(community), "linux_do")
        store.saveCachedTopics("linux_do_latest_page1", listOf(thread))
        store.saveCachedTopics("v2ex_hot_page1", listOf(thread.copy(id = "other")))
        store.saveCachedThread(thread.id, "linux_do", thread, emptyList())
        store.toggleBookmark(thread, "linux_do")
        store.saveUrlBookmark("https://example.com", "Example")
        store.saveSummary(thread.id, "linux_do", "Summary")
        store.addFilteredPost("answer-1", "zhihu")
        store.replaceRssFeeds(
            listOf(RssFeedSubscription("https://example.com/feed.xml", "Example Feed", isDefault = false, createdAt = now)),
        )
        store.saveCookies("linux_do", listOf(FeedflowCookie("_t", "old", "linux.do", "/", null)))
        store.saveCookies("linux_do", listOf(FeedflowCookie("_t", "new", "linux.do", "/", null)))

        assertEquals(listOf(community), store.getCommunities("linux_do"))
        assertEquals(listOf(thread), store.getCachedTopics("linux_do_latest_page1"))
        store.clearCachedTopicsForService("linux_do")
        assertEquals(null, store.getCachedTopics("linux_do_latest_page1"))
        assertEquals("other", store.getCachedTopics("v2ex_hot_page1")?.single()?.id)
        assertEquals(thread, store.getCachedThread(thread.id, "linux_do")?.first)
        assertTrue(store.isBookmarked(thread.id, "linux_do"))
        assertTrue(store.isUrlBookmarked("https://example.com"))
        assertEquals("Summary", store.getSummaryIfFresh(thread.id, "linux_do", maxAgeSeconds = 60))
        now += 61_000L
        assertEquals(null, store.getSummaryIfFresh(thread.id, "linux_do", maxAgeSeconds = 60))
        assertTrue(store.isPostFiltered("answer-1", "zhihu"))
        assertEquals(setOf("answer-1"), store.getFilteredPostIds("zhihu"))
        assertEquals("Example Feed", store.getRssFeeds().single().title)
        assertEquals("new", store.getCookies("linux_do")?.single()?.value)
    }

    @Test fun databaseContractKeepsIosCompatibleTables() {
        val schema = FeedflowDatabaseContract.schemaStatements.joinToString("\n")
        listOf(
            "communities",
            "settings",
            "filtered_posts",
            "ai_summaries",
            "cached_topics",
            "cached_threads",
            "bookmarks",
            "url_bookmarks",
            "rss_feeds",
        ).forEach { table -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS $table")) }
        assertTrue(schema.contains("PRIMARY KEY (id, serviceId)"))
        assertTrue(schema.contains("PRIMARY KEY (thread_id, service_id)"))
        assertFalse(DatabaseSchemaMigration.needsCompositePrimaryKeyMigration(listOf("thread_id", "service_id"), listOf("thread_id", "service_id")))
        assertNotNull(FeedflowDatabaseContract.cookieSettingKey("linux_do"))
    }
}
