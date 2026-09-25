plugins {
    alias(libs.plugins.owasp.dependencycheck)
}

// CLAUDE.md §11: "CI gates: SAST, dependency scan, secret scan. A high-severity finding fails the
// build." Two things are needed for that to be true, and both were missing:
//
// 1. USE `dependencyCheckAggregate`, NOT `dependencyCheckAnalyze`. This is a multi-project build and
//    the root project declares no dependencies of its own, so Analyze at the root scans ZERO
//    dependencies and still reports "Found 0 vulnerabilities" -- a vacuous pass. (Observed:
//    "Dependencies Scanned: 0 (0 unique)".) Aggregate walks the subprojects' configurations.
// 2. FAIL on a high-severity finding. The plugin's default only reports.
//
// An NVD API key makes the first NVD download minutes rather than ~40; without one the plugin warns
// and throttles. Supply it via the environment, never in this file (CLAUDE.md §3).
dependencyCheck {
    // CVSS v3 "High" starts at 7.0.
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    // Java-only project: the .NET/Node/Python analysers add scan time and false positives.
    analyzers.apply {
        assemblyEnabled = false
        nodeEnabled = false
        nodeAuditEnabled = false
        // Sonatype OSS Index is a SUPPLEMENTARY remote lookup; it fails on every artifact from this
        // network ("An error occurred while analyzing ... (Sonatype OSS Index Analyzer)"), and one
        // analyser error aborts the whole task with "Analysis failed" -- i.e. no report at all.
        // The NVD data remains the authoritative source and is unaffected. If OSS Index enrichment
        // is wanted later, it needs egress to ossindex.sonatype.org and an account for its rate limit.
        ossIndexEnabled = false
    }
    nvd.apply {
        System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() }?.let { apiKey = it }
    }
    // Every suppression needs a written justification in the file itself (CLAUDE.md §11).
    val suppressions = file("config/dependency-check-suppressions.xml")
    if (suppressions.exists()) {
        suppressionFile = suppressions.absolutePath
    }
}

allprojects {
    group = "com.afreximbank.horus"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
