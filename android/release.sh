#!/bin/sh -e
# The deploy: test, build, and publish the APK as a GitHub release.
#
#   android/release.sh 0.12 "what changed" [notes.md]
#
# The release IS the deployment for a sideloaded prototype, so a build that
# only lands in app/build/outputs has not shipped. This is the one command
# that counts as shipping.

cd "$(dirname "$0")"

version="$1"
title="$2"
notes="$3"
[ -n "$version" ] && [ -n "$title" ] || {
  echo "usage: android/release.sh <version> <title> [notes.md]" >&2
  exit 1
}

# A release names a commit everyone can see. Refuse anything else.
[ -z "$(git status --porcelain)" ] || { echo "working tree is dirty" >&2; exit 1; }
git merge-base --is-ancestor HEAD "@{upstream}" 2>/dev/null || {
  echo "HEAD is not pushed" >&2
  exit 1
}

gradle -q :app:testDebugUnitTest :app:assembleRelease

tag="radial-v$version"
apk="$(mktemp -d)/stfst-radial-v$version.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"

if [ -n "$notes" ]; then
  gh release create "$tag" "$apk" --target master -t "radial v$version: $title" -F "$notes"
else
  gh release create "$tag" "$apk" --target master -t "radial v$version: $title" --generate-notes
fi
