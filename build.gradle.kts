plugins {
    id("java")
}

group = "io.github.whoisamyy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runParser") {
    dependsOn(":parsing:classes")
    group = "application"
    description = "Run lang.aurum.parsing.Parser.main()"
    mainModule.set("aurum.parsing/lang.aurum.parsing.Parser")
    classpath = project(":parsing").sourceSets.main.get().runtimeClasspath
    modularity.inferModulePath.set(true)
//    mainClass.set("lang.aurum.parsing.Parser")
}