# UX-06, UX-15: Honest integration and metric states

Status: implementation is in the working tree; source-review findings have been addressed; Gradle compilation was blocked before project configuration; automated and manual acceptance remain pending.
Source: [review](../../UI_UX_REVIEW_2026-10-18.md); [shared brief](../../UI_UX_IMPLEMENTATION_READY_2026-10-18.md). Depends on UX-16.

## Intent and approaches

Explain why data is absent without confusing permission, opt-in, saved evidence and zero. Recommended: independent read-only access probes plus a shared presentation mapper over existing saved data. Inferring access from status is incorrect while imports are off: UsageSyncer.refreshAccess and HealthSyncer.refreshAccess currently short-circuit then. A permission-state database migration or import-policy redesign is unnecessary.

## Access/import contracts

Add suspend fun access(): UsageAccess to UsageIntegration and suspend fun access(): HealthAccess to HealthIntegration. Syncers delegate to source.access(), with no permission launch, record query, scheduler mutation, import or preference write. Probe failures are errors, not denied access. No default implementation guessing from status. Update all fake integrations.

Create app ui/integrations/IntegrationAvailability.kt:
- enum IntegrationAccessState { CHECKING, ALLOWED, REQUIRED, LOCKED, UPDATE_REQUIRED, UNAVAILABLE, ERROR }.
- data class IntegrationAvailability(val enabled: Boolean?, val access: IntegrationAccessState, val lastSync: Instant?, val importFailed: Boolean, val configurationFailed: Boolean = false). enabled is null until the saved configuration has loaded, and on a configuration-observation failure; never infer Off from unknown state.
- data class IntegrationAvailabilityState(val usage: IntegrationAvailability, val health: IntegrationAvailability).
Create IntegrationAvailabilityViewModel(usage: UsageIntegration?, health: HealthIntegration?) observing the existing enabled/status/lastSync flows; expose StateFlow<IntegrationAvailabilityState> and refreshAccess(). Probe on lifecycle resume and explicit Refresh, serialize/coalesce overlapping probes, prevent stale probes replacing newer state, and restart a failed configuration observer on explicit Refresh. Null integration maps UNAVAILABLE. Retain the existing foreground refresh/scheduling policy: this new access probe does not replace it.

Share this ViewModel instance from LifeOsApp with settings and summary/review routes. Saved summary streams remain keyed by date/zone and never wait on optional access. No provider record reads occur from displaying a summary or opening settings; pre-existing enabled Usage background jobs remain possible independently.

## UX-15: Two states and one useful primary action

Always show Android access and import preference separately, even with switch off. Use “App usage” consistently, explaining sum of foreground app time, not screen-on duration.

| Access | Imports | Primary action | Secondary |
| --- | --- | --- | --- |
| Checking/error | Either | Refresh status | Existing saved history remains readable |
| Required | Either | Grant access | Import switch remains independent; permission explanation first |
| Allowed | Off | Manage access | Switch can enable imports; “Saved history is kept” |
| Allowed | On | Import now (Retry import after failure) | Manage access; Refresh status |
| Usage locked | Either | No permission-grant CTA; “Unlock device, then refresh” | Refresh status |
| Health update needed | Either | Update Health Connect | Refresh status |
| Unavailable | Either | Explain provider unavailable | Refresh status; no disabled fake Grant button |

Usage switch: “Automatic app usage imports” with On/Off. Preserve current scheduling and disable-during-in-flight safety.
Health switch: “Step imports” with On/Off; helper “Import steps while Luma is open. Tap Import now to refresh.” Do not call this background/automatic import: current HealthForegroundRefresh refreshes access only. Permission grant and return only refresh access; they do not enable switch/import. Health grant flow may run while imports off after explicit Grant access; remove the current enabled-only guard around that explicit permission request, keeping busy/loading guards.

Show last successful import when lastSync exists. Show the saved period only when its real bounds are known; this iteration uses each existing summary/report's selected data period where available, not a fabricated complete-history range in settings. Settings says “Saved history remains on this device” and does not derive a saved range from lastSync/checkpoint. No new min/max database query is required for optional unknown bounds.

## UX-06: Metric presentation precedence

Use MetricAbsence { LOADING, READ_ERROR, CONFIGURATION_ERROR, CHECKING_ACCESS, NO_CHECK_INS, NO_RATINGS, IMPORTS_OFF, ACCESS_NEEDED, DEVICE_LOCKED, PROVIDER_UPDATE, PROVIDER_UNAVAILABLE, ACCESS_CHECK_FAILED, IMPORT_FAILED, NO_SAVED_DATA }.
Inputs distinguish saved-data loading/read failure, hasSavedData, and optional integration availability.

1. Saved read loading/error: Loading/“Could not load saved data” with saved-data Retry; never imply zero.
2. Saved value present (including 0): display value and coverage. Off/denied/import failure may add a secondary status but never replace historical data.
3. No mood observations: “No check-ins today/this week” and Check in using existing Mood capture; no integration CTA. If mood exists but optional energy/stress has no samples, say “No energy ratings for this period” / “No stress ratings for this period”; do not claim the check-ins are absent.
4. No imported value: unknown configuration → Loading or “Could not load import settings” with Refresh status; imports off → Imports off; enabled + checking → Checking access; required/locked/update/unavailable/probe failure → corresponding reason; allowed + import error → Could not import; otherwise → No saved data for this day/week.
5. With off + access-required show Imports off as main cause and “Android access also needed to import” as secondary; never claim one proves the other.

Only add actions that work: Check in → current-time Mood editor; Integration settings → existing integrations route; Retry saved read → current summary/review retry; Retry import/Import now belongs in settings with current eligibility. Summary Refresh means saved-data requery/access refresh, never silent native import.
Manual activity evidence is shown even when Health imports are off/denied. Missing manual activity must not be described as a Health permission failure. Health-specific absence copy only for the Health Connect step metric; generic activity absence says No saved measurements, with optional integration link.

No cache schema/version change: live availability decorates existing deterministic saved-only metrics. Current/previous periods keep independent absence and values; missing previous values yield no fabricated delta.

Audit every review_unavailable/summary_unavailable rendering call, including ReviewValues.kt, ReviewMood in WeeklyReviewRoute.kt and the prior-period cells in ReviewSignals.kt. Keep number/duration helpers for present values; pass an explicit metric-specific absent label for null values. ReviewOverviewMetric must receive an explicit unavailable/absence state rather than comparing localized display strings to “Unavailable”. Do not leave a bare generic “Unavailable” in these summary/review metric surfaces.

## Acceptance

Fixtures cover enabled/disabled × allowed/required, locked/update/unavailable/probe failure, loading/read failure, import failure, null vs zero, historical values while off/denied, manual activity and retained history. Opening settings performs zero source.query/import calls and no permission launch. Return from provider settings updates access without switch changes. Disable while import is active preserves current cancellation/commit guards and history.
