#!/usr/bin/env bash
set -euo pipefail

core="$(cd "${1:?usage: core-contract/check.sh <core checkout>}" && pwd)"
here="$(cd "$(dirname "$0")" && pwd)"
fresh="$(mktemp -d)"
"$here/vendor.sh" "$core" "$fresh"

status=0
while IFS= read -r path; do
    if ! cmp -s "$here/$path" "$fresh/$path"; then
        echo "differs from core: $path"
        status=1
    fi
done < "$here/VENDORED"

for directory in $(grep '/fixtures/' "$here/VENDORED" | sed 's|/[^/]*$||' | sort -u); do
    for file in "$core/$directory"/*.json; do
        path="$directory/$(basename "$file")"
        if ! grep -qxF "$path" "$here/VENDORED"; then
            echo "in core but not vendored: $path"
            status=1
        fi
    done
done

for args in "$here"/bundles/*.args; do
    name="$(basename "$args" .args)"
    if [ ! -f "$here/bundles/$name.tar.gz" ]; then
        echo "no reference bundle for $name.args"
        status=1
    elif ! cmp -s <(gzip -dc "$here/bundles/$name.tar.gz") <(gzip -dc "$fresh/bundles/$name.tar.gz"); then
        echo "differs from core's brokoli bundle jvm, uncompressed: bundles/$name.tar.gz"
        status=1
    fi
done

if [ "$status" -eq 0 ]; then
    echo "core-contract matches core at $(git -C "$core" rev-parse HEAD)"
fi
exit "$status"
