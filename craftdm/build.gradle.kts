plugins {
    id("java")
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

}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.ultreon.craftos.dm.CraftDM"
    }

    archiveFileName = "craftdm.jar"

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
