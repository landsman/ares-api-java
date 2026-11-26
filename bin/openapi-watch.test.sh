#!/usr/bin/env bash
# What openapi-watch.sh would get wrong silently: key order read as a change (an issue every month), a real change
# read as none, an issue that does not say what changed, a second issue opened instead of the first edited, an issue
# opened when nothing changed, or a failed fetch read as "no change". curl and gh are stubs.
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/openapi-watch.sh"
t=$(mktemp -d)
trap 'rm -rf "$t"' EXIT
rc=0

ok() { echo "ok   $1"; }
bad() {
    echo "FAIL $1"
    rc=1
}
# expect <behaviour> <command...>: the command succeeds; refuse: it fails.
expect() {
    local what=$1
    shift
    if "$@" >/dev/null 2>&1; then ok "$what"; else bad "$what"; fi
}
refuse() {
    local what=$1
    shift
    if "$@" >/dev/null 2>&1; then bad "$what"; else ok "$what"; fi
}

# A name in the summary is in backticks, Markdown's inline code.
q='`'

cat >"$t/old.json" <<'JSON'
{"info":{"title":"ARES 1.4.0","version":"1.4"},"servers":[{"url":"/rest"}],"paths":{"/a":{"get":{}},"/b":{"get":{}}},
 "components":{"schemas":{"S":{"type":"object"},"T":{"type":"string"}}}}
JSON
cat >"$t/reordered.json" <<'JSON'
{"components":{"schemas":{"T":{"type":"string"},"S":{"type":"object"}}},"servers":[{"url":"/rest"}],
 "paths":{"/b":{"get":{}},"/a":{"get":{}}},"info":{"version":"1.4","title":"ARES 1.4.0"}}
JSON
cat >"$t/new.json" <<'JSON'
{"info":{"title":"ARES 1.5.0","version":"1.5"},"servers":[{"url":"/rest/v2"}],
 "paths":{"/a":{"get":{"deprecated":true}},"/c":{"get":{}}},
 "components":{"schemas":{"S":{"type":"array"},"U":{"type":"string"}},"parameters":{"P":{}}}}
JSON
# Only the version and the description differ: nothing in paths or schemas moved.
cat >"$t/version-only.json" <<'JSON'
{"info":{"title":"ARES 1.4.0","version":"1.4.1","description":"new"},"servers":[{"url":"/rest"}],
 "paths":{"/a":{"get":{}},"/b":{"get":{}}},"components":{"schemas":{"S":{"type":"object"},"T":{"type":"string"}}}}
JSON

refuse "the same document in another key order is unchanged" "$script" changed "$t/old.json" "$t/reordered.json"
expect "an added path is a change" "$script" changed "$t/old.json" "$t/new.json"

"$script" summary "$t/old.json" "$t/new.json" >"$t/summary.md"
expect "the summary names the new title" grep -q "title: ARES 1.4.0 → ARES 1.5.0" "$t/summary.md"
expect "the summary names the new version" grep -q "version: 1.4 → 1.5" "$t/summary.md"
expect "the summary names an added path" grep -qF "paths added: ${q}/c${q}" "$t/summary.md"
expect "the summary names a removed path" grep -qF "paths removed: ${q}/b${q}" "$t/summary.md"
expect "the summary names a changed path" grep -qF "paths changed: ${q}/a${q}" "$t/summary.md"
expect "the summary names an added schema" grep -qF "schemas added: ${q}U${q}" "$t/summary.md"
expect "the summary names a removed schema" grep -qF "schemas removed: ${q}T${q}" "$t/summary.md"
expect "the summary names a changed schema" grep -qF "schemas changed: ${q}S${q}" "$t/summary.md"
expect "the summary names other keys that changed" grep -qF "other keys changed: ${q}servers${q}, ${q}components.parameters${q}" "$t/summary.md"

"$script" summary "$t/old.json" "$t/version-only.json" >"$t/version.md"
expect "a change of info.version alone is named" grep -q "version: 1.4 → 1.4.1" "$t/version.md"
expect "a change elsewhere in info is named" grep -qF "info changed besides title and version: ${q}info${q}" "$t/version.md"
refuse "a change of info.version alone names no title change" grep -q "title:" "$t/version.md"

# curl serves $SERVED; gh logs its arguments, keeps the --body-file it was given and answers `issue list` with $OPEN.
mkdir "$t/bin"
cat >"$t/bin/curl" <<'SH'
#!/usr/bin/env bash
while [ $# -gt 0 ]; do
    if [ "$1" = -o ]; then cp "$SERVED" "$2" && exit 0; fi
    shift
done
exit 1
SH
cat >"$t/bin/gh" <<'SH'
#!/usr/bin/env bash
echo "$*" >>"$GH_LOG"
prev=""
for a in "$@"; do
    if [ "$prev" = --body-file ]; then cp "$a" "$GH_LOG.body"; fi
    prev=$a
done
if [ "$1 $2" = "issue list" ]; then echo "${OPEN:-[]}"; fi
SH
chmod +x "$t/bin/curl" "$t/bin/gh"
export GH_LOG="$t/gh.log" ARES_OPENAPI_STORED="$t/old.json" PATH="$t/bin:$PATH"

: >"$GH_LOG"
SERVED="$t/reordered.json" "$script" report >/dev/null
refuse "an unchanged document opens no issue" grep -q "issue create\|issue edit" "$GH_LOG"

: >"$GH_LOG"
SERVED="$t/new.json" OPEN="[]" "$script" report >/dev/null
expect "a change with no open issue opens one" grep -q "^issue create" "$GH_LOG"
expect "the issue says what changed" grep -qF "paths added: ${q}/c${q}" "$GH_LOG.body"
expect "the issue carries the prompt for Claude" grep -q "^@claude ARES changed its OpenAPI document" "$GH_LOG.body"

: >"$GH_LOG"
title="ares: the register's OpenAPI document changed"
SERVED="$t/new.json" OPEN="[{\"number\":7,\"title\":\"$title\"}]" "$script" report >/dev/null
expect "a change with an open issue edits that one" grep -q "^issue edit 7" "$GH_LOG"
refuse "a change with an open issue opens no second one" grep -q "issue create" "$GH_LOG"

: >"$GH_LOG"
SERVED="$t/new.json" OPEN="[{\"number\":8,\"title\":\"$title, again\"}]" "$script" report >/dev/null
refuse "an issue that only resembles the title is not edited" grep -q "^issue edit" "$GH_LOG"
expect "an issue that only resembles the title gets one of its own" grep -q "^issue create" "$GH_LOG"

refuse "a document that cannot be fetched fails the run" env SERVED="$t/missing.json" "$script" report

exit $rc
