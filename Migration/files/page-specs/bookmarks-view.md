# BookmarksView spec

**iOS source:** `Feedflow/Views/BookmarksView.swift`

## Purpose

Show saved thread bookmarks and URL bookmarks.

## Android UI

- Modal navigation titled `bookmarks`, bottom Close.
- Empty state: bookmark slash icon and `no_bookmarks`.
- Non-empty state: scroll list with sections:
  - `thread_bookmarks` section header and `BookmarkRow` items.
  - `url_bookmarks` section header and `URLBookmarkRow` items.

## Behavior

- On appear and pull-to-refresh, load bookmarks.
- Thread row opens Thread Detail using service id stored with bookmark.
- URL row opens In-App Browser.
- URL context delete removes URL bookmark.

## Data

- `bookmarkedThreads: [(Thread, serviceId)]`.
- `urlBookmarks: [(url, title, date)]`.

## Test cases

- Empty state when both lists empty.
- Thread and URL sections conditionally render.
- Refresh reloads bookmarks.
- Thread bookmark uses correct service.
- URL delete removes row.
