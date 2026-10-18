# UX-14: Layered, accurate privacy explanations

Status: implementation-ready proposal; product changes pending.
Source: [review](../../UI_UX_REVIEW_2026-10-18.md); [brief](../../UI_UX_IMPLEMENTATION_READY_2026-10-18.md).
Consume UX-13 fixed header. UX-18 adds the backup shortcut by the existing reset action after this unit.

## Intent and approaches

Make privacy facts easy to find without dropping qualifications. Recommended: short persistent summary and four independent expandable sections. Shortening all copy would omit retention/revocation details; another policy route adds navigation cost and risks divergent Health rationale content.

## Layout and interaction

Summary always visible: “On this device”, “No account”, “Optional connections” with a short explanation that records stay in private app storage, no account/ads/telemetry/network requests, core capture/browsing needs no optional connection.

Four labeled ≥48 dp disclosures: Records & photos, App usage, Health Connect, Backups. Each independently expands/collapses; save expansion by section key across recreation. Use named headings and expanded/collapsed semantics. A brief visible summary under each heading includes its revocation/boundary fact, so the critical fact is discoverable without opening every paragraph.

Near the destructive action keep two headings expanded: “What deletion removes” and “What stays outside Luma”. Existing reset action, red treatment, explicit warning, confirmation, busy state, error recovery and Android accepted-request wording remain. Do not claim observed erasure merely because Android accepted the request.

## Copy retention matrix

These are required facts, not optional editorial suggestions. Reuse existing accurate sentences while splitting into resources; all facts must remain accessible in en/fa/fr.

| Section | Persistent short boundary | Full details must retain |
| --- | --- | --- |
| Records & photos | Private copies; original photos stay outside Luma | Life records/catalog names/photos local; system photo picker; private copied selected images; no broad media access; core browsing/capture works without optional permission |
| App usage | Turning imports off keeps saved history; revoke access in Android | Optional app names + foreground time, not screen-on time; enabled jobs may run in background; disabling stops new requests/cancels scheduled refreshes; in-flight query may finish but its results discarded while off; switch does not revoke Usage access; Manage access in integration's Android settings |
| Health Connect | Turning imports off keeps saved step history; revoke in Health Connect | Reads daily step totals only, never writes health data, no sleep/workouts; Import now refreshes last seven calendar dates while app open; overlapping-source aggregation; reconciles corrected/removed data in recent seven dates; older totals remain snapshots; absent data not zero; off stops reads; revocation separate |
| Backups | Exported files remain after deleting local data | No automatic cloud backup/device transfer; manual archive includes private records/photos, unencrypted; chosen provider controls destination; local deletion does not remove exported files/original photos/provider records; user manages those copies separately |

“What deletion removes”: saved life records, catalogs, imported history, private photos, drafts, caches and preferences; Android reset closes Luma and resets runtime permissions/file grants.
“What stays outside Luma”: exported backups, original photos, Android usage history, Health Connect records. Do not promise all special access grants are revoked by clearing data; explicitly point to provider settings.

## Reuse and boundaries

Keep PrivacyPolicyContent usable without database, reset service, integration instances or navigation. Native HealthRationaleActivity can render the same four sections in its own scroll/Scaffold with Close, without inheriting outer NavHost insets. Use shared resource sections for health_explanation/health_privacy_retention and permission explanation; avoid contradictory timing copy.

Privacy screen may link to Integrations through callback for concrete access management, while existing Open Luma's Android settings remains recovery for reset failures. Links are explicit user actions; opening disclosures never launches permission/settings screens. Missing settings intent displays existing useful error and preserves content.

No network/privacy model, permission, reset implementation, exported component or backup-policy change.

## Acceptance

All retained facts mapped to visible/resource sections; revoke access and external-copy boundaries found without reading long paragraph. Expand/collapse survives recreation. Reset remains protected and unchanged; settings-intent failure is recoverable. Native rationale launches standalone without opening database or requesting access. Verify TalkBack heading/expanded state and long translated text.

