# Luma redesign reference brief

Prepared 22 October 2026 from the six local PNGs and six local HTML files. This is the common specification for the [task index](../UI_REDESIGN_TASKS.md) and every linked task spec/plan. App code was inspected to separate visual work from feature changes. T30, T31, T32-A and T32-B are implemented with validation pending; T32 coordination is in progress, T32-C/D remain pending, and device acceptance is pending.

## Intent and scope

Regenerate the redesign backlog from the supplied Today, Timeline and Review designs, then provide a specification and implementation plan for every required and optional task. Required work changes presentation while preserving the current product. New features or existing-feature behavior changes are separate optional tasks, deferred by default. The planning pass did not begin app implementation; subsequent T30 and T31 work is recorded in the task index and its evidence.

A single all-app task would hide navigation/query changes; copying each mockup literally would introduce unsupported behavior. The selected approach shares a visual foundation and divides work by screen family, with explicit optional feature tasks. Existing T30/T31/T32/T33 identities are retained; T32 now delegates to T32-A/B/C/D instead of remaining an undifferentiated catch-all.

## Source resolution and precedence

1. Existing product/data/privacy contracts govern what can be claimed or mutated; optional task specs explicitly identify any proposed changes.
2. For visual composition, use the matching PNG; use corresponding valid HTML to extract roles, typography and spacing. Shared role decisions below resolve differences between the six valid screen HTML documents.
3. The corrected `timeline-light.html` is a distinct, valid Timeline reference. Use it with `timeline-light.png` for the grouped date/count ribbon, type chips, rail/node geometry, editorial card metadata, photo placement and capture styling. The hidden capture sheet is also a reference; its open/close script is illustrative. The View options script only renames the control “Compact” and does not implement a compact layout. Preserve the app’s working disclosure. Keep source assets unchanged by documentation edits.
4. There are no More, Settings, Habits list, editor, detail or launcher-widget screen mockups in this folder. Their required treatment extrapolates the shared system. Quick Capture is the exception among modal flows: corrected timeline-light.html contains a hidden reference sheet, whose styling informs T32-D while habit shortcuts remain optional O08.
5. Source text, people, Persian task names, dates, step counts, chart shapes, hydration volumes, progress and quoted prose are illustrative. Fixtures may reproduce representative states in a dedicated test install; production renders actual saved data.

## Visual observations

| Family | Light reference | Dark reference | Shared composition |
| --- | --- | --- | --- |
| Today | Warm paper, light nested habit/task surfaces, sage/ember progress and rounded controls | Obsidian canvas, elevated group cards, muted nested rows and mint/ember progress | Date and tracking context; capture chips; habit count/target with +1; task groups; recent saved records and summary |
| Timeline | Grouped date/count ribbon, thin left rail, pastel type nodes, soft rounded editorial cards, photo crop and hidden capture sheet | Dark rail/nodes, charcoal cards, warm serif journal title and ember capture action | Date/search/view controls; mixed feed; saved source and time labels; contextual capture above navigation |
| Review | Soft period selector, rounded summary/signal/archive/catalog modules and comparison chips | Elevated dark modules, mint/ember accents, high-contrast value hierarchy | Partial-week context, section shortcuts, entry/habit/mood summary, saved signals, comparison and people/place provenance |

PNG dimensions are Today 380/381 × 1600, Timeline 466/430 × 1600 and Review 272/342 × 1600 (light/dark). These are tall reference captures, not dp or fixed target viewport specifications. Their small text/controls and clipping are not accessibility requirements.

## Shared design decisions

HTML declares Newsreader for editorial headings and Plus Jakarta Sans for Latin body/controls. Implement with bundled resources and a Persian-capable offline fallback; do not reproduce Google Fonts/CDN dependencies. Use existing drawable icons rather than a web icon font. Serif: page/section/journal headings. Sans: data, dates, controls, body and Persian text. Baseline type roles: page 26/34 sp, section 22/28 sp, card title 20/26 sp, body 14/22 sp (long reading 16/26 sp), metadata 12/18 sp, control label 14/20 sp. Allow font scaling/wrapping; never make a 48 dp action shorter for visual fidelity.

Retain existing reading width 600 dp and 20 dp page gutter; group gap 24 dp, section padding/gap 16 dp, content gap 8 dp and related gap 4 dp. Main section radius 24 dp, inner card 16 dp, small choice 12 dp, chips/actions fully rounded. Habit track 8 dp, separate +1 target at least 48 dp (56 dp where space permits). At narrow/large-text sizes stack actions. Preserve system bars and measured FAB bottom clearance. Existing confirmed progress animation remains 180 ms and respects system animation scale.

### Color role baseline

The corrected Timeline HTML confirms light primary `#993612` and primaryContainer `#ba4d28` with onPrimaryContainer `#fff1ed`. Use its primaryContainer, tertiaryContainer, outlineVariant and inverse role pairs app-wide; use primaryFixed/onPrimaryFixed for pale terracotta chips instead of treating the pale fill as primaryContainer. Keep one shared palette rather than per-screen substitutes. The secondary-container foreground below is deliberately darker than the HTML’s `#566754` for legibility. Dark HTML includes a malformed `on-surface-variant: dcc0b9`; normalize it to `#dcc0b9`. Values below are implementation targets, not a claim that contrast has already been measured. If an intended pair fails, adjust the foreground/border and document the measured final pair in T33 while retaining the palette direction.

| Role | Light | Dark |
| --- | --- | --- |
| background / surface | `#fff8f4` | `#131413` |
| surfaceContainerLowest | `#ffffff` | `#0d0e0d` |
| surfaceContainerLow | `#faf2ed` | `#1b1c1b` |
| surfaceContainer | `#f4ece7` | `#262726` |
| surfaceContainerHigh | `#eee7e2` | `#2d2e2d` |
| surfaceContainerHighest | `#e8e1dc` | `#383938` |
| onSurface | `#1e1b18` | `#ede0d8` |
| onSurfaceVariant | `#57423b` | `#dcc0b9` |
| primary / onPrimary | `#993612` / `#ffffff` | `#ffb5a0` / `#5f1600` |
| primaryContainer / onPrimaryContainer | `#ba4d28` / `#fff1ed` | `#ff8c69` / `#5f1600` |
| primaryFixed / onPrimaryFixed | `#ffdbd0` / `#3a0b00` | `#ffdbd1` / `#3a0a00` |
| secondary / onSecondary | `#526350` / `#ffffff` | `#aeceb8` / `#193627` |
| secondaryContainer / onSecondaryContainer | `#d2e5cd` / `#304d3c` | `#304d3c` / `#c9ead4` |
| tertiary / onTertiary | `#705125` / `#ffffff` | `#ffb59f` / `#5e1700` |
| tertiaryContainer / onTertiaryContainer | `#8b693b` / `#fff1e5` | `#782102` / `#ffdbd0` |
| outline / outlineVariant | `#8b726a` / `#dec0b7` | `#a48b84` / `#56423d` |
| error / onError | `#ba1a1a` / `#ffffff` | `#ffb4ab` / `#690005` |
| errorContainer / onErrorContainer | `#ffdad6` / `#410002` | `#93000a` / `#ffdad6` |
| inverseSurface / inverseOnSurface | `#33302d` / `#f7efea` | `#ede0d8` / `#30312f` |
| decorative sage accent | `#526350` | `#94b49f` |
| decorative ember accent | `#ba4d28` | `#ff8c69` |

Use role-paired text rather than a decorative accent as arbitrary foreground. Disabled states need not mimic active contrast but must remain understandable with a visible reason where applicable. No task may use color alone for status, deltas, progress or selected state. Preserve platform-default scrim/remaining Material roles unless they conflict with the paired role measurements; no dynamic-color override of the selected reference palette.

## Reference-to-product differences

| Reference element | Current product | Required redesign decision | Optional owner |
| --- | --- | --- | --- |
| Four bottom destinations | Five, including Habits | Keep all five and restyle | O01 |
| Timeline event-type chips | Search and saved app-usage switch | Preserve these controls; omit inactive decorative filter chips | O02 |
| Today Open/Completed and No date | Three-item due/overdue preview; full lists elsewhere | Keep bounded query, counts, View all and recovery | O03 |
| Journal feed photo | EventCard has photo-count metadata; gallery is in detail/editor | Keep metadata and editorial text | O04 |
| Review sparklines | Daily usage evidence, aggregate activity and per-habit week slots | Keep supported metrics/slots and sources; no fake chart | O05 |
| “100% streak”, active days and consistency coaching | Repetition/scheduled-day summaries; no defined new streak contract | Keep current summaries; omit new claims | O06 |
| Reflect chip and cadence quotation | Mood/Journal/Memory capture | Keep three choices and omit synthetic insight footer | O07 |
| Water/Stretch shortcuts in capture sheet | No habit entry in current Quick Capture sheet | Retain current choices; no hard-coded sample habits | O08 |
| View options → Compact script | Real View options disclosure exists | Preserve actual disclosure; no label-only toggle or implied compact-mode feature | T32-A |
| Calendar dropdown glyph | Existing day navigation | Preserve existing date actions; omit unmapped picker affordance | T32-A |
| People/Places search and switching | Already implemented with separate saved queries | Restyle and preserve; no optional feature needed | T32-C |
| Profile avatar / unnamed tune action | No account/profile product | Omit unless mapped to an existing clearly named action | Excluded |
| “Encrypted on-device” / “Encrypted automatically” / lock badges | Local storage; ZIP backup unencrypted | Use factual local-storage copy; no encryption claim | Excluded |
| +Link/+Tag in Review | Linking is available through editors | Preserve editor/source routes; omit a data-free direct-link action | Excluded |
| “Pinned spotlight”, “Formal Volumes” | Latest saved memory and existing journal/memory counts | Keep actual existing names/semantics | T32-B |
| Markdown export in old backlog | Not implemented; not present in supplied designs | Do not add active export format | Excluded |

Unselected optional tasks are documented scope differences, not release failures. Optional tasks may be selected individually; their plans must be reviewed before implementation. Excluded ideas require a separately scoped future request and are not hidden requirements of this redesign.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Planning and verification status

All specs/plans created in the original documentation pass are proposed artifacts. The previous backlog’s blanket “Approved” label does not approve the regenerated scope or optional additions. T30, T31, T32-A and T32-B are implemented (validation pending); T32 coordination is in progress, T32-C/D remain pending, T33 acceptance is in progress, and optional status is deferred. The current route inventory and evidence gaps are recorded in [T32 route coverage](route-coverage.md). T31 and T32-A assemble/lint results and outstanding runtime checks are recorded in their [implementation evidence](evidence/2026-10-22/t31-review.md) and [T32-A implementation evidence](evidence/2026-10-22/t32-a-review.md); T32-B source review and blocked build attempt are recorded in [T32-B implementation evidence](evidence/2026-10-22/t32-b-review.md). Existing behavior baseline includes the implemented portions of `docs/superpowers/specs/2026-10-21-fresh-ui-refresh-design.md`; retain its behavior safeguards unless an optional task expressly changes them.

## Source inventory

| File | SHA-256 |
| --- | --- |
| [review-dark.html](review-dark.html) | 5e254189a7cf162f7f5e353ba5b827fbc9d5fd623b481282607ecfc0dcfe51e6 |
| [review-dark.png](review-dark.png) | 2a747910e9642cf44b2d8c8dff99641e0815dec6d9112785bb9dbcb7f216942a |
| [review-light.html](review-light.html) | 2631a457c0a969bbcee23350a75b9d29df20b71cf4e01f902a0ec91a81147c57 |
| [review-light.png](review-light.png) | e04d6d907c0d5ccf10039b2f2fa7f0b609cdddf453184961645ae15e7c3e03a6 |
| [timeline-dark.html](timeline-dark.html) | 5f690f960c21e5f0c698b8e441755a69a90a86a684965ff401ed87dbe0d70316 |
| [timeline-dark.png](timeline-dark.png) | b7f70ea0ae85e19aa6ddd0171a7f116dc9f258dd9e788d06d8f6da9101ac12e0 |
| [timeline-light.html](timeline-light.html) | 0b0de9c044a9ee9af36d6c7cd33aeb03918078077a2155964fae590667fb4e69 |
| [timeline-light.png](timeline-light.png) | 0abe3f9556758719fda3725dc1ca1717fe36f753a40d3953dac8837e3d4b2ae8 |
| [today-dark.html](today-dark.html) | 665b2c69623dd003d75e3164d90dbd146f9104156b528188a98816434adb9dab |
| [today-dark.png](today-dark.png) | c5fa90790c47e7382ac0d82e98205ee2c355969e95bebad8a99e38c99df1015b |
| [today-light.html](today-light.html) | 43717ba96537042f3bfaaacf3b5fb67b2cdc52613f7a0299bdb28ec5fbebc1f5 |
| [today-light.png](today-light.png) | 550641fab24fe1fcc88ff3305a6c33c811857f98b9fb5ff8e7e5e0baa7213067 |
