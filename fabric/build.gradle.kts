plugins {
    id("fabric-loom")
}

// Le module du chargeur : il voit TOUT, et tout le monde peut le voir sans le connaître.
// C'est le seul endroit du dépôt qui a le droit d'importer net.fabricmc — c'est même sa
// définition.
dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":platform", configuration = "namedElements"))
    implementation(project(path = ":core", configuration = "namedElements"))
    implementation(project(path = ":client", configuration = "namedElements"))
    implementation(project(path = ":compat", configuration = "namedElements"))
}

// Le fabric.mod.json a suivi le point d'entrée : c'est une métadonnée de chargeur, elle
// n'avait rien à faire dans core. Le remplacement de ${version} vient donc ici aussi.
tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
