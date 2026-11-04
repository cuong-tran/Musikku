# CLAUDE_MUSIKKU.md - Musikku Fork Guide for AI Agents

## 🎯 Purpose of this fork

**Musikku** is a personal build of [SimpMusic](https://github.com/maxrave-dev/SimpMusic), kept in `cuong-tran/Musikku`. It has two goals:

1. **A personal build that can sit next to upstream SimpMusic.** It has its own name, icon, brand colour, Android application id, desktop app id and desktop data folder, so both apps can be installed and run on one device or computer at the same time.
2. **A base for contributing upstream.** Bug fixes, new features and improvements are written here and sent to `maxrave-dev/SimpMusic` (and `maxrave-dev/core`) as pull requests.

Both goals lead to one rule: **change as little as possible.** The fork changes the names users can see and the ids that would clash with upstream. Everything else stays as upstream wrote it: Kotlin packages, class names, module names, deep-link schemes and file formats. Any line that differs from upstream can conflict on the next merge, and the more the fork changes, the harder upstream PRs become to cut cleanly.

## 📎 How this file relates to CLAUDE.md

- `CLAUDE.md` is upstream's file, word for word, except for one line at the top that points here. On every merge, take upstream's `CLAUDE.md` and add that line back (see **Conflict rules**).
- **Notes about fork-only changes go in this file, never in `CLAUDE.md`.** The "CLAUDE.md Auto-Update Rule" in `CLAUDE.md` still applies to work meant for upstream, because that note travels with the PR. A change that stays in the fork is recorded here instead: add it to the matching group in **What the fork changes**, with its commit id.
- `CLAUDE.md` describes upstream's behaviour. Where Musikku behaves differently (the app id, the official-build check, the desktop data paths, artifact names), this file is the one that is right for this checkout.

## 🌳 Repositories and branches

| Repo | Remote | URL | Branch the fork uses |
|---|---|---|---|
| superproject | `origin` | `cuong-tran/Musikku` | `musikku-desktop` (the personal build, Android + Desktop) |
| superproject | `upstream` | `maxrave-dev/SimpMusic` | `main` (releases), `dev` (where upstream PRs go) |
| `core/` submodule | `core` | `cuong-tran/simpmusic_core` | `musikku` |
| `core/` submodule | `upstream_core` | `maxrave-dev/core` | `multiplatform` |

- `musikku` is an older branch. It is an ancestor of `musikku-desktop` and has no commits of its own (as of 2026-10-19).
- `.gitmodules` points `core` at `cuong-tran/simpmusic_core`. The superproject's `core` pointer must always name a commit that is on that repo's `musikku` branch and has been pushed there, or a fresh clone cannot check it out.
- Contribution branches (`fix/…`, `feat/…`) start from `upstream/dev` and never carry fork-only commits. See **Contributing upstream**.

## 🔖 Last upstream sync

Update this after every merge, because these commits are the `OLD` side of the sweep in **Merging upstream**.

- **Superproject**: `upstream/main` at `f037043d` (Merge PR #2628, 2026-10-10), merged into `musikku-desktop` by `bb2dc237` (2026-10-16).
- **core**: upstream pins `b9f4ceac` (`upstream_core/multiplatform`), merged into `core/musikku`.
- Not merged yet: `upstream/dev` was 16 commits ahead of `upstream/main` on 2026-10-19.

## 🧾 What the fork changes

Seven groups. Each says what changed and why, what to look for in new upstream code (apply the same change there too), and which fork commits make it up. Commits marked `core:` are in the submodule; a superproject commit that "bumps core" brings them in.

### 1. Android identity and the official-build check

- Application id `app.musikku` (debug: `app.musikku.dev`), so the app installs next to SimpMusic. Launcher shortcuts target it; the vivo build's own shortcuts keep `com.spotify.music`.
- `Config.OFFICIAL_PACKAGE_NAMES` lists both ids. The signing-key half of `SharedViewModel.checkOfficialBuild()` is commented out, because it compares against upstream's F-Droid key and Musikku is signed with the fork's own key. The package-name half stays.
- The update dialog's download button opens `github.com/cuong-tran/Musikku/releases`.
- **Watch for**: a new `targetPackage="com.maxrave.simpmusic"`, any new signature or package check, a new `simpmusic.org/download` link.
- **Commits**: `e2ac4e77`, `2e46b169` (release URL), `bf8f7020`, `6339fbe5` (vivo debug shortcuts, since merged upstream as #2689); core: `ba91562e`.

### 2. The app's name

- Text that names the app says Musikku. Text that names upstream stays SimpMusic: the SimpMusic Lyrics and Charts services, the import file format, the credits, the review, blog and footguns prompts, and the unofficial-build notice.
- This covers `app_name` (shared, plus the Android launcher label and `Musikku Dev` for debug) and about 20 string keys in every locale. Turkish, Azerbaijani, Finnish and Korean inflect the name, so those were fixed by hand. It also covers hardcoded text: the share cards, the listening receipt, saved-image and log-export file names, the notification channel name (its id stays, so users keep their settings), the auto-backup file names and folder, and the fastlane title and tagline. Changelogs are never rewritten.
- **Watch for**: new strings or literals that name the app. Upstream manages translations on Crowdin and the fork edits them anyway, so every Crowdin sync can bring `SimpMusic` back.
- **Commits**: `33038f4e`, `b0d66d20`, `b1f11b29`, `2a7639df`, `d1b14e83`, `c04b8003`; core: `3410f196`.

### 3. Brand: icon and colour

- Every icon asset (launcher, in-app logo, desktop, AppImage, DMG background, store images) is regenerated from the Musikku artwork by `scripts/icons/`, whose `README.md` holds the pipeline and its traps. Two things outside it: the three `mono.xml` copies (one lives in `core`) and the two `monochrome.xml` copies must stay byte-identical, and the adaptive launcher background is now a `@mipmap` raster.
- The seed colour is the leaf red `#D13433` instead of `#8ECAE6`. This covers `seed`, the default custom colour and the preset list (the old blue is kept second, `#EF5350` is dropped). The check mark on a seed-filled circle is white.
- **Watch for**: new or changed icon assets (keep ours or regenerate them, never take upstream's), a new `8ECAE6`, dark content drawn on `seed`.
- **Commits**: `2e46b169`, `9e8a4983`; core: `1413c0ec`, `2cf9dda2`.

### 4. Desktop as its own app

- **Name**: Musikku everywhere users see it: app bundle, Start menu, `.desktop` entry, AppImage, DMG and installer names, and the DMG volume. `StartupWMClass` appears in three places (`Main.kt`, `conveyor.conf`, the AppImage `.desktop`), which must agree.
- **Identity**: `app.musikku` (`DESKTOP_APP_ID`) is used for:
  - the single-instance lock: `nucleus.app.id`, which must be set before `SingleInstanceManager` loads
  - the user agent
  - the Linux media controls (MPRIS)
  - Conveyor's `rdns-name`, the macOS `bundleID` and `CFBundleURLName`

  The pending deep-link file is `musikku_pending_deeplink.txt`. Without these, a running SimpMusic holds Musikku's lock so Musikku quits at launch, and macOS treats the two as one app.
- **Data**: `~/.musikku` (`DESKTOP_DATA_DIR`) holds settings, database, downloads, caches, covers, backups and logs. On first launch `LegacyDesktopDataImport` copies SimpMusic's `~/.simpmusic` in, without its logs. It runs before Koin and before file logging, and asks what to do if it fails. `MUSIC_DATABASE_VERSION` lets it refuse a database newer than this build.
- **Kept**: `app.fsname = simpmusic` and everything named after it (`bin/simpmusic`, the mac zips, the MSIX identity, `install.bat`), the `simpmusic://` and `wordbyword://` schemes, and the `.desktop` filename.
- **Watch for**: a new `".simpmusic"` path, temp file or lock named `simpmusic_…`; a Room version bump (set `MUSIC_DATABASE_VERSION`); anything new before `startKoin` that writes to the data folder; new uses of the app id.
- **Commits**: `b0d66d20`, `170f4a9e`, `8a197c8f`, `366d6f53` (log path); core: `a8232991`, `f490a596`, `ecd1f63d`.

### 5. Upstream's launch banners

- The remote config is still upstream's, because it carries the TIDAL credentials, but its `promos` block is always stored as `"[]"`, so SimpMusic's banners never show.
- **Watch for**: new remote fields that put upstream's own content on screen.
- **Commits**: core: `ffd4dd19` (brought in by `366d6f53`).

### 6. Builds, CI and Sentry

- Every workflow signs with `BASE_64_SIGNING_KEY`; the fork has no `SIGNING_KEY` secret. Each signed APK is uploaded on its own as `Musikku-<version>-…-<abi>.apk` with `upload-artifact@v7` and `archive: false`, and the job fails unless it finds exactly one APK per ABI. Desktop artifacts and the output of `build_and_sign_apk.sh` are named Musikku too.
- Sentry reports to org `komikku`, project `musikku`, from both `androidApp` and `vivoAndroidApp`.
- The workflows read four secrets the fork doesn't have: `SENTRY_DSN_JVM`, `LASTFM_API_KEY`, `LASTFM_SECRET` and `BLOCK_USERS_TOKEN`. They arrive empty, so desktop CI builds have no Sentry.
- **Watch for**: new jobs or upload steps, `SimpMusic` in artifact names, `SIGNING_KEY`, a new `sentry { }` block.
- **Commits**: `e2ac4e77` (nightly secret), `33038f4e` (Sentry), `653e0593`, `b0d66d20`, `366d6f53` (vivo Sentry), `a7ac52b8`. `73eaa299` was merged upstream as #2575 and has left the fork's diff.

### 7. Repository wiring

- `.gitmodules` points `core` at `cuong-tran/simpmusic_core` (branch `musikku`). A core change is committed there first, then the superproject bumps its pointer. `CLAUDE.md` is upstream's file plus one pointer line; everything else about Musikku lives in this file.
- **Commits**: `2fde1053`.

### Kept as upstream

- **Identifiers**: Kotlin packages, class names, Android `namespace`, intent actions, Gradle modules, DataStore keys, the database name and the notification channel id.
- **Not decided yet**: the update check (asks `maxrave-dev/SimpMusic` for its latest release), the Discord "Visit SimpMusic" button and the Credits screen links still point at upstream.

## 🔀 Merging upstream

The fork follows upstream by **merging** (`bb2dc237` merged `upstream/main` into `musikku-desktop`), not by rebasing. `musikku-desktop` is pushed to `origin`, so rewriting it would break other clones. Merge `upstream/main` to follow releases; merge `upstream/dev` only when you want unreleased work.

### Steps

```bash
# 0. Clean tree, on the personal branch
git switch musikku-desktop
git fetch upstream
git -C core fetch upstream_core
NEW=upstream/main                                  # or upstream/dev
OLD=f037043d                                       # from "Last upstream sync"
NEW_CORE=$(git ls-tree "$NEW" core | awk '{print $3}')   # the core commit upstream pins
OLD_CORE=b9f4ceac                                  # from "Last upstream sync"

# 1. core first: merge upstream's pinned commit into core/musikku
git -C core switch musikku
git -C core merge "$NEW_CORE"                      # resolve with the conflict rules below

# 2. superproject
git merge "$NEW"                                   # resolve with the conflict rules below
git -C core checkout musikku && git add core       # point at the merged core, not upstream's pin
git commit                                         # once every conflict is resolved
```

3. Run the sweep (below) over `OLD..NEW` in the superproject and `OLD_CORE..NEW_CORE` in core. Re-apply the matching group from **What the fork changes** to whatever the sweep finds. Make each fix its own commit after the merge commit, with the reason in the message, as `366d6f53` did ("Fix what the upstream merge left pointing at SimpMusic"). A fix inside `core/` is committed on `core/musikku` first, and then the superproject bumps its pointer.
4. Verify as `CLAUDE.md` says (**Verification After Code Changes**): check the touched files with the JetBrains MCP diagnostics, and do not build unless asked.
5. Update **Last upstream sync** in this file, and add the fix-up commits to their groups.
6. Push `core` (`git -C core push core musikku`) before the superproject, so the pointer resolves for anyone who pulls.

### Conflict rules

| Where | Resolution |
|---|---|
| `CLAUDE.md` | Take upstream's (`git checkout --theirs CLAUDE.md`), then add the pointer line back as the first line. Never merge fork notes into it. |
| `core` (the gitlink) | Use the commit from step 1 (`git -C core checkout musikku && git add core`). Never upstream's pin, which has no Musikku changes. |
| `.gitmodules` | Keep ours (`cuong-tran/simpmusic_core`). |
| Icons and artwork (`*.png`, `*.webp`, `*.icns`, `*.ico`, `mono.xml`, `monochrome.xml`, `ic_launcher*.xml`) | Keep ours (`git checkout --ours <file>`). If upstream changed the icon *structure* (new layer, density or asset), regenerate it with `scripts/icons/` instead of taking theirs. |
| `values*/strings.xml` | Take upstream's, then re-apply group 2, including the grammar fixes. `git show b1f11b29` lists the keys. Check for new keys that name the app. |
| `MusicDatabase.kt` `@Database(version = …)` | Keep `version = MUSIC_DATABASE_VERSION` and set the constant to upstream's new number. |
| `SharedViewModel.checkOfficialBuild` | Take upstream's logic, then comment out (or remove) the signing-key half again. Keep the package-name half. |
| `CommonRepositoryImpl` promos write | Take upstream's surrounding changes, and keep writing `"[]"`. |
| Workflows, `build_and_sign_apk.sh` | Take upstream's new steps, then re-apply group 6: Musikku names, `BASE_64_SIGNING_KEY`, one artifact per file. |
| `androidApp` / `vivoAndroidApp` `build.gradle.kts` | Take upstream's, and keep `applicationId = "app.musikku"` and the Sentry org and project. |
| `desktopApp/build.gradle.kts`, `conveyor.conf` | Take upstream's, and keep display name, `rdns-name`, `bundleID`, `packageName`, AppImage `appName`, `CFBundleURLName` and `StartupWMClass` (group 4). |
| A fork change that upstream has since merged as its own PR (e.g. #2689) | Take upstream's version and drop the fork's copy, keeping only Musikku-specific wording such as comments that name `app.musikku`. |

### Sweep new upstream code

The sweep prints every **added** line in the upstream delta that matches something the fork changes. It skips binaries, changelogs, `README.md`, `CLAUDE.md`, and `import`/`package` lines. Each hit is either a place to re-apply a group or a known keep (see **Kept as upstream**). Over the last real merge (`bdf28a83..f037043d`) it prints 122 lines, and among them are the leftovers that were later fixed by hand: the desktop log path and the vivo Sentry org (`366d6f53`), the vivo signing secret (`a7ac52b8`), `taste_card_subtitle` (`b1f11b29`), and the share-card, receipt and log-export literals (`c04b8003`). It only sees what the merge added. Text that already said SimpMusic before the last sync needs a full search instead (`git grep -n SimpMusic`).

```bash
# usage: sweep <repo dir> <old> <new>
sweep() {
  git -C "$1" diff "$2" "$3" -U0 -- . \
      ':(exclude)*.png' ':(exclude)*.webp' ':(exclude)*.icns' ':(exclude)*.ico' \
      ':(exclude)changelogs/' ':(exclude)fastlane/metadata/android/*/changelogs/' \
      ':(exclude)README.md' ':(exclude)CLAUDE.md' \
    | perl -ne '
        if (m{^\+\+\+ (?:b/)?(.*)}) { $f = $1; next }
        next unless /^\+/;
        next if /^\+\s*(import|package)\s/;
        print "$f: ", substr($_, 0, 200), (length($_) > 200 ? "\n" : "")
          if /SimpMusic(?![A-Za-z])|simpmusic\.org|"com\.maxrave\.simpmusic|"\.simpmusic|simpmusic_|8ECAE6|SIGNING_KEY|upload-artifact|OFFICIAL_PACKAGE_NAMES|FdroidSigningKeys|isOfficialBuild|targetPackage|org\.set\(|^\+\s*version = \d|promos|remote-config/;
      '
}
sweep . "$OLD" "$NEW"
sweep core "$OLD_CORE" "$NEW_CORE"
```

What a hit usually means:

| Pattern | Group |
|---|---|
| `SimpMusic` in a string, literal, file name, artifact or comment | 2 (name), 4 (desktop), 6 (CI). Log messages naming "SimpMusic Lyrics" and similar are upstream services, so they stay. |
| `simpmusic.org` | 1 (download link), 2 (receipt footer). Blog, tools and the official-build notice stay. |
| `"com.maxrave.simpmusic`, `targetPackage` | 1 (app id, shortcuts), 4 (desktop id). `namespace` and `…MainActivity` / `…action.*` stay. |
| `".simpmusic`, `simpmusic_` | 4 (data folder, temp and IPC file names) |
| `8ECAE6` | 3 (brand colour) |
| `SIGNING_KEY`, `upload-artifact` | 6 (CI) |
| `OFFICIAL_PACKAGE_NAMES`, `FdroidSigningKeys`, `isOfficialBuild` | 1 (official-build check) |
| `org.set(` | 6 (Sentry) |
| `version = NN` | 4 (`MUSIC_DATABASE_VERSION`) |
| `promos`, `remote-config` | 5 (launch banners) |

The sweep cannot see new **binary** assets. List them with `git diff --stat "$OLD" "$NEW" -- '*.png' '*.webp' '*.icns' '*.ico' '*/drawable*/*.xml' '*/mipmap*/*'` and check each against group 3.

### Rebasing instead

If a branch is ever rebased onto upstream (a short-lived local branch, never `musikku-desktop` once pushed), the same rules apply commit by commit: resolve each conflict with the table above, then run the sweep once at the end over the whole upstream range.

## 🤝 Contributing upstream

- **Start from upstream, not from the fork.** Superproject: `git switch -c fix/<topic> upstream/dev`, then open the PR against `maxrave-dev/SimpMusic` `dev` (upstream's guideline: "create branch from `dev`"). Core: branch from `upstream_core/multiplatform` and open the PR against `maxrave-dev/core`.
- **Keep fork-only changes out of those branches**: Musikku names, `app.musikku`, `DESKTOP_APP_ID` / `DESKTOP_DATA_DIR` values, the red seed, Musikku icons, the disabled signature check, the promos override, Sentry `komikku`, and CI artifact names and secrets. If a fix was written on `musikku-desktop`, cherry-pick it onto the upstream branch and strip those parts.
- Follow `CLAUDE.md` in the upstream branch, including its Auto-Update Rule. That note belongs to the PR.
- **Once upstream merges a PR, drop the fork's copy on the next merge** (conflict rules, last row). Examples: #2575 (mac-zip artifact path) and #2689 (vivo debug shortcuts) were merged and left the fork's diff. #2690 (vivo APK artifact names) was closed, so that change stays in group 6.
- **Shrink the fork when you can.** A fork change that is a neutral refactor upstream could accept leaves the fork with a one-line value change instead of a scattered edit. These are only suggestions and none of them has been proposed: the `DESKTOP_DATA_DIR` / `DESKTOP_APP_ID` constants with upstream's values (`.simpmusic`, `com.maxrave.simpmusic`), the `MUSIC_DATABASE_VERSION` constant, and share-card or file-name literals read from `app_name` instead of a hardcoded `"SimpMusic"`.

---

**Last updated**: 2026-10-19
