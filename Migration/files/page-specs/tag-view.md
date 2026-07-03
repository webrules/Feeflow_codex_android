# TagView spec

**iOS source:** `Feedflow/Views/ThreadDetailView.swift`

## Purpose

Small reusable chip for roles, tags, and source metadata.

## Android UI

- Text uses caption-sized font.
- Secondary text color.
- Horizontal padding 10 dp, vertical padding 4 dp.
- Forum card background.
- 8 dp rounded corners.

## Behavior

- Pure presentation; no click behavior unless a parent wraps it.

## Data

- Input: `text`.

## Test cases

- Renders provided text.
- Uses secondary color/card background.
- Handles long text with parent-defined constraints.
