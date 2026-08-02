plugins {
    id("fabric-loom")
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Les tests de core tournent sans Minecraft démarré (coding-standards §7).
    testLogging {
        events("failed", "skipped")
    }
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
