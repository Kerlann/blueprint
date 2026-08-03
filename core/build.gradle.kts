plugins {
    id("fabric-loom")
    jacoco
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.9.2")
    // Le mod d'exemple, chargé par PluginLoaderTest (story 2.2 AC5) — acyclique (testmod → api).
    testImplementation(project(path = ":testmod", configuration = "namedElements"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Régénération de la doc générée (story 9.5) : la propriété doit traverser jusqu'à
    // la JVM des tests, sinon -D ne parle qu'au démon Gradle.
    systemProperty("blueprint.regenDocs", System.getProperty("blueprint.regenDocs") ?: "false")
    // Les tests de core tournent sans Minecraft démarré (coding-standards §7).
    testLogging {
        events("failed", "skipped")
    }
    finalizedBy(tasks.jacocoTestReport)
}

// NFR13 : couverture ≥ 80 % sur core, vérifiée en CI (reprise QA TEST-001).
// BlueprintMod (entrypoint Fabric) est exclu : il ne s'exerce qu'en jeu (gametests).
tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            classDirectories.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching {
                // Raccords Fabric/Brigadier : ils ne s'exercent qu'avec un serveur vivant
                // (gametests, story 1.6). Leur LOGIQUE est extraite et testée à part —
                // GraphGuard, RateLimiter, DebugSession.resolve, Profiler.report… — c'est
                // ce qui reste ici qui n'est que du câblage.
                exclude("fr/blueprint/core/BlueprintMod*")
                exclude("fr/blueprint/core/command/BlueprintCommand*")
                exclude("fr/blueprint/core/net/ServerBlueprintNet*")
            })
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
