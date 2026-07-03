# LinearProgressView spec

**iOS source:** `Feedflow/Views/NewThreadView.swift`

## Purpose

Tiny reusable indeterminate progress line used by New Thread posting state.

## Android UI

- Material linear progress indicator.
- Accent tint.
- Height equivalent to iOS 2 pt.
- Full width of parent.

## Behavior

- Pure presentation.

## Test cases

- Renders when `isPosting` true.
- Hidden when `isPosting` false through parent test.
