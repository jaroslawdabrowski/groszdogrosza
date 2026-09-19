# End-to-end tests

`main-flow.spec.ts` drives the real app through the main money flow (treasurer creates two
collections, students pay in, both settle, the settlement surplus math is checked against
concrete numbers, both the treasurer's and a parent's login see the right numbers) with a
real browser against a real running backend - not mocked. `collection-membership.spec.ts`
covers the other two collection-creation/editing paths: a student excluded from a collection
from the start (the "who's in this collection" checklist), and a student removed from an
already-ACTIVE collection mid-way through, refunding whatever they'd paid back to their
piggy bank - see CLAUDE.md's `collection` section for why both exist (a class trip isn't
"everyone owes money" the way a teacher's gift collection is). `pages.ts` is the Page Object
Model both specs are written against (`TreasurerPanel`, `CollectionDetailsPage`,
`PiggyBankPage`, ..., plus a small `Api` wrapper for the one thing there's no UI for yet -
see below) - keeps each spec readable as a story about money and students rather than a wall
of `page.locator(...)` calls.

**Every spec file in this suite runs one at a time** (`workers: 1` in
`playwright.config.ts`) - they all seed the same bootstrap-gap treasurer under the same
email (see `seed.ts`) against one real backend/DynamoDB table, so two spec files as separate
Playwright workers race on that shared state exactly the way two runs of the same spec used
to (see the cleanup note below) - confirmed by actually hitting it once two spec files
existed, not assumed.

## Running it

1. Start the backend in dev mode from the repo root (takes ~60s, Dev Services boots a local
   Keycloak + DynamoDB via Localstack): `./mvnw quarkus:dev`
2. In a second terminal, from `src/main/webui/`: `npm run e2e`

The suite seeds one thing directly into DynamoDB before it starts (`e2e/seed.ts`) - the very
first treasurer record. That's not a shortcut, it's the actual documented bootstrap gap (see
CLAUDE.md, "Bootstrap gap, not yet solved"): `POST /api/students/{id}/parents` is
treasurer-only, so nothing in the app itself can create the first treasurer. `seed.ts` finds
the already-running Dev Services Localstack container by image name (Quarkus picks a random
host port each run, so there's no fixed endpoint to hardcode) and writes directly to its
DynamoDB, the same way every other local-dev recipe in CLAUDE.md seeds a treasurer.

Everything after that - adding a student, adding a parent, creating and settling a
collection, both logins - goes through the real UI or the real API, not direct database
writes.

The suite cleans up after itself in `test.afterEach` - both the seeded treasurer's own
`Jasio Skarbnik` and the UI-created `Kasia <timestamp>` student are deleted via the real
`DELETE /api/students/{id}` once the test finishes, pass or fail. This isn't just tidiness:
`Parent.email` isn't unique and `AuthorizationSupport` resolves "who am I" via a full-table
scan's `.findFirst()`, so a leftover `Parent` row from a previous run sharing the same
dev-user email can non-deterministically win that scan and make the *next* run assert
against stale data instead of what it just created - confirmed the hard way, a second
consecutive run without this hook failed on a stale-student mismatch. Never point
`GG_E2E_BASE_URL` at a real deployment - `seed.ts` writes straight to whatever DynamoDB table
the app under test is using, dev-only Localstack credentials and all.

## Why a payment is recorded via API, not a UI click

There's no "record a manual contribution" button in the UI yet (see CLAUDE.md's own TODO
list) - and a real parent's payment normally arrives via the automatic bank-statement
pipeline (`BankStatementProcessingService`), not something a parent clicks in this app at
all. That pipeline's own logic (fuzzy/exact matching, the piggy-bank-first-then-sweep
allocation) is covered by `PaymentMatchingPolicyTest`/`ContributionAllocationPolicyTest` and
was manually verified against a mocked mailbox - see CLAUDE.md, "Verified end-to-end
locally". This suite calls `POST /api/collections/{id}/contributions` (`Api.recordContribution`)
directly to stand in for "a payment landed", the same way the treasurer would record a cash
payment by hand once that UI exists - `SettlementPolicy`'s surplus-crediting math (what a
collection actually settles the leftover to the piggy bank for) is the same either way, so
the two settlement scenarios in `main-flow.spec.ts` (an overpayment settled with money left
over; two students settled at a lower-than-expected actual cost) exercise real business
logic even though the money arrived via this stand-in rather than a real bank transfer.

One real bug surfaced while writing those scenarios and is fixed alongside this suite: a
`ContributionRequirement` whose `requiredAmount` is already 0 (fully covered by an existing
piggy bank balance at the moment the collection was created) used to still be created with
status `PENDING` ("Do zapłaty") - misleadingly implying money was still owed when nothing
was. `CollectionService.createCollection` now sets the initial status to `PAID` when the
computed required amount is zero. See the second collection in `main-flow.spec.ts` for the
regression test.
