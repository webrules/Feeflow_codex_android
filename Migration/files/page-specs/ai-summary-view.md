# AISummaryView spec

**iOS source:** `Feedflow/Views/AISummaryView.swift`

## Purpose

Generate and display one thread’s AI summary with cache and speech.

## Android UI

- Modal navigation titled `ai_assistant`.
- Loading: progress and `gemini_analyzing`.
- Error: warning icon, `failed_summary`, `check_api_key`, Try Again.
- Success: `gemini_summary` title, optional cached badge, selectable body text, generated-by footer, Regenerate.
- Bottom toolbar: speaker button and close.

## Behavior

- Cache key is `threadId_serviceId_language`.
- Use cached summary unless force refresh.
- Generate through Gemini and save summary.
- Errors set error state without crashing.
- Speaker reads summary in current language; close stops speech and dismisses.

## Test cases

- Cache hit sets cached badge and skips generation.
- Force refresh overwrites cache.
- Error state and retry.
- Summary persisted by thread/service/language.
- Speech start/stop state.
