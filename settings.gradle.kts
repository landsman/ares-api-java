rootProject.name = "ares-java"

// What ships: the client, and the Spring Boot starter around it.
include(
    ":modules:ares-api",
    ":modules:ares-api-spring-boot-starter",
)
