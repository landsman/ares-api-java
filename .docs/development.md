# Development

`make build` checks formatting, compiles with Error Prone and NullAway, runs the tests twice (on the Jackson in
the build and on Jackson 3.0.4, Spring Boot 4.0.0's, so an API newer than that fails here rather than in a
caller), checks the declared dependencies and packages the jar. `make` lists the targets. A JDK 21 or newer is
enough; `mise.toml` pins the one used here.

The tests run against fixtures in [src/test/resources/ares/](../modules/ares-api/src/test/resources/ares/README.md)
and the JDK's own HTTP server, never a live account.

After a dependency changes, `make lock` rewrites `modules/ares-api/gradle.lockfile`, which the vulnerability scan
reads; the build fails while it is stale.

## Releasing

Only a tag releases: merging to `main` publishes nothing. `make release` asks for the tag, suggesting the next
patch, refuses one that exists or a checkout that is not a clean `origin/main`, tags, waits for
[release.yml](../.github/workflows/release.yml) to publish the jar to GitHub Packages and writes the release page,
a pre-release while it is 0.x. A version, once published, cannot be published again; the next fix is the next tag.

## When ARES changes its API

[openapi-watch.yml](../.github/workflows/openapi-watch.yml) compares ARES's
[OpenAPI document](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs) with
[ares-openapi.json](../spec/ares-openapi.json) on the first of every month, and when they differ opens one issue, or updates
the open one, listing the paths and schemas that changed. The issue ends with a prompt: commented on the issue, it
has Claude refresh the copy, work out what of it this library reads, and open a pull request. By hand,
`make openapi` refreshes the copy and `git diff spec/ares-openapi.json` shows what moved. The copy is sorted by key
(`jq -S`), so only a real change shows in the diff.

`AresSpecTest` holds the client to that copy: every field the client reads has to be in it with the type the client
reads it as, and every fixture may hold only fields it has. So refreshing the copy runs the tests again, and a field
ARES renamed or dropped fails one, naming it.
