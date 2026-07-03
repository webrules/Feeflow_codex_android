# CommentRow spec

**iOS source:** `Feedflow/Views/ThreadDetailView.swift`

## Purpose

Display a comment/reply inside Thread Detail.

## Android UI

- Horizontal layout with optional leading avatar.
- Author row: bold username, optional role `TagView`, reply action if supported, spacer, time.
- Body uses `ParsedContentView`.
- Vertical padding equivalent to iOS 12 pt.

## Behavior

- `hideAvatar` true hides avatar for Hacker News.
- `canReply` true shows localized `reply` action.
- Reply tap calls parent callback.

## Data

- Input: `Comment`, `canReply`, `hideAvatar`.

## Test cases

- Avatar visibility follows flag.
- Role tag visible when role exists.
- Reply hidden for read-only services.
- Nested reply content parses images/quotes/links.
- Time and username render.
