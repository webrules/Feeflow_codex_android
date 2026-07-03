# ParsedContentView spec

**iOS source:** `Feedflow/Views/ThreadDetailView.swift`

## Purpose

Render Feedflow-normalized content blocks: text, quote, image, and links.

## Android UI

- Vertical stack with 12 dp spacing.
- Text blocks use `LinkedTextView`.
- Quote blocks use grey background, rounded corners, leading vertical accent/grey bar.
- Image blocks show async image fit width with rounded corners and loading spinner.
- Double-tap image opens `FullScreenImageView`.

## Behavior

- Split `[IMAGE:url]` markers into image blocks.
- Deduplicate equivalent image URLs using normalized key:
  - Decode `&amp;`, protocol-relative URLs to HTTPS.
  - Strip query/fragment.
  - Normalize Zhihu image host/size/path variants.
- Split text blocks on `[QUOTE]...[/QUOTE]`.
- Missing quote close marker falls back to text.

## Test cases

- Text-only content.
- Image marker renders one image.
- Duplicate Zhihu images dedupe.
- Quote block renders styled quote.
- Link markers inside text/quote still open browser.
- Bad image URL does not crash.
