#!/usr/bin/env bash
# Tells me when ARES changes its OpenAPI document, against the copy in .docs/ares-openapi.json.
#
#   openapi-watch.sh changed <stored> <fetched>   exit 0 when the two differ, key order and whitespace aside
#   openapi-watch.sh summary <stored> <fetched>   what changed, as Markdown: version, paths and schemas
#   openapi-watch.sh refresh                      overwrite the stored copy with today's document
#   openapi-watch.sh report                       fetch it; when it changed, open or update one GitHub issue
#
# `report` needs gh and a token with issues: write. A document that cannot be fetched fails the run rather than
# reading as "no change".
set -euo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
url=${ARES_OPENAPI_URL:-https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs}
stored=${ARES_OPENAPI_STORED:-$dir/../.docs/ares-openapi.json}
title="ares: the register's OpenAPI document changed"

changed() { ! cmp -s <(jq -S . "$1") <(jq -S . "$2"); }

# Every list is "added" or "removed" by name; a path or schema in both whose body differs is "changed". What lies
# outside paths and schemas is named by its key, so an issue never says only "something changed".
summary() {
    jq -rn --slurpfile old "$1" --slurpfile new "$2" '
        $old[0] as $o | $new[0] as $n
        | def names(f): [$o, $n] | map(f // {} | keys);
        def list(what; xs): if (xs | length) == 0 then empty else "- \(what): " + (xs | map("`\(.)`") | join(", ")) end;
        def line(what; f): if ($o | f) != ($n | f) then "- \(what): \($o | f) → \($n | f)" else empty end;
        def differing(a; b; skip): [(a | keys) + (b | keys) | unique[] | select(skip | not) | select(a[.] != b[.])];
        names(.paths) as [$op, $np]
        | names(.components.schemas) as [$os, $ns]
        | [ line("title"; .info.title),
            line("version"; .info.version),
            list("paths added"; $np - $op),
            list("paths removed"; $op - $np),
            list("paths changed"; [$op[] | select(. as $p | $np | index($p)) | select($o.paths[.] != $n.paths[.])]),
            list("schemas added"; $ns - $os),
            list("schemas removed"; $os - $ns),
            list("schemas changed"; [$os[] | select(. as $s | $ns | index($s))
                | select($o.components.schemas[.] != $n.components.schemas[.])]),
            list("info changed besides title and version";
                if ($o.info | del(.title, .version)) != ($n.info | del(.title, .version)) then ["info"] else [] end),
            list("other keys changed"; differing($o; $n; . == "paths" or . == "components" or . == "info")
                + (differing($o.components // {}; $n.components // {}; . == "schemas") | map("components.\(.)"))) ]
        | if length == 0 then "- nothing this summary names; see the diff after a refresh" else join("\n") end'
}

fetch() {
    local out=$1
    curl -fsSL --retry 2 "$url" -o "$out.raw"
    jq -S . "$out.raw" >"$out"
    rm -f "$out.raw"
}

body() {
    # Assigned first: a jq failure inside the heredoc would not stop the script, and the issue would say nothing.
    local changes
    changes=$(summary "$stored" "$1")
    cat <<MD
ARES's [OpenAPI document]($url) is no longer the copy in \`.docs/ares-openapi.json\`.

$changes

To have Claude handle it, comment on this issue with:

\`\`\`\`markdown
@claude ARES changed its OpenAPI document; this issue lists what moved. Run \`bin/openapi-watch.sh refresh\` and read
\`git diff .docs/ares-openapi.json\`. Then decide, for each change, whether it touches what this library reads:
the endpoints \`ekonomicke-subjekty/{ico}\`, \`-nrpzs/{ico}\`, \`-rs/{ico}\` and \`-rzp/{ico}\`, and the fields
\`AresClient\` maps (see .docs/ares-api.md). Where it does, change the client, its fixtures under
modules/ares-api/src/test/resources/ares/ and its tests, the README and .docs/ares-api.md, so a renamed or removed
field fails a test rather than turning into a quiet null. Run \`make build\`. Open one pull request with the refreshed
copy and those changes; its body says which changes matter to the library and which do not, and why. If none
matter, the pull request carries the refreshed copy alone and says so.
\`\`\`\`
MD
}

case "${1:-}" in
changed) changed "${2:?stored}" "${3:?fetched}" ;;
summary) summary "${2:?stored}" "${3:?fetched}" ;;
refresh) fetch "$stored" ;;
report)
    t=$(mktemp -d)
    trap 'rm -rf "$t"' EXIT
    fetch "$t/fetched.json"
    if ! changed "$stored" "$t/fetched.json"; then
        echo "unchanged since the stored copy"
        exit 0
    fi
    body "$t/fetched.json" >"$t/body.md" || exit 1
    # One issue, edited rather than duplicated, so a month of the same change is not a month of new issues.
    # The search is fuzzy, so the title is compared exactly: an issue that only resembles it is left alone.
    n=$(gh issue list --state open --search "\"$title\" in:title" --json number,title |
        jq -r --arg title "$title" 'map(select(.title == $title)) | .[0].number // empty')
    if [ -n "$n" ]; then gh issue edit "$n" --body-file "$t/body.md"; else gh issue create --title "$title" --body-file "$t/body.md"; fi
    ;;
*)
    sed -n '2,8p' "$0" >&2
    exit 2
    ;;
esac
