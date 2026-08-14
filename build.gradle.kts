plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.tenmilelabs"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

tasks.test {
    useJUnitPlatform {
        excludeTags("db-integration")
    }
}

val dbIntegrationTest by tasks.registering(Test::class) {
    description = "Runs database-backed integration tests."
    group = "verification"
    useJUnitPlatform {
        includeTags("db-integration")
    }
    shouldRunAfter(tasks.test)
}

val importTheMealDb by tasks.registering(JavaExec::class) {
    description = "Fetches recipes from TheMealDB's free API and writes " +
        "src/main/resources/sql/seed_themealdb.sql. One-off, no DB connection required."
    group = "data"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.tenmilelabs.tools.themealdb.MainKt"
}

dependencies {

    implementation(libs.logback.classic)

    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.thymeleaf)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.rate.limit)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.h2)
    implementation(libs.postgresql)

    // Security
    implementation(libs.bcrypt)

    // Bulk import tooling (tools/themealdb) — a Ktor HTTP client, not used by the server itself
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Tests
    testImplementation(libs.json.path)
    testImplementation(libs.ktor.server.test.host)

    // JUnit 5
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

}
