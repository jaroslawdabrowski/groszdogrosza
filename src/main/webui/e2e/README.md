# End-to-end tests

`main-flow.spec.ts` drives the real app through the main money flow (treasurer creates a
collection → a payment is recorded → it settles → both the treasurer's and a parent's login
see the right numbers) with a real browser against a real running backend - not mocked.

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

Re-running the suite doesn't clean up after itself (matching every other local-dev recipe in
this project) - it leaves an extra treasurer-role `Parent` row and a `Jasio Skarbnik`/`Kasia
<timestamp>` student behind each time. Harmless for local Dev Services DynamoDB, which nobody
depends on staying clean; never point `GG_E2E_BASE_URL` at a real deployment.

## Why a payment is recorded via API, not a UI click

There's no "record a manual contribution" button in the UI yet (see CLAUDE.md's own TODO
list) - and a real parent's payment normally arrives via the automatic bank-statement
pipeline (`BankStatementProcessingService`), not something a parent clicks in this app at
all. That pipeline's own logic (fuzzy/exact matching, the piggy-bank-first-then-sweep
allocation) is covered by `PaymentMatchingPolicyTest`/`ContributionAllocationPolicyTest` and
was manually verified against a mocked mailbox - see CLAUDE.md, "Verified end-to-end
locally". This suite calls `POST /api/collections/{id}/contributions` directly to stand in
for "a payment landed", the same way the treasurer would record a cash payment by hand once
that UI exists.
