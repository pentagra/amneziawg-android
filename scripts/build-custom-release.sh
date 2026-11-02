#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: ./scripts/build-custom-release.sh VERSION_CODE VERSION_NAME
Example: ./scripts/build-custom-release.sh 13 3.0.1-custom.2

Requires Android SDK, JDK 17 and the existing personal release.p12 keystore.
The script does not modify upstream Gradle files or the signing key.
USAGE
}

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi
version_code=$1
version_name=$2
if [[ ! $version_code =~ ^[1-9][0-9]*$ ]] || [[ ! $version_name =~ ^[0-9A-Za-z][0-9A-Za-z._+-]*$ ]]; then
  usage
  exit 2
fi

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

if [[ "$(git branch --show-current)" != 'custom/endpoint-port-randomization' ]]; then
  echo 'ERROR: expected branch custom/endpoint-port-randomization' >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo 'ERROR: commit or clean your working tree before creating a signed APK.' >&2
  git status --short >&2
  exit 1
fi

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
build_tools="$ANDROID_HOME/build-tools/36.0.0"
zipalign="$build_tools/zipalign"
apksigner="$build_tools/apksigner"
keystore="${AMNEZIAWG_KEYSTORE:-$HOME/.local/share/amneziawg-signing/release.p12}"
output_dir="${AMNEZIAWG_APK_OUTPUT:-$HOME/amneziawg-apks}"
unsigned_apk='ui/build/outputs/apk/release/ui-release-unsigned.apk'

for executable in "$zipalign" "$apksigner"; do
  if [[ ! -x $executable ]]; then
    echo "ERROR: tool is missing or not executable: $executable" >&2
    exit 1
  fi
done
if [[ ! -f $keystore ]]; then
  echo "ERROR: signing keystore not found: $keystore" >&2
  exit 1
fi

printf 'Building versionCode=%s versionName=%s from commit %s\n' \
  "$version_code" "$version_name" "$(git rev-parse --short=12 HEAD)"
printf '%s\n' 'Reminder: versionCode must exceed the installed custom APK versionCode.'

./gradlew --no-daemon :tunnel:testReleaseUnitTest \
  --tests 'org.amnezia.awg.backend.EndpointPortRandomizerTest'
./gradlew --no-daemon \
  "-PamneziawgVersionCode=$version_code" \
  "-PamneziawgVersionName=$version_name" \
  :ui:assembleRelease

if [[ ! -f $unsigned_apk ]]; then
  echo "ERROR: expected release APK not found: $unsigned_apk" >&2
  exit 1
fi

mkdir -p -- "$output_dir"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT
aligned_apk="$work_dir/aligned.apk"
output_apk="$output_dir/amneziawg-custom-v${version_code}-$(git rev-parse --short=12 HEAD).apk"

"$zipalign" -P 16 -f 4 "$unsigned_apk" "$aligned_apk"
"$zipalign" -c -P 16 4 "$aligned_apk"
"$apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias amneziawg-custom \
  --out "$output_apk" \
  "$aligned_apk"
"$apksigner" verify --verbose --print-certs "$output_apk"
sha256sum "$output_apk"
echo "Signed APK: $output_apk"
