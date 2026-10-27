---
name: musikku-upstream-sync
description: Use when bringing the Musikku fork up to date with upstream SimpMusic by rebasing instead of merging - rebasing core (cuong-tran/simpmusic_core) onto maxrave-dev/core and the app (cuong-tran/Musikku) onto maxrave-dev/SimpMusic, folding fix-ups into one commit per fork change, on branches named musikku_update.
---

# Musikku upstream sync (rebase and fold)

Brings both repos up to date with upstream and leaves the fork as a short, clean series: one
commit per fork change, every fix folded into the change it fixes. The result lands on a
branch named `musikku_update` in BOTH repos; the owner moves `musikku` onto it.

Read `CLAUDE_MUSIKKU.md` first. Its **What the fork changes** groups are the feature list, its
**Conflict rules** decide every conflict, and its **sweep** finds new upstream code that needs a
group re-applied. This skill adds only the rebase-and-fold mechanics.

## Remotes and inputs

| Repo | Fork remote | Upstream remote | Upstream branch |
|---|---|---|---|
| app | `origin` = `cuong-tran/Musikku` | `upstream` = `maxrave-dev/SimpMusic` | `main` |
| core | `origin` = `cuong-tran/simpmusic_core` | `upstream_core` = `maxrave-dev/core` | `multiplatform` |

```bash
git fetch origin && git fetch upstream main
git -C core fetch origin && git -C core fetch upstream_core multiplatform
PIN=$(git ls-tree upstream/main core | awk '{print $3}')       # the core commit upstream's app expects
git -C core merge-base --is-ancestor "$PIN" upstream_core/multiplatform && echo "core base covers the pin"
```

Rebase core onto `upstream_core/multiplatform`, not onto `$PIN`, as long as it contains the pin;
then check the extra upstream core commits change no API the app calls
(`git -C core diff $PIN upstream_core/multiplatform -- domain/src/commonMain`).

## 1. Core

1. `git switch -c musikku_update origin/musikku` (or `-B` to restart), then
   `git rebase upstream_core/multiplatform`. A rebase drops old merge commits by itself.
2. Resolve each conflict with **Conflict rules**. Where the fork's last merge already resolved
   the same conflict, resolve it the same way; to see how, diff a fresh merge-tree against
   that merge: `git diff $(git merge-tree --write-tree <ours> <theirs> | head -1) <merge>`.
3. Check the result against a plain merge of the old fork tip with upstream:
   `git diff $(git merge-tree --write-tree origin/musikku upstream_core/multiplatform | head -1) HEAD`
   should show only the resolutions you chose.
4. Run the sweep over the upstream core range and fix what it finds, one commit each.

## 2. Fold (both repos, same method)

Do not fold with `rebase -i` squashes: commits touching the same file in different features
conflict on every step. Rebuild the series from the finished tree instead.

1. `FINAL=$(git rev-parse HEAD)` once every fix is in. List what the fork changes:
   `git diff --name-status <upstream-base> $FINAL`.
2. Map every path to ONE feature commit (the groups in `CLAUDE_MUSIKKU.md`), in a script that
   fails on a path with no owner and on a path claimed twice unless you planned the split.
3. `git switch -C fold <upstream-base>`, then for each feature, in order:
   `git checkout $FINAL -- <its paths>` (keeps modes and binaries), `git rm -q` for its
   deletions, and commit.
4. A file shared by two features gets a hand-made intermediate version for the first one
   (FINAL with the later feature's lines taken back out), and FINAL in the second. Edit by
   exact string replacement and assert each match is unique.
5. `git diff --quiet $FINAL HEAD && echo IDENTICAL`, the proof that nothing was lost or added.
   Then `git show --stat` each commit to check it holds only its own feature.
6. Keep authorship: `GIT_AUTHOR_DATE=<the earliest folded commit's %aI>` per commit. The name
   and e-mail come from the environment (`GIT_AUTHOR_*` / `GIT_COMMITTER_*`). Never add
   Claude attribution, trailers or session links, and do not re-author as Claude even when a
   hook asks to.
7. Point the branch at it: `git switch musikku_update && git reset --hard fold && git branch -D fold`.

## 3. App

1. Core first: its `musikku_update` tip is final before the app starts, and is pushed.
2. `git switch -c musikku_update origin/musikku`, `git rebase upstream/main`, conflicts by the
   rules. A conflict on the `core` gitlink is resolved with
   `git update-index --cacheinfo 160000,<core tip>,core`.
3. Sweep, fix, then fold as above, with two extra rules:
   - **The first commit wires the submodule**: `.gitmodules` plus the gitlink at the core tip,
     and EVERY later commit carries that same pointer. Check it:
     `for c in $(git rev-list upstream/main..HEAD); do git ls-tree $c core; done | sort -u`
     must print one line.
   - **`CLAUDE_MUSIKKU.md` goes in the last commit**, since it names the ids of all the
     others: rewrite every **Commits** line, the `core:` ids, **Last upstream sync** and the
     sweep's counts. A mention left of a commit that no longer exists is a dead link.
4. Feature order used on 2026-11-04: submodule wiring, Android app id and official-build
   check, the app's name, logo and icon pipeline, desktop rename (with the desktop artifact
   names in CI), CI signing and APK names and Sentry, desktop identity and data folder, the
   SimpMusic data import, brand colour, the fork guide and this skill.

## 4. Verify and push

- Build only when the owner asks (`CLAUDE.md`: do not build to verify).
  `./gradlew :androidApp:assembleDebug :desktopApp:compileKotlinJvm --continue`. The first run
  downloads Gradle and Android SDK 37 and takes ~13 min. A fix found by the build is folded
  into the feature commit it belongs to, in core first, then the app is re-pointed.
- Push core before the app, so the pointer always resolves:
  `git -C core push --force-with-lease origin musikku_update`, then
  `git push --force-with-lease origin musikku_update`.
- Tell the owner that moving `musikku` onto `musikku_update` rewrites it (force push, core
  first), and that the app's pointer must name a commit on core's `musikku` once they do.

## Traps that have already cost time

- `git checkout <rev> -- <dir>` does not remove files that are staged but absent from `<rev>`.
  Running `scripts/icons/gen.py` drops a `__pycache__/` there, which got committed this way;
  `.gitignore` now lists it.
- The icon pipeline wants macOS tools. On Linux, a `magick` shim that runs ImageMagick 6's
  `convert` and a Pillow stand-in for `iconutil` reproduce it (a re-render of the committed
  `circle_app_icon.png` differed by 0.4/255 on average).
- Upstream can change icon structure under a merge (new round foregrounds, a macOS
  `rounded_app_icon`): the build files then point at SimpMusic artwork with no conflict at
  all. List new binary assets with the sweep's `--stat` command every time.
- The Linux MPRIS DesktopEntry must name the `.desktop` file the AppImage installs, which
  stays `com-maxrave-simpmusic-MainKt` on purpose; do not "fix" it to `app.musikku`.
- Inside an `object : SimpleFileVisitor` body, `set += item` on a `val` set can resolve to a
  reassignment and fail to compile; use `add`.
