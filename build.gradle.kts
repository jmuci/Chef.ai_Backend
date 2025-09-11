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
    useJUnitPlatform()
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

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.h2)
    implementation(libs.postgresql)

    testImplementation(libs.json.path)
//    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.server.test.host)
    // Ktor server test engine
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.12")

    // Ktor testing
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.12")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

}
