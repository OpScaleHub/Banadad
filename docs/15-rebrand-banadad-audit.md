# 15 — Rebrand to بناداد / Banadad: Phase 0 audit and migration plan

Status (2026-11-24): phases 0–3 done — app (PR #25), compatibility site, repo renamed to `OpScaleHub/Banadad`, Pages on `https://banadad.opscale.ir/`, landing page, release script, README/docs, workbook. Left: release 0.12.0, Bazaar listing. Date: 2026-11-24. Baseline: main at v0.11.1 (versionCode 27).
Every occurrence of Dong / Dang / دنگ / دُنگ was classified; no global replace.

## 1. Counts

| Pattern | Lines | Files | Of which |
|---|---|---|---|
| `dong` (lower) | 2,557 | 104 | ~2,480 are the Kotlin package `com.github.opscalehub.dong` (imports/package lines) |
| `Dong` | 106 | 40 | class names `DongApp`, `DongDatabase`, `DongTheme`, `DongRoot`, `DongNav*`, `Theme.Dong` |
| `Dang` / `dang` | 101 | 33 | user-visible/release names, URLs, file names, log tag |
| `دنگ` / `دُنگ` | 56 | 18 | user-visible name |

## 2. MUST NOT change (technical identity; changing any breaks updates, data or backups)

| Identifier | Where | Why |
|---|---|---|
| applicationId / namespace / package `com.github.opscalehub.dong` | `app/build.gradle.kts`, all sources | Android treats a new id as a different app: no update, data stranded |
| Debug id `com.github.opscalehub.dong.debug` | build.gradle.kts (suffix) | same, for test devices |
| DB file `dong.db`, `DongDatabase`, schema v6 + migrations | `data/db/DongDatabase.kt`, `Migrations.kt`, `app/schemas` | existing data |
| Backup format id `"dong-backup"`, formatVersion 1 | `io/Backup.kt` | old backups must restore; old apps must read new backups |
| FileProvider authority `${applicationId}.files` | Manifest, `ImageDoc`, Settings | derived from the id; unchanged |
| SharedPreferences names (`ui`, updater prefs) and keys | `UiPrefs`, `Updater` | settings, backup reminder, update state |
| Signing keystore `~/.config/dong/dong-release.jks`, alias `dong`, cert DN `CN=Dang, O=OpScaleHub` | `scripts/release.sh`, `DONG_SIGNING` | a new key = no in-place update for anyone. The DN text says "Dang"; it is invisible to users and **must stay** |
| Env var `DONG_SIGNING`, dir `~/.config/dong/` | build.gradle.kts, release.sh | build-machine paths to the key; renaming risks building with a fresh key |
| Updater internal cache names `dong-<code>.apk(.part)`, install session name `dong.apk` | `update/Updater.kt` | private cache, invisible; leave |
| Class names `DongApp`, `DongTheme`, `DongRoot`, `DongNavHost`, style `Theme.Dong` | sources, Manifest, themes.xml | internal; renaming is churn with no user benefit (issue §5 agrees) |
| Tests referencing the above (`DongDatabase.build`, `dong-export.xlsx`, `UpdaterTest` URLs) | `app/src/test`, `androidTest` | internal |

## 3. User-visible — change to بناداد / Banadad

| Item | Where | Current |
|---|---|---|
| Launcher name | `res/values/strings.xml` `app_name` | دُنگ |
| Launcher name (debug) | `src/debug/res/values/strings.xml` | دنگ (debug) — also inconsistent (no zamma) |
| In-app name | `ui/S.kt` `appName` (welcome, top bar) | دُنگ |
| Privacy text | `S.privacyBody` | «دُنگ» حساب کاربری نمی‌خواهد… |
| Update banner | `S.updAvailable` | نسخه تازه «دُنگ» آماده است |
| Bill/receipt/summary image footer | `S.billFooter` | تهیه‌شده با برنامه دُنگ |
| HTML statement footer | `io/Exports.kt:289` | تهیه‌شده با برنامه «دُنگ» |
| Feedback email subject / rate hint | `S.fbSubject`, `S.fbRateHint` | نظر درباره دُنگ / اگر دُنگ به کارتان آمده |
| Restore error | `Settings.kt:56` | این فایل، پشتیبان «دُنگ» نیست |
| Backup file name offered to the user | `Settings.kt:160,177` | `dang-backup-YYYY-MM-DD.json` → `banadad-backup-…` (restore checks content, not name: safe) |
| Excel / CSV export names | `Reports.kt:209-210` | `dang-….xlsx`, `dang-journal.csv` |
| Feedback attachment | `Feedback.kt:169` | `dang-backup-report.json` |
| Launcher icon + adaptive icon | `res/drawable/ic_launcher_*`, mipmaps | "building split into shares" — Phase 1 redesign |
| Logcat tag `"Dang"` (8 places) | AppViewModel, Updater, Common, BillShare | developer-only; rename to `"Banadad"` optional, harmless |

Not user-visible but worth renaming for consistency (no risk): `settings.gradle.kts` `rootProject.name = "Dang"`; comment in `ic_launcher_foreground.xml`.

## 4. Release pipeline and updater (highest risk)

### How updates work today
- Direct-APK builds (not Bazaar) fetch `release.json` from `BuildConfig.UPDATE_URLS`, tried in order:
  `https://opscalehub.github.io/Dang/release.json`, then `…/Dong/release.json`.
  Every direct install since 0.3.1 has exactly these two built in.
- `release.json` gives `versionCode`, `sha256`, `apk`, `apkPinned`, `minSdk`; the app downloads
  `apkPinned` (else `apk`), checks SHA-256 and that the APK's versionCode is the advertised one,
  then installs. `HttpURLConnection` follows https→https redirects.
- The Bazaar build has the updater off (`UPDATES_ENABLED=false`); Bazaar users update via Bazaar only.

### Live state (checked 2026-11-24)
| URL | Result |
|---|---|
| `opscalehub.github.io/Dang/release.json` | 200 — **the only working update endpoint for every direct install** |
| `opscalehub.github.io/Dong/release.json` | 404 (dead since the 0.3.x rename; fallback is useless) |
| `opscalehub.github.io/` (org site) | 404 — no `OpScaleHub/opscalehub.github.io` repo |
| `github.com/OpScaleHub/Dong` | redirects to `/Dang` (GitHub keeps repo redirects) |
| `…/Dang/releases/latest/download/dang.apk` | 200 |
| Pages config for Dang | workflow build, **no custom domain**, HTTPS enforced |
| `banadad.opscale.ir` | CNAME → opscalehub.github.io (added by the owner 2026-11-24) |
| `opscale.ir` | A → 185.199.109.153 (GitHub Pages), answers 404; NS = ArvanCloud |

### Release names hard-coded
`scripts/release.sh`: `REPO=OpScaleHub/Dang`, assets `dang.apk`, `dang-X.Y.Z.apk`, `dang-bazaar-X.Y.Z.apk`,
tag message "Dang", release title «دنگ X.Y.Z», notes heading, `release.json` URLs (`…/Dang/releases/…`),
final echo of the Pages URL. `site/release.json` (generated) holds the same URLs.
Landing page download button: `…/Dang/releases/latest/download/dang.apk`.

### Key facts for the migration
1. **Custom domain on the existing Pages site is safe for old installs.** When a Pages project site gets a
   custom domain, GitHub answers `opscalehub.github.io/Dang/*` with a 301 to `https://<domain>/*`;
   old apps follow it (https→https). To be **verified with curl and with an old APK** before relying on it.
2. **Renaming the repo breaks the only update endpoint old installs know.** After `Dang → Banadad`,
   `opscalehub.github.io/Dang/` is no longer a project site (GitHub does not redirect Pages paths;
   our own `/Dong/` 404 proves it). Every direct install older than the first Banadad release would
   stop seeing updates. There is no telemetry, so we cannot measure when stragglers are gone.
3. **A shim keeps old installs alive after a rename**, without breaking GitHub's repo redirect: an org
   Pages site (`OpScaleHub/opscalehub.github.io`, no custom domain) serving `/Dang/release.json`
   (copy of the current release.json, or a page that is the current one). Pages serves an unclaimed
   path from the org site. Must be verified before the rename. Creating a *new repo named `Dang`*
   instead would kill the `github.com/OpScaleHub/Dang → Banadad` redirect (release links, clones,
   Bazaar listing link) — **don't**.
4. Release asset URLs inside `release.json` are read fresh each time, so they can move to
   `…/Banadad/releases/…` with the new metadata; `github.com/OpScaleHub/Dang/…` asset links keep
   working through GitHub's repo redirect (verify after the rename).
5. `dang.apk` is linked from outside the app (old README copies, chats). Keep attaching a `dang.apk`
   alias to releases for a compatibility period, alongside `banadad.apk`.

## 5. Store, site, docs

- **Cafe Bazaar** listing `com.github.opscalehub.dong`: title «دنگ مدیریت شارژ ساختمان», icon, screenshots,
  descriptions. Same package → edit the listing in place (Pishkhan «اطلاعات» / «تصاویر»), no new listing.
  The 0.11.1 review is still queued; do the store rebrand with the first Banadad build.
- **Landing page** `site/index.html`: title, meta description, `application-name`, OG/Twitter tags
  (title, url, image, alt), canonical `opscalehub.github.io/Dang/`, header/footer brand, body copy
  («دُنگ» ×4), GitHub and download links; `site/assets/icon.svg` (aria-label), `og.png`, screenshots
  (show the old name in the app bar).
- **README**: 13 lines (title, meaning of «دنگ», links, release asset names, signing paths — the
  signing paths stay).
- **docs**: 10 files. Historical facts stay as history (docs/08 explains Dong→Dang; docs/13 timeline);
  current-state wording and links get the new name; docs/08 gains a Banadad section.
- **workbook** `workbook/Dang-accounting-prototype.xlsx` (referenced by `WorkbookScenarioTest` comment
  and docs): rename optional; if renamed, update the references in the same change.

## 6. Repo hygiene found during the audit (unrelated to the brand)
Ten JVM crash/replay logs are tracked in git: `app/hs_err_pid*.log`, `app/replay_pid*.log`
(local paths, ~160 KB). Should be removed and `*.log` / `hs_err_pid*` added to `.gitignore`.

## 7. Name availability (2026-11-24)

| Check | Result |
|---|---|
| Web search «بناداد» | no product or company found; nearest are the dictionary word «بنداد» (foundation) and unrelated «بنادک» |
| Web search "Banadad" | no product found; nearest "Banhada" (Korean shopping app, iOS) |
| Cafe Bazaar / Myket search | **could not be verified automatically** (client-rendered / 404 to fetchers) — needs a manual search in both stores |
| banadad.com, banadad.app | **not registered** (RDAP: not found) |
| banadad.ir | not checkable here (no public RDAP) — check at nic.ir |
| Iranian trademark (ثبت علائم تجاری) | **not checked** — needs a search at the IP office (iripo.ssaa.ir); I cannot claim clearance |
| Social handles | not checked |

No conflict found so far; the manual checks above are still required before the name goes public.

## 8. Proposed migration plan

Order matters: the domain first, the repo rename last (or never).

**Phase 1 — identity (no code risk).** Final spelling بناداد / Banadad, tagline, icon concepts
(geometric: balance/shared parts), tested at 48/72/96/144/192 px and as an adaptive icon; colours.

**Phase 2 — app strings and assets (one PR, one release, e.g. 0.12.0).**
- Only the items in §3; nothing in §2.
- Updater URLs become, in order: `https://banadad.opscale.ir/release.json`,
  `https://opscalehub.github.io/Dang/release.json` (drop the dead `/Dong/`).
- Tests: unit + end-to-end; **install-over-existing** from the oldest buildable tag (0.3.x), 0.10.3 and
  0.11.1 on the emulator: data intact, backups from each restore, update found and installed.

**Phase 3 — domain and site (before or with 0.12.0).**
1. DNS at ArvanCloud: `banadad CNAME opscalehub.github.io.` (owner does this).
2. Pages custom domain `banadad.opscale.ir` + HTTPS on the **current** Dang repo (add `site/CNAME`).
3. Verify: `opscalehub.github.io/Dang/release.json` 301 → `https://banadad.opscale.ir/release.json`;
   an installed 0.11.1 (knows only the github.io URL) finds and installs the new version.
4. Landing page rebrand (§5), canonical = `https://banadad.opscale.ir/`.
5. `release.sh`: new asset name `banadad.apk` (+ `banadad-X.Y.Z.apk`, `banadad-bazaar-…`), keep a
   `dang.apk` alias for one compatibility period; titles/notes «بناداد».

**Phase 4 — store and repo.**
1. Cafe Bazaar listing in place: title «بناداد | مدیریت ساختمان», short/full description, icon,
   screenshots, release notes; submit 0.12.0.
2. Repo rename `Dang → Banadad` **only after**: (a) the org-site shim serving `/Dang/release.json` is
   live and verified from an old install, (b) GitHub release/asset redirects verified, (c) remotes,
   README, site links, `REPO=` in release.sh updated in the same change. Alternative with zero risk:
   keep the repo name `Dang` and only show "Banadad" everywhere else — the domain carries the brand.

**Rollback:** every phase is reversible except a released APK; strings/assets revert with a release,
DNS/custom domain can be removed (github.io answers again), the repo rename can be reverted by
renaming back before anyone creates a repo with the old name.

## 9. Decisions needed from the owner
1. Run the manual checks (Bazaar/Myket search, nic.ir, trademark office) — or accept the risk.
2. DNS record `banadad.opscale.ir` at ArvanCloud (I can't touch DNS).
3. Repo rename: do it last with the org-site shim, or keep `OpScaleHub/Dang` (recommended unless the
   repo name matters publicly).
4. Icon direction (Phase 1) before any build.

## 10. What was actually done (phase 3)

- A custom domain on the *old* repo's Pages made `opscalehub.github.io/Dang/release.json` answer
  `301 http://banadad.opscale.ir/…` (http, not https) until HTTPS was enforced; Android's
  HttpURLConnection does not follow https→http, so installed copies' update checks failed. Rolled
  back; the CDN kept the cached redirect until a deploy succeeded (the Pages workflow first had to be
  fixed: upload and deploy now run as separate jobs).
- So the order became: org site `OpScaleHub/opscalehub.github.io` serving `Dang/release.json` and a
  `Dang/index.html` that points to the new site (no redirect) → repo renamed to `OpScaleHub/Banadad` →
  custom domain + HTTPS on Banadad. Verified: `https://opscalehub.github.io/Dang/release.json` 200
  (org copy), `https://banadad.opscale.ir/release.json` 200, `github.com/OpScaleHub/Dang/…` 301 to
  `…/Banadad/…` over https.
- `scripts/release.sh` publishes `banadad.apk`, `banadad-X.Y.Z.apk`, `banadad-bazaar-X.Y.Z.apk` and a
  `dang.apk` copy, and pushes `site/release.json` to the org repo's `Dang/release.json` on every release.
- Never create a repository named `Dang` (breaks GitHub's redirects) and never delete the org repo
  (older installs check there).
