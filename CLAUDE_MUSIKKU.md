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
| superproject | `origin` | `cuong-tran/Musikku` | `musikku` (the personal build, Android + Desktop) |
| superproject | `upstream` | `maxrave-dev/SimpMusic` | `main` (releases), `dev` (where upstream PRs go) |
| `core/` submodule | `origin` | `cuong-tran/simpmusic_core` | `musikku` |
| `core/` submodule | `upstream_core` | `maxrave-dev/core` | `multiplatform` |

- A fresh clone has only the two `origin` remotes (`git submodule update --init core` names the submodule's after `.gitmodules`). Add the upstream ones before a merge or a sync: `git remote add upstream https://github.com/maxrave-dev/SimpMusic` and `git -C core remote add upstream_core https://github.com/maxrave-dev/core`.
- A sync that rebases instead of merging (the `musikku-upstream-sync` skill in `.claude/skills/`) rewrites `musikku` in BOTH repos, pushed core first with `--force-with-lease`. It works on `musikku` only. Before rewriting, it offers to tag the tip in both repos (default no; the owner names the tag, and the suggestion is the app's version, `x.x.x`, e.g. `2.5.0`). It doesn't ask when the tip is already tagged. An `x.x.x` / `x.x.x.x` tag on the app also drafts a GitHub release (group 6).
- `.gitmodules` points `core` at `cuong-tran/simpmusic_core`. The superproject's `core` pointer must always name a commit that is on that repo's `musikku` branch and has been pushed there, or a fresh clone cannot check it out.
- Contribution branches (`fix/…`, `feat/…`) start from `upstream/dev` and never carry fork-only commits. See **Contributing upstream**.

## 🔖 Last upstream sync

Update this after every merge, because these commits are the `OLD` side of the sweep in **Merging upstream**.

- **Superproject**: `upstream/main` at `140673a` (Merge PR #2879, 2026-11-14). On 2026-11-20 the fork's commits were rebased onto it, one by one and not folded, so `musikku` was rewritten again and every id below changed. Upstream's changes that the fork had to follow went into the commit they belong to. Before that: `b5cff20`, rebased and folded on 2026-11-04; `f037043d`, merged.
- **core**: upstream pins `8413a25`, the tip of `upstream_core/multiplatform` (Merge PR #59, library sync). Core was rebased onto it the same way, and every superproject commit points at its tip, `f15ee25`, from the first commit on. The old "bump core" commit for the download fix carried ahead of upstream became empty that way and was dropped; the fix itself is core `f15ee25` (see **Contributing upstream**). Before that: `344d614` (pins `1830a60`), rebased and folded; `b9f4ceac`, merged.

## 🧾 What the fork changes

Nine groups. Each says what changed and why, what to look for in new upstream code (apply the same change there too), and which fork commits make it up. Commits marked `core:` are in the submodule; a superproject commit that "bumps core" brings them in.

### 1. Android identity and the official-build check

- Application id `app.musikku` (debug: `app.musikku.dev`), so the app installs next to SimpMusic. Launcher shortcuts target it; the vivo build's own shortcuts keep `com.spotify.music`.
- `Config.OFFICIAL_PACKAGE_NAMES` lists Musikku's two ids and the vivo build's `com.spotify.music`, and no longer upstream's `com.maxrave.simpmusic` ids, which a renamed Musikku APK could pass as. The signing-key half of `SharedViewModel.checkOfficialBuild()` is commented out, because it compares against upstream's F-Droid key and Musikku is signed with the fork's own key. The package-name half stays.
- The update dialog offers Musikku's own releases (group 9). A build that cannot update itself still opens `github.com/cuong-tran/Musikku/releases` from its Download button.
- **Watch for**: a new `targetPackage="com.maxrave.simpmusic"`, any new signature or package check, a new `simpmusic.org/download` link.
- **Commits**: `1afd490`; core: `f820163`. The vivo debug shortcuts were merged upstream as #2689 and have left the fork's diff.

### 2. The app's name

- Text that names the app says Musikku. Text that names upstream stays SimpMusic: the SimpMusic Lyrics and Charts services, the import file format, the credits and the unofficial-build notice. (The review, blog and footguns prompts named it too; group 8 removed them.)
- This covers `app_name` (shared, plus the Android launcher label and `Musikku Dev` for debug) and about 20 string keys in every locale, plus ten newer keys that so far exist only in English: library sync's and the Discord and browser sign-in steps. Their translations will name SimpMusic when Crowdin brings them, so they need this group then. Turkish, Azerbaijani, Finnish and Korean inflect the name, so those were fixed by hand. It also covers hardcoded text: the share cards, the listening receipt, saved-image and log-export file names, the notification channel name (its id stays, so users keep their settings; core renames the existing channel on start, since Media3 only names it when creating it), the auto-backup file names and folder, and the fastlane title and tagline. Changelogs are never rewritten.
- **Watch for**: new strings or literals that name the app. Upstream manages translations on Crowdin and the fork edits them anyway, so every Crowdin sync can bring `SimpMusic` back.
- **Commits**: `6d68bf5`; core: `fd425b5` (the notification channel).

### 3. Brand: icon and colour

- Every icon asset (launcher, in-app logo, desktop, the macOS `rounded_app_icon`, DMG background, store images) is regenerated from the Musikku artwork by `scripts/icons/`, whose `README.md` holds the pipeline and its traps. Two things outside it: the three `mono.xml` copies (one lives in `core`) and the two `monochrome.xml` copies must stay byte-identical, and the adaptive launcher background is now a `@mipmap` raster.
- The seed colour is the leaf red `#D13433` instead of `#8ECAE6`. This covers `seed`, the default custom colour and the custom-colour presets: upstream's are other music apps' brand colours, and `"FFD13433" to "Musikku"` leads them, three across instead of four so the nine fill three rows. Text and icons drawn on a seed fill are white: the check mark on a seed-filled circle, the Analytics date-range bar label, and the sleep timer's Set button (its old colour while disabled). Login Sync's sheet button is upstream's again: it now fills with the theme's primary and draws `onPrimary`. Dark content on `#D13433` is about 3.5–3.7:1, under 4.5:1 for small text.
- **Watch for**: new or changed icon assets (keep ours or regenerate them, never take upstream's), a new `8ECAE6`, dark content drawn on `seed`.
- The AppImage takes `circle_app_icon.png` (upstream deleted `composeApp/appimage/simpmusic.png` in `7c0a983`). Upstream's round-mask `ic_launcher_round_foreground` is not used: our round launcher icon keeps `ic_launcher_foreground`.
- **Commits**: `c6bbfe8` (icons), `7fb07fd` (colour), `9387232` (white on seed); core: `21cd147`, `952389e`.

### 4. Desktop as its own app

- **Name**: Musikku everywhere users see it: app bundle, Start menu, `.desktop` entry and the DMG volume. The files CI uploads (APKs, AppImage, mac zips, DMGs, the Windows installer) are named `musikku-…`, lowercase (group 6). `StartupWMClass` appears in three places (`Main.kt`, `conveyor.conf`, the AppImage `.desktop`), which must agree.
- **Identity**: `app.musikku` (`DESKTOP_APP_ID`) is used for:
  - the single-instance lock: `nucleus.app.id`, which must be set before `SingleInstanceManager` loads
  - the user agent
  - the Linux menu entry and media controls: the AppImage's AppRun installs `~/.local/share/applications/app.musikku.desktop` with `Icon=musikku` and `musikku.png`, and the MPRIS DesktopEntry is `app.musikku`, which must name that file. Upstream's AppImage installs `com-maxrave-simpmusic-MainKt.desktop` and `simpmusic.png`; sharing those let whichever app ran last own the menu entry and icon. On upgrade AppRun deletes an old `com-maxrave-simpmusic-MainKt.desktop` only when it is Musikku's (`Name=Musikku`, or an `Exec` that runs an AppImage named `*musikku*`), and never touches `simpmusic.png`. Conveyor's own `.desktop` would be `${app.long-fsname}.desktop` (`maxrave-dev-simpmusic.desktop`), not the rdns-name, but `linux.amd64.targets = [ ]`, so it never reaches users.
  - Conveyor's `rdns-name`, the macOS `bundleID` and `CFBundleURLName`

  The pending deep-link file is `musikku_pending_deeplink.txt`. Without these, a running SimpMusic holds Musikku's lock so Musikku quits at launch, and macOS treats the two as one app.
- **Data**: `~/.musikku` (`DESKTOP_DATA_DIR`) holds settings, database, downloads, caches (reverb, waveforms), covers, backups, logs, and library sync's pairing file and database copies. On first launch `LegacyDesktopDataImport` copies SimpMusic's `~/.simpmusic` in, without its logs, its launch banners, downloads still in progress, its library-sync pairing and database copies (with them Musikku would answer SimpMusic's phones as SimpMusic; it starts unpaired, with a sync id of its own), or its Discord OAuth login (Discord spends the refresh token on every refresh, so two apps holding the same one log each other out). It follows links rather than copying them, hard-links downloads (safe only because `Ytmusic.download` deletes a download's old name before every attempt, the first included, rather than writing through it), and leaves out (and marks not downloaded) any download it cannot hard-link instead of copying gigabytes before a window is up. It runs before Koin and before file logging, and asks what to do if it fails. `MUSIC_DATABASE_VERSION` lets it refuse a database newer than this build.
- **Library sync port**: the computer end listens on a fixed port while a phone is paired. Musikku's core uses 48518 (`PairedSyncWire.PORT`), upstream 48517, so both desktop apps can serve their phones at once. A phone takes the port from the pairing QR and the computer's replies, so either phone app pairs with either computer. The busy-port message (`library_sync_port_in_use`) names 48518; upstream's `CLAUDE.md` still says 48517.
- **Kept**: `app.fsname = simpmusic` and everything named after it (`bin/simpmusic`, the mac zips, `simpmusic.crt` and `simpmusic-<version>.x64.msix` Conveyor writes, which CI renames to `musikku-…` before uploading (group 6), and the MSIX Identity Name `Simpmusic`), the `simpmusic://` and `wordbyword://` schemes (Discord's OAuth callback `simpmusic://discord-auth` among them), and the browser sign-in import's temp folder `simpmusic-browser-login`, which both apps share safely because a file lock keeps their imports apart.
- **Windows MSIX**: the Identity Name stays upstream's `Simpmusic`, but the Publisher is `CN=Musikku` (Conveyor's self-signed certificate follows `app.display-name`; upstream's is `CN=SimpMusic`), so Musikku's package family is `Simpmusic_f44rw8h81n7b6` and upstream's `Simpmusic_ejp2bhxmz1qq6`: the two install side by side. Read from the 2026-09-28 `musikku-desktop` CI artifact. A match by Name alone therefore hits both apps, so `install.bat` reads Name, Publisher and Application Id from the MSIX it installs, removes only packages with that Name and Publisher, and launches by the installed PackageFamilyName.
- **Watch for**: a new `".simpmusic"` path, temp file or lock named `simpmusic_…`; a Room version bump (set `MUSIC_DATABASE_VERSION`); anything new before `startKoin` that writes to the data folder; new uses of the app id; a new file AppRun installs into the user's XDG folders, or a new `com-maxrave-simpmusic-MainKt`; anything in `install.bat` that matches or launches a package by Name or by a hard-coded family, or looks for its files under a `simpmusic` name; a new writer into `downloads/` that opens an existing name instead of deleting it first; a new fixed port; a new file in the data folder that identifies this install to other devices or holds a single-use token (the import must leave it behind).
- **Commits**: `b743d83` (name), `3893095` (identity, data folder and the busy-port message), `3e13c42` (import), `2018b90` (AppImage menu entry and icon names), `bacfdbe` (`install.bat` by package family); core: `f393e02` (id, data folder, library sync paths and port), `a86bfab` (import), `126545b` (delete before every download attempt), `856f70f` (MPRIS DesktopEntry).

### 5. Upstream's launch banners

- The remote config is still upstream's, because it carries the TIDAL credentials, but its `promos` block is always stored as `"[]"`, so SimpMusic's banners never show, on Home or under the lyrics (both read that one block). The desktop import drops the block SimpMusic had saved, since Home reads it before the first fetch lands.
- **Watch for**: new remote fields that put upstream's own content on screen.
- **Commits**: core: `ec7367c`.

### 6. Builds, CI and Sentry

- Every workflow signs with `BASE_64_SIGNING_KEY`; the fork has no `SIGNING_KEY` secret. Each signed APK is uploaded on its own as `musikku-<version>-…-<abi>.apk` with `upload-artifact@v7` and `archive: false`, and the job fails unless it finds exactly one APK per ABI. Desktop artifacts and the output of `build_and_sign_apk.sh` are named `musikku-…` too: every file CI uploads or the script writes starts with a lowercase `musikku`.
- The mac files are uploaded the same way, one artifact per file (aarch64 and amd64 apart), in both `desktop-package.yml` and `android-release.yml`: `musikku-<version>-mac-<arch>.zip` and the `musikku-<version>-mac-<arch>.dmg` made from it. The zips are Conveyor's `simpmusic-<version>-mac-<arch>.zip`, moved to `musikku-…` names before the upload, and the step fails unless it finds exactly one per arch. The DMG job fetches the zips with `download-artifact@v8`, `merge-multiple` and `skip-decompress`; without `skip-decompress` it unpacks any file served as a zip, and the loop that wraps the DMGs finds no zips.
- The AppImage and the Windows installer are uploaded the same way, with no `desktop-appimage` or `desktop-windows` zip around them: `musikku-<version>-linux-x86_64.AppImage` (`packageConveyorAppImage` writes `Musikku-x86_64.AppImage`) and `musikku-<version>-windows-x64-installer.zip`. The installer zip holds `install.bat`, `musikku.crt` and `musikku-<version>-windows-x64.msix`, which are Conveyor's `simpmusic.crt` and `simpmusic-<version>.x64.msix` renamed, and `install.bat` looks for those names. Each step fails unless it finds exactly one AppImage or MSIX.
- **Releases**: `android-release.yml` also runs on pushes to `musikku` and on tags named `x.x.x` or `x.x.x.x` (never `vx.x.x`, which are upstream's). A `gate` job decides: a tag push builds and drafts a release only when the tag's commit is on `musikku` at that moment (GitHub's compare API answers `identical` or `ahead`), and any other tag builds nothing. Branch pushes and manual runs build without releasing. Once every build succeeds, `draft-release` downloads all 15 artifacts (`skip-decompress`, so the zips stay zips), checks each one is there, and runs `gh release create --draft --verify-tag`. A re-run that finds the release only re-uploads the files with `--clobber` and leaves notes edited by hand alone. It then puts the same lightweight tag on the core commit the tagged commit points at, in the repository `.gitmodules` names, through the API with the `CORE_TAG_TOKEN` secret (a fine-grained token with Contents read and write on `cuong-tran/simpmusic_core` only, since `GITHUB_TOKEN` cannot write to another repository). A core tag already on that commit is fine; one on another commit fails the step and is never moved. Without the secret the step fails after the release is drafted, and core has to be tagged by hand. Its notes come from `scripts/musikku-release-notes.sh <tag> [<rev>]`, which can be run locally. That script compares the tag with the highest lower release tag in version order, not with the nearest ancestor, because a sync rebases `musikku` and the old tag stops being an ancestor. It lists fork commits under Musikku (dropping subjects the previous release already had, since a rebase carries every fork commit again) and upstream commits under Upstream SimpMusic. Each line is the subject alone, with no commit id and no PR or issue number (`(#N)`, `#N`), since the fork's and upstream's numbers look the same. It tells the two apart by fetching upstream `main` and `dev`; if that fetch fails, everything is listed under Musikku. A tag runs the workflow file at the tagged commit, so a tag on a commit older than this change drafts nothing.
- **Pull requests** run `pr-check.yml`, a check of the fork's own: every pull request, whatever its target branch, skipping doc-only changes (`paths-ignore`), with a new push cancelling the PR's run in progress. Three parallel jobs only compile: Android Full (`androidApp` + `vivoAndroidApp`), Android FOSS (`androidApp`, which pulls in the `*-empty` modules) and Desktop (`:desktopApp:compileKotlinJvm`). No R8/ProGuard, lint-vital, Conveyor, DMG, signing, secrets or artifacts, so a fork's PR runs it too. Its first run (2026-11-22) took 5.9 min on about 17 runner minutes, 5.4 min of Gradle for each Android job and 4.8 for Desktop, and the next one 4.3 min once the dependencies were cached. The packaging workflows took 25-29 min on about 55, R8 being ~5 min of each APK job, ProGuard ~10 and Conveyor ~4.5 of the desktop one. `android.yml` and `desktop-package.yml` keep upstream's triggers: the first also runs on PRs into `dev`, the second on no PR.
- Sentry reports to org `komikku`, project `musikku`, from both `androidApp` and `vivoAndroidApp`.
- The workflows read four secrets the fork doesn't have: `SENTRY_DSN_JVM`, `LASTFM_API_KEY`, `LASTFM_SECRET` and `BLOCK_USERS_TOKEN`. They arrive empty, so desktop CI builds have no Sentry. Since upstream's Discord OAuth they also read `DISCORD_CLIENT_ID`; a build without it signs in to Discord through kizzy, as FOSS does, and one with it should use a Discord application of the fork's own, since the OAuth steps name Musikku. Set it only after giving the login a callback of the fork's own too: Discord returns through `simpmusic://discord-auth` (`DiscordOAuth.REDIRECT_URI` in core), a scheme SimpMusic also handles, so with both apps installed the OS can hand the code to SimpMusic and Musikku's login never completes. The new callback is registered on the fork's Discord application and changes `REDIRECT_URI`, the handlers in `App.kt` and `DesktopDeepLinkHandler`, and the schemes Android and Conveyor register, together.
- **Watch for**: upstream's `pull_request` triggers in `android.yml` and `desktop-package.yml` (take them as they are; `pr-check.yml` covers every PR), a new module, target or flavour that `pr-check.yml` doesn't compile, a new build job in `android-release.yml` that doesn't need `gate` or isn't listed in `draft-release`'s `needs`, or a new artifact the release's file check doesn't name; new jobs or upload steps (one that puts several mac files in one artifact, uploads a desktop file inside a named artifact zip, or does a `download-artifact` of the mac zips without `skip-decompress`), `SimpMusic` or a capitalised `Musikku` in artifact or installer file names, `SIGNING_KEY`, a new `sentry { }` block.
- **Commits**: `413794d`; the desktop artifact names are in `b743d83`, the one-artifact-per-file mac uploads in `5c9d993`, their Musikku names in `d292c74`, the AppImage and Windows installer's in `1bb1014`, and the lowercase `musikku-…` file names in `d7a1436`. The mac-zip artifact path was merged upstream as #2575 and has left the fork's diff.

### 7. Repository wiring

- `.gitmodules` points `core` at `cuong-tran/simpmusic_core` (branch `musikku`). A core change is committed there first, then the superproject bumps its pointer. `CLAUDE.md` is upstream's file plus one pointer line; everything else about Musikku lives in this file.
- **Commits**: `849c776` (the submodule), and the commits that add and update this file and the `musikku-upstream-sync` skill: `332635c`, `b108c97` (group 8), `8dfec0a` and the last of the series.

### 8. Upstream's promotional prompts and blog notifications

- Home shows none of upstream's own prompts: `ReviewDialog` (star on GitHub, ProductHunt, buy me a coffee, follow on X), `BlogPromoDialog` and `FootgunsStarDialog` are deleted with `FOOTGUNS_STAR_KEY` and `BLOG_PROMO_KEY`. The share-lyrics permission request is the only dialog left on Home's app-open milestones, and the launch banner's `otherDialogOpen` no longer waits on the removed three.
- Android posts no blog notifications: `RssFeedNotifyWork` is deleted, and `MainActivity` cancels the `"Blog RSS Worker"` unique work and deletes the `blog_updates_channel` channel on every start, for devices that had them from an earlier build. The Notifications screen hides the `TYPE_BLOG` rows already stored.
- Settings → About drops "Follow me on X", "Developer Blog" and the blog-notification switch. `DataStoreManager.blogNotificationEnabled` (core) is left in place, unread. The unused strings stay, since translations come from Crowdin.
- Group 5 is the data side of the same idea: the launch banners and the lyrics promo line are emptied in core, so their UI stays as upstream wrote it.
- **Watch for**: a new dialog on Home's `openAppTime` milestones, a new worker or channel that pushes upstream's news, a new link to the developer's X account or blog in Settings, and new uses of `blogNotificationEnabled`.
- **Commits**: `b108c97`.

### 9. In-app auto update

- **Source**: the update check asks Musikku's own GitHub releases (`api.github.com/repos/cuong-tran/Musikku/releases`), not upstream's. Only published releases whose tag is plain dotted numbers count (`x.x.x` or `x.x.x.x`, group 6); drafts, prereleases, upstream-style `vx.x.x` tags and suffixed tags (`2.6.0-beta`) are skipped. The highest version wins by number, whatever order GitHub lists them in, and it is offered only when it is strictly newer than this build (`pickLatestRelease`, tested in `MusikkuReleasesTest`). Code: `utils/update/MusikkuReleases.kt`.
- **Asset per platform**, matched by suffix (the file's version is the build's versionName, which normally equals the tag): Android `-<full|foss>-<abi>.apk`, where the flavour is the new `BuildKonfig.isFullBuild` and the ABI is read from the installed APK's own `lib/<abi>/` folders (several or none → `universal`); the vivo build (`com.spotify.music`) `-full-vivo-arm64-v8a.apk`; Linux `-linux-x86_64.AppImage`, only when started from an AppImage (`$APPIMAGE`); macOS `-mac-<aarch64|amd64>.zip`, only from inside an `.app` bundle; Windows `-windows-x64-installer.zip`. A debug or `.dev` Android build and a desktop dev run (`simpmusic.dev`) get no asset, and neither does a release with no matching file: Download then opens the releases page as before. The suffix is worked out on the IO thread, since on Android it opens the installed APK. When the matching file's own version (`musikku-<V><suffix>`) is not newer than this build, the release is not offered at all: a tag ahead of its build's versionName (tag `2.6.0.1` built from `2.6.0`) would otherwise install the same version and be offered again on every launch.
- **Download and install** (`utils/update/AppUpdater.kt`, `expect/UpdateInstaller.kt` and its two actuals, `ui/component/UpdateDialog.kt`): the file is downloaded only from `github.com/cuong-tran/Musikku/releases/download/`, into the app cache (`cacheDir/updates`) on Android and `~/.musikku/update` on every desktop OS: `/tmp` is shared between users, so a fixed name there could be planted or swapped for a link, and on Windows MSIX virtualizes AppData, temp included, while `install.bat` elevates itself out of the package. The folder is refused when it is a symbolic link, is emptied only when a download actually starts. On Desktop that folder is not a temp folder, so every update check deletes what is staged in it, the current release's own file included; Android's cache copy is cleared once a check finds this build up to date. On Android a finished download of the same file (same name and size) is installed again without fetching it, which matters because Android 11+ kills the app when the install permission is granted. `AppUpdater` holds the release it is updating to while its state is not Idle, so the dialog comes back after the activity is recreated mid-update (the view model holding the offer is rebuilt with it); `App.kt` reopens the dialog whenever the updater is busy. Cancelling closes the connection off the UI thread. The Linux restart waits for this process to exit (single-instance lock) before starting the new AppImage. Progress, cancel, the permission guide, installing and failure all show on the update dialog, which moved out of `App.kt` unchanged in look. Android hands the APK to the system installer through the existing FileProvider; Linux replaces the running AppImage in place and restarts it; macOS unpacks with `ditto` and a script swaps the bundle once the app has quit, then reopens it; Windows unpacks the installer zip and starts `install.bat`, which closes and relaunches the app itself.
- **Android permission**: `REQUEST_INSTALL_PACKAGES` is declared in androidApp's manifest (the vivo build shares it). Until the user allows Musikku to install apps, the dialog shows a three-step guide with Open settings, and continues by itself when the app resumes.
- **Upstream releases only notify**: the upstream check (`updateRepository`, by `updateChannel`) still runs after Musikku's, but a newer upstream release now raises a notification ("SimpMusic update available … Musikku has not been synced to it yet") once per app start, not once per release. The flag is in memory (`upstreamUpdateNotified`), outside the view model, which Android rebuilds with the activity, and is raised only once the notification was actually posted (Android: notifications allowed and enabled), so a check made with notifications off can still post later in the same run. Android posts it on channel `upstream_update_channel` and opens the upstream release page; Desktop posts it through `DesktopNotificationManager`, with no click action.
- **Strings** are English only, in `composeResources/values/musikku_strings.xml`, outside the Crowdin-managed `strings.xml`.
- **Watch for**: changes upstream makes to `SharedViewModel.checkForUpdate()`, the update dialog or `VersionManager`; a change to the release asset names or tag format in the workflows (group 6), which the suffixes and the tag filter must follow; a new manifest that does not share androidApp's.
- **Commits**: `dadc143`, `b18ec85`, `e0ac68b`, `f9f6392` (this group), `8d3aec2`; review fixes `deb7b61`, `8b75a10`, `786aa61`, `2ebaafd`, `0337c6b`, `423a11f`, `bedc8ab`, `d19b9a9`, `31e0844`, `2613e22`, `5188248`, `8edb8a7`; `1c43a91` (Desktop cleanup on every check), and the commit that names them.

### Kept as upstream

- **Identifiers**: Kotlin packages, class names, Android `namespace`, intent actions, Gradle modules, DataStore keys, the database name and the notification channel id.
- **Not decided yet**: the Discord "Visit SimpMusic" button and the Credits screen links still point at upstream.

## 🔀 Merging upstream

The fork follows upstream by **merging**, not by rebasing: `musikku` is pushed to `origin`, so rewriting it breaks other clones. The exceptions were 2026-11-04 and 2026-11-20, when the owner had both repos rebased (see **Last upstream sync**); the `musikku-upstream-sync` skill holds that procedure. Merge `upstream/main` to follow releases; merge `upstream/dev` only when you want unreleased work.

### Steps

```bash
# 0. Clean tree, on the personal branch
git switch musikku
git fetch upstream
git -C core fetch upstream_core
NEW=upstream/main                                  # or upstream/dev
OLD=140673a                                        # from "Last upstream sync"
NEW_CORE=$(git ls-tree "$NEW" core | awk '{print $3}')   # the core commit upstream pins
OLD_CORE=8413a25                                   # from "Last upstream sync"

# 1. core first: merge upstream's pinned commit into core/musikku
git -C core switch musikku
git -C core merge "$NEW_CORE"                      # resolve with the conflict rules below

# 2. superproject
git merge "$NEW"                                   # resolve with the conflict rules below
git -C core checkout musikku && git add core       # point at the merged core, not upstream's pin
git commit                                         # once every conflict is resolved
```

3. Run the sweep (below) over `OLD..NEW` in the superproject and `OLD_CORE..NEW_CORE` in core. Re-apply the matching group from **What the fork changes** to whatever the sweep finds. Make each fix its own commit after the merge commit, with the reason in the message (for example "Fix what the upstream merge left naming SimpMusic"). A fix inside `core/` is committed on `core/musikku` first, and then the superproject bumps its pointer.
4. Verify as `CLAUDE.md` says (**Verification After Code Changes**): check the touched files with the JetBrains MCP diagnostics, and do not build unless asked.
5. Update **Last upstream sync** in this file, and add the fix-up commits to their groups.
6. Push `core` (`git -C core push origin musikku`) before the superproject, so the pointer resolves for anyone who pulls.

### Conflict rules

| Where | Resolution |
|---|---|
| `CLAUDE.md` | Take upstream's (`git checkout --theirs CLAUDE.md`), then add the pointer line back as the first line. Never merge fork notes into it. |
| `core` (the gitlink) | Use the commit from step 1 (`git -C core checkout musikku && git add core`). Never upstream's pin, which has no Musikku changes. |
| `.gitmodules` | Keep ours (`cuong-tran/simpmusic_core`). |
| Icons and artwork (`*.png`, `*.webp`, `*.icns`, `*.ico`, `mono.xml`, `monochrome.xml`, `ic_launcher*.xml`) | Keep ours (`git checkout --ours <file>`). If upstream changed the icon *structure* (new layer, density or asset), regenerate it with `scripts/icons/` instead of taking theirs. |
| `values*/strings.xml` | Take upstream's, then re-apply group 2, including the grammar fixes. `git show 6d68bf5 -- composeApp/src/commonMain/composeResources/values/strings.xml` lists the keys. Check for new keys that name the app. |
| `MusicDatabase.kt` `@Database(version = …)` | Keep `version = MUSIC_DATABASE_VERSION` and set the constant to upstream's new number. |
| `SettingScreen` custom-colour presets | Take upstream's list, and keep `"FFD13433" to "Musikku"` first and the grid three across (group 3). |
| `PairedSyncWire.PORT` (core), `library_sync_port_in_use` | Keep 48518 in both (group 4). |
| `SharedViewModel.checkOfficialBuild` | Take upstream's logic, then comment out (or remove) the signing-key half again. Keep the package-name half. |
| `SharedViewModel.checkForUpdate`, the update dialog in `App.kt` | Take upstream's other changes, and keep the Musikku release check first and the upstream check notifying only (group 9). A change upstream makes to its inline dialog goes into `UpdateDialog.kt`. |
| `CommonRepositoryImpl` promos write | Take upstream's surrounding changes, and keep writing `"[]"`. |
| `HomeScreen` open-count milestones, the review/blog/footguns dialogs, `RssFeedNotifyWork`, the About rows in `SettingScreen` | Take upstream's other changes around them, and keep the prompts, the worker and the rows removed (group 8). Imports the removal dropped can be needed again by new upstream code (`lifecycleScope` and `launch` in `MainActivity` were, for library sync): check each one. |
| Workflows, `build_and_sign_apk.sh` | Take upstream's new steps, then re-apply group 6: lowercase `musikku-…` file names, `BASE_64_SIGNING_KEY`, one artifact per file. |
| `androidApp` / `vivoAndroidApp` `build.gradle.kts` | Take upstream's, and keep `applicationId = "app.musikku"` and the Sentry org and project. |
| `desktopApp/build.gradle.kts`, `conveyor.conf` | Take upstream's, and keep display name, `rdns-name`, `bundleID`, `packageName`, AppImage `appName`, `CFBundleURLName`, `StartupWMClass`, and the AppImage's `app.musikku.desktop` / `musikku.png` names with AppRun's legacy-entry cleanup (group 4). |
| `scripts/windows/install.bat` | Take upstream's other changes, and keep the identity read from the MSIX: remove and launch by Name AND Publisher, never by Name alone or by upstream's hard-coded `Simpmusic_ejp2bhxmz1qq6` (group 4), and keep looking for `musikku.crt` and `musikku-*.msix`, the names CI gives the bundle (group 6). |
| A fork change that upstream has since merged as its own PR (e.g. #2689) | Take upstream's version and drop the fork's copy, keeping only Musikku-specific wording such as comments that name `app.musikku`. |

### Sweep new upstream code

The sweep prints every **added** line in the upstream delta that matches something the fork changes. It skips binaries, changelogs, `README.md`, `CLAUDE.md`, and `import`/`package` lines. Each hit is either a place to re-apply a group or a known keep (see **Kept as upstream**). Over `bdf28a83..f037043d` it printed 122 lines, among them the leftovers that were then fixed by hand: the desktop log path, the vivo Sentry org and signing secret, `taste_card_subtitle`, and the share-card, receipt and log-export literals. Over `f037043d..b5cff20` it printed 80, and the leftovers were the new Hebrew locale, the song-recognition strings and a few Italian and Swedish translations. Over `b5cff20..140673a` it printed 13: library sync's, Discord's and the browser sign-in's new strings and the busy-port message; in core, over `344d614..8413a25`, it found library sync's and the waveform cache's paths under `~/.simpmusic`. It only sees what the merge added. Text that already said SimpMusic before the last sync needs a full search instead (`git grep -n SimpMusic`).

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
          if /SimpMusic(?![A-Za-z])|simpmusic\.org|"com\.maxrave\.simpmusic|"\.simpmusic|simpmusic_|8ECAE6|com-maxrave-simpmusic|Simpmusic_|48517|SIGNING_KEY|upload-artifact|OFFICIAL_PACKAGE_NAMES|FdroidSigningKeys|isOfficialBuild|targetPackage|org\.set\(|^\+\s*version = \d|promos|remote-config/;
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
| `com-maxrave-simpmusic`, `Simpmusic_` | 4 (the AppImage's desktop entry and MPRIS name; MSIX package families in `install.bat`) |
| `48517` | 4 (library sync's port, 48518 in Musikku) |
| `8ECAE6` | 3 (brand colour) |
| `SIGNING_KEY`, `upload-artifact` | 6 (CI) |
| `OFFICIAL_PACKAGE_NAMES`, `FdroidSigningKeys`, `isOfficialBuild` | 1 (official-build check) |
| `org.set(` | 6 (Sentry) |
| `version = NN` | 4 (`MUSIC_DATABASE_VERSION`) |
| `promos`, `remote-config` | 5 (launch banners) |

The sweep cannot see new **binary** assets. List them with `git diff --stat "$OLD" "$NEW" -- '*.png' '*.webp' '*.icns' '*.ico' '*/drawable*/*.xml' '*/mipmap*/*'` and check each against group 3.

### Rebasing instead

If a branch is rebased onto upstream (a short-lived local branch, or `musikku` itself only when the owner asks, as on 2026-11-04 and 2026-11-20), the same rules apply commit by commit: resolve each conflict with the table above, then run the sweep once at the end over the whole upstream range, and put each fix in the fork commit it belongs to. The `musikku-upstream-sync` skill has the mechanics and the traps.

## 🤝 Contributing upstream

- **Start from upstream, not from the fork.** Superproject: `git switch -c fix/<topic> upstream/dev`, then open the PR against `maxrave-dev/SimpMusic` `dev` (upstream's guideline: "create branch from `dev`"). Core: branch from `upstream_core/multiplatform` and open the PR against `maxrave-dev/core`.
- **Keep fork-only changes out of those branches**: Musikku names, `app.musikku`, `DESKTOP_APP_ID` / `DESKTOP_DATA_DIR` values, the red seed, Musikku icons, the disabled signature check, the promos override, Sentry `komikku`, and CI artifact names and secrets. If a fix was written on `musikku`, cherry-pick it onto the upstream branch and strip those parts.
- Follow `CLAUDE.md` in the upstream branch, including its Auto-Update Rule. That note belongs to the PR.
- **Fixes carried ahead of upstream.** Core `f15ee25` makes `singleThreadedDownload` start each attempt with no error, so a download that fails an attempt and then succeeds is no longer reported as failed. It is a bug in upstream's code, not a fork change, so it belongs to no group above. The same change sits on `fix/single-threaded-download-retry` off `upstream_core/multiplatform` (`3f91074`, cuong-tran/simpmusic_core#1) and has not been sent to `maxrave-dev/core` yet. Drop the fork's copy once upstream has it. Until then a rebase keeps it as its own commit, the last of core's: `Ytmusic.kt` also carries group 4's `126545b`, so the path is claimed twice on purpose.
- **Once upstream merges a PR, drop the fork's copy on the next merge** (conflict rules, last row). Examples: #2575 (mac-zip artifact path) and #2689 (vivo debug shortcuts) were merged and left the fork's diff. #2690 (vivo APK artifact names) was closed, so that change stays in group 6.
- **Shrink the fork when you can.** A fork change that is a neutral refactor upstream could accept leaves the fork with a one-line value change instead of a scattered edit. These are only suggestions and none of them has been proposed: the `DESKTOP_DATA_DIR` / `DESKTOP_APP_ID` constants with upstream's values (`.simpmusic`, `com.maxrave.simpmusic`), the `MUSIC_DATABASE_VERSION` constant, and share-card or file-name literals read from `app_name` instead of a hardcoded `"SimpMusic"`.

---

**Last updated**: 2026-11-22
