# CommunityRow spec

**iOS source:** `Feedflow/Views/CommunitiesView.swift`

## Purpose

Reusable row for one community/category.

## Android UI

- Horizontal row with 12 dp spacing.
- Leading folder icon in accent-soft rounded/circular background, equivalent to iOS 38 pt frame.
- Text column:
  - Community name, bold 16 sp.
  - Optional description, 14 sp, secondary color, max 2 lines.
- Trailing chevron.
- Full-width forum background with subtle bottom divider.

## Behavior

- Entire row is tappable when used by Communities.
- Description block is omitted when empty.

## Data

- Inputs: `Community`.

## Test cases

- Renders name.
- Hides empty description.
- Limits long description to 2 lines.
- Exposes useful content description for accessibility.
