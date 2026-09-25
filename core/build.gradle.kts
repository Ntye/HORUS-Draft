dependencies {
    api(project(":domain"))
    implementation(libs.icu4j)
    implementation(libs.commons.codec)
    implementation(libs.commons.text)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

// NFR-030 / Step 3 done-when: line coverage on horus.normalisation must be >= 80%,
// enforced here rather than merely asserted.
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
