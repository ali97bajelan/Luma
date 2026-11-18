# Calendar type on share

## Intent

Let someone choose whether the date in a shared entry is formatted as Persian or Gregorian from the share preview. Start with the app's currently active calendar so existing behavior is preserved.

## Approaches

- **Per-share override (selected):** The share preview offers Persian and Gregorian choices, initialized from the active app calendar. The choice affects only this share's generated text. This matches “on share” and avoids changing dates elsewhere in Luma.
- **Change the app calendar setting from share:** This would affect every date in Luma and require handling preference-save errors and broader refresh behavior. That side effect is outside the requested share workflow.

## Behavior and constraints

- Selecting a calendar updates the generated date/date-time in the preview and the text passed to Android Sharesheet.
- If the user has edited the text, changing the calendar follows the existing regenerate confirmation so manual edits are not silently lost.
- The share-specific choice does not persist and does not change the app calendar setting.
- Reuse the existing Persian/Gregorian selection pattern, localized calendar labels, CalendarDateFormatter, shared DisplayTimePolicy time formatting, and Luma tokens/theme roles.
- Preserve existing share options, privacy behavior, stale-state checks, authentication, text validation, navigation, and accessibility tags.
- Keep English, French, Persian, RTL, Light/Dark/System, font scaling, touch targets, and time-zone behavior working as before.
