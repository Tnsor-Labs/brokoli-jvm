#!/usr/bin/env bash
set -euo pipefail

usage="usage: python-reference/check.sh <brokoli-sdk checkout> <brokoli-typescript checkout>"
sdk="$(cd "${1:?$usage}" && pwd)"
typescript="$(cd "${2:?$usage}" && pwd)"
here="$(cd "$(dirname "$0")" && pwd)"
fresh="$(mktemp -d)"
trap 'rm -rf "$fresh"' EXIT
"$here/vendor.sh" "$sdk" "$typescript" "$fresh"

status=0
for directory in fixtures expected; do
    for file in "$fresh/$directory"/*; do
        path="$directory/$(basename "$file")"
        if [ ! -f "$here/$path" ]; then
            echo "in the SDKs but not vendored: $path"
            status=1
        elif ! cmp -s "$here/$path" "$file"; then
            echo "differs from the SDKs: $path"
            status=1
        fi
    done
    for file in "$here/$directory"/*; do
        path="$directory/$(basename "$file")"
        if [ ! -f "$fresh/$path" ]; then
            echo "vendored but no longer in the SDKs: $path"
            status=1
        fi
    done
done

if [ "$status" -eq 0 ]; then
    echo "python-reference matches brokoli-sdk $(git -C "$sdk" rev-parse HEAD) and brokoli-typescript $(git -C "$typescript" rev-parse HEAD)"
fi
exit "$status"
