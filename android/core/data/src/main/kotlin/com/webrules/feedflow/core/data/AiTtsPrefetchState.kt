package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.FeedflowHttpClient
import com.webrules.feedflow.core.network.UrlConnectionFeedflowHttpClient

data class AiSummaryUiState(
    val isLoading: Boolean = false,
    val summary: String? = null,
    val isCached: Boolean = false,
    val errorMessage: String? = null,
)

class AiSummaryCoordinator(
    private val store: FeedflowStore,
) {
    fun cachedSummary(threadId: String, serviceId: String, language: String): AiSummaryUiState? {
        val cacheKey = FeedflowCacheKeys.summary(threadId, serviceId, language).first
        return store.getSummary(cacheKey, serviceId)?.let { AiSummaryUiState(summary = it, isCached = true) }
    }

    fun summarize(
        thread: FeedThread,
        serviceId: String,
        language: String,
        comments: List<Comment>,
        forceRefresh: Boolean = false,
        generator: (String) -> String,
    ): AiSummaryUiState {
        if (!forceRefresh) cachedSummary(thread.id, serviceId, language)?.let { return it }
        return runCatching {
            val prompt = buildPrompt(thread, comments, language)
            generator(prompt).also { summary ->
                val cacheKey = FeedflowCacheKeys.summary(thread.id, serviceId, language).first
                store.saveSummary(cacheKey, serviceId, summary)
            }
        }.fold(
            onSuccess = { AiSummaryUiState(summary = it, isCached = false) },
            onFailure = { AiSummaryUiState(errorMessage = it.message ?: "failed_summary") },
        )
    }

    suspend fun summarizeAsync(
        thread: FeedThread,
        serviceId: String,
        language: String,
        comments: List<Comment>,
        forceRefresh: Boolean = false,
        generator: suspend (String) -> String,
    ): AiSummaryUiState {
        if (!forceRefresh) cachedSummary(thread.id, serviceId, language)?.let { return it }
        return runCatching {
            val prompt = buildPrompt(thread, comments, language)
            generator(prompt).also { summary ->
                val cacheKey = FeedflowCacheKeys.summary(thread.id, serviceId, language).first
                store.saveSummary(cacheKey, serviceId, summary)
            }
        }.fold(
            onSuccess = { AiSummaryUiState(summary = it, isCached = false) },
            onFailure = { AiSummaryUiState(errorMessage = it.message ?: "failed_summary") },
        )
    }

    fun buildPrompt(thread: FeedThread, comments: List<Comment>, language: String): String =
        buildString {
            appendLine("You are a helpful assistant. Please summarize the following forum discussion clearly and concisely.")
            appendLine("Identify the main topic, key arguments or points made, and the general sentiment if applicable.")
            appendLine(if (language == "zh") "Respond in Simplified Chinese." else "Respond in English.")
            appendLine()
            appendLine("Content:")
            appendLine("Title: ${thread.title}")
            appendLine(thread.content.take(10_000))
            appendLine("Comments:")
            comments.take(25).forEachIndexed { index, comment ->
                appendLine("${index + 1}. ${comment.author.username}: ${comment.content}")
            }
        }
}

interface GeminiSummaryClient {
    suspend fun generateSummary(apiKey: String, prompt: String): String
}

class GeminiRestSummaryClient(
    private val httpClient: FeedflowHttpClient = UrlConnectionFeedflowHttpClient(),
    private val modelName: String = "gemini-pro-latest",
) : GeminiSummaryClient {
    override suspend fun generateSummary(apiKey: String, prompt: String): String {
        if (apiKey.isBlank()) return "Please set your Gemini API Key in Settings (top left icon on home page)."
        val body = """{"contents":[{"parts":[{"text":${prompt.jsonString()}}]}]}"""
        val response = httpClient.post(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey",
            body = body,
            contentType = "application/json; charset=UTF-8",
        )
        return parseGeminiText(response) ?: "Unable to generate summary."
    }

    private fun parseGeminiText(response: String): String? {
        val text = Regex(""""text"\s*:\s*"((?:\\.|[^"\\])*)"""").find(response)?.groupValues?.get(1)
        return text?.jsonStringUnescape()?.takeIf { it.isNotBlank() }
    }
}

private fun String.jsonString(): String =
    buildString {
        append('"')
        this@jsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private fun String.jsonStringUnescape(): String {
    val builder = StringBuilder()
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '\\' && index + 1 < length) {
            when (val next = this[index + 1]) {
                '"' -> builder.append('"')
                '\\' -> builder.append('\\')
                '/' -> builder.append('/')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    val hex = substring(index + 2, (index + 6).coerceAtMost(length))
                    if (hex.length == 4) {
                        hex.toIntOrNull(16)?.let { builder.append(it.toChar()) }
                        index += 4
                    }
                }
                else -> builder.append(next)
            }
            index += 2
        } else {
            builder.append(char)
            index += 1
        }
    }
    return builder.toString()
}

data class SpeechPlaybackState(
    val isSpeaking: Boolean = false,
    val spokenText: String = "",
    val language: String = "en",
) {
    fun speak(text: String, language: String): SpeechPlaybackState =
        copy(isSpeaking = text.isNotBlank(), spokenText = text, language = language)

    fun stop(): SpeechPlaybackState = copy(isSpeaking = false, spokenText = "")
}

data class PrefetchDecisionInput(
    val enabled: Boolean,
    val isWifi: Boolean,
    val site: ForumSite,
    val detailCached: Boolean,
    val queueSize: Int,
    val maxQueueSize: Int = 6,
)

object PrefetchGate {
    private val allowlisted = setOf(ForumSite.Rss, ForumSite.HackerNews, ForumSite.V2ex, ForumSite.LinuxDo)

    fun shouldPrefetch(input: PrefetchDecisionInput): Boolean =
        input.enabled &&
            input.isWifi &&
            allowlisted.contains(input.site) &&
            !input.detailCached &&
            input.queueSize < input.maxQueueSize
}
