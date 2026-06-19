import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    group = "io.github.origincoding.kignal"
    version = "0.1.0-SNAPSHOT"

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
