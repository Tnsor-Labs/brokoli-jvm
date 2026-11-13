#!/usr/bin/env bash
set -euo pipefail

usage="usage: python-reference/vendor.sh <brokoli-sdk checkout> <brokoli-typescript checkout> [output directory]"
sdk="$(cd "${1:?$usage}" && pwd)"
typescript="$(cd "${2:?$usage}" && pwd)"
here="$(cd "$(dirname "$0")" && pwd)"
target="${3:-$here}"
mkdir -p "$target/fixtures" "$target/expected"
out="$(cd "$target" && pwd)"
fixtures=tests/differential/fixtures

printed="$(mktemp)"
trap 'rm -f "$printed"' EXIT

for name in $(git -C "$typescript" ls-tree --name-only "HEAD:$fixtures" | sed -n 's/\.py$//p'); do
    git -C "$typescript" show "HEAD:$fixtures/$name.py" > "$out/fixtures/$name.py"
    (cd "$out/fixtures" && PYTHONPATH="$sdk" python3 "$name.py") > "$printed"
    last="$(tail -n 1 "$printed")"
    case "$last" in
        "DIGEST sha256:"*) ;;
        *) echo "$name.py did not end with a DIGEST line: $last" >&2; exit 1 ;;
    esac
    head -n -1 "$printed" > "$out/expected/$name.ir.json"
    echo "${last#DIGEST }" > "$out/expected/$name.digest"
done
