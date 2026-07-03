package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.network.FeedflowHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AiTtsPrefetchParityTest {
    private val thread = FeedThread(
        id = "t1",
        title = "Title",
        content = "Content",
        author = User("u1", "alice", ""),
        community = Community("c", "Community", "", "", 0, 0),
        timeAgo = "now",
        likeCount = 0,
        commentCount = 0,
    )

    @Test fun aiSummaryUsesLanguageSpecificCacheUnlessForced() {
        val store = InMemoryFeedflowStore()
        val coordinator = AiSummaryCoordinator(store)
        val key = FeedflowCacheKeys.summary(thread.id, "v2ex", "en").first
        store.saveSummary(key, "v2ex", "cached")

        val cached = coordinator.summarize(thread, "v2ex", "en", emptyList()) { error("should not call generator") }
        assertEquals("cached", cached.summary)
        assertTrue(cached.isCached)

        val fresh = coordinator.summarize(thread, "v2ex", "en", emptyList(), forceRefresh = true) { "fresh" }
        assertEquals("fresh", fresh.summary)
        assertFalse(fresh.isCached)
    }

    @Test fun aiSummaryBuildsPromptFromFirstTwentyFiveCommentsAndReportsErrors() {
        val coordinator = AiSummaryCoordinator(InMemoryFeedflowStore())
        val comments = (1..30).map { index -> Comment("c$index", User("$index", "u$index", ""), "comment $index", "now", 0) }
        val prompt = coordinator.buildPrompt(thread, comments, "zh")
        assertTrue(prompt.contains("Respond in Simplified Chinese."))
        assertTrue(prompt.contains("25. u25: comment 25"))
        assertFalse(prompt.contains("30. u30"))

        val error = coordinator.summarize(thread, "v2ex", "zh", emptyList(), forceRefresh = true) { error("bad key") }
        assertNotNull(error.errorMessage)
    }

    @Test fun geminiRestClientPostsJsonPromptAndParsesFirstCandidateText() = runBlocking {
        val http = RecordingSummaryHttpClient("""{"candidates":[{"content":{"parts":[{"text":"Line 1\nLine 2"}]}}]}""")
        val client = GeminiRestSummaryClient(http, modelName = "gemini-test")

        val summary = client.generateSummary("api-key", "Quote \"this\"\n中文")

        assertEquals("Line 1\nLine 2", summary)
        assertTrue(http.lastUrl.contains("/models/gemini-test:generateContent?key=api-key"))
        assertEquals("application/json; charset=UTF-8", http.lastContentType)
        assertTrue(http.lastBody.contains("""Quote \"this\"\n中文"""))
    }

    @Test fun repositoryThreadSummaryUsesCachedSummaryBeforeCallingGemini() = runBlocking {
        val store = InMemoryFeedflowStore()
        val cacheKey = FeedflowCacheKeys.summary(thread.id, ForumSite.V2ex.serviceId, "en").first
        store.saveSummary(cacheKey, ForumSite.V2ex.serviceId, "cached")
        val client = RecordingGeminiSummaryClient()
        val repository = FeedflowRepository(store = store, summaryClient = client)

        val cached = repository.summarizeThread("api-key", ForumSite.V2ex, thread, emptyList(), "en")
        val fresh = repository.summarizeThread("api-key", ForumSite.V2ex, thread, emptyList(), "en", forceRefresh = true)

        assertEquals("cached", cached.summary)
        assertEquals("generated:api-key", fresh.summary)
        assertEquals(1, client.calls)
    }

    @Test fun speechPlaybackStartsAndStopsWithLanguage() {
        val speaking = SpeechPlaybackState().speak("summary", "zh")
        assertTrue(speaking.isSpeaking)
        assertEquals("summary", speaking.spokenText)
        assertEquals("zh", speaking.language)
        assertFalse(speaking.stop().isSpeaking)
    }

    @Test fun speechSynthesisConfigMatchesIosVoiceAndUtteranceDefaults() {
        val zh = SpeechSynthesisConfig.forLanguage("zh")
        val en = SpeechSynthesisConfig.forLanguage("en")

        assertEquals("zh-CN", zh.voiceLanguageTag)
        assertEquals("en-US", en.voiceLanguageTag)
        assertEquals(0.5f, zh.speechRate)
        assertEquals(1.0f, zh.pitch)
    }

    @Test fun prefetchGateMatchesPreferenceNetworkSourceCacheAndQueueRules() {
        val base = PrefetchDecisionInput(enabled = true, isWifi = true, site = ForumSite.V2ex, detailCached = false, queueSize = 0)
        assertTrue(PrefetchGate.shouldPrefetch(base))
        assertFalse(PrefetchGate.shouldPrefetch(base.copy(enabled = false)))
        assertFalse(PrefetchGate.shouldPrefetch(base.copy(isWifi = false)))
        assertFalse(PrefetchGate.shouldPrefetch(base.copy(site = ForumSite.Zhihu)))
        assertFalse(PrefetchGate.shouldPrefetch(base.copy(detailCached = true)))
        assertFalse(PrefetchGate.shouldPrefetch(base.copy(queueSize = 6)))
    }

    private class RecordingGeminiSummaryClient : GeminiSummaryClient {
        var calls = 0
        override suspend fun generateSummary(apiKey: String, prompt: String): String {
            calls += 1
            assertTrue(prompt.contains("Respond in English."))
            return "generated:$apiKey"
        }
    }

    private class RecordingSummaryHttpClient(private val response: String) : FeedflowHttpClient {
        var lastUrl: String = ""
        var lastBody: String = ""
        var lastContentType: String = ""

        override suspend fun get(url: String, cookies: List<FeedflowCookie>): String =
            error("not used")

        override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String {
            lastUrl = url
            lastBody = body
            lastContentType = contentType
            return response
        }
    }
}
