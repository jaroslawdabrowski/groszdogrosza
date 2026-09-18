# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Grosz do Grosza: a money-tracking tool for a class treasurer (2nd-grade parent, one primary school class) to run collections (e.g. an end-of-year teacher gift), track each parent's payments, and carry overpayments forward as a per-parent "piggy bank" balance applied automatically to future collections. Currently in an early scaffolding phase: the full toolchain (Quarkus/hexagonal backend, DynamoDB persistence, Angular frontend, OIDC auth, Terraform-provisioned serverless AWS infra, i18n) is wired up end-to-end, the domain model and its decision policies are real and tested, but nothing has been deployed or run against live data yet - see "TODO for the next session" at the bottom.

Single Maven module at the repo root (not a `backend/`/`frontend/` split, mirroring the sibling "turboorders" project): Java under `src/main/java`, config under `src/main/resources`, Angular app at Quinoa's default `src/main/webui`. Quinoa builds the Angular app and serves it from the same Quarkus artifact.

Code and comments are in English. The UI is bilingual (Polish/English) via `@ngx-translate`, **default language Polish** - this is a real Polish primary school context, Polish is the primary audience.

## Commands

Backend (run from the repo root):
- `./mvnw quarkus:dev` - dev mode; Quinoa also runs `ng serve` and proxies frontend requests; `quarkus-amazon-dynamodb`'s Dev Services auto-starts a local DynamoDB (via a Localstack testcontainer) - no manual docker-compose needed, and `DynamoDbTableInitializer` creates the table on startup if missing. **Do not add `-Plambda` here** - see "Dev mode vs. the Lambda extension" below.
- `./mvnw test` - full backend test suite. The domain policy tests (`SettlementPolicyTest`, `ParentMatchingPolicyTest`, `ContributionAllocationPolicyTest`, `MBankStatementHtmlParserTest`) are plain JUnit, no `@QuarkusTest`, no Dev Services needed - they're the fast, primary safety net for this app's actual business rules.
- `./mvnw test -Dtest=SettlementPolicyTest` - single test class; `-Dtest=ClassName#methodName` for a single method.
- `./mvnw package -Plambda` - builds the deployable app (`target/function.zip` etc. - see "AWS Lambda packaging" below). `./mvnw package` (no profile) also works and is what `./mvnw package -DskipTests` was verified with during scaffolding - it additionally runs Quinoa's `ng build`, so it needs `npm install` to have been run once in `src/main/webui/` (or let Quinoa do it - `quarkus.quinoa.package-manager-install=false` means it uses the system npm, already verified to work with Node 24).

Frontend (run from `src/main/webui/`, only needed standalone - normally Quinoa drives it):
- `npm install` - first-time setup.
- `npm start` / `ng serve` - dev server on `:4200` (no working backend behind `/api` unless Quarkus is also running).
- `npm test` / `ng test` - Karma/Jasmine unit tests.
- `npm run build` / `ng build` - production build to `dist/webui/browser`. Verified working during scaffolding (Node 24.14.0, npm 11) - one non-fatal budget warning (initial bundle ~546kB vs. a 500kB soft budget), not fixed yet, see TODO.

Java 21; Node must satisfy Angular 20's engine check (`^20.19.0 || ^22.12.0 || >=24.0.0`) - Node 24.14.0 was used during scaffolding and works.

## Architecture

### Hexagonal, package-by-feature

Everything lives under `io.github.jaroslawdabrowski.groszdogrosza`, structured domain → port → application → adapter per bounded context, exactly the pattern established in the sibling "turboorders" and "pvopt" projects:

```
collection/       Collection, ContributionRequirement, Contribution, SettlementPolicy (the
                   settlement decision engine - see below)
parent/            Parent (identity + piggy bank balance)
ledger/             LedgerEntry / LedgerEventType - the auditable per-parent event journal
bankstatement/      BankTransaction, ParentMatchingPolicy, ContributionAllocationPolicy -
                    the automatic mBank-statement-to-parent-to-collection pipeline
platform/security/  OIDC, cross-cutting - identical pattern to turboorders (see there for
                    the OIDC/Cognito/Keycloak reasoning, not repeated here)
platform/persistence/ Attr (AttributeValue conversion helpers) + DynamoDbTableInitializer,
                    shared by every context's DynamoDB adapter
```

Each context: `domain/` (zero framework annotations - this is where the actual business
rules live and where changes should start), `port/in/` (one interface per use case),
`port/out/` (only where an external dependency is actually needed), `application/` (one
`@ApplicationScoped` service implementing every `port.in` interface for that context, wiring
in other contexts' `port.in` interfaces rather than their internals), `adapter/in/web`
(JAX-RS + DTOs), `adapter/out/persistence` (DynamoDB).

**Cross-context calls always go through `port.in`, never through another context's
internals or repository.** E.g. `collection.application.CollectionService` depends on
`parent.port.in.CreditPiggyBankUseCase`/`ListParentsUseCase` and
`ledger.port.in.RecordLedgerEntryUseCase`, not on `ParentRepositoryPort` or
`LedgerRepositoryPort` directly. `bankstatement.application.BankStatementProcessingService`
is the most cross-context-heavy piece (it touches parent, collection and ledger) for exactly
this reason - it's the orchestrator for the one fully-automatic money-moving path in the app.

### `collection`: the settlement decision - the core of this app

`Collection.baseAmountPerParent` is the nominal ask; `ContributionRequirement` is computed
**once, when the collection is created** (`CollectionService.createCollection`), as
`baseAmountPerParent - parent.piggyBankBalance()` at that exact moment, floored at zero -
deliberately not recomputed later if the parent's piggy bank balance changes afterwards
(see `ContributionRequirement`'s javadoc). There's no separate "activate a draft" step
wired up yet: `createCollection` computes requirements and sets status `ACTIVE`
immediately; `CollectionStatus.DRAFT` exists in the domain model for a possible future
two-step flow but nothing currently produces one.

**`SettlementPolicy.settle(contributions, actualCostSpent)`** is a pure static function
(no Spring/Quarkus, no I/O) - same pattern as `ChargeDecisionPolicy` in the sibling "pvopt"
project, and this app's single most important piece of tested logic. The rule, from the
user's own example: 4 parents each paid 50 zł (200 zł total), the gift cost 160 zł, the 40
zł surplus is split equally among the parents **who actually contributed** - 10 zł each - a
parent who paid nothing gets no share, even if their requirement was 0 (fully covered by an
existing piggy bank balance). If cost exceeds contributions, `totalShortfall` is reported
instead of a negative leftover - nothing here decides how to cover a shortfall, that's a
manual, human decision.

**Rounding rule** (see `SettlementPolicy`'s class javadoc for the full detail): all math is
done in integer grosz to avoid `BigDecimal` rounding ambiguity. `surplusGrosz /
contributorCount` is floored; the 0..n-1 grosz that don't divide evenly go **one grosz each
to the earliest payers**, in the order their first contribution to that collection appears
in the input list. Arbitrary but deterministic and auditable - don't change this tie-break
without updating `SettlementPolicyTest.unevenSurplusGivesOddGroszToEarliestPayers` (and
understanding that changing it changes who gets an extra grosz on every future settlement
with a non-dividing surplus).

`CollectionResource` exposes `POST /api/collections/{id}/settle` which runs the policy AND
commits the result (credits piggy banks, writes ledger entries, marks the collection
SETTLED) in one call - there's no separate "preview" endpoint yet even though the user's
spec asked for a settle-preview-then-confirm UI flow; `CollectionDetails`'s "Rozlicz"
button currently commits immediately. **TODO**: add a preview-only variant of
`SettleCollectionUseCase` (or an optional `dryRun` param) before this goes live - settling
should not be a single irreversible click with no preview in a tool tracking real money.

`CollectionService.settleCollection` guards against being called on a collection that isn't
`ACTIVE` (`CollectionNotActiveException`, mapped to `409` by
`CollectionNotActiveExceptionMapper`) - without this, a double-clicked "Rozlicz" button or a
client retry after a network timeout would re-run `SettlementPolicy` against the same
contributions and credit every contributing parent's leftover a second time. `NoSuchElementException`
(the "no such id" signal every application service in this app uses) is mapped globally to
`404` by `platform.web.NoSuchElementExceptionMapper` - before this existed, `POST` endpoints
that didn't explicitly re-wrap it (unlike the `GET` endpoints, which already did) leaked an
opaque `500`.

### `bankstatement`: the fully-automatic path, and why it's safe enough

The user explicitly confirmed wanting **fully automatic** matching and booking, no manual
confirmation step - real money gets credited to a parent's piggy bank and swept into
collections with no human in the loop. Two things make this an acceptable risk instead of a
reckless one:

1. **Every automatic operation writes a `ledger.LedgerEntry`** (`PIGGY_BANK_CREDITED`,
   `PIGGY_BANK_APPLIED_TO_COLLECTION`, `CONTRIBUTION_RECEIVED`) - nothing happens silently,
   everything is retrospectively auditable per parent via `GET /api/parents/{id}/ledger`
   and the frontend's `ParentView` page.
2. **Idempotency via `ProcessedTransactionRepositoryPort.claimProcessing`**: a single
   conditional DynamoDB write (`attribute_not_exists(pk)`) claims a bank transaction's
   reference (mBank's own, or a SHA-256 hash of its fields if mBank's HTML doesn't expose
   one - see `MBankStatementHtmlParser.computeReferenceHash`) **immediately before booking
   it**, not via a separate read-then-write check. This is a genuine compare-and-swap, so
   two overlapping polls (or a retried EventBridge invocation) can never both believe they
   own the same transaction - the original design used a `GetItem`-then-`PutItem` pair,
   which raced under exactly that scenario. Claiming happens after matching, not before, so
   an unmatched transaction stays unclaimed and is retried on the next poll. This does NOT
   make the claim atomic with the actual piggy-bank credit that follows it - a crash between
   the two leaves the reference claimed but the money never credited (a logged miss needing
   manual reconciliation, tracked in `PollResult.transactionsFailed`) rather than credited
   twice (the failure mode this replaces). True cross-aggregate atomicity would need a
   DynamoDB `TransactWriteItems` spanning the parent and processed-tx items - deferred until
   this is exercised against a real mailbox. A single transaction's booking failure is
   caught per-transaction in `BankStatementProcessingService.pollAndProcess` so it can never
   abort the rest of that poll cycle.

**`ParentMatchingPolicy`'s confidence threshold is deliberately high** (default 0.90,
`groszdogrosza.bankstatement.matching.min-confidence`) - a normalized Levenshtein
similarity of 0.90 tolerates roughly one wrong/transposed character in a ~10-character
name, not much more. **If more than one parent clears the threshold, the match is treated
as ambiguous and rejected outright** (`ParentMatchingPolicyTest.twoSimilarCandidatesAboveThresholdAreAmbiguousAndNotMatched`)
- two similar surnames (siblings, common Polish surnames) must never be silently guessed
between. An unmatched or ambiguous transaction is logged and left for the treasurer to book
manually via `RecordManualContributionUseCase` - it is deliberately **not** marked as
processed, so it's retried (and logged again) on every subsequent poll until either a
matching parent is added/corrected or it's booked by hand.

**`ContributionAllocationPolicy`** decides, for one matched transaction, how the money
splits between "stays in piggy bank" and "sweeps into active collections": the *entire*
pre-existing piggy bank balance plus the new payment are pooled and applied to that
parent's outstanding `ContributionRequirement`s across ACTIVE collections, **oldest
collection first**, until either the money runs out or every requirement is covered; what's
left stays as the new piggy bank balance. This means an old accumulated piggy bank surplus
gets swept into a brand new collection automatically the next time *any* payment (even an
unrelated small one) arrives for that parent - intentional, matches "skarbonka" (piggy
bank) as a real running balance rather than money tied to a specific past collection.

### The treasurer's own account is excluded from bank matching, and settled by hand

The mailbox being polled (jaros.dabrowski@gmail.com) belongs to the treasurer, who is *also*
a `Parent` in the domain model (their own child pays into collections too) and whose bank
account is the one every parent transfers into. That means the daily statement naturally
contains the treasurer's own account activity - outgoing payments, internal transfers - not
just other parents paying in. `BankStatementProcessingService.pollAndProcess` explicitly
skips (and counts in `PollResult.transactionsIgnoredTreasurerOwnAccount`) any transaction
that `ParentMatchingPolicy` matches to a `Parent` whose `role()` is `TREASURER` - crediting
those would let the treasurer accidentally double-count their own money as if it were a
parent's contribution. Instead, the treasurer's piggy bank is topped up **manually** via
`POST /api/parents/{id}/piggy-bank/credit` (`CreditPiggyBankManuallyUseCase`, treasurer-only,
records a `PIGGY_BANK_CREDITED` ledger entry) - e.g. crediting it 1000 zł once covers several
future collections, since the treasurer already knows they'll be paying from their own
account regardless of what the statement shows. From there on the treasurer is just another
row in `Parent`: `CollectionService.createCollection` computes their `ContributionRequirement`
against their piggy bank balance exactly like everyone else's, so a new 50 zł/parent
collection automatically draws down the treasurer's manually-credited balance too.

### Authorization: treasurer vs. parent, stored on `Parent`, not the identity provider

Every `/api/*` endpoint requires `@Authenticated` (any logged-in Cognito/Keycloak user), but
that alone doesn't distinguish the treasurer from a regular parent. **Who's the treasurer is
stored on `Parent.role()` (`ParentRole.TREASURER`/`PARENT`), this app's own data - not
derived from an OIDC role/group claim.** This was a deliberate choice over the more common
"Cognito/Keycloak group → `@RolesAllowed`" pattern: it keeps a single source of truth (no
Keycloak-realm-role/Cognito-User-Pool-Group configuration to keep in sync across two
environments) and lets the treasurer manage roles the same way they manage everything else -
by creating a `Parent` record with the right role, via `CreateParentRequest.role`.

`platform.security.AuthorizationSupport` is the one place this logic lives: it reads the
OIDC token's verified `email` claim (`JsonWebToken.getClaim("email")`, cast from
`SecurityIdentity.getPrincipal()` - no `quarkus-smallrye-jwt` dependency needed, since
`quarkus-oidc`'s own JWT caller principal already implements that interface) and looks up
the matching `Parent` by email (`ParentRepositoryPort.findByEmail`, a full-scan-and-filter
like every other read in this table - fine at this scale). A parent's own identity is
established purely by this email match - **no separate account-linking step or stored
Cognito subject id is needed**, since the treasurer enters each parent's real email when
creating their record and Cognito only ever issues accounts for addresses the treasurer
created. Every resource method calls `authorizationSupport.requireTreasurer(identity)` or
`.requireSelfOrTreasurer(identity, parentEmail)` explicitly (not `@RolesAllowed`, since that
annotation only understands identity-provider role claims) - see `ParentResource`,
`LedgerResource`, `CollectionResource`.

**What this means concretely**: `GET /api/parents`, `POST /api/parents`,
`POST /api/parents/{id}/piggy-bank/credit`, `POST /api/collections`,
`POST /api/collections/{id}/contributions` and `POST /api/collections/{id}/settle` are all
treasurer-only. `GET /api/parents/{id}` and `GET /api/parents/{id}/ledger` require the
caller to either be the treasurer or that exact parent (matched by email) - without this,
any logged-in parent could enumerate every other family's balance and payment history, which
was the original gap this closes. `GET /api/parents/me` lets the frontend discover the
current account's own `Parent` record (id, role) right after login without knowing their
`parentId` up front - 404 if the treasurer hasn't created a matching record yet.
`GET /api/collections/{id}` is available to everyone, but returns a different shape per
role: a treasurer gets `CollectionDetailsResponse` (full per-parent requirement/contribution
breakdown, and the settle form on the frontend); a regular parent gets
`CollectionProgressResponse` (aggregate totals and a percentage only - how many parents
paid, how much is still owed in total - never another family's individual amounts or
identity). See `CollectionResource.get` and the frontend's `CollectionDetails` component
(`isCollectionDetails` type guard in `core/models.ts`).

**Bootstrap gap, not yet solved**: the very first `Parent` record with `role=TREASURER`
still has to be created by *someone* already holding treasurer access - a chicken-and-egg
problem for a brand new deployment. For now this means seeding it manually (e.g. directly in
DynamoDB, or temporarily relaxing the check for one call) rather than through the normal
treasurer-only `POST /api/parents` - see "TODO for the next session".

### Public collection overview - deliberately unauthenticated

The user explicitly wants a class-wide "how's the collection going and how do I pay" page
visible to **anyone**, no login required - a grandparent, a parent who never bothered
creating an account, anyone with the link. `GET /api/public/overview`
(`platform.web.PublicOverviewResource`) is the one endpoint in this app with no
`@Authenticated` at all, carved out via `quarkus.http.auth.permission.public.paths` (which
must include `/api/public/*` alongside the pre-existing `/api/auth-config`) rather than a
role check - Quarkus's `@Authenticated` model has no "public" level to fall back to, so this
has to be a routing-level exemption, same mechanism as `/internal/*` for the bank-statement
poll (see below) but with the opposite intent (never authenticated, vs. authenticated by a
shared secret instead of Cognito).

It returns, for every currently `ACTIVE` collection, the exact same aggregate-only shape
`CollectionProgressResponse` already gives a logged-in non-treasurer parent (percent
complete, how many parents paid out of how many, total collected out of total required) -
**never** a per-parent breakdown, matching the user's explicit choice (see below) that
individual names/amounts stay behind login. Alongside that, it returns the treasurer's
payment info (`Parent.paymentInfo`, a `bankAccountNumber`/`blikPhoneNumber` pair, settable
via the treasurer-only `PUT /api/parents/{id}/payment-info` and edited from a card on
`TreasurerPanel`) so anyone can actually pay in without needing an account first. Payment
info is deliberately modeled as one field on **the treasurer's own** `Parent` record, not a
per-collection setting - every collection is paid into the same account regardless of which
collection it's for, so duplicating account/BLIK fields onto every `Collection` would just
be a chance for them to drift out of sync. `ParentService.getTreasurerPaymentInfo` finds it
by scanning for `role == TREASURER` (there's normally exactly one - see `ParentRole`), not
by a dedicated "the" pointer, consistent with how the rest of this app treats the treasurer
as just another `Parent` row rather than a special singleton entity.

The Angular route `/` was repointed from `Dashboard` to a new unauthenticated
`PublicOverview` component for this - `Dashboard` (the full collection list, previously at
`/`) moved to `/dashboard`, now behind `authGuard` like everything else. `PublicOverview`
shows a "log in to see your own piggy bank" prompt (`AuthService.login()`) rather than
forcing a redirect, since the whole point is that an anonymous visitor can use this page.

**The full cross-parent transaction log stays authenticated, on purpose** - the user's own
worked example ("Kowalski wpłacił 50 zł... 50 zł przekazane do skarbonki Kowalskiego...")
names specific parents, which is exactly the per-parent detail kept off the public page
(see above). `GET /api/ledger` (`ledger.adapter.in.web.GlobalLedgerResource`) is
**treasurer-only** (`AuthorizationSupport.requireTreasurer`, same pattern as every other
treasurer-only endpoint) and returns every parent's `LedgerEntry`s in one feed, newest first
(`LedgerRepositoryPort.findAll` - a full table scan filtered by the `LEDGER#` sk prefix,
same accepted-at-this-scale tradeoff as every other cross-partition read in this adapter,
sorted in application code since `Scan` has no ordering guarantee), enriched with each
entry's `parentName` (looked up once via `ListParentsUseCase` and joined in
`GlobalLedgerResource`, since a global feed - unlike a single parent's own ledger page -
needs to say whose entry each one is). A *regular* parent still only ever sees their own
entries via the pre-existing `GET /api/parents/{id}/ledger` (self-or-treasurer, unchanged) -
there is deliberately no "any parent can see everyone's log" middle option, matching the
user's explicit answer when asked ("tylko po zalogowaniu i dostępny dla skarbnika dla
dowolnej osoby").

### Why IMAP + a Gmail App Password instead of the Gmail API/OAuth

The mail lives in a personal Gmail (jaros.dabrowski@gmail.com), read by one unattended
backend process, for one purpose (find today's mBank statement mail). Registering a Google
Cloud OAuth client and running a consent flow for an unattended Lambda is real ongoing
operational overhead (managing a refresh token's lifecycle, Google's OAuth consent screen
verification requirements for anything beyond a handful of test users) for no benefit over
a **Gmail App Password**: scoped to "mail access only", independently revocable from the
Google account password, requires 2FA to already be enabled on the account, and needs zero
token-refresh machinery - `ImapBankStatementFetchAdapter` just holds host/username/password
in config (`groszdogrosza.bankstatement.imap.*`, deliberately empty in the repo, see
`application.properties`). This is the same "simplest thing that's still actually secure
for a single-user personal use case" reasoning as pvopt's Basic Auth choice - don't
"upgrade" this to full OAuth without a real reason (e.g. moving off a personal Gmail to a
shared/managed mailbox, where an App Password stops being available at all since Google
disables them for accounts under org-level 2FA enforcement policies in some Workspace
configurations).

### Why EventBridge Scheduler instead of `@Scheduled` in Lambda

**This is the one place groszdogrosza's architecture has to diverge from pvopt's.** pvopt
runs as a long-lived process (`quarkus.scheduler.start-mode=forced` + Quarkus's in-process
`@Scheduled`/programmatic Scheduler) because it's deployed as a regular process on a
machine that's always on. A Lambda instance, by contrast, **only exists for the duration of
one invocation** - there is no persistent process for Quarkus's scheduler engine to tick in
between invocations, so a plain `@Scheduled` method deployed to Lambda simply never fires in
production, silently. There's no error, no crash, no log - it just never runs, which is a
dangerous kind of silent failure for something handling real money.

The fix: `POST /internal/bankstatement/poll` (`BankStatementPollResource`) is a normal
JAX-RS endpoint, deliberately **outside `/api/*`** (see
`quarkus.http.auth.permission.internal` in `application.properties`) so it isn't gated by
Cognito/Keycloak - the caller is AWS infrastructure, not a logged-in parent's browser.
It's protected instead by a shared-secret header (`X-Poll-Secret`,
`groszdogrosza.bankstatement.poll-secret`), checked in the resource itself. In AWS,
`infra/main/main.tf`'s `aws_scheduler_schedule.bankstatement_poll` fires once a day
(`cron(0 6 * * ? *)` UTC) and calls this endpoint via an EventBridge **API destination**
(`aws_cloudwatch_event_api_destination` + `aws_cloudwatch_event_connection` holding the
`X-Poll-Secret` value as an API-key-style header) - a direct "invoke this Lambda"
EventBridge Scheduler target uses the Lambda Invoke API instead of a real HTTPS call and
can't attach a custom header, which is why an API destination (a genuine HTTPS call with
headers) is needed instead of the simpler-looking direct-Lambda-target option.

For local development, `BankStatementDevPoller` (`adapter.in.scheduler`,
`@IfBuildProfile("dev")` **and** gated by the dev-only
`groszdogrosza.bankstatement.dev-poll-interval` config property) uses a real `@Scheduled`
method - safe here because `quarkus:dev` *is* a long-running process. **This class must
never run outside dev mode** - the `@IfBuildProfile("dev")` annotation means the bean
doesn't even exist in a packaged build, so there's no scheduler wiring to accidentally
misfire in Lambda. Don't remove that annotation "to simplify" - it's the actual safety
mechanism, not a redundant belt-and-suspenders comment.

### `ledger`: enum + params, not pre-rendered text

`LedgerEntry` carries `LedgerEventType` + `Map<String,String> params`, never a rendered
sentence - identical pattern to `ChargeDecision`/`DecisionReason` in the sibling "pvopt"
project. Rendering happens in the frontend via i18n: `ledger.<EVENT_TYPE>` keys in
`en.json`/`pl.json` with placeholders matching whatever keys the backend put in `params`
for that event type (see `ParentView.translationKeyFor` and the `ledger.*` keys in both
JSON files). If you add a new `LedgerEventType` value or change what a `record(...)` call's
`params` map contains, update **both** language files' `ledger.*` keys to match, or the
frontend renders raw `{{placeholder}}` text for that entry.

### Persistence: DynamoDB, single table, composite key (pk/sk)

`quarkus-amazon-dynamodb` (+ `software.amazon.awssdk:url-connection-client`, which the
extension needs explicitly - `mvn package` fails with `DeploymentException: Missing
'software.amazon.awssdk:url-connection-client' dependency` without it, discovered during
scaffolding). No Flyway, no JPA/Hibernate. Dev Services auto-starts a local
Localstack-backed DynamoDB in dev/test mode; `platform.persistence.DynamoDbTableInitializer`
creates the table on `StartupEvent` if it doesn't exist yet (catches
`ResourceInUseException` and no-ops in prod, where Terraform already created it) - this is
what makes `quarkus:dev` work with zero manual table setup, and it's a deliberate departure
from turboorders (which has no DynamoDB-backed logic yet to need this).

**Single table, `pk`/`sk` composite key** - a deliberate departure from turboorders' single
hash-key table: turboorders has one flat entity type, groszdogrosza has several related
entity types per aggregate (a collection's requirements and contributions). Key layout,
see each adapter's class javadoc for the authoritative detail:

```
PARENT#<id>          / PARENT                        - Parent
COLLECTION#<id>       / COLLECTION                     - Collection
COLLECTION#<id>       / REQUIREMENT#<parentId>          - ContributionRequirement
COLLECTION#<id>       / CONTRIBUTION#<contributionId>    - Contribution
PARENT#<parentId>     / LEDGER#<occurredAt ISO>#<id>      - LedgerEntry (Query, ScanIndexForward=false -> newest first)
PROCESSEDTX#<bankRef> / PROCESSEDTX                        - bank transaction idempotency marker
```

`CollectionRepositoryPort.findActivePendingRequirementsForParent` is the one query that
cuts across collections (not one partition) - it does a table `Scan` filtered by `sk` and
status, then re-checks each candidate's parent collection is ACTIVE and sorts
oldest-collection-first. A full scan is fine at this app's actual scale (one class, a
handful of collections a year, maybe 20-30 parents) - if that ever stops being true, add a
GSI keyed by parentId instead of "optimizing" the scan itself.

`platform.persistence.Attr` centralizes the handful of `AttributeValue` conversions
actually used (String, BigDecimal via `.toPlainString()`, Instant via ISO-8601 string,
nullable String via DynamoDB's real `NULL` type, a flat `Map<String,String>` for
`LedgerEntry.params`) - it is deliberately not a general object-mapping layer; each
adapter still writes its own explicit item shape rather than relying on annotations/reflection.

### `platform/security`: identical to turboorders

`quarkus-oidc`, `application-type=service`, `AuthConfigResource` public on `/api/auth-config`,
`permission.authenticated` on `/api/*`, Dev Services local Keycloak with a custom realm
import (`keycloak-realm.json` - one `treasurer` role, one `skarbnik`/`skarbnik` test user;
groszdogrosza only ever has one real user, the treasurer, unlike turboorders' multi-user
alice/bob setup). See turboorders' own CLAUDE.md for the detailed reasoning behind every
OIDC/Cognito/Keycloak decision here (strict discovery document validation, Cognito's
non-standard logout endpoint, the two required `aws_lambda_permission` grants, etc.) - it
is not repeated in this file since it's identical, not adapted.

**One divergence**: `/internal/*` (the bank statement poll trigger) is explicitly carved
out of `permission.authenticated` via its own `permission.internal` rule set to `permit` -
see "Why EventBridge Scheduler instead of `@Scheduled`" above. Don't fold `/internal/*`
under `/api/*`'s authenticated policy; it needs to stay reachable without a Cognito bearer
token for EventBridge to be able to call it at all.

### AWS Lambda packaging and dev-mode/Lambda-extension conflict

Identical setup and identical underlying bug to turboorders - `quarkus-amazon-lambda-http`
is a Maven profile (`-Plambda`), not a plain dependency, because its dev-mode poll loop
collides with Quinoa's live-coding forward proxy on the same
`io.quarkus.runtime.ValueRegistry` key. See turboorders' CLAUDE.md, "Dev mode vs. the
Lambda extension", for the full confirmed-reproduction writeup - not re-verified
independently for this project but there is no reason to expect it not to apply (same
Quarkus platform version, same extension, same Quinoa version).

### AWS infrastructure

Same serverless shape as turboorders (ECR + Lambda container image + Function URL,
`authorization_type = NONE` at the AWS layer, Cognito for real auth, no VPC/ALB/ECS) with
two additions specific to this app:

- **Composite-key DynamoDB table** (`hash_key = pk`, `range_key = sk`) instead of
  turboorders' single hash key - must match `DynamoDbTableInitializer`'s key names exactly.
- **EventBridge Scheduler + API destination** for the daily bank statement poll (see above)
  - `aws_cloudwatch_event_connection` holds the `X-Poll-Secret` value as an API-key auth
    parameter (`var.bankstatement_poll_secret`, a sensitive Terraform variable, never
    committed - set via `terraform.tfvars` (gitignored) or `TF_VAR_*` env vars, see
    `infra/main/terraform.tfvars.example`). `aws_scheduler_schedule` needs its own IAM
    execution role (`scheduler.amazonaws.com` principal) with `events:InvokeApiDestination`
    scoped to that one API destination ARN - a separate role from the Lambda execution
    role, since EventBridge Scheduler assumes it, not the Lambda service.
- Lambda env vars also carry `GROSZDOGROSZA_BANKSTATEMENT_POLL_SECRET`,
  `GROSZDOGROSZA_BANKSTATEMENT_IMAP_USERNAME`, `GROSZDOGROSZA_BANKSTATEMENT_IMAP_APP_PASSWORD`
  (all from sensitive Terraform variables, all empty by default) alongside the
  OIDC/DynamoDB ones turboorders already has.

Same chicken-and-egg first-deploy sequence, same `aws_lambda_permission` double-grant
requirement, same Cognito callback-URL dependency-cycle workaround, same tag-per-build
image strategy via `scripts/build.sh`/`target/.image-tag` - see turboorders' CLAUDE.md for
why each of those is the way it is; `scripts/deploy.sh`'s header comment here has the
groszdogrosza-specific step-by-step (including the two new secrets).

### Frontend

Angular 20, standalone components + signals, Angular Material M3 (`primary:
mat.$green-palette`, `tertiary: mat.$orange-palette` - a friendlier, "piggy bank" set of
tones than turboorders' cosmetics-brand orange/rose or pvopt's Bauhaus blue/yellow/red).
`@ngx-translate` with **Polish as the default and fallback language** (`lang: 'pl',
fallbackLang: 'pl'` in `app.config.ts` - the one place this project's i18n setup
deliberately differs from turboorders/pvopt, which default to `pl`/`en` fallback or plain
`en` respectively; there is no English-speaking audience for this specific app, Polish
coming first everywhere is intentional, not an oversight).

`angular-oauth2-oidc` + `AuthService`/`authGuard`/`authInterceptor` in `core/` are a
line-for-line port of turboorders' auth setup (see its CLAUDE.md for the Cognito-specific
gotchas: strict discovery document validation, the manual dual-parameter logout URL). One
addition: an explicit, non-guarded `Login` page (`/login`) that calls `authService.login()`
on a button click - turboorders relies purely on `authGuard`'s implicit redirect and has no
dedicated login route; this project adds one so there's an explicit landing point after a
logout (`AuthService.logout()` redirects back to `/`, which immediately re-triggers
`authGuard` anyway, so `/login` is mostly a discoverable manual re-entry point rather than
a load-bearing part of the auth flow).

Pages: `PublicOverview` (`/`, unauthenticated - active collections' progress + how to pay,
see "Public collection overview" above), `Dashboard` (`/dashboard`, guarded, full collection
list with a status chip), `CollectionDetails` (`/collections/:id`, requirements table,
contributions list, the settle form - see the "no preview yet" TODO above), `ParentView`
(`/parents/:id`, piggy bank balance + ledger rendered via the `ledger.*` i18n keys),
`GlobalLedger` (`/ledger`, guarded, treasurer-only - every parent's ledger entries in one
feed, each prefixed with `parentName`), `TreasurerPanel` (`/treasurer`, parent list + "add
parent" + "create collection" forms + the payment-info card - the closest thing to an admin
page), `Login` (`/login`, unguarded).

**Client-side treasurer guard, on top of the backend's own check**: `/treasurer` and
`/ledger` used to be gated only by `authGuard` (any logged-in user) - the page shells
rendered for any parent, and `TreasurerPanel`'s API calls had no error handling, so a
regular parent could land on a page that *looked* like an active treasurer panel even
though every write the backend actually did anything with was still correctly rejected
(`requireTreasurer`). `core/current-user.service.ts` (`CurrentUserService`, caches
`GET /api/parents/me`) + `core/treasurer.guard.ts` (`treasurerGuard`, redirects to
`/dashboard` if `role !== TREASURER`) close this at the UX level; `app.html`'s nav also
hides the Dziennik/Panel skarbnika links for non-treasurers. This is a UX fix, not a
security boundary - `AuthorizationSupport.requireTreasurer` on the backend is and remains
the only thing that actually matters for data protection.

**Visual style - redesigned from the original scaffolding pass**: `--gg-*` custom
properties on `:root` in `styles.scss` are the published "Lagoon Latte" teal/coral/gold
palette (`#2A9D8F` teal, `#FF7A6E` coral, `#E9C46A` gold, `#FAF3E0` cream, `#264653` deep
teal-navy for ink) - picked by comparing it against two other candidate palettes
("Aqua Terracotta" - too muted/adult, no gold; "Freshwater Coral" - too pastel, no
money-ish accent) rather than freehanded, after an earlier freehand attempt (a hand-tuned
emerald/rose "color wheel" scheme, then an even more saturated gradient-heavy pass) didn't
land - the user's own feedback was "colors don't go together" and, separately, "you went
overboard with gradients." **Gradients are used in exactly two places on purpose** - the
toolbar (`--gg-gradient-ink`, teal fading into the palette's own ink color) and the coin/
logo-mark circle (`--gg-gradient-warm`, cream-gold to deep gold) - not on every card border,
button, or heading; that blanket-gradient version was explicitly walked back after user
feedback. `--gg-ink` is that palette's own dark teal-navy, not generic near-black, which is
what keeps the toolbar/text/shadows feeling like one family rather than "dark UI chrome +
pastel accents" bolted together - see the `:root` block's own comment for the reasoning.
Each accent (`--gg-coin`/`--gg-mint`/`--gg-blush`/`--gg-sky`) carries a `-soft` tint for
chip/row backgrounds - same "hand-picked palette as plain CSS custom properties, used only
where the app fully controls the surface, `--mat-sys-*` tokens for anything Material
renders itself" convention as pvopt (`--pv-*`) and turboorders (`--to-*`). Typography is
Inter (body, and `mat.theme`'s `typography` - a deliberate departure from turboorders/
pvopt's plain Roboto, chosen for a more "real product" feel) + Nunito 800/900 for headings,
both loaded in `index.html`. `.gg-card` (rounded corners,
resting shadow via `--gg-shadow-md`, lift-and-deepen-shadow on hover when wrapped in
`a.collection-link` or given `.gg-card--interactive`) replaces the old flat top-accent-only
card; `--mint`/`--blush`/`--sky` still work as border-top accent modifiers. `.gg-fade-up`
(+ a `--gg-stagger` custom property set per-card, e.g. `[style.--gg-stagger.ms]="i * 70"`)
gives lists of cards a staggered entrance animation on load. `shared/logo/logo.ts`
(`<app-logo>`) is the app's mark - a piggy-bank-with-a-coin SVG using `currentColor` for the
body/shading (so it reads correctly both on the dark toolbar and on light hero surfaces) and
a fixed gold for the coin itself; used in the toolbar, `Login`, and `PublicOverview`'s hero.
Shared cross-page utility classes (`.back-link`, `.timeline`/`.timeline-icon`, `.loading-line`/
`.empty-state`, `.status-chip--*`, `.title-with-icon` - the last needed because a bare
`mat-icon` sibling before `mat-card-title` doesn't get picked up by Material's card-header
grid and renders in the wrong place; put the icon *inside* `mat-card-title` with this class
instead) live in `styles.scss`, not duplicated per component - see ParentView/GlobalLedger/
CollectionDetails for the pattern. The toolbar (`app.html`/`app.scss`) collapses its inline
nav links into a `mat-menu`-driven hamburger below 720px - verified with Playwright at a
390px viewport, not just by inspecting CSS.

`ng build` was verified working during scaffolding: succeeds, one non-fatal warning
(initial bundle ~546kB against a 500kB soft budget in `angular.json`) - not addressed yet,
see TODO. Not re-measured after the redesign (fonts changed, one new shared component) -
worth checking next time that TODO is picked up.

## Verified end-to-end locally (real browser + a mock mailbox)

`quarkus:dev` was actually run for the first time this session, driven by a real headless
browser (Playwright, scripted - no MCP server wired into this repo yet, see `.mcp.json`) and
a mock mail server (GreenMail, Docker), rather than just `mvn test`. This surfaced two real
bugs that all the unit/compile-level checks so far had missed entirely:

1. **The app failed to boot at all in `quarkus:dev`** - `groszdogrosza.bankstatement.imap.username`/
   `app-password` and `groszdogrosza.bankstatement.poll-secret` were plain (non-`Optional`)
   `@ConfigProperty String` fields with no default, deliberately left as empty values in
   `application.properties` until configured. SmallRye Config treats a property with an
   *empty* value (as opposed to one that's absent) as "not set" by default, so a required
   `@ConfigProperty` with no default fails Quarkus startup outright the moment anything
   actually tries to start (not at compile time - `mvn test`/`mvn package` never exercises
   CDI injection of these fields the way a running app does). Fixed by making all three
   `Optional<String>` (`ImapBankStatementFetchAdapter`, `BankStatementPollResource`) - the
   existing blank-checks (`username.isBlank()` etc.) already treated "not configured" as a
   normal, expected state, so the fix was purely about not crashing the whole app over it.
   **This class of bug - `@ConfigProperty` fields deliberately left blank in the repo - will
   never be caught by `mvn test`/`mvn package` alone; only actually running `quarkus:dev` (or
   deploying) exercises it.**
2. **The settlement result card disappeared the instant it appeared** -
   `CollectionDetails`'s settle-result block (`@if (settlementPreview(); as result)`) was
   nested inside `@if (v.collection.status === 'ACTIVE')`. `settle()` flips the collection to
   `SETTLED` and reloads, so the moment the result was computed, the reload made the whole
   surrounding block (including the result the treasurer needed to actually see, e.g. "+10 zł
   do skarbonki" per parent) vanish. Moved the result card outside the `ACTIVE`-only block so
   it survives the reload - see `collection-details.html`.

Also fixed while testing (smaller, found by inspection, not by a crash): a minor CSS layout
bug on the public overview page (payment-info card overlapping the collection cards below it
- `public-overview.scss` was missing the `mat-card { margin-top: 1rem; }` convention every
other page already has).

**What was confirmed working, end-to-end, with real HTTP requests and real (local) data**:
public overview page with no auth; unauthenticated rejection (401) of every `/api/*` and
`/api/ledger` endpoint except `/api/public/*`; treasurer creating parents, a collection, and
payment info via the actual UI; a regular parent (logged in via real Keycloak login flow)
getting the aggregate-only collection view, being rejected (403) from every treasurer-only
endpoint (create/settle/manual-contribution/global-ledger/piggy-bank-credit/payment-info),
and reading their own ledger; one parent being unable to read another parent's record or
ledger by id (the BOLA fix from the previous session, re-verified against a live server, not
just the domain logic); a full mock mBank email → `POST /internal/bankstatement/poll` →
piggy-bank-credited → swept-into-collection → `CONTRIBUTION_RECEIVED` pipeline, including the
treasurer's own transfer being correctly ignored (`transactionsIgnoredTreasurerOwnAccount`)
and a re-run of the same poll being a no-op (`transactionsSkippedAlreadyProcessed`, not a
double-credit); settling a collection and the surplus correctly landing in each contributing
parent's piggy bank, then correctly reducing their *next* collection's requirement; and a
second `settle` call on an already-`SETTLED` collection being rejected with `409`.

**How to reproduce this locally** (useful for the next session, since none of this is
automated yet - there is no `@QuarkusTest` covering any of it):
- `./mvnw quarkus:dev` boots Dev Services (local Keycloak + DynamoDB via Localstack) but the
  very first treasurer `Parent` still has the bootstrap-gap problem (see above) - seed one
  directly: `aws dynamodb put-item --endpoint-url <Dev Services DynamoDB URL, logged at
  startup> --region eu-central-1 --table-name groszdogrosza --item '{"pk":{"S":"PARENT#<uuid>"},
  "sk":{"S":"PARENT"},"id":{"S":"<uuid>"},"firstName":{"S":"..."},"lastName":{"S":"..."},
  "email":{"S":"skarbnik@example.com"},"expectedSenderName":{"S":"..."},"cognitoSubjectId":
  {"NULL":true},"role":{"S":"TREASURER"},"piggyBankBalance":{"N":"0"},"bankAccountNumber":
  {"NULL":true},"blikPhoneNumber":{"NULL":true}}'` (dummy AWS creds, e.g.
  `AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test`, work fine against Localstack).
- `keycloak-realm.json` now seeds three dev users: `skarbnik`/`skarbnik` (matches the
  treasurer seeded above), `rodzic1`/`rodzic1` (anna.testowa@example.com), `rodzic2`/`rodzic2`
  (piotr.testowy@example.com) - deliberately kept in the repo so the next session doesn't
  have to recreate them.
- For the bank-statement pipeline, a GreenMail container stands in for Gmail:
  `docker run -d --name gg-greenmail -e GREENMAIL_OPTS='-Dgreenmail.setup.test.smtp
  -Dgreenmail.setup.test.imaps -Dgreenmail.hostname=0.0.0.0 -Dgreenmail.users=skarbnik:skarbnik@example.com'
  -p 3025:3025 -p 3993:3993 greenmail/standalone:2.1.6` - note **`-Dgreenmail.hostname=0.0.0.0`
  is required** (GreenMail defaults to binding `127.0.0.1` *inside* the container, which
  Docker's port mapping can't forward into), and GreenMail's IMAP `LOGIN` wants the bare
  username (`skarbnik`), not the full email address, unlike real Gmail which wants the full
  address - set `GROSZDOGROSZA_BANKSTATEMENT_IMAP_USERNAME=skarbnik` (not
  `skarbnik@example.com`) when pointing at GreenMail specifically. Send a test email with
  Python's `smtplib`/`email.mime.multipart` (see the shape in
  `MBankStatementHtmlParser`'s class javadoc / the anonymized fixture) to `localhost:3025`,
  `From: kontakt@mbank.pl`, then `curl -X POST localhost:8080/internal/bankstatement/poll
  -H "X-Poll-Secret: <groszdogrosza.bankstatement.poll-secret>"`.

## First production deployment (confirmed against real AWS, not just planned)

The full stack is live: `infra/bootstrap` and `infra/main` have both actually been applied
against the real AWS account (814478897174, eu-central-1) - Function URL
`https://ju3aa5pkvfu7vemof5xr55xpyq0ilifs.lambda-url.eu-central-1.on.aws/`, Cognito user
pool `eu-central-1_JG96wYzao` / SPA client `5f3ajvquknpm62j076gsampbf4`. None of this was
exercised end-to-end before this session - four more real, previously-undetected bugs
surfaced, on top of the two from local `quarkus:dev` testing (see "Verified end-to-end
locally" above). **Every one of these would have been caught by actually deploying once,
and none of them were catchable by `mvn test`/`mvn package` alone or even by `quarkus:dev`
against Dev Services, which papers over exactly these problems** (Dev Services' Localstack
DynamoDB has no IAM at all, and dev mode's `DynamoDbTableInitializer` creates the table
under whatever name the app itself expects, so a table-name mismatch can't happen there).

1. **`DynamoDbTableInitializer` crashed the Lambda's cold start outright** - not just wasted
   latency as originally assessed (see the "known non-blocking issues" list further down,
   now stale on this point): the prod Lambda role deliberately has no `dynamodb:CreateTable`
   (see below), so the unconditional `CreateTable` call threw an uncaught `DynamoDbException`
   from a `@Observes StartupEvent` method, which crashes Quarkus's entire boot - every single
   request 502'd. Fixed with `@IfBuildProfile("dev")`.
   **The gotcha within the gotcha**: putting `@IfBuildProfile("dev")` on just the `onStart`
   method (not the class) compiled fine and looked correct, but did NOT actually exclude the
   bean from the prod build - the observer still fired in the deployed Lambda, confirmed by
   re-deploying and hitting the exact same crash again. Moving the annotation to the class
   level (matching `BankStatementDevPoller`'s already-correct pattern) is what actually
   worked. If you ever add another dev-only bean, put `@IfBuildProfile` on the class, not an
   individual method - method-level placement was tried here and silently didn't work.
2. **EventBridge *Scheduler* (`aws_scheduler_schedule`) cannot target an API destination at
   all** - `CreateSchedule` rejected the API destination's own ARN with "Provided Arn is not
   in correct format". This was a wrong assumption baked into the original design (and into
   CLAUDE.md's own "Why EventBridge Scheduler" explanation, now corrected below): Scheduler's
   target types are Lambda/SQS/SNS/StepFunctions/ECS/an event bus/a fixed list of "universal"
   aws-sdk targets - a raw API destination ARN isn't one of them. Scheduling a call to an API
   destination is specifically a **classic EventBridge Rule** feature
   (`aws_cloudwatch_event_rule` with a `schedule_expression`, plus `aws_cloudwatch_event_target`
   pointing at the API destination) - a different, older service under the same "EventBridge"
   product umbrella. Switched to that; the IAM role's assumed principal changed from
   `scheduler.amazonaws.com` to `events.amazonaws.com` accordingly (the resource is still
   named `aws_iam_role.scheduler_exec` in `main.tf` to avoid a bigger rename - it's really
   the event rule's role now).
3. **`GROSZDOGROSZA_DYNAMODB_TABLE_NAME` was never actually wired into the Lambda's
   environment** - the app silently fell back to `application.properties`' literal
   `groszdogrosza` default (correct for local dev, where `DynamoDbTableInitializer` creates
   a table by that exact name), while Terraform creates the real table as
   `groszdogrosza-prod` (`local.name`). Every DynamoDB call 403'd with "no identity-based
   policy allows ... on resource ... table/groszdogrosza" - a deeply misleading error, since
   the IAM policy was actually fine; it was correctly scoped to a table name the app just
   never asked for. Added the missing environment variable in `main.tf`.
4. **The EventBridge Connection (for the API-key-authenticated call to the poll endpoint)
   needed three IAM permissions nobody would think to grant upfront**: `iam:CreateServiceLinkedRole`
   (EventBridge API destinations auto-provision `AWSServiceRoleForAmazonEventBridgeApiDestinations`
   on first use - discovered only by hitting "Failed to create service linked role because
   the caller does not have sufficient permissions" on `terraform apply`), permissions on a
   Secrets Manager secret under the `events!connection/*` prefix (the connection's API key is
   stored there, not inline - discovered via "Failed to create the secret because the user is
   not authorized"), and `iam:UpdateAssumeRolePolicy` (needed once the Scheduler→Rule switch
   above required changing an existing role's trust policy, not just its permissions).
   `infra/iam/terraform-user-policy.json` now includes all of these - see that file for the
   exact scoped statements (`SLR`, `EBSec`, and the `UpdateAssumeRolePolicy` action in `Iam`).

**A real, previously-unmentioned AWS IAM constraint**: a customer-managed policy's JSON is
capped at 6144 *non-whitespace* characters. `infra/iam/terraform-user-policy.json` hit this
repeatedly while accumulating the fixes above - `Sid` values were shortened (e.g.
`EventBridgeServiceLinkedRole` → `SLR`, `TerraformStateBucketMeta` → `TFStateBucket`) purely
to fit under the limit; they carry no other significance, don't rename them back without
checking the character count (`python3 -c "import json; print(len(''.join(json.dumps(json.load(open('infra/iam/terraform-user-policy.json')),separators=(',',':')).split())))"`
against the *current* file before pasting a new version into the IAM console).

This is deployed but **not yet in daily real use**: the treasurer's own `Parent` record
(role=TREASURER) still needs seeding (see "Bootstrap gap" above - same manual
`aws dynamodb put-item` recipe used for local testing, just against the real table and a
real Cognito user created via `admin-create-user`), and the bank-statement pipeline still
needs a real Gmail App Password (TODO #2 below) before the daily EventBridge-triggered poll
does anything beyond finding zero configured credentials and skipping gracefully.

## TODO for the next session

Roughly in the order they'd block real usage:

1. ~~Get a real mBank statement HTML sample~~ **Done** - the user provided a real
   "Powiadomienie e-mail" (anonymized before committing; see
   `src/test/resources/mbank/sample-statement.html`'s header comment for what was changed).
   This turned out to be a fundamentally different shape than originally guessed: not a
   batch statement export with one row per transaction field, but a daily event log (one
   `<table>`, columns "Czas operacji"/"Opis operacji", one free-text Polish sentence per
   event, mixing transfers with unrelated events like login confirmations) covering one
   calendar day, with the date only in the page heading, not per row. `MBankStatementHtmlParser`
   was rewritten against this real structure - see its class javadoc. `ImapBankStatementFetchAdapter.extractHtmlPart`
   was also made recursive (was one-level-only) so a `multipart/related`-wrapped HTML part
   (e.g. an embedded logo) isn't silently missed - still unconfirmed against the real raw
   MIME source though (only the rendered/saved HTML was available), so verify this once
   real IMAP access works (TODO #2).
   **Outgoing transfers are a non-issue by design, not just by the regex**: the user
   configured mBank's notification settings to fire only for incoming transfers, so an
   outgoing-transfer sentence will never appear in this mailbox at all - `INCOMING_TRANSFER_PATTERN`
   requiring literally "Przelew przych." is a second, redundant safety net, not the only
   thing preventing a wrongly-matched outgoing payment.
   **Still open**: whether the trailing reference code (`/OPF/AN/PL11...`) is ever a
   human-typed title vs. always a structured code is unconfirmed; the "From" address for
   `groszdogrosza.bankstatement.imap.expected-sender` also still needs verifying against a
   real header (only the rendered body was available, not the raw `.eml`).
2. **Create a Gmail App Password** for jaros.dabrowski@gmail.com (Google Account → Security
   → 2-Step Verification → App passwords - requires 2FA already enabled) and set
   `groszdogrosza.bankstatement.imap.username`/`app-password` locally (env vars, never
   committed) to test `ImapBankStatementFetchAdapter` against the real inbox. The pipeline
   itself (IMAP fetch → parse → match → book → ledger) is now verified end-to-end against a
   mock IMAP/SMTP server (GreenMail) with a synthetic mBank-shaped email - see "Verified
   end-to-end locally" below - so this step is specifically about the real Gmail connection
   (TLS handshake against imap.gmail.com, real credentials), not the business logic.
3. **Decide the settle-preview UX** - right now `POST /collections/{id}/settle` commits
   immediately with no dry-run; the user's spec asked for a preview-then-confirm flow. Add
   either a `dryRun` param to `SettleCollectionUseCase` or a separate preview endpoint, and
   a confirm step in `CollectionDetails`'s template before this is safe to use for real.
4. **Terraform**: run `infra/bootstrap` once for real, `terraform apply -target=aws_ecr_repository.app`,
   then a first real `scripts/build.sh && scripts/deploy.sh` - none of `infra/main` has
   been applied against a real AWS account yet, only `terraform validate`/`terraform fmt`
   during scaffolding. Double-check `aws_scheduler_schedule`'s cron time (`0 6 * * ? *` UTC)
   once real mBank mail delivery timing is known.
5. **Create real parent data and the treasurer's own Cognito account** - nothing exists yet
   beyond the domain model; `TreasurerPanel`'s "add parent" form is the way in once the app
   is actually deployed, and `CreateParentRequest.role` lets the treasurer's own record be
   created with `role=TREASURER` (see TODO #8 below for the bootstrap catch). Once that
   record exists, `POST /api/parents/{id}/piggy-bank/credit` is how the treasurer tops up
   their own piggy bank manually (see "The treasurer's own account is excluded from bank
   matching" above for why that's manual rather than automatic).
6. Fix the `ng build` initial-bundle budget warning (~546kB vs. 500kB) - likely
   Angular Material module imports pulling in more than needed; not urgent, but worth
   trimming before real users load this on a phone.
7. Consider adding a GSI for `findActivePendingRequirementsForParent` if the parent/collection
   count ever grows enough that a full table scan stops being obviously fine (see that
   method's javadoc for why a scan is currently an accepted tradeoff, not an oversight).
8. **Solve the treasurer bootstrap problem** - `POST /api/parents` is treasurer-only, so the
   very first `role=TREASURER` `Parent` record can't be created through the normal API. For
   now it needs a one-off manual DynamoDB write (or a temporarily relaxed check, reverted
   right after) - see "Authorization: treasurer vs. parent" above.

### Known non-blocking issues from code review (not fixed this session)

A full code review pass (8 parallel angles) found several more issues beyond the three the
user asked to fix before the first push (authorization, settle idempotency, atomic booking -
all addressed above). These are real but lower-severity/lower-likelihood at this app's scale
and were deliberately left for a later session:

- **`ContributionSource.BANK_STATEMENT_AUTO` is defined but never actually used** -
  `BankStatementProcessingService.bookMatchedTransaction` always books swept-in money as
  `PIGGY_BANK_APPLIED`, even for the portion that came directly from the just-matched
  transaction rather than a pre-existing balance. Since `ContributionAllocationPolicy` pools
  the two together before allocating, distinguishing them per-euro would need either
  splitting a single requirement's allocation across two `Contribution` records or changing
  the policy's return shape - deferred as a data-provenance/audit-trail issue, not a money
  bug.
- **No optimistic concurrency on `ContributionRequirement` writes**
  (`CollectionDynamoDbAdapter.saveRequirement` is a plain `PutItem`) - a race between the
  automatic bank-matching path and a manual contribution for the same parent/collection at
  the same instant could lose one of the two updates. Low likelihood (this app has one
  treasurer and one daily poll), but a DynamoDB conditional write (`ExpressionAttributeValues`
  checking the previously-read `paidAmount`) would close it properly.
- **`DynamoDbTableInitializer` runs `CreateTable` unconditionally on every startup**,
  including every Lambda cold start in production where Terraform already guarantees the
  table exists - harmless (the resulting `ResourceInUseException` is caught) but wasted
  latency on every cold start. Should be gated behind `@IfBuildProfile("dev")` like
  `BankStatementDevPoller`.
- ~~`ImapBankStatementFetchAdapter` only walked one level of `Multipart`~~ **Fixed** -
  `extractHtmlPart` is now recursive (see TODO #1).
- ~~`MBankStatementHtmlParser.parseAmount` discarded the transaction's sign~~ **Moot with the
  real parser** - only rows matching the confirmed "Przelew przych." (incoming transfer)
  sentence pattern are ever converted to a `BankTransaction` now; anything else (an outgoing
  transfer's sentence, once its shape is confirmed - see TODO #1) is skipped outright rather
  than parsed with a sign to get wrong.
- ~~The idempotency hash was derived from (sender, title, amount, date) only~~ **Fixed** -
  `MBankStatementHtmlParser.computeReferenceHash` now hashes the transaction's date plus its
  full notification sentence (which includes the running post-transaction account balance),
  so two distinct transfers with identical sender/amount/date no longer collide.
- **No UI to manually record a contribution** - `RecordManualContributionUseCase` and
  `CollectionApiService.recordContribution` are fully wired backend-to-frontend-service, but
  no component/template calls it yet (e.g. a cash payment the treasurer wants to log by
  hand). `TreasurerPanel` or `CollectionDetails` needs a small form for it.
