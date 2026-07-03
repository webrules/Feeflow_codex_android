# AvatarView spec

**iOS source:** `Feedflow/Views/AvatarView.swift`

## Purpose

Render user avatars from URLs or local/system fallback names.

## Android UI

- Circular avatar at requested size.
- If URL loads, show clipped image.
- If local/system symbol name or empty/bad URL, show fallback initial/text or person icon.
- Maintain source row/detail sizing: 20, 32, 40 dp equivalents.

## Behavior

- Treat `http/https` strings as remote URLs.
- Treat known generic avatar names as local fallback, not remote URL.
- Support fallback text initials when image unavailable.

## Test cases

- HTTP URL attempts remote image.
- Generic avatar names are not treated as URLs.
- Empty avatar uses fallback text/icon.
- Failed URL does not crash.
- Size parameter controls rendered dimensions.
