plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin module: platform-agnostic domain models with no Android dependencies.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
