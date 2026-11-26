# Fixtures

Answers in the shape of ARES's REST API, `ekonomicke-subjekty-v-be/rest`, as its
[OpenAPI document](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs) describes them, cut down to the
fields this library reads plus a few it ignores. `company.json` is the public record of IČO 27074358; the rest
are made up.

| File | Path it answers | What it holds |
|---|---|---|
| `company.json` | `ekonomicke-subjekty/{ico}` | a limited company, every field this library reads |
| `hospital.json` | `ekonomicke-subjekty/{ico}` | a subject with NRPZS active |
| `school.json` | `ekonomicke-subjekty/{ico}` | a subject with RŠ active |
| `sole-trader.json` | `ekonomicke-subjekty/{ico}` | legal form 101, RŽP active |
| `nrpzs.json` | `ekonomicke-subjekty-nrpzs/{ico}` | two records, the second primary |
| `rs.json` | `ekonomicke-subjekty-rs/{ico}` | e-mails as a list |
| `rzp.json` | `ekonomicke-subjekty-rzp/{ico}` | the person behind the licence |
