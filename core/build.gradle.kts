plugins {
    id("java")
    kotlin("jvm") version "2.4.0"
}

group = "io.github.whoisamyy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:26.0.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    modularity.inferModulePath.set(true)
}

tasks.withType<JavaCompile> {
    options.compilerArgumentProviders.add(object : CommandLineArgumentProvider {
        @CompileClasspath
        val kotlinClasses = kotlin.sourceSets.main.flatMap { it.kotlin.classesDirectory }

        override fun asArguments() = listOf(
            "-Xlint:-module",
            "--patch-module",
            "aurum.core=${kotlinClasses.get().asFile.absolutePath}"
        )
    })
}

tasks.test {
    useJUnitPlatform()
}