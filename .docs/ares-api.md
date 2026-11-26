# How ARES behaves

What the [OpenAPI document](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs) does not make obvious.

## One subject, up to three requests

`ekonomicke-subjekty/{ico}` is the merged record every lookup starts with. Its `seznamRegistraci` says which source
registers know the subject, each `AKTIVNI`, `NEEXISTUJICI` or another state. The client reads one more endpoint
only when it is `AKTIVNI` there:

| Source | Endpoint | Read for | When |
|---|---|---|---|
| `stavZdrojeNrpzs` | `ekonomicke-subjekty-nrpzs/{ico}` | contacts | a health provider |
| `stavZdrojeRs` | `ekonomicke-subjekty-rs/{ico}` | contacts | a school, unless NRPZS is active too |
| `stavZdrojeRzp` | `ekonomicke-subjekty-rzp/{ico}` | the person | legal form in `SOLE_TRADER`: a company's RŽP record names no person |

**Nobody else has contacts.** ARES publishes no e-mail or phone for an ordinary company.

Each of those answers is a list of `zaznamy`; the one with `primarniZaznam: true` is read, or the first when none
is. RŠ lists e-mails as an array, NRPZS has one string.

## Numbers are numbers

`psc`, `cisloDomovni` and `cisloOrientacni` are JSON numbers, so a postal code `01001` arrives as `1001`: pad it to
five digits before writing it. A house number written `778/3a` is three fields: `cisloDomovni` 778,
`cisloOrientacni` 3, `cisloOrientacniPismeno` `a`.

## Legal forms

`pravniForma` is a three-digit code from ARES's `PravniForma` code list; `000` is the register's own "not
determined yet". `AresLegalFormGroup.of` sorts a code into sole trader, non-profit or legal entity, and anything not
listed as one of the first two is a legal entity.

## Status codes

| Status | Means | The client |
|---|---|---|
| 404 | no subject with this IČO | `AresNotFoundException`, no retry |
| 400 | a malformed request, such as an IČO that is not eight digits | refused before sending; any other 400 is `AresUnavailableException` |
| 5xx | the register is down or overloaded | retried, then `AresUnavailableException` |
