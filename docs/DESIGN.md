# Design system and UI rules

How Grosz do Grosza looks and why. Part 1 holds the rules that carry over to the sibling
projects (same stack: Angular 20, standalone components and signals). Part 2 is specific to
this app. The code is the source of truth for exact values: `src/main/webui/src/styles.scss`
holds the tokens and the shared classes.

History: the app went through three palettes on Angular Material ("Ocean & Amber" was the
last). On 2026-10-01 the whole UI was rebuilt from scratch, because it still looked like a
Material demo. Three directions were mocked up (`docs/brand/kierunki-ABC.png`):
A "Zeszyt w kratkę", B "Skarbonka" and C "Tablica". The user picked **A**, and asked for
animations on top of it.

---

## Part 1: portable rules

### 1. Material only for icons

- The only Material piece left is `<mat-icon>`, with the **Material Symbols Rounded** font set
  as the default (`MatIconRegistry.setDefaultFontSetClass` in `app.config.ts`). There is no
  `mat.theme`, no ripple, no `mat-card`, `mat-table`, `mat-form-field`, `mat-menu`,
  `mat-chip` or `mat-checkbox`.
- Everything else is native HTML styled in `styles.scss`:
  - **pages:** `.page`, `.page-head` (with `.page-head-row` for a title plus an action) and
    `.section-head`;
  - **surfaces:** `.sheet` (the hero surface) and `.panel` / `.panel--flush` (quiet
    containers);
  - **lists:** `.list` > `.list-row` > `.list-icon` / `.avatar` + `.list-main`
    (`.list-title`, `.list-sub`) + `.list-end` (`.pill`, `.list-amount`, `.list-chevron`);
  - **buttons:** `.btn--primary/secondary/text/danger` and `.icon-btn`;
  - **forms:** `.field` (label above) + `.input`, `.input-group .suffix`, `.field-row`,
    `.check-list` > `.check` (a restyled native checkbox);
  - **controls and messages:** `.segmented` with a sliding `.segmented-indicator` (filters
    and tabs), and `.notice--ok/--error`.
- Dropping Material cut the initial bundle from ~546 kB to ~420 kB.

### 2. Tokens: every value comes from a scale

| Scale | Values |
|---|---|
| Spacing | 4 8 12 16 24 32 48 64 96 (`--space-1..9`) |
| Type | 12 14 16 18 20 24 30 36 48 (`--text-xs..5xl`) |
| Radius | 12 fields, 18 panels, 22 sheets, full for pills and buttons |
| Shadow | `--shadow-1..3`, tinted with the ink hue (never a grey `rgba(0,0,0,.1)`) |
| Motion | `--ease-out`, `--ease-in-out`, `--dur-press/fast/base` 140/180/240 ms |

- **Colour roles, not swatches.** Components use roles only: `--ink`, `--ink-2`, `--ink-3`
  (all ≥ 4.5:1 on white), `--surface`, `--surface-well`, `--border` (decorative),
  `--border-strong` (≥ 3:1, the only edge an input has), plus one accent ramp
  (`--ink-blue-50..700`) and three meanings: `--owe` (still to pay), `--ok` (paid) and
  `--gold` (piggy bank). Each meaning has a `-tint` for backgrounds.
- **Red is not the "to pay" colour.** A first pass used red for it, and a class list with 10
  of 17 rows in red read like a page of errors. "Still to pay" is a normal state, so it is
  a warm orange (`--owe` `#a8430a` on `#ffeedd`, 5.3:1). Red (`--danger`) is kept for
  destructive actions and real problems (a shortfall), so it keeps its alarm value.
- **Destructive actions in a list are tertiary.** They are quiet grey icons
  (`.icon-btn--quiet`) that turn red only on a real hover. The red, primary version lives
  in the confirmation dialog.
- **Colour is never the only signal.** A status pill always has text ("Do zapłaty"), and a
  roster dot is filled, half-filled, empty or dashed, so the dots read in black and white too.

### 3. Typography

- **Two faces, clearly different:**
  - **Bricolage Grotesque** 600–800 for headings and big amounts;
  - **Figtree** 400–700 for everything else.
- Numbers use `.num` (`tabular-nums`). Amounts go through the `money` pipe (Polish format; no
  decimals when the amount is whole). A big amount is written as `{{ x | money }}<small>zł</small>`,
  so the currency is small next to the figure.
- Sentence case everywhere, with no ALL CAPS labels. An eyebrow line above an `h1` appears
  only when it says something ("Cześć, Anna", "Skarbonka").

### 4. Layout and navigation

- **Phone first.** Every screen is checked at 390 px and 1280 px.
- **Phone (< 900 px), logged in:** a floating bottom tab bar with Start, Zbiórki, Skarbonka
  (if the account is linked to a child), Klasa (treasurer only) and Więcej. "Więcej"
  (`/wiecej`) holds the ledger, "Jak to działa", the language and logout.
- **Desktop (≥ 900 px), logged in:** a sidebar with the same destinations, the language switch
  and logout.
- **Logged out:** a slim top bar with the logo, the language switch and "Zaloguj się".
- **Content column:** `.page` is at most 760 px wide.

### 5. Forms

- Labels sit above the fields (`<label for>` + `id`), and fields are ≥ 16 px so iOS never
  zooms in.
- There is one filled primary button per view. Destructive actions are `btn--danger` or a red
  `icon-btn` and always go through a `confirm()`.
- Rows with several rare actions (the treasurer's students and parents) don't get a menu.
  Their actions unfold inline under the row (`.unfold`).

### 6. Motion

The rules come from Emil Kowalski's skills (`emil-design-eng`, `find-animation-opportunities`,
`animate`). Before animating anything, ask two questions. **How often is this seen?** Things
seen tens of times a day only get press feedback. **What is the motion for?** It has to give
feedback, show a state, keep things spatially consistent, prevent a jump, or (only for rare
moments) delight.

| Where | Motion | Why |
|---|---|---|
| Buttons, rows, tabs, segments, checkboxes | `:active` scale 0.97 (rows 0.985, icon buttons 0.92), 140 ms | feedback on press |
| Roster dots (Start, collection) | dots fill one after another, 35 ms apart, on first render | **the one orchestrated moment**: "who has paid" at a glance |
| Segmented control | the white indicator slides to the chosen option, 240 ms | state |
| "Jak zapłacić", a row's actions | height unfolds via `grid-template-rows: 0fr → 1fr` | state, without measuring heights |
| Inline forms, notices | `.gg-enter`: fade + 4 px drop, 180 ms | it appeared because you did something |
| Student filter (Wszyscy / Do zapłaty / Zapłacone) | `animate.enter="row-in"` / `animate.leave="row-out"`, 180 / 120 ms | rows don't teleport |
| Pills and avatars changing status | colour transition, 240 ms | state |
| Phone tab bar | the active icon sits on a soft pill, which grows in from 0.6× width, 240 ms | state shown by shape |
| Copy account / BLIK | the check icon pops in from 0.5, 200 ms | feedback |
| Settlement result (rare) | the panel lands from 0.97, the 🎉 icon pops with a light overshoot, refund rows follow 50 ms apart | the one place the app spends delight |
| Route change | View Transitions cross-fade; the tab bar and sidebar are their own groups, so they stay still | native feel |
| App start (once) | the tab bar rises, sidebar links cascade 40 ms apart | the shell renders once |

- Never use `ease-in` or `transition: all`, and never start from `scale(0)`.
- Animate `transform` and `opacity` only. The exception is `.unfold`, which animates
  grid rows.
- `:hover` styles live only inside `@media (hover: hover) and (pointer: fine)`.
- `prefers-reduced-motion` cuts every animation and transition to ~0.
- **Rejected on purpose:** counting up amounts (data people read, seen daily), staggered
  entrances on lists and the ledger (functional, frequent), hold-to-confirm on deletes (a
  confirm dialog already exists), extra motion on navigation (used 100+ times a day).

### 7. Keeping e2e tests stable through a redesign

- The page objects (`e2e/pages.ts`) locate elements by `data-testid`, `data-*` state
  attributes (`data-status`, `data-name`, `data-title`) and accessible roles or labels, never
  by CSS classes.
- The one exception is `.print-table` / `.total-collected`, which the print test checks on
  purpose.
- Clean the local DB before running the suite. Specs that count a whole class ("1 / 1
  students") fail if preview seed data is still there.

---

## Part 2: Grosz do Grosza specifics

### Direction A, "Zeszyt w kratkę"

A school exercise book. The page is squared paper: `--paper` `#f6f8fc` with an 18 px grid in
`#e4e9f4`, drawn by a `background-image` on `body`. Content sits on white `.sheet`s that carry
a red notebook margin line (`::before`). Fountain-pen blue `#2445c4` is the only action
colour. Orange means "still to pay", green means "paid", gold means "piggy bank" (red is
only for destructive actions and shortfalls). Ink text is `#1c2a5c`.

### The one bold element

On Start, **"Jak zapłacić" is drawn like a bank card**: an ink-blue gradient, white figures,
and the account number, BLIK number and transfer title as translucent wells with copy
buttons. Everything else on the page stays white and quiet. Every text colour on the card is
a light tint of its own hue (never grey on colour), and all of them are ≥ 5:1. It is shown
always, even when no collection is active (paying ahead is the point), and it sits at
the top of the page, which is the user's choice.

**The card encourages paying ahead, not paying exactly what's owed.** This is the user's
explicit product rule: the point of the piggy bank is one bigger transfer that later
collections draw from, instead of a transfer per collection. So the card's headline is
"Wpłać z góry, np. 100 zł" (`suggestedPrepay`: 100 zł, or the next round 50 above what is
due). What is currently due appears only as a small secondary line ("Na bieżące zbiórki
brakuje 27 zł"). For the same reason a collection's sheet leads with the *class's* progress
(collected / of, roster dots), and the viewer's own child is one quiet line under it, not a
big "you owe" number. For a logged-in parent the card also shows their child's surname as
the transfer title (bank matching relies on it).

### Screens

| Route | What it is |
|---|---|
| `/` Start | The "Jak zapłacić" card first (see above), then one sheet per active collection: how much the class has collected, the roster dots, one line for your own child ("Kalina: brakuje 27 zł" / paid / not taking part) and a "Szczegóły" link. Logged out: totals and a login prompt. Logged in: earlier collections and the child's piggy bank. |
| `/dashboard` Zbiórki | Active and finished collections as a list. The pill shows your own child's status. |
| `/collections/:id` | A hero sheet (collected / of, roster dots, your child, join/leave), then, for the treasurer only, the students list with a filter, payments, the settle panel (settling asks for confirmation and restates the cost and the amount collected), the settlement result and attachments. Printing switches to a plain black-and-white table (`.print-only`) that fits one page. |
| `/treasurer` Klasa | Tabs Uczniowie / Nowa zbiórka / Dane do wpłat (`?tab=new` / `?tab=payment`). Shows the piggy bank total, students with their parents, and actions that unfold inline. |
| `/students/:id` | The piggy bank balance, the parents and the activity log. |
| `/ledger` | The treasurer's full activity log, grouped by day. |
| `/wiecej`, `/jak-to-dziala`, `/login` | The phone's "more" page, an explainer, and the login landing page. |

### App icon, favicon, splash

- **App icon** (`public/icons/*`, maskable): the ink-blue gradient (`#2b4fd6 → #1d38a3`) with
  a faint white squared-paper grid, a white piggy bank, and a gold (`#f4b400`) coin. The grid
  is dropped below 128 px, where it would only read as noise. The pig stays inside the
  maskable 80% safe zone.
- **Favicon** (`favicon.svg` / `.ico`): just the ink-blue pig with the gold coin, with no
  background square, so it reads on light and dark browser tabs.
- **Splash** (inline in `index.html`): the squared-paper page with the blue pig, shown until
  Angular renders.
- The PNGs are rendered from one SVG definition with Playwright. If the mark changes, re-render
  every size rather than editing single PNGs.

### Reviewing the design with screenshots

`src/main/webui/scripts/` has the helpers used for every design pass. All of them need
`./mvnw quarkus:dev` running and are run from `src/main/webui`:
- `design-seed.mjs` fills an **empty** local DB with a realistic class (17 students, three
  collections, two parent logins) and saves the logins;
- `design-shots.mjs <tag> [screens] [phone,desktop]` screenshots every screen into
  `target/design/<tag>/`, at full-page height or (with `FULL=0`) the phone's viewport only;
- `render-icons.mjs` re-renders all the icons.

Don't run the e2e suite against seeded data: specs that count the class would fail.

### Ledger rendering

`shared/ledger-format.ts` holds the icon and tone for each `LedgerEventType`: money in is
green, the piggy bank is gold, everything else is blue. Both the student view and the global
ledger use it.
