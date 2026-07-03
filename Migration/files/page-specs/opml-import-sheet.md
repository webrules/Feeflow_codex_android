# OPMLImportSheet spec

**iOS source:** `Feedflow/Views/DataImportView.swift`

## Purpose

Preview OPML feeds, select new feeds, and ignore duplicates.

## Android UI

- Modal navigation title `import_from_opml`.
- List section for new feeds with selectable rows.
- New feed row: title, URL, trailing selected/unselected circle.
- Header shows `new_feeds_count` and select/deselect all button.
- Duplicate section shows disabled rows and `already_added`.
- Bottom toolbar: Cancel, Import count button disabled when none selected.

## Behavior

- On appear, select all new feeds.
- Existing URLs are excluded from importable set.
- Select/deselect all toggles all new feeds.
- Import passes only selected new feeds to parent and dismisses.

## Test cases

- Parses and separates new vs duplicate feeds.
- Preselects all new feeds.
- Select/deselect all.
- Import disabled with none selected.
- Import callback receives selected feeds only.
