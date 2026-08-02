plugins {
    id("fabric-loom")
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":core", configuration = "namedElements"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Comme core : les tests tournent sans Minecraft démarré (coding-standards §7).
    // Camera, NodeGeometry et le banc de rendu sont de la logique pure.
    testLogging {
        events("failed", "skipped")
    }
}
