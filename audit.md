# Luma UI/UX audit — 13 October 2026

Goal: make daily capture, browsing, and reviewing easier to understand and faster to use, with a coherent visual identity across the existing app.

## Review conditions

- Running app: `com.luma.lifeos`, Android emulator `emulator-5554`, 1080 × 2400, English initially, system font scale 1.0, light mode, Asia/Tehran.
- Reviewed the live interface alongside its Compose screens, state handling, resources, and relevant repositories.
- Opened blank capture/catalog forms and existing read-only details. No entry was saved, edited, deleted, restored, or exported during the audit. No provider permission was granted.
- Some screens lacked saved examples in this install; those paths were reviewed in source rather than populated with invented personal records.
- Screenshots and accessibility hierarchies are in this directory. Early captures 02–05 include transition frames and are excluded from visual findings.

## Coverage

| Area | Coverage |
| --- | --- |
| Today | Saved summary, date controls, count card, entries, repeated usage check; empty-day presentation |
| Timeline | Existing mood entry, app-usage toggle, date navigation, empty day |
| Quick Capture | Bottom sheet and all three capture choices |
| Mood | New form, optional details/scales, emotions, people/place selectors, time controls; existing edit screen reviewed in source |
| Journal | New form, tags, optional context, photo controls, date/time dialogs; reading/editing paths reviewed in source |
| Memory | Standalone form, importance selector, optional context; promotion/removal safeguards reviewed in source |
| People | List, new/edit form, existing person detail with empty shared history |
| Places | Empty list and new form; detail/edit layouts reviewed in source |
| Search | Results, filter dialog, people selector, imported record detail |
| Weekly Review | Header, entry counts, mood, memories, people, places, digital usage, activity, source browsing |
| Settings | Language, backup, app usage, Health Connect, saved usage totals/history, privacy/local-data content |
| Safeguards | Delete/discard, restore confirmation, permission explanation logic reviewed in source |
| Keyboard | Focused forms inspected; emulator displayed a floating handwriting toolbar rather than a conventional docked keyboard |
| Languages | English main flows; Persian Settings/Today/mood capture and RTL navigation; French Settings and navigation |
| Theme/text | Today and Timeline in dark mode; Today, mood capture, and Timeline at system font scale 1.3 |

## Findings

| Priority | Finding | Evidence | User impact |
| --- | --- | --- | --- |
| High | Today and Settings use different usage sources | Today repeatedly displays `0 h 0 min · apps shown: 0`; Settings and Weekly Review show `0 h 43 min 41.203 s` for 13 October. Screens 31, 36, 46. | Daily usage feels inconsistent and the top-app list is empty despite saved visits. |
| High | Today places the entry feed below two summaries | Screen 36: the daily-summary card fills most of the first viewport; another card repeats event counts before the first entry. | The screen prioritizes explanatory text over the user's day. |
| High | Integration controls are buried under lengthy descriptions | Screens 28–31: usage and health descriptions/retention text appear before switches, status, and import actions. | Frequent controls are hard to find and Settings takes many scrolls. |
| Medium | Theme is only partially specified | `ui/theme/Theme.kt` overrides four color roles. Cards, navigation, and Quick Capture use purple defaults alongside green actions. Stable screens 22, 36, 37. | The interface lacks a consistent hierarchy and identity. |
| Medium | Day/week navigation occupies several vertical rows | Screens 01, 36, 37; `DayNavigation.kt`, `WeeklyReviewRoute.kt`. | Browsing controls consume space that could show entries or metrics. |
| Medium | Navigation symbols look inconsistent | `LifeOsApp.kt` uses Unicode text symbols for tab icons. | Tabs are harder to recognize visually and icon rendering depends on the font. |
| Medium | Secondary screens retain primary navigation and capture | Search and Review Sources are absent from `eventScreen`; screens 32 and 38 show an unselected bottom bar plus Quick Capture. | Focused tasks have extra navigation and an unrelated action. |
| Medium | Floating capture action overlaps visible controls | Screen 22: Quick Capture covers part of the restore button; other settings screens show overlap with access actions. | Actions can be obscured during scrolling. |
| Medium | Five-point selectors wrap unevenly | Screen 08: mood option 5 wraps to a second row. Screen 16: importance choices occupy three rows with repeated labels. | The scales lose their visual order and make forms longer. |
| Medium | Forms lack consistent persistent headings | Mood, journal, memory, person, place use large headings within scrolling content and bottom Cancel/Save rows. Screens 08, 13, 16, 18, 24. | After scrolling, users lose context and the close action is distant from the title. |
| Medium | Optional capture context has excessive vertical weight | Repeated People, Place, Photos, When sections and stacked actions; screens 10, 14, 17. | A simple capture looks like a long form. |
| Medium | Keyboard actions are not tailored to fields | Editor text fields have default keyboard options/actions; focused emulator fields show a floating handwriting toolbar. | Name/title/tag fields lack deliberate Next/Done behavior. Conventional keyboard resize needs follow-up validation. |
| Medium | Review metrics repeat long structures and actions | `ReviewValue` repeats label, value, previous, change, and two source links; screens 37, 40, 45–46. | Comparing the week requires considerable scrolling and reading. |
| Medium | Detail screens repeat information without clear grouping | Imported app detail repeats title, app label, package, source, duration, date and interval; screen 35. Journal body follows photos in source. | Readers have to scan metadata before reaching content. |
| Medium | Sub-minute use is formatted as zero minutes | Screen 35 shows a roughly 36-second interval as `0 h 0 min`; `AppUsageDuration.kt` truncates seconds. | Nonzero use can appear to be no use. |
| Low | Empty states and catalog rows are mostly plain text | Screens 20, 23, 47 and catalog route code. | The next useful action and the role of each section could be clearer. |
| Low | Privacy content is a long unsectioned reading flow | Screens 25–26. | Important storage, permission, backup, and deletion facts are harder to distinguish. |
| Low | Search filters nest a selector dialog above another dialog | Screens 33–34 and `SearchFilters.kt`. | Selection requires navigating layered modal surfaces. |
| Medium | Large text amplifies the summary and form length | Screens 55–57 at font scale 1.3: the daily summary takes the viewport, while mood options and emotion chips wrap into more rows. | Typography needs adaptable grouping as well as scalable text. |

## Usage discrepancy: traced cause

`RoomDailySummaryRepository` reads `summaryUsageSources` and calls `calculateDailySummary`, which derives usage from saved Android aggregate buckets. Its observation does not include `usage_session` or `usage_session_window`.

Settings reads `RoomUsageRepository.observeDaily`, and Weekly Review calls `RoomUsageRepository.loadDaily`. This path prefers saved sessions, clips them to the selected local day, and falls back to aggregate buckets when appropriate.

The same date and timezone can therefore show different usage values. Sorting and taking three in `DailySummaryCard` already works on the values it receives. The daily summary needs the shared saved-usage selection and matching observation/cache handling to represent the same data consistently.

## Existing protections to carry forward

- Offline storage and capture.
- Draft retention, unsaved-change confirmation, disabled/busy states, retry, and deletion confirmation.
- A persistent Quick Capture entry point on the five primary tabs.
- The distinction between missing measurements, recorded zero, and incomplete session history.
- Date/timezone semantics, same-entry memory promotion, and existing content/catalog/photo relationships.
- Explicit permission explanations and backup/reset consequences.
- English, Persian RTL, and French resources.

## Review limits

This is a UI/UX audit, not a completed redesign or a full accessibility/performance certification. A conventional docked keyboard, TalkBack, larger data sets, populated galleries, populated catalog histories, and destructive/system-provider outcomes require dedicated follow-up checks.

The original English language, 1.0 font scale, light mode, hardware-keyboard setting 0, selected date (13 October), and Timeline screen were restored. The repository's existing worktree changes were preserved; this audit introduced no product-code edits.

Capture screenshots and accessibility hierarchies remain in `/tmp/luma-ux-audit/`.

## Implementation follow-up — three selected high-priority findings

The three High findings were implemented on 13 October 2026:

1. Today now uses the same saved-session usage selection as Settings and Weekly
   Review, including local-day clipping, aggregate fallback, observation updates
   and versioned cache rebuilds. Its qualifying app list remains sorted by usage
   and capped at three.
2. Today shows compact summary metrics and app names/durations. Explanations,
   sample counts, package identifiers and activity groups are expandable. The
   second count card is replaced by an entry-feed heading.
3. Usage and Health Connect settings show controls and status before expandable
   import and retention explanations, with English, Persian and French labels.

Regression checks passed: 278 JVM tests, 112 Room tests, the full app UI rerun
(53 passed and one permission-dependent native test skipped), and all five Today
tests at font scale 1.3. Debug/release builds and app/data lint also passed.
The initial Weekly Review timeout passed unchanged in isolation and on the full
rerun. Full validation evidence is recorded in the project's `docs/VALIDATION.md`.

## Additional implementation follow-up — six medium-priority findings already completed

The current repository HEAD (`29426fe`, 13 October 2026) also addresses these
six findings:

1. The light and dark Material color schemes now define the full set of color
   roles so cards, navigation, and actions use a consistent palette.
2. Day and week browsing controls are condensed into fewer rows.
3. Primary tab icons use vector drawables instead of Unicode symbols.
4. Secondary screens no longer show the primary navigation bar or Quick
   Capture action.
5. Quick Capture has a reserved row above the primary navigation, avoiding
   overlap with screen content.
6. Five-point mood and importance selectors keep their choices in one compact
   row with accessible labels.

At HEAD `29426fe`, the other findings remained open. The follow-up below records
the next three completed findings.

## Form follow-up — three more medium-priority findings completed

1. Mood, Journal, Memory, Person, and Place editors now keep their localized
   capture/edit heading and close control in a persistent top bar. Close still
   uses the existing unsaved-change confirmation, and Save/Delete stay in the
   fixed bottom action area.
2. Mood, Journal, and Memory place People, Place, Photos, and When controls
   under a localized More details disclosure. New captures start compact;
   editing an existing entry or restoring a draft with context opens the
   section so its saved context remains visible.
3. Single-line person/place names and journal/memory titles use Next to focus
   the following text field. The journal tag field uses Done to add the tag.
   Multiline notes and journal text retain the default action so Enter inserts
   a line break. The disclosure labels are localized in English, Persian, and
   French.

`git diff --check` passed, and the four changed/new XML resources parsed
successfully. The debug build could not be completed in this sandbox: the
wrapper could not create its lock under the read-only Gradle home, and direct
Gradle startup failed while initializing native/network services. Tests were
not run. Conventional docked-keyboard resizing still needs device validation.

## Detail and review follow-up — next three medium findings completed

1. Weekly Review comparison values now use compact cards that show the current
   and previous periods together, keep changes beside them, and place source
   actions in one row while allowing either action to use the full width alone.
2. Imported app usage details now group app name, duration, interval, timezone,
   source, and package in one card without repeating the event title and date
   metadata above it. Journal details show the journal body before photos and
   keep linked people and place with the secondary details.
3. Positive app usage shorter than one minute now displays localized seconds;
   sub-second usage displays “less than one second.” English, Persian, and
   French resources were updated.

`git diff --check` passed, and the English, Persian, and French string resources
parsed successfully. No tests were run. The debug Kotlin compile could not start:
the Gradle wrapper could not create its distribution lock under the read-only
Gradle home.

## Remaining UI findings follow-up — three low-priority findings completed

1. People and place catalog rows now have recognizable leading icons. Empty
   states for people, places, person history, and Timeline use localized cards
   with a clear heading and next step; the catalog create action moves into the
   empty card when there are no records.
2. Privacy and local-data information is grouped into labeled cards for on-device
   storage, optional access, backups, and deletion. The same policy sections
   appear in the Health Connect explanation. Existing copy and safeguards remain.
3. Search now displays only one modal at a time. Opening a selector or date
   picker temporarily replaces the filter dialog, which returns when the child
   surface closes.

The earlier large-text finding is addressed in code by the compact Today summary,
disclosed secondary details, one-row numeric scales, and wrapping emotion choices.
The 1.3 font-scale screens have not been rechecked after these changes. Keyboard
Next/Done actions are implemented; conventional docked-keyboard resizing still
needs device validation. TalkBack and the other review limits above remain
separate follow-up checks.

`git diff --check` passed. English, Persian, and French string resources and the
new place vector parsed successfully; each locale contains the new resource
keys. Tests were not run. The debug compile could not start because the Gradle
wrapper could not write its distribution lock under the read-only Gradle home.
