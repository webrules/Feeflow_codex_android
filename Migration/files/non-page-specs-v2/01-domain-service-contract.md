# Domain model and service contract technical design

## iOS anchors

- `Feedflow/Models/Models.swift`
- `Feedflow/Services/ForumService.swift`
- All concrete `Feedflow/Services/*Service.swift`

## Domain data classes

Android domain models must be source-neutral, serializable, and keyed by service scope in persistence. Kotlin names can differ from Swift only where needed to avoid conflicts, but field semantics must remain identical.

```kotlin
data class FeedUser(
    val id: String,
    val username: String,
    val avatar: String,
    val role: String? = null,
)

data class Community(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val activeToday: Int,
    val onlineNow: Int,
)

data class FeedThread(
    val id: String,
    val title: String,
    val content: String,
    val author: FeedUser,
    val community: Community,
    val timeAgo: String,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean = false,
    val tags: List<String>? = null,
    val lastPostTime: String? = null,
    val lastPosterName: String? = null,
)

data class FeedComment(
    val id: String,
    val author: FeedUser,
    val content: String,
    val timeAgo: String,
    val likeCount: Int,
    val replies: List<FeedComment>? = null,
)
```

### Identity rules

- UI equality can use Kotlin value equality.
- Database, cache, bookmarks, summaries, and navigation must key all source content by `(serviceId, id)`.
- Required strings must never be null. Use explicit source-aware fallbacks like `"Unknown"`, `"Anonymous"`, `"No Title"`, or empty string only where the iOS implementation does so.
- Unsupported numeric counts map to `0`.
- Unsupported optional values stay `null`, not empty strings.

## Service metadata contract

```kotlin
interface ForumService {
    val name: String
    val id: String
    val logo: String
    val requiresLogin: Boolean
    val supportsCommenting: Boolean
    val supportsThreadCreation: Boolean
    val currentUsername: String?

    suspend fun restoreSession(): Boolean
    suspend fun fetchCategories(): List<Community>
    suspend fun fetchCategoryThreads(
        categoryId: String,
        communities: List<Community>,
        page: Int = 1,
    ): List<FeedThread>
    suspend fun refreshCategoryThreads(
        categoryId: String,
        communities: List<Community>,
    ): List<FeedThread>
    suspend fun fetchThreadDetail(
        threadId: String,
        page: Int = 1,
    ): ThreadDetailResult
    suspend fun postComment(topicId: String, categoryId: String, content: String)
    suspend fun createThread(categoryId: String, title: String, content: String)
    fun canDeleteThread(thread: FeedThread): Boolean
    suspend fun deleteThread(threadId: String, categoryId: String)
    suspend fun searchThreads(query: String, page: Int): SearchResult
    fun getWebUrl(thread: FeedThread): String
    fun canCreateThread(community: Community): Boolean = supportsThreadCreation
}

data class ThreadDetailResult(
    val thread: FeedThread,
    val comments: List<FeedComment>,
    val totalPages: Int?,
)

data class SearchResult(
    val threads: List<FeedThread>,
    val hasMore: Boolean,
)
```

## Default behaviors to copy from iOS

| API | Default Android behavior |
|---|---|
| `requiresLogin` | `false` |
| `supportsCommenting` | `false` |
| `supportsThreadCreation` | `false` |
| `restoreSession()` | `true` |
| `currentUsername` | `null` |
| `canDeleteThread()` | `false` |
| `deleteThread()` | Throw typed `UnsupportedFeature.DeleteThread`. |
| `searchThreads()` | Return empty false only when UI hides search for that source; otherwise implement or throw typed unsupported. |
| `fetchCategoryThreads(categoryId, communities)` overload | Delegates to page 1. |
| `fetchThreadDetail(threadId)` overload | Delegates to page 1 and drops `totalPages`. |

## Time formatting

iOS `ForumService.calculateTimeAgo` behavior:

- `< 60s`: `"just now"`
- `< 3600s`: `"{minutes}m"`
- `< 86400s`: `"{hours}h"`
- otherwise: `"{days}d"`
- ISO8601 parser accepts both fractional-second and non-fractional strings.
- Invalid date string returns `"now"`.

Android must implement this as a pure utility with a fixed-clock overload for tests.

## Typed error model

Use a sealed hierarchy equivalent to:

```kotlin
sealed class FeedflowError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object AuthRequired : FeedflowError("Authentication required")
    data object UnsupportedFeature : FeedflowError("Unsupported feature")
    data class Network(val statusCode: Int? = null, val url: String, val bodyPreview: String? = null) : FeedflowError()
    data class Parsing(val serviceId: String, val stage: String, val bodyPreview: String? = null) : FeedflowError()
    data class PermissionDenied(val operation: String) : FeedflowError()
    data class Upstream(val serviceId: String, val statusCode: Int, val messageText: String?) : FeedflowError()
}
```

Do not convert errors to empty lists except where iOS explicitly returns empty for unsupported optional paging, such as V2EX/Hacker News page > 1.

## Source capability table

| Service ID | Name | Requires login | Commenting | Thread creation | Search | Notes |
|---|---|---:|---:|---:|---:|---|
| `4d4y` | 4D4Y | Yes | Yes | Yes | Yes | Discuz, GBK/GB18030, own-thread deletion. |
| `linux_do` | Linux.do | Yes | Yes | Yes | Yes | Discourse JSON + CSRF. |
| `hackernews` | Hacker News | No | No | No | No native search in iOS service | Read-only Firebase. |
| `v2ex` | V2EX | No for reading | Yes | No | Yes | HTML scraping + Google fallback search. |
| `zhihu` | 知乎 | Yes | No | No | Yes | Cookie-auth JSON APIs, filtering/downvote. |
| `rss` | RSS Feeds | No | No | No | No | Custom feeds + OPML. |

## Acceptance tests

1. Kotlin models serialize/deserialize every field, including null optional fields.
2. `(serviceId, id)` scoping prevents bookmark/cache collisions for identical IDs.
3. Service default methods match iOS defaults.
4. Capability table is verified for all services.
5. Time formatting thresholds and invalid ISO fallback match iOS.
6. Unsupported operations throw typed errors, not generic exceptions.
