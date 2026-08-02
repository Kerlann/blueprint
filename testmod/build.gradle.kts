plugins {
    id("fabric-loom")
}

// Le testmod ne dépend QUE de l'api : c'est la preuve qu'un mod tiers
// peut s'intégrer sans voir l'implémentation.
dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
}
