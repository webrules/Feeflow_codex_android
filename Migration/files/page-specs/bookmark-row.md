# BookmarkRow spec

**iOS source:** `Feedflow/Views/BookmarksView.swift`

## Purpose

Display one bookmarked thread.

## Android UI

- Card with forum card background, 12 dp rounded corners, subtle border.
- Top row: service badge, spacer, thread time.
- Title headline, max 2 lines.
- Bottom row: avatar, author username, spacer, like/comment labels.

## Behavior

- Parent wraps in navigation to Thread Detail.

## Data

- Inputs: `Thread`, `serviceName`.

## Test cases

- Renders service badge, title, author, time, likes, comments.
- Title line-limits.
- Avatar fallback works.
