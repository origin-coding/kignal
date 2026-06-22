import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    group = "io.github.origincoding.kignal"
    version = "1.0.0"

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
