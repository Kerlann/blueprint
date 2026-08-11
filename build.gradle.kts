import net.fabricmc.loom.api.LoomGradleExtensionAPI
// Dans un script Gradle Kotlin, `java` désigne l'extension du greffon Java et masque le
// paquetage : `java.io.PipedInputStream` ne résout pas sans cet import.
import java.io.PipedInputStream
import java.io.PipedOutputStream

plugins {
    id("fabric-loom") version "1.13.6"
}

val minecraftVersion = "1.21.11"
val loaderVersion = "0.18.2"
val fabricVersion = "0.139.4+1.21.11"

base {
    archivesName.set("blueprint")
}

// Configuration commune : chaque module compile contre Minecraft avec les mêmes
// mappings ; les dépendances ENTRE modules passent par "namedElements" (voir plus bas).
allprojects {
    version = "1.0.0"
    group = "fr.blueprint"

    repositories {
        mavenCentral()
    }

    // Loom pour tout le monde SAUF le module NeoForge, qui a sa propre chaîne d'outils
    // (ModDevGradle) et refuserait de cohabiter avec celle-ci.
    //
    // Les deux chaînes s'entendent malgré tout, et c'est ce qui rend le lot E abordable :
    // en 1.21, Loom comme ModDevGradle compilent contre un Minecraft aux mappings
    // OFFICIELS de Mojang. Les classes de `core` produites par Loom nomment donc
    // exactement les mêmes membres que ce que NeoForge attend, et se lient telles quelles
    // — c'est la sortie brute des sourceSets qui voyage, jamais le JAR remappé.
    if (path == ":neoforge") {
        apply(plugin = "java")
    } else {
        apply(plugin = "fabric-loom")
    }

    val loomExt = extensions.findByType<LoomGradleExtensionAPI>()

    // Minecraft pour TOUT LE MONDE, le chargeur pour presque personne.
    //
    // C'est le lot D du plan multiloader, et c'est ce qui transforme une règle en fait :
    // `checkLoaderIsolation` lit les sources et peut s'oublier, un chemin de classes ne
    // s'oublie pas. Depuis ce commit, écrire `net.fabricmc` dans core ne produit plus un
    // avertissement mais une erreur de compilation, à la ligne, dans l'IDE.
    //
    // La liste est courte et doit le rester : `fabric` est le module du chargeur ;
    // `testmod` et `gametest` sont des mods Fabric à part entière, jamais livrés ; le
    // projet racine porte les configurations de lancement, qui ont besoin du chargeur
    // pour démarrer le jeu.
    val voientLeChargeur = setOf(":", ":fabric", ":testmod", ":gametest")

    if (loomExt != null) {
        dependencies {
            "minecraft"("com.mojang:minecraft:$minecraftVersion")
            "mappings"(loomExt.officialMojangMappings())
            if (path in voientLeChargeur) {
                "modImplementation"("net.fabricmc:fabric-loader:$loaderVersion")
                "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
            }
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // Les sources sont en UTF-8 (messages français accentués) ; sans ça, javac lit en cp1252
    // sous Windows et les accents sortent en mojibake dans le jeu.
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // Noms de paramètres conservés : @BlueprintNode déduit le nom des pins de la
        // signature (story 8.1). Sans ça, un pin s'appellerait « arg0 » — et un nom de
        // pin ne se corrige plus une fois dans les graphes des joueurs.
        options.compilerArgs.add("-parameters")
    }
}

// Le projet racine ne contient pas de sources : il agrège les modules dans un JAR
// unique et porte les configurations de lancement (runClient / runServer).
dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":platform", configuration = "namedElements"))
    implementation(project(path = ":core", configuration = "namedElements"))
    implementation(project(path = ":client", configuration = "namedElements"))
    implementation(project(path = ":compat", configuration = "namedElements"))
    implementation(project(path = ":fabric", configuration = "namedElements"))
    // testmod : uniquement sur le classpath de dev, jamais dans le JAR final
    runtimeOnly(project(path = ":testmod", configuration = "namedElements"))
    // gametest : idem — chargé par runGametest, absent du JAR livré (story 1.6)
    runtimeOnly(project(path = ":gametest", configuration = "namedElements"))
}

// JAR unique : api + platform + core + client + compat + fabric (testmod exclu).
// remapJar remappe le tout.
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    listOf(":api", ":platform", ":core", ":client", ":compat", ":fabric").forEach { path ->
        from(provider { project(path).extensions.getByType<SourceSetContainer>()["main"].output })
    }
    // La licence MIT exige que son texte accompagne toute redistribution — et un JAR
    // téléchargé est une redistribution. Le champ "license" du fabric.mod.json l'annonce
    // sans le fournir : le déclarer sans le joindre ne remplit pas la condition.
    from(rootProject.file("LICENSE"))
}

// Le critère du lot A, rendu MÉCANIQUE : aucun module commun ne mentionne le chargeur.
//
// Une règle qu'on se rappelle est une règle qu'on oublie. Celle-ci s'est déjà défendue
// pendant le lot A lui-même : un `FabricLoader.getGameDir()` restait dans BlueprintClient,
// invisible au relevé des imports parce qu'il était écrit en toutes lettres au milieu
// d'un appel.
//
// Sur les SOURCES et non les .class, contrairement à checkApiIsolation : les noms du
// chargeur ne survivent pas tous à la compilation, et c'est le geste d'écrire l'import
// qu'on veut interdire. Le lot D rendra la chose plus forte encore en retirant fabric-api
// du chemin de compilation de ces modules ; d'ici là, ceci tient la ligne.
val checkLoaderIsolation = tasks.register("checkLoaderIsolation") {
    description = "Échoue si un module commun mentionne un chargeur (plan multiloader, lot A)"
    group = "verification"
    val communs = listOf("api", "platform", "core", "client", "compat")
    val sources = communs.map { project(":$it").file("src/main/java") }
    // Les DEUX chargeurs, depuis le lot E : la règle n'a jamais visé Fabric en
    // particulier, elle vise le fait de nommer un chargeur dans du code commun.
    val chargeurs = listOf("net.fabricmc", "net.neoforged")
    inputs.files(sources)
    doLast {
        val fautifs = mutableListOf<String>()
        sources.forEach { racine ->
            if (!racine.exists()) return@forEach
            racine.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { f ->
                val contenu = f.readText(Charsets.UTF_8)
                chargeurs.filter { contenu.contains(it) }.forEach { chargeur ->
                    fautifs.add(rootDir.toPath().relativize(f.toPath()).toString()
                        + " (" + chargeur + ")")
                }
            }
        }
        if (fautifs.isNotEmpty()) {
            throw GradleException(
                "Ces fichiers de modules communs nomment un chargeur :\n  " +
                    fautifs.joinToString("\n  ") +
                    "\nLa question se pose dans platform/, la réponse s'écrit dans " +
                    "fabric/ ou neoforge/. Voir docs/plan-multiloader.md §2."
            )
        }
    }
}

// La règle absolue n°3 de coding-standards, enfin mécanisée.
//
// « Aucune exécution de graphe côté client. Le paquet fr.blueprint.client ne doit contenir
// ni compilateur, ni VM, ni évaluation de nœud. » Elle porte le modèle de sécurité entier —
// principe P1, décision AD2, exigence FR17 — et elle était tenue par la seule revue, alors
// que trois autres frontières bien moins critiques ont leur tâche depuis longtemps
// (checkApiIsolation, checkLoaderIsolation, checkPlatformIsolation).
//
// Le moment est venu parce que la tentation vient d'augmenter : l'épic 21 a appris au client
// à peindre des valeurs sans aller-retour. Le pas suivant — « et si le client calculait cette
// petite valeur lui-même ? » — est celui qu'aucune revue ne rattrape à tous les coups.
//
// Sur les PAQUETS et non sur une liste de classes : une liste se contourne en ajoutant une
// classe, un paquet non. C'est aussi ce qui a décidé du déplacement de VarValueNbt vers
// core.graph — un format de sérialisation n'est pas de l'exécution, et le laisser dans
// core.vm aurait obligé à écrire une exception dans la règle dès son premier jour.
val checkClientIsolation = tasks.register("checkClientIsolation") {
    description = "Échoue si le module client touche au compilateur ou à la VM (P1, AD2, FR17)"
    group = "verification"
    val sources = project(":client").file("src/main/java")
    val interdits = listOf("fr.blueprint.core.vm", "fr.blueprint.core.compile")
    inputs.files(sources)
    doLast {
        val fautifs = mutableListOf<String>()
        if (sources.exists()) {
            sources.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { f ->
                val contenu = f.readText(Charsets.UTF_8)
                interdits.filter { contenu.contains(it) }.forEach { paquet ->
                    fautifs.add(rootDir.toPath().relativize(f.toPath()).toString()
                        + " (" + paquet + ")")
                }
            }
        }
        if (fautifs.isNotEmpty()) {
            throw GradleException(
                "Ces fichiers du client touchent au compilateur ou à la VM :\n  " +
                    fautifs.joinToString("\n  ") +
                    "\nL'exécution est serveur, l'édition est client (architecture.md P1, " +
                    "AD2, prd.md FR17). Le client affiche des descripteurs et des valeurs " +
                    "qu'on lui envoie ; il ne compile ni n'exécute jamais un graphe."
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkLoaderIsolation, checkClientIsolation)
}

loom {
    // Ce qui compose « le mod » en DÉVELOPPEMENT — et sans quoi les traductions
    // disparaissent.
    //
    // Fabric prend pour racine du mod l'entrée de classpath qui porte le
    // fabric.mod.json. Depuis que ce manifeste a déménagé dans `fabric/` (lot A1), cette
    // racine est `fabric/build/resources/main`, qui ne contient que lui — pendant que
    // `assets/blueprint/lang/` est resté dans `core/build/resources/main`, une autre
    // entrée, invisible au gestionnaire de ressources. Résultat en jeu : les clés
    // s'affichent brutes, « blueprint.editor.diag.ok » au lieu du texte.
    //
    // Le JAR livré n'a jamais eu le problème : il fusionne tous les modules dans une
    // seule archive, où manifeste et assets se retrouvent voisins. C'était donc un bug
    // que seul le développement voyait, et qu'aucun test n'attrape.
    //
    // C'est le pendant exact du bloc `mods` de ModDevGradle côté NeoForge, posé lui dès
    // le premier jour parce que le LinkageError l'avait imposé. Ici rien ne plantait :
    // ça s'est contenté de ne plus traduire.
    mods {
        create("blueprint") {
            listOf(":api", ":platform", ":core", ":client", ":compat", ":fabric")
                    .forEach { sourceSet("main", project(it)) }
        }
    }

    runs {
        configureEach {
            vmArg("-Xmx4G")
            vmArg("-XX:+UseG1GC")
        }
        // Un pseudo FIXE en développement. Sans lui, le client de test en tire un au
        // hasard à chaque lancement — « Player848 », puis « Player123 » — et comme
        // l'UUID hors-ligne se calcule depuis le pseudo, chaque lancement est un JOUEUR
        // DIFFÉRENT. Tout ce qui est rangé par joueur repart alors de zéro : le monde de
        // développement avait accumulé seize identités de jeu de rôle, une par session,
        // et le formulaire de création se rouvrait à chaque fois comme si rien n'avait
        // été enregistré.
        named("client") {
            programArg("--username")
            programArg("Kerlann")
        }
        // Story 1.6 : les tests joués dans un vrai serveur. Sans fenêtre, sans joueur,
        // sans intervention — `./gradlew runGametest` rend un rapport JUnit et un code
        // de sortie. C'est ce qui remplace une partie des vérifications manuelles.
        create("gametest") {
            server()
            name("Game Test")
            source("main")
            vmArg("-Dfabric-api.gametest=true")
            vmArg("-Dfabric-api.gametest.report-file=" +
                    layout.buildDirectory.file("gametest/report.xml").get().asFile.absolutePath)
            runDir("build/gametest/run")
        }
    }
}

// Le MÊME serveur piloté que côté NeoForge, sur le MÊME scénario.
//
// C'est ce qui rend les deux chargeurs comparables : sans une entrée identique, dire
// « ça se comporte pareil » revient à comparer deux souvenirs. La mécanique est décrite
// dans neoforge/build.gradle.kts, y compris pourquoi les commandes attendent le
// démarrage.
//
// `./gradlew runServer -Pblueprint.scenario`
tasks.matching { it.name == "runServer" }.configureEach {
    if (!project.hasProperty("blueprint.scenario")) {
        return@configureEach
    }
    val scenario = rootProject.file("docs/qa/scenario-serveur.txt")
    inputs.file(scenario)
    doFirst {
        // Monde neuf, pour la même raison que côté NeoForge : un scénario rejoué doit
        // partir du même état, sinon les deux chargeurs ne sont plus comparables.
        delete(rootProject.file("run/world"))
        val lignes = scenario.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val sortie = PipedOutputStream()
        val entree = PipedInputStream(sortie, 8192)
        Thread {
            Thread.sleep(30_000)
            lignes.forEach { ligne ->
                sortie.write((ligne + System.lineSeparator()).toByteArray())
                sortie.flush()
                Thread.sleep(3_000)
            }
            Thread.sleep(10_000)
            sortie.write(("stop" + System.lineSeparator()).toByteArray())
            sortie.flush()
        }.apply { isDaemon = true }.start()
        (this as JavaExec).standardInput = entree
    }
}

// Monde neuf à chaque lancement. Sans ça, les blueprints qu'un test a laissés derrière
// lui (échec, donc pas de nettoyage) sont RESTAURÉS par la persistance au démarrage
// suivant et font échouer les runs d'après : les tests s'empoisonnent entre eux.
tasks.named<Delete>("clean") {
    delete(layout.buildDirectory.dir("gametest"))
}
tasks.named("runGametest") {
    doFirst {
        delete(layout.buildDirectory.dir("gametest/run/world"))
        // Du contenu déclaré POUR DE VRAI dans le serveur de test (épic 11).
        //
        // Il n'y en avait aucun : l'épic 11 n'avait pas une seule vérification en jeu, et
        // c'est pourtant le seul code du projet dont l'échec se paie avant l'écran titre.
        // Le lot B du plan multiloader en avait besoin — il déplace la fenêtre
        // d'enregistrement, et déplacer sans filet ce qui décide des identifiants réseau
        // n'était pas défendable.
        //
        // Les mêmes fichiers que la documentation : ils sont déjà validés par
        // ContentExamplesTest, donc un échec ici parle du registre, pas du JSON.
        copy {
            from(rootProject.file("docs/examples/content"))
            into(layout.buildDirectory.dir("gametest/run/blueprint/content"))
        }
    }
}
