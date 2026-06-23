plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin utilities shared by every module (Result, dispatchers, time, ids).
// Kept Android-free so both `:core:domain` (pure) and Android modules can depend on it.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
