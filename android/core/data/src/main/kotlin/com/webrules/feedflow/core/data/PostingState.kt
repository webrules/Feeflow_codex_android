package com.webrules.feedflow.core.data

data class NewThreadComposerState(
    val title: String = "",
    val content: String = "",
    val isPosting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canPost: Boolean
        get() = title.isNotBlank() && content.isNotBlank() && !isPosting

    val wordCount: Int
        get() = content.split(Regex("""\s+""")).count { it.isNotBlank() }
}

data class ReplyComposerState(
    val content: String = "",
    val replyingToCommentId: String? = null,
    val replyingToUsername: String? = null,
    val replyingToContent: String? = null,
    val isPosting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canReply: Boolean
        get() = content.isNotBlank() && !isPosting

    fun formattedContent(saidLabel: String = "said"): String =
        if (replyingToUsername.isNullOrBlank()) {
            content.trim()
        } else {
            "[quote][b]${replyingToUsername} $saidLabel:[/b]\n${replyingToContent.orEmpty()}[/quote]\n\n${content.trim()}"
        }
}

object FormattingToolbar {
    fun bold(selection: String): String = wrap(selection, "**")
    fun italic(selection: String): String = wrap(selection, "*")
    fun link(title: String, url: String): String = "[LINK:$url|${title.ifBlank { url }}]"
    fun bullet(lines: String): String =
        lines.lineSequence().joinToString("\n") { line -> if (line.isBlank()) line else "- $line" }

    private fun wrap(selection: String, marker: String): String = "$marker$selection$marker"
}
