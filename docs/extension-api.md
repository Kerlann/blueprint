# Extension API — intégrer Blueprint dans un autre mod

**Statut :** spécification de référence (épics 2 et 8)
**Public :** développeurs de mods tiers, modpackers, datapackers
**Stabilité :** `fr.blueprint.api.*` suit le semver. `BlueprintApi.API_VERSION` permet de vérifier à l'exécution.

---

## 0. En une minute

```java
public final class MyPlugin implements BlueprintPlugin {
    @Override public void registerNodes(NodeRegistry r) {
        r.register(NodeType.builder(Identifier.fromNamespaceAndPath("mymod", "heal_player"))
            .category(NodeCategories.ENTITY)
            .exec()                                   // ajoute exec-in et exec-out
            .in("player", PinTypes.PLAYER)
            .in("amount", PinTypes.DOUBLE, 1.0)
            .out("healed", PinTypes.BOOL)
            .permission(Permission.GAMEPLAY)
            .action(ctx -> {
                ServerPlayer p = ctx.in("player");
                p.heal(ctx.<Double>in("amount").floatValue());
                ctx.out("healed", true);
            })
            .build());
    }
}
```

```json
// fabric.mod.json
"entrypoints": { "blueprint": ["com.example.MyPlugin"] }
```

```gradle
// build.gradle — dépendance douce : le mod fonctionne sans Blueprint
compileOnly "fr.blueprint:blueprint-api:1.0.0"
modLocalRuntime "fr.blueprint:blueprint:1.0.0"   // dev seulement
```

```json
// fabric.mod.json — dépendance DOUCE, jamais "depends"
"suggests": { "blueprint": ">=1.0.0" }
```

Le nœud apparaît immédiatement dans la palette de tous les joueurs, y compris ceux qui
n'ont pas votre mod côté client (ils reçoivent le descripteur, pas votre code).

---

## 1. Les trois voies d'intégration

| Voie | Pour qui | Java requis | Puissance | Permission max |
|---|---|---|---|---|
| **A — Builder** | Auteurs de mods | Oui | Totale | `ADMIN` |
| **B — Annotation** | Auteurs de mods | Oui | Élevée | `ADMIN` |
| **C — JSON datapack** | Modpackers, datapackers | Non | Composition de nœuds existants | `GAMEPLAY` |

---

## 2. Voie A — le builder

### 2.1 `BlueprintPlugin`

```java
package fr.blueprint.api;

public interface BlueprintPlugin {
    /** Appelé une fois au démarrage. Obligatoire. */
    void registerNodes(NodeRegistry registry);

    /** Types de pins personnalisés. Appelé AVANT registerNodes. */
    default void registerTypes(PinTypeRegistry registry) {}

    /** Événements déclencheurs personnalisés. Appelé APRÈS registerNodes. */
    default void registerEvents(EventRegistry registry) {}
}
```

Une exception levée par votre plugin est isolée : Blueprint journalise l'erreur en
nommant votre mod, désactive vos nœuds, et continue de charger les autres. Vos nœuds
deviennent alors des **nœuds fantômes** dans les graphes qui les utilisent — les joueurs
ne perdent rien.

### 2.2 Le builder en détail

```java
NodeType.builder(id)
    .category(NodeCategory)         // regroupement dans la palette
    .titleKey("mymod.node.x.name")  // par défaut : blueprint.node.<ns>.<path>.name
    .descKey("mymod.node.x.desc")

    .exec()                         // ajoute "exec_in" et "exec_out"
    .execOut("then")                // sortie exec supplémentaire nommée (branches)
    .pure()                         // aucun pin exec : évalué à la demande, mémoïsé

    .in("nom", PinTypes.X)          // entrée sans valeur par défaut
    .in("nom", PinTypes.X, valeur)  // entrée avec littéral par défaut
    .out("nom", PinTypes.X)

    .generic("T")                   // groupe de jokers résolu au câblage
    .in("liste", PinTypes.listOf("T"))
    .out("element", PinTypes.of("T"))

    .side(ExecSide.SERVER)          // SERVER par défaut ; SERVER seul est supporté en v1
    .permission(Permission.GAMEPLAY)
    .fuelCost(1)                    // coût en instructions ; >1 pour un nœud lourd
    .deterministic(true)            // faux si le nœud dépend de l'aléatoire ou de l'heure

    .action(ctx -> { ... })         // corps du nœud
    .build();
```

Une déclaration incohérente (`pure()` + `exec()`, deux pins de même nom, type inconnu)
lève une exception **au démarrage**, avec un message nommant votre mod et votre nœud —
jamais une erreur silencieuse en jeu.

### 2.3 `NodeContext`

```java
public interface NodeContext {
    <T> T in(String pin);                 // lit une entrée, typée
    void out(String pin, Object value);   // écrit une sortie
    void exec(String pin);                // choisit la branche exec suivante

    void suspend(int ticks);              // suspend et reprend plus tard
    void fail(Component reason);          // met le blueprint en faute proprement

    MinecraftServer server();
    ServerLevel level();
    BlueprintHandle blueprint();
    TriggerContext trigger();             // le contexte de l'événement déclencheur
    Logger logger();
}
```

Le contexte n'est **valide que pendant l'appel**. Le conserver dans un champ lève une
exception (garde anti-fuite testée). Toutes les entrées sont déjà évaluées et typées à
l'entrée de `action`.

### 2.4 Nœud de flux avec branches

```java
NodeType.builder(id("mymod", "check_quest"))
    .exec()
    .execOut("completed")
    .execOut("failed")
    .in("player", PinTypes.PLAYER)
    .in("quest", PinTypes.RESOURCE_LOCATION)
    .action(ctx -> {
        boolean ok = QuestManager.isDone(ctx.in("player"), ctx.in("quest"));
        ctx.exec(ok ? "completed" : "failed");
    })
    .build();
```

### 2.5 Nœud pur

```java
NodeType.builder(id("mymod", "mana_of"))
    .pure()                                  // pas de pin exec
    .in("player", PinTypes.PLAYER)
    .out("mana", PinTypes.DOUBLE)
    .action(ctx -> ctx.out("mana", ManaApi.get(ctx.in("player"))))
    .build();
```

### 2.6 Nœud suspendable

```java
.action(ctx -> {
    if (!RitualApi.isReady(ctx.in("altar"))) {
        ctx.suspend(20);   // réessaie dans 1 seconde, l'état survit à un redémarrage
        return;
    }
    ctx.out("result", RitualApi.complete(ctx.in("altar")));
})
```

---

## 3. Type de pin personnalisé

Nécessaire seulement si votre mod manipule un type que Blueprint ne connaît pas.

```java
public static final PinType MANA_POOL = PinType.builder(id("mymod", "mana_pool"))
    .javaType(ManaPool.class)
    .color(0xFF4FC3F7)
    .shape(PinShape.DIAMOND)
    .translationKey("mymod.pin.mana_pool")
    .codec(ManaPool.CODEC)              // requis : persistance des littéraux
    .streamCodec(ManaPool.STREAM_CODEC) // requis : synchro réseau
    .coerceFrom(PinTypes.INT, i -> new ManaPool((int) i))   // conversion implicite
    .build();

@Override public void registerTypes(PinTypeRegistry r) { r.register(MANA_POOL); }
```

Sans `codec`, un littéral de ce type ne peut pas être sauvegardé — Blueprint refuse
l'enregistrement au démarrage plutôt que de perdre des données plus tard.

---

## 4. Événement personnalisé

```java
public static final EventType ON_RITUAL = EventType.builder(id("mymod", "ritual_complete"))
    .out("player", PinTypes.PLAYER)
    .out("altar",  PinTypes.BLOCK_POS)
    .out("power",  PinTypes.DOUBLE)
    .dispatch(Dispatch.PER_LEVEL)     // GLOBAL | PER_LEVEL | PER_PLAYER
    .build();

@Override public void registerEvents(EventRegistry r) { r.register(ON_RITUAL); }

// Déclenchement depuis votre code :
BlueprintEvents.fire(ON_RITUAL, payload -> payload
    .set("player", player)
    .set("altar", pos)
    .set("power", 3.5));
```

`fire` appelé hors du thread serveur est automatiquement reporté sur le thread serveur.
Un événement sans abonné coûte un test de booléen.

---

## 5. Voie B — l'annotation

```java
public final class MyNodes {

    @BlueprintNode(value = "mymod:heal_player", category = "entity",
                   permission = Permission.GAMEPLAY, fuelCost = 3)
    public static void healPlayer(ServerPlayer player, @In(def = "1.0") double amount) {
        player.heal((float) amount);
    }

    @BlueprintNode(value = "mymod:mana_of", pure = true)
    @Out("mana")                                   // sans @Out, le pin s'appelle "result"
    public static double manaOf(ServerPlayer player) {
        return ManaApi.get(player);
    }

    // Le contexte s'obtient en le déclarant : il n'apparaît pas comme pin.
    @BlueprintNode("mymod:announce")
    public static void announce(NodeContext ctx, @In("texte") String text) {
        ctx.server().getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
```

Les pins sont déduits de la signature : **nom du paramètre → nom du pin** (le mod doit
compiler avec `-parameters`, sinon nommez-les par `@In("nom")` — le refus est explicite),
**type Java → `PinType`**, **valeur de retour → pin de sortie**. Une méthode `void`
devient un nœud d'exécution (`exec_in`/`exec_out`) ; `pure = true` exige une valeur de
retour.

Un type de pin maison se déclare dans la table passée à l'enregistrement :

```java
AnnotatedNodes.register(registry, Map.of(ManaPool.class, MANA), MyNodes.class);
```

Deux façons de faire lire ces classes :

- **Depuis votre `BlueprintPlugin`** — une ligne, et vous gardez la main sur l'ordre :

  ```java
  @Override public void registerNodes(NodeRegistry registry) {
      AnnotatedNodes.register(registry, MyNodes.class);
  }
  ```

- **Sans aucune classe de plugin** — déclarez les porteuses dans `fabric.mod.json`,
  Blueprint les scanne au démarrage :

  ```json
  "custom": { "blueprint:node_holders": ["com.example.MyNodes"] }
  ```

Une déclaration fautive (identifiant invalide, méthode non statique, type sans pin,
valeur par défaut illisible…) est **refusée avec un message qui nomme la méthode**, et
elle n'isole que **votre** mod : les autres chargent normalement.

> **État v1.0** : le processeur d'annotations qui vérifierait tout cela à la compilation
> n'est pas livré — le PRD le décrivait comme optionnel, et le scan à l'exécution couvre
> le même besoin sans imposer d'étape de build aux mods tiers. Les erreurs apparaissent
> donc au démarrage du jeu, pas dans `javac`.
>
> **Sorties multiples** : une méthode annotée n'a qu'un pin de sortie (sa valeur de
> retour). Pour plusieurs sorties, déclarez le nœud au builder (voie A) et appelez
> `ctx.out(...)` autant de fois qu'il faut.

---

## 6. Voie C — nœuds en datapack (sans Java)

`data/<modid>/blueprint/nodes/heal_and_feed.json` :

```json
{
  "id": "mypack:heal_and_feed",
  "category": "player",
  "translation": { "name": "mypack.node.heal_and_feed", "desc": "mypack.node.heal_and_feed.desc" },
  "pins": {
    "in":  [ { "name": "player", "type": "blueprint:player" },
             { "name": "amount", "type": "blueprint:double", "default": 4.0 } ],
    "out": [ { "name": "ok", "type": "blueprint:bool" } ]
  },
  "body": {
    "type": "bscript",
    "source": "player.heal($player, $amount)\nplayer.feed($player, 4)\n$ok = true"
  }
}
```

- Rechargé à `/reload`. Un fichier invalide est signalé sans casser les autres.
- Le corps ne peut appeler que des nœuds existants : la permission est bornée à `GAMEPLAY`.
- Alternative au corps BScript : `"type": "graph"` avec une liste de nœuds et de liens,
  ce qui permet d'exporter un blueprint existant comme nœud réutilisable.

---

## 7. Robustesse : les nœuds fantômes

Quand un graphe référence un identifiant absent du registre :

1. Le nœud devient un **nœud fantôme** : identifiant, position, liens, littéraux et
   configuration sont intégralement **conservés**.
2. L'éditeur l'affiche en rouge avec « nœud fourni par `mymod`, mod absent ».
3. Le blueprint refuse de s'exécuter et nomme le mod manquant.
4. Réinstaller le mod restaure le nœud à l'identique.

Conséquence pour vous : **ne changez jamais l'identifiant d'un nœud publié.** Pour le
renommer, gardez l'ancien identifiant en `@Deprecated` avec un alias :

```java
r.registerAlias(id("mymod", "old_name"), id("mymod", "new_name"));
```

Ajouter un pin d'entrée avec valeur par défaut est **compatible**. Retirer un pin ou en
changer le type est **cassant** : cela invalide les liens existants avec un diagnostic
explicite plutôt qu'une suppression silencieuse.

---

## 8. Dépendance douce et compatibilité

```java
// Dans votre initialiseur : Blueprint peut être absent
if (FabricLoader.getInstance().isModLoaded("blueprint")) {
    BlueprintBridge.init();   // classe séparée, chargée seulement ici
}
```

La classe qui touche `fr.blueprint.api` doit être **isolée** : si elle est chargée alors
que Blueprint est absent, la JVM lève `NoClassDefFoundError`. Utilisez toujours un pont
dans une classe distincte, jamais un `import` en tête de votre initialiseur principal.

Vérification de version :

```java
if (BlueprintApi.API_VERSION.isCompatibleWith("1.0")) { ... }
```

Politique : rupture uniquement en majeure, dépréciation maintenue au moins un cycle de
version Minecraft, surface publique vérifiée en CI par japicmp.

---

## 9. Intégrations livrées par Blueprint lui-même

Quand un mod tiers populaire n'expose pas ses propres nœuds, Blueprint peut fournir
l'intégration : `compat/src/main/java/fr/blueprint/compat/<modid>/`, chargée uniquement si
`FabricLoader.isModLoaded("<modid>")`. C'est un plan de repli, pas la voie normale : la
voie normale est que le mod déclare ses nœuds lui-même.

---

## 10. Liste de vérification avant publication

- [ ] Entrypoint `blueprint` déclaré, dépendance en `suggests` (jamais `depends`)
- [ ] `blueprint-api` en `compileOnly`, pont isolé dans une classe séparée
- [ ] Identifiants sous votre namespace, stables, documentés
- [ ] Clés de traduction présentes dans vos fichiers de langue
- [ ] Permission correcte sur chaque nœud (surtout : rien en `SAFE` qui modifie le monde)
- [ ] `fuelCost` > 1 sur les nœuds coûteux
- [ ] Codec **et** stream codec sur tout `PinType` personnalisé
- [ ] Le mod démarre et fonctionne sans Blueprint installé
- [ ] Un graphe utilisant vos nœuds survit à la désinstallation puis réinstallation du mod
