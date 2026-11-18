# Luma Android App Reference

## Snapshot and evidence conventions

This reference describes the checked-out Android app snapshot on **18 November 2026**: branch **main**, revision **73b16d3**. At capture time, the only reported working-tree item was the pre-existing untracked path **app/production/**; it was preserved and was not treated as inspected source. This document is a source audit, not a release or runtime acceptance report.

Claims are labeled by their evidence:

- **Code-confirmed** means production source contains the behavior and its route or trigger is wired.
- **Test-confirmed** means a test source asserts the behavior. A test file does not prove that the current app was run or that a user can reach the path.
- **Runtime-observed** means executed on a device during this documentation pass. **None.**
- **Unverified** means the source suggests behavior that depends on a device, external provider, OS policy, or an unrun test.

No Gradle build, test task, deploy, emulator/device interaction, or live provider check was run for this document. “Available” below describes source wiring and does not imply runtime acceptance.

## Table of contents

- [App overview](#app-overview)
- [Complete feature inventory](#complete-feature-inventory)
- [Navigation and shared behavior](#navigation-and-shared-behavior)
- [Daily views](#daily-views)
- [Capture, memories, catalog, and sharing](#capture-memories-catalog-and-sharing)
- [Habits and tasks](#habits-and-tasks)
- [Focus](#focus)
- [Android integrations and background behavior](#android-integrations-and-background-behavior)
- [Settings, presentation, and accessibility](#settings-presentation-and-accessibility)
- [Data and business rules](#data-and-business-rules)
- [Technical reference](#technical-reference)
- [Verification, discrepancies, and known gaps](#verification-discrepancies-and-known-gaps)
- [Coverage matrix](#coverage-matrix)
- [Maintaining this reference](#maintaining-this-reference)

## App overview

Luma is a personal life log and planning app. The implemented product combines dated mood and memory entries, a timeline and calendar, people/place catalogs, repeating habit tracking, dated tasks, focus sessions, weekly review, optional Android usage and Health Connect step imports, reminders, local widgets, backup/restore, and optional app lock. The active application is backed by local Room storage and private app files; several OS integrations are opt-in. This description is derived from wired application code, not a marketing or roadmap statement.

### Glossary

| Term | Meaning in this codebase |
| --- | --- |
| Life event | A dated record with a stable event ID, type and occurrence instant; typed mood, journal, memory, activity and usage details attach to the event graph. |
| Memory | A user-authored entry with required prose and optional title, importance, context note, tags, people, place and photos. |
| Memory marker | A saved marker on an existing eligible mood or journal event. It adds memory metadata while leaving the source event and its prose intact. |
| Habit definition / schedule | A named habit plus an effective-dated target, weekday set and active/paused/archived lifecycle. |
| Habit log | A count for a habit on one civil date in the habit’s saved tracking zone; optional occurrence time is stored separately. |
| Task family | A root task and its direct child tasks. The domain contract permits one child level, not nested descendants. |
| Focus run / session | A session is one user focus period; its runs are the separately credited intervals divided by pauses/resumes. |
| Import zone | The saved time zone used to define imported health day windows. It can differ from a later device zone. |

### Platform, SDK, build, language

- Android app ID/namespace: **com.luma.lifeos**. The app module compiles against SDK 35, targets SDK 35 and sets minimum SDK 28 (Android 9). Java/Kotlin toolchain is 17. See [app/build.gradle.kts](../app/build.gradle.kts) and [settings.gradle.kts](../settings.gradle.kts).
- The app module has a production flavor and debug/release build types; debug uses a **.qa** application ID suffix. Release enables code shrinking and resource shrinking. The app bundle language split is disabled so the bundled app language resources remain available. Build behavior and versioning are in [app/build.gradle.kts](../app/build.gradle.kts).
- The default Android build includes :app, :core:data, :core:domain, :integration:health and :integration:usage. The domainOnly=true Gradle property includes only :core:domain; it is not the installable app. The version catalog in this snapshot declares AGP 8.7.3, Kotlin 2.2.10, Compose BOM 2025.08.01, Room 2.8.5, Navigation Compose 2.9.3, WorkManager 2.10.0 and Health Connect 1.1.0-beta01; see [gradle/libs.versions.toml](../gradle/libs.versions.toml).
- App language choices are English, French and Persian. App language selection uses Android’s application locale API on API 33+ and a Luma preference plus Activity recreation on older supported versions. Persian sets RTL layout direction. The manifest declares RTL support and locale configuration. Source: [AppLanguages.kt](../app/src/main/java/com/luma/lifeos/language/AppLanguages.kt), [locales_config.xml](../app/src/main/res/xml/locales_config.xml).
- The theme choice is System, Light or Dark, defaulting to System. Health Connect and Usage Access are optional provider/device capabilities; notifications require Android notification access and may be disabled by OS/channel settings. Details are in [settings and integrations](#settings-presentation-and-accessibility).

## Complete feature inventory

“Production-wired” means the normal app graph supplies the repository or integration. “Conditional” means the route is registered only when its provider exists or it depends on an external system capability. “Partial/unreachable” is an implementation limit, not a roadmap status.

| Feature | Entry point or trigger | Implementation status | Dependencies, permissions, or availability | Details |
| --- | --- | --- | --- | --- |
| Today | Initial destination and bottom navigation | Production-wired | Life event, habit, summary and task inputs are wired; optional integrations add cards | [Today](#today) |
| Timeline and entry detail | Bottom navigation; tap a record | Production-wired | Timeline repository; search action is supplied when SearchRepository exists | [Timeline](#timeline) |
| Calendar and agenda | Bottom navigation | Production-wired | Calendar and task repositories; uses device zone for “today” | [Calendar](#calendar) |
| Weekly review | Bottom navigation | Production-wired | Weekly review repository; integrations contribute data only when enabled/available | [Weekly review](#weekly-review) |
| Daily summary | Today for the selected date | Production-wired | Summary repository; usage/activity inputs are included when present | [Daily summary](#daily-summary) |
| Search | Search action in timeline | Conditional route, wired in the normal app graph | Search repository; date, tag, person and place filters | [Search](#search) |
| Mood capture/edit | Today, capture sheet, widgets, review/detail actions | Production-wired | Mood repository; score required | [Mood entries](#mood-entries) |
| Memory capture/library/markers | Today, capture sheet, More, timeline/detail actions | Production-wired | Memory and photo repositories; selected image picker for regular attachment flow | [Memories](#memories-and-memory-markers) |
| Journal record support | Legacy record data and memory-compatible detail/backup paths | **Dedicated editor route is not registered**; current journal route aliases to Memory | Journal repository/model exists; current navigation must be treated as Memory flow | [Journal status](#journal-records-and-unreachable-editor) |
| People and places | More → People & Places and linked editor/detail actions | Production-wired | Person/place repositories | [People and places](#people-and-places) |
| Habits | More → Habits; Today, Timeline, Calendar, widgets and reminders link into dated habit state | Production-wired | Habit repository; reminders additionally need notification delivery | [Habits](#habits) |
| Tasks and subtasks | More → Tasks; Today, Calendar, widgets and Focus task selection | Production-wired | Todo repository; reminders need notification delivery | [Tasks](#tasks) |
| Focus sessions, goals and history | More → Focus, Today, task actions | Production-wired | Focus runtime, Room repository and AlarmManager for timed expiry | [Focus sessions](#focus-sessions) |
| Focus invitations | Focus settings | Production-wired schedule; system delivery is conditional | Local alarm/notification; notification access | [Focus invitations](#focus-invitations) |
| Task and habit reminders | Reminder settings and saved due/reminder schedules | Production-wired schedules; delivery conditional | POST_NOTIFICATIONS on API 33+, app/channel notification switch | [Reminders](#reminders) |
| Health Connect steps | Settings → Integrations; refresh on app resume | Optional integration, source-wired | Health Connect provider and READ_STEPS grant | [Health Connect](#health-connect-steps) |
| Usage history | Settings → Integrations; refresh on app resume and scheduled work | Optional integration, source-wired | Usage Access special app-op grant | [Usage Access](#usage-access) |
| Photo attachments and restricted camera capture | Memory editor; lock-enabled restricted capture | Production-wired | System photo picker; camera action delegates to another app using FileProvider | [Photos](#photos-and-restricted-capture) |
| Text sharing | Saved eligible entry → Share preview → Share | Production-wired | Android ACTION_SEND chooser; only on explicit confirmation | [Entry sharing](#entry-sharing) |
| Home-screen widgets | Android launcher picker; widget actions | Production-wired | Launcher support; row data can be hidden by widget configuration or lock policy | [Widgets](#widgets) |
| App lock and restricted capture | Settings, app lifecycle, protected-route gate, locked create actions | Optional but wired | Local credential/Keystore state; OS device security may affect recovery; screenshot policy varies by API | [App lock](#app-lock-and-restricted-capture) |
| Backup and restore | Settings → Backup | Production-wired | Android document picker/URI access; local storage capacity | [Backup and restore](#backup-and-restore) |
| Delete all local app data | Settings → Privacy | Production-wired | Confirmation; app lock requests fresh authentication | [Delete all data](#delete-all-local-data) |
| Appearance, language and calendar settings | Settings → Language/appearance controls | Production-wired | Luma preferences and Android locale/time settings | [Settings](#settings-presentation-and-accessibility) |

## Navigation and shared behavior

### Main and nested navigation

[LifeOsApp.kt](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt) owns Compose Navigation registration and route callbacks. The primary destinations are **Today → Timeline → Calendar → Review → More**; Today is the start destination. Top-level navigation saves and restores each destination’s back stack. The full Habits and Tasks lists are opened from More rather than occupying main-bar slots. More also links to Focus, Memories, People & Places and Settings when their callbacks/providers are present.

Nested routes cover Search, Memory Library, Focus task selection/history/session details/settings/invitations, Habits list/editor/detail/history/reminders, task list/editor, People and Places catalog/editor/detail, Settings sections, Review sources and usage, entry details, Mood/Memory editors, restricted capture, App Lock settings and recovery. Route registration and repository gates are in [LifeOsApp.kt](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt); route names are kept in [AppDestinations.kt](../app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt).

The app has a floating capture action and a quick-capture sheet. Selecting an item carries the selected day into the editor. Calendar can pass a due date to task creation. Search exposes its filters in a route-local filter surface; photo editing, task-family confirmation/Undo, share preview, backup document selection, privacy confirmation and App Lock credential/recovery flows are separate route or modal states. The lock-enabled path replaces ordinary create flows with the restricted capture screen. A running Focus session gates in-app navigation until the user pauses/stops or uses the explicit emergency exit path; it does not prevent the user leaving Android or starting unrelated apps.

[MainActivity.kt](../app/src/main/java/com/luma/lifeos/MainActivity.kt) applies edge-to-edge and the selected app locale before Compose content. It forwards new widget/reminder/focus-invitation requests, refreshes widgets and reconciles reminders on Activity start. It also applies FLAG_SECURE on API levels below 33 while the lock policy requires it. The exact Activity lifecycle and request handoff are code-confirmed; no Activity recreation or process-death scenario was run here.

### Back, editor state and external entry points

Page and editor screens use their own back/close callbacks; the shared page scaffold reserves system insets for its host and supplies accessible navigation semantics. Some state is explicitly saved: selected day uses Compose saveable state, Search uses SavedStateHandle, Mood capture snapshots and photo-draft identity are saved by their editor paths, and Memory photo files use a draft lease. These mechanisms do not establish that every form’s unsaved text survives process death; each flow must be checked independently. See [AppPageScaffold.kt](../app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt), [SearchViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt), [MoodEditorViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt) and [PhotoDraftSession.kt](../app/src/main/java/com/luma/lifeos/ui/photos/PhotoDraftSession.kt).

External entry points are the launcher, three home-screen widget provider types, local reminder/focus notifications and their action receivers, Android’s Health Connect rationale/permission surfaces, and the system photo picker/camera app. The inspected manifest has no Luma web/deep-link route filter. Notification/widget action delivery after force-stop, OEM battery restrictions, launcher caching and reboot were not runtime-checked.

## Daily views

### Today

Today opens on the current device-zone civil date. Its date controls let the user inspect another day or return to today. The date is passed to capture and daily queries; underlying event timestamps remain instants. The Today screen composes recent life-event context, a daily summary, habit progress/log actions, task preview and Focus state when those inputs are available. See [TodayRoute.kt](../app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt), [TodayScreen.kt](../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt), and the route callbacks in [LifeOsApp.kt](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt).

The Today task preview is bounded (domain default: three rows); opening the list gives the larger task query. Habit actions write dated logs and offer Undo where the action supports it. Task completion is revision-checked and may require confirmation when completing a parent affects open children. Load/write failures have explicit retry or error state in the relevant child components. The summary is cached per date and zone; its inputs exclude entry prose and photos. A successful query can still show no summary inputs; that is not a sync failure.

### Daily summary

The Today summary is calculated for a civil date and ZoneId and cached by that pair. It counts non-usage event records, journal/memory records, mood score/energy/stress samples and averages, activity totals grouped by type/unit/source, and usage totals grouped by native bucket start. Mood averages are arithmetic mean values over the available samples; missing optional energy/stress samples are omitted rather than treated as zero. Entry prose, notes and photos are not summary inputs. No matching rows is a valid empty result. Calculation and storage are in [DailySummary.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/summary/DailySummary.kt) and [RoomDailySummaryRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomDailySummaryRepository.kt); the card is [DailySummaryCard.kt](../app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt).

### Timeline

Timeline shows a paged, date-grouped event feed. A row opens the saved entry/detail path or linked habit-log details. Search is entered from Timeline when the search provider exists. Paging and date-grouped presentation are wired through [TimelineViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewModel.kt), [TimelineRoute.kt](../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt) and [RoomTimelineRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTimelineRepository.kt).

The feed can include mood, journal/memory, activity/usage and habit-log records according to the repository projection; the existence of a database row alone does not guarantee a standalone editor or route. Record details retain their source event IDs, which are used for edit/delete/share eligibility. Timeline loading, empty and paging behavior is covered by test sources but was not executed in this pass.

### Calendar

Calendar displays a month grid and selected-day agenda. Its main route combines dated events/memories with scheduled tasks; current source does not wire habit rows into the calendar agenda. A selected date can open the dated capture flow or create a task with that due date. The Persian/Gregorian calendar preference changes presentation and date labels, not stored event instants or Room chronology. Persian month grids are Saturday-first; Gregorian grid week order follows the resolved display locale. The calendar choice is separate from the app language: System calendar choice resolves from the primary system locale. See [CalendarRoute.kt](../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarRoute.kt), [CalendarGrid.kt](../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarGrid.kt), [CalendarViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarViewModel.kt) and [RoomCalendarRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt).

The route needs Calendar and Todo repositories; without them the app displays its unavailable state. Date boundaries use local civil dates and the device time zone. Calendar system selection affects grid and formatted display; a future date is not an event occurrence until an editor saves a timestamp/task. Calendar acceptance across locales, DST boundaries and device sizes remains unverified on-device.

### Weekly review

The active review route is **Weekly Review**, not a monthly reflection. It compares the current week-to-date with the same number of civil dates in the previous week. The first day follows the saved week-start preference; future weeks are rejected. A partial current week ends today in the selected device zone. If WeeklyReviewRepository is absent, the registered Review destination falls back to the older Memory Review route; the normal application graph supplies Weekly Review. The period and comparison logic are in [WeeklyReview.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt).

Review aggregates event counts, journal/memory counts, mood/energy/stress averages, emotion/person/place frequencies, activity totals and (when available) daily usage summaries. A score average is arithmetic sum divided by sample count; optional energy/stress averages use only records that contain that value. Activity values are added by type, unit and source, with distinct active dates also counted. Review deliberately reads no entry bodies, notes or photos. Usage comparison exposes coverage/completeness separately from a zero value. A separate habit-week comparison is shown when the HabitRepository is supplied. When Focus repositories are available, Review also shows weekly session/goal reports and current-session context; task-origin details are subject to App Lock access. Tapping a metric can open its source records or catalog; unavailable inputs have their own state. UI and repository source: [WeeklyReviewRoute.kt](../app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt), [RoomWeeklyReviewRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomWeeklyReviewRepository.kt).

### Search

Search is a Timeline action in the normal app graph. The screen supports text, inclusive from/through dates, multiple tags, multiple people and one place. Text is capped at 200 characters and debounced by 300 ms; each tag/person selection set is capped at 100. A from-date after the through-date is invalid and suppresses the repository query. Results are newest-first and paged in windows of 50; the view model can load earlier/later windows, preserve the selected result’s offset, retry failures and independently retry search-option loading. Search criteria and the offset are written to SavedStateHandle. See [SearchViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt), [SearchRoute.kt](../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt), [Search.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/search/Search.kt) and [RoomSearchRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomSearchRepository.kt).

The blank-query state explains whether the library is empty or asks for a query; a matching query with no rows has a separate empty state. Text matching details are delegated to the Room search query; the UI’s control set should not be read as evidence that task titles or every private field are indexed.

## Capture, memories, catalog, and sharing

### Mood entries

Mood capture is available from Today, the capture sheet, quick-capture widget, and linked entry actions. It requires a score from 1 through 5. Emotion selections are optional; energy and stress are optional 1–5 scales; the note is limited to 5,000 characters. People and one place can be linked. The event stores an occurrence instant; new capture defaults to the supplied date/time or current instant. A saved mood can be edited or deleted, subject to the app-lock guard. The ViewModel tracks loading, validation, save/delete failures and delete recovery status. Source: [Mood.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Mood.kt), [MoodNotePolicy.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/MoodNotePolicy.kt), [MoodEditorViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt), [MoodEditorScreen.kt](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt), [RoomMoodRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMoodRepository.kt).

### Memories and memory markers

A direct Memory requires nonblank text (up to 50,000 characters). Optional title is limited to 200 characters; context note to 10,000; importance to 1–5; tags to 50 normalized names, each up to 50 characters; selected people are capped at 100; one place and up to 20 photos can be attached. The editor supports date/time, edit, remove, tag/people/place selection, photo editing and save error/retry feedback. Domain validation is in [Memory.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Memory.kt); UI state and write handling are in [MemoryEditorViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorViewModel.kt).

An eligible saved mood or journal event can instead be marked as a memory. A marker can add importance, note, people, place, photos and tags, but it does not copy or replace the source content. Removing a marker removes the marker relationship; it is not the same operation as deleting the source event. The Memory Library lists direct memories and marked records and opens their existing detail/edit path. Source: [RoomMemoryRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt), [MemoryLibraryRoute.kt](../app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryRoute.kt), [LifeEvent.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/LifeEvent.kt).

### Journal records and unreachable editor

Journal model/repository and database support remain in source. Journal input requires nonblank body up to 50,000 characters, allows title up to 200, and up to 50 tags of at most 50 characters, plus people/place/photos. However, current navigation maps the legacy journal route to Memory and the standalone JournalEditorRoute is not registered in the active route graph. Therefore this snapshot does **not** establish a reachable dedicated Journal creation/edit workflow. Old journal records can still participate in memory-marker/detail/review/backup paths where their type is supported. See [Journal.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Journal.kt), [RoomJournalRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomJournalRepository.kt), [LifeOsApp.kt](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt) and [JournalEditorRoute.kt](../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorRoute.kt).

### Photos and restricted capture

Regular entry photo selection uses Android’s system Photo Picker with image-only selection. Luma copies selected data into its app-private photo store and tracks draft file ownership so abandoned edits can release files. Model limits are 20 photos per entry, 25 MiB per photo and 40 million pixels; previews use a 640-pixel edge. Crop/rotate edits are represented in the photo editor path. See [Photo.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Photo.kt), [PhotoDraftSession.kt](../app/src/main/java/com/luma/lifeos/ui/photos/PhotoDraftSession.kt), [MemoryPhotoEditor.kt](../app/src/main/java/com/luma/lifeos/ui/memory/MemoryPhotoEditor.kt) and [RoomPhotoRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomPhotoRepository.kt).

When App Lock is enabled, create actions can open the restricted-capture screen instead of browsing protected records. Its active UI supports a subset of Mood, Memory, Task and Habit-definition creation; it can select a photo or delegate camera capture to another app through a non-exported FileProvider URI. Luma does not declare CAMERA permission in the inspected source manifest. Sensitive draft data is encrypted with an Android Keystore AES-GCM key while in the restricted draft store; committing writes the corresponding normal feature records. Camera-app availability, permission prompts owned by that app, and recovery after killing the process mid-capture were not observed. Source: [RestrictedCaptureRoute.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/RestrictedCaptureRoute.kt), [RestrictedCaptureService.kt](../app/src/main/java/com/luma/lifeos/lock/RestrictedCaptureService.kt), [PrivateCaptureDraftStore.kt](../app/src/main/java/com/luma/lifeos/lock/PrivateCaptureDraftStore.kt).

### App lock and restricted capture

App Lock is optional and disabled until a policy is configured. The coordinator loads policy before protected destinations are drawn; while authorization is unavailable or missing, the gate avoids rendering private previews. The policy governs protected sections, local navigation grants and when the app relocks after leaving the foreground. Settings manage the lock policy and credentials. Sensitive operations such as sharing, archive export/restore and ordinary data reset require a fresh authorization proof when the lock is enabled. See [AppLockCoordinator.kt](../app/src/main/java/com/luma/lifeos/lock/AppLockCoordinator.kt), [AppLockGate.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockGate.kt), [AppLockSettingsRoute.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt) and [LockSensitiveOperationGuard.kt](../app/src/main/java/com/luma/lifeos/lock/LockSensitiveOperationGuard.kt).

If the user forgets the credential, the recovery path is erase-only: it does not unlock or export protected records and requires explicit confirmation before requesting deletion. An emergency Focus exit releases the Focus navigation guard, but does not authorize access to saved protected content. On API levels below 33, MainActivity applies FLAG_SECURE while policy requires screenshot protection; the full per-OS screenshot behavior was not observed here. The lock does not stop a running Focus timer or prevent leaving Android. Recovery and lock-state handling are implemented in [AppLockRecoveryRoute.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockRecoveryRoute.kt), [AndroidLockCrypto.kt](../app/src/main/java/com/luma/lifeos/lock/AndroidLockCrypto.kt) and [MainActivity.kt](../app/src/main/java/com/luma/lifeos/MainActivity.kt).

The restricted-capture route is the create-only path described in [Photos and restricted capture](#photos-and-restricted-capture); it lets supported entries be prepared without browsing protected content. Its temporary draft is encrypted locally, while the saved feature record uses the regular repository and lock policy.

### People and places

People and places are reusable local catalogs. Person name is required and limited to 200 characters; note is optional up to 10,000. Place name is required and limited to 200; note is optional up to 10,000. The place model can carry paired latitude/longitude, but the reviewed editor flow does not establish a user-facing location acquisition capability. Events can link multiple people (maximum 100) and at most one place. Detail/history views show associated saved records. Create, update and delete paths are in [PeopleRoute.kt](../app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt), [PlacesRoute.kt](../app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt), [RoomPersonRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomPersonRepository.kt) and [RoomPlaceRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomPlaceRepository.kt).

### Entry sharing

Share is offered for eligible saved entries through an editable plain-text preview. Default options include date and note; people, place, energy, stress and source are opt-in. The user must confirm Share, after which Luma launches Android’s text ACTION_SEND chooser. The launcher returns “opened” when it hands off to the chooser; this does not prove a recipient received or stored the text. The preview has a 65,536-byte UTF-8 cap, re-reads the source before launch, and excludes photos. A stale/missing source, blank output, lock check or launch failure can stop the operation. See [EntrySharePolicy.kt](../app/src/main/java/com/luma/lifeos/sharing/EntrySharePolicy.kt), [EntryShareViewModel.kt](../app/src/main/java/com/luma/lifeos/sharing/EntryShareViewModel.kt), [EntryShareRoute.kt](../app/src/main/java/com/luma/lifeos/sharing/EntryShareRoute.kt) and [AndroidTextShareLauncher.kt](../app/src/main/java/com/luma/lifeos/sharing/AndroidTextShareLauncher.kt).

## Habits and tasks

### Habits

A habit has a required name (up to 100 code points), optional unit (up to 30), start date, target from 1 to 999, and a non-empty set of scheduled weekdays. A habit’s tracking zone is saved with its dated records. A scheduled day can have no records, a partial count or a reached target; non-scheduled, paused and archived days are distinct states. Logging cannot target a future date. A log can be a date-only count or have an optional local occurrence time validated against its tracking zone; DST gaps and future wall times have explicit handling. See [HabitModels.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitModels.kt), [HabitRepository.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitRepository.kt) and [HabitReminderPlanner.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitReminderPlanner.kt).

Editing a habit already in progress creates an effective-dated schedule change from the next eligible date rather than rewriting prior days. Pause/archive are lifecycle changes; archive does not mean delete. The UI supports list, editor, detail/history, daily log/correction and Undo paths, with retry state for repository failures. Today previews and launcher widgets can add one log; Review can aggregate habit state. Source: [RoomHabitRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHabitRepository.kt), [HabitsScreen.kt](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt), [HabitDetailRoute.kt](../app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt).

### Tasks

A task title is required, trimmed, single-line and limited to 200 Unicode code points. Optional note is limited to 4,000 code points. Due date is optional; due time requires a due date and has minute precision. Due years are bounded to 1–9999. Importance is None/Low/Medium/High and defaults to None. Completion stores a timestamp. See [TodoModels.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt).

Task lists support open/completed views, importance filtering, title search, ordering and bounded loading. The normal open-task ordering is by due date/time and stable creation identity; the optional importance sort places higher importance first and uses the base order for ties. Completed tasks are ordered by most recent completion. An unset due date is a valid state. Tasks can be edited, completed/reopened and deleted; completion and family mutations are revision-checked. Deletion/completion offers confirmation or Undo where the affected task family requires it. Child tasks are one level below a root, have independent content and due details, and are ordered within their family; a parent’s completion can be offered/confirmed when children remain open. Reopening a child may reopen its completed parent to preserve family state. Focus sessions may be associated with a task; deletion/unlinking clears the live association while preserving that a session originated from task context. Source: [TodoRepository.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt), [RoomTodoRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt), [TodoListRoute.kt](../app/src/main/java/com/luma/lifeos/ui/todo/TodoListRoute.kt).

Tasks appear in Today, Calendar, widgets and reminder flows. Current models and schedulers contain no recurring-task rule. Recurrence from older requirements is not an implemented task capability in this snapshot.

## Focus

### Focus sessions

Focus is an in-app focus timer and session history. It does not enforce OS-wide app blocking. A session can count up or use a timed target; timed targets are whole minutes from 1 to 180. Start, pause, resume and stop mutate durable session/run state. A session’s credited time is the sum of its runs; paused time is excluded. User stop and target reached are distinct terminal reasons. Focus allows optional name/tags/reflection, weekly goals and task association. After a session reaches a terminal state, a separate break countdown can be started for 1–60 minutes (default 5); it is local countdown state, does not create a Focus session and contributes no credited focus time. Session history and details are available from Focus routes. Domain state and rules: [FocusSession.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusSession.kt), [FocusRuntime.kt](../app/src/main/java/com/luma/lifeos/focus/FocusRuntime.kt), [FocusCoordinator.kt](../app/src/main/java/com/luma/lifeos/focus/FocusCoordinator.kt), [RoomFocusRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/focus/RoomFocusRepository.kt).

The runtime checkpoints the active run and reconciles process/boot/clock changes. It distinguishes recovery-required states rather than silently crediting uncertain elapsed time. A timed-session expiry uses AlarmManager and may post a notification if notifications are allowed; it does not use an exact-alarm permission and does not keep a foreground service or wake lock running for the whole session. The Focus navigation guard only controls navigation within Luma. An emergency exit releases that in-app guard and does not grant access to protected records. Recovery behavior depends on device elapsed time, boot identity and wall clock; it was not exercised here. See [FocusExpiryPlatform.kt](../app/src/main/java/com/luma/lifeos/focus/FocusExpiryPlatform.kt).

Focus settings also support allowed-app shortcuts. Only apps explicitly selected by the user can be launched from that shortcut path; Android does not block other apps. Daily/weekly focus goals and break/invitation preferences are local app state. Exact targets and status are read from saved focus configuration at use time.

### Focus invitations

A user can configure local weekday/time prompts from Focus settings. The scheduler computes the next local slot; a daylight-saving gap moves it to the first valid local time, and an overlap uses the earlier offset. A slot stays eligible for one hour after its due time. Invitations are local notification/alarm work and are not messages sent to another person. Sources: [FocusInvitationRules.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusInvitationRules.kt), [FocusInvitationSettings.kt](../app/src/main/java/com/luma/lifeos/ui/focus/FocusInvitationSettings.kt), [FocusInvitationWork.kt](../app/src/main/java/com/luma/lifeos/reminders/FocusInvitationWork.kt).

## Android integrations and background behavior

### Permissions and capabilities

| Capability | Source declaration / grant | Used by | When unavailable |
| --- | --- | --- | --- |
| Notifications | POST_NOTIFICATIONS on API 33+; app notification switch and channel must also allow delivery | Task/habit reminders, Focus expiry/invitations | Settings reports blocked/required status; alarms/schedules may remain configured but no user-visible notification is guaranteed |
| Health Connect steps | READ_STEPS from health integration module; provider permission granted through Health Connect | Daily step import | Provider missing/update-required or grant missing becomes an explicit status; no import is committed |
| Usage Access | PACKAGE_USAGE_STATS and Usage Access app-op in Android settings | Foreground-use import | Status becomes access-required/locked/unavailable; no new observation is imported |
| Boot reception | RECEIVE_BOOT_COMPLETED | Reconcile reminders and scheduled local work | Android may defer delivery; rescheduling was not tested after actual reboot |
| Photos | System image picker; camera delegated through TakePicture intent and a constrained FileProvider URI in restricted capture | Memory photos and lock-enabled capture | Picker/camera cancellation leaves the draft unchanged or removes temporary capture output |

No camera, microphone, location or broad external-storage permission is declared in the inspected app source manifests. This does not imply that an external picker/camera/provider has no independent permission model.

### Health Connect steps

Health integration reads step counts only. It requests permission through the Health Connect flow after the app’s rationale/continue path. It does not write Health Connect records and does not import workouts or sleep in this snapshot. HealthSyncer queries seven daily windows in the saved import zone, capped at query time for today. It verifies access before and after the query and rejects partial/conflicting window sets rather than committing an incomplete batch. Turning the integration off cancels an in-flight read and stops new imports; saved observations remain. See [HealthSyncer.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/health/HealthSyncer.kt), [HealthModels.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/health/HealthModels.kt), [HealthForegroundRefresh.kt](../app/src/main/java/com/luma/lifeos/ui/health/HealthForegroundRefresh.kt), [HealthConnectPlatform.kt](../integration/health/src/main/kotlin/com/luma/lifeos/integration/health/HealthConnectPlatform.kt).

When the app returns to RESUMED, enabled health integration refreshes access and imports if status allows it. Manual refresh is available in settings. No periodic health worker was found. Provider availability and grant/revocation behavior require device/provider verification.

### Usage Access

Usage integration reads Android UsageStats foreground application buckets and reconstructs available app sessions. It does not mean total device screen-on time: per-app foreground buckets can overlap, and Android’s available history may be incomplete. Buckets are deduplicated and clock discontinuity/overlap checks quarantine ambiguous imports. Reports label bucket starts in the requested zone; zero/no data is distinguished from unavailable history. The threshold for display is 60 seconds. See [UsageSyncer.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/usage/UsageSyncer.kt), [UsageModels.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/usage/UsageModels.kt) and [AndroidUsagePlatform.kt](../integration/usage/src/main/kotlin/com/luma/lifeos/integration/usage/AndroidUsagePlatform.kt).

Usage Access must be granted in Android’s special app access settings. If enabled, usage refresh occurs when Luma resumes and through a unique periodic WorkManager job scheduled for six-hour intervals; retry/backoff and attempt limits are enforced by the worker. Opting out cancels scheduled usage refresh and results from an in-progress native query are discarded before repository commit. Existing imported rows are retained. Source: [UsageForegroundRefresh.kt](../app/src/main/java/com/luma/lifeos/ui/usage/UsageForegroundRefresh.kt), [UsageWorker.kt](../app/src/main/java/com/luma/lifeos/usage/UsageWorker.kt), [UsageWorkScheduler.kt](../integration/usage/src/main/kotlin/com/luma/lifeos/integration/usage/UsageWorkScheduler.kt).

### Reminders

Task reminders are based on due date and optional due time. Habit reminder schedules can use selected local times or intervals; planner rules enforce quiet hours and schedule limits. Quiet hours default on from 22:00 to 08:00. Private notification previews default off; when enabled or when App Lock requires protected previews, notification text is generic. Task notification actions include Complete and Snooze; habit actions include Log One and Snooze, with a default 10-minute snooze. Action tokens and saved row identity are revalidated to reject stale actions. Sources: [ReminderPreferences.kt](../app/src/main/java/com/luma/lifeos/reminders/ReminderPreferences.kt), [ReminderPlatform.kt](../app/src/main/java/com/luma/lifeos/reminders/ReminderPlatform.kt), [ReminderWork.kt](../app/src/main/java/com/luma/lifeos/reminders/ReminderWork.kt), [TaskReminderPlanner.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TaskReminderPlanner.kt).

Reminders use inexact AlarmManager delivery rather than exact-alarm permission. WorkManager reconciliation runs on app start, relevant database/state changes and boot/time-zone/time changes. Denying notification permission or disabling the app/channel switch blocks visible delivery but does not mean the saved reminder was deleted. OS scheduling delays, OEM restrictions and notification action behavior were not exercised.

### Widgets

The manifest declares three home-screen providers: Quick Capture, Tasks and Habits. Quick Capture is a static responsive RemoteViews widget whose actions open Mood or Memory capture; compact sizes can open the quick-capture route. Task and Habit widgets show current-date summary/counts and, when enabled per widget instance, names, progress and row actions. A launcher controls sizing and can cache its own view.

Task/habit widget actions run through WorkManager, revalidate opaque action tokens and record/date identity, and apply App Lock policy before mutation. Protected content is redacted according to widget policy. A permitted completion/log action can expose feedback/Undo, while stale or protected actions are rejected or routed to app confirmation. Widgets refresh on application/database changes and relevant system date/time/locale/boot/theme events; active data widgets also use scheduled refresh. See [QuickCaptureWidgetProvider.kt](../app/src/main/java/com/luma/lifeos/widget/QuickCaptureWidgetProvider.kt), [WidgetActionWorker.kt](../app/src/main/java/com/luma/lifeos/widget/WidgetActionWorker.kt), [WidgetRefreshCoordinator.kt](../app/src/main/java/com/luma/lifeos/widget/WidgetRefreshCoordinator.kt), [WidgetSettingsRoute.kt](../app/src/main/java/com/luma/lifeos/ui/widgets/WidgetSettingsRoute.kt), and [quick_capture_widget_info.xml](../app/src/main/res/xml/quick_capture_widget_info.xml), [tasks_widget_info.xml](../app/src/main/res/xml/tasks_widget_info.xml) and [habits_widget_info.xml](../app/src/main/res/xml/habits_widget_info.xml).

The widget contract is a specialized launcher presentation and uses RemoteViews resources rather than the in-app Compose component tree. Details and row actions are launcher-visible content; the settings surface describes this choice. Launcher cache clearing/removal is outside Luma’s control.

## Settings, presentation, and accessibility

### Settings inventory

| Setting | Default and persistence | Effect |
| --- | --- | --- |
| Theme | System; SharedPreferences | System follows Android appearance; Light/Dark select app palette. |
| Language | Resolve supported device language, otherwise English; API 33+ application locales, older SharedPreferences | English/French/Persian strings, formatting and layout direction; selection can recreate Activity. |
| Calendar system | System; SharedPreferences | System resolves Persian when primary system locale is Persian, Gregorian otherwise; explicit Persian/Gregorian choices override display calendar. |
| Week start | Locale’s first weekday; separately persisted preference | Controls weekly review grouping/comparison; it does not change event timestamps. |
| Quiet hours | Enabled, 22:00–08:00; SharedPreferences | Suppresses scheduled reminder delivery in the quiet interval according to planner rules. |
| Private notification previews | Off; SharedPreferences | Uses generic reminder text when enabled. Lock policy can require generic text independently. |
| Health import | Off until user enables; Room integration state | Controls step import and its foreground refresh behavior. |
| Usage import | Off until user enables; Room integration state | Controls Usage Access imports and scheduled refresh. |
| Widget instance options | Per widget in Room | Controls row details/actions and current widget projection. |
| Focus options | Selected app packages in SharedPreferences; goals and invitations in feature repositories | Selected-app shortcuts, weekly goal configuration and local weekday/time invitations. |
| App Lock | Off until configured; local lock store | Protects selected content/actions and lock-gated navigation. |
| Backup/restore | User initiated; no automatic cloud sync | Exports/imports portable archive data. |
| Delete all app data | User initiated with confirmation | Requests Android app-data reset after explicit confirmation. |

Language/date preferences and failure handling are wired in [AppLanguages.kt](../app/src/main/java/com/luma/lifeos/language/AppLanguages.kt), [CalendarPreferences.kt](../app/src/main/java/com/luma/lifeos/calendar/CalendarPreferences.kt), [CalendarSettingsCard.kt](../app/src/main/java/com/luma/lifeos/ui/settings/CalendarSettingsCard.kt), [WeekStartSettingsCard.kt](../app/src/main/java/com/luma/lifeos/ui/settings/WeekStartSettingsCard.kt) and [ThemeSettingsCard.kt](../app/src/main/java/com/luma/lifeos/ui/theme/ThemeSettingsCard.kt).

### Design system, accessibility, scaling and insets

The in-app Compose UI uses the warm-paper/obsidian Luma theme, shared Material color roles, typography and shapes. Shared spacing/sizing tokens include 20 dp page gutters, 600 dp reading width, 16 dp section padding/gap, 8 dp content gap, 12 dp choice gap, 24/16/12 dp radii, 24 dp icons, 48 dp minimum touch target and 56 dp primary actions. Reusable page scaffolds provide heading/navigation semantics and leave window insets to their content hosts. Sources: [Theme.kt](../app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt), [LumaTypography.kt](../app/src/main/java/com/luma/lifeos/ui/theme/LumaTypography.kt), [LumaUiTokens.kt](../app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt), [LumaUiComponents.kt](../app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt), [AppPageScaffold.kt](../app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt).

Bundled Newsreader, Plus Jakarta Sans and Vazirmatn fonts are stored locally with license records, so those font files do not require a network fetch. Persian uses RTL layout. Some screens expose headings, live/accessible feedback, content descriptions and test tags. The codebase includes accessibility-specific widget/task tests and a shared 48 dp touch token, but this is not evidence of full TalkBack, contrast, keyboard, large-text or every-route accessibility conformance. Compose text generally uses scalable sp; no all-device runtime text-scale matrix was run. The app has a 600 dp reading pane and scrollable screens, but a tablet/foldable/desktop navigation adaptation was not verified. Inset, keyboard, scroll and FAB-clearance behavior are maintained in route scaffolds and source tests; none was manually exercised here. Launcher widgets intentionally have their own RemoteViews contract.

## Data and business rules

### Entities, relationships and ownership

Room database **luma.db** is version 17, has explicit migrations and exported schemas. It stores life events and typed mood/journal/memory details; people, places, emotions and tags; event-to-catalog links and photo metadata; habits/schedules/logs/reminders; tasks/families/reminders; daily summaries; usage records/sessions and integration status; widget configuration/action state; and Focus sessions/runs/tags/goals/invitation schedules. Primary wiring is in [LumaDatabase.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt), [Entities.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/entity/Entities.kt) and [LumaStorage.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt).

Photo bytes live under the app’s private filesDir/photos; temporary imports and backup staging use app-private working directories. Theme, language (on older Android), calendar, week-start and reminder presentation preferences use SharedPreferences or Android application locale state. There is no login/account backend in the inspected app graph. Repositories expose domain-facing flows; Room implementations perform transactions and preserve event/family revision rules.

### Time, persistence, deletion, restore

Life event occurrences are Instants and are grouped for display/query using a supplied/device ZoneId. Tasks use civil due dates plus optional minute-precision local time. Habits save a tracking zone with logs; Health Connect saves its import zone. Thus changing device time zone can change display-day mapping for event instants while existing date-only habit/health records retain their stored civil date/zone semantics. Persian/Gregorian selection is presentation only.

Room migrations evolve schema; this does not itself document backup compatibility. Entity repository commands validate inputs and use transactions/revision tokens for concurrent or retried writes. Deleting a catalog item can change the links shown by its histories; event existence and event body remain governed by their own repository operation. Retention beyond explicit delete/reset was not established as a separate policy.

### Backup and restore

Settings provides user-directed local archive export and restore through Android document URIs. Android automatic backup is disabled in the app manifest (allowBackup=false and fullBackupContent=false); the manifest also excludes app data from system data extraction. The version 14 manual archive is a ZIP with table payloads and photo bytes; a manifest stores expanded sizes and SHA-256 hashes for integrity/validation. This integrity check is **not encryption or confidentiality**. Restore extracts to a staging location/database, validates the archive/schema and relationships, then replaces the local database data. See [BackupArchive.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt), [RoomBackupService.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt) and [BackupViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt).

Focus export includes terminal sessions and portable focus history/goal data; running sessions are excluded, and paused sessions require an explicit stop-and-include choice or explicit exclusion. Device-local state such as lock credentials, active runtime checkpoints, local reminder/invitation delivery state and selected app shortcuts is not portable. Restore does not import lock credentials. The exact item list follows archive adapters, not the Room entity list; verify the adapter whenever an entity is added.

### Delete all local data

Settings → Privacy asks the user to confirm before requesting a full app-data reset. If App Lock is enabled, the route obtains fresh authentication before that confirmation can proceed. AndroidAppDataReset prepares the Focus runtime, cancels Luma’s reminder alarms and calls Android’s clearApplicationUserData API, which stops the app and asks Android to clear the whole app sandbox, including stores and work state. A true result means the OS accepted the request; Luma does not independently verify that erasure finished. If the OS rejects or the call fails, the code resumes Focus where possible and re-enqueues reminder reconciliation. Source: [PrivacySettingsRoute.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt), [PrivacyViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacyViewModel.kt) and [AppDataReset.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt).

### External data and transmission

The reviewed app source manifest declares no INTERNET permission. No app-owned HTTP client/server upload path was found in the scanned production source. The code does invoke Android-owned surfaces: Health Connect, Usage Access settings, the system photo picker/camera app, Android’s text share chooser and the Play Store listing for the Health Connect provider. Text leaves Luma only after the user confirms Share and chooses a target. This is a static source observation, not a merged-manifest/network-traffic audit; no runtime traffic was captured. Usage and step records are imported from their local Android/provider APIs into Luma’s local database.

## Technical reference

### Module responsibilities

| Module | Responsibility |
| --- | --- |
| :app | Compose screens/navigation, application/Activity lifecycle, ViewModels, Android permissions/intents, WorkManager/AlarmManager, widgets, lock and platform adapters. |
| :core:domain | Kotlin domain models, validation, planners, calculations and repository/integration interfaces. |
| :core:data | Room schema/migrations, Room repositories, file-backed photos and backup archive/restore. |
| :integration:health | Health Connect step-reading adapter and provider settings intents. |
| :integration:usage | Android UsageStats adapter, Usage Access status/query implementation. |

Dependency injection is manual: [LumaApplication.kt](../app/src/main/java/com/luma/lifeos/LumaApplication.kt) owns the lazy [LumaStorage](../core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt), integration instances, lock/focus runtimes and platform services; it passes repositories into [MainActivity.kt](../app/src/main/java/com/luma/lifeos/MainActivity.kt) and the app navigation graph. Feature routes create ViewModels with Compose viewModel factories and collect StateFlow using lifecycle-aware collection. Write flows normally pass through a domain interface into a Room repository; Android-specific capabilities are represented by interfaces/adapters in the integration/app modules.

### Useful extension points

- Add or change domain constraints/calculations in the relevant :core:domain model/rules, then update the Room adapter and UI validation; keep both paths aligned.
- Register reachable screens and preserve route arguments/callbacks in [LifeOsApp.kt](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt) and [AppDestinations.kt](../app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt).
- Add persisted fields through Room entities, migration, exported schema and backup adapter/version as appropriate.
- Keep OS access behind the integration/app adapter and update manifest/runtime permission handling, settings status, cancellation and tests together.
- Keep shared in-app UI based on the Luma theme/tokens/components; launcher RemoteViews follow their dedicated widget contract.

## Verification, discrepancies, and known gaps

### Test evidence present in the repository

Tests were inspected as source only; they were **not executed** in this documentation pass.

| Area | Representative test source |
| --- | --- |
| Navigation, Today and persistence | [AppNavigationTest.kt](../app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt), [TodayScreenTest.kt](../app/src/androidTest/java/com/luma/lifeos/TodayScreenTest.kt), [TodayPersistenceTest.kt](../app/src/androidTest/java/com/luma/lifeos/TodayPersistenceTest.kt) |
| Timeline, Calendar, Review and summaries | [TimelineFlowTest.kt](../app/src/androidTest/java/com/luma/lifeos/TimelineFlowTest.kt), [CalendarFlowTest.kt](../app/src/androidTest/java/com/luma/lifeos/calendar/CalendarFlowTest.kt), [WeeklyReviewFlowTest.kt](../app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt), [DailySummaryFlowTest.kt](../app/src/androidTest/java/com/luma/lifeos/summary/DailySummaryFlowTest.kt) |
| Capture, photos, catalogs and search | [MoodEditorTest.kt](../app/src/test/java/com/luma/lifeos/ui/mood/MoodEditorTest.kt), [MemoryCaptureTest.kt](../app/src/androidTest/java/com/luma/lifeos/MemoryCaptureTest.kt), [PhotoCaptureTest.kt](../app/src/androidTest/java/com/luma/lifeos/PhotoCaptureTest.kt), [SearchFlowTest.kt](../app/src/androidTest/java/com/luma/lifeos/SearchFlowTest.kt), [PeopleCaptureTest.kt](../app/src/androidTest/java/com/luma/lifeos/PeopleCaptureTest.kt) |
| Habits and tasks | Habit UI tests under [app/src/androidTest/java/com/luma/lifeos/habits](../app/src/androidTest/java/com/luma/lifeos/habits), task UI tests under [app/src/androidTest/java/com/luma/lifeos/todo](../app/src/androidTest/java/com/luma/lifeos/todo), and domain rule tests such as [HabitRulesTest.kt](../core/domain/src/test/kotlin/com/luma/lifeos/domain/habit/HabitRulesTest.kt) and [TodoRulesTest.kt](../core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoRulesTest.kt) |
| Focus and privacy | Focus runtime/UI tests under [app/src/androidTest/java/com/luma/lifeos/focus](../app/src/androidTest/java/com/luma/lifeos/focus), lock/privacy tests under [app/src/androidTest/java/com/luma/lifeos/privacy](../app/src/androidTest/java/com/luma/lifeos/privacy) |
| Integrations and reminders | [HealthSyncerTest.kt](../core/domain/src/test/kotlin/com/luma/lifeos/domain/health/HealthSyncerTest.kt), [UsageSyncerTest.kt](../core/domain/src/test/kotlin/com/luma/lifeos/domain/usage/UsageSyncerTest.kt), [ReminderNotificationAccessTest.kt](../app/src/androidTest/java/com/luma/lifeos/reminders/ReminderNotificationAccessTest.kt) |
| Backup and widgets | [BackupViewModelTest.kt](../app/src/test/java/com/luma/lifeos/backup/BackupViewModelTest.kt), widget renderer/action/configuration tests under [app/src/androidTest/java/com/luma/lifeos/widget](../app/src/androidTest/java/com/luma/lifeos/widget) |

Test coverage demonstrates asserted rules in those test artifacts only. Device-dependent provider grants, alarms, notifications, reboot behavior, widget launcher caching, external sharing, privacy flags, locale rendering, RTL, adaptive sizes and process recovery remain unverified here.

### Confirmed implementation limits and documentation conflicts

- **Journal:** the model and repository exist, but the standalone editor is not registered and the legacy route resolves to Memory. Do not describe a reachable dedicated Journal composer without changing and verifying the route wiring.
- **Task recurrence:** the current task model/repository has date, time, importance and a one-level family, but no recurring schedule field or recurring-task worker. Roadmap/backlog items proposing recurrence remain unimplemented.
- **Backup encryption:** current backup has ZIP payloads and SHA-256 validation, not archive encryption. The older roadmap’s encrypted-backup requirement is not implemented by this archive code. Automatic Android backup is also disabled in the inspected app manifest; the manual archive is the implemented export path.
- **Historical tests:** the task backlog records repeated 15 November 2026 check failures in three core-domain cases: MemoryTest.directMemoryRequiresTextAndValidImportanceAndBoundedDrafts, ProtectedRecordClassifierTest.eventRecordsMatchTheIndependentFixtureProtectionSets and ProtectedRecordClassifierTest.linkedIdentityFieldsRequirePeopleIndependentlyOfJournalOrMemoryProse. The later TI-15 record says core-data and integration checks completed but app Kotlin compilation was not reached. This is historical evidence from that dated document, not a result for revision 73b16d3; no tests were run for this reference.
- **Review period:** the current screen/domain path is Weekly Review. Older monthly-reflection plans do not describe current behavior.
- **Task hierarchy/importance:** older text in the 14 November task backlog says these are absent, but current Todo models/entities/repository/UI include four importance levels and one child level. The implementation wins over that stale audit.
- **Health breadth:** current Health Connect source reads daily steps only; sleep/workout support is not wired.
- **Focus enforcement:** current code guards Luma navigation and offers selected-app shortcuts; it does not block the device from opening other apps.
- **Visual acceptance:** redesign route ledgers and older QA plans contain planned matrices, blockers and prior test results. They are historical planning evidence, not runtime proof for revision 73b16d3. See [redesign route coverage](luma-redesign/route-coverage.md), [feature roadmap](FEATURE_ROADMAP_2026-10-26.md) and [tasks improvement backlog](TASKS_IMPROVEMENT_BACKLOG_2026-11-14.md).
- **Unverified runtime:** no claim in this reference is based on a connected device. Permission dialogs, AlarmManager/WorkManager delivery, provider-specific retention, restored archive compatibility on real data, notification action dispatch, locale/device combinations and all process-death cases require a separately recorded run.

No unrelated files were changed while preparing this reference. The original untracked **app/production/** path remains outside the document’s source audit.

## Coverage matrix

This matrix maps discovered routes and platform/background entry points to the explanatory sections above. “Static only” means traced in source; this pass did not launch the path.

| Discovered surface or trigger | Coverage section | Evidence status |
| --- | --- | --- |
| Today, Timeline, Calendar, Review, More | [Navigation](#navigation-and-shared-behavior), [Daily views](#daily-views) | Static route wiring |
| Capture sheet, Search filters, photo editor, task confirmation/Undo, share/backup/privacy dialogs | [Navigation](#navigation-and-shared-behavior), [Capture](#capture-memories-catalog-and-sharing), [Tasks](#tasks), [Entry sharing](#entry-sharing) | Static route/state trace; not manually exercised |
| Search and active filter/removal states | [Search](#search) | Static route/ViewModel; tests exist |
| Mood and Memory create/edit/detail/library | [Capture](#capture-memories-catalog-and-sharing) | Static route/state; tests exist |
| Legacy Journal editor and journal alias | [Journal status](#journal-records-and-unreachable-editor) | Static route comparison; classified unreachable |
| Photo picker, photo crop/rotate, restricted camera action | [Photos](#photos-and-restricted-capture) | Static wiring; camera/provider behavior unverified |
| People, Places, catalog editors and histories | [People and places](#people-and-places) | Static route/repository trace |
| Habit list/editor/detail/history/log/undo/reminder | [Habits](#habits) | Static route/repository trace; tests exist |
| Task list/editor/family/importance/reminder | [Tasks](#tasks) | Static route/repository trace; tests exist |
| Focus timer, recovery, task association, history, goals | [Focus sessions](#focus-sessions) | Static runtime/repository trace; tests exist |
| Focus invitation schedule and notification | [Focus invitations](#focus-invitations) | Static planner/work trace; delivery unverified |
| Settings hub, language/theme/calendar/week start | [Settings](#settings-presentation-and-accessibility) | Static settings/preferences trace |
| Integration toggles and permission/status routes | [Integrations](#health-connect-steps) | Static permission/adapter trace |
| Reminder settings, alarms, actions, boot/time reconciliation | [Reminders](#reminders) | Static receiver/worker trace |
| Quick Capture, Tasks and Habits launcher widgets | [Widgets](#widgets) | Static provider/worker trace; launcher unverified |
| App Lock gate, recovery and lock-enabled create route | [App lock](#app-lock-and-restricted-capture), [Photos](#photos-and-restricted-capture) | Static lock/capture trace |
| Backup export, staging/restore and delete-all flow | [Backup](#backup-and-restore), [Delete all](#delete-all-local-data) | Static source; no archive round trip run |
| Launcher, Health Connect rationale, photo picker/camera, share chooser | [Navigation](#navigation-and-shared-behavior), [Permissions](#permissions-and-capabilities), [Sharing](#entry-sharing) | Static intent/manifest trace |
| Usage periodic work, widget refresh/action workers, focus expiry, reminder and invitation alarms | [Android integrations](#android-integrations-and-background-behavior) | Static worker/receiver trace; no live delivery check |

## Maintaining this reference

Update this document whenever a code change alters a reachable workflow, a persisted default or constraint, route/entry point, sorting/filtering/calculation rule, background trigger, permission, OS integration, backup payload, privacy boundary, accessibility behavior or test evidence. Reconcile code and tests first, then update the relevant feature explanation, inventory row, source links and coverage matrix. If documentation or plans disagree with the current code, record the discrepancy until the implementation or specification changes.

### Update checklist

- Re-trace entry point → route/state holder → domain command/repository → persistence/platform effect.
- Verify required fields, defaults, bounds, date/time-zone rules, sorting, filters and calculations.
- Record loading/empty/error/permission-denied states and retry, undo, cancellation, delete and confirmation behavior.
- Check draft/configuration/process restoration claims against explicit implementation and tests.
- Update permission, receiver, worker, alarm, notification, widget, external intent and offline/data-transfer details.
- Update Room migrations, backup adapters/schema version and local-file ownership for storage changes.
- Link production source and test source separately; label tests as unexecuted unless run and record runtime evidence only when captured.
- Re-audit the inventory and coverage matrix. Future code changes must be reconciled with this reference.
