# T12 session views update

Implement the reopened T12: exclude unused apps, provide a chronological list of
app visits with opening time/end/duration, and cumulative per-app usage for the
selected civil day. Both views share date navigation and saved view selection.

Read UsageEvents alongside the existing native daily buckets. Reconstruct
foreground visits from activity resume/pause events, union concurrent activities
within each package, close at screen-off/lock/shutdown, and discard unmatched
starts across startup. An unclosed visit ends at the import boundary and is
explicitly ongoing, never labeled as an observed closing time. Orphan closes
never invent an opening. Android event retention is short and completeness is
not promised: native session history is always labeled partial.

Store sessions and query windows separately using Room v2 with an additive v1→v2
migration. Keep existing daily LifeEvents, metadata, links and summary behavior.
Equal-time ordering keeps same-class reopens distinct from activity handoffs.
The public API has no instance identifier; class resume counts and exact-event
deduplication are conservative observations, not a guarantee of complete history.
Session identity is package + original start, independent of growing end. Import
daily buckets, sessions, query-window metadata and checkpoint atomically under
the existing permission/clock/opt-out guards. Preserve saved sessions if Android
returns shorter retained history; update matching identities on retry. Re-query
seven days of events to retain starts for ongoing visits. Quarantine boundaries
exclude old sessions as well as old daily intervals.

Daily session reports clip visits to local midnight boundaries, use exact
millisecond durations, sort visits chronologically and totals descending, and
exclude zero durations. Repeated imports cannot double count. Report session
history as unavailable, partial, or complete; old aggregate-only days show their
existing native totals with an explicit legacy explanation and no invented
timeline. Empty partial event windows do not override positive legacy totals,
since requested query bounds do not establish retained history. Partial days with
visits report recorded-session totals with a visible notice.
Empty session windows show no reported visits rather than a measured zero.

No added dependencies, permissions, SDK changes, telemetry or uploads. Use the
current checkout. Produce a commit message; do not commit. Validate with JVM
tests, build/lint and migration/Room/Compose instrumentation on the existing
emulator using a separate validation application ID.
