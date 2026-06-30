tasks.register<Copy>("copyBin") {
    description = "Copy resources to OS base"

    from(fileTree("res/os"))
    into(file("OS/"))
}
tasks.register<Copy>("copyCraftSession") {
    description = "Copy resources to OS base"

    dependsOn(":craft-session:createSetup")
}

tasks.register("build") {
    dependsOn("copyBin", "copyCraftSession")
}
