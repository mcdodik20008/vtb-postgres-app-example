plugins {
    application
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.mcdodik"
version = "0.0.1-SNAPSHOT"
description = "postgres-plan-analyzer"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.mcdodik.postgresplananalyzer.cli.MainKt")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.liquibase:liquibase-core")

    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

fun prop(
    name: String,
    default: String = "",
): String = (findProperty(name) as String?) ?: System.getenv(name.uppercase()) ?: default

tasks.register<JavaExec>("planAnalyze") {
    group = "verification"
    description = "Run Postgres Plan Advisor against SQL corpus"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)

    // параметры берём из -P... или ENV
    val argsList =
        listOf(
            "--jdbc-url",
            prop("dbUrl", "jdbc:postgresql://localhost:5432/postgres"),
            "--user",
            prop("dbUser", "postgres"),
            "--password",
            prop("dbPass", "postgres"),
            "--sql-glob",
            prop("sqlGlob", "src/main/resources/sql/**/*.sql"),
            "--out-dir",
            prop("outDir", "$buildDir/reports/plan-analyzer"),
            "--fail-on",
            prop("failOn", "NONE"), // NONE|LOW|MEDIUM|HIGH|CRITICAL
        )
    args = argsList

    // чтобы Ctrl+C корректно прерывал
    isIgnoreExitValue = false
}
