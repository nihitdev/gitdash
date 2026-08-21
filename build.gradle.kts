plugins {
    application
    java
}

group = "dev.nihit"
version = "0.1.0"

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(26) } }

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.4")
}

application {
    mainClass = "dev.nihit.gitdash.GitDash"
    applicationName = "gitdash"
}

tasks.test { useJUnitPlatform() }
tasks.jar {
    manifest { attributes["Main-Class"] = application.mainClass.get() }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 26
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}
