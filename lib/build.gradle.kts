plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

repositories {
    mavenCentral()
}

group = "dev.tjpal.ai"
version = "1.0.0"

dependencies {
    testImplementation(libs.kotlin.test)

    api(libs.kotlinx.serialization.json)
    api(libs.dagger)
    kapt(libs.dagger.compiler)
    implementation(libs.openai)
    implementation(libs.slf4j.api)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
