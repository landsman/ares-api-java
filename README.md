# ares-java

A client for [ARES](https://ares.gov.cz/), the Czech register of economic subjects, for Java 21+ and Kotlin: a
company's name, VAT number, legal form and registered seat by its IČO, the contacts the health and school registers
publish, and the person behind a sole trader's licence. Its only dependencies are Jackson 3 and JSpecify.
Published on GitHub Packages, not Maven Central.

Written against ARES's public REST API, [Swagger UI](https://ares.gov.cz/swagger-ui/) and
[OpenAPI document](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs). No key, no sign-up.

| Read | For |
|---|---|
| [How ARES behaves](.docs/ares-api.md) | what bites: which subjects have contacts, the numbers, the registrations |
| [Installing](.docs/install.md) | the artifact on GitHub Packages, for Gradle and Maven |
| [Development](.docs/development.md) | build, tests, releasing |

## Use

```java
var ares = new AresClient();                     // one per application; thread-safe, closeable

AresCompany company = ares.company("27074358");
company.name();                                  // "Asseco Central Europe, a.s."
company.dic();                                   // "CZ27074358"
AresLegalFormGroup.of(company.legalForm());      // LEGAL_ENTITY
company.address().textAddress();                 // "Budějovická 778/3a, Michle, 14000 Praha 4"
company.contacts();                              // null: only health providers and schools have them
company.soleTrader();                            // null: only for a sole trader, from the trade register
```

Only `ico` and `name` are always there. An IČO that is not eight digits is refused before any request.

A 5xx or no answer is retried twice, waiting 500 ms and then 1 s; each attempt may take 10 seconds. With a person
waiting on the lookup, cut both down:

```java
var ares = AresClient.builder().requestTimeout(Duration.ofSeconds(3)).retries(1).build();
```

## Spring Boot

`cz.insuit:ares-api-spring-boot-starter` brings the client and gives the application an `AresClient` bean.
Nothing has to be set:

```yaml
ares:
  base-uri: http://localhost:8089/ekonomicke-subjekty-v-be/rest   # only for a mock server; production otherwise
  request-timeout: 3s                                            # 10 seconds otherwise
  retries: 1                                                     # 2 otherwise
  retry-delay: 200ms                                             # 500 ms otherwise
```

An application's own `AresClient` bean replaces the starter's. Compiled against Spring Boot 3.5.

## Failures

`AresException` is sealed, so a `switch` over it is exhaustive:

| Exception | When | Do |
|---|---|---|
| `AresNotFoundException` | 404: no subject has the IČO | tell the person; retrying changes nothing |
| `AresUnavailableException` | no answer, a 5xx after the retries, any other status, a body it cannot read | try again later |

The contacts and the sole trader's person are best effort: when their register fails, the field is null, the
failure is logged through `System.Logger` at `WARNING`, and the lookup still succeeds. An interrupted thread
throws `CancellationException`, its flag kept set.

## Kotlin

JSpecify reaches Kotlin as `String` / `String?`, enforced from Kotlin 2.1; on older versions add
`-Xjspecify-annotations=strict`. `AresException` is exhaustive in a `when` without `else`.

## Not here

No search by name, no history, no statutory bodies from the commercial register (VR), no caching: one subject by
its IČO, as it stands today.

Licence: [Apache 2.0](LICENSE)
