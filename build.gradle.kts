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

    version = "0.1.0"
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
}

// JAR unique : api + core + client + compat (testmod exclu). remapJar remappe le tout.
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    listOf(":api", ":core", ":client", ":compat").forEach { path ->
        from(provider { project(path).extensions.getByType<SourceSetContainer>()["main"].output })
    }
}

loom {
    runs {
        configureEach {
            vmArg("-Xmx4G")
            vmArg("-XX:+UseG1GC")
        }
    }
}
