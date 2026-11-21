#!/usr/bin/env bash
# Prints the draft release notes for a Musikku release tag, built from the commit subjects between
# the previous release tag and this one.
#
# usage: scripts/musikku-release-notes.sh <tag> [<rev>]
#   <tag>  the release tag, x.x.x or x.x.x.x (never vx.x.x: those are upstream's)
#   <rev>  the commit to describe, default <tag> (lets the script be tried before tagging)
#
# The previous tag is the highest x.x.x / x.x.x.x tag below <tag> in version order, not the nearest
# ancestor: the upstream sync rebases musikku, so an older release tag is usually not an ancestor of
# the new one. Commits are split into the fork's own and upstream's when an upstream ref exists
# (UPSTREAM_REFS, default refs/remotes/upstream/main refs/remotes/upstream/dev; refs that do not
# exist are skipped). A rebase carries every fork commit again under a new id, so a fork commit
# whose subject the previous release already had is left out. Each line is the subject alone: no
# commit id, and no PR or issue number (#N), since the fork's and upstream's numbers share one form.
set -euo pipefail

TAG=${1:?usage: $0 <tag> [<rev>]}
CUR=${2:-$TAG}
REPO=${GITHUB_REPOSITORY:-cuong-tran/Musikku}
MAX_UPSTREAM=300
TAG_RE='^[0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?$'

if ! [[ $TAG =~ $TAG_RE ]]; then
  echo "error: '$TAG' is not x.x.x or x.x.x.x" >&2
  exit 1
fi
git rev-parse -q --verify "$CUR^{commit}" >/dev/null || { echo "error: no commit '$CUR'" >&2; exit 1; }

# The highest release tag below TAG. sort -V orders 2.5.0 < 2.5.0.1 < 2.5.1 < 2.10.0.
prev=$({ git tag --list | grep -E "$TAG_RE" | grep -vxF "$TAG" || true; echo "$TAG"; } \
  | sort -V | grep -B1 -xF "$TAG" | head -1)
[ "$prev" = "$TAG" ] && prev=

upstream=()
for ref in ${UPSTREAM_REFS:-refs/remotes/upstream/main refs/remotes/upstream/dev}; do
  git rev-parse -q --verify "$ref^{commit}" >/dev/null && upstream+=("$ref")
done

# Subjects over the given range, oldest first, without PR or issue numbers: "Fix X (#12)",
# "Fix X (#12, #13)", "fix #12 in Y" and "fix owner/repo#12 in Y" become "Fix X", "Fix X",
# "fix in Y" and "fix in Y". A number must end the token, so a hex colour (#8ECAE6) stays.
REF='([[:alnum:]_.-]+/[[:alnum:]_.-]+)?#[0-9]+'
subjects() {
  git log --no-merges --reverse --format=%s "$@" \
    | sed -E "s~[[:space:]]*\\((${REF}[,[:space:]]*)+\\)~~g; s~[[:space:]]*${REF}([^[:alnum:]_]|\$)~\\2~g; s~[[:space:]]{2,}~ ~g"
}

# Subjects of the fork commits the previous release already had.
seen=$(mktemp)
trap 'rm -f "$seen"' EXIT
if [ -n "$prev" ]; then
  subjects "$prev" --not "$CUR" ${upstream[@]+"${upstream[@]}"} >"$seen"
fi

# One "- subject" line per commit in the given range.
list() { subjects "$@" | sed 's/^/- /'; }

fork_range=("$CUR")
[ -n "$prev" ] && fork_range+=("^$prev")
fork=$(list "${fork_range[@]}" --not ${upstream[@]+"${upstream[@]}"} \
  | while IFS= read -r line; do
      subject=${line#- }
      grep -qxF -- "$subject" "$seen" || printf '%s\n' "$line"
    done)

echo "## Musikku"
echo
if [ -n "$fork" ]; then printf '%s\n' "$fork"; else echo "No changes of the fork's own."; fi

if [ "${#upstream[@]}" -gt 0 ]; then
  # Every upstream commit cur contains is an ancestor of one of these merge bases.
  bases=()
  for ref in "${upstream[@]}"; do
    while IFS= read -r b; do bases+=("$b"); done < <(git merge-base --all "$CUR" "$ref" || true)
  done
  # A first release has nothing to compare upstream's commits with, so it gets no upstream section.
  if [ "${#bases[@]}" -gt 0 ] && [ -n "$prev" ]; then
    echo
    echo "## Upstream SimpMusic"
    echo
    ups=$(list "${bases[@]}" "^$prev")
    count=$(printf '%s' "$ups" | grep -c '' || true)
    if [ "$count" -eq 0 ]; then
      echo "No new upstream commits."
    elif [ "$count" -gt "$MAX_UPSTREAM" ]; then
      printf '%s\n' "$ups" | head -n "$MAX_UPSTREAM"
      echo "- …and $((count - MAX_UPSTREAM)) more, see the full changelog below."
    else
      printf '%s\n' "$ups"
    fi
  fi
fi

echo
if [ -n "$prev" ]; then
  echo "**Full changelog**: https://github.com/${REPO}/compare/${prev}...${TAG}"
else
  echo "First release: no earlier x.x.x tag to compare with."
fi
