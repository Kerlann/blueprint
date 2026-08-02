pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "blueprint"

// api    : surface publique consommée par les mods tiers (ne voit pas l'implémentation)
// core   : modèle, registre, compilateur, VM, BScript, persistance, réseau serveur
// client : éditeur visuel et réseau client
// compat : intégrations conditionnelles avec des mods tiers
// testmod: mod d'exemple validant l'api (exclu du JAR final)
include("api", "core", "client", "compat", "testmod")
