# State, navigation, cache, and prefetch technical design

## iOS anchors

- `Feedflow/ForumReaderApp.swift`
- `Feedflow/ContentView.swift`
- `Feedflow/ViewModels/NavigationManager.swift`
- `Feedflow/ViewModels/ForumViewModel.swift`
- `Feedflow/ViewModels/ThreadListViewModel.swift`
- `Feedflow/ViewModels/ThreadDetailViewModel.swift`
- `Feedflow/ViewModels/NewThreadViewModel.swift`
- `Feedflow/ViewModels/BookmarksViewModel.swift`
- `Feedflow/Services/NetworkMonitor.swift`
- `Feedflow/Services/ImageCache.swift`

## App/navigation contract

iOS:

- App owns `AuthViewModel` and `ThemeManager`.
- Root `ContentView` owns `NavigationManager`.
- Root screen is `SiteListView`.
- Navigation destinations:
  - `ForumSite -> CommunitiesView(service: site.makeService())`
  - `SiteSearchRoute -> SiteSearchResultsView(route:)`
- `NavigationManager.popToRoot()` resets path to empty.

Android:

- One `NavHostController` at app root.
- Home route: site list.
- Routes use stable IDs:
  - `site/{serviceId}`
  - `communities/{serviceId}`
  - `threads/{serviceId}/{communityId}`
  - `thread/{serviceId}/{communityId}/{threadId}`
  - `search/{serviceId}?query=...`
  - `login?serviceId=...&returnTo=...`
  - global settings/bookmarks/rss/import/browser/image/AI routes.
- Large objects are loaded from repository by ID; offline bookmark detail may pass bookmark key and load stored JSON.

## ForumViewModel state machine

State:

- `communities`
- `isLoading`
- `selectedCategory = "All Categories"`
- `needsLogin`

Flow:

1. Init hydrates cached communities synchronously from DB.
2. Init starts `loadData()`.
3. `loadData`:
   - set loading.
   - call `service.restoreSession()`.
   - if false and service requires login: set `needsLogin=true`, stop loading, keep cache.
   - fetch categories.
   - for 4D4Y: if fresh count < cached count, append cached communities missing from fresh.
   - set UI communities.
   - save communities.
4. `refresh()` clears `needsLogin`, then reloads.

Tests: cached-first hydration, login-required keeps cache, 4D4Y smaller fresh preservation, refresh resets login flag.

## ThreadListViewModel state machine

Published state:

- `threads`
- `isLoading`
- `canLoadMore`
- `isAtTop`
- `refreshMessage`
- `needsLogin`

Private state:

- `currentPage`
- `currentCommunity`
- prefetch queue/tasks
- `maxPrefetchQueueSize = 5`
- scroll debounce task

Settings:

- Background prefetch key: `background_prefetch_enabled`
- Default: `false`
- Allowed services: `hackernews`, `rss`, `4d4y`, `v2ex`, `linux_do`, `zhihu`

Cache key:

```text
{service.id}_{community.id}_page1
```

Load algorithm:

1. Always call `restoreSession()` first.
2. If service requires login and restore fails:
   - `needsLogin=true`
   - `refreshMessage` indicates login required
   - return without clearing threads.
3. Force refresh:
   - loading true.
   - call `refreshCategoryThreads`.
   - reject unsafe empty refresh for 4D4Y or Zhihu recommend if existing threads are non-empty.
   - preserve richer metadata from old rows where fresh data has generic avatar/missing tags/last-post info.
   - save page-1 cache.
   - diff-merge UI list.
   - reset page to 1.
4. Returning/cached load:
   - show cached page-1 threads immediately.
   - reset page to 1.
   - unless Zhihu recommend, fetch fresh in background.
   - background fresh updates visible list only if user is still at top.
5. First load with no cache:
   - loading true.
   - page 1.
   - clear list.
   - fetch page 1.
   - save cache.
   - set `canLoadMore`.

Pagination:

- Guard `!isLoading && canLoadMore`.
- Fetch `currentPage + 1`.
- Empty result -> `canLoadMore=false`.
- Non-empty -> append and increment page.
- Page > 1 is not saved back to page-1 cache.

Scroll:

- Reaching top sets `isAtTop=true` immediately.
- Leaving top debounced 100 ms.

Prefetch:

- Requires:
  - setting enabled,
  - service in allow list,
  - Wi-Fi/unmetered,
  - queue size `< 5`,
  - detail not already cached.
- Debounce per thread: 400 ms.
- Queue processes serially.
- Check Wi-Fi before each item; if Wi-Fi lost, clear queue.
- Fetch detail page 1, save cached thread/comments.
- Delay 1 second between items.

## ThreadDetailViewModel state machine

State:

- `thread`
- `comments`
- `isLoading`
- `canLoadMore`
- `isLatest`
- `isBookmarked`
- `shouldScrollAfterReply`
- `replyingTo`

Init:

- Load bookmark status.
- Mark Zhihu recommend item read/filtered if iOS does for selected item.

Load detail:

1. `loadDetails()` uses cache; `refreshDetails()` bypasses cache.
2. Reset page to 1 and `isLatest=false`.
3. If cache exists and valid:
   - set thread/comments immediately.
   - compute `canLoadMore`.
4. Invalid cache rule:
   - for 4D4Y, cached content `"Could not parse content."` is ignored.
5. Always fetch fresh page 1 after cache.
6. Merge author preserving non-generic avatar.
7. Save cache.
8. Set `isLatest=true`.
9. `canLoadMore` from `totalPages` or non-empty comments.
10. Fresh error leaves cached content visible.

Comment pagination:

- Guard `!isLoading && canLoadMore`.
- Fetch next page.
- Empty -> stop.
- Non-empty -> append, increment page.
- If `totalPages` present, honor it.

Reply:

If replying to a comment, prepend exact Discuz quote:

```text
[quote][b]{username} said:[/b]
{content}[/quote]

{userReply}
```

Then:

1. `service.postComment`.
2. Refresh to last page if multiple pages.
3. Set `shouldScrollAfterReply=true`.
4. Clear `replyingTo`.

Previous/next:

- Uses context thread list.
- Resets comments/page/bookmark/read state.
- Loads new detail.

Bookmark:

- Toggle DB by `(threadId, serviceId)`.
- Re-read bookmark state after toggle.

## NewThreadViewModel

State:

- `title`
- `content`
- `isPosting`
- `error`

Rules:

- Return early if `title` or `content` is empty; iOS does not trim.
- On post start: `isPosting=true`, `error=null`.
- Success: `isPosting=false`, UI dismisses.
- Failure: set `error`, `isPosting=false`, rethrow to UI.

## BookmarksViewModel

- Thread bookmarks: `[(Thread, serviceId)]`, newest first.
- URL bookmarks: `[(url,title,date)]`, newest first.
- `loadBookmarks()` reads both DB tables.
- URL removal updates DB and local list.
- Service mapping fallback is 4D4Y.

## NetworkMonitor

iOS:

- Singleton.
- Publishes `isConnected` and `isWiFi`.
- Simulator treats any satisfied connection as Wi-Fi.

Android:

- Singleton/repository backed by `ConnectivityManager`.
- Expose `StateFlow<NetworkState>`.
- `isWifiOrUnmetered` gates prefetch.
- Tests must fake network state.

## Required tests

1. Nav root starts at site list; pop-to-root clears stack.
2. Login-required route returns to original destination.
3. Forum cache-first and login-required flows.
4. Thread list force refresh empty preservation for 4D4Y/Zhihu recommend.
5. Returning cached load updates only at top.
6. Pagination appends and stops on empty.
7. Prefetch gating and queue behavior.
8. Thread detail cache display, invalid 4D4Y cache ignored, fresh failure keeps stale.
9. Reply quote format exact.
10. Bookmark toggle persists and refreshes state.
11. New-thread empty title/content returns early.
