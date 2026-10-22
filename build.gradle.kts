plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependencyManagement)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.ktlint)
}

group = "io.fluxion"
version = "0.0.1-SNAPSHOT"
description = "Fluxion"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starterActuator)
    implementation(libs.spring.boot.starterDataJpa)
    implementation(libs.spring.boot.starterFlyway)
    implementation(libs.spring.boot.starterWebmvc)
    implementation(libs.flyway.databasePostgresql)
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.modulith.starterCore)
    implementation(libs.spring.modulith.starterInsight)
    implementation(libs.spring.modulith.starterJpa)
    implementation(libs.jackson.moduleKotlin)
    developmentOnly(libs.spring.boot.dockerCompose)
    runtimeOnly(libs.micrometer.registryPrometheus)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.spring.modulith.runtime)
    testImplementation(libs.spring.boot.starterActuatorTest)
    testImplementation(libs.spring.boot.starterDataJpaTest)
    testImplementation(libs.spring.boot.starterFlywayTest)
    testImplementation(libs.spring.boot.starterWebmvcTest)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.kotlin.testJunit5)
    testImplementation(libs.spring.modulith.starterTest)
    testImplementation(libs.reactor.nettyHttp)
    testImplementation(libs.testcontainers.junitJupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platformLauncher)
}

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.modulith.bom
                .get()
                .toString(),
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Rules live in .editorconfig. `ktlintCheck` runs as part of `check`; `ktlintFormat` fixes violations.
ktlint {
    version = libs.versions.ktlint.get()
}
