# Blueprint

**Un éditeur de logique par nœuds, en jeu, pour Minecraft 1.21.11 (Fabric).**

On pose des nœuds, on les relie, on déclare des variables typées — et le graphe s'exécute
côté serveur sur de vrais événements du monde. Tout graphe se compile en un script texte
lisible (**BScript**) et tout BScript se re-parse en graphe. Les autres mods déclarent
leurs propres nœuds sans dépendance dure, et un mod retiré ne casse jamais un graphe
existant : ses nœuds deviennent des **fantômes** qui reprennent vie à la réinstallation.

> **État : les neuf épics du PRD sont livrés** — 49 stories, 48 gates QA, ~250 tests
> headless et 5 gametests dans un serveur réel. Reste la session de vérification
> visuelle, listée dans [`docs/README.md`](docs/README.md).

---

## Démarrer

```bash
./gradlew runClient
```

Puis, en jeu :

| | |
|---|---|
| `/blueprint demo` puis `/blueprint-edit blueprint:demo` | voir un exemple qui marche |
| `/blueprint-edit create mon_premier` | créer le sien |
| `F6` | rouvrir le dernier édité |

Le guide pas à pas est dans [`docs/getting-started.md`](docs/getting-started.md) — premier
blueprint en dix minutes, raccourcis, dépannage.

## Ce que le mod sait faire

- **Éditeur complet** : palette, câblage typé, littéraux sur les nœuds, variables et
  portées, annuler/rétablir, copier/coller en BScript, diagnostics cliquables, vue script,
  commentaires, minimap, thème JSON rechargeable, navigation au clavier.
- **Exécution sûre** : compilation en IR, VM bornée par un budget de fuel, suspension qui
  survit à un redémarrage, blueprint glouton ou en faute désactivé automatiquement.
- **~80 nœuds** livrés (flux, maths, logique, chaînes, monde, entité, joueur, item, texte)
  et 10 événements du monde, plus `/bpc <nom>` pour déclencher un graphe à la commande.
- **Multijoueur** : synchronisation du registre au login, ouverture et enregistrement par
  paquets avec verrou optimiste, garde de graphe et quotas côté serveur.
- **Extensible** de trois façons : builder Java, annotation `@BlueprintNode`, ou simple
  JSON de datapack — voir [`docs/extension-api.md`](docs/extension-api.md).
- **Débogage** : points d'arrêt, pas-à-pas et valeurs affichées dans l'éditeur ;
  profileur par nœud ; audit des nœuds `ADMIN`.

## Construire et tester

```bash
./gradlew build          # 6 modules, ~250 tests headless, couverture, docs générées
./gradlew runGametest    # 5 tests dans un vrai serveur, sans fenêtre
./gradlew runClient      # jouer
```

`build` échoue si : un test tombe, la couverture de `core` passe sous 80 %, le module
`api` référence l'implémentation, la référence des nœuds ne correspond plus au registre,
ou la surface publique de l'api a changé sans être régénérée. Ces deux derniers points se
régénèrent avec `-Dblueprint.regenDocs=true`.

> **Ne construisez pas pendant que le jeu tourne** : Gradle réécrit les jars sous la JVM
> et le classloader tombe sur un zip à moitié écrit (`ZipException: invalid LOC header`).

## Structure

Multi-module, mais **un seul JAR** en sortie (`build/libs/blueprint-<version>.jar`) :

```
api/       surface publique pour les mods tiers (publiable seule : blueprint-api)
core/      entrypoint serveur, modèle, registre, compilateur, VM, BScript, persistance, réseau
client/    entrypoint client, éditeur visuel
compat/    intégrations conditionnelles avec des mods tiers
testmod/   mod d'exemple validant l'api (exclu du JAR final)
gametest/  tests joués dans un vrai serveur (exclu du JAR final)
```

Le module `api` ne peut pas référencer l'implémentation : `:api:checkApiIsolation`,
branchée sur `check`, fait échouer le build sinon. Sa surface publique est figée dans
[`docs/api-surface.txt`](docs/api-surface.txt) et comparée à chaque construction.

```bash
./gradlew :api:publishToMavenLocal   # publie fr.blueprint:blueprint-api
```

## Stack

| Composant | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loom | 1.13.6 |
| Fabric Loader | 0.18.2 |
| Fabric API | 0.139.4+1.21.11 |
| Java | 21 |
| Gradle | 8.14 |

Mappings officiels Mojang. Les pièges de nommage rencontrés sont consignés dans
[`docs/architecture/tech-stack.md`](docs/architecture/tech-stack.md) — à lire avant
d'appeler une API du jeu.

## Documentation

Le dossier de conception complet (méthode BMAD) est dans [`docs/`](docs/README.md) :
brief, PRD, architecture, spécification BScript, API d'extension, spec UX de l'éditeur,
stories et gates QA.

| Pour | Lire |
|---|---|
| Jouer | [`docs/getting-started.md`](docs/getting-started.md) |
| Chercher un nœud | [`docs/node-reference.md`](docs/node-reference.md) *(généré)* |
| Écrire un mod compagnon | [`docs/extension-api.md`](docs/extension-api.md) |
| Comprendre les choix | [`docs/architecture.md`](docs/architecture.md) |
| Savoir ce qui a changé | [`CHANGELOG.md`](CHANGELOG.md) |
| Savoir où en est le projet | [`docs/rapport-de-fin.md`](docs/rapport-de-fin.md) |

## Licence

[MIT](LICENSE) — © 2026 Kerlann.

Tu peux l'utiliser, le modifier, le redistribuer et le vendre, y compris dans un
projet fermé ; la seule obligation est de conserver l'avis de copyright. Un mod
compagnon qui s'appuie sur `blueprint-api` choisit librement sa propre licence.
