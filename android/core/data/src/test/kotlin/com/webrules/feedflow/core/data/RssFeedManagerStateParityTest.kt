package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.RssFeedSubscription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RssFeedManagerStateParityTest {
    @Test fun manualAddUsesUrlAsNameWhenNameBlankAndRejectsBlankUrl() {
        val rejected = RssFeedManagerState().addManual("", "  ", 1L)
        assertEquals("Feed URL is required", rejected.errorMessage)
        assertEquals(emptyList(), rejected.feeds)

        val added = RssFeedManagerState().addManual("", " https://example.com/feed.xml ", 2L)
        assertEquals("https://example.com/feed.xml", added.feeds.single().title)
        assertEquals("https://example.com/feed.xml", added.feeds.single().url)
    }

    @Test fun editModeSelectionAndDeleteMatchManagerBehavior() {
        val state = RssFeedManagerState(
            feeds = listOf(
                RssFeedSubscription("a", "A", false, 0),
                RssFeedSubscription("b", "B", false, 0),
            ),
        ).toggleEdit().toggleSelection("a")

        assertTrue(state.editMode)
        assertEquals(setOf("a"), state.selectedUrls)
        val deleted = state.deleteSelected()
        assertEquals(listOf("b"), deleted.feeds.map { it.url })
        assertTrue(deleted.selectedUrls.isEmpty())
        assertTrue(deleted.editMode)
        assertFalse(deleted.toggleSelection("b").deleteSelected().editMode)
    }

    @Test fun opmlPreviewImportsSelectedFeedsAndReportsEmptyFiles() {
        val opml = """
            <opml><body>
              <outline text="One" xmlUrl="https://one.example/rss.xml"/>
              <outline title="Two" xmlUrl="https://two.example/rss.xml"/>
            </body></opml>
        """.trimIndent().toByteArray()
        val preview = RssFeedManagerState().previewOpml(opml, 3L)
        assertEquals(listOf("One", "Two"), preview.importPreview.map { it.title })

        val imported = preview.importPreview(setOf("https://two.example/rss.xml"))
        assertEquals(listOf("Two"), imported.feeds.map { it.title })
        assertTrue(imported.importPreview.isEmpty())

        val empty = RssFeedManagerState().previewOpml("<opml/>".toByteArray(), 0)
        assertEquals("No feeds found", empty.errorMessage)
    }
}
