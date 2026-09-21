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

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

// horus.architecture-tests scans bootstrap's compiled classes; the plain jar
// task is re-enabled alongside bootJar so the project dependency resolves.
tasks.named<Jar>("jar") {
    enabled = true
}
