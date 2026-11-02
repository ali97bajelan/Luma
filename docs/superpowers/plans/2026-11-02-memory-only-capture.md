# Memory-Only Capture Implementation Plan

> **Execution:** Inline in the current project checkout, at the user's direction.

**Goal:** Make Memory the only free-form capture type while preserving existing writing features and saved content.

**Architecture:** Extend Memory to store long-form body text and event tags. Migrate persisted journal events into Memory events in Room and normalize journal rows from older backups during restore. Remove Journal from visible capture, navigation, widget, and app-lock choices while keeping legacy storage decoding needed for existing databases and backups.

**Global constraints:** Preserve existing user edits. Keep event IDs and linked people, places, photos, tags, timestamps, and memory marker importance/context. Accept Memory bodies up to 50,000 characters. No tests will be added or run in this change.

**Review focus:** Legacy databases and backups with journal bodies above 10,000 characters must retain the full body. Journal entries already marked as memories must retain importance and marker context. Existing journal lock preferences must continue protecting converted memories.

### Task 1: Domain, persistence, and legacy conversion

**Files:** `core/domain/.../Memory.kt`, `MemoryRepository.kt`, event mapping and repositories, `core/data/.../LumaDatabase.kt`, backup restore.

- [x] Raise Memory body limit to 50,000 characters and carry event tag names through direct and promoted memory writes.
- [x] Add the next Room migration to convert each journal event to a memory, copy its body to the event body, preserve any existing memory marker fields and event links, remove its legacy journal row, and stale derived summaries.
- [x] Normalize journal rows restored from existing backup formats using the same conversion rules.
- [x] Map old journal-only lock preferences to the Memory lock section without weakening protection.

### Task 2: Unified Memory editor and library

**Files:** `app/src/main/java/com/luma/lifeos/ui/memory/*`, Memory editor state/repository integration, Memory library and Review surfaces.

- [x] Add reusable tag selection to direct Memory capture and Memory promotion/editing; save tags through the existing event tag catalog.
- [x] Load and save long Memory body text without truncating existing migrated entries.
- [x] Display converted entries as Memory throughout the library, timeline, people/place history, and Review.

### Task 3: Remove Journal from user-facing capture

**Files:** `LifeOsApp.kt`, Today and Quick Capture surfaces, `QuickCaptureWidgetProvider.kt`, restricted capture choices, app-lock settings, localized strings and widget layouts.

- [x] Remove Journal capture buttons and widget actions; redirect old destinations and route all new free-form capture to Memory.
- [x] Hide Journal as a lockable section while retaining legacy policy decoding.
- [x] Remove Journal-only user-facing labels and update product documentation to describe Memory as the single writing feature.

### Task 4: Source review

- [x] Review remaining production references and confirm no visible Journal entry point remains; keep legacy models, storage, and redirect code for older data and links.
- [x] Inspect the final diff and run whitespace/source-reference checks. Automated tests and builds are omitted per the developer instruction not to run them unless requested.
