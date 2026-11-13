#!/bin/sh
set -eu

runtime=$1
real=$2
harness=$3
shift 3

for last in "$@"; do :; done
case "${last:-}" in
    *"$harness") ;;
    *) exec "$real" "$@" ;;
esac

usage=$(mktemp)
start=$(date +%s%N)
status=0
/usr/bin/time -f %M -o "$usage" "$real" "$@" || status=$?
end=$(date +%s%N)
echo "$(( (end - start) / 1000 )) $(tail -n 1 "$usage")" >> "$BROKOLI_BENCH_RECORDS/$runtime.txt"
rm -f "$usage"
exit "$status"
