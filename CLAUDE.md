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

Pages: `Dashboard` (`/`, collection cards with a status chip), `CollectionDetails`
(`/collections/:id`, requirements table, contributions list, the settle form - see the
"no preview yet" TODO above), `ParentView` (`/parents/:id`, piggy bank balance + ledger
rendered via the `ledger.*` i18n keys), `TreasurerPanel` (`/treasurer`, parent list + "add
parent" + "create collection" forms - the closest thing to an admin page), `Login`
(`/login`, unguarded).

**Visual style**: `--gg-*` custom properties on `:root` in `styles.scss` (cream/ink base,
coin-gold/mint/blush/sky accents) - same "hand-picked pastel palette as plain CSS custom
properties, used only where the app fully controls the surface (toolbar, card top-accent
stripes), `--mat-sys-*` tokens for anything Material renders itself" convention as
pvopt (`--pv-*`) and turboorders (`--to-*`). `.gg-card`/`.gg-card--mint`/`--blush`/`--sky`
are top-accent-stripe utility classes used across Dashboard/CollectionDetails/ParentView -
follow that pattern (a 4px `border-top` color, not a full background recolor) for any new
card-based page rather than inventing a new visual language.

`ng build` was verified working during scaffolding: succeeds, one non-fatal warning
(initial bundle ~546kB against a 500kB soft budget in `angular.json`) - not addressed yet,
see TODO.

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
   **Still open**: the sample only ever showed incoming-transfer and login-confirmation
   sentences - an actual outgoing transfer's sentence shape (to confirm it's never
   accidentally matched) and whether the trailing reference code (`/OPF/AN/PL11...`) is
   ever a human-typed title vs. always a structured code are both unconfirmed; the "From"
   address for `groszdogrosza.bankstatement.imap.expected-sender` also still needs
   verifying against a real header (only the rendered body was available, not the raw
   `.eml`).
2. **Create a Gmail App Password** for jaros.dabrowski@gmail.com (Google Account → Security
   → 2-Step Verification → App passwords - requires 2FA already enabled) and set
   `groszdogrosza.bankstatement.imap.username`/`app-password` locally (env vars, never
   committed) to test `ImapBankStatementFetchAdapter` against the real inbox.
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
