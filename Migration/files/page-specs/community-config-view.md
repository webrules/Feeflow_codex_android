# CommunityConfigView spec

**iOS source:** `Feedflow/Views/SiteListView.swift`

## Purpose

Configure which source cards appear on Home while keeping RSS locked on.

## Android UI

- Modal screen titled `communities`.
- Forum background.
- List of all `ForumSite` values.
- Row content: `SiteIcon`, service name, spacer, trailing control.
- RSS row shows locked/check seal, not a toggle.
- Non-RSS rows show a switch tinted accent.
- Bottom toolbar has `done`.

## Behavior

- Toggling a non-RSS site updates persisted enabled-site set.
- RSS toggle attempt is a no-op.
- Done dismisses and Home reflects updated visible sites.

## Data

- Reads/writes `CommunitySettingsManager.enabledSites`.
- Persist as site raw values/service ids.

## Test cases

- RSS is always enabled.
- Non-RSS toggle hides and re-shows site.
- Visible sites contain only enabled sites plus RSS.
- UI test verifies RSS uses locked icon rather than switch.
