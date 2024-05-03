import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val burpVersion = "2023.10.4"
val graalVersion = "23.1.2"
val kotlinxVersion = "1.6.3"
val kotestVersion = "5.4.1"

plugins {
    java
    `java-library`
    kotlin("jvm") version "1.9.10"
    id("antlr")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("plugin.serialization") version "1.9.10"
}

val pluginVersion = "0.5.0-beta"

group = "com.carvesystems.burpscript"
version = pluginVersion

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val isRunningInIntelliJ: Boolean = System.getProperty("idea.active") == "true"
val isTestTaskRequested: Boolean = gradle.startParameter.taskNames.contains("test")

val langPy =
    System.getProperty("burpscript.langPython", "on") == "on"
            || isRunningInIntelliJ
            || isTestTaskRequested
val langJs =
    System.getProperty("burpscript.langJs", "off") == "on"
            || isRunningInIntelliJ
            || isTestTaskRequested

dependencies {
    antlr("org.antlr:antlr4:4.10.1")

    implementation("net.portswigger.burp.extensions:montoya-api:$burpVersion")
    implementation("org.graalvm.polyglot:polyglot:$graalVersion")
    if (langPy) {
        println("Building with Python support")
        implementation("org.graalvm.polyglot:python-community:$graalVersion")
        implementation("org.graalvm.polyglot:llvm-community:$graalVersion")
    }
    if (langJs) {
        println("Building with JavaScript support")
        implementation("org.graalvm.polyglot:js-community:$graalVersion")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxVersion")
    implementation(kotlin("stdlib"))

    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-engine-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-property-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:1.12.2")

}

tasks.generateGrammarSource {
    arguments.addAll(arrayOf("-visitor", "-no-listener"))
}

tasks.generateTestGrammarSource {
    arguments.addAll(arrayOf("-visitor", "-no-listener"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    sourceSets["main"].java {
        srcDir("src/main/kotlin")
    }
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.dependsOn("generateGrammarSource")
compileKotlin.kotlinOptions {
    jvmTarget = "17"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.dependsOn("generateTestGrammarSource")
compileTestKotlin.kotlinOptions {
    jvmTarget = "17"
}

val checkLanguageTask = "checkLanguage"


val generatedDir: File = layout.buildDirectory.file("generated/source/burpscript/main/kotlin").get().asFile

sourceSets {
    main {
        kotlin {
            srcDir(generatedDir)
        }
    }
}

tasks {
    register(checkLanguageTask) {
        val langPy = System.getProperty("burpscript.langPython", "on") == "on"
        val langJs = System.getProperty("burpscript.langJs", "off") == "on"

        if (!(langPy || langJs)) {
            error("a guest language must be configured")
        }
    }

    shadowJar {
        archiveBaseName.set("burpscript-plugin")
        archiveClassifier.set("")
        archiveVersion.set(pluginVersion)
        mergeServiceFiles()
    }

    jar {
        dependsOn(getTasks()[checkLanguageTask])
    }

    withType<Test> {
        useJUnitPlatform()
    }
}

