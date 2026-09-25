plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":adapters"))
    implementation(project(":application"))
    implementation(project(":core"))
    implementation(project(":domain"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
    implementation("org.springframework.boot:spring-boot-starter")
    // Step 5: the composition root defines the three role-scoped DataSource/JdbcTemplate beans
    // (CLAUDE.md §11), so it needs DataSource/JdbcTemplate types on its own compile classpath.
    // Postgres driver and Flyway are already on the runtime classpath transitively via
    // :adapters (its `implementation` deps); this adds nothing new there, only the compile-time
    // types bootstrap's own config class references.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // CliRunnerConfig wires picocli's CommandLine directly; already a runtime dependency via
    // :adapters, this just adds it to bootstrap's own compile classpath.
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    // ApplicationBootTest starts the real Spring context against a throwaway Postgres. Same
    // catalog entries :adapters already uses for its integration tests -- nothing new to pin.
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

// horus.architecture-tests scans bootstrap's compiled classes; the plain jar
// task is re-enabled alongside bootJar so the project dependency resolves.
tasks.named<Jar>("jar") {
    enabled = true
}
