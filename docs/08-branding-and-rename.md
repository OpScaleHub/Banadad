# Branding: دنگ / Dang (formerly "Dong")

- Persian name: **دنگ** (primary, everywhere users see the product).
- Latin spelling: **Dang**. Earlier releases and documents used "Dong".
- Tagline: «حساب‌وکتاب ساختمان، ساده و شفاف».
- Short description: «مدیریت هزینه‌ها و شارژ ساختمان، بدون نیاز به اینترنت».
- Repository: `OpScaleHub/Dang` (renamed from `OpScaleHub/Dong`; GitHub redirects the old
  repository, git and release-download URLs to the new name).
- Landing page: `https://opscalehub.github.io/Dang/`.

## Planned for the next release: show how the name is pronounced (owner, 2026-11-18)

Testers can't tell how to read «دنگ». Without a vowel mark it can be read *dang* (دَنگ, which
means dazed) instead of *dong* (دُنگ, a share), which is the meaning the app is named after.
The fix is to add the *zamma* (ـُ, the small «و» above the first letter) wherever users see
the name: **دُنگ**. This changes only what users see. The repository, package, technical ids
and the Latin spelling "Dang" stay as they are (see the table below).

Where the name changes to «دُنگ»:

- the launcher label: `app_name` in `app/src/main/res/values/strings.xml`
- the in-app name `S.appName` (top bar, welcome screen, shared bill images and exports)
- the Cafe Bazaar listing title in Pishkhan
- the landing page `<title>`, `og:title`, `application-name` and the icon's `aria-label` in
  `site/`

Notes for whoever does it:

- Keep the plain form «دنگ» in the store keywords and in the page text so search still finds
  the app. Typed searches rarely include vowel marks.
- The launcher icon is a picture with no letters. One option is a small «دُ» monogram. Try it
  at launcher size first, because the zamma is tiny and may not be readable there.
- The Latin "Dang" suggests the *a* reading that the zamma rules out. It is kept because of the
  2026-10-18 rename and the owner's "don't change references" decision. Revisit it only if the
  owner asks.
- Check how the zamma renders on an older device and font (the app uses the system font; the landing page and exports name Vazirmatn).

## What was renamed and what deliberately was not

| Kind | Occurrences | Decision |
|---|---|---|
| Branding | README, docs prose, landing page (title, meta, Open Graph, footer, links), social card, workbook title and file name (`workbook/Dang-accounting-prototype.xlsx`), Gradle `rootProject.name`, log tag, default export file names (`dang-backup-…json`, `dang-….xlsx`, `dang-journal.csv`), release asset names (`dang.apk`, `dang-X.Y.Z.apk`), tag messages | Renamed to Dang / دنگ |
| Technical identifiers | `applicationId` and Kotlin package `com.github.opscalehub.dong`; classes `DongApp`, `DongDatabase`, theme `Theme.Dong`; database file `dong.db`; backup format id `"dong-backup"`; FileProvider authority (`<applicationId>.files`); SharedPreferences names; updater cache file names; signing key location `~/.config/dong/`, `DONG_SIGNING`, `dong-release.jks`, alias `dong` | **Kept.** Changing the application id makes Android treat the app as a different one (no update path, data left behind); the database, backup id and preferences are persisted data; the signing key must stay the same for updates to install. None of these is shown to users. |
| Historical | Releases v0.2.0 and v0.3.0 (assets `dong.apk`, `dong-X.Y.Z.apk`), tags, commit messages, results recorded in `docs/05-test-results.md` | Kept for traceability |
| Outside the repository | Repository name, GitHub Pages address | Renamed via the GitHub API; see below |

## The rename and in-app updates

GitHub redirects a renamed repository's web, git and release URLs, but **not** its project
Pages site: after the rename, `https://opscalehub.github.io/Dong/` returns 404 and the site
is served at `https://opscalehub.github.io/Dang/`.

The in-app updater reads `release.json` from Pages. To keep installs updating:

1. v0.3.1 was released **before** the rename, so v0.3.0 installs (which only know the old
   address) could still find it.
2. From v0.3.1 the app tries `…/Dang/release.json` first and `…/Dong/release.json` second
   (`UPDATE_URLS` in `app/build.gradle.kts`).
3. A v0.3.0 install that missed v0.3.1 before the rename has to be updated once by hand from
   the landing page. Installing over it keeps its data, because the signing key is unchanged.

Releases from v0.3.1 on attach `dang.apk` (always the latest, which the landing page links
to) and `dang-X.Y.Z.apk` (pinned, which the updater downloads).
