pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
        }
        mavenCentral()
        gradlePluginPortal()
    }
    // La version du greffon NeoForge est déclarée ICI et pas dans le sous-projet : c'est
    // la seule façon de la tenir au même endroit que celle de Loom, qui vit dans le build
    // racine. Deux versions de chaîne d'outils éparpillées dans deux fichiers finissent
    // par diverger.
    plugins {
        id("net.neoforged.moddev") version "2.0.143"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "blueprint"

// api     : surface publique consommée par les mods tiers (ne voit pas l'implémentation)
// platform: ce que le code commun demande au chargeur — des interfaces, aucune réponse
// core    : modèle, registre, compilateur, VM, BScript, persistance, réseau serveur
// client  : éditeur visuel et réseau client
// compat  : intégrations conditionnelles avec des mods tiers
// fabric  : le point d'entrée Fabric et les implémentations de platform. Le SEUL module
//           autorisé à importer net.fabricmc (plan multiloader, lot A)
// neoforge: idem pour NeoForge — le seul autorisé à importer net.neoforged (lot E)
// testmod : mod d'exemple validant l'api (exclu du JAR final)
// gametest: tests joués dans un VRAI serveur (story 1.6), jamais livrés
include("api", "platform", "core", "client", "compat", "fabric", "neoforge",
        "testmod", "gametest")
