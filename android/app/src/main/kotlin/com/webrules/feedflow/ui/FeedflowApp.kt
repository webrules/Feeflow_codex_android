package com.webrules.feedflow.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Looks4
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.webrules.feedflow.R
import com.webrules.feedflow.auth.AndroidWebLoginCookieBridge
import com.webrules.feedflow.core.data.AiSummaryUiState
import com.webrules.feedflow.core.data.AuthSessionCoordinator
import java.time.Duration
import com.webrules.feedflow.core.data.AvatarRenderingPolicy
import com.webrules.feedflow.core.data.ContentBlock
import com.webrules.feedflow.core.data.FeedflowRepository
import com.webrules.feedflow.core.data.FeedflowContentRenderer
import com.webrules.feedflow.core.data.FeedflowAppStateController
import com.webrules.feedflow.core.data.FeedflowError
import com.webrules.feedflow.core.data.FeedflowHomeState
import com.webrules.feedflow.core.data.FormattingToolbar
import com.webrules.feedflow.core.data.LoadableContent
import com.webrules.feedflow.core.data.LinkSegment
import com.webrules.feedflow.core.data.LoginCaptureResult
import com.webrules.feedflow.core.data.NewThreadComposerState
import com.webrules.feedflow.core.data.ReplyComposerState
import com.webrules.feedflow.core.data.RssFeedManagerState
import com.webrules.feedflow.core.data.SearchResult
import com.webrules.feedflow.core.data.SiteLoginConfig
import com.webrules.feedflow.core.data.SpeechPlaybackState
import com.webrules.feedflow.core.data.SpeechSynthesisConfig
import com.webrules.feedflow.core.data.ThreadRowRenderingPolicy
import com.webrules.feedflow.core.data.UrlBookmarkRelativeTime
import com.webrules.feedflow.core.database.FeedflowDatabaseContract
import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.database.RssFeedSubscription
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.ui.FeedflowTheme
import com.webrules.feedflow.persistence.AndroidSqliteFeedflowStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Locale

private sealed interface FeedflowRoute {
    data object SiteList : FeedflowRoute
    data class Communities(val site: ForumSite) : FeedflowRoute
    data class Threads(val site: ForumSite, val community: Community) : FeedflowRoute
    data class Detail(val site: ForumSite, val thread: FeedThread, val contextThreads: List<FeedThread> = emptyList()) : FeedflowRoute
    data object Login : FeedflowRoute
    data object Settings : FeedflowRoute
    data object Bookmarks : FeedflowRoute
    data object RssManager : FeedflowRoute
    data object DailyRssSummary : FeedflowRoute
    data object CommunityConfig : FeedflowRoute
    data object CrossSiteAi : FeedflowRoute
    data class AiSummary(val site: ForumSite, val thread: FeedThread, val comments: List<Comment>) : FeedflowRoute
    data class SearchResults(val site: ForumSite, val query: String) : FeedflowRoute
    data class WebLogin(val site: ForumSite) : FeedflowRoute
    data class Browser(val url: String, val title: String) : FeedflowRoute
    data class ImageViewer(val url: String) : FeedflowRoute
    data class NewThread(val site: ForumSite, val community: Community) : FeedflowRoute
}

private object FeedflowIconMap {
    fun siteIcon(site: ForumSite): ImageVector = when (site) {
        ForumSite.Rss -> Icons.Default.RssFeed
        ForumSite.HackerNews -> Icons.Default.LocalFireDepartment
        ForumSite.FourD4Y -> Icons.Default.Looks4
        ForumSite.V2ex -> Icons.Default.Hub
        ForumSite.LinuxDo -> Icons.Default.Terminal
        ForumSite.Zhihu -> Icons.Default.QuestionAnswer
    }

    fun symbol(name: String): ImageVector = when (name) {
        "plus" -> Icons.Default.Add
        "plus.circle.fill" -> Icons.Default.AddCircle
        "sparkles" -> Icons.Default.AutoAwesome
        "chevron.left" -> Icons.Default.ArrowBack
        "chevron.up" -> Icons.Default.KeyboardArrowUp
        "chevron.down" -> Icons.Default.KeyboardArrowDown
        "bookmark" -> Icons.Default.BookmarkBorder
        "bookmark.fill" -> Icons.Default.Bookmark
        "safari.fill", "globe" -> Icons.Default.Public
        "xmark.circle.fill" -> Icons.Default.Close
        "bubble.left.and.bubble.right.fill" -> Icons.Default.ChatBubble
        "square.grid.2x2.fill" -> Icons.Default.GridView
        "square.and.pencil" -> Icons.Default.Edit
        "dot.radiowaves.left.and.right" -> Icons.Default.RssFeed
        "list.bullet.rectangle.portrait.fill" -> Icons.Default.FormatListBulleted
        "chevron.right" -> Icons.Default.KeyboardArrowRight
        "house.fill" -> Icons.Default.Home
        "stop.fill" -> Icons.Default.Stop
        "speaker.wave.2.fill" -> Icons.Default.VolumeUp
        "arrow.up.forward.app" -> Icons.Default.OpenInNew
        "tray.and.arrow.down.fill" -> Icons.Default.FileDownload
        "link.circle.fill" -> Icons.Default.Link
        "person.fill" -> Icons.Default.Person
        "ellipsis.circle.fill" -> Icons.Default.MoreHoriz
        "arrow.triangle.2.circlepath" -> Icons.Default.Refresh
        "slider.horizontal.3" -> Icons.Default.Tune
        "square.and.arrow.up" -> Icons.Default.Share
        "sparkles.rectangle.stack.fill" -> Icons.Default.AutoAwesome
        "circle.lefthalf.filled" -> Icons.Default.Palette
        "trash.fill" -> Icons.Default.Delete
        "tray" -> Icons.Default.Inbox
        "image" -> Icons.Default.Image
        "hand.thumbsup" -> Icons.Default.ThumbUp
        "paperplane.fill" -> Icons.Default.Send
        "flame.fill" -> Icons.Default.LocalFireDepartment
        "point.3.connected.trianglepath.dotted" -> Icons.Default.Hub
        "terminal.fill" -> Icons.Default.Terminal
        "4.circle.fill" -> Icons.Default.Looks4
        "questionmark.bubble.fill" -> Icons.Default.QuestionAnswer
        else -> Icons.Default.Public
    }
}

private const val BackgroundPrefetchSettingKey = "background_prefetch"
private const val DarkThemeSettingKey = "dark_theme"
private const val LanguageSettingKey = "language"

private class AndroidTtsController(
    context: android.content.Context,
    private val onFinished: () -> Unit,
) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = onFinished()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onFinished()
                override fun onStop(utteranceId: String?, interrupted: Boolean) = onFinished()
            })
        }
    }

    fun speak(text: String, language: String): Boolean {
        if (!ready || text.isBlank()) return false
        val config = SpeechSynthesisConfig.forLanguage(language)
        tts.language = Locale.forLanguageTag(config.voiceLanguageTag)
        tts.setSpeechRate(config.speechRate)
        tts.setPitch(config.pitch)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "feedflow-ai-summary") == TextToSpeech.SUCCESS
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

@Composable
fun FeedflowApp(repositoryOverride: FeedflowRepository? = null, storeOverride: FeedflowStore? = null) {
    val context = LocalContext.current
    val store = remember(context, storeOverride) { storeOverride ?: AndroidSqliteFeedflowStore(context) }
    val repository = remember(store, repositoryOverride) { repositoryOverride ?: FeedflowRepository(store = store) }
    val initialHomeState = remember(store) {
        val defaults = FeedflowHomeState()
        FeedflowHomeState(
            signedInSites = ForumSite.entries.filter { site ->
                SiteLoginConfig.forSite(site)?.hasAuthenticatedSession(store.getCookies(site.serviceId).orEmpty()) == true
            }.toSet(),
            geminiApiKey = store.getEncryptedSetting(FeedflowDatabaseContract.geminiApiKey).orEmpty(),
            backgroundPrefetch = store.getSetting(BackgroundPrefetchSettingKey)?.toBooleanStrictOrNull() ?: true,
            rssFeeds = store.getRssFeeds().ifEmpty { defaults.rssFeeds },
        )
    }
    val appStateController = remember(repository, initialHomeState) { FeedflowAppStateController(repository, initialHomeState) }
    val authCoordinator = remember(store) { AuthSessionCoordinator(store) }
    val cookieBridge = remember { AndroidWebLoginCookieBridge() }
    val networkMonitor = remember(context) { com.webrules.feedflow.net.NetworkMonitor(context) }
    val networkStatus by produceState(initialValue = networkMonitor.current(), networkMonitor) {
        networkMonitor.statusFlow().collect { value = it }
    }
    val appScope = rememberCoroutineScope()
    var route by remember { mutableStateOf<FeedflowRoute>(FeedflowRoute.SiteList) }
    var homeState by remember { mutableStateOf(appStateController.homeState) }
    var bookmarkRevision by remember { mutableStateOf(0) }
    var filterRevision by remember { mutableStateOf(0) }
    var darkTheme by remember { mutableStateOf(store.getSetting(DarkThemeSettingKey)?.toBooleanStrictOrNull() ?: false) }
    var language by remember { mutableStateOf(store.getSetting(LanguageSettingKey) ?: "en") }
    val localizedContext = remember(context, language) {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(if (language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.US)
        context.createConfigurationContext(configuration)
    }
    FeedflowTheme(darkTheme = darkTheme) {
    FeedflowSystemBars(darkTheme)
    CompositionLocalProvider(LocalContext provides localizedContext) {
    when (val current = route) {
        FeedflowRoute.SiteList -> SiteListScreen(
            sites = homeState.visibleSites,
            language = language,
            onSiteClick = { route = FeedflowRoute.Communities(it) },
            onLogin = { route = FeedflowRoute.Login },
            onSettings = { route = FeedflowRoute.Settings },
            onBookmarks = { route = FeedflowRoute.Bookmarks },
            onAi = { route = FeedflowRoute.CrossSiteAi },
            onCommunityConfig = { route = FeedflowRoute.CommunityConfig },
            onRssManager = { route = FeedflowRoute.RssManager },
            onSearch = { site, query -> route = FeedflowRoute.SearchResults(site, query) },
            onTheme = {
                darkTheme = !darkTheme
                store.saveSetting(DarkThemeSettingKey, darkTheme.toString())
            },
            onLanguage = {
                language = if (language == "en") "zh" else "en"
                store.saveSetting(LanguageSettingKey, language)
            },
        )
        is FeedflowRoute.Communities -> {
            var content by remember(current.site) { mutableStateOf(appStateController.communities(current.site)) }
            LaunchedEffect(current.site) {
                content = content.copy(isLoading = true)
                content = appStateController.refreshCommunities(current.site)
            }

            CommunitiesScreen(
                site = current.site,
                communities = content.value,
                isLoading = content.isLoading,
                warning = content.warning,
                onBack = { route = FeedflowRoute.SiteList },
                onCommunityClick = { route = FeedflowRoute.Threads(current.site, it) },
            )
        }
        is FeedflowRoute.Threads -> {
            var content by remember(current.site, current.community.id) {
                mutableStateOf(appStateController.threads(current.site, current.community))
            }
            var refreshToken by remember(current.site, current.community.id) { mutableStateOf(0) }
            LaunchedEffect(current.site, current.community.id, refreshToken) {
                content = content.copy(isLoading = true)
                content = appStateController.refreshThreads(current.site, current.community)
            }
            LaunchedEffect(current.site, current.community.id, content.value, networkStatus, homeState.backgroundPrefetch) {
                if (content.value.isNotEmpty()) {
                    appStateController.prefetchThreadDetails(
                        site = current.site,
                        threads = content.value.take(6),
                        enabled = homeState.backgroundPrefetch,
                        isWifi = networkStatus.isWifi,
                    )
                }
            }
            val filteredPostIds = remember(filterRevision, current.site) {
                store.getFilteredPostIds(current.site.serviceId)
            }
            val visibleThreads = content.value.filterNot { thread ->
                filteredPostIds.contains(thread.id) || filteredPostIds.contains(thread.id.substringAfter("_", thread.id))
            }
            ThreadListScreen(
                site = current.site,
                community = current.community,
                threads = visibleThreads,
                isLoading = content.isLoading,
                warning = content.warning,
                loadedFromCache = content.loadedFromCache,
                onBack = { route = FeedflowRoute.Communities(current.site) },
                onHome = { route = FeedflowRoute.SiteList },
                onThreadClick = { route = FeedflowRoute.Detail(current.site, it, visibleThreads) },
                onNewThread = { route = FeedflowRoute.NewThread(current.site, current.community) },
                onRefresh = {
                    refreshToken += 1
                },
                onNotInterested = { thread ->
                    appScope.launch {
                        val error = appStateController.markThreadNotInterested(current.site, thread)
                        if (error == null) {
                            filterRevision += 1
                        }
                    }
                },
            )
        }
        is FeedflowRoute.Detail -> {
            var activeThread by remember(current.site, current.thread.id) { mutableStateOf(current.thread) }
            var content by remember(current.site, activeThread.id) {
                mutableStateOf(appStateController.detail(current.site, activeThread))
            }
            var refreshToken by remember(current.site, activeThread.id) { mutableStateOf(0) }
            var extraComments by remember(current.site, activeThread.id) { mutableStateOf(emptyList<Comment>()) }
            var commentPage by remember(current.site, activeThread.id) { mutableStateOf(1) }
            var canLoadMore by remember(current.site, activeThread.id) { mutableStateOf(false) }
            LaunchedEffect(current.site, activeThread.id, refreshToken) {
                content = content.copy(isLoading = true)
                if (current.site == ForumSite.Zhihu && activeThread.community.id == "recommend") {
                    appStateController.markThreadRead(current.site, activeThread)
                    filterRevision += 1
                }
                content = appStateController.refreshDetail(current.site, activeThread)
                extraComments = emptyList()
                commentPage = 1
                canLoadMore = appStateController.supportsCommentPagination(current.site) && content.value.comments.isNotEmpty()
            }
            val contextThreads = current.contextThreads
            val activeIndex = contextThreads.indexOfFirst { it.id == activeThread.id }
            ThreadDetailScreen(
                site = current.site,
                thread = content.value.thread,
                comments = content.value.comments + extraComments,
                isLoading = content.isLoading,
                warning = content.warning,
                loadedFromCache = content.loadedFromCache,
                webUrl = appStateController.webUrl(current.site, content.value.thread),
                hasPrevious = activeIndex > 0,
                hasNext = activeIndex >= 0 && activeIndex < contextThreads.size - 1,
                onPrevious = { if (activeIndex > 0) activeThread = contextThreads[activeIndex - 1] },
                onNext = { if (activeIndex >= 0 && activeIndex < contextThreads.size - 1) activeThread = contextThreads[activeIndex + 1] },
                canLoadMore = canLoadMore,
                onLoadMore = {
                    val nextPage = commentPage + 1
                    val more = appStateController.moreComments(current.site, content.value.thread, nextPage)
                    if (more.isEmpty()) {
                        canLoadMore = false
                    } else {
                        extraComments = extraComments + more
                        commentPage = nextPage
                    }
                },
                onBack = { route = FeedflowRoute.Threads(current.site, content.value.thread.community) },
                onHome = { route = FeedflowRoute.SiteList },
                onAiSummary = { route = FeedflowRoute.AiSummary(current.site, content.value.thread, content.value.comments) },
                onOpenBrowser = { route = FeedflowRoute.Browser(appStateController.webUrl(current.site, content.value.thread), content.value.thread.title) },
                onOpenImage = { url -> route = FeedflowRoute.ImageViewer(url) },
                onRefresh = { refreshToken += 1 },
                canDelete = appStateController.canDeleteThread(current.site, content.value.thread),
                onDelete = {
                    val error = appStateController.deleteThread(current.site, content.value.thread)
                    if (error == null) {
                        filterRevision += 1
                        route = FeedflowRoute.Threads(current.site, content.value.thread.community)
                    }
                    error
                },
                isBookmarked = store.isBookmarked(content.value.thread.id, current.site.serviceId),
                onToggleBookmark = {
                    store.toggleBookmark(content.value.thread, current.site.serviceId)
                    bookmarkRevision += 1
                },
                postController = appStateController,
            )
        }
        FeedflowRoute.Login -> LoginScreen(
            signedInSites = homeState.signedInSites,
            onLogout = {
                store.clearCookies(it.serviceId)
                appStateController.setSignedIn(it, false)
                homeState = appStateController.homeState
            },
            onWebLogin = { route = FeedflowRoute.WebLogin(it) },
            onClose = { route = FeedflowRoute.SiteList },
        )
        FeedflowRoute.Settings -> SettingsScreen(
            apiKey = homeState.geminiApiKey,
            backgroundPrefetch = homeState.backgroundPrefetch,
            onSave = { key, prefetch ->
                store.saveEncryptedSetting(FeedflowDatabaseContract.geminiApiKey, key)
                store.saveSetting(BackgroundPrefetchSettingKey, prefetch.toString())
                appStateController.saveSettings(key, prefetch)
                homeState = appStateController.homeState
                route = FeedflowRoute.SiteList
            },
            onCancel = { route = FeedflowRoute.SiteList },
        )
        FeedflowRoute.Bookmarks -> BookmarksScreen(
            threadBookmarks = remember(bookmarkRevision) { store.getBookmarkedThreads() },
            urlBookmarks = remember(bookmarkRevision) { store.getUrlBookmarks() },
            onClose = { route = FeedflowRoute.SiteList },
            onThreadClick = { thread, serviceId ->
                route = FeedflowRoute.Detail(ForumSite.fromServiceId(serviceId) ?: ForumSite.Rss, thread)
            },
            onUrlClick = { url, title -> route = FeedflowRoute.Browser(url, title) },
        )
        FeedflowRoute.RssManager -> RssFeedManagerScreen(
            feeds = homeState.rssFeeds,
            onFeedsChange = {
                store.replaceRssFeeds(it)
                appStateController.updateRssFeeds(it)
                homeState = appStateController.homeState
            },
            onDailySummary = { route = FeedflowRoute.DailyRssSummary },
            onClose = { route = FeedflowRoute.SiteList },
        )
        FeedflowRoute.DailyRssSummary -> DailyRssSummaryScreen(
            loader = appStateController,
            apiKey = homeState.geminiApiKey,
            language = language,
            onClose = { route = FeedflowRoute.RssManager },
        )
        FeedflowRoute.CommunityConfig -> CommunityConfigScreen(
            enabledSites = homeState.enabledSites,
            onToggle = { site ->
                appStateController.toggleSite(site)
                homeState = appStateController.homeState
            },
            onClose = { route = FeedflowRoute.SiteList },
        )
        FeedflowRoute.CrossSiteAi -> CrossSiteAiSummaryScreen(
            loader = appStateController,
            apiKey = homeState.geminiApiKey,
            language = language,
            onClose = { route = FeedflowRoute.SiteList },
            onThreadClick = { site, thread -> route = FeedflowRoute.Detail(site, thread) },
        )
        is FeedflowRoute.AiSummary -> AiSummaryScreen(
            site = current.site,
            thread = current.thread,
            apiKey = homeState.geminiApiKey,
            loader = appStateController,
            comments = current.comments,
            language = language,
            onClose = { route = FeedflowRoute.SiteList },
        )
        is FeedflowRoute.SearchResults -> SearchResultsScreen(
            site = current.site,
            query = current.query,
            requiresLogin = current.site.requiresLogin && !homeState.signedInSites.contains(current.site),
            loader = appStateController,
            onBack = { route = FeedflowRoute.SiteList },
            onHome = { route = FeedflowRoute.SiteList },
            onThreadClick = { route = FeedflowRoute.Detail(current.site, it) },
        )
        is FeedflowRoute.WebLogin -> WebLoginSheetScreen(
            site = current.site,
            authCoordinator = authCoordinator,
            cookieBridge = cookieBridge,
            onAccepted = {
                appStateController.setSignedIn(current.site, true)
                homeState = appStateController.homeState
                route = FeedflowRoute.Login
            },
            onClose = { route = FeedflowRoute.Login },
        )
        is FeedflowRoute.Browser -> InAppBrowserScreen(
            url = current.url,
            pageTitle = current.title,
            isBookmarked = store.isUrlBookmarked(current.url),
            onToggleBookmark = {
                if (store.isUrlBookmarked(current.url)) store.removeUrlBookmark(current.url) else store.saveUrlBookmark(current.url, current.title)
                bookmarkRevision += 1
            },
            onClose = { route = FeedflowRoute.SiteList },
        )
        is FeedflowRoute.ImageViewer -> FullScreenImageScreen(url = current.url, onClose = { route = FeedflowRoute.SiteList })
        is FeedflowRoute.NewThread -> NewThreadScreen(
            site = current.site,
            community = current.community,
            postController = appStateController,
            onCancel = { route = FeedflowRoute.Threads(current.site, current.community) },
            onPosted = { route = FeedflowRoute.Threads(current.site, current.community) },
        )
    }
    }
    }
}

@Composable
private fun FeedflowSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    val background = MaterialTheme.colorScheme.background.toArgb()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = background
        window.navigationBarColor = background
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

@Composable
private fun SiteListScreen(
    sites: List<ForumSite>,
    language: String,
    onSiteClick: (ForumSite) -> Unit,
    onLogin: () -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
    onAi: () -> Unit,
    onCommunityConfig: () -> Unit,
    onRssManager: () -> Unit,
    onSearch: (ForumSite, String) -> Unit,
    onTheme: () -> Unit,
    onLanguage: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedSearchSite by remember { mutableStateOf(ForumSite.Zhihu) }
    Scaffold(
        bottomBar = {
            HomeToolbar(
                language = language,
                onLogin = onLogin,
                onSettings = onSettings,
                onBookmarks = onBookmarks,
                onAi = onAi,
                onCommunityConfig = onCommunityConfig,
                onRssManager = onRssManager,
                onTheme = onTheme,
                onLanguage = onLanguage,
            )
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.select_community),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    HomeSearchBar(
                        selectedSearchSite = selectedSearchSite,
                        query = query,
                        onQueryChange = { query = it },
                        onSiteChange = { selectedSearchSite = it },
                        onSearch = { onSearch(selectedSearchSite, query.trim()) },
                    )
                }
                items(sites.chunked(2)) { rowSites ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowSites.forEach { site ->
                            SiteCard(site = site, modifier = Modifier.weight(1f), onClick = { onSiteClick(site) })
                        }
                        if (rowSites.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitiesScreen(
    site: ForumSite,
    communities: List<Community>,
    isLoading: Boolean,
    warning: String?,
    onBack: () -> Unit,
    onCommunityClick: (Community) -> Unit,
) {
    Scaffold(bottomBar = { ScreenToolbar(title = site.displayName, onBack = onBack, onHome = onBack) }) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FeedflowProgressLine(isLoading) }
                warning?.let {
                    item { WarningCard(it) }
                }
                item { HeaderCard(site.displayName, "Browse communities and categories", site) }
                items(communities) { community ->
                    CommunityRow(community = community, onClick = { onCommunityClick(community) })
                }
            }
        }
    }
}

@Composable
private fun ThreadListScreen(
    site: ForumSite,
    community: Community,
    threads: List<FeedThread>,
    isLoading: Boolean,
    warning: String?,
    loadedFromCache: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onThreadClick: (FeedThread) -> Unit,
    onNewThread: () -> Unit,
    onRefresh: () -> Unit,
    onNotInterested: (FeedThread) -> Unit,
) {
    Scaffold(
        bottomBar = {
            ScreenToolbar(
                title = community.name,
                onBack = onBack,
                onHome = onHome,
                actions = {
                    if (site.supportsThreadCreation) CircularToolbarIcon(FeedflowIconMap.symbol("square.and.pencil"), stringResource(R.string.new_thread_action), onNewThread)
                    CircularToolbarIcon(FeedflowIconMap.symbol("arrow.triangle.2.circlepath"), stringResource(R.string.refresh), onRefresh)
                    CircularToolbarIcon(FeedflowIconMap.symbol("circle.lefthalf.filled"), stringResource(R.string.theme)) {}
                },
            )
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FeedflowProgressLine(isLoading) }
                warning?.let {
                    item {
                        WarningCard(
                            if (loadedFromCache) {
                                stringResource(R.string.showing_cached_topics, it)
                            } else {
                                threadListWarning(site, it)
                            },
                        )
                    }
                }
                item {
                    ThreadListStatusHeader(
                        site = site,
                        community = community,
                        visibleCount = threads.size,
                        isRefreshing = isLoading,
                    )
                }
                if (threads.isEmpty()) {
                    item {
                        ThreadListEmptyView(site = site, community = community, onRefresh = onRefresh)
                    }
                }
                items(threads) { thread ->
                    ThreadRow(
                        thread = thread,
                        site = site,
                        onClick = { onThreadClick(thread) },
                        onNotInterested = if (site == ForumSite.Zhihu) ({ onNotInterested(thread) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadDetailScreen(
    site: ForumSite,
    thread: FeedThread,
    comments: List<Comment>,
    isLoading: Boolean,
    warning: String?,
    loadedFromCache: Boolean,
    webUrl: String,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canLoadMore: Boolean,
    onLoadMore: suspend () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onAiSummary: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenImage: (String) -> Unit,
    onRefresh: () -> Unit,
    canDelete: Boolean,
    onDelete: suspend () -> FeedflowError?,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    postController: FeedflowAppStateController,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var speech by remember { mutableStateOf(SpeechPlaybackState()) }
    val ttsController = remember(context) {
        AndroidTtsController(context) {
            scope.launch { speech = speech.stop() }
        }
    }
    val ttsLanguage = if (LocalContext.current.resources.configuration.locales[0].language == "zh") "zh" else "en"
    DisposableEffect(ttsController) { onDispose { ttsController.shutdown() } }
    var isLoadingMore by remember(thread.id) { mutableStateOf(false) }
    var replyState by remember { mutableStateOf(ReplyComposerState()) }
    var actionError by remember(thread.id) { mutableStateOf<String?>(null) }
    var showingDeleteConfirmation by remember(thread.id) { mutableStateOf(false) }
    var postedReplies by remember(thread.id) { mutableStateOf(emptyList<Comment>()) }
    val justNow = stringResource(R.string.just_now)
    val saidLabel = stringResource(R.string.said)
    val renderedComments = comments + postedReplies
    LaunchedEffect(thread.id) { ttsController.stop(); speech = speech.stop() }
    fun toggleSpeech() {
        speech = if (speech.isSpeaking) {
            ttsController.stop()
            speech.stop()
        } else {
            val text = buildString {
                appendLine(thread.title)
                appendLine(thread.content)
                renderedComments.take(20).forEach { appendLine("${it.author.username}: ${it.content}") }
            }
            if (ttsController.speak(text, ttsLanguage)) speech.speak(text, ttsLanguage) else speech.stop()
        }
    }
    fun shareThread() {
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, thread.title)
            putExtra(android.content.Intent.EXTRA_TEXT, "${thread.title}\n$webUrl")
        }
        context.startActivity(android.content.Intent.createChooser(sendIntent, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun openExternal() {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webUrl))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    val firstImageUrl = remember(thread.content, renderedComments) {
        firstImageUrl(thread.content + "\n" + renderedComments.joinToString("\n") { it.content })
    }
    Scaffold(
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                if (site.supportsCommenting) {
                    replyState.replyingToUsername?.let { username ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.replying_to_user, username),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { replyState = ReplyComposerState() }, modifier = Modifier.size(32.dp)) {
                                Icon(FeedflowIconMap.symbol("xmark.circle.fill"), contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularToolbarIcon(FeedflowIconMap.symbol("image"), stringResource(R.string.image)) {}
                        BasicTextField(
                            value = replyState.content,
                            onValueChange = { replyState = replyState.copy(content = it, errorMessage = null) },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            decorationBox = { innerTextField ->
                                if (replyState.content.isBlank()) {
                                    Text(stringResource(R.string.thread_reply), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                innerTextField()
                            },
                        )
                        IconButton(
                            enabled = replyState.canReply,
                            onClick = {
                                val formatted = replyState.formattedContent(saidLabel)
                                replyState = replyState.copy(isPosting = true, errorMessage = null)
                                scope.launch {
                                    val error = postController.postComment(site, thread, formatted)
                                    if (error == null) {
                                        postedReplies = postedReplies + Comment(
                                            id = "local-${postedReplies.size + 1}",
                                            author = User("me", "Me", "person.circle"),
                                            content = formatted,
                                            timeAgo = justNow,
                                            likeCount = 0,
                                        )
                                        replyState = ReplyComposerState()
                                    } else {
                                        replyState = replyState.copy(isPosting = false, errorMessage = error.message)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                FeedflowIconMap.symbol("paperplane.fill"),
                                contentDescription = stringResource(R.string.reply),
                                tint = if (replyState.canReply) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    replyState.errorMessage?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        )
                    }
                }
                ThreadDetailActionToolbar(
                    loadedFromCache = loadedFromCache,
                    canDelete = canDelete,
                    isBookmarked = isBookmarked,
                    isSpeaking = speech.isSpeaking,
                    onBack = onBack,
                    onHome = onHome,
                    onRefresh = onRefresh,
                    onToggleBookmark = onToggleBookmark,
                    onAiSummary = onAiSummary,
                    onOpenBrowser = onOpenBrowser,
                    onSpeak = ::toggleSpeech,
                    onShare = ::shareThread,
                    onOpenExternal = ::openExternal,
                    onDelete = { showingDeleteConfirmation = true },
                )
            }
        },
    ) { padding ->
        if (showingDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showingDeleteConfirmation = false },
                title = { Text(stringResource(R.string.delete_thread)) },
                text = { Text(stringResource(R.string.delete_thread_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showingDeleteConfirmation = false
                            scope.launch {
                                actionError = onDelete()?.message
                            }
                        },
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showingDeleteConfirmation = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
        ForumBackground(Modifier.padding(padding)) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { FeedflowProgressLine(isLoading) }
                    warning?.let {
                        item { WarningCard(if (loadedFromCache) stringResource(R.string.showing_cached_thread, it) else it) }
                    }
                    actionError?.let {
                        item { WarningCard(it) }
                    }
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            if (site != ForumSite.Rss) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (site != ForumSite.HackerNews) {
                                        AvatarView(thread.author.avatar, thread.author.username, sizeDp = 38)
                                    }
                                    Column {
                                        Text(thread.author.username, fontWeight = FontWeight.SemiBold)
                                        thread.author.role?.let { FeedflowTag(it) }
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(thread.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            ParsedContent(thread.content.ifBlank { stringResource(R.string.cached_content_placeholder) }, onImageClick = onOpenImage)
                            firstImageUrl?.let { imageUrl ->
                                TextButton(onClick = { onOpenImage(imageUrl) }) { Text(stringResource(R.string.open_image_viewer)) }
                            }
                            if (site != ForumSite.Rss) thread.tags.orEmpty().takeIf { it.isNotEmpty() }?.let { tags ->
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { FeedflowTag(it) } }
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                    items(renderedComments) { comment ->
                        Column {
                            CommentRow(
                                comment = comment,
                                canReply = site.supportsCommenting,
                                hideAvatar = site == ForumSite.HackerNews,
                                onReply = {
                                    replyState = replyState.copy(
                                        replyingToCommentId = comment.id,
                                        replyingToUsername = comment.author.username,
                                        replyingToContent = comment.content,
                                    )
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                    if (canLoadMore && renderedComments.isNotEmpty()) {
                        item {
                            Button(
                                onClick = {
                                    if (!isLoadingMore) {
                                        isLoadingMore = true
                                        scope.launch {
                                            onLoadMore()
                                            isLoadingMore = false
                                        }
                                    }
                                },
                                enabled = !isLoadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (isLoadingMore) stringResource(R.string.loading) else stringResource(R.string.load_more_comments)) }
                        }
                    }
                }
                if (hasPrevious || hasNext) {
                    FloatingThreadNavigationControls(
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                        enabled = !isLoading,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    signedInSites: Set<ForumSite>,
    onLogout: (ForumSite) -> Unit,
    onWebLogin: (ForumSite) -> Unit,
    onClose: () -> Unit,
) {
    var selectedSite by remember { mutableStateOf(ForumSite.FourD4Y) }
    val loginSites = listOf(ForumSite.HackerNews, ForumSite.FourD4Y, ForumSite.V2ex, ForumSite.LinuxDo, ForumSite.Zhihu)
    val config = SiteLoginConfig.forSite(selectedSite)
    Scaffold(bottomBar = { ScreenToolbar(title = stringResource(R.string.login), onBack = onClose, onHome = onClose) }) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        loginSites.forEach { site ->
                            LoginSiteChip(
                                site = site,
                                selected = site == selectedSite,
                                signedIn = signedInSites.contains(site),
                                onClick = { selectedSite = site },
                            )
                        }
                    }
                }
                item {
                    ForumCard {
                        if (signedInSites.contains(selectedSite)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.signed_in), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(selectedSite.displayName)
                                }
                                TextButton(onClick = { onLogout(selectedSite) }) {
                                    Icon(Icons.Default.Logout, contentDescription = null)
                                    Text(stringResource(R.string.logout))
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Login, contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                   Text(stringResource(R.string.signed_out), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                   Text(stringResource(R.string.login_to_site_format, selectedSite.displayName))
                                }
                                Button(onClick = { onWebLogin(selectedSite) }) { Text(stringResource(R.string.web_login)) }
                            }
                        }
                    }
                }
                item {
                    val oauth = config?.oauthOptions.orEmpty()
                    if (oauth.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.oauth))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.height(((oauth.size + 1) / 2 * 56).dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(oauth) { option ->
                                Button(onClick = { onWebLogin(selectedSite) }, modifier = Modifier.fillMaxWidth()) { Text(option.name) }
                            }
                        }
                    }
                }
                item {
                    ForumCard {
                        Text(stringResource(R.string.login_capture_note))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    apiKey: String,
    backgroundPrefetch: Boolean,
    onSave: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var pendingKey by remember(apiKey) { mutableStateOf(apiKey) }
    var pendingPrefetch by remember(backgroundPrefetch) { mutableStateOf(backgroundPrefetch) }
    Scaffold(
        bottomBar = {
            ToolbarCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onSave(pendingKey, pendingPrefetch) }) { Text(stringResource(R.string.save)) }
                }
            }
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    SectionTitle(stringResource(R.string.gemini_api_key))
                    ForumCard {
                        OutlinedTextField(
                            value = pendingKey,
                            onValueChange = { pendingKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            placeholder = { Text(stringResource(R.string.enter_api_key)) },
                            singleLine = true,
                        )
                        Text(stringResource(R.string.settings_api_key_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.reading))
                    ForumCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.background_prefetch), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.prefetch_source_note), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = pendingPrefetch, onCheckedChange = { pendingPrefetch = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksScreen(
    threadBookmarks: List<Pair<FeedThread, String>>,
    urlBookmarks: List<Pair<String, String>>,
    onClose: () -> Unit,
    onThreadClick: (FeedThread, String) -> Unit,
    onUrlClick: (String, String) -> Unit,
) {
    Scaffold(bottomBar = { ScreenToolbar(title = stringResource(R.string.bookmarks), onBack = onClose, onHome = onClose) }) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (threadBookmarks.isEmpty() && urlBookmarks.isEmpty()) {
                    item {
                        ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null)
                            Text(stringResource(R.string.no_bookmarks))
                        }
                    }
                }
                if (threadBookmarks.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.thread_bookmarks_title)) }
                    items(threadBookmarks) { (thread, serviceId) ->
                        ThreadRow(
                            thread = thread,
                            site = ForumSite.fromServiceId(serviceId) ?: ForumSite.Rss,
                            onClick = { onThreadClick(thread, serviceId) },
                        )
                    }
                }
                if (urlBookmarks.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.url_bookmarks_title)) }
                    items(urlBookmarks) { (url, title) ->
                        ForumCard(modifier = Modifier.fillMaxWidth().clickable { onUrlClick(url, title) }) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(UrlBookmarkRelativeTime.format(Duration.ZERO), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RssFeedManagerScreen(
    feeds: List<RssFeedSubscription>,
    onFeedsChange: (List<RssFeedSubscription>) -> Unit,
    onDailySummary: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember(feeds) { mutableStateOf(RssFeedManagerState(feeds = feeds)) }
    var manualName by remember { mutableStateOf("") }
    var manualUrl by remember { mutableStateOf("") }
    val opmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) state = state.previewOpml(bytes, System.currentTimeMillis())
        }
    }
    fun updateState(next: RssFeedManagerState) {
        state = next
        onFeedsChange(next.feeds)
    }
    Scaffold(
        bottomBar = {
            ToolbarCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
                    Text(stringResource(R.string.manage_feeds), fontWeight = FontWeight.SemiBold)
                    Row {
                        if (state.feeds.isNotEmpty()) TextButton(onClick = { state = state.toggleEdit() }) { Text(if (state.editMode) stringResource(R.string.cancel) else stringResource(R.string.edit)) }
                        TextButton(onClick = onDailySummary) { Text(stringResource(R.string.daily_summary)) }
                        TextButton(onClick = { opmlPicker.launch(arrayOf("text/xml", "application/xml", "text/x-opml", "*/*")) }) { Text(stringResource(R.string.import_opml)) }
                    }
                }
            }
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ForumCard {
                        OutlinedTextField(value = manualName, onValueChange = { manualName = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.feed_name)) })
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = manualUrl, onValueChange = { manualUrl = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.feed_url)) })
                        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = {
                                updateState(state.addManual(manualName, manualUrl, System.currentTimeMillis()))
                                if (manualUrl.isNotBlank()) {
                                    manualName = ""
                                    manualUrl = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(stringResource(R.string.add_feed)) }
                    }
                }
                if (state.importPreview.isNotEmpty()) {
                    item {
                        ForumCard {
                            Text(stringResource(R.string.opml_import), fontWeight = FontWeight.SemiBold)
                            state.importPreview.forEach { feed -> Text("${feed.title} · ${feed.url}", style = MaterialTheme.typography.bodySmall) }
                            Button(
                                onClick = { updateState(state.importPreview()) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Text(stringResource(R.string.import_count, state.importPreview.size)) }
                        }
                    }
                }
                if (state.feeds.isEmpty()) {
                    item {
                        ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RssFeed, contentDescription = null)
                            Text(stringResource(R.string.empty_rss_feeds_note))
                        }
                    }
                }
                items(state.feeds) { feed ->
                    ForumCard(modifier = Modifier.fillMaxWidth().clickable(enabled = state.editMode) {
                        state = state.toggleSelection(feed.url)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.RssFeed, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(feed.title, fontWeight = FontWeight.SemiBold)
                                Text(feed.url, style = MaterialTheme.typography.bodySmall)
                            }
                            if (feed.isDefault) FeedflowTag(stringResource(R.string.default_label))
                            if (state.editMode && state.selectedUrls.contains(feed.url)) Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected))
                        }
                    }
                }
                if (state.editMode && state.selectedUrls.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                updateState(state.deleteSelected())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text(stringResource(R.string.delete_count, state.selectedUrls.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityConfigScreen(
    enabledSites: Set<ForumSite>,
    onToggle: (ForumSite) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(bottomBar = { ScreenToolbar(title = stringResource(R.string.communities), onBack = onClose, onHome = onClose) }) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ForumSite.entries) { site ->
                    ForumCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SiteIcon(site)
                            Text(site.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (site == ForumSite.Rss) {
                                FeedflowTag(stringResource(R.string.locked))
                            } else {
                                Switch(checked = enabledSites.contains(site), onCheckedChange = { onToggle(site) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CrossSiteUiSection(
    val site: ForumSite,
    val summary: String = "",
    val posts: List<FeedThread> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@Composable
private fun CrossSiteAiSummaryScreen(
    loader: FeedflowAppStateController,
    apiKey: String,
    language: String,
    onClose: () -> Unit,
    onThreadClick: (ForumSite, FeedThread) -> Unit,
) {
    val sites = listOf(ForumSite.HackerNews, ForumSite.V2ex, ForumSite.LinuxDo, ForumSite.FourD4Y)
    var sections by remember {
        mutableStateOf(sites.map { CrossSiteUiSection(site = it) })
    }
    var refreshToken by remember { mutableStateOf(0) }
    fun replaceSection(site: ForumSite, update: CrossSiteUiSection) {
        sections = sections.map { if (it.site == site) update else it }
    }
    LaunchedEffect(refreshToken, apiKey, language) {
        sections = sites.map { CrossSiteUiSection(site = it) }
        sites.forEach { site ->
            val communities = loader.refreshCommunities(site)
            val preferred = when (site) {
                ForumSite.HackerNews -> "topstories"
                ForumSite.V2ex -> "hot"
                ForumSite.LinuxDo -> "latest"
                else -> null
            }
            val community = communities.value.firstOrNull { it.id == preferred } ?: communities.value.firstOrNull()
            if (community == null) {
                replaceSection(site, CrossSiteUiSection(site = site, isLoading = false, error = communities.warning ?: "No categories found"))
            } else {
                val threads = loader.refreshThreads(site, community)
                val topThreads = threads.value.take(10)
                val summaryState = loader.summarizeCrossSite(apiKey, site, topThreads, language)
                replaceSection(
                    site,
                    CrossSiteUiSection(
                        site = site,
                        summary = summaryState.summary.orEmpty(),
                        posts = topThreads,
                        isLoading = false,
                        error = summaryState.errorMessage?.takeIf { it != "check_api_key" }
                            ?: threads.warning?.takeIf { topThreads.isEmpty() }
                            ?: summaryState.errorMessage?.let { "Check API key in Settings to generate Gemini summaries." },
                    ),
                )
            }
        }
    }
    Scaffold(
        bottomBar = {
            ToolbarCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                    Spacer(Modifier.weight(1f))
                    CircularToolbarIcon(FeedflowIconMap.symbol("arrow.triangle.2.circlepath"), stringResource(R.string.refresh)) { refreshToken += 1 }
                }
            }
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text(stringResource(R.string.ai_cross_site_top_10), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(sections) { section ->
                    ForumCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SiteIcon(section.site)
                            Text(section.site.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            FeedflowTag(stringResource(R.string.top_10))
                        }
                        Spacer(Modifier.height(10.dp))
                        when {
                            section.isLoading -> {
                                FeedflowProgressLine(true)
                                Text(stringResource(R.string.loading_summary_links), style = MaterialTheme.typography.bodySmall)
                            }
                            section.error != null -> Text(section.error, color = MaterialTheme.colorScheme.error)
                            else -> {
                                Text(section.summary, style = MaterialTheme.typography.bodyMedium)
                                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                                section.posts.forEach { thread ->
                                    Text(
                                        text = thread.title.ifBlank { stringResource(R.string.untitled_thread) },
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().clickable { onThreadClick(section.site, thread) }.padding(vertical = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyRssSummaryScreen(
    loader: FeedflowAppStateController,
    apiKey: String,
    language: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    var articlesFound by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var generateToken by remember { mutableStateOf(0) }
    LaunchedEffect(generateToken, apiKey, language) {
        isLoading = true
        error = null
        val communities = loader.refreshCommunities(ForumSite.Rss)
        val allThreads = mutableListOf<FeedThread>()
        communities.value.take(30).forEach { community ->
            val threads = loader.refreshThreads(ForumSite.Rss, community)
            allThreads += threads.value
        }
        articlesFound = allThreads.size
        val result = loader.summarizeDailyRss(apiKey, allThreads, language, forceRefresh = generateToken > 0)
        if (result.errorMessage == null) {
            summary = result.summary.orEmpty()
        } else {
            summary = ""
            error = if (result.errorMessage == "check_api_key") {
                context.getString(R.string.daily_summary_api_key_error, allThreads.size)
            } else {
                result.errorMessage
            }
        }
        isLoading = false
    }
    Scaffold(bottomBar = { ScreenToolbar(title = stringResource(R.string.daily_rss_summary), onBack = onClose, onHome = onClose) }) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    isLoading -> ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.fetching_summary), textAlign = TextAlign.Center)
                        if (articlesFound > 0) Text(stringResource(R.string.articles_found, articlesFound), style = MaterialTheme.typography.bodySmall)
                    }
                    error != null -> ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.error), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(error.orEmpty(), textAlign = TextAlign.Center)
                        Button(onClick = { generateToken += 1 }) { Text(stringResource(R.string.try_again)) }
                    }
                    summary.isNotBlank() -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            ForumCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.daily_briefing), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    FeedflowTag(stringResource(R.string.last_24h))
                                }
                                Spacer(Modifier.height(12.dp))
                                ParsedContent(summary)
                                Button(onClick = { generateToken += 1 }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                    Text(stringResource(R.string.regenerate))
                                }
                            }
                        }
                    }
                    else -> ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_summary))
                        Button(onClick = { generateToken += 1 }) { Text(stringResource(R.string.generate_daily_summary)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSummaryScreen(
    site: ForumSite,
    thread: FeedThread,
    apiKey: String,
    loader: FeedflowAppStateController,
    comments: List<Comment>,
    language: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var speech by remember { mutableStateOf(SpeechPlaybackState()) }
    val ttsController = remember(context) {
        AndroidTtsController(context) {
            scope.launch { speech = speech.stop() }
        }
    }
    var summaryState by remember(thread.id, apiKey, language) { mutableStateOf(AiSummaryUiState(isLoading = true)) }
    DisposableEffect(ttsController) {
        onDispose { ttsController.shutdown() }
    }
    fun stopSpeech() {
        ttsController.stop()
        speech = speech.stop()
    }
    fun refresh(force: Boolean) {
        stopSpeech()
        summaryState = AiSummaryUiState(isLoading = true)
        scope.launch {
            summaryState = loader.summarizeThread(apiKey, site, thread, comments, language, forceRefresh = force)
        }
    }
    LaunchedEffect(thread.id, apiKey, language) {
        refresh(force = false)
    }
    Scaffold(
        bottomBar = {
            ScreenToolbar(
                title = stringResource(R.string.ai_assistant),
                onBack = { stopSpeech(); onClose() },
                onHome = { stopSpeech(); onClose() },
                actions = {
                    IconButton(
                        enabled = summaryState.summary != null,
                        onClick = {
                            speech = if (speech.isSpeaking) {
                                ttsController.stop()
                                speech.stop()
                            } else if (ttsController.speak(summaryState.summary.orEmpty(), language)) {
                                speech.speak(summaryState.summary.orEmpty(), language)
                            } else {
                                speech.stop()
                            }
                        },
                    ) { Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.speak)) }
                },
            )
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    if (summaryState.summary != null) {
                        ForumCard {
                            Text(stringResource(R.string.gemini_summary), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            if (summaryState.isCached) FeedflowTag(stringResource(R.string.cached))
                            Spacer(Modifier.height(8.dp))
                            ParsedContent(summaryState.summary.orEmpty())
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            Text(
                                if (speech.isSpeaking) {
                                    stringResource(R.string.speaking_in_language, speech.language)
                                } else {
                                    stringResource(R.string.generated_by_gemini_language_note)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = {
                                    refresh(force = true)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) { Text(stringResource(R.string.regenerate)) }
                        }
                    } else {
                        ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Text(stringResource(R.string.failed_summary_short), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(summaryState.errorMessage ?: stringResource(R.string.check_api_key), textAlign = TextAlign.Center)
                            Button(
                                onClick = {
                                    refresh(force = true)
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text(stringResource(R.string.try_again)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsScreen(
    site: ForumSite,
    query: String,
    requiresLogin: Boolean,
    loader: FeedflowAppStateController,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onThreadClick: (FeedThread) -> Unit,
) {
    var results by remember(site, query) { mutableStateOf(LoadableContent.loaded(SearchResult(emptyList(), false))) }
    var refreshToken by remember(site, query) { mutableStateOf(0) }
    LaunchedEffect(site, query, requiresLogin, refreshToken) {
        if (!requiresLogin) {
            results = results.copy(isLoading = true)
            results = loader.search(site, query)
        }
    }
    Scaffold(
        bottomBar = {
            ScreenToolbar(
                title = site.displayName,
                onBack = onBack,
                onHome = onHome,
                actions = { IconButton(onClick = { refreshToken += 1 }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload)) } },
            )
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { FeedflowProgressLine(results.isLoading) }
                item {
                    HeaderCard(stringResource(R.string.search_results), "\"$query\"", site)
                }
                results.warning?.let { item { WarningCard(it) } }
                if (requiresLogin) {
                    item {
                        ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Text(stringResource(R.string.login_required_search, site.displayName), textAlign = TextAlign.Center)
                        }
                    }
                } else if (results.value.threads.isEmpty()) {
                    item {
                        ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Text(stringResource(R.string.no_results_found))
                        }
                    }
                } else {
                    items(results.value.threads) { thread ->
                        ThreadRow(thread = thread, site = site, onClick = { onThreadClick(thread) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WebLoginSheetScreen(
    site: ForumSite,
    authCoordinator: AuthSessionCoordinator,
    cookieBridge: AndroidWebLoginCookieBridge,
    onAccepted: () -> Unit,
    onClose: () -> Unit,
) {
    val config = SiteLoginConfig.forSite(site)
    var accepted by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember(config) { mutableStateOf(config?.loginUrl) }
    fun saveSession() {
        if (isSaving || accepted || config == null) return
        isSaving = true
        saveError = null
        when (val result = cookieBridge.capture(site, config, currentUrl, authCoordinator)) {
            is LoginCaptureResult.Success -> {
                accepted = true
                cookieBridge.flush()
                onAccepted()
            }
            is LoginCaptureResult.Rejected -> saveError = result.reason.name
        }
        isSaving = false
    }
    Scaffold(
        bottomBar = {
            ToolbarCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(if (accepted) stringResource(R.string.done) else stringResource(R.string.cancel)) }
                    Text(site.displayName, fontWeight = FontWeight.SemiBold)
                    TextButton(enabled = !accepted && !isSaving, onClick = ::saveSession) { Text(if (isSaving) stringResource(R.string.saving) else stringResource(R.string.save_session)) }
                }
            }
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding).imePadding()) {
            if (accepted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ForumCard(contentAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                        Text(stringResource(R.string.login_success), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            cookieBridge.configure(this)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    currentUrl = url
                                    if (config != null && url != null && (config.isPostLoginNavigation(url) || !config.isLoginUrl(url))) {
                                        saveSession()
                                    }
                                }
                            }
                            loadUrl(config?.loginUrl ?: "about:blank")
                        }
                    },
                    update = { webView ->
                        if (webView.url == null && config != null) webView.loadUrl(config.loginUrl)
                    },
                )
                saveError?.let { error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        ForumCard(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.signed_out), fontWeight = FontWeight.SemiBold)
                            Text(error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InAppBrowserScreen(
    url: String,
    pageTitle: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    fun updateHistory(webView: WebView) {
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
        currentUrl = webView.url ?: currentUrl
    }
    Scaffold(
        bottomBar = {
            ToolbarCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
                    Text(pageTitle.ifBlank { stringResource(R.string.browser) }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    TextButton(onClick = onToggleBookmark) { Text(if (isBookmarked) stringResource(R.string.saved) else stringResource(R.string.bookmark)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            ToolbarCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(enabled = canGoBack, onClick = { webViewRef?.goBack() }) { Text(stringResource(R.string.back)) }
                    TextButton(enabled = canGoForward, onClick = { webViewRef?.goForward() }) { Text(stringResource(R.string.forward)) }
                    TextButton(onClick = {
                        val webView = webViewRef ?: return@TextButton
                        if (loading) webView.stopLoading() else webView.reload()
                        loading = !loading
                    }) { Text(if (loading) stringResource(R.string.stop) else stringResource(R.string.reload)) }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                    }) { Text(stringResource(R.string.open)) }
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                                if (url != null) currentUrl = url
                                view?.let(::updateHistory)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (url != null) currentUrl = url
                                view?.let(::updateHistory)
                            }
                        }
                        loadUrl(url)
                    }
                },
                update = { webView -> webViewRef = webView },
            )
        }
    }
}

@Composable
private fun FullScreenImageScreen(url: String, onClose: () -> Unit) {
    var rotation by remember { mutableStateOf(0) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.close), tint = androidx.compose.ui.graphics.Color.White)
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth()
                .height(420.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    rotationZ = rotation.toFloat()
                }
                .pointerInput(url) {
                    detectTransformGestures { _, pan, zoom, gestureRotation ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale == 1f) Offset.Zero else offset + pan
                        rotation += gestureRotation.toInt()
                    }
                }
                .pointerInput(url) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
        ) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
            )
            Text(
                text = stringResource(R.string.image_transform_status, "%.1f".format(scale), rotation),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = { rotation -= 90 }) { Text(stringResource(R.string.rotate_left)) }
            Button(onClick = {
                scale = 1f
                offset = Offset.Zero
                rotation = 0
            }) { Text(stringResource(R.string.reset)) }
            Button(onClick = { rotation += 90 }) { Text(stringResource(R.string.rotate_right)) }
        }
    }
}

@Composable
private fun NewThreadScreen(
    site: ForumSite,
    community: Community,
    postController: FeedflowAppStateController,
    onCancel: () -> Unit,
    onPosted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var composer by remember { mutableStateOf(NewThreadComposerState()) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = (selectedImages + uris).distinct()
    }
    fun appendContent(value: String) {
        composer = composer.copy(
            content = buildString {
                append(composer.content)
                if (composer.content.isNotBlank() && !composer.content.endsWith("\n")) append("\n")
                append(value)
            },
            errorMessage = null,
        )
    }
    Scaffold(
        bottomBar = {
            ToolbarCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.new_thread), fontWeight = FontWeight.SemiBold)
                    Button(
                        enabled = composer.canPost,
                        onClick = {
                            composer = composer.copy(isPosting = true, errorMessage = null)
                            scope.launch {
                                val error = postController.createThread(site, community, composer.title, composer.content)
                                if (error == null) {
                                    composer = composer.copy(isPosting = false)
                                    onPosted()
                                } else {
                                    composer = composer.copy(isPosting = false, errorMessage = error.message)
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.thread_button)) }
                }
            }
        },
    ) { padding ->
        ForumBackground(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { FeedflowProgressLine(composer.isPosting) }
                item { HeaderCard(stringResource(R.string.new_thread), "${site.displayName} · ${community.name}", site) }
                item {
                    ForumCard {
                        OutlinedTextField(
                            value = composer.title,
                            onValueChange = { composer = composer.copy(title = it, errorMessage = null) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.thread_title)) },
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = composer.content,
                            onValueChange = { composer = composer.copy(content = it, errorMessage = null) },
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            placeholder = { Text(stringResource(R.string.share_thoughts)) },
                        )
                        composer.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.attachments_header), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.add_images))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val attachmentSlots: List<Uri?> = selectedImages.map<Uri, Uri?> { it }.ifEmpty { listOf(null, null, null) }
                            attachmentSlots.forEach { uri ->
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (uri != null) {
                                        AndroidView(
                                            modifier = Modifier.fillMaxSize(),
                                            factory = { context ->
                                                android.widget.ImageView(context).apply {
                                                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                                    setImageURI(uri)
                                                }
                                            },
                                            update = { imageView -> imageView.setImageURI(uri) },
                                        )
                                        IconButton(
                                            onClick = { selectedImages = selectedImages - uri },
                                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                                        ) {
                                            Icon(FeedflowIconMap.symbol("xmark.circle.fill"), contentDescription = stringResource(R.string.remove_attachment), modifier = Modifier.size(18.dp))
                                        }
                                    } else {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TextButton(onClick = { appendContent("**bold**") }) { Text("B", fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { appendContent("*italic*") }) { Text("I", fontWeight = FontWeight.SemiBold) }
                            IconButton(onClick = { appendContent("[LINK:https://|Title]") }) { Icon(FeedflowIconMap.symbol("link.circle.fill"), contentDescription = stringResource(R.string.link)) }
                            IconButton(onClick = { composer = composer.copy(content = FormattingToolbar.bullet(composer.content.ifBlank { "List item" })) }) {
                                Icon(FeedflowIconMap.symbol("list.bullet.rectangle.portrait.fill"), contentDescription = stringResource(R.string.bullet_list))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.word_count, composer.wordCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteCard(site: ForumSite, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val siteLabel = stringResource(R.string.site_accessibility, site.displayName)
    ForumCard(
        modifier = modifier
            .defaultMinSize(minHeight = 150.dp)
            .semantics { contentDescription = siteLabel }
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
        contentAlignment = Alignment.CenterHorizontally,
    ) {
        SiteIcon(site)
        Spacer(Modifier.height(12.dp))
        Text(site.displayName, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CommunityRow(community: Community, onClick: () -> Unit) {
    val rowLabel = stringResource(R.string.community_row)
    ForumCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = rowLabel }
            .clickable(onClick = onClick),
    ) {
        Text(community.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(community.description.ifBlank { community.category }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.community_activity, community.activeToday, community.onlineNow),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ThreadRow(
    thread: FeedThread,
    site: ForumSite,
    onClick: () -> Unit,
    onNotInterested: (() -> Unit)? = null,
) {
    val rowLabel = stringResource(R.string.thread_row)
    val isRss = isRssThread(site, thread)
    val compactMeta = usesCompactThreadMeta(site)
    val showBadgeRow = site != ForumSite.Zhihu && site != ForumSite.HackerNews
    val excerpt = thread.content.trim().takeIf { it.isNotEmpty() && (site == ForumSite.Zhihu || isRss || site == ForumSite.V2ex) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = rowLabel }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isRss && site != ForumSite.HackerNews) {
                AvatarView(thread.author.avatar, thread.author.username, sizeDp = 40)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (showBadgeRow) {
                    ThreadBadgeRow(thread = thread, site = site, compactMeta = compactMeta)
                }

                Text(
                    thread.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (compactMeta && !thread.lastPostTime.isNullOrBlank()) {
                    val lastPost = ThreadRowRenderingPolicy.normalizedLastPostTime(thread.lastPostTime)
                    Text(
                        if (thread.lastPosterName.isNullOrBlank()) {
                            "↳ ${lastPost.orEmpty()}"
                        } else {
                            "↳ ${thread.lastPosterName} · ${lastPost.orEmpty()}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                excerpt?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (threadFooterHasContent(thread, site, isRss, compactMeta)) {
                    ThreadRowFooter(thread = thread, site = site, isRss = isRss, compactMeta = compactMeta)
                }

                onNotInterested?.let {
                    TextButton(onClick = it) { Text(stringResource(R.string.not_interested)) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    CommentRow(comment = comment, canReply = false, hideAvatar = false, onReply = {})
}

@Composable
private fun CommentRow(comment: Comment, canReply: Boolean, hideAvatar: Boolean, onReply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (!hideAvatar) AvatarView(comment.author.avatar, comment.author.username, sizeDp = 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(comment.author.username, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                comment.author.role?.let { FeedflowTag(it) }
                if (canReply) {
                    TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(stringResource(R.string.reply), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(comment.timeAgo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ParsedContent(comment.content)
            if (comment.likeCount > 0) ThreadMetricPill(icon = "hand.thumbsup", text = "${comment.likeCount}")
        }
    }
}

@Composable
private fun ThreadListStatusHeader(site: ForumSite, community: Community, visibleCount: Int, isRefreshing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SiteIcon(site, size = 34.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        community.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        threadListStatusSubtitle(site, community, stringResource(R.string.recommendations)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.size(width = 22.dp, height = 2.dp))
                } else {
                    Icon(
                        imageVector = FeedflowIconMap.symbol("slider.horizontal.3"),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThreadStateChip(text = stringResource(R.string.visible_count, visibleCount), color = MaterialTheme.colorScheme.primary)
                when {
                    site == ForumSite.Zhihu && community.id == "recommend" -> {
                        ThreadStateChip(text = stringResource(R.string.read_hidden), color = Color(0xFF2E7D32))
                        ThreadStateChip(text = stringResource(R.string.fetches_to_10), color = Color(0xFFF57C00))
                    }
                    site.requiresLogin -> ThreadStateChip(text = stringResource(R.string.session_checked), color = Color(0xFF2E7D32))
                    else -> ThreadStateChip(text = stringResource(R.string.public_source), color = Color(0xFF7E57C2))
                }
            }
        }
    }
}

@Composable
private fun ThreadListEmptyView(site: ForumSite, community: Community, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(FeedflowIconMap.symbol("tray"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.no_visible_threads), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.no_visible_threads_detail, site.displayName, community.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            TextButton(
                onClick = onRefresh,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Icon(FeedflowIconMap.symbol("arrow.triangle.2.circlepath"), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun ThreadBadgeRow(thread: FeedThread, site: ForumSite, compactMeta: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (compactMeta) {
            Text(
                "${thread.author.username} · ${thread.timeAgo}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (thread.commentCount > 0) ThreadMetricPill(icon = "bubble.left.and.bubble.right.fill", text = "${thread.commentCount}")
        } else {
            ThreadSourceBadge(text = threadSourceName(site), color = threadSourceColor(site), filled = true)
            ThreadSourceBadge(text = threadCategoryName(thread, site), color = MaterialTheme.colorScheme.onSurfaceVariant, filled = false)
            thread.tags.orEmpty().firstOrNull()?.takeIf { site == ForumSite.Zhihu }?.let {
                ThreadSourceBadge(text = it, color = threadTagColor(it), filled = false)
            }
            Spacer(Modifier.weight(1f))
            if (isLikelyFresh(thread.timeAgo)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
            }
        }
    }
}

@Composable
private fun ThreadRowFooter(thread: FeedThread, site: ForumSite, isRss: Boolean, compactMeta: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            threadRowMetadata(thread, site, isRss, compactMeta),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (thread.likeCount > 0 && site != ForumSite.LinuxDo) {
            ThreadMetricPill(icon = "hand.thumbsup", text = "${thread.likeCount}")
        }
        if (!isRss && !compactMeta) {
            ThreadMetricPill(icon = "bubble.left.and.bubble.right.fill", text = "${thread.commentCount}")
        }
    }
}

@Composable
private fun ThreadSourceBadge(text: String, color: Color, filled: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color.White else color,
        maxLines = 1,
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (filled) color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ThreadStateChip(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ThreadMetricPill(icon: String, text: String) {
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(FeedflowIconMap.symbol(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FloatingThreadNavigationControls(
    hasPrevious: Boolean,
    hasNext: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FloatingThreadNavigationButton(
            icon = FeedflowIconMap.symbol("chevron.up"),
            label = stringResource(R.string.previous_thread),
            enabled = enabled && hasPrevious,
            onClick = onPrevious,
        )
        FloatingThreadNavigationButton(
            icon = FeedflowIconMap.symbol("chevron.down"),
            label = stringResource(R.string.next_thread),
            enabled = enabled && hasNext,
            onClick = onNext,
        )
    }
}

@Composable
private fun FloatingThreadNavigationButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(42.dp)) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ThreadDetailActionToolbar(
    loadedFromCache: Boolean,
    canDelete: Boolean,
    isBookmarked: Boolean,
    isSpeaking: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAiSummary: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSpeak: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onDelete: () -> Unit,
) {
    ToolbarCard {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CircularToolbarIcon(FeedflowIconMap.symbol("chevron.left"), stringResource(R.string.back), onBack)
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background((if (loadedFromCache) Color(0xFFF57C00) else Color(0xFF2E7D32)).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (loadedFromCache) Icons.Default.Bookmarks else Icons.Default.CheckCircle,
                    contentDescription = if (loadedFromCache) stringResource(R.string.local_content) else stringResource(R.string.latest_content),
                    tint = if (loadedFromCache) Color(0xFFF57C00) else Color(0xFF2E7D32),
                    modifier = Modifier.size(14.dp),
                )
            }
            if (canDelete) CircularToolbarIcon(FeedflowIconMap.symbol("trash.fill"), stringResource(R.string.delete), onDelete)
            CircularToolbarIcon(FeedflowIconMap.symbol("arrow.triangle.2.circlepath"), stringResource(R.string.refresh), onRefresh)
            CircularToolbarIcon(
                icon = if (isSpeaking) FeedflowIconMap.symbol("stop.fill") else FeedflowIconMap.symbol("speaker.wave.2.fill"),
                label = stringResource(R.string.speak),
                onClick = onSpeak,
            )
            CircularToolbarIcon(
                icon = if (isBookmarked) FeedflowIconMap.symbol("bookmark.fill") else FeedflowIconMap.symbol("bookmark"),
                label = stringResource(R.string.bookmark),
                onClick = onToggleBookmark,
            )
            CircularToolbarIcon(FeedflowIconMap.symbol("square.and.arrow.up"), stringResource(R.string.share), onShare)
            CircularToolbarIcon(FeedflowIconMap.symbol("sparkles.rectangle.stack.fill"), stringResource(R.string.ai_summary_action), onAiSummary)
            CircularToolbarIcon(FeedflowIconMap.symbol("safari.fill"), stringResource(R.string.browser), onOpenBrowser)
            CircularToolbarIcon(FeedflowIconMap.symbol("arrow.up.forward.app"), stringResource(R.string.open_in_browser), onOpenExternal)
            CircularToolbarIcon(FeedflowIconMap.symbol("house.fill"), stringResource(R.string.select_community), onHome)
        }
    }
}

private fun threadListStatusSubtitle(site: ForumSite, community: Community, recommendationsLabel: String): String =
    if (site == ForumSite.Zhihu && community.id == "recommend") {
        "${site.displayName} · $recommendationsLabel"
    } else {
        "${site.displayName} · ${community.category}"
    }

private fun isRssThread(site: ForumSite, thread: FeedThread): Boolean =
    site == ForumSite.Rss || thread.community.category == "RSS"

private fun usesCompactThreadMeta(site: ForumSite): Boolean =
    site == ForumSite.FourD4Y || site == ForumSite.V2ex || site == ForumSite.LinuxDo

private fun threadFooterHasContent(thread: FeedThread, site: ForumSite, isRss: Boolean, compactMeta: Boolean): Boolean =
    (!isRss && !compactMeta) ||
        threadRowMetadata(thread, site, isRss, compactMeta).isNotEmpty() ||
        (thread.likeCount > 0 && site != ForumSite.LinuxDo)

private fun threadRowMetadata(thread: FeedThread, site: ForumSite, isRss: Boolean, compactMeta: Boolean): String =
    when {
        isRss -> "${thread.community.name} · ${thread.timeAgo}"
        compactMeta -> ""
        else -> "@${thread.author.username} · ${thread.timeAgo}"
    }

private fun threadSourceName(site: ForumSite): String = when (site) {
    ForumSite.Zhihu -> "知乎"
    ForumSite.LinuxDo -> "linux.do"
    ForumSite.FourD4Y -> "4D4Y"
    ForumSite.V2ex -> "V2EX"
    ForumSite.HackerNews -> "HN"
    ForumSite.Rss -> "RSS"
}

private fun threadCategoryName(thread: FeedThread, site: ForumSite): String =
    if (site == ForumSite.Zhihu && thread.community.id == "recommend") {
        "Recommend"
    } else {
        thread.community.name.ifEmpty { thread.community.category }
    }

private fun isLikelyFresh(timeAgo: String): Boolean {
    val lower = timeAgo.lowercase()
    return lower.contains("m") || lower.contains("minute") || lower.contains("分钟") || lower.contains("刚刚")
}

private fun threadSourceColor(site: ForumSite): Color = when (site) {
    ForumSite.Zhihu -> Color(0xFF1976D2)
    ForumSite.LinuxDo -> Color(0xFF2E7D32)
    ForumSite.FourD4Y -> Color(0xFFF57C00)
    ForumSite.V2ex -> Color(0xFF7E57C2)
    ForumSite.HackerNews -> Color(0xFFF57C00)
    ForumSite.Rss -> Color(0xFF00897B)
}

private fun threadTagColor(tag: String): Color = when (tag) {
    "回答" -> Color(0xFF1976D2)
    "文章" -> Color(0xFF2E7D32)
    "问题" -> Color(0xFFF57C00)
    "视频" -> Color(0xFF7E57C2)
    "想法" -> Color(0xFFD81B60)
    else -> Color(0xFF64748B)
}

@Composable
private fun threadListWarning(site: ForumSite, warning: String): String =
    if (site == ForumSite.Rss && warning.startsWith("Network failure")) {
        stringResource(R.string.rss_network_warning)
    } else {
        warning
    }

@Composable
private fun WarningCard(message: String) {
    ForumCard {
        Text(stringResource(R.string.offline_cache), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HeaderCard(title: String, subtitle: String, site: ForumSite) {
    ForumCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SiteIcon(site)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            if (site.requiresLogin) FeedflowTag(stringResource(R.string.login_badge))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LoginSiteChip(site: ForumSite, selected: Boolean, signedIn: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 92.dp, height = 94.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SiteIcon(site)
            Spacer(Modifier.height(4.dp))
            Text(site.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
            Icon(
                imageVector = if (signedIn) Icons.Default.CheckCircle else Icons.Default.Login,
                contentDescription = if (signedIn) stringResource(R.string.signed_in_accessibility) else stringResource(R.string.signed_out_accessibility),
                modifier = Modifier.size(14.dp),
                tint = if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun HomeSearchBar(
    selectedSearchSite: ForumSite,
    query: String,
    onQueryChange: (String) -> Unit,
    onSiteChange: (ForumSite) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceSelector(selectedSearchSite, onSelect = onSiteChange)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { innerTextField ->
                if (query.isBlank()) {
                    Text(stringResource(R.string.search), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                innerTextField()
            },
        )
        TextButton(
            enabled = query.trim().isNotEmpty(),
            onClick = onSearch,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text(
                stringResource(R.string.search),
                style = MaterialTheme.typography.labelLarge,
                color = if (query.trim().isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SourceSelector(selected: ForumSite, onSelect: (ForumSite) -> Unit) {
    val searchable = listOf(ForumSite.FourD4Y, ForumSite.V2ex, ForumSite.LinuxDo, ForumSite.Zhihu)
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .size(width = 88.dp, height = 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FeedflowSiteSymbol(selected, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface, compact = true)
            Text(selected.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.search_source), modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            searchable.forEach { site ->
                DropdownMenuItem(
                    text = { Text(site.displayName) },
                    leadingIcon = { FeedflowSiteSymbol(site, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface, compact = true) },
                    onClick = {
                        onSelect(site)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeToolbar(
    language: String,
    onLogin: () -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
    onAi: () -> Unit,
    onCommunityConfig: () -> Unit,
    onRssManager: () -> Unit,
    onTheme: () -> Unit,
    onLanguage: () -> Unit,
) {
    ToolbarCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row {
                ToolbarIcon(FeedflowIconMap.symbol("person.fill"), "Login", onLogin)
                ToolbarIcon(FeedflowIconMap.symbol("slider.horizontal.3"), "Settings", onSettings)
                ToolbarIcon(FeedflowIconMap.symbol("bookmark.fill"), "Bookmarks", onBookmarks)
                ToolbarIcon(FeedflowIconMap.symbol("sparkles.rectangle.stack.fill"), "AI", onAi)
            }
            Row {
                ToolbarIcon(FeedflowIconMap.symbol("square.grid.2x2.fill"), "Communities", onCommunityConfig)
                ToolbarIcon(FeedflowIconMap.symbol("list.bullet.rectangle.portrait.fill"), "RSS", onRssManager)
                ToolbarIcon(FeedflowIconMap.symbol("circle.lefthalf.filled"), "Theme", onTheme)
                TextButton(onClick = onLanguage, modifier = Modifier.size(44.dp)) {
                    Text(if (language == "en") "EN" else "中", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ScreenToolbar(
    title: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    ToolbarCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircularToolbarIcon(FeedflowIconMap.symbol("chevron.left"), stringResource(R.string.back), onBack)
            Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            actions()
            CircularToolbarIcon(FeedflowIconMap.symbol("house.fill"), stringResource(R.string.home), onHome)
        }
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CircularToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ToolbarCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        content = { Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { content() } },
    )
}

@Composable
private fun ForumBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}

@Composable
private fun ForumCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    contentAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            horizontalAlignment = contentAlignment,
            content = content,
        )
    }
}

private fun firstImageUrl(content: String): String? {
    val markdownImage = Regex("""!\[[^]]*]\((https?://[^)\s]+)""").find(content)?.groupValues?.get(1)
    if (markdownImage != null) return markdownImage
    val htmlImage = Regex("""<img[^>]+src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE).find(content)?.groupValues?.get(1)
    if (htmlImage != null) return htmlImage
    return Regex("""https?://\S+\.(?:png|jpe?g|gif|webp)(?:\?\S*)?""", RegexOption.IGNORE_CASE)
        .find(content)
        ?.value
        ?.trimEnd('.', ',', ')', ']')
}

@Composable
private fun SiteIcon(site: ForumSite, size: androidx.compose.ui.unit.Dp = 54.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FeedflowSiteSymbol(site, modifier = Modifier.size(size * 0.52f), tint = Color.White, compact = size < 40.dp)
    }
}

@Composable
private fun FeedflowSiteSymbol(site: ForumSite, modifier: Modifier = Modifier, tint: Color, compact: Boolean = false) {
    when (site) {
        ForumSite.FourD4Y -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "4",
                color = tint,
                fontSize = if (compact) 14.sp else 25.sp,
                lineHeight = if (compact) 14.sp else 25.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
        ForumSite.V2ex -> V2exTriangleSymbol(modifier = modifier, tint = tint)
        ForumSite.Zhihu -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ChatBubble, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
            Text(
                text = "?",
                color = MaterialTheme.colorScheme.primary,
                fontSize = if (compact) 7.sp else 12.sp,
                lineHeight = if (compact) 7.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
        else -> Icon(
            imageVector = FeedflowIconMap.siteIcon(site),
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    }
}

@Composable
private fun V2exTriangleSymbol(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val radius = size.minDimension * 0.14f
        val top = Offset(size.width / 2f, size.height * 0.17f)
        val left = Offset(size.width * 0.22f, size.height * 0.73f)
        val right = Offset(size.width * 0.78f, size.height * 0.73f)
        drawLine(tint, top, left, strokeWidth = stroke)
        drawLine(tint, left, right, strokeWidth = stroke)
        drawLine(tint, right, top, strokeWidth = stroke)
        listOf(top, left, right, Offset(size.width / 2f, size.height * 0.48f)).forEach { center ->
            drawCircle(tint, radius, center)
        }
    }
}

@Composable
private fun AvatarView(avatar: String, fallbackText: String, sizeDp: Int) {
    val initial = AvatarRenderingPolicy.fallbackInitial(fallbackText)
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (AvatarRenderingPolicy.isRemoteAvatar(avatar)) {
            coil.compose.SubcomposeAsyncImage(
                model = avatar,
                contentDescription = stringResource(R.string.avatar_for, fallbackText),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Text(initial, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) },
                error = { Text(initial, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) },
            )
        } else {
            Text(initial, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FeedflowTag(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpecChip(label: String) {
    FeedflowTag(label)
}

@Composable
private fun FeedflowProgressLine(visible: Boolean) {
    if (visible) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ParsedContent(content: String, onImageClick: ((String) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeedflowContentRenderer.parseBlocks(content).forEach { block ->
            when (block) {
                is ContentBlock.Text -> LinkedText(block.segments)
                is ContentBlock.Quote -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(width = 3.dp, height = 42.dp).background(MaterialTheme.colorScheme.outline))
                        LinkedText(block.segments)
                    }
                }
                is ContentBlock.Image -> coil.compose.AsyncImage(
                    model = block.url,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onImageClick != null) Modifier.clickable { onImageClick(block.url) } else Modifier),
                )
            }
        }
    }
}

@Composable
private fun LinkedText(segments: List<LinkSegment>) {
    val single = segments.singleOrNull() as? LinkSegment.Link
    if (single != null) {
        ForumCard {
            Text(single.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(single.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Text(
            segments.joinToString("") { segment ->
                when (segment) {
                    is LinkSegment.Plain -> segment.text
                    is LinkSegment.Link -> segment.title
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun FeedflowAppPreview() {
    FeedflowTheme {
        FeedflowApp()
    }
}
