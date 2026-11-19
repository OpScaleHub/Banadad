# 13 — Project history, decisions and lessons (to resume later)

This is the handover record: what was built, in what order, **why** each choice was made, and
what we learned. It is written so that someone (or a new AI session) who was not there can pick
the project up without the original conversations. Details live in the other docs; this file
links to them.

## 1. What دنگ / Dang is, and for whom

- An offline-first Android app for running a residential building: expenses, monthly charges,
  water meters, parking, payments, debts, monthly bills and reports. Persian only, right-to-left,
  for Iranian buildings (Jalali calendar, Toman display, Rial storage).
- **The user who decides whether it works**: the owner's father, 60+, who will manage his
  building and has never used accounting software. Other real testers: the owner's wife (highly
  educated, non-technical) and his brother. Every UX choice after 0.3 is measured against them.
- Repository: <https://github.com/OpScaleHub/Dang>. Landing page and update feed:
  <https://opscalehub.github.io/Dang/>. Everything in the repo is synthetic data; it is public.

## 2. Timeline

| Date | Version | What and why |
|---|---|---|
| 2026-10-17 | 0.1 (no release) | Requirements, domain model and a prototype accounting workbook first (docs/01–03), then the engine (allocation, water, periods, double-entry ledger) with tests, Room persistence, XLSX/CSV/HTML export, backup/restore with validation, and the Compose UI. Tested on the emulator and on a physical phone (docs/05). |
| 2026-10-17/18 | — | Task-first UI and in-app reports and bill sharing (docs/07), then fixes found on the phone. |
| 2026-10-18 | 0.2.0 | Landing page on GitHub Pages and a local release script (`scripts/release.sh`). |
| 2026-10-18 | 0.3.0 | In-app updater: reads `release.json` from Pages, downloads the pinned APK and installs it. |
| 2026-10-18 | 0.3.1 | Sent-bill tracking, debtors list, month summary image. Released **before** the repo rename so old installs could still find it (docs/08). |
| 2026-10-18 | — | Rename to دنگ / Dang (repo `Dong` → `Dang`); technical identifiers kept (see §4). |
| 2026-10-18 | 0.3.2 | Plain-language wording review for non-technical managers (docs/09). |
| 2026-10-21 | — | Real users gave up after about half an hour: too many categories, too many choices, "where are the formulas?". Research plan and Persian test kit written (docs/10). |
| 2026-10-21 | 0.4.0 | "Simple by default" redesign (docs/11): the wizard asks *how the building charges*, 6 expense types, simple/advanced mode, text size, built-in feedback («نظر») and a SUS survey. Tester message: docs/12. |
| 2026-10-21 | 0.4.1 | Owner's feedback: combined monthly charge (per m² + per person + base), per-unit multipliers («۱ برابر … ۴ برابر», «بر اساس طبقه»). |
| 2026-10-21 | 0.4.2 | Generate, don't add one by one: water meters and parking created from the wizard, a «قبض‌های مشترک» step, meters no longer block months nobody splits by meter, simpler menu and screens. |
| 2026-10-21 | 0.4.3 | Reorder units (▲▼ in the wizard and the Units tab); the water-meter split offered only on the water type. |
| 2026-10-21 | 0.4.4 | The in-app demo is named «نمونه» (easy to find and delete) and holds a year of data: 11 closed months plus the current one, seasonal water, a price rise, one-off repairs, a reserve-funded emergency, and different payment habits (on time, partial, late, ahead, a bounced cheque). |
| 2026-11-13 | 0.4.6 | Opening balances («مانده‌های اول کار»): past unit debt or prepayment at setup, editable until the first close. |
| 2026-11-17 | 0.5.0 | Testers' reports: (1) cancelling a bill took several steps, (2) Iranian buildings prepay the monthly charge by the 10th but Dang charged it at closing, (3) "two buckets" confused managers and a water bill paid from the pot was charged to units again by meter. Now: one-step cancel (payments reversed, closed months credited by a revision), prepaid monthly charge posted on day 1 (due the 10th, months start themselves), and in fixed-charge buildings every bill is covered by the charge unless marked «جدا از واحدها گرفته شود». Simple mode shows one «صندوق ساختمان». DB v5 (`charge_items.inAdvance`). |
| 2026-11-17 | 0.6.0 | One app, fewer knobs: the simple/advanced switch is gone (simple is the app). Removed from the UI: calendar, currency, rounding and occupancy-basis settings, water tariffs, the split-formula editor, fund/cash-account management and fund transfers, audit log, meter serial/rollover, creating charge items by hand. Feedback: no more «نظر» on every screen and no SUS survey; «نظر یا گزارش مشکل» in «بیشتر» emails the support address on the Bazaar listing (share sheet if no email app), and the Bazaar edition adds «امتیاز و نظر در بازار». Engine and data unchanged; old buildings keep their settings. |
| 2026-11-17 | 0.6.1 | Owner's report: the keyboard covered the form being filled. The app is edge-to-edge, so `adjustResize` alone does nothing: screens now use `imePadding()` and form dialogs are keyboard-aware (fields scroll, «ذخیره» stays visible); the keyboard's key moves to the next field. Toman/Rial display is back in settings. The cash-account picker is hidden when there is only one. |
| 2026-11-18 | 0.7.0 | Owner's review of the whole app: backup reminder (home card when there is no backup or the last is 30+ days old, «بعداً» waits a week; a backup offer right after closing a month; last backup date in Settings, per phone), where to pay on bills and reminders (card number or IBAN and holder in Settings, grouped left-to-right; DB v6 `buildings.payAccount/payHolder`), the month's due date on bills, a payment receipt image (offered right after saving a payment, and from each payment in a unit's history), "due date passed, N units owe" card on Home with a reminder button per debtor, undo right after saving a payment or expense, «این صفحه چیست؟» help on the main screens. The name is shown as «دُنگ» everywhere users see it (docs/08). Landing page screenshots and social card retaken.
| 2026-11-18 | 0.8.0 | Testers' first reports: a cancelled advance charge (month deleted and made again, people or rate changed) broke every unit statement and units showed «تسویه»; fixed, with `WorkflowInvariantsTest`. «اصلاح حساب واحد» on the unit screen after the first close, with no default direction. The app now checks its own books (`SelfCheck`: same invariants as the test) on Home and in «نظر یا گزارش مشکل»; a failure shows a card asking for a report. Reports carry the self-check result, counts and recent error codes (no names or amounts) and, only if the manager turns it on, the building's backup file. |
| 2026-11-18 | 0.8.1 | «ویرایش ماه» for an open month (name, dates, due date; the advance charge follows the start date) so managers stop deleting and re-creating months; the delete dialog points to it. A screen computation that fails now says so (snackbar, at most once a minute) instead of leaving a default; an expense's paid/outstanding and the pay-supplier button wait until the paid amount is known (a wrong «outstanding» invited paying twice). `WorkflowInvariantsTest` also covers a part payment to a supplier and editing the open month. |

## 3. Key decisions and the reasons behind them

### Accounting model
- **Double-entry, append-only journal; nothing is edited or deleted.** Mistakes are fixed with
  reversals and adjustments that require a reason. A building's money is a trust matter between
  neighbours, so every number must be explainable and auditable (docs/02).
- **Money is stored as integer Rials, displayed as Toman** (÷10, configurable). No floating
  point anywhere in money; rounding increments are explicit (docs/01).
- **Months are periods that get finalized.** A finalized month is locked; corrections go through
  a recalculation with a revision (docs/02). Consequence that bit us in code: nothing may be
  written into a finalized month's dates (readings, payments, assignments), so generators must
  write data in time order.
- **Opening balances («مانده‌های اول کار») can change only until the first month is closed**
  (owner's rule, 2026-11-13). Many buildings start using Dang mid-way, with cash in the pot and
  units already in debt or paid ahead. The wizard's last step («حساب‌های قبلی») takes the cash and,
  optionally, each unit's past debt or prepayment, entered as an amount plus «بدهکار است /
  پیش‌پرداخت داده», never as a signed number. Until the first close they can be changed from
  «همه تراکنش‌ها» or a unit's page («بدهی/طلب قبلی»): the old entry is reversed on its own date
  and a new one posted the day before the first month. After the first close the past is fixed;
  a unit's balance is corrected with «اصلاح حساب واحد», dated today with a reason. Rewriting
  closed months would break the trust the journal exists for.
- **The fixed monthly charge is prepaid** (owner's report, 0.5.0). Iranian buildings collect it
  at the start of the month, due by the 10th. Charge items flagged `inAdvance` (the wizard's
  «شارژ ماهانه»; migration v5 flags existing ones) are posted as a CHARGE entry on the month's
  first day; closing posts only the rest (bills charged separately, tariff water, changes during
  the month) per unit/category/fund. The advance entry is re-posted (old one reversed on its own
  date) when rates, residents or units change while the month is open. With a fixed charge the
  current month is created on its own, including months missed while the app was closed. Split-
  bills buildings and the workbook demo keep charging at closing.
- **In a fixed-charge building a bill is covered by the charge by default** (0.5.0). The bill form
  asks «از شارژ ماهانه پرداخت می‌شود» / «جدا از واحدها گرفته شود» first; only the second shows the
  split methods. Testers recorded a metered water bill as "paid from the pot" and it was also
  split by meter, because "paid now" (cash) and "who bears it" (policy) were separate questions
  both using the word «صندوق».
- **Cancelling cascades** (0.5.0): «ابطال هزینه» reverses the bill's supplier payments and the
  bill in one transaction; in a closed month it also posts a revision (dated today) that credits
  each unit the share it was charged. Corrections now start from the latest revision's input, so
  a cancelled bill never comes back.
- **One bucket in simple mode** (0.5.0): funds stay in the engine (reserve, demo, advanced), but
  simple mode hides the fund pickers, fund transfers and the reserve tile unless it holds money.
- **Payments are balance-forward** (not matched to single charges); ageing uses oldest-first
  settlement. Simpler for managers; listed as a limitation in docs/06.

### Product and distribution
- **Offline-first, one phone per building.** No server, no account, no data leaves the phone
  except files the user shares. Backups move a building between phones. Sync is a P3 roadmap
  item; a read-only Google Drive view for residents is a deliberately deferred idea (docs/06).
- **Persian only**: the product targets Iranian buildings; the domain layer is locale-free so a
  language could be added, but it is not planned.
- **Direct APK + in-app updater** instead of a store: fastest path to a few testers. One signing
  key, kept outside the repo; losing it means every install has to be uninstalled to update.

### UX (from 0.4)
- **Ask how the building charges, then hide the engine.** Iranian buildings either take a fixed
  monthly amount (bills paid from the pot) or split the actual bills. The wizard asks this in
  plain words and sets up everything else. The engine (formulas, bases, funds) is still there
  (the UI for it was in advanced mode until 0.6.0).
- **Simple mode never changes data**, it only hides controls. Old buildings keep everything.
  (0.6.0: simple mode is now the only mode; the engine keeps everything for old data and the demo.)
- **Feedback must reach the developer from anyone** (0.6.0): the share sheet only worked for
  testers who knew the owner. Feedback is emailed to the public support address of the Bazaar
  listing (`BuildConfig.SUPPORT_EMAIL`), and Bazaar users can rate the app in Bazaar.
- **Undo where people mis-tap, help where they get lost** (0.7.0, from the docs/09 review): a
  payment is followed by a small dialog (send a receipt / undo / close) rather than a snackbar,
  because a receipt is usually wanted anyway and older users miss a snackbar; a new expense gets
  an undo snackbar (it voids the expense with its payment). Undo is always a reversal or a void:
  the journal stays append-only.
- **Remind about backups, don't automate them** (0.7.0): the data is only on the phone and Android
  cloud backup is off on purpose (financial data). The app cannot save to Drive silently without
  an account and permissions, so it asks at the right moments (no backup yet, 30 days, after
  closing a month) and the system file picker offers Google Drive.
- **Name choices by what they do** («مساوی بین همه واحدها»، «به تعداد نفرات»، «از صندوق
  ساختمان») rather than by engine terms («دسته»، «پایه تسهیم»).
- **Generate from the building's structure, keep later edits possible** (owner's principle,
  0.4.2). The wizard already knows floors and units, so meters and parking are created for all;
  each unit's «ویرایش» changes residents and parking from today, keeping history.
- **Meters are water-only.** In practice only water has per-unit internal meters; electricity is
  always each unit's own, and a shared gas meter has no unit sub-meters (it is split by area,
  people or equally).
- **▲▼ buttons instead of drag-and-drop** for ordering: drag needs a long press and steady hands,
  which older users find hard.
- **Feedback is built in** because testers will not write reports on their own. 0.4: «نظر» on
  every screen (screenshot + guided questions via any messenger) and a SUS survey; 0.6.0
  replaced both with «بیشتر ← نظر یا گزارش مشکل» by email (see above).
- **The demo is a real-looking year, not two months** (0.4.4): people judge an app by its
  reports and debts screens, which are empty without history. The name is short so that it is
  obvious which building to delete afterwards. The brief's two-month reference scenario is kept
  unchanged under `addDemoData(history = 0)` because the workbook tests check its exact numbers.

## 4. Things that must never change
- applicationId/package `com.github.opscalehub.dong`, `DongDatabase`, `dong.db`, backup format id
  `"dong-backup"`, FileProvider authority, prefs names, `Theme.Dong`. Renaming any of them breaks
  updates or old backups (docs/08).
- The release key `~/.config/dong/dong-release.jks` (alias `dong`, CN=Dong) and its passwords.
  Back it up off the machine.
- The update feed URLs (`UPDATE_URLS` in `app/build.gradle.kts`): `…/Dang/release.json` first,
  `…/Dong/release.json` second.

## 5. Lessons learned

### About users and UX
- **A builder's own review fixes words, not the model.** The 0.3.2 wording review was useful but
  real users still gave up: the gap was conceptual (engine concepts on screen). Watch real users,
  or at least role-play the specific person, before polishing text (docs/09, docs/10).
- **The owner's quick feedback rounds were the most productive input**: each of 0.4.1–0.4.4 came
  from one concrete observation ("a third unit on floor 1 stays at the bottom", "why is the meter
  option on every expense?"). Ship small, ask, repeat.
- **Defaults matter more than options.** Every choice we removed from the default path (rounding,
  currency, funds, tariffs, serial numbers) made the app easier without losing anything.

### Technical
- **GitHub redirects a renamed repo but not its Pages site.** Release once before renaming, and
  let the updater try both URLs (docs/08).
- **A meter's first reading becomes its baseline** and sets its install date. Writing a later
  reading first (as happened while building the year demo) makes the earlier one look missing,
  which surfaces as `MAIN_METER_REQUIRED`. Always write readings oldest first.
- **Finalized months lock their dates**, so seed data must be generated month by month: readings
  for all months first, then for each month create → expenses → finalize → payments dated in
  the next month.
- **Meter checks must only block what uses meters.** Before 0.4.2, adding a main meter by hand
  blocked every month even when no expense was split by reading.
- **A reversal belongs where the entry it undoes belongs.** Before this fix a unit statement put a
  cancelled advance charge (month deleted, people or rate changed) in no bucket, failed its own
  check, and the screen showed every unit as settled with no history. `WorkflowInvariantsTest`
  now walks a manager's first weeks and checks the books after every step; add new flows to it.
- **Never show a default where a value failed to load.** A missing balance must not read
  «تسویه»; show nothing until the number is known.
- **Let the phone say when the books are wrong.** `SelfCheck` runs the same invariants as
  `WorkflowInvariantsTest` on the device (Home and the feedback screen) and reports codes like
  `unit3:statement:IllegalStateException@Finance.kt:380`; on the 0.7.0 code it flags the testers'
  bug at once. Keep it free of names, labels and amounts so a report can include it as is. Bug
  reports come by email from the app, with the building's backup attached when the manager
  agrees, so a problem can be replayed exactly instead of explained over the phone.
- **Deterministic seed data**: the year demo uses `Random(1405)` so it is the same on every phone
  and in tests; a test loads it on awkward dates (Nowruz, 30 Esfand of a leap year, month ends).

### Working on devices
- **Never run the instrumented test on a real phone**: `EndToEndTest` deletes every building. Use
  `ANDROID_SERIAL=emulator-5554` (AVD `qg2`).
- **Never drive the owner's phone while they are using it.** Before any tap, check
  `adb shell dumpsys window | grep mCurrentFocus` shows `com.github.opscalehub.dong`.
- Wi-Fi adb changes port after the phone locks; rediscover with `adb mdns services`.
- In UI automation, dismiss the keyboard (`input keyevent 4`) before tapping «بعدی».

## 6. How to resume

- **Build and test**: `./gradlew testDebugUnitTest` (163 tests after 0.8.0; count them from
  `app/build/test-results/testDebugUnitTest/*.xml`), then
  `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` (1 end-to-end test).
- **Release**: commit, `git push` (the script requires main in sync with origin), then
  `scripts/release.sh patch --skip-tests` (or `minor`). It builds the signed APK, writes
  `site/release.json`, tags, creates the GitHub Release with `dang.apk`, `dang-X.Y.Z.apk` and
  `dang-bazaar-X.Y.Z.apk`, and Pages redeploys. Upload the Bazaar APK in Pishkhan by hand (Chrome
  extension); the landing page links the Bazaar listing. Installed apps see the update within an hour (or on next start).
- **Where things are**: requirements docs/01, domain docs/02, architecture docs/04, test results
  docs/05, limitations and roadmap docs/06, UX history docs/07, docs/09–11, tester kit docs/10
  (appendix) and docs/12.
- **Next steps** (in order):
  1. Keep the testers on the latest release (docs/12); feedback arrives by email and as Bazaar
     reviews.
  2. Synthesize their feedback as in docs/10 step 4 before building anything new.
  3. Watch whether testers use the 0.7.0 additions (receipts, card number on bills, backup
     reminder); drop what nobody uses.
  4. Roadmap items in docs/06 (receipt photos, Drive read-only view).
