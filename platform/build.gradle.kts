plugins {
    id("fabric-loom")
}

// Ne voit QUE l'api. Pas core, pas client : ce module énonce des questions, il n'en
// connaît aucune réponse — et surtout il ne doit pas pouvoir en connaître, sinon la
// frontière qu'il définit fuit dès le premier raccourci.
dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
}

// Même garde-fou que l'api (story 1.1 AC2), pour la même raison et un cran plus loin :
// un module de frontière qui référence l'implémentation n'est plus une frontière.
val checkPlatformIsolation = tasks.register("checkPlatformIsolation") {
    description = "Échoue si platform référence fr.blueprint.core, .client ou .compat"
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
                        "Le module platform référence « ${paquet.replace('/', '.')} » dans " +
                            "${classFile.name} — interdit : platform pose les questions, " +
                            "core y répond, jamais l'inverse"
                    )
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(checkPlatformIsolation)
}
