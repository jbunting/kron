import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.11"
}

group = "io.bunting.kron"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/michaelbull/maven")
}

dependencies {
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    implementation("com.michael-bull.kotlin-result", "kotlin-result", "1.1.0")
    // TODO: make this optional
    compileOnly("org.slf4j", "slf4j-api", "1.7.25")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.3.2")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "5.3.2")
    testImplementation("org.mockito", "mockito-junit-jupiter", "2.23.4")
    testImplementation("com.nhaarman.mockitokotlin2", "mockito-kotlin", "2.1.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
