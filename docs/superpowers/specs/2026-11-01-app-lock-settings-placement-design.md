# App lock settings placement

## Intent

Move the App lock entry from Privacy to the top of the Settings hub, where users can find and open the existing app-lock settings flow.

## Goals and boundaries

- Show an App lock row first in Settings, below the hub description, only when app-lock settings are available.
- Open the existing app-lock settings route from that row.
- Remove the App lock card from Privacy.
- Keep Privacy's data-reset authorization and lock-state safeguards unchanged.
- Keep app-lock credentials, policy behavior, and recovery flow unchanged.

## Approaches

- **Direct Settings row (chosen interpretation):** Add a row at the top of the Settings list that opens the current app-lock route. This matches the Settings hub's navigation pattern and the request to move the entry.
- **Inline controls on the Settings hub:** Put the app-lock controls directly on the hub. This duplicates the existing dedicated, authorization-aware settings flow and changes more than placement requires.

The direct row is inferred from the request; implementation waits for approval of this short design.

## Constraints and implementation calls

- Reuse the current App lock title and description strings, with a Settings-specific test tag.
- Remove only the Privacy entry-point parameters and UI; retain `appLockEnabled` where Privacy uses it to guard data reset.
- The user later instructed implementation without tests; no test files are edited and no tests are run. The existing navigation test reference remains unchanged.
- The linked app-lock QA document is context only; this placement change does not execute the broader QA campaign.
