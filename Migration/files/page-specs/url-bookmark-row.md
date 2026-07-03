# URLBookmarkRow spec

**iOS source:** `Feedflow/Views/BookmarksView.swift`

## Purpose

Display and manage one saved URL bookmark.

## Android UI

- Card row with globe icon in accent-soft background.
- Text column: title max 2 lines, URL max 1 line secondary.
- Trailing relative time (`just now`, `Xm`, `Xh`, `Xd`).

## Behavior

- Tap opens `InAppBrowserView(url, pageTitle)`.
- Context menu destructive delete calls parent deletion.

## Test cases

- Relative time thresholds match iOS.
- Tap opens browser with URL/title.
- Delete callback invoked.
- Long URL/title line limits.
