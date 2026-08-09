plugins {
    id("fabric-loom")
    jacoco
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":platform", configuration = "namedElements"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.9.2")
    // Le mod d'exemple, chargé par PluginLoaderTest (story 2.2 AC5) — acyclique (testmod → api).
    testImplementation(project(path = ":testmod", configuration = "namedElements"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // Les BANCS sont dehors. Deux classes — FuelCalibrationTest et CompilerPerfTest —
    // coûtaient 76 des 81 secondes de cette tâche ; les 840 autres tests en coûtaient
    // cinq. Une boucle de travail qui paie soixante-seize secondes de mesure à chaque
    // correction d'affichage n'est pas une boucle, et on finit par ne plus la lancer.
    // Ils ne sont pas perdus pour autant : `check` les exige (voir la tâche `bench`).
    useJUnitPlatform {
        excludeTags("bench")
    }
    // Régénération de la doc générée (story 9.5) : la propriété doit traverser jusqu'à
    // la JVM des tests, sinon -D ne parle qu'au démon Gradle.
    systemProperty("blueprint.regenDocs", System.getProperty("blueprint.regenDocs") ?: "false")
    // Les tests de core tournent sans Minecraft démarré (coding-standards §7).
    testLogging {
        events("failed", "skipped")
    }
    finalizedBy(tasks.jacocoTestReport)
}

/**
 * Les bancs de mesure, dans LEUR propre JVM.
 *
 * Séparés pour le temps, mais aussi pour la fidélité : ils mesuraient jusqu'ici après
 * huit cent quarante tests dans la même machine virtuelle, donc sur un JIT chauffé par
 * tout autre chose que ce qu'ils chronomètrent. Une mesure nulle sur `string/split` —
 * exactement ce que la garde §7.1 est là pour refuser — est apparue une fois puis n'est
 * pas revenue en trois relances isolées. Seuls, ils partent d'un état connu.
 */
val bench = tasks.register<Test>("bench") {
    group = "verification"
    description = "Les bancs de mesure (@Tag(\"bench\")), isolés du reste des tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("bench")
    }
    systemProperty("blueprint.regenDocs", System.getProperty("blueprint.regenDocs") ?: "false")
    testLogging {
        events("failed", "skipped")
    }
}

// NFR13 : couverture ≥ 80 % sur core, vérifiée en CI (reprise QA TEST-001).
// BlueprintMod (entrypoint Fabric) est exclu : il ne s'exerce qu'en jeu (gametests).
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    // « Après les bancs, s'ils tournent » — et non « lance les bancs ». C'est la nuance
    // exacte que Gradle exige pour lire leur trace sans les rendre obligatoires ; sans
    // elle, il refuse de configurer la tâche plutôt que de laisser l'ordre au hasard.
    mustRunAfter(bench)
    // Les DEUX traces, quand elles existent, pour que la couverture mesure ce que le
    // build a réellement exécuté et non une moitié arbitraire.
    //
    // Mesuré, parce que je m'attendais à l'inverse : les bancs n'apportent que QUINZE
    // instructions (0,7711 → 0,7713). Ils passent par les mêmes nœuds que le reste de
    // la suite. Ce raccord ne sauve donc rien aujourd'hui — il empêche seulement qu'un
    // banc futur, exerçant un chemin que lui seul emprunte, disparaisse du compte sans
    // que personne ne le remarque.
    //
    // Sans dépendance dure sur `bench` : elle rendrait `./gradlew test` aussi lent
    // qu'avant, ce qui annulerait la séparation. Le rapport peut donc refléter le
    // DERNIER passage des bancs, pas forcément celui-ci. C'est acceptable pour un
    // rapport ; ça ne l'est pas pour une barrière, et la barrière ci-dessous, elle,
    // dépend des deux.
    executionData(
        fileTree(layout.buildDirectory).include("jacoco/test.exec", "jacoco/bench.exec")
    )
    reports {
        // Aligné sur le client : sans XML, la couverture de core ne se lit qu'à l'œil
        // dans un rapport HTML, donc aucun outil ne peut dire QUELLE classe manque.
        xml.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, bench)
    executionData(
        fileTree(layout.buildDirectory).include("jacoco/test.exec", "jacoco/bench.exec")
    )
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
    // `bench` est dans la barrière, pas seulement dans le rapport : un banc qu'on ne
    // lance qu'à la demande ne mesure plus rien, et la doctrine §7.1 tomberait avec lui.
    // Ce qui change, c'est QUAND on le paie — au build, plus à chaque `test`.
    dependsOn(tasks.jacocoTestCoverageVerification, bench)
}
