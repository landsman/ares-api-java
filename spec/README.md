# spec

`ares-openapi.json` is ARES's [OpenAPI document](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs)
as it was when this library was last checked against it, sorted by key (`jq -S`), so a diff shows only what really
moved. The shipped jar does not read it: the client maps the JSON by hand. Two things do:

- **The monthly comparison**, below, which needs a baseline, since a change can only be found against what came
  before.
- **`AresSpecTest`**, which holds the client to it: every field the client reads has to be here with the type the
  client reads it as, and every fixture in
  [src/test/resources/ares/](../modules/ares-api/src/test/resources/ares/README.md) may hold only fields it has.

## The comparison

[openapi-watch.yml](../.github/workflows/openapi-watch.yml) runs on the first of every month at 06:23 UTC, and on
demand (*Run workflow*). It calls [bin/openapi-watch.sh](../bin/openapi-watch.sh) `report`, which

1. fetches the document and sorts it the same way;
2. compares it with this copy, so key order and whitespace never count as a change;
3. when they differ, opens one issue, or edits the open one with the same title, listing the title and version,
   the paths and schemas added, removed or changed, and any other key that moved.

A fetch that fails fails the run, rather than reading as "no change". The run never touches this copy.

## When an issue opens

The issue ends with a prompt; posted as a comment on the issue, it has Claude refresh the copy, sort the changes
by whether the client reads them, and open one pull request. By hand:

```sh
make openapi                     # overwrite ares-openapi.json with today's document
git diff spec/ares-openapi.json  # what moved
```

`make build` then fails in `AresSpecTest` on any field the client reads that moved. Change the client, its fixtures
and its tests to match, and commit the refreshed copy in the same pull request. A copy refreshed without that review hides the change from the next comparison.
