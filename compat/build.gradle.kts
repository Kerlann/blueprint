plugins {
    id("fabric-loom")
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":platform", configuration = "namedElements"))
    implementation(project(path = ":core", configuration = "namedElements"))
}

// Le chargement conditionnel se teste sans jeu : la présence d'un mod est un
// paramètre, pas un appel à FabricLoader (story 8.4).
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
    }
}
