# DailyRSSSummaryView spec

**iOS source:** `Feedflow/Views/DailyRSSSummaryView.swift`

## Purpose

Generate a daily briefing from RSS updates in the last 24 hours.

## Android UI

- Modal navigation title `daily_rss_summary`.
- Loading: progress, `fetching_summary`, optional article count.
- Error: warning icon, `error`, message, Try Again.
- Success: `daily_briefing`, cached badge, `last_24h`, markdown-capable selectable text, Regenerate.
- Empty/initial: `no_summary`, Generate button.
- Bottom close icon.

## Behavior

- Auto-generates on first appear if empty.
- Uses cache key `daily_rss_summary` with 7-day TTL unless force refresh.
- Fetches daily RSS updates, caps prompt at 30 articles and 500 chars per article.
- No updates shows `no_updates_24h`.
- Pull-to-refresh/regenerate force refresh.

## Test cases

- Cache hit within TTL.
- Stale cache ignored.
- Empty updates message.
- Article/snippet cap.
- Error and retry.
