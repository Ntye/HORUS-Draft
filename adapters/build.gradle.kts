dependencies {
    api(project(":application"))
    api(project(":core"))
    api(project(":domain"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Step 5: list_version.capabilities and audit_event.payload are stored as JSONB. Covered by
    // the Spring Boot BOM already declared above (no new version to pin); Apache License 2.0,
    // same as every other Spring Boot starter here. Nothing already on the classpath does JSON.
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}
