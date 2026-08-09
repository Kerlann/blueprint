# Publier sur Modrinth

Deux choses à régler avant la page elle-même : **le nom** et **l'espace de noms**. Ils ne
coûtent pas la même chose, et les confondre est le piège de cette étape.

## Décidé : **Blueprints**, slug `blueprints-mod`

Le nom affiché est **Blueprints**, le slug **`blueprints-mod`** — vérifié libre.

Le singulier était impossible : `modrinth.com/mod/blueprint` est occupé par la
bibliothèque **Blueprint** de Team Abnormals (Forge/NeoForge), **6,86 millions de
téléchargements**. Le pluriel nu l'est aussi, par un mod **Fabric** homonyme (~27 000
téléchargements) — d'où le suffixe.

> **La collision est assumée, pas ignorée.** Le mod se retrouve à une lettre de deux
> projets établis, un sur chaque chargeur visé. Il faut s'attendre à des tickets ouverts
> au mauvais endroit, et le jour où le module NeoForge sort, à partager le terrain de la
> bibliothèque à 6,86 M. Un nom distinct — `blockprint` était libre — aurait évité tout
> cela ; le choix a été fait de rester dans la famille.

Ce qui doit changer et ce qui n'a pas à changer :

| | Faut-il le changer ? | Ce qu'il en coûte |
|---|---|---|
| Le **slug** Modrinth et le titre affiché | **oui**, l'un est occupé | rien du tout |
| Le **`id`** de `fabric.mod.json` | non | Fabric n'exige l'unicité que par chargeur, et l'autre Blueprint n'existe pas sur Fabric |
| L'**espace de noms** `blueprint:` | non, et **surtout pas** | c'est le préfixe de tous les identifiants de nœuds (`blueprint:flow/for`), donc de **tout `.bp` déjà écrit** et de tout graphe enregistré |

Un renommage d'espace de noms se migrerait — `SchemaMigrations` existe pour ça — mais il
casserait chaque fichier partagé entre-temps, pour un gain purement cosmétique. **La page
peut porter un nom que le code ne porte pas** ; c'est le cas courant, et ça se dit en une
ligne dans la page.

## Noms libres sur Modrinth

Testés contre l'API, tous en `404` — donc disponibles au moment du test :

| Nom | Ce qu'il dit |
|---|---|
| **Blockprint** | garde l'association « blueprint » et la rend minecraftienne d'un seul caractère |
| **Redprint** | même idée, du côté de la redstone — le public visé est celui qui a arrêté la redstone |
| **Nodesmith** | met le nœud au centre, sans jeu de mots |
| **BScript** | le nom du format texte du mod ; cohérent, mais vend l'écrit plutôt que l'éditeur |
| **Nodeforge**, **Nodegraph**, **Wirecraft**, **Circuitry**, **Visualscript** | corrects, moins distinctifs |

Occupés : `nodeworks`, `lattice`, `weaver`, `flowcraft`, `blueprint`.

Revérifier avant de créer le projet, ces choses partent vite :

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://api.modrinth.com/v2/project/<slug>
# 404 = libre, 200 = pris
```

---

# La page

## Les champs à remplir

| Champ | Valeur |
|---|---|
| **Nom** | `Blueprints` |
| **Slug** | `blueprints-mod` — **pas** le nom en minuscules : `blueprints` est déjà pris |
| **Résumé** | 256 caractères maximum, visible dans les résultats de recherche — c'est lui qui décide du clic |
| **Description** | le corps Markdown, ci-dessous |
| **Catégories** | `Utility`, `Game mechanics`, `Library` (le mod expose une API de nœuds) |
| **Environnement** | client **requis** (l'éditeur) et serveur **requis** (l'exécution) |
| **Licence** | MIT |
| **Liens** | dépôt, issues, et `docs/` en guise de wiki |

## Ce que Modrinth accepte dans la description

- Markdown, plus un HTML restreint et nettoyé (`<img>`, `<p align>`, `<details>` passent ;
  scripts et styles, non).
- **Les images doivent être hébergées ailleurs.** Deux voies : les déposer dans la galerie
  du projet et reprendre l'URL CDN, ou pointer sur
  `raw.githubusercontent.com/Kerlann/blueprint/main/docs/images/...`. La seconde garde les
  captures versionnées avec le code — ce qui évite une page qui montre une interface que le
  mod n'a plus.
- Toutes les captures vivent dans `docs/images/` — et **ce document ne les énumère pas**.
  Il l'a fait deux fois, et deux fois la liste s'est retrouvée à citer des fichiers
  renommés ou supprimés. Le dossier fait foi ; une copie de son contenu dans une phrase ne
  fait que vieillir.

- **Pour l'image mise en avant** (celle de la carte, dans les résultats de recherche) :
  `editeur-graphe.png`, mais **recadrée sur les nœuds**. Telle quelle, le graphe n'occupe
  qu'environ 40 % de la largeur et la moitié de la hauteur — réduite à la taille d'une
  carte, elle se lit comme un rectangle noir. Le test qui tranche : réduire à 400 px et
  regarder à un mètre. Toutes ces captures sont en 1920 et pensées pour être lues de près,
  ce qui est le bon réflexe pour la documentation et le mauvais pour une vitrine.

## Langue

Modrinth ne gère pas la traduction des descriptions. Le public y est majoritairement
anglophone : **anglais d'abord**, français dans un `<details>` replié en fin de page. Le
brouillon ci-dessous suit cet ordre.

## Ce qu'il faut montrer, et dans quel ordre

Une page de mod se lit en dix secondes avant d'être lue vraiment. L'ordre qui suit place en
tête ce qui distingue ce mod des autres éditeurs par nœuds :

1. **Une capture de l'éditeur**, tout en haut, avant la moindre phrase.
2. **Ce que c'est**, en deux lignes.
3. **Le graphe ↔ texte**, avec les deux côte à côte — c'est la garantie centrale du produit
   et personne d'autre ne la propose.
4. **Le coût pour un serveur**, chiffré. C'est *la* question que se pose un
   administrateur, et y répondre avant qu'elle soit posée vaut mieux que n'importe quel
   superlatif.
5. Le reste : écrans, contenu déclaré, API, débogage.
6. Installation, licence, liens.

---

# Brouillon du corps de la page

<!-- Copier ce qui suit dans la description Modrinth. Rien à remplacer. -->

![The node editor](https://raw.githubusercontent.com/Kerlann/blueprint/main/docs/images/editeur-graphe.png)

# Blueprints

**An in-game node editor for Minecraft 1.21.11 (Fabric).** Place nodes, wire them, declare
typed variables — and the graph runs **server-side** on real world events.

> Not related to *Blueprint* by Team Abnormals, nor to the mod called *Blueprints* — the
> names collide, the projects do not. This one is `blueprints-mod`. Its mod id and
> namespace stay `blueprint`, because renaming them would break every graph already
> written.

## Graphs are text, and text is graphs

Every graph compiles to a readable script — **BScript** — and every BScript parses back
into the same graph. Not an export: a **round trip**, guarded by tests on every build.

```
node "add_score" blueprint:math/add {
    a = var "score"
    b = 10
}
```

That is what makes a graph reviewable in a pull request, diffable, and pasteable into a
chat window. Copy in the editor, paste in Discord, paste back into the editor.

## What it costs a server

A node editor invites the assumption of a slow interpreter. These are measurements, taken
by benchmarks committed to the repository that **fail the build** if they drift.

| Active graphs on `server_tick` | Scheduling time per tick | Share of the 50 ms budget |
|---|---|---|
| 50 | ~0.25 ms | **0.5 %** |
| 200 | ~0.6 ms | **1.2 %** |

Every graph runs on a **fuel budget**. A runaway loop is cut and reported, not left to
take the server down. A graph that faults repeatedly disables itself.

And you can check it yourself, in-game, in three commands:

```
/blueprint bench
/bpc bench
/blueprint profile blueprint:bench
```

The bench is a real graph — three nested loops — that you can open, read and edit in the
editor. Nothing is hidden behind a benchmark harness you cannot see.

## What is in the box

- **A full editor** — palette, typed wiring, literals on nodes, variables and scopes,
  undo/redo, copy-paste as BScript, clickable diagnostics, script view, comments, minimap,
  a reloadable JSON theme, keyboard navigation.
- **239 node types** — screens (34), maths (20), world (20), player (19), strings (12),
  flow (12), entities (12), vectors (11), logic (10), lists, maps, text, items,
  scoreboards — **including 26 events**, plus `/bpc <name>` to fire a graph from a command.
- **A screen designer** — 12 widget kinds (labels, buttons, lists, dropdowns, sliders,
  toggles, inputs, progress bars, item slots, images, entity previews, panels), with
  **bindings**: a label can *follow* a variable instead of being written by a node.
- **Declared content** — items and blocks from JSON files, no code.
- **Debugging** — breakpoints, single-step, live values in the editor, a per-node profiler.
- **Multiplayer from the start** — registry sync on login, optimistic locking on save,
  server-side guards and quotas.

![The screen designer](https://raw.githubusercontent.com/Kerlann/blueprint/main/docs/images/concepteur-ecran.png)

## Extensible three ways

Other mods add their own nodes with a Java builder, a `@BlueprintNode` annotation, or a
plain datapack JSON — **no hard dependency required**.

Remove such a mod and existing graphs do not break: its nodes become **ghosts** that keep
their wiring and come back to life when the mod is reinstalled.

## Install

1. [Fabric Loader](https://fabricmc.net/) 0.16+ and Java 21
2. [Fabric API](https://modrinth.com/mod/fabric-api) 0.139.4+
3. Drop the jar in `mods/`

Then `/blueprint-edit create my_first`, or press **F6**. A ten-minute guide is in
[`docs/getting-started.md`](https://github.com/Kerlann/blueprint/blob/main/docs/getting-started.md).

Server-side only? The editor is client-side, so players need the mod to edit — but graphs
run on the server, and a vanilla client can play on a server running them.

## Licence

MIT. [Source](https://github.com/Kerlann/blueprint) ·
[Issues](https://github.com/Kerlann/blueprint/issues)

<details>
<summary><b>En français</b></summary>

**Un éditeur de logique par nœuds, en jeu, pour Minecraft 1.21.11 (Fabric).** On pose des
nœuds, on les relie, on déclare des variables typées — et le graphe s'exécute côté serveur
sur de vrais événements du monde.

Tout graphe se compile en un script texte lisible (**BScript**) et tout BScript se re-parse
en graphe : c'est un aller-retour, pas un export, et il est vérifié à chaque construction.
Un graphe se relit donc dans une pull request, se compare, et se colle dans un salon.

Le coût pour un serveur est mesuré par des bancs commités qui **font échouer la
construction** s'ils dérivent : 200 graphes branchés sur `server_tick` prennent ~0,6 ms par
tick, soit 1,2 % du budget. Chaque graphe tourne sur un budget de carburant — une boucle
folle est coupée et signalée, jamais laissée faire tomber le serveur.

Le reste : 239 types de nœuds (dont 26 événements), un concepteur d'écrans à 12 types de widgets, du contenu déclaré en
JSON, points d'arrêt et profileur, et une API pour que les autres mods ajoutent leurs
nœuds sans dépendance dure — un mod retiré laisse des nœuds **fantômes** qui reprennent vie
à la réinstallation.

Guide de démarrage :
[`docs/getting-started.md`](https://github.com/Kerlann/blueprint/blob/main/docs/getting-started.md).

</details>

---

# Le résumé (256 caractères)

Le champ « Summary » de Modrinth est du **texte brut** : ni mise en forme, ni retour à la
ligne, ni lien. Il est donc écrit ici sur **une seule ligne**, sans citation ni repli — une
citation Markdown mettrait des `>` en tête et des sauts de ligne au milieu, et Modrinth
refuse le tout. C'est arrivé une fois ; le format de ce bloc est la correction.

```
In-game node editor: wire logic, declare typed variables, run it server-side on real world events. 239 nodes, and every graph is readable text and back again. Screen designer, declared content, profiler, and an API for other mods to add nodes.
```

*(243 caractères — la limite est 256, et tout y est en ASCII)*

> **Les chiffres se revérifient, ils bougent.** Le total des nœuds a deux sources qui
> doivent concorder : l'en-tête de `docs/node-reference.md`, qui est généré et fait
> échouer la construction s'il dérive, et la ligne de démarrage du serveur. Attention au
> piège : le serveur de développement **Fabric** en annonce cinq de plus, parce que
> `testmod` en ajoute — et testmod n'est jamais livré. C'est le compte sans lui qui va sur
> la page.

---

# Avant de publier

- [ ] Slug `blueprints-mod` revérifié libre juste avant la création
- [x] Captures déplacées dans `docs/images/` et commitées
- [x] `name` / `displayName` mis à `Blueprints` dans les deux manifestes (`id` inchangé)
- [x] Icône : `core/src/main/resources/assets/blueprint/icon.png`, déclarée par
      `icon` (Fabric) et `logoFile` (NeoForge) — un seul fichier pour les deux JARs
- [ ] Téléverser cette même icône dans le champ « Icon » de Modrinth (elle ne se déduit
      pas du jar)
- [ ] Une capture de l'éditeur, une du concepteur d'écrans, une de la vitrine en jeu
- [ ] Version taguée `v1.0.x` — le workflow `build.yml` ne construit que sur tag
- [ ] Le jar téléversé est celui de l'action, pas un jar local
- [ ] Dépendance Fabric API déclarée **requise** sur la version Modrinth
- [ ] **Ne pas annoncer NeoForge.** Le module existe et démarre (serveur et client), mais
      aucune partie n'y a été jouée, aucun écran affiché, aucun gametest — et trois écarts
      connus subsistent (paquets volumineux non fragmentés, `player_sleep` muet, casse de
      bloc signalée avant plutôt qu'après). Voir `docs/plan-multiloader.md`. La page reste
      Fabric tant que ces trois lignes ne sont pas fermées.
