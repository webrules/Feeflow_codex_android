# ThreadDetailView spec

**iOS source:** `Feedflow/Views/ThreadDetailView.swift`, `Feedflow/ViewModels/ThreadDetailViewModel.swift`

## Purpose

Read a topic, comments, parsed media/links, and perform source-aware actions.

## Android UI

- Forum background; hidden native top bar.
- Scroll content:
  - Non-RSS header with avatar except Hacker News, username, optional role tag.
  - Title, parsed content, optional tags.
  - Divider, comments list, loading indicators.
- Reply target strip and composer only when service supports commenting.
- Bottom action toolbar: back, local/latest indicator, delete when allowed, refresh, bookmark, AI summary, theme, home.
- Right-edge previous/next floating controls for supported services when context has neighbors.

## Behavior

- Load cached detail first, then fresh detail.
- Ignore invalid 4D4Y cached detail with “Could not parse content.”
- Fresh detail merges richer author metadata, saves cache, sets latest indicator.
- Load more comments on last comment appear.
- Reply sends content, optionally with quote, refreshes comments, scrolls bottom.
- Bookmark toggles persistence.
- Delete confirms then calls service delete and dismisses.
- Previous/next switches thread, clears comments/page state, scrolls top.
- AI opens `AISummaryView` with prompt built from thread and first 25 comments in current language.

## Data

- Inputs: initial `Thread`, `ForumService`, optional context threads.
- View state: thread, comments, isLoading, canLoadMore, isLatest, isBookmarked, replyingTo, canDelete.

## Test cases

- Cache-first then fresh update.
- Invalid 4D4Y cache ignored.
- Bookmark toggle/idempotence.
- Comment pagination and max page handling.
- Reply quote formatting and failure alert.
- Delete shown only when service allows own-thread delete.
- Previous/next controls and switching.
- RSS/HN source-specific header/avatar behavior.
