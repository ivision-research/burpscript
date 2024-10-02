import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

val burpVersion = "2023.10.4"
val graalVersion = "24.0.1"
val kotlinxVersion = "1.6.3"
val kotestVersion = "5.9.1"

plugins {
    java
    `java-library`
    kotlin("jvm") version "1.9.10"
    id("antlr")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("plugin.serialization") version "1.9.10"
}

val pluginVersion = "0.7.1-beta"

group = "com.carvesystems.burpscript"
version = pluginVersion

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val isRunningInIntelliJ: Boolean = System.getProperty("idea.active") == "true"
val isTestTaskRequested: Boolean = gradle.startParameter.taskNames.any {
    it.lowercase(Locale.getDefault()).contains("test")  || it == "check"
}

val enableLangPy =
    System.getProperty("burpscript.langPython", "on") == "on"
            || isRunningInIntelliJ
            || isTestTaskRequested

val enableLangJs =
    System.getProperty("burpscript.langJs", "off") == "on"
            || isRunningInIntelliJ
            || isTestTaskRequested

val langDeps = mutableListOf(
    "org.graalvm.polyglot:polyglot:$graalVersion",
)

if (enableLangPy) {
    langDeps.add("org.graalvm.polyglot:python-community:$graalVersion")
    langDeps.add("org.graalvm.polyglot:llvm-community:$graalVersion")
    println("Python language enabled")
}

if (enableLangJs) {
    langDeps.add("org.graalvm.polyglot:js-community:$graalVersion")
    println("JavaScript language enabled")
}

val testDeps = listOf(
    "io.kotest:kotest-assertions-core-jvm:$kotestVersion",
    "io.kotest:kotest-assertions-json:$kotestVersion",
    "io.kotest:kotest-framework-engine-jvm:$kotestVersion",
    "io.kotest:kotest-property-jvm:$kotestVersion",
    "io.kotest:kotest-runner-junit5:$kotestVersion",
    "io.mockk:mockk:1.12.2",
)

val generatedDir: File = layout.buildDirectory.file("generated/source/burpscript/main/kotlin").get().asFile

val internalTarget = "internal"

val integrationTestTarget = "integrationTest"

sourceSets {
    main {
        java {
            srcDirs(
                "src/main/kotlin",
                generatedDir
            )
        }
    }

    create(internalTarget) {
        java {
            srcDir("src/internal/kotlin")
        }
    }
}

dependencies {
    antlr("org.antlr:antlr4:4.10.1")

    api("net.portswigger.burp.extensions:montoya-api:$burpVersion")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxVersion")
    langDeps.forEach { dependencies.add("implementation", it) }

    "${internalTarget}Implementation"(project)
    langDeps.forEach { dependencies.add("${internalTarget}Implementation", it) }
    testDeps.forEach { dependencies.add("${internalTarget}Implementation", it) }
}

testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(sourceSets[internalTarget].output)
                langDeps.forEach { implementation(it) }
                testDeps.forEach { implementation(it) }
            }
        }

        register<JvmTestSuite>(integrationTestTarget) {
            sources {
                java {
                    srcDir("src/integrationTest/kotlin")
                }
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter("test")
                    }
                }
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    withType<AntlrTask> {
        arguments.addAll(arrayOf("-visitor", "-no-listener"))
    }

    compileKotlin {
        dependsOn("generateGrammarSource")
    }

    compileTestKotlin {
        dependsOn("generateTestGrammarSource")
    }

    named("compileIntegrationTestKotlin") {
        dependsOn("generateIntegrationTestGrammarSource")
    }

    named("compileInternalKotlin") {
        dependsOn("generateInternalGrammarSource")
    }

    val checkLanguage by registering {
        if (!(enableLangPy || enableLangJs)) {
            error("A guest language must be configured")
        }
    }

    shadowJar {
        archiveBaseName.set("burpscript-plugin")
        archiveClassifier.set("")
        archiveVersion.set(pluginVersion)
        mergeServiceFiles()
    }

    jar {
        dependsOn(checkLanguage)
    }

    check {
        dependsOn(named(integrationTestTarget))
    }
}

