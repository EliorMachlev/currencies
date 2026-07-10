import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.0" apply false
    // dependency-update-checker
    id("com.github.ben-manes.versions") version "0.54.0"
}

// only check for stable versions
tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.layout.buildDirectory)
}
