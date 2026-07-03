package com.webrules.feedflow.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostingStateParityTest {
    @Test fun newThreadComposerValidatesTitleContentAndPostingState() {
        assertFalse(NewThreadComposerState().canPost)
        assertFalse(NewThreadComposerState(title = "Title", content = "").canPost)
        assertFalse(NewThreadComposerState(title = "", content = "Body").canPost)
        assertFalse(NewThreadComposerState(title = "Title", content = "Body", isPosting = true).canPost)
        assertTrue(NewThreadComposerState(title = "Title", content = "Body").canPost)
    }

    @Test fun newThreadComposerCountsWordsLikeEditorFooter() {
        assertEquals(0, NewThreadComposerState(content = "  ").wordCount)
        assertEquals(3, NewThreadComposerState(content = "one two\nthree").wordCount)
    }

    @Test fun replyComposerFormatsOptionalQuoteTarget() {
        assertEquals("Plain reply", ReplyComposerState(content = " Plain reply ").formattedContent())
        assertEquals(
            "[quote][b]alice said:[/b]\nOriginal comment[/quote]\n\nThanks",
            ReplyComposerState(
                content = "Thanks",
                replyingToCommentId = "c1",
                replyingToUsername = "alice",
                replyingToContent = "Original comment",
            ).formattedContent(),
        )
        assertEquals(
            "[quote][b]alice 说:[/b]\nOriginal comment[/quote]\n\nThanks",
            ReplyComposerState(
                content = "Thanks",
                replyingToUsername = "alice",
                replyingToContent = "Original comment",
            ).formattedContent("说"),
        )
        assertFalse(ReplyComposerState(content = "", replyingToUsername = "alice").canReply)
        assertTrue(ReplyComposerState(content = "ok", replyingToUsername = "alice").canReply)
    }

    @Test fun formattingToolbarAppliesMarkdownAndFeedflowLinkMarkers() {
        assertEquals("**bold**", FormattingToolbar.bold("bold"))
        assertEquals("*italic*", FormattingToolbar.italic("italic"))
        assertEquals("[LINK:https://example.com|Example]", FormattingToolbar.link("Example", "https://example.com"))
        assertEquals("- a\n- b", FormattingToolbar.bullet("a\nb"))
    }
}
