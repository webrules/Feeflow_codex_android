# FullScreenImageView spec

**iOS source:** `Feedflow/Views/FullScreenImageView.swift`

## Purpose

Full-screen image viewer for parsed thread/comment images.

## Android UI

- Full-screen black background.
- Async image centered with fit scaling.
- Loading progress in white.
- Failure text `failed_load_image`.
- Top-left close icon.
- Bottom rotate-left and rotate-right controls with labels on translucent black cards.

## Behavior

- Pinch zoom clamps to 1.0–5.0.
- Pan only works when zoomed above 1.0.
- Double tap: if zoomed, reset; otherwise zoom to 2.5.
- Rotate buttons adjust by -90/+90 degrees.

## Test cases

- Clamp min/max/in-range.
- Pan ignored at min zoom.
- Double tap toggle.
- Rotate accumulates degrees.
- Close dismisses.
- Failure state renders.
