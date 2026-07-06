package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.network.FeedflowHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedflowRepositoryCacheParityTest {
    private val community = Community("recommend", "Recommend", "", "Zhihu", 0, 0)
    private val thread = FeedThread("t1", "Title", "Content", User("u", "alice", ""), community, "now", 0, 1)

    @Test fun loadCommunitiesSavesFreshAndFallsBackToCachedOnFailure() {
        runBlocking {
        val store = InMemoryFeedflowStore()
        val repository = FeedflowRepository(store = store, serviceFactory = { SucceedingService(listOf(community), listOf(thread)) })
        assertEquals(listOf(community), repository.loadCommunities(ForumSite.Zhihu).fresh)

        val failing = FeedflowRepository(store = store, serviceFactory = { FailingService("boom") })
        val result = failing.loadCommunities(ForumSite.Zhihu)
        assertEquals(listOf(community), result.cached)
        assertNull(result.fresh)
        assertNotNull(result.warning)
        }
    }

    @Test fun loadThreadsUsesCacheKeyAndPreservesOldForZhihuRecommendEmptyRefresh() {
        runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCachedTopics(FeedflowCacheKeys.topicList(ForumSite.Zhihu.serviceId, community.id, 1), listOf(thread))
        val repository = FeedflowRepository(store = store, serviceFactory = { SucceedingService(listOf(community), emptyList()) })
        val result = repository.loadThreads(ForumSite.Zhihu, community)
        assertEquals(listOf(thread), result.cached)
        assertEquals(listOf(thread), result.fresh)
        }
    }

    @Test fun nonPreservedEmptyRefreshCanClearThreads() {
        runBlocking {
        val store = InMemoryFeedflowStore()
        val hnCommunity = Community("topstories", "Top", "", "HN", 0, 0)
        store.saveCachedTopics(FeedflowCacheKeys.topicList(ForumSite.HackerNews.serviceId, hnCommunity.id, 1), listOf(thread.copy(community = hnCommunity)))
        val repository = FeedflowRepository(store = store, serviceFactory = { SucceedingService(listOf(hnCommunity), emptyList()) })
        assertEquals(emptyList(), repository.loadThreads(ForumSite.HackerNews, hnCommunity).fresh)
        }
    }

    @Test fun rssListAndDetailReuseTheSameServiceInstance() = runBlocking {
        val client = object : FeedflowHttpClient {
            override suspend fun get(url: String, cookies: List<FeedflowCookie>): String = """
                <rss><channel><item>
                  <title>Full RSS article</title>
                  <link>https://example.com/full-article</link>
                  <description><![CDATA[<p>Complete article body.</p>]]></description>
                  <author>Writer</author>
                </item></channel></rss>
            """.trimIndent()

            override suspend fun post(
                url: String,
                body: String,
                cookies: List<FeedflowCookie>,
                contentType: String,
            ): String = error("Unexpected POST")
        }
        val repository = FeedflowRepository(
            store = InMemoryFeedflowStore(),
            httpClient = client,
        )
        val rssCommunity = repository.loadCommunities(ForumSite.Rss).fresh!!.first()
        val listedThread = repository.loadThreads(ForumSite.Rss, rssCommunity).fresh!!.single()

        val detail = repository.loadThreadDetail(ForumSite.Rss, listedThread).fresh!!.first

        assertEquals("Full RSS article", detail.title)
        assertEquals("Complete article body.", detail.content)
    }

    @Test fun fourD4YMemberCommunitiesSurviveSmallerGuestResponse() = runBlocking {
        val store = InMemoryFeedflowStore()
        val memberCommunities = listOf(
            Community("2", "Public", "", "4D4Y", 0, 0),
            Community("99", "Discovery", "", "4D4Y", 0, 0),
        )
        store.saveCommunities(memberCommunities, ForumSite.FourD4Y.serviceId)
        val repository = FeedflowRepository(
            store = store,
            serviceFactory = { SucceedingService(memberCommunities.take(1), emptyList()) },
        )

        val result = repository.loadCommunities(ForumSite.FourD4Y)

        assertEquals(memberCommunities, result.fresh)
        assertEquals(memberCommunities, store.getCommunities(ForumSite.FourD4Y.serviceId))
    }

    @Test fun loadDetailIgnoresInvalidFourD4YCacheAndCachesFreshDetail() {
        runBlocking {
        val store = InMemoryFeedflowStore()
        val bad = thread.copy(content = "Could not parse content.")
        store.saveCachedThread(thread.id, ForumSite.FourD4Y.serviceId, bad, emptyList())
        val fresh = thread.copy(content = "Fresh")
        val repository = FeedflowRepository(store = store, serviceFactory = { SucceedingService(listOf(community), listOf(thread), fresh, listOf(Comment("c", User("u", "bob", ""), "ok", "now", 0))) })
        val result = repository.loadThreadDetail(ForumSite.FourD4Y, thread)
        assertNull(result.cached)
        assertEquals("Fresh", result.fresh?.first?.content)
        assertEquals("ok", store.getCachedThread(thread.id, ForumSite.FourD4Y.serviceId)?.second?.single()?.content)
        }
    }

    @Test fun prefetchUsesIosQueueLimitDebounceAndRateControl() {
        runBlocking {
        val store = InMemoryFeedflowStore()
        val requested = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        val service = object : StaticForumService() {
            override val name = "Prefetch"
            override val id = "prefetch"
            override val logo = "prefetch"

            override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
                requested += threadId
                val detail = thread.copy(id = threadId)
                return ThreadDetailResult(detail, emptyList(), 1)
            }

            override fun getWebUrl(thread: FeedThread): String = thread.id
        }
        val repository = FeedflowRepository(
            store = store,
            serviceFactory = { service },
            prefetchDelay = { delays += it },
        )
        val candidates = (1..7).map { index -> thread.copy(id = "thread-$index") }

        repository.prefetchThreadDetails(ForumSite.Zhihu, candidates, enabled = true, isWifi = true)

        assertEquals((1..5).map { "thread-$it" }, requested)
        assertEquals(
            listOf(
                BackgroundPrefetchPolicy.debounceMillis,
                BackgroundPrefetchPolicy.interItemDelayMillis,
                BackgroundPrefetchPolicy.interItemDelayMillis,
                BackgroundPrefetchPolicy.interItemDelayMillis,
                BackgroundPrefetchPolicy.interItemDelayMillis,
            ),
            delays,
        )
        }
    }
}

private class SucceedingService(
    private val categories: List<Community>,
    private val threads: List<FeedThread>,
    private val detailThread: FeedThread = threads.firstOrNull() ?: FeedThread("empty", "", "", User("", "", ""), Community("", "", "", "", 0, 0), "", 0, 0),
    private val comments: List<Comment> = emptyList(),
) : StaticForumService() {
    override val name = "Fixture"
    override val id = "fixture"
    override val logo = "fixture"
    override suspend fun fetchCategories(): List<Community> = categories
    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> = threads
    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult = ThreadDetailResult(detailThread, comments, 1)
    override fun getWebUrl(thread: FeedThread): String = thread.id
}

private class FailingService(private val message: String) : StaticForumService() {
    override val name = "Fail"
    override val id = "fail"
    override val logo = "fail"
    override suspend fun fetchCategories(): List<Community> = error(message)
    override fun getWebUrl(thread: FeedThread): String = thread.id
}
