# Architecture — Blueprint

**Version :** 1.0 — 2026-08-02
**Entrées :** `docs/prd.md`, `docs/brief.md`
**Shards chargés par l'agent Dev :** `docs/architecture/tech-stack.md`, `coding-standards.md`, `source-tree.md`

---

## 1. Introduction

Ce document décrit l'architecture technique complète de Blueprint : modules, modèle de
données, chaîne de compilation, machine virtuelle, réseau, persistance, rendu de
l'éditeur et surface d'extension.

**Projet existant.** Le dépôt contient un squelette Fabric fonctionnel (Loom 1.13.6,
Minecraft 1.21.11, Fabric Loader 0.18.2, Fabric API 0.139.4+1.21.11, Java 21,
**mappings officiels Mojang**). L'architecture ci-dessous est un *brownfield* léger :
elle réorganise le projet en modules Gradle et ajoute tout le reste.

**Conséquence des mappings Mojang** — les noms de classes du jeu sont ceux des mappings
officiels Mojang **1.21.11**, pas ceux de Yarn : `Identifier` (nom Mojang depuis 1.21.x ;
s'appelait `ResourceLocation` avant — vérifié en story 1.2), `CompoundTag` (≠ `NbtCompound`),
`GuiGraphics` (≠ `DrawContext`), `Level` / `ServerLevel` (≠ `World`),
`SavedData` (≠ `PersistentState`), `Component` (≠ `Text`), `ItemStack`, `ServerPlayer`.
Toute story qui cite du code doit respecter cette convention ; en cas de doute sur un
nom, vérifier dans le JAR mergé de Loom (`javap`) — voir `tech-stack.md`.

---

## 2. Vue d'ensemble

### 2.1 Principes directeurs

| # | Principe | Conséquence concrète |
|---|---|---|
| P1 | **L'exécution est serveur, l'édition est client** | Le client ne compile ni n'exécute jamais un graphe reçu ; il n'affiche que des descripteurs |
| P2 | **Le graphe est une donnée, pas du code** | Aucun chargement de classe à l'exécution, aucune réflexion dans la boucle chaude |
| P3 | **Rien n'est illimité** | Fuel, profondeur, taille de paquet, nombre de nœuds, quota d'allocation |
| P4 | **Un identifiant inconnu se conserve, ne se supprime pas** | Nœuds fantômes ; retirer un mod ne détruit pas le travail du joueur |
| P5 | **L'API publique ne voit pas l'implémentation** | Module `api` sans dépendance vers `core` ; barrière vérifiée au build |
| P6 | **Texte et graphe sont deux vues de la même chose** | BScript est un citoyen de première classe, pas un export secondaire |

### 2.2 Flux principal

```
                   ┌──────────────── CLIENT ────────────────┐
                   │  BlueprintEditorScreen                 │
                   │   canvas · palette · variables · debug │
                   │            ▲            │              │
                   │   descripteurs      patchs d'édition   │
                   └───────────│────────────│───────────────┘
                               │            ▼
  ═════════════════════ réseau (CustomPacketPayload) ═══════════════════
                               │            │
                   ┌───────────┴────────────▼───────────────┐
                   │              SERVEUR                   │
                   │                                        │
                   │  NodeRegistry ◄── BlueprintPlugin      │  ← mods tiers
                   │       │                (entrypoint)    │
                   │       ▼                                │
                   │  Blueprint (modèle) ◄──► BScript       │
                   │       │                                │
                   │       ▼  Compiler                      │
                   │  Ir (instructions)                     │
                   │       │                                │
                   │       ▼  BlueprintVm  ◄── Scheduler ◄── événements MC
                   │  exécution bornée, suspendable         │
                   │       │                                │
                   │       ▼  BlueprintStorage (SavedData)  │
                   └────────────────────────────────────────┘
```

### 2.3 Modules Gradle

| Module | Dépendances | Contenu | Publié ? |
|---|---|---|---|
| `api` | Minecraft uniquement | Interfaces et types publics (`fr.blueprint.api.*`) | Oui (Maven, pour les mods tiers) |
| `platform` | `api` | Ce que le code commun demande au chargeur : des interfaces, aucune réponse | Non |
| `core` | `api`, `platform` | Modèle, registre, compilateur, VM, BScript, persistance, réseau serveur, nœuds standard | Non |
| `client` | `core` | Éditeur, rendu, réseau client | Non |
| `compat` | `core`, `platform` | Intégrations conditionnelles par mod tiers | Non |
| `fabric` | tous | Point d'entrée Fabric et implémentations de `platform` | Non |
| `neoforge` | tous | Idem pour NeoForge — **écrit et compilé, jamais exécuté** | Non |
| `testmod` | `api` | Mod d'exemple validant l'API en test d'intégration | Non |

Deux JARs : `blueprint` (Fabric) et `blueprint-neoforge`, chacun embarquant
`api` + `platform` + `core` + `client` + `compat` plus son module de chargeur. `api` est
aussi publié seul pour être consommé en `compileOnly` par les mods tiers.

**Aucun module commun ne nomme un chargeur** — vérifié à la compilation (leur classpath
n'a ni fabric-api ni NeoForge) et par la tâche `checkLoaderIsolation`. La frontière se
traverse dans deux sens :

- le code commun **appelle** la plateforme (chemins, mods présents) via
  `Platform`, résolu par `ServiceLoader` ;
- la plateforme **appelle** le code commun (initialisation, événements du monde,
  commandes) par simple appel — le module du chargeur est le point d'entrée, donc c'est
  lui qui pousse, et il n'y a rien à découvrir.

---

## 3. Modèle de données

### 3.1 Entités

```java
// api
record BlueprintId(Identifier value) {}

final class Blueprint {
    BlueprintId id;
    BlueprintMeta meta;          // auteur, description, version, permission plafond
    boolean enabled;
    int revision;                // verrouillage optimiste pour les patchs
    Map<UUID, Node> nodes;
    Set<Link> links;
    Map<String, Variable> variables;
    List<CommentBox> comments;
}

final class Node {
    UUID uuid;
    Identifier typeId;     // conservé même si absent du registre
    Vec2 position;
    Map<String, LiteralValue> literals;   // valeurs des pins d'entrée non connectés
    CompoundTag config;                   // configuration libre du nœud
}

record Link(UUID fromNode, String fromPin, UUID toNode, String toPin) {}

record Variable(String name, PinType type, LiteralValue defaultValue,
                VarScope scope, boolean replicated) {}

enum VarScope { LOCAL, GRAPH, WORLD, PLAYER }
```

Le `Node` **ne contient pas ses pins** : les pins viennent du `NodeType` résolu depuis
le registre. C'est ce qui permet à un mod d'ajouter un pin sans invalider les graphes
existants, et c'est ce qui rend le nœud fantôme possible.

### 3.2 Système de types

```java
public interface PinType {
    Identifier id();
    Class<?> javaType();
    int color();                 // ARGB, palette accessible
    PinShape shape();            // EXEC, CIRCLE, DIAMOND, ARRAY, MAP
    String translationKey();
    boolean isAssignableFrom(PinType other);
    LiteralValue defaultValue();
}
```

- **Conversions implicites** déclarées dans un graphe de coercition :
  `int → long → double`, `player → entity`, `T → list<T>` interdite (explicite requise).
- **Joker `any`** : résolu au câblage. Un nœud peut déclarer un *groupe de jokers*
  (ex. `list<T>` en entrée et `T` en sortie) ; résoudre un pin du groupe résout tout le groupe.
- **Génériques** : `list<T>` et `map<K,V>` sont des `ParameterizedPinType` ; l'assignabilité
  est invariante (pas de covariance, pour éviter les pièges d'écriture).

### 3.3 Cardinalité des liens

| Pin | Sortant | Entrant |
|---|---|---|
| `EXEC` | ≤ 1 lien | N liens |
| `DATA` | N liens | ≤ 1 lien (sinon littéral) |

### 3.4 Sérialisation

- Format : **NBT**, compressé gzip, avec `schemaVersion` en racine.
- Un registre `SchemaMigration` applique en chaîne les migrations `v(n) → v(n+1)`.
- Les `LiteralValue` sont encodées par un `Codec` fourni par le `PinType` (les mods tiers
  fournissent le codec de leurs types).
- Un identifiant de nœud inconnu **n'est jamais supprimé** au chargement ; il devient fantôme.

---

## 4. Registre et surface d'extension

Voir `docs/extension-api.md` pour le contrat détaillé et les exemples.

### 4.1 Cycle de vie

```
FabricLoader charge les entrypoints "blueprint"
        │
        ▼
BlueprintPlugin#registerTypes(PinTypeRegistry)      ← les types d'abord
        │
        ▼
BlueprintPlugin#registerNodes(NodeRegistry)         ← puis les nœuds
        │
        ▼
BlueprintPlugin#registerEvents(EventRegistry)
        │
        ▼
Registres figés (freeze) + calcul du hash + génération des descripteurs
        │
        ▼
Chargement des nœuds datapack (rechargeable à /reload, couche séparée)
```

Un plugin qui lève une exception est isolé : le chargement continue, l'erreur est
journalisée avec le nom du mod, et ses nœuds deviennent indisponibles (donc fantômes
dans les graphes qui les utilisent) — jamais un crash au démarrage.

### 4.2 Trois niveaux de déclaration

| Niveau | Pour qui | Coût | Puissance |
|---|---|---|---|
| Builder Java `NodeType.builder(...)` | Auteurs de mods | ~10 lignes | Totale |
| Annotation `@BlueprintNode` sur méthode statique | Auteurs de mods pressés | ~3 lignes | Élevée (pins déduits de la signature) |
| JSON de datapack | Modpackers, datapackers | 0 Java | Nœuds **composites** uniquement |

Les nœuds JSON sont composites : leur corps est une séquence de nœuds existants ou un
fragment BScript. Ils ne peuvent pas appeler du code arbitraire, ce qui borne leur
permission à `GAMEPLAY` (FR / story 8.2).

### 4.3 Descripteurs

Le client ne connaît que des `NodeDescriptor` : identifiant, catégorie, clés de traduction,
pins, permission, drapeaux. Aucune classe du mod fournisseur n'est requise côté client.
C'est ce qui permet à un joueur d'éditer un blueprint sur un serveur dont il n'a pas tous les mods.

---

## 5. Compilation

### 5.1 Chaîne

```
Blueprint ──► GraphValidator ──► Diagnostics
     │                              │ (bloquant si ERROR)
     ▼
 Linéarisation du flux EXEC
     │
     ▼
 Allocation de slots pour chaque pin DATA de sortie
     │
     ▼
 Ordonnancement des nœuds purs (avant leur consommateur, mémoïsés par étape)
     │
     ▼
 Émission des instructions  ──►  Ir  ──► cache (sérialisable)
```

### 5.2 Jeu d'instructions

Machine **à registres**, pas à pile : chaque pin de sortie de données correspond à un
slot indexé de la frame courante. Plus simple à générer depuis un graphe, plus facile à
inspecter au débogage.

| Instruction | Opérandes | Effet |
|---|---|---|
| `CONST` | `slot`, valeur | Charge un littéral |
| `CALL` | `typeId`, slots d'entrée, slots de sortie | Exécute un nœud, décrémente le fuel selon son coût |
| `JMP` | `label` | Saut inconditionnel |
| `JMP_IF` | `slot`, `label` | Saut conditionnel |
| `LOAD_VAR` / `STORE_VAR` | portée, nom, `slot` | Accès variable |
| `FRAME_PUSH` / `FRAME_POP` | taille | Appel de fonction/macro |
| `YIELD` | ticks ou condition | Suspend l'exécution |
| `RETURN` | — | Fin de l'exécution courante |

Chaque instruction porte l'`UUID` du nœud source : c'est le lien entre l'exécution, les
diagnostics et le surlignage du débogueur.

### 5.3 Nœuds purs

Un nœud pur (aucun pin `EXEC`) est évalué **à la demande** lors de la première lecture de
sa sortie, puis mémoïsé pour l'étape d'exécution en cours. Cela évite d'évaluer une
branche non prise et rend le coût prévisible.

---

## 6. Runtime

### 6.1 VM

```java
final class BlueprintVm {
    ExecutionState state;   // pc, slots, pile de frames, fuel restant
    ExecResult step(int fuelBudget);   // RUNNING | SUSPENDED | DONE | FAULT
}
```

- **Fuel** : chaque instruction a un coût (1 par défaut, davantage pour les nœuds coûteux
  comme un `explosion` ou une recherche d'entités). Budget par tick et par blueprint,
  configurable. Épuisement → `SUSPENDED` avec diagnostic ; N dépassements consécutifs →
  blueprint désactivé et admin notifié.
- **Profondeur de frames** bornée.
- **Faute** : une exception levée par un nœud est capturée, journalisée avec le nœud
  fautif, et met le blueprint en état `FAULTED`. Elle ne remonte jamais dans la boucle de tick.

### 6.2 Suspension et persistance de l'exécution

`ExecutionState` est entièrement sérialisable (pc, slots, frames, contexte du trigger sous
forme de références faibles résolvables : UUID de joueur, UUID d'entité, position + dimension).
Les exécutions en attente sont écrites dans la sauvegarde du monde et reprises au
chargement. Si une référence ne se résout plus (joueur parti, entité morte), l'exécution
est annulée proprement avec une entrée de journal.

### 6.3 Ordonnanceur

- Un `BlueprintScheduler` s'exécute à la fin du tick serveur.
- Budget global partagé, réparti entre les exécutions prêtes ; les exécutions non servies
  ce tick le sont au suivant (famine évitée par une file FIFO).
- Statistiques par blueprint : temps moyen, pic, instructions, exécutions actives.

### 6.4 Sécurité

| Vecteur | Contrôle |
|---|---|
| Boucle infinie | Fuel par tick |
| Récursion infinie | Profondeur de frames |
| Explosion mémoire | Quota de taille des listes/chaînes par exécution |
| Escalade de privilèges | Niveau de permission par nœud + plafond par blueprint + config serveur |
| Exécution de commande | Nœud `execute_command` en `ADMIN`, journalisé (NFR15) |
| Import malveillant | Validation complète + re-application du plafond de permission à l'import |
| Client hostile | Toute entrée réseau revalidée serveur ; aucune confiance au client |

---

## 7. BScript

Voir `docs/bscript-spec.md` pour la grammaire complète.

- **Générateur** : `Blueprint → Ast → texte`, sortie déterministe. Reconstruction des
  structures de contrôle (`if`/`while`/`for`) par reconnaissance de motifs sur le flux
  exec ; repli sur étiquettes et `goto` explicites si le motif n'est pas structuré.
- **Parseur** : `texte → Ast → Blueprint`. Un identifiant de nœud inconnu produit un nœud
  fantôme au lieu d'un échec. Mise en page automatique déterministe (tri topologique en
  couches) si les positions ne sont pas fournies.
- **Positions** : émises en métadonnées structurées (`@pos`), donc le round-trip préserve
  la mise en page.
- Le presse-papier de l'éditeur utilise BScript : on peut coller des nœuds depuis Discord.

---

## 8. Réseau

Tous les paquets sont des `CustomPacketPayload` avec un `StreamCodec<RegistryFriendlyByteBuf, T>`,
enregistrés via `PayloadTypeRegistry.playC2S()` / `playS2C()`.

| Paquet | Sens | Rôle |
|---|---|---|
| `RegistryHashS2C` | S→C | Hash du registre au login |
| `RegistrySyncS2C` | S→C | Descripteurs, fragmentés et compressés, si le hash diverge |
| `OpenEditorS2C` | S→C | Ouvre l'éditeur avec un graphe |
| `GraphDataS2C` | S→C | Graphe complet, fragmenté |
| `PatchC2S` / `PatchS2C` | ↔ | Opérations d'édition incrémentales |
| `CompileResultS2C` | S→C | Diagnostics |
| `DebugTraceS2C` | S→C | Trace d'exécution pour le débogueur (souscription explicite) |

**Règles :**
- Taille maximale par paquet et par blueprint ; limitation de débit par joueur.
- Verrouillage optimiste : un patch porte le numéro de révision attendu ; en cas de
  divergence, le serveur renvoie une resynchronisation ciblée plutôt que de rejeter le travail.
- Le débogage n'émet que si un client est abonné : coût nul quand il est éteint.

---

## 9. Persistance

| Donnée | Emplacement | Format |
|---|---|---|
| Blueprints du monde | `SavedData` de la sauvegarde (via `SavedDataType` + `Codec`) | NBT gzip |
| Exécutions suspendues | idem | NBT |
| Variables `WORLD` | idem | NBT |
| Variables `PLAYER` | données persistantes du joueur, ≤ 64 Ko | NBT |
| Bibliothèque partagée / exports | `blueprint/` | `.bp` (BScript) |
| Nœuds datapack | `data/<modid>/blueprint/nodes/*.json` | JSON, rechargé à `/reload` |
| Thème de l'éditeur | `assets/blueprint/theme/*.json` | JSON |

Écriture atomique (fichier temporaire + renommage). Une sauvegarde de l'ancienne version
est conservée avant toute migration de schéma.

---

## 10. Éditeur client

Voir `docs/ux-ui-spec.md` pour l'ergonomie détaillée.

### 10.1 Contraintes de rendu

Pas de bibliothèque GUI externe : **ACsGuis est un projet Forge 1.12 et n'est pas
utilisable ici**. L'éditeur est construit sur `Screen` / `GuiGraphics`, avec :

- une hiérarchie de widgets maison minimale (`CanvasWidget`, `NodeWidget`, `PinWidget`,
  `WireLayer`, `PalettePopup`, `SidePanel`) ;
- un **thème JSON à jetons** inspiré de CSS (`assets/blueprint/theme/default.json` :
  couleurs, rayons, épaisseurs, polices) pour rester stylable sans recompiler ;
- un **atlas de géométrie** pour les liens : les courbes de Bézier sont tesselées une
  fois par changement, mises en cache, et dessinées en lot.

> **Point de vigilance 1.21.6+** : le rendu GUI est passé à un état de rendu retenu
> (`GuiRenderState`). Toute géométrie personnalisée (liens, grille) doit passer par un
> `RenderPipeline` enregistré, pas par des appels immédiats. La story 5.1 doit valider ce
> point en premier — c'est le principal risque technique de l'épic 5.

### 10.2 Performance

- **Culling** : seuls les nœuds et liens intersectant la vue sont traités.
- **Niveau de détail** : sous 0,5× de zoom, les nœuds se rendent en boîtes simplifiées
  sans texte de pin.
- **Compilation débouncée** (≈ 300 ms d'inactivité) pour les diagnostics à la volée.
- Cible : 60 fps à 500 nœuds (NFR1), vérifié par un banc de rendu headless.

### 10.3 Édition

Toutes les mutations passent par des `EditOperation` réversibles, ce qui donne
gratuitement l'annuler/rétablir **et** les patchs réseau : la même opération est appliquée
localement et envoyée au serveur.

---

## 11. Arborescence

Voir `docs/architecture/source-tree.md` (shard chargé par l'agent Dev).

---

## 12. Stratégie de test

| Niveau | Périmètre | Outil |
|---|---|---|
| Unitaire | Modèle, types, validateur, compilateur, VM, BScript, codecs | JUnit 5, sans Minecraft en marche |
| Round-trip | Graphe → NBT → graphe, graphe → BScript → graphe, sur 20 graphes de référence | JUnit 5 |
| Fuzzing | Parseur BScript, décodage réseau, chargement NBT corrompu | jqwik |
| Intégration serveur | Commandes, événements, exécution, persistance, `/reload` | Fabric gametest |
| Intégration client | Ouverture de l'éditeur, câblage, palette | Fabric gametest client |
| Performance | Compilation 1 000 nœuds, rendu 500 nœuds, coût par tick | Banc dédié, seuils en CI |
| Compatibilité binaire | Surface de `fr.blueprint.api` comparée à une signature de référence | japicmp |

Cible de couverture : ≥ 80 % sur `core` (NFR13).

---

## 13. Décisions d'architecture

| # | Décision | Alternative écartée | Raison |
|---|---|---|---|
| AD1 | VM à registres sur IR linéaire | Interprétation directe du graphe | Fuel mesurable, suspension possible, débogage par instruction |
| AD2 | Exécution serveur uniquement | Exécution partagée client/serveur | Sécurité : un graphe est une donnée non fiable |
| AD3 | Pins portés par le `NodeType`, pas par le `Node` | Pins copiés dans le graphe | Un mod peut faire évoluer ses nœuds sans casser les graphes |
| AD4 | Nœuds fantômes | Suppression des nœuds inconnus | Retirer un mod ne doit pas détruire le travail du joueur (P4) |
| AD5 | BScript bidirectionnel | Export texte à sens unique | Versionnage, diff, presse-papier, édition avancée |
| AD6 | Trois niveaux d'extension (builder / annotation / JSON) | Une seule API Java | Couvre auteurs de mods **et** modpackers |
| AD7 | Rendu GUI maison + thème JSON | Bibliothèque GUI externe | Aucune bibliothèque viable en Fabric 1.21.11 ; ACsGuis est Forge 1.12 |
| AD8 | Patchs op-based avec révision | Renvoi du graphe complet | Passe à l'échelle, prépare l'édition collaborative |
| AD9 | Module `api` séparé et publié | Tout dans un module | Les mods tiers compilent contre une surface stable et étroite |
| AD10 | Nœuds datapack composites uniquement | Nœuds datapack scriptables | Borne la surface d'attaque et la permission |
| AD11 | Langage propre (BScript), pas un langage existant | Lua (LuaJ/Cobalt), JS | Round-trip texte↔graphe impossible avec un langage général ; coroutines Lua non sérialisables (casse FR15, cf. ComputerCraft) ; sandbox par liste noire fragile (NFR4) ; ordre d'itération non déterministe (FR23). Un éventuel nœud `script/lua` en `compat/`, permission `ADMIN`, reste possible sans toucher au cœur |
| AD12 | Syntaxe BScript famille C/JS + ergonomies Lua | Lua-like (`then/end`), Python-like (indentation) | Les lecteurs réels du texte (admins, modpackers, devs) baignent dans KubeJS/Java ; accolades = délimiteurs robustes au copier-coller (FR27) ; l'indentation significative casse au collage. Emprunts Lua : `--` en commentaire, `and`/`or`/`not` en synonymes, pas de point-virgule |
