import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "2.2.0"
    antlr
}

group = "io.github.whoisamyy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ir"))
    implementation(project(":runtime"))
    antlr("org.antlr:antlr4:4.13.2")

    implementation(kotlin("reflect"))
    implementation("org.jetbrains:annotations:26.0.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-no-listener")
    source = fileTree("src/main/antlr")
}

tasks.withType<KotlinCompile> {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.compileTestJava {
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.withType<JavaCompile> {
    dependsOn(tasks.generateGrammarSource)

    options.compilerArgumentProviders.add(object : CommandLineArgumentProvider {
        @CompileClasspath
        val kotlinClasses = kotlin.sourceSets.main.flatMap { it.kotlin.classesDirectory }

        override fun asArguments() = listOf(
            "--patch-module",
            "aurum.compiler=${kotlinClasses.get().asFile.absolutePath}"
        )
    })
}

java {
    modularity.inferModulePath.set(true)
}

tasks.test {
    useJUnitPlatform()
}