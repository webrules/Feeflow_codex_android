package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.network.UrlConnectionFeedflowHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in, read-only checks against live upstream sources.
 *
 * Run with:
 * ./gradlew :core:data:test --tests '*LiveSourceSmokeTest' -Pfeedflow.liveTests=true
 *
 * Authenticated sources and all write operations are intentionally excluded.
 */
class LiveSourceSmokeTest {
    private val httpClient = UrlConnectionFeedflowHttpClient(
        connectTimeoutMillis = 15_000,
        readTimeoutMillis = 30_000,
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
    )

    @Test
    fun rssDefaultFeedReturnsReadableItems() = liveTest {
        val service = RssService(httpClient)
        val categories = service.fetchCategories()
        val category = categories.first()
        val threads = service.fetchCategoryThreads(category.id, categories, page = 1)

        assertTrue(threads.isNotEmpty(), "Default RSS feed returned no items")
        assertTrue(threads.first().title.isNotBlank(), "First RSS item has no title")
        val detail = service.fetchThreadDetail(threads.first().id, page = 1).thread
        assertEquals(threads.first().id, detail.id)
        assertTrue(detail.title != "Content Unavailable", "RSS detail fell back to a placeholder")
        assertTrue(detail.content.isNotBlank(), "RSS detail has no article content")
    }

    @Test
    fun hackerNewsReturnsListDetailAndSearch() = liveTest {
        val service = HackerNewsService(httpClient)
        val categories = service.fetchCategories()
        val category = categories.first { it.id == "topstories" }
        val threads = service.fetchCategoryThreads(category.id, categories, page = 1)

        assertTrue(threads.isNotEmpty(), "Hacker News top stories returned no items")
        val detail = service.fetchThreadDetail(threads.first().id, page = 1)
        assertEquals(threads.first().id, detail.thread.id)
        assertTrue(detail.thread.title.isNotBlank(), "Hacker News detail has no title")

        val search = service.searchThreads("android", page = 1)
        assertTrue(search.threads.isNotEmpty(), "Hacker News search returned no items")
    }

    @Test
    fun v2exReturnsPublicListAndDetail() = liveTest {
        val service = V2exService(httpClient = httpClient)
        val categories = service.fetchCategories()
        val category = categories.first { it.id == "tech" }
        val threads = service.fetchCategoryThreads(category.id, categories, page = 1)

        assertTrue(threads.isNotEmpty(), "V2EX tech tab returned no items")
        assertTrue(threads.first().author.avatar.startsWith("https://"), "V2EX list avatar was not parsed")
        val detail = service.fetchThreadDetail(threads.first().id, page = 1)
        assertEquals(threads.first().id, detail.thread.id)
        assertTrue(detail.thread.title.isNotBlank(), "V2EX detail has no title")
        assertTrue(detail.thread.author.avatar.startsWith("https://"), "V2EX topic avatar was not parsed")
        detail.comments.firstOrNull()?.let { comment ->
            assertTrue(comment.author.avatar.startsWith("https://"), "V2EX reply avatar was not parsed")
        }
    }

    @Test
    fun fourD4YGuestIndexReturnsPublicCategories() = liveTest {
        val service = FourD4YService(
            store = InMemoryFeedflowStore(),
            httpClient = httpClient,
        )
        val categories = service.fetchCategories()

        assertTrue(categories.isNotEmpty(), "4D4Y guest index returned no public categories")
        assertTrue(categories.all { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    private fun liveTest(block: suspend () -> Unit) = runBlocking {
        assumeTrue(
            "Enable live source checks with -Pfeedflow.liveTests=true",
            java.lang.Boolean.getBoolean("feedflow.liveTests"),
        )
        block()
    }
}
