# F15 — Memory and mood sharing specification

Date: 28 October 2026 · Priority: **P2 (proposed)** · Status: **SOURCE COMPLETE — QA PENDING / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Plan](../plans/2026-10-28-roadmap-15-memory-mood-sharing.md) · [QA scenarios](../../feature-roadmap/15-memory-mood-sharing-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and scope

Let a user share one saved memory or mood as readable text through Android's system share sheet, including to compatible messaging apps such as Telegram and Google Messages. The recipient can read the content without installing Luma. Users review and optionally edit the outgoing text before choosing a destination.

The user requested this documentation package after the sharing-format proposal. **Text-first v1 is the recommended scope used in these documents; image cards and photo sharing remain deferred.** Priority and detailed defaults below are proposed design choices for implementation review, not separately confirmed user preferences. Creating this package does not start implementation or authorize a release.

F15 is independent of F12's Markdown/text file exports, range selection and backup workflows. It does not expand F12's journal/memory-only selection to moods. No Room/archive schema change, file staging, network permission, account, new dependency or receiving-app SDK is expected. Coordinate with F06's privacy gate if app lock exists at execution time; do not require implementing F06 to ship an otherwise unlocked app.

## Approaches

| Approach | Benefit | Tradeoff / decision |
| --- | --- | --- |
| Plain text through system share sheet | Editable, readable, offline preparation; no photo/file permissions | Recommended v1; receiving apps control rendering and delivery |
| Styled image card | Preserves Luma's visual design | Deferred; needs rendering, long-text handling, file ownership and additional accessibility/privacy QA |
| Text with selected photo attachments | Useful for illustrated memories | Deferred; needs attachment selection, URI grants and receiving-app compatibility QA |

Do not promise support for every installed app: Android offers destinations that accept the payload's MIME type. Luma hands text to the user's chosen destination; it cannot certify message delivery. Platform reference: [Android — send data to other apps](https://developer.android.com/develop/ui/compose/sharing/send).

## Current source and boundaries

- [LifeOsApp.kt](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt) routes event/journal details and mood/memory editors; several memory entry points open an editor directly.
- [EventDetailRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt) displays saved moods/memories. [JournalDetailRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt) displays journals, including ones marked as memories.
- [MemoryEditorRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorRoute.kt) and [MoodEditorRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorRoute.kt) own draft/save completion; sharing must not save, discard or mutate those drafts.
- [Memory.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Memory.kt) distinguishes standalone memories from markers. [Mood.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Mood.kt) defines typed mood fields and the saved `LifeEventRecord` graph.
- [TimelineRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/TimelineRepository.kt) exposes `suspend fun get(id: EventId): LifeEventRecord?`; reuse it for saved single-record reads. Confirm committed graph semantics in its data implementation before relying on it.
- [DisplayTimePolicy.kt](../../../app/src/main/java/com/luma/lifeos/language/DisplayTimePolicy.kt) and [MoodUi.kt](../../../app/src/main/java/com/luma/lifeos/ui/mood/MoodUi.kt) own localized dates and the existing five mood labels. Reuse them.

## Proposed contracts and ownership

New declarations are proposed, not existing APIs. Keep sharing policy/formatting/state under `app/.../sharing`; pure formatting can depend on domain models but must not read Android UI or Room directly. Reuse existing repositories without adding a persistence subsystem.

- `enum class EntryShareKind { MEMORY, MOOD }` distinguishes a marked mood shared as a memory from that same event shared as a mood.
- `EntryShareRequest(eventId: EventId, kind: EntryShareKind)` is the navigation input. Never pass entry bodies in route strings or arguments.
- `EntryShareOptions(includeDate: Boolean = true, includeNote: Boolean = true, includePeople: Boolean = false, includePlace: Boolean = false, includeEnergy: Boolean = false, includeStress: Boolean = false, includeSourceContent: Boolean = false)`.
- `EntryShareSnapshot(request: EntryShareRequest, record: LifeEventRecord, zone: ZoneId)` owns a defensive immutable copy of the committed graph. `loadEntryShareSnapshot(repository: TimelineRepository, request: EntryShareRequest, zone: ZoneId): EntryShareSnapshot?` returns null for missing/ineligible records; read failures propagate to state for retry.
- `EntryShareTextContext(locale: Locale, memoryHeading: String, feelingHeading: (String) -> String, emotionHeading: String, noteHeading: String, sourceHeading: String, peopleHeading: String, placeHeading: String, energyHeading: String, stressHeading: String, scoreLabels: Map<Int, String>, formatDate: (Instant, ZoneId) -> String, formatMoodDateTime: (Instant, ZoneId) -> String)` supplies localized resources/date policy; context lives in memory only.
- `formatEntryShare(snapshot: EntryShareSnapshot, options: EntryShareOptions, context: EntryShareTextContext): String` is deterministic for its inputs and has no repository/write side effects.
- `validateEntryShareText(text: String): EntryShareTextError?` returns `EMPTY` for blank text or `TOO_LARGE` above **65,536 UTF-8 bytes**. Otherwise null. This is a proposed conservative transport bound, not an Android maximum. Never truncate silently.
- `EntryShareViewModel` owns load/ready/error/unavailable state, options, edited text and confirmation/launch consumption. `AndroidTextShareLauncher.launch(text: String): TextShareLaunchResult` returns `OPENED` or `UNAVAILABLE`; it never returns a delivered/sent result.

## Requirements

### F15-R01 — Saved-entry eligibility and reachability

Offer **Share** on loaded saved mood/memory event details, on journal details only when a memory marker exists, and in existing saved mood/memory editors so Calendar/Review entry paths can reach it. Keep the main navigation and Calendar content unchanged. New unsaved captures, loading/error/missing content and save/delete/import-in-progress states cannot share. In editors, Share is disabled while any draft change or pending photo import exists, with accessible guidance to save changes first; never silently save/discard or share an older saved version in place of visible unsaved text. Returning from preview preserves the editor draft and navigation context.

`MOOD` eligibility requires event type MOOD and non-null mood details. `MEMORY` requires non-null memory details and event type MEMORY, MOOD or JOURNAL, with required typed details present. Unmarked journals, activities and app usage are ineligible. On a marked mood, the regular mood detail action opens MOOD mode; its memory editor opens MEMORY mode. A marked journal detail opens MEMORY mode. One request represents one event, never every entry on a day or a whole journal collection.

### F15-R02 — Explicit privacy choices and review

Each newly opened preview starts with the contract defaults; do not remember a previous user's opt-in choices. Show date and note controls, and show people/place or energy/stress controls only where the respective saved values exist. Show **Include original entry content** only for a mood/journal marked as a memory in MEMORY mode; explain that it adds the original entry's text. Energy/stress controls apply in MOOD mode and, when source content is enabled, to a marked mood's source section.

Standalone memory: share its title (or localized Memory heading) and, when enabled, its memory note, falling back to event note only if the memory note is null. Mood: share its existing localized score label, selected emotions in their stored order and, when enabled, its mood note. Memory marker: share the localized Memory heading and marker note; **original title/body/mood content is excluded by default**. Explicit source opt-in adds journal title/body or mood summary/emotions and its note when includeNote is enabled, under a labeled source section. Do not emit the same note twice when marker and source notes are identical.

Linked people names and place name require separate opt-ins; never include addresses, coordinates or internal IDs. Structured journal tags, importance ratings, photo names/URIs/binaries, source references and creation/update timestamps are always excluded in v1. Explain that names already written in a note/body remain in that free text. Photos are not attached; show a short explanation for records with photos.

The preview is the complete editable outgoing text. Edits affect only this share session. Changing an option after manual text edits requires an explicit **Regenerate text** confirmation before replacing edits; cancelling retains both the current options and text. Nonblank manual edits may remove any generated section. Generated text ends with `#Luma` as a separate paragraph, visible and editable in the preview. Do not append branding, tracking links or hidden fields after preview. Users can still manually type personal details; toggles control generated fields rather than promising redaction.

### F15-R03 — Readable localized text and bounds

Use plain text, with a heading, optional localized occurrence date, a blank line, then available content/sections. Mood shares include the localized occurrence time with the date; memory shares show the date only. Do not include a zone identifier; freeze the display zone at preview creation and use Luma's existing date and time policy. Use named months/years according to locale; preserve Gregorian chronology from `DisplayTimePolicy`. The example date below is illustrative, not an exact locale assertion.

Mood labels remain Very low / Low / Okay / Good / Great through existing translations. Format selected energy/stress values as a localized label plus `n/5`. Omit missing values and empty generated sections; preserve user Unicode, emoji and newlines within included text without HTML/Markdown conversion. App-generated metadata uses plain separators and must not insert control characters or interpret user strings as formatting instructions. Generated headings for EN/FR/FA are resource-backed.

Example standalone memory:

```text
A lovely afternoon 🌿
28 October 2026

Walked through the park and watched the sunset.

#Luma
```

Example mood (energy/stress are opt-in and absent here):

```text
Feeling good
28 October 2026 14:30

Emotions: Calm, grateful

A quiet walk helped me clear my head.

#Luma
```

Validate the final edited text at confirmation and again before launch: nonblank and at most 65,536 UTF-8 bytes. Oversize output stays fully visible/editable with a clear message to shorten it; do not split messages, clip content or automatically switch to files. Whitespace used to check blankness is not trimmed from an otherwise valid outgoing string.

### F15-R04 — Exact handoff, freshness and lifecycle

Only the preview's explicit Share action may launch `Intent.createChooser(Intent(ACTION_SEND).setType("text/plain").putExtra(EXTRA_TEXT, text), ...)`. The outbound `EXTRA_TEXT` equals the validated preview string exactly. Do not set a package, recipient, subject, stream, file grant or unpreviewed content; no Telegram/Google-specific integration or broad package enumeration. Handle activity/security/transaction launch failures as retryable UNAVAILABLE with the preview retained. A successful start means chooser opened, never message sent.

Before launch, re-read the selected saved graph and recheck eligibility plus the currently active lock gate if present. If the graph differs from the preview snapshot (including linked names and marker removal), block launch and offer Refresh; deleted/ineligible records become unavailable. Do not regenerate edited text or send stale content automatically. Refresh explicitly discards the old share edits/options and starts a new preview with default choices. The freshness guarantee ends at the final read; the receiving app owns any copy already handed off. Locale/date policy or display-zone changes invalidate a preview similarly and require explicit refresh.

Guard each confirmation so rapid taps launch at most one chooser. Returning/cancelling never automatically launches again; a fresh tap may retry after availability/freshness checks. Configuration recreation may retain the in-memory session through the ViewModel, but must not replay launch events. Process death drops the share text, graph and launch token; restore at most request IDs/mode, reload and require a new explicit preview/confirmation with defaults. Do not persist share text in SavedStateHandle, disk, analytics, clipboard or logs. No clipboard action is part of v1.

If F06 app lock is active, gate loading/rendering and final launch, conceal/clear private session content when lock activates, and require a fresh preview after authentication. Do not treat the normal pause caused by chooser opening as authorization to relaunch or as a way to retract the receiver's copy. Sharing never writes event records, revision metadata, catalog links, photos, backups or drafts.

### F15-R05 — Accessible presentation and acceptance scope

Preview supports loading, missing/ineligible, load error/retry, oversize/empty text, options, text editing, regeneration confirmation, source-changed refresh and launch error states. Share remains reachable with IME/system insets, small windows, large fonts, landscape and large screens. Provide EN/FR/FA, real RTL, light/dark, TalkBack labels/state/error announcements, keyboard traversal and 48dp minimum action targets using existing UI patterns.

Native QA must check the chooser and the text handed into **Telegram and Google Messages** when installed on the isolated QA target. Inspect the recipient compose field without sending messages; real delivery is outside Luma's acceptance claim. Record app versions and route limitations (including recipient-app truncation/normalization) honestly. If either app cannot be exercised, its case remains BLOCKED until available; a fake receiver validates payload contracts only. Test no-compatible-destination handling separately. Image cards, attached photos, bulk/date-range sharing, PDF/Markdown/file export, links to hosted entries, receiving/importing shared content and auto-sharing remain out of scope.

## Two-phase delivery and acceptance

**Phase 1 — Implementation:** production/configuration/resource/documentation and source review only. No Gradle/sync/resolution/build/lint/schema generation, test authoring/execution or emulator/device use. Exit only with recorded **SOURCE COMPLETE — QA PENDING** handoff.

**Phase 2 — QA:** author fixtures/tests, run build/lint/automated/native/manual scenarios, correct defects and rerun affected coverage using the shared isolation/evidence policy. Every case starts NOT RUN. F15-R01–R05 must map to plan steps and actual QA evidence before feature acceptance. Documentation/source checks alone establish no runtime result.
