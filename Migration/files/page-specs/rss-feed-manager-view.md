# RSSFeedManagerView spec

**iOS source:** `Feedflow/Views/DataImportView.swift`

## Purpose

Manage RSS feed subscriptions manually or via OPML import.

## Android UI

- Modal navigation title `manage_feeds`.
- Empty state with feed icon and `add_feeds_description`.
- Feed list rows: feed icon, feed name, URL.
- Bottom toolbar: Done, Edit when feeds exist, Add menu.
- Add menu: manual add, import OPML.
- Edit mode supports selection and bottom delete-count bar.
- Manual add dialog: feed name and URL.

## Behavior

- Delete removes selected feeds and exits edit mode if empty.
- Manual add uses URL as name if name blank and URL non-empty.
- File picker accepts XML/OPML, parses outlines, opens `OPMLImportSheet` if feeds found.

## Test cases

- Empty state with no feeds.
- Manual add valid URL.
- Blank URL does not add.
- Delete single and multiple feeds.
- Edit mode selection clear.
- File import success/failure handling.
