package com.webrules.feedflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.espresso.Espresso
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
import org.junit.Assert.assertTrue
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
        listOf("Login", "Settings", "Bookmarks", "AI", "Communities", "Theme").forEach { description ->
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        compose.onNodeWithText("EN").assertIsDisplayed()
    }

    @Test fun rssCommunitiesExposeFeedManagerAndDailySummaryActions() {
        compose.onNodeWithContentDescription("Site RSS Feeds").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Community row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Daily Summary").assertIsDisplayed()
        compose.onNodeWithContentDescription("Manage Feeds").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh").assertIsDisplayed()
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
        listOf("Theme", "AI Summary", "Bookmark", "Select a Community").forEach { description ->
            assertTrue(description, compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty())
        }
        compose.onNodeWithContentDescription("AI Summary").performClick()
        compose.onNodeWithText("Failed summary").assertIsDisplayed()
    }

    @Test fun homeAiOpensCrossSiteTopTen() {
        compose.onNodeWithContentDescription("AI").performClick()
        compose.onNodeWithText("AI Cross-Site Top 10").assertIsDisplayed()
        compose.onNodeWithText("Hacker News").assertIsDisplayed()
    }

    @Test fun zhihuNotInterestedRequiresRecommendationLongPress() {
        compose.onNodeWithContentDescription("Site 知乎").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Community row").fetchSemanticsNodes().size == 2
        }

        compose.onAllNodesWithContentDescription("Community row")[1].performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Thread row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Not interested").assertDoesNotExist()
        compose.onNodeWithText("热").assertDoesNotExist()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Community row").fetchSemanticsNodes().size == 2
        }
        compose.onAllNodesWithContentDescription("Community row")[0].performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Thread row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Not interested").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Thread row")[0].performTouchInput { longClick() }
        compose.onNodeWithText("Not interested").assertIsDisplayed()
    }

    @Test fun systemBackReturnsToListAndPinsSelectedThreadAtTop() {
        compose.onNodeWithContentDescription("Site Hacker News").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Community row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Community row")[0].performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Thread row").fetchSemanticsNodes().size > 3
        }

        val selectedTitle = "Hacker News fixture topic 4"
        compose.onNodeWithText(selectedTitle).performClick()
        compose.onNodeWithText("Fixture comment.").assertIsDisplayed()

        Espresso.pressBack()

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(selectedTitle).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(selectedTitle).assertIsDisplayed()
        compose.onNodeWithText("Hacker News fixture topic 1").assertDoesNotExist()
    }

    @Test fun searchLoginButtonOpensLoginAndReturnsToSearch() {
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("android")
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Login required").assertIsDisplayed()

        compose.onNodeWithText("Web Login").performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Save Session").assertIsDisplayed()

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Search results").assertIsDisplayed()
        compose.onNodeWithText("Web Login").assertIsDisplayed()
    }

    private class FixtureForumService(private val site: ForumSite) : ForumService {
        private val communities = if (site == ForumSite.Zhihu) {
            listOf(
                Community("recommend", "Recommendations", "Fixture recommendations", "Zhihu", 12, 3),
                Community("hot", "Hot", "Fixture hot list", "Zhihu", 12, 3),
            )
        } else {
            listOf(Community("topstories", "Top Stories", "Fixture top stories", "General", 12, 3))
        }
        private val community = communities.first()
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

        override suspend fun fetchCategories(): List<Community> = communities

        override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
            val selectedCommunity = communities.firstOrNull { it.id == categoryId } ?: community
            val selectedAuthor = if (site == ForumSite.Zhihu && categoryId == "hot") {
                User("", "热榜", "")
            } else {
                author
            }
            return (1..12).map { index ->
                thread.copy(
                    id = "${site.serviceId}-thread-$index",
                    title = "${site.displayName} fixture topic $index",
                    author = selectedAuthor,
                    community = selectedCommunity,
                )
            }
        }

        override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
            val index = threadId.substringAfterLast("-").toIntOrNull() ?: 1
            return ThreadDetailResult(
                thread = thread.copy(id = threadId, title = "${site.displayName} fixture topic $index"),
                comments = listOf(Comment("comment-1", author, "Fixture comment.", "now", 0)),
                totalPages = 1,
            )
        }

        override suspend fun searchThreads(query: String, page: Int): SearchResult = SearchResult(listOf(thread), false)

        override fun getWebUrl(thread: FeedThread): String = "https://example.com/${site.serviceId}/${thread.id}"
    }
}
