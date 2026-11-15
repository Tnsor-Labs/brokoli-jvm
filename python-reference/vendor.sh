#!/usr/bin/env bash
set -euo pipefail

usage="usage: python-reference/vendor.sh <brokoli-sdk checkout> <brokoli-typescript checkout> [output directory]"
sdk="$(cd "${1:?$usage}" && pwd)"
typescript="$(cd "${2:?$usage}" && pwd)"
here="$(cd "$(dirname "$0")" && pwd)"
target="${3:-$here}"
mkdir -p "$target/fixtures" "$target/expected" "$target/local/fixtures" "$target/local/expected"
out="$(cd "$target" && pwd)"
fixtures=tests/differential/fixtures

printed="$(mktemp)"
trap 'rm -f "$printed"' EXIT

print_fixture() {
    local directory="$1" name="$2"
    (cd "$out/$directory/fixtures" && PYTHONPATH="$sdk" python3 "$name.py") > "$printed"
    local last
    last="$(tail -n 1 "$printed")"
    case "$last" in
        "DIGEST sha256:"*) ;;
        *) echo "$directory/fixtures/$name.py did not end with a DIGEST line: $last" >&2; exit 1 ;;
    esac
    head -n -1 "$printed" > "$out/$directory/expected/$name.ir.json"
    echo "${last#DIGEST }" > "$out/$directory/expected/$name.digest"
}

for name in $(git -C "$typescript" ls-tree --name-only "HEAD:$fixtures" | sed -n 's/\.py$//p'); do
    git -C "$typescript" show "HEAD:$fixtures/$name.py" > "$out/fixtures/$name.py"
    print_fixture . "$name"
done

for fixture in "$here/local/fixtures"/*.py; do
    name="$(basename "$fixture" .py)"
    if [ "$out" != "$here" ]; then
        cp "$fixture" "$out/local/fixtures/$name.py"
    fi
    print_fixture local "$name"
done
