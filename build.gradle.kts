tasks.register<Copy>("copyBin") {
    description = "Copy resources to OS base"

    from(fileTree("res/os"))
    into(file("OS/"))
}
tasks.register<Copy>("copyCraftSession") {
    description = "Copy resources to OS base"

    dependsOn(":craft-session:packageLinuxX64")

    from(zipTree(project(":craft-session").tasks.named("packageLinuxX64").get().outputs.files.singleFile.resolve("craft-session-linuxX64.zip")))
    into(file("OS/opt/craft-session"))

    doFirst {
        delete(fileTree("OS/opt/craft-session"))
        delete(file("OS/opt/craft-session"))
    }
}

tasks.register("build") {
    dependsOn("copyBin", "copyCraftSession")
}
