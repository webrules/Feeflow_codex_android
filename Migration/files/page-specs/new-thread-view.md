# NewThreadView spec

**iOS source:** `Feedflow/Views/NewThreadView.swift`

## Purpose

Create a new topic in sources/categories that support thread creation.

## Android UI

- Modal navigation titled `new_thread`.
- Forum background.
- Thin linear progress indicator while posting.
- Title text field styled like title, placeholder `thread_title`.
- Text editor with overlay placeholder `share_thoughts`.
- Attachments section with header, Add Images button, and placeholder image tiles.
- Formatting toolbar: bold, italic, link, bullet, word count.
- Navigation actions: Cancel, trailing pill `thread_button`.

## Behavior

- Post button disabled when title/content empty or currently posting.
- Post calls `NewThreadViewModel.postThread`.
- Success dismisses.
- Failure shows `post_failed` alert with error.
- Keyboard Done hides keyboard.

## Test cases

- Disabled state for empty fields.
- Word count updates.
- Progress line visible while posting.
- Successful post calls service with category/title/content and dismisses.
- Failed post shows alert and keeps input.
