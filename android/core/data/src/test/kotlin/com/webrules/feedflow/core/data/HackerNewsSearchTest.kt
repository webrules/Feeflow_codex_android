package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.network.FeedflowHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class HackerNewsSearchTest {
    @Test fun parsesAlgoliaSearchHitsIntoFeedThreads() {
        val result = HackerNewsSearchJson.parse(
            """
            {
              "hits": [
                {
                  "created_at_i": 1700000000,
                  "title": "Launch HN: Feedflow",
                  "url": "https://example.com/feedflow",
                  "author": "pg",
                  "points": 42,
                  "num_comments": 7,
                  "objectID": "123"
                }
              ],
              "page": 0,
              "nbPages": 2
            }
            """.trimIndent(),
            Community("search", "Search", "Feedflow", "General", 0, 0),
        )

        assertEquals(1, result.threads.size)
        assertEquals("123", result.threads.single().id)
        assertEquals("Launch HN: Feedflow", result.threads.single().title)
        assertEquals("https://example.com/feedflow", result.threads.single().content)
        assertEquals(42, result.threads.single().likeCount)
        assertEquals(7, result.threads.single().commentCount)
        assertTrue(result.hasMore)
    }

    @Test fun serviceSearchUsesAlgoliaEndpointAndEncodesQuery() = runBlocking {
        val client = RecordingClient(HACKER_NEWS_SEARCH_FIXTURE)
        val result = HackerNewsService(client).searchThreads("android parity", 2)

        assertTrue(client.lastUrl.contains("hn.algolia.com/api/v1/search"))
        assertTrue(client.lastUrl.contains("query=android+parity"))
        assertTrue(client.lastUrl.contains("page=1"))
        assertEquals("456", result.threads.single().id)
    }

    private class RecordingClient(private val response: String) : FeedflowHttpClient {
        var lastUrl: String = ""
        override suspend fun get(url: String, cookies: List<FeedflowCookie>): String {
            lastUrl = url
            return response
        }

        override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String =
            error("not used")
    }

    private companion object {
        const val HACKER_NEWS_SEARCH_FIXTURE = """
            {
              "hits": [
                {
                  "created_at_i": 1700000000,
                  "title": "Android parity details",
                  "url": "https://example.com/android",
                  "author": "feedflow",
                  "points": 10,
                  "num_comments": 3,
                  "objectID": "456"
                }
              ],
              "page": 1,
              "nbPages": 2
            }
        """
    }
}
