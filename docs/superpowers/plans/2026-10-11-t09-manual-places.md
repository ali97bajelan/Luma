# T09 Manual Places

Goal: reusable local place list/create/edit/detail and one optional place during supported mood/journal/memory capture/edit, without location permissions or tracking.

Use existing v1 place/event_place tables and current catalog/editor/picker patterns. PlaceInput accepts a required name (200 characters maximum) and optional note (10,000 maximum). Existing coordinates are preserved on edit and shown in detail; no coordinate acquisition or map dependency. UUID identities allow equal names. Place deletion removes its links, never event content.

Capture contracts append nullable PlaceSelection: absent preserves an existing link; PlaceSelection(null) explicitly clears; PlaceSelection(UUID) replaces it. Captures and link changes use the same transaction, including people/tags/cache rollback on missing references. LifeEventRecord carries the optional Place. Editors save one nullable UUID and submit an explicit selection; missing selections remain removable.

Places is reached through Settings and each capture picker. Creating a place retains the parent draft and makes the catalog row available for explicit selection on return. Detail observes the catalog row live, with missing/loading/error/retry states. No history feature is required in T09. Feeds, journal detail, memory review and person history display the linked place.

- [x] Domain and persistence tests first; implement Place/Input/Selection, repository, mapping and transactional link writes.
- [x] Editor and capture selection tests first; add saved place editor, list/detail/picker and navigation.
- [x] Compile device flows, run JVM/APK/lint checks, request independent review, document outcomes and provide commit message.

Review focus: default preservation vs explicit clearing, missing-link transaction rollback, capture retry identity, source promotion/unmark preservation, stale deleted selections and lost committed save/delete results.

Work in the authorized shared checkout; preserve existing untracked schemas/plans. No commit or device execution is requested. Device acceptance remains pending user validation.

Verification: 91 JVM tests pass (27 domain, 64 app); debug and both test APKs build; data lint is clean. Independent review found no concrete defects. App lint repeats the known analyzer crash. Five Room cases and two capture device flows compile; execution remains pending user validation. No commit created.
