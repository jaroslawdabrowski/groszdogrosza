# Multiple classes - design

Plan for turning Grosz do Grosza from a one-class app into one that serves several classes,
each with its own treasurers and parents. Agreed with the user (the treasurer of SP 34 - 2A)
on 2026-10-19. **Stage 1 is implemented** on the `multi-class` branch (see "Stage 1 as built").

## Decisions

| Question | Decision |
|---|---|
| Who uses it | Classes from one school (SP 34). The user is the only administrator. |
| Automatic bank booking | Only for the existing class (SP 34 - 2A), as today. Other classes book payments by hand. |
| Public page | One unguessable link per class (`/k/<token>`), totals and payment details only. A logged-in member of another class holding the link sees exactly what an anonymous visitor sees. |
| Treasurers per class | One or more, all with full rights in their class. |
| What the admin sees | Management only (classes, treasurers). Money details only where the admin is also a member. |
| New school year | Rename the class (2A -> 3A). Children, piggy banks and history stay. |
| The existing data | Migrated into a class named "SP 34 - 2A". |
| Deleting a class | Administrator only, with a confirmation and an export of the history first. |

## Today's assumptions that change

- "Treasurer" is a global flag on `Parent`; whoever has it can act on everything.
- Most reads are full-table scans, so a missing filter would leak data between classes.
- One payment-info record, one bank mailbox, one public page.
- A `Parent` record belongs to exactly one `Student`.

## Model

- **User**: one login, identified by a verified email.
- **Class**: name, public-link token, payment info (account / BLIK), optional bank mailbox
  (only SP 34 - 2A has one).
- **Membership**: user + class + role (`TREASURER` / `PARENT`). Roles are per class: a user
  can be a treasurer in 2A and a parent in 4B.
- **Guardianship**: user <-> student. A parent with children in two classes has one account.
- **Student, Collection, requirement, contribution, attachment, ledger entry**: each belongs
  to exactly one class.
- **Administrator**: configured on the server (list of emails), never stored in the data, so
  it can't be granted from inside the app.

## Isolation between classes (the security core)

The rule: a single mistake in one place must not be enough to expose another class.

1. **Every class-scoped endpoint carries the class in its path**:
   `/api/classes/{classId}/...`. No class-scoped endpoint works "across everything".
2. **One gatekeeper** (a JAX-RS request filter) runs before every class endpoint. It looks up
   the caller's membership in that class. No membership -> **404** (not 403, which would
   reveal that the class exists). It hands the resource a `ClassContext` (class id, role, the
   caller's own students). Code never takes the class id from a request body.
3. **Every class-owned record carries its class** (a `classId` attribute on students, parents,
   collections and ledger entries), and repository methods can't be called without a class:
   a single record is returned only if its `classId` matches, scans filter on it
   (`platform.persistence.ClassScopedReads` - one place, not each query). A collection or
   student id from another class pasted into your class's URL returns 404. Requirements,
   contributions and attachments hang off a collection and are only reached after the
   collection's class check. A record with no `classId` belongs to no class.

   *Changed from the first plan* (class in the key): an attribute keeps the migration purely
   additive - no record is copied, re-keyed or deleted - so the previous app version keeps
   working on migrated data and rolling back needs no restore. That mattered most to the
   user ("najważniejsze, żebyśmy nie utracili żadnych danych").
4. **Roles are checked within the class**: treasurer actions need `TREASURER` in that class;
   a child's details need a guardianship of that child; a parent still sees only class totals.
5. **Public link**: a long random token (not guessable, not enumerable). Its response is the
   same for everyone, logged in or not. The treasurer can rotate it.
6. **Tests**:
   - an isolation e2e suite with two classes: a user of class A calls every endpoint of
     class B and must get 404 each time;
   - a test that fails the build if a class-scoped endpoint isn't behind the gatekeeper.
7. **Login**: accounts by invitation only, and only verified emails are accepted (identity is
   the email).

## Stages

1. **Foundations, invisible to users**: new model, migration of today's data into
   "SP 34 - 2A", class-scoped endpoints behind the gatekeeper, isolation tests. Production
   behaves as before. Take a DynamoDB backup before migrating. Work on a separate branch;
   deploy only after the full suite passes.
2. **Admin and multiple classes**: admin panel (create class, invite treasurer, delete class
   with export), class switcher, parents with children in several classes, the public link
   per class, treasurers inviting co-treasurers.
3. **Later, if needed**: automatic bank booking for other classes (a shared mailbox with an
   address per class, e.g. `gdogrosza+3b@gmail.com`), a child moving between classes.

## Stage 1 as built

- **Backend.** New `schoolclass` context (class record, payment details, treasurer
  records). `classId` threaded through every use case. `platform.security`:
  `CallerEmail` (verified email only), `ClassMembershipResolver`, `ClassAccessFilter` (the
  gatekeeper), `ClassContext` (request-scoped result), `MeResource` (`GET /api/me`). Endpoints
  moved under `/api/classes/{classId}`; the global ones are listed in
  `ClassEndpointsGuardTest`. Payment details moved from the treasurer's parent record to the
  class (`PUT /api/classes/{id}/payment-info`). Parent records keep their legacy `role` and
  payment fields untouched (rollback safety); `role=TREASURER` is still how bank matching
  recognises the treasurer's own transfers.
- **Paginated reads.** Every scan/query now pages through results; the old adapters read only
  the first 1 MB and would have silently missed records as the table grew.
- **Config** (`application.properties`): `groszdogrosza.public.default-class-id` (what "/"
  shows), `groszdogrosza.bankstatement.class-id` (the class the mailbox books into),
  `groszdogrosza.admin-emails`. All `sp34-2a` / the treasurer's email in prod, `dev-class` /
  `skarbnik@example.com` in dev.
- **Cognito** (`main.tf`): `attributes_require_verification_before_update = ["email"]`, so a
  changed email only takes effect once verified - identity is the verified email.
- **Frontend.** `CurrentUserService` loads `/api/me` and picks the current class (remembered
  per device); services call `/api/class/...`, which `classInterceptor` rewrites to the
  current class. `/k/:token` shows a class's public page. Nothing visible changes yet.
- **Tests.** Unit: `ClassEndpointsGuardTest`, `ClassContextTest`,
  `ClassMembershipResolverTest`, `ClassScopedReadsTest`. e2e: `class-isolation.spec.ts`
  (members of another class get 404 on every endpoint; ids from another class pasted into
  your own URLs get 404 and change nothing; a parent can't do treasurer things or see other
  families; a class's public link answers the same with or without another class's login).
- **Migration** (`scripts/migrate-multiclass.py`): backup to a file first, dry run by
  default, additive conditional writes, then an item-by-item comparison with the backup.
  Rehearsed on a copy of the production data: 413 items, 266 given the class, 2 created,
  0 differences, piggy-bank and contribution totals identical.

### Production rollout (in this order)

1. `scripts/migrate-multiclass.py --table groszdogrosza-prod --compare-and-put` (dry run), then
   with `--apply`. `--compare-and-put` because the deploy user has no `dynamodb:UpdateItem`:
   each record is written back with `classId` only if it's still exactly what was read.
   The running (old) version keeps working.
2. Deploy the new version (`terraform apply` also changes the Cognito setting above).
3. Run the migration with `--apply` again - it gives a class to anything the old version
   wrote between steps 1 and 2, and changes nothing else.
4. Smoke test as treasurer and as a parent.

Rollback at any point: deploy the previous image. No data restore is needed.
