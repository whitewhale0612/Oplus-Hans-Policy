#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
variant="${1:-debug}"

case "$variant" in
    debug)
        gradle_task="assembleDebug"
        apk_path="app/build/outputs/apk/debug/app-debug.apk"
        output_suffix="debug"
        ;;
    release)
        gradle_task="assembleRelease"
        apk_path="app/build/outputs/apk/release/app-release-unsigned.apk"
        output_suffix="release-unsigned"
        ;;
    *)
        echo "Usage: $0 [debug|release]" >&2
        exit 2
        ;;
esac

cd "$project_dir"
chmod +x gradlew
./gradlew --no-daemon lintDebug "$gradle_task"

version_name="$(sed -n "s/^[[:space:]]*versionName '\([^']*\)'.*/\1/p" app/build.gradle)"
if [[ -z "$version_name" ]]; then
    echo "Unable to read versionName from app/build.gradle" >&2
    exit 1
fi

mkdir -p dist
output_path="dist/HansPolicy-v${version_name}-${output_suffix}.apk"
cp "$apk_path" "$output_path"

echo "Built $output_path"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$output_path"
else
    shasum -a 256 "$output_path"
fi

if [[ "$variant" == "release" ]]; then
    echo "Release output is unsigned. Sign it before installation or distribution."
fi
