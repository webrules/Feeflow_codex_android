# ThreadRow spec

**iOS source:** `Feedflow/Views/ThreadListView.swift`

## Purpose

Reusable visual representation of a thread in lists/search/bookmarks.

## Android UI

- Horizontal row/card matching iOS spacing.
- Avatar hidden for RSS and Hacker News; shown for other services.
- Badge row hidden for Zhihu and Hacker News.
- Title: semibold 16 sp, primary text, max 2 lines.
- For 4D4Y/V2EX/Linux.do, show last poster/time row when present.
- Optional excerpt/content preview for services that provide it.
- Likes/comments/tag metadata in secondary color.

## Behavior

- Row prefetch hooks are attached by parent list.
- Row tap opens detail through parent navigation.
- Source-specific hiding rules must exactly match iOS.

## Data

- Inputs: `Thread`, `ForumService`.
- Uses author avatar/name, title, content, timeAgo, likeCount, commentCount, tags, lastPostTime, lastPosterName.

## Test cases

- Avatar hidden for RSS/HN and shown otherwise.
- Badge row hidden for Zhihu/HN.
- Last poster/time strips leading year like iOS regex.
- Title is limited to 2 lines.
- Metadata counts render.
