import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.31"
    application
    id("com.squareup.sqldelight") version "1.4.4"
    kotlin("plugin.serialization") version "1.4.31"
}

group = "com.github.softwareengineeringclub"
version = "1.0"

repositories {
    jcenter()
    mavenCentral()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlinx") }
    maven { url = uri("https://dl.bintray.com/kotlin/ktor") }
    maven { url = uri("https://jitpack.io") }

}

dependencies {
    // Testing Dependencies (unused)
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")

    // Coroutines for Async Work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")

    // Ktor Webserver Dependencies
    val ktorVersion = "1.4.0"
    implementation("io.ktor:ktor-server-netty:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("org.slf4j:slf4j-simple:2.0.0-alpha1")

    // JSON Serializer for returning objects to the user
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")

    // BCrypt Password Hasher/Encrypter
    implementation("com.ToxicBakery.library.bcrypt:bcrypt:+")

    // SQLite JDBC Driver
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("org.postgresql:postgresql:42.2.19.jre7")
    implementation("com.squareup.sqldelight:jdbc-driver:1.4.4")
    implementation("com.squareup.sqldelight:sqlite-driver:1.4.4")
    implementation("com.squareup.sqldelight:coroutines-extensions-jvm:1.4.4")
}

sqldelight {
    database("Database") {
        packageName = "com.github.softwareengineeringclub.database"
        dialect = "postgresql"
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "ServerKt"
}