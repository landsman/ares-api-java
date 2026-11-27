# Installing

The client, `cz.insuit:ares-api`, and its Spring Boot starter, `cz.insuit:ares-api-spring-boot-starter`, are on
[Maven Central](https://central.sonatype.com/namespace/cz.insuit). No repository block or token is needed.

## Gradle

```kotlin
dependencies {
    implementation("cz.insuit:ares-api-spring-boot-starter:0.2.0") // or ares-api alone
}
```

## Maven

```xml
<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>ares-api</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Older versions

0.1.1 and earlier are only on this repository's
[GitHub Packages](https://github.com/landsman?tab=packages&repo_name=ares-api-java), which asks for a token with the
`read:packages` scope even for a public package. The [previous version of this page](https://github.com/landsman/ares-api-java/blob/bbc1c1e/.docs/install.md)
shows the repository block that needs.

## Snapshots

None are published: only a tagged release is. To try what is not released yet, `make publish-local` in a checkout
installs the jars into `~/.m2` as `0.1.0-SNAPSHOT`, where `mavenLocal()` finds them.
