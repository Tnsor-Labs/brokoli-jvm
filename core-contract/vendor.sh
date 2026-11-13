#!/usr/bin/env bash
set -euo pipefail

core="$(cd "${1:?usage: core-contract/vendor.sh <core checkout> [output directory]}" && pwd)"
here="$(cd "$(dirname "$0")" && pwd)"
target="${2:-$here}"
mkdir -p "$target"
out="$(cd "$target" && pwd)"

while IFS= read -r path; do
    mkdir -p "$out/$(dirname "$path")"
    git -C "$core" show "HEAD:$path" > "$out/$path"
done < "$here/VENDORED"

toolchain="$(awk '$1 == "toolchain" { print $2 }' "$core/go.mod")"
build="$(mktemp -d)"
cli="$build/brokoli$(cd "$core" && GOTOOLCHAIN="${toolchain:-auto}" go env GOEXE)"
(cd "$core" && GOTOOLCHAIN="${toolchain:-auto}" go build -o "$cli" .)

mkdir -p "$out/bundles"
for args in "$here"/bundles/*.args; do
    name="$(basename "$args" .args)"
    (cd "$here/bundles" && xargs "$cli" bundle jvm --out "$out/bundles/$name.tar.gz" < "$args" > /dev/null)
done

assembler="$(mktemp -d "$core/brokoli-jvm-assemble.XXXXXX")"
trap 'rm -rf "$assembler"' EXIT
cp "$here/bundles/python/assemble.go" "$assembler/main.go"
(cd "$core" && GOTOOLCHAIN="${toolchain:-auto}" go run "./$(basename "$assembler")" "$here/bundles/python" "$out/bundles")
