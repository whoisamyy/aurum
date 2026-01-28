plugins {
    id("java")
}

group = "io.github.whoisamyy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":codegen"))
    implementation(project(":parsing"))
    implementation(project(":ir"))

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