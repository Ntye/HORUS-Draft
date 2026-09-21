plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "horus"

include("domain")
include("core")
include("application")
include("adapters")
include("bootstrap")
include("tools")
include("architecture-tests")
