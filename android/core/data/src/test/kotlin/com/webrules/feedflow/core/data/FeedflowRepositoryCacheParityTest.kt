package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
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
