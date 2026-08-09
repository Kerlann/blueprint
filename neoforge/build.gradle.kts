// Importés en tête : dans un script Gradle Kotlin, `java` désigne l'extension du greffon
// Java et masque le paquetage — `java.io.PipedInputStream` ne résout pas.
import java.io.PipedInputStream
import java.io.PipedOutputStream

plugins {
    id("net.neoforged.moddev")
}

base {
    archivesName.set("blueprint-neoforge")
}

neoForge {
    version = "21.11.45"

    // Ce que NeoForge doit considérer comme « le mod » au lancement — et les modules
    // communs en font partie. Sans ce bloc, le serveur démarre sans nous, et le silence
    // ressemble à un succès.
    //
    // Ce n'est pas une formalité de déclaration : c'est ce qui décide du CLASSLOADER.
    // Mis simplement sur le chemin d'exécution, les classes communes sont chargées par le
    // chargeur « app », tandis que NeoForge charge Minecraft par le sien (« TRANSFORMER »).
    // Les deux voient alors deux CustomPacketPayload différents portant le même nom, et le
    // mod meurt au démarrage sur un LinkageError incompréhensible. Déclarées ici, elles
    // passent par le même chargeur que le reste du mod.
    mods {
        register("blueprint") {
            sourceSet(sourceSets.main.get())
            commun.forEach {
                sourceSet(it.extensions.getByType<SourceSetContainer>()["main"])
            }
        }
    }

    runs {
        register("server") {
            server()
            gameDirectory = layout.buildDirectory.dir("run/server").get().asFile
            jvmArgument("-Xmx4G")
        }
        register("client") {
            client()
            gameDirectory = layout.buildDirectory.dir("run/client").get().asFile
            jvmArgument("-Xmx4G")
            // Un pseudo FIXE, pour la raison écrite dans le build racine : l'UUID
            // hors-ligne se calcule depuis le pseudo, donc un pseudo tiré au hasard fait
            // de chaque lancement un joueur différent — et tout ce qui est rangé par
            // joueur repart de zéro.
            programArgument("--username")
            programArgument("Kerlann")
        }
    }
}

// Les modules communs voyagent en SORTIE DE SOURCESET, jamais en JAR remappé : le JAR de
// Loom est remappé vers l'intermédiaire de Fabric, ce que NeoForge ne saurait pas lire.
// La sortie brute, elle, porte les noms officiels de Mojang — ceux que NeoForge attend.
//
// compileOnly puis inclusion manuelle dans le JAR : une dépendance de projet ordinaire
// tirerait la configuration Loom du module avec elle.
val commun = listOf(":api", ":platform", ":core", ":client", ":compat")
        .map { rootProject.project(it) }

// Sans cela, leurs sourceSets n'existent pas encore quand ce script s'évalue : Gradle
// configure les sous-projets dans l'ordre alphabétique, et « neoforge » passe avant
// « platform ».
commun.forEach { evaluationDependsOn(it.path) }

private fun Project.classesEtRessources() =
        extensions.getByType<SourceSetContainer>()["main"].output

// compileOnly, et SEULEMENT compileOnly : au lancement, les classes communes arrivent par
// le bloc `mods` ci-dessus, qui les fait passer par le bon chargeur. Les ajouter aussi en
// `implementation` les remettrait sur le chemin « app » et ferait revenir le LinkageError.
dependencies {
    commun.forEach { compileOnly(it.classesEtRessources()) }
}

// Les classes doivent EXISTER avant que le serveur démarre : la déclaration `mods` ne
// porte pas la dépendance de tâche que porterait un `project(...)`.
tasks.matching { it.name.startsWith("run") }.configureEach {
    commun.forEach { dependsOn("${it.path}:classes") }
    doFirst {
        // Le même contenu déclaré que le serveur de test Fabric. C'est ce qui exerce la
        // fenêtre d'enregistrement du lot B — la seule partie du portage où NeoForge ne
        // fait PAS ce que fait Fabric, et donc la première à vérifier en vrai.
        copy {
            from(rootProject.file("docs/examples/content"))
            into(layout.buildDirectory.dir("run/server/blueprint/content"))
        }
    }
}

// Un serveur PILOTÉ, pour vérifier la chaîne complète sans fenêtre ni main humaine.
//
// NeoForge 1.21.11 a bien un cadre de gametests, mais c'est le nouveau, orienté données
// (`GameTestInstance`, `TestEnvironmentDefinition`) — sans rapport avec les annotations
// que le module gametest utilise côté Fabric. Le porter est un chantier en soi.
//
// En attendant, le serveur dédié lit ses commandes sur l'entrée console : lui en donner
// une liste suffit à exercer commande → pont d'événements → compilation → VM →
// ordonnanceur, c'est-à-dire ce qu'aucun démarrage ne prouve.
//
// Derrière une propriété : `./gradlew :neoforge:runServer -Pblueprint.scenario`, pour que
// le lancement ordinaire reste interactif.
tasks.matching { it.name == "runServer" }.configureEach {
    if (!project.hasProperty("blueprint.scenario")) {
        return@configureEach
    }
    val scenario = rootProject.file("docs/qa/scenario-serveur.txt")
    inputs.file(scenario)
    doFirst {
        // Monde NEUF à chaque scénario, comme runGametest le fait déjà côté Fabric.
        //
        // Sans cela le scénario n'est pas reproductible, et il l'a prouvé : au second
        // passage, `/blueprint bench` répondait « already exists », et `/bpc bench`
        // « no enabled blueprint declares the command » — parce que le graphe du passage
        // précédent avait été sauvegardé, faute comprise, donc désactivé. Un scénario
        // dont le résultat dépend du run d'avant ne vérifie plus rien.
        //
        // (Ce qu'il a révélé au passage mérite son propre test : l'état désactivé avait
        // bien traversé un redémarrage. La persistance marche sur NeoForge.)
        delete(layout.buildDirectory.dir("run/server/world"))
        val lignes = scenario.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val sortie = PipedOutputStream()
        val entree = PipedInputStream(sortie, 8192)
        // Les commandes arrivent APRÈS le démarrage, et pas avant.
        //
        // Poussées d'un bloc, elles sont lues dès que la console démarre — c'est-à-dire
        // avant que le monde principal existe. La source de commande de la console n'a
        // alors pas de niveau, et `sendSuccess` lève sur `getLevel().getGameRules()` :
        // toutes les commandes échouent pour une raison qui n'a rien à voir avec elles.
        // Un vrai administrateur tape forcément après le démarrage ; le harnais doit
        // l'imiter.
        Thread {
            Thread.sleep(30_000)
            lignes.forEach { ligne ->
                sortie.write((ligne + System.lineSeparator()).toByteArray())
                sortie.flush()
                Thread.sleep(3_000)
            }
            // Puis on arrête proprement : le scénario doit se terminer tout seul, sinon
            // il faut le tuer de l'extérieur et le journal finit sur un tuyau cassé qui
            // ressemble à une panne.
            Thread.sleep(10_000)
            sortie.write(("stop" + System.lineSeparator()).toByteArray())
            sortie.flush()
        }.apply { isDaemon = true }.start()
        (this as JavaExec).standardInput = entree
    }
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    commun.forEach { module ->
        from(provider { module.classesEtRessources() })
        dependsOn("${module.path}:classes")
    }
    // La licence MIT exige que son texte accompagne toute redistribution, ce JAR-ci
    // comme l'autre.
    from(rootProject.file("LICENSE"))
}
