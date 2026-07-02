package com.webrules.feedflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.webrules.feedflow.core.data.FeedflowRepository
import com.webrules.feedflow.core.data.ForumService
import com.webrules.feedflow.core.data.SearchResult
import com.webrules.feedflow.core.data.ThreadDetailResult
import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.ui.FeedflowApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FeedflowUiParitySmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private val store = InMemoryFeedflowStore()

    @Before fun setUp() {
        val repository = FeedflowRepository(
            store = store,
            serviceFactory = { site -> FixtureForumService(site) },
        )
        compose.setContent {
            FeedflowApp(repositoryOverride = repository, storeOverride = store)
        }
    }

    @Test fun homeRendersAllDefaultSitesAndToolbarUtilities() {
        compose.onNodeWithText("Select a Community").performScrollTo().assertIsDisplayed()
        listOf("RSS Feeds", "Hacker News", "4D4Y", "V2EX", "Linux.do", "知乎").forEach { label ->
            compose.onNodeWithContentDescription("Site $label").performScrollTo().assertIsDisplayed()
        }
        listOf("Login", "Settings", "Bookmarks", "AI", "Communities", "RSS", "Theme").forEach { description ->
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        compose.onNodeWithText("EN").assertIsDisplayed()
    }

    @Test fun loginSettingsAndBookmarksRoutesRenderModalContent() {
        compose.onNodeWithContentDescription("Login").performClick()
        compose.onNodeWithText("Web Login").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("GEMINI API KEY").assertIsDisplayed()
        compose.onNodeWithText("READING").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.onNodeWithText("No bookmarks yet").performScrollTo().assertIsDisplayed()
    }

    @Test fun threadNavigationRendersListDetailAndAiSummaryEntryPoint() {
        compose.onNodeWithContentDescription("Site Hacker News").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Community row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Community row")[0].performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithContentDescription("Thread row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Thread row")[0].performClick()
        compose.onNodeWithText("Fixture comment.").assertIsDisplayed()
        compose.onNodeWithContentDescription("AI Summary").performClick()
        compose.onNodeWithText("Failed summary").assertIsDisplayed()
    }

    @Test fun homeAiOpensCrossSiteTopTen() {
        compose.onNodeWithContentDescription("AI").performClick()
        compose.onNodeWithText("AI Cross-Site Top 10").assertIsDisplayed()
        compose.onNodeWithText("Hacker News").assertIsDisplayed()
    }

    private class FixtureForumService(private val site: ForumSite) : ForumService {
        private val community = Community("topstories", "Top Stories", "Fixture top stories", "General", 12, 3)
        private val author = User("fixture-user", "Feedflow", "person.circle")
        private val thread = FeedThread(
            id = "${site.serviceId}-thread-1",
            title = "${site.displayName} fixture topic",
            content = "Fixture content for ${site.displayName}.",
            author = author,
            community = community,
            timeAgo = "now",
            likeCount = 1,
            commentCount = 1,
        )

        override val name: String = site.displayName
        override val id: String = site.serviceId
        override val logo: String = site.icon
        override val requiresLogin: Boolean = false

        override suspend fun fetchCategories(): List<Community> = listOf(community)

        override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> =
            listOf(thread.copy(community = communities.firstOrNull { it.id == categoryId } ?: community))

        override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult =
            ThreadDetailResult(
                thread = thread,
                comments = listOf(Comment("comment-1", author, "Fixture comment.", "now", 0)),
                totalPages = 1,
            )

        override suspend fun searchThreads(query: String, page: Int): SearchResult = SearchResult(listOf(thread), false)

        override fun getWebUrl(thread: FeedThread): String = "https://example.com/${site.serviceId}/${thread.id}"
    }
}
