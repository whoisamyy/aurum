plugins {
    id("java")
    kotlin("jvm") version "2.2.0"
}

group = "io.github.whoisamyy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":compiler"))
    implementation(project(":ir"))
    implementation(kotlin("reflect"))

    implementation("org.jetbrains:annotations:26.0.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs = options.compilerArgs + "-Aproject=${project.group}/${project.name}"
}

java {
    modularity.inferModulePath.set(true)
}

tasks.withType<JavaCompile> {
    options.compilerArgumentProviders.add(object : CommandLineArgumentProvider {
        @CompileClasspath
        val kotlinClasses = kotlin.sourceSets.main.flatMap { it.kotlin.classesDirectory }

        override fun asArguments() = listOf(
            "--patch-module",
            "aurum.cli=${kotlinClasses.get().asFile.absolutePath}"
        )
    })
}