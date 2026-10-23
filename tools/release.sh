#!/usr/bin/env bash
# Baut das Release-APK und veröffentlicht es als GitHub-Release (Tag launcher-android-<versionCode>).
# Die App findet es über de.letzgo.launcher.update.AppUpdate.
#
# Aufruf: tools/release.sh "Release-Notizen"   (oder Notizen per Datei: tools/release.sh -F notes.md)
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Arbeitsverzeichnis nicht sauber – erst committen." >&2
  exit 1
fi
git push -q

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home}"
./gradlew -q :app:assembleRelease

apk=app/build/outputs/apk/release/app-release.apk
aapt2="$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/ | sort -V | tail -1)aapt2"
badging="$("$aapt2" dump badging "$apk" | sed -n 1p)"
code="$(sed -E "s/.*versionCode='([0-9]+)'.*/\1/" <<<"$badging")"
name="$(sed -E "s/.*versionName='([^']+)'.*/\1/" <<<"$badging")"
tag="launcher-android-$code"
out="build/release/letzgo-launcher-$name-$code.apk"
mkdir -p build/release
cp "$apk" "$out"

if [[ "${1:-}" == "-F" ]]; then
  notes=(--notes-file "$2")
else
  notes=(--notes "${1:-}")
fi
gh release create "$tag" "$out" \
  --repo 1letzgo/letzgo-launcher \
  --target "$(git rev-parse HEAD)" \
  --title "letzgo-launcher $name ($code) – Android" \
  "${notes[@]}"
echo "Veröffentlicht: $tag ($out)"
