package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.RssFeedSubscription
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FeedflowAppStateControllerTest {
    @Test fun visibleSitesKeepRssLockedAndToggleOtherSources() {
        val controller = FeedflowAppStateController(FeedflowRepository())

        controller.toggleSite(ForumSite.Rss)
        assertTrue(controller.homeState.visibleSites.contains(ForumSite.Rss))

        controller.toggleSite(ForumSite.V2ex)
        assertFalse(controller.homeState.visibleSites.contains(ForumSite.V2ex))
        controller.toggleSite(ForumSite.V2ex)
        assertTrue(controller.homeState.visibleSites.contains(ForumSite.V2ex))
    }

    @Test fun refreshCommunitiesUsesFreshNetworkThenCaches() = runBlocking {
        val fresh = listOf(Community("fresh", "Fresh", "from service", "hn", 1, 2))
        val controller = FeedflowAppStateController(
            FeedflowRepository(
                serviceFactory = { StaticService(categories = fresh) },
            ),
        )

        val loaded = controller.refreshCommunities(ForumSite.HackerNews)

        assertEquals(fresh, loaded.value)
        assertFalse(loaded.loadedFromCache)
        assertEquals(fresh, controller.communities(ForumSite.HackerNews).value)
    }

    @Test fun refreshThreadsFallsBackToCachedStoreDataWhenNetworkFails() = runBlocking {
        val store = InMemoryFeedflowStore()
        val community = Community("top", "Top Stories", "", "hn", 0, 0)
        val cachedThread = FeedThread(
            id = "cached",
            title = "Cached iOS-compatible topic",
            content = "offline copy",
            author = User("cached", "Cached", "person.circle"),
            community = community,
            timeAgo = "1h",
            likeCount = 1,
            commentCount = 2,
        )
        store.saveCachedTopics(FeedflowCacheKeys.topicList(ForumSite.HackerNews.serviceId, community.id), listOf(cachedThread))
        val controller = FeedflowAppStateController(
            FeedflowRepository(
                store = store,
                serviceFactory = { FailingService() },
            ),
        )

        val loaded = controller.refreshThreads(ForumSite.HackerNews, community)

        assertEquals(listOf(cachedThread), loaded.value)
        assertTrue(loaded.loadedFromCache)
        assertTrue(loaded.warning?.contains("Network failure") == true)
    }

    @Test fun searchDelegatesToServiceAndSurfacesResults() = runBlocking {
        val community = Community("search", "Search", "android", "General", 0, 0)
        val thread = FeedThread(
            id = "search-1",
            title = "Android parity",
            content = "result",
            author = User("u", "User", "person.circle"),
            community = community,
            timeAgo = "now",
            likeCount = 0,
            commentCount = 1,
        )
        val controller = FeedflowAppStateController(
            FeedflowRepository(
                serviceFactory = { SearchService(SearchResult(listOf(thread), hasMore = true)) },
            ),
        )

        val loaded = controller.search(ForumSite.HackerNews, "android")

        assertEquals(listOf(thread), loaded.value.threads)
        assertTrue(loaded.value.hasMore)
    }

    @Test fun createThreadAndReplyDelegateToService() = runBlocking {
        val service = PostingService()
        val community = Community("general", "General", "", "v2ex", 0, 0)
        val thread = FeedThread(
            id = "topic-1",
            title = "Topic",
            content = "",
            author = User("u", "User", "person.circle"),
            community = community,
            timeAgo = "now",
            likeCount = 0,
            commentCount = 0,
        )
        val controller = FeedflowAppStateController(
            FeedflowRepository(
                serviceFactory = { service },
            ),
        )

        assertEquals(null, controller.createThread(ForumSite.V2ex, community, "  Title  ", "  Body  "))
        assertEquals("general:Title:Body", service.created.single())

        assertEquals(null, controller.postComment(ForumSite.V2ex, thread, "  Reply  "))
        assertEquals("topic-1:general:Reply", service.replies.single())
    }

    @Test fun rssServiceFactoryUsesStoredSubscriptions() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.replaceRssFeeds(listOf(RssFeedSubscription("https://example.com/custom.xml", "Custom Feed", false, 1)))

        val service = ForumSite.Rss.makeService(store = store, httpClient = FailingHttpClient())

        assertEquals(listOf("Custom Feed"), service.fetchCategories().map { it.name })
    }

    private class StaticService(
        private val categories: List<Community>,
    ) : StaticForumService() {
        override val name = "Static"
        override val id = "static"
        override val logo = "static"
        override suspend fun fetchCategories(): List<Community> = categories
        override fun getWebUrl(thread: FeedThread): String = thread.id
    }

    private class FailingService : StaticForumService() {
        override val name = "Failing"
        override val id = "failing"
        override val logo = "failing"
        override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
            throw FeedflowError.Network("https://example.invalid")
        }
        override fun getWebUrl(thread: FeedThread): String = thread.id
    }

    private class SearchService(private val result: SearchResult) : StaticForumService() {
        override val name = "Search"
        override val id = "search"
        override val logo = "search"
        override suspend fun searchThreads(query: String, page: Int): SearchResult = result
        override fun getWebUrl(thread: FeedThread): String = thread.id
    }

    private class PostingService : StaticForumService() {
        override val name = "Posting"
        override val id = "posting"
        override val logo = "paperplane.fill"
        val created = mutableListOf<String>()
        val replies = mutableListOf<String>()

        override suspend fun createThread(categoryId: String, title: String, content: String) {
            created += "$categoryId:$title:$content"
        }

        override suspend fun postComment(topicId: String, categoryId: String, content: String) {
            replies += "$topicId:$categoryId:$content"
        }

        override fun getWebUrl(thread: FeedThread): String = thread.id
    }

    private class FailingHttpClient : com.webrules.feedflow.core.network.FeedflowHttpClient {
        override suspend fun get(url: String, cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>): String =
            error("not used")

        override suspend fun post(
            url: String,
            body: String,
            cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>,
            contentType: String,
        ): String =
            error("not used")
    }
}
