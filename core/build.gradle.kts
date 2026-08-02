plugins {
    id("fabric-loom")
    jacoco
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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
                exclude("fr/blueprint/core/BlueprintMod*")
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
