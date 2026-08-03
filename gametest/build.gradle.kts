plugins {
    id("fabric-loom")
}

// Tests joués dans un vrai serveur (story 1.6). Ils voient l'implémentation — c'est
// justement leur travail — mais ne partent jamais dans le JAR livré.
dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":core", configuration = "namedElements"))
}
