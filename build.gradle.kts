import net.fabricmc.loom.api.LoomGradleExtensionAPI

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
    apply(plugin = "fabric-loom")

    version = "1.0.0"
    group = "fr.blueprint"

    repositories {
        mavenCentral()
    }

    val loomExt = extensions.getByType<LoomGradleExtensionAPI>()

    dependencies {
        "minecraft"("com.mojang:minecraft:$minecraftVersion")
        "mappings"(loomExt.officialMojangMappings())
        "modImplementation"("net.fabricmc:fabric-loader:$loaderVersion")
        "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
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
    implementation(project(path = ":core", configuration = "namedElements"))
    implementation(project(path = ":client", configuration = "namedElements"))
    implementation(project(path = ":compat", configuration = "namedElements"))
    // testmod : uniquement sur le classpath de dev, jamais dans le JAR final
    runtimeOnly(project(path = ":testmod", configuration = "namedElements"))
    // gametest : idem — chargé par runGametest, absent du JAR livré (story 1.6)
    runtimeOnly(project(path = ":gametest", configuration = "namedElements"))
}

// JAR unique : api + core + client + compat (testmod exclu). remapJar remappe le tout.
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    listOf(":api", ":core", ":client", ":compat").forEach { path ->
        from(provider { project(path).extensions.getByType<SourceSetContainer>()["main"].output })
    }
    // La licence MIT exige que son texte accompagne toute redistribution — et un JAR
    // téléchargé est une redistribution. Le champ "license" du fabric.mod.json l'annonce
    // sans le fournir : le déclarer sans le joindre ne remplit pas la condition.
    from(rootProject.file("LICENSE"))
}

loom {
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

// Monde neuf à chaque lancement. Sans ça, les blueprints qu'un test a laissés derrière
// lui (échec, donc pas de nettoyage) sont RESTAURÉS par la persistance au démarrage
// suivant et font échouer les runs d'après : les tests s'empoisonnent entre eux.
tasks.named<Delete>("clean") {
    delete(layout.buildDirectory.dir("gametest"))
}
tasks.named("runGametest") {
    doFirst {
        delete(layout.buildDirectory.dir("gametest/run/world"))
    }
}
