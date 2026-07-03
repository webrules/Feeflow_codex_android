# LinkedTextView spec

**iOS source:** `Feedflow/Views/ThreadDetailView.swift`

## Purpose

Render text with tappable links, supporting Feedflow’s `[LINK:url|title]` marker and raw URL detection.

## Android UI

- If content is a single link and no plain text, render a prominent link card with link icon and display title.
- Otherwise render inline text with accent-colored tappable link spans.
- Preserve body typography and line spacing.

## Behavior

- Parse `[LINK:url|title]` markers before raw URL detection.
- Support bracketed/nested-bracket titles consistent with iOS regex.
- Raw `http/https` URLs become tappable.
- Tapping any link opens `InAppBrowserView` full screen.

## Data

- Input: raw text.
- Output segments: plain, marker link, raw URL.

## Test cases

- Simple `[LINK]` marker.
- Bracketed/nested title marker.
- Multiple link markers.
- Raw URL in surrounding text.
- Single-link card path.
- Link tap opens browser URL.
