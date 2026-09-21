dependencies {
    api(project(":domain"))
    implementation(libs.icu4j)
    implementation(libs.commons.codec)
    implementation(libs.commons.text)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
