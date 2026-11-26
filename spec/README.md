# spec

`ares-openapi.json` is ARES's [OpenAPI document](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs)
as it was when this library was last checked against it, sorted by key (`jq -S`), so a diff shows only what really
moved. The library does not read it: the client maps the JSON by hand, and the tests run on their own fixtures in
[src/test/resources/ares/](../modules/ares-api/src/test/resources/ares/README.md). It is the baseline the
comparison needs, since a change can only be found against what came before.

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

Then change the client, its fixtures and its tests where a field it reads moved, and commit the refreshed copy in
the same pull request. A copy refreshed without that review hides the change from the next comparison.
