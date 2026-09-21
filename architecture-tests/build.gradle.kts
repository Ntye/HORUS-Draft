dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":core"))
    testImplementation(project(":application"))
    testImplementation(project(":adapters"))
    testImplementation(project(":bootstrap"))
    testImplementation(project(":tools"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
}
