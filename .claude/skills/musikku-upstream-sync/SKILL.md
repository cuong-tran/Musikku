---
name: musikku-upstream-sync
description: Use when bringing the Musikku fork up to date with upstream SimpMusic by rebasing instead of merging - rebasing core (cuong-tran/simpmusic_core) onto maxrave-dev/core and the app (cuong-tran/Musikku) onto maxrave-dev/SimpMusic, adapting each fork commit to what upstream changed, pointing every app commit at the new core tip, reviewing the fork commits with /code-review (fixing only what the fork caused), and pushing core first. Covers rebasing musikku in place and the rebase-and-fold onto musikku_update.
---

# Musikku upstream sync (rebase)

Brings both repos up to date with upstream by rebasing the fork's commits onto it, in BOTH repos,
core first. Read `CLAUDE_MUSIKKU.md` first: its **What the fork changes** groups are the list of
fork changes, its **Conflict rules** decide every conflict, and its **sweep** finds new upstream
code that needs a group re-applied. This skill holds the mechanics and the traps.

| Mode | When | Branch | Done on |
|---|---|---|---|
| Rebase in place | the owner asks to rebase `musikku` (the usual request) | `musikku` | 2026-11-20 |
| Rebase and fold | the owner asks for one commit per fork change | `musikku_update`, which the owner then moves `musikku` onto | 2026-11-04 |

The usual request, in the owner's words: rebase `simpmusic_core/musikku` onto upstream, fix
conflicts, and for each fork commit find whether upstream's new code needs more of that change;
then `/code-review`, fix only the findings the fork's changes caused, and push. Then the same for
`Musikku/musikku` onto `upstream/main`, with `core` pointing at the new core commit.

## 0. Inputs

| Repo | Fork remote | Upstream remote | Upstream branch |
|---|---|---|---|
| app | `origin` = `cuong-tran/Musikku` | `upstream` = `maxrave-dev/SimpMusic` | `main` |
| core | `origin` = `cuong-tran/simpmusic_core` | `upstream_core` = `maxrave-dev/core` | `multiplatform` |

Work in two clones: `/home/user/simpmusic_core` for core, `/home/user/Musikku` (with its own
`core/` submodule checkout) for the app.

```bash
git fetch --all && git -C core fetch --all          # or each clone separately
PIN=$(git ls-tree upstream/main core | awk '{print $3}')   # the core commit upstream's app expects
git -C core merge-base --is-ancestor "$PIN" upstream_core/multiplatform && echo "core base covers the pin"
```

- Start from `origin/musikku`, never from a local `musikku`: the owner moves and extends it
  between syncs (2026-11-20: 3 core and 6 app commits added after the last sync). Use
  `git switch -C musikku origin/musikku`.
- Rebase core onto `upstream_core/multiplatform` as long as it contains `$PIN`; on 2026-11-20 the
  pin WAS that tip.
- Commits the owner added after the last sync (review fixes, core bumps, fixes carried ahead of
  upstream) stay their own commits in an in-place rebase.

## 1. Survey upstream first

Before rebasing, find what upstream changed that the fork's commits will have to follow. `OLD` and
`OLD_CORE` are in the guide's **Last upstream sync**.

```bash
FILES=$(git diff --name-only OLD origin/musikku)                 # what the fork touches
git diff --stat OLD upstream/main -- $FILES                      # predicts the conflicts
# then the guide's sweep over OLD..upstream/main and OLD_CORE..<new core base>,
# and its binary-asset --stat
```

Then read every new upstream feature (the commit list, not only the sweep) against the groups.
What turned up on 2026-11-20, as a checklist for next time:

| Upstream change | Fork work it needed | Group |
|---|---|---|
| Room version bump | `MUSIC_DATABASE_VERSION` to the new number (the only core conflict) | 4 |
| New files under `~/.simpmusic` (library sync pairing + backups, waveform cache) | `DESKTOP_DATA_DIR` / `desktopDataPath()` | 4 |
| A fixed TCP/UDP port on the desktop (library sync, 48517) | Musikku's own port (48518) and the UI text naming it | 4 |
| A file that identifies the install to other devices, or a single-use token (sync pairing, Discord OAuth refresh token) | the desktop import leaves it behind | 4 |
| New strings and literals naming the app | Musikku, base locale and every translation present | 2 |
| Colour presets reworked | Musikku's red first | 3 |
| A seed-filled control moved to theme `primary` | drop the fork's white-on-seed edit there | 3 |
| New workflow secrets (`DISCORD_CLIENT_ID`) | note it in group 6; nothing to sign | 6 |
| New uses of imports a fork commit removed (`lifecycleScope`, `launch` in `MainActivity`) | restore the imports | 8 |
| New OAuth redirect on a kept scheme (`simpmusic://discord-auth`) | no code yet; record it in the guide as a prerequisite | 4, 6 |

Known keeps that the sweep will print again: wire and protocol names (`X-SimpMusic-Phone`,
`simpmusic-library-sync/v1`, `simpmusic://` schemes), Discord RPC's "SimpMusic" name and buttons,
`"SimpMusic Desktop"` as the last-resort computer name, the `simpmusic-browser-login` temp folder
(a file lock keeps the two apps apart), comments about upstream's own services.

## 2. Core: rebase, adapt, review, push

1. `git switch -C musikku origin/musikku && git rebase upstream_core/multiplatform`. Resolve each
   conflict with the **Conflict rules**; stage by file name.
2. Check the rebase against a plain merge; only the resolutions you chose may differ:
   `git diff $(git merge-tree --write-tree origin/musikku upstream_core/multiplatform | head -1) HEAD`.
3. Adapt each fork commit to the survey's findings. Make the change on top as an `amend!` commit
   whose message is `amend! <subject>`, a blank line, then the full new message (so the commit
   message describes the adaptation too), then fold it in without an editor:
   `GIT_SEQUENCE_EDITOR=: git rebase -q -i --autosquash upstream_core/multiplatform`.
   `git diff --quiet <tip before> HEAD && echo IDENTICAL` proves the fold moved nothing else.
4. A change that spans two fork commits (a helper the import commit adds and the promos commit
   uses) cannot be one fixup. Write the file's version for each commit (a short script doing exact,
   asserted string replacements), then rebase with a custom todo:
   `GIT_SEQUENCE_EDITOR="cp todo.txt" git rebase -i upstream_core/multiplatform`, with an
   `exec cp <first version> <file> && git commit -q -a --amend -F <msg>` after the first commit;
   when the second one conflicts, `cp <final version>`, `git add <file>`,
   `GIT_EDITOR=true git rebase --continue`, and an `exec cmp <final version> <file>` after it.
5. Review: `/code-review` with the target `branch musikku in /home/user/simpmusic_core against
   upstream_core/multiplatform` (see **5. Review** below).
6. Push: `git push --force-with-lease=musikku:origin/musikku origin musikku`. Core is pushed
   before the app starts pointing at it.

## 3. App: rebase, point core, adapt

1. **Move the submodule checkout to the new core tip first**:
   `git -C core fetch origin musikku && git -C core checkout <core tip>`. Left at the old pointer,
   anything that stages the whole tree stages the old `core` too (see **Traps**).
2. `git switch -C musikku origin/musikku && git rebase upstream/main`. On a `core` gitlink
   conflict: `git update-index --cacheinfo 160000,<core tip>,core`. Stage other resolutions by
   file name, then `GIT_EDITOR=true git rebase --continue`.
3. Point EVERY commit at the core tip and drop those left empty (the old "bump core" commits),
   keeping each commit's author and original date:

   ```bash
   # repoint.sh <base> <tip> <core sha>  -> prints the new tip; then: git reset --hard <new tip>
   set -euo pipefail; BASE=$1; TIP=$2; CORE=$3; parent=$(git rev-parse "$BASE")
   orig_date() { local s o; s=$(git log -1 --format=%s "$1")
     o=$(git log --format='%H%x09%s' "$BASE..origin/musikku" | awk -F'\t' -v s="$s" '$2==s{print $1; exit}')
     git log -1 --format=%aI "${o:-$1}"; }
   export GIT_INDEX_FILE=$(mktemp -u)
   for c in $(git rev-list --reverse "$BASE..$TIP"); do
     git read-tree "$c"; git update-index --cacheinfo 160000,"$CORE",core; tree=$(git write-tree)
     [ "$tree" = "$(git rev-parse "$parent^{tree}")" ] && { echo "dropped: $(git log -1 --format=%s "$c")" >&2; continue; }
     parent=$(GIT_AUTHOR_NAME="$(git log -1 --format=%an "$c")" GIT_AUTHOR_EMAIL="$(git log -1 --format=%ae "$c")" \
       GIT_AUTHOR_DATE="$(orig_date "$c")" git commit-tree -S "$tree" -p "$parent" -m "$(git log -1 --format=%B "$c")")
   done; rm -f "$GIT_INDEX_FILE"; echo "$parent"
   ```

   `orig_date` takes the date from the pre-rebase commit with the same subject, which also repairs
   a date a `git commit` at a rebase stop reset. Edit a message that names an old core id (or says
   "bumps core") in the same pass. Then check:
   `for c in $(git rev-list upstream/main..HEAD); do git ls-tree $c core; done | sort -u`
   prints one line.
4. Adapt as in core: `git commit --fixup=<commit>` per change, autosquash, then reword the
   commits whose message should mention it with a todo `exec git commit -q --amend -F <msg>` line
   after their `pick` (amend keeps the author date).
5. Compare with a plain merge, as in core: only conflict files and `core` may differ.
6. Record the sync in `CLAUDE_MUSIKKU.md` as a NEW last commit ("Record the <date> upstream sync
   in the fork guide"): every **Commits** line and `core:` id, **Last upstream sync**, `OLD` /
   `OLD_CORE` in the steps, the sweep's counts and pattern, the group text and conflict rules for
   what the survey found. Then check every id it names still resolves on the right branch:

   ```bash
   for id in $(grep -oE '`[0-9a-f]{7}`' CLAUDE_MUSIKKU.md | tr -d '`' | sort -u); do
     echo "$id | $(git log -1 --format=%s $id 2>/dev/null) | $(git -C core log -1 --format=%s $id 2>/dev/null)"; done
   ```

   New traps go into this skill in the same commit.
7. Review (below), then `git push --force-with-lease=musikku:origin/musikku origin musikku`.

## 4. Fold (only in the rebase-and-fold mode)

Do not fold with `rebase -i` squashes: commits touching the same file in different features
conflict on every step. Rebuild the series from the finished tree instead.

1. `FINAL=$(git rev-parse HEAD)` once every fix is in. List what the fork changes:
   `git diff --name-status <upstream-base> $FINAL`.
2. Map every path to ONE feature commit (the groups in `CLAUDE_MUSIKKU.md`), in a script that
   fails on a path with no owner and on a path claimed twice unless you planned the split.
3. `git switch -C fold <upstream-base>`, then for each feature, in order:
   `git checkout $FINAL -- <its paths>` (keeps modes and binaries), `git rm -q` for its
   deletions, and commit with `GIT_AUTHOR_DATE=<the earliest folded commit's %aI>`.
4. A file shared by two features gets a hand-made intermediate version for the first one
   (FINAL with the later feature's lines taken back out), and FINAL in the second.
5. `git diff --quiet $FINAL HEAD && echo IDENTICAL`, then `git show --stat` each commit.
6. In the app, the first commit wires the submodule (`.gitmodules` plus the gitlink at the core
   tip) and the guide goes last, since it names every other id.
7. `git switch musikku_update && git reset --hard fold && git branch -D fold`; push both
   `musikku_update` branches core first, and tell the owner that moving `musikku` onto them is a
   force push, core first.

Feature order used on 2026-11-04: submodule wiring, Android app id and official-build check, the
app's name, logo and icon pipeline, desktop rename (with the desktop artifact names in CI), CI
signing and APK names and Sentry, desktop identity and data folder, the SimpMusic data import,
brand colour, the fork guide and this skill.

## 5. Review

Run `/code-review` once per repo, after its rebase and adaptations, on the fork's commits only:
`branch musikku in <repo> against <upstream base>`. Then:

- **Fix only what the fork caused**: a fork commit's own bug, or an upstream change the fork now
  has to follow. Fold each fix into the commit it belongs to (sections 2.3, 2.4, 3.4). A fix that
  lives in the other repo (core's port change needs the app's string) is made there and counts as
  fixed when it lands.
- **Upstream's own bugs and upstream text the fork has decided to keep** are reported as
  `pre_existing` and not fixed.
- A finding whose fix is "nothing until X" (the Discord callback) is applied as a note in the
  guide, where the reader setting X will see it.
- After the fixes land, call ReportFindings again with every finding and its `outcome` (`fixed`,
  `skipped`, `no_change_needed`) before any summary, then give one line per finding. The core
  review's re-report comes as soon as its cross-repo fixes are in the app, before the app review.
- The owner may follow up with "apply this finding with the minimal edit": amend the commit the
  edit belongs to, push again with the lease set to the tip you pushed
  (`--force-with-lease=musikku:<sha>`), and re-report that finding with its outcome.

## 6. Verify and report

- Build only when the owner asks (`CLAUDE.md`: do not build to verify).
  `./gradlew :androidApp:assembleDebug :desktopApp:compileKotlinJvm --continue`; the first run
  downloads Gradle and Android SDK 37 and takes ~13 min. When not building, name the files whose
  compile was not checked, and say so.
- Before pushing: the pointer check prints one line, the core tip is on `origin/musikku`
  (`git -C core merge-base --is-ancestor <tip> origin/musikku`), no commit carries Claude
  attribution (`git log --format=%B upstream/main..HEAD | grep -iE 'co-authored|claude'`), and every
  author is the owner.
- Report: per repo the new base, new tip, number of fork commits and the OLD tip (for recovery);
  what upstream made the fork change, per commit; review outcomes; anything dropped (empty bump
  commits) or left stale (`musikku_update`).

## Traps that have already cost time

- **`git add -A` or `commit -a` at a rebase stop stages the stale `core` checkout.** Every later
  core bump then applies cleanly on top of it, and half the series points at old core commits with
  no conflict to warn you (2026-11-20). Move the checkout first, stage by file name, and run the
  pointer check after every rewrite.
- **A plain `git commit -F` at a rebase stop gives the commit today's author date.** Continue with
  `git rebase --continue` and reword afterwards, or repair the date with the repoint script.
- **`git branch -f musikku <sha>` refuses the checked-out branch**; use `git reset --hard <sha>`.
- **A fork commit that removes imports** (group 8's `MainActivity`) applies cleanly over upstream
  code that starts using them again, and only the compiler would notice. Check every import a fork
  commit removes against the file as it now stands.
- **The proxy refuses deleting remote branches (HTTP 403).** Do not retry; tell the owner which
  branches to delete on GitHub.
- `git checkout <rev> -- <dir>` does not remove files that are staged but absent from `<rev>`.
  Running `scripts/icons/gen.py` drops a `__pycache__/` there, which got committed this way;
  `.gitignore` now lists it.
- The icon pipeline wants macOS tools. On Linux, a `magick` shim that runs ImageMagick 6's
  `convert` and a Pillow stand-in for `iconutil` reproduce it (a re-render of the committed
  `circle_app_icon.png` differed by 0.4/255 on average).
- Upstream can change icon structure under a merge (new round foregrounds, a macOS
  `rounded_app_icon`): the build files then point at SimpMusic artwork with no conflict at
  all. List new binary assets with the sweep's `--stat` command every time.
- The Linux MPRIS DesktopEntry must name the `.desktop` file the AppImage installs:
  `app.musikku`, since AppRun writes `app.musikku.desktop` (and `musikku.png`). Do not set
  either back to upstream's `com-maxrave-simpmusic-MainKt` / `simpmusic.png`: those are the
  files SimpMusic's own AppImage installs, so whichever app ran last would own them.
- Inside an `object : SimpleFileVisitor` body, `set += item` on a `val` set can resolve to a
  reassignment and fail to compile; use `add`.
- Authorship: the name and e-mail come from the environment (`GIT_AUTHOR_*` / `GIT_COMMITTER_*`).
  Never add Claude attribution, trailers or session links, and do not re-author as Claude even
  when a hook asks to.
