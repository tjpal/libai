plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    `maven-publish`
}

repositories {
    mavenLocal()
    mavenCentral()
}

group = "dev.tjpal.ai"
version = "1.1.0"

dependencies {
    testImplementation(libs.kotlin.test)

    api(libs.kotlinx.serialization.json)
    api(libs.dagger)
    kapt(libs.dagger.compiler)
    implementation(libs.openai)
    implementation(libs.slf4j.api)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("libai") {
            from(components["kotlin"])
            groupId = project.group.toString()
            artifactId = "libai"
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "local"
            mavenLocal()
        }
    }
}

tasks.register<PublishToMavenRepository>("publishToLocal") {
    publication = publishing.publications["libai"] as MavenPublication
    repository = publishing.repositories["local"] as MavenArtifactRepository
}
