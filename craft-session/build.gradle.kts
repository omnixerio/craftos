
import io.github.fourlastor.construo.Target
import io.github.fourlastor.construo.task.jvm.RoastTask
import io.github.fourlastor.construo.task.macos.GeneratePlist

plugins {
    id("java")
    id("io.github.fourlastor.construo") version "2.1.0"
}

group = "dev.ultreon.craftos"
version = "1.0-SNAPSHOT"

val gdxVersion = "1.13.5"
val log4j2Version = "2.22.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.jetbrains:annotations:26.0.2")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    implementation("org.slf4j:slf4j-api:2.0.11")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j2Version")

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    implementation("dev.3-3:jmccc:3.1.4")
    implementation("dev.3-3:jmccc-microsoft-authenticator:3.1.4")
    implementation("dev.3-3:jmccc-mojang-api:3.1.4")
    implementation("dev.3-3:jmccc-mcdownloader:3.1.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.ultreon.craftos.dm.craft-session"
    }

    archiveFileName = "craft-session.jar"

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

construo {
    mainClass = "dev.ultreon.craftos.session.CraftSession"
    name = "craft-session"
    version = rootProject.version.toString()
    humanName = "craft-session"
    jarTask = "jar"
    outputDir = file("build/dist")
    roast.runOnFirstThread = true
    roast.useZgc = true
    roast.vmArgs.addAll("-Xmx6g", "-Xms2g", "-Dconstruo=true")
    jlink.modules.addAll("java.base", "java.scripting", "java.net.http", "java.logging", "java.xml", "java.desktop", "java.sql", "java.management", "java.instrument", "java.compiler", "java.management.rmi", "java.rmi", "java.security.jgss", "java.security.sasl", "java.smartcardio", "jdk.unsupported", "jdk.zipfs", "jdk.jfr")
    jlink.guessModulesFromJar = false

    targets {
        register<Target.Linux>("linuxX64") {
            this.jdkUrl =
                "https://cache-redirector.jetbrains.com/intellij-jbr/jbr-25.0.3-linux-x64-b508.4.tar.gz"
            this.architecture = Target.Architecture.X86_64
        }
    }
}
