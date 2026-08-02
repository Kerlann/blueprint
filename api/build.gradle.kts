plugins {
    id("fabric-loom")
    `maven-publish`
}

base {
    archivesName.set("blueprint-api")
}

java {
    withSourcesJar()
}

// Publiable seul : les mods tiers le consomment en compileOnly (docs/extension-api.md §0).
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "blueprint-api"
            from(components["java"])
        }
    }
}

// Garde-fou AC2 (story 1.1) : le module api ne doit jamais référencer l'implémentation.
// Heuristique : on cherche les chemins de packages interdits dans le pool de constantes
// des .class compilés — toute référence (import, appel, string) y apparaît.
val checkApiIsolation = tasks.register("checkApiIsolation") {
    description = "Échoue si api référence fr.blueprint.core, .client ou .compat"
    group = "verification"
    val classes = sourceSets["main"].output.classesDirs
    inputs.files(classes)
    dependsOn(tasks.named("compileJava"))
    doLast {
        val interdits = listOf("fr/blueprint/core", "fr/blueprint/client", "fr/blueprint/compat")
        classes.asFileTree.matching { include("**/*.class") }.forEach { classFile ->
            val contenu = classFile.readBytes().toString(Charsets.ISO_8859_1)
            for (paquet in interdits) {
                if (contenu.contains(paquet)) {
                    throw GradleException(
                        "Le module api référence « ${paquet.replace('/', '.')} » dans " +
                            "${classFile.name} — interdit (principe P5, story 1.1 AC2)"
                    )
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(checkApiIsolation)
}
