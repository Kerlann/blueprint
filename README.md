# Blueprint

Mod Fabric pour Minecraft 1.21.11.

## Stack

| Composant   | Version        |
|-------------|----------------|
| Minecraft   | 1.21.11        |
| Fabric Loom | 1.13.6         |
| Fabric Loader | 0.18.2       |
| Fabric API  | 0.139.4+1.21.11 |
| Java        | 21             |
| Gradle      | 8.14           |

Mappings : official Mojang mappings.

## Build

```bash
./gradlew build
```

Le JAR est produit dans `build/libs/`.

## Lancer en dev

```bash
./gradlew runClient
./gradlew runServer
```

## Structure

Projet multi-module (story 1.1) — le build produit **un seul JAR** (`build/libs/blueprint-<version>.jar`) :

```
api/      surface publique pour les mods tiers (publiable seule : blueprint-api)
core/     entrypoint main, modèle, registre, compilateur, VM, BScript, persistance
client/   entrypoint client, éditeur visuel
compat/   intégrations conditionnelles avec des mods tiers
testmod/  mod d'exemple validant l'api (exclu du JAR final)
```

Le module `api` ne peut pas référencer l'implémentation : la tâche `:api:checkApiIsolation`
(branchée sur `check`) fait échouer le build sinon.

```bash
./gradlew :api:publishToMavenLocal   # publie fr.blueprint:blueprint-api
```

## Documentation

Le dossier de conception complet (méthode BMAD) est dans [`docs/`](docs/README.md) :
PRD, architecture, spec BScript, API d'extension, spec UX de l'éditeur, stories.
