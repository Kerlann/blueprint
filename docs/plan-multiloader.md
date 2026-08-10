# Plan multiloader — Fabric + NeoForge

**État : lots A, B, C et D TERMINÉS. Lot E écrit et compilé, mais jamais exécuté.**
Décidé le 9 août 2026, après relevé de l'écosystème et lecture complète des points
d'attache du dépôt à la plateforme.

> **Où en est NeoForge, exactement.**
>
> Le **serveur dédié démarre** (`:neoforge:runServer`, `Done (0.454s)`) : 1 plugin
> découvert par service, 16 types de pins, 239 nœuds, 26 événements, 0 en échec — et
> **« Contenu déclaré : 3 item(s) et 2 bloc(s) enregistré(s), 0 écarté(s) »**, les mêmes
> chiffres que sur Fabric.
>
> Le **client démarre** aussi (`:neoforge:runClient`), sans erreur : « Blueprint client
> initialisé », touches et couche de HUD posées, traitements de paquets descendants
> acceptés, et la boucle de tick client tourne — « Packs de script : 0 chargé(s) » sort de
> `endClientTick`.
>
> **La chaîne complète a tourné**, côté serveur, par un scénario de commandes joué sur
> l'entrée console (`./gradlew :neoforge:runServer -Pblueprint.scenario`) :
> `/blueprint content` liste les cinq contenus déclarés avec leurs propriétés,
> `/blueprint bench` crée et active un graphe, `/blueprint list` le confirme, et
> **`/bpc bench` rend « Command "bench" triggered 1 blueprint(s) »** — c'est-à-dire
> commande → pont d'événement → compilation → VM → ordonnanceur.
>
> **Vérifié à la main par Kerlann, sur le client NeoForge** : `/bpc` ouvre un menu, et
> l'événement `player_join` déclenche un `send_message` qui arrive au joueur. Cela valide
> d'un coup toute la chaîne descendante — commande → pont → VM → `gui/show_screen` →
> paquet `ScreenOpen` → récepteur client → rendu — ainsi qu'un événement du monde autre
> que la commande.
>
> Ce qui n'a **toujours pas** été observé, par ordre d'importance :
>
> 1. **Un gros graphe dans l'éditeur.** C'est là que manque la fragmentation des paquets
>    (§E) : un menu tient dans un paquet ordinaire, un graphe de mille nœuds non. Test
>    décisif : `/blueprint bench` puis F6.
> 2. **Un clic dans un menu.** Ouvrir prouve le sens descendant ; cliquer prouverait le
>    sens montant (`ScreenInteraction`).
> 3. **Le JAR assemblé**, jamais chargé par une installation NeoForge réelle — seulement
>    le mode développement, dont le chemin de chargement de classes diffère. Ce n'est pas
>    une nuance théorique : la première panne du portage était justement un
>    `LinkageError` de classloader.
>
> Et il n'existe aucun gametest de ce côté. Trois écarts connus sont listés au §E.
>
> (Les neuf avertissements « Nœud d'événement non synthétisé » du journal ne sont **pas**
> une régression : ils sortent à l'identique sur Fabric, où neuf identifiants d'événement
> sont déjà des nœuds à part entière.)

**Aucun module commun ne mentionne plus `net.fabricmc`** — 0 fichier sur `api`,
`platform`, `core`, `client` et `compat` ; 8 dans `fabric`, qui est sa définition. La
règle est tenue par une tâche Gradle, `checkLoaderIsolation`, dont on a vérifié qu'elle
échoue quand on la viole.

**Décision : Fabric + NeoForge. Pas Forge.** La couche d'abstraction est néanmoins
conçue pour en accueillir un troisième, parce que ça ne coûte rien de le prévoir et cher
de le rétro-ajouter.

| Lot | Sujet | Poids | État |
|---|---|---|---|
| **A1** | Modules `platform/` et `fabric/` ; chemins, mods, point d'entrée | petit | **fait** |
| **A2** | Le réseau derrière une interface (7 fichiers) | moyen | **fait** |
| **A3** | Le client : touches, HUD, commandes client | moyen | **fait** |
| **A4** | Les vingt événements du monde passent dans `fabric/` | moyen | **fait** |
| **B** | Fenêtre d'enregistrement du contenu déclaré | **le point dur** | **fait** |
| **C** | Découverte des plugins tiers par `ServiceLoader` | petit | **fait** |
| **D** | Build : classpath commun sans fabric-api | moyen | **fait** |
| **E** | Module `neoforge/` | grand | **chaîne complète vérifiée côté serveur ; client démarre seulement** |
| **F** | Métadonnées, CI, publication | petit | à faire |

Le lot A s'est révélé assez gros pour mériter quatre tranches. Le découpage suit les
attaches, pas les fichiers : chaque tranche laisse le dépôt compilable et le jeu
identique.

**A1 (fait).** `platform/` et `fabric/` existent. `Platform.paths()` et
`Platform.mods()` sont résolus par `ServiceLoader`. `BlueprintPaths`, `PluginLoader` et
`CompatPlugin` ne connaissent plus Fabric. Surtout : `BlueprintMod` n'implémente plus
`ModInitializer` — c'est `fr.blueprint.fabric.FabricBootstrap` qui le fait et qui appelle
`BlueprintMod.init()`. Le `fabric.mod.json` a suivi le point d'entrée dans `fabric/`.
Vérifié par `check` complet (couverture, bancs, isolations) et par le serveur des
gametests : même suite, même unique échec préexistant (`func/call` et ses deux voisins),
même comptage au démarrage — 2 plugins, 16 types de pins, 244 nœuds, 26 événements, 0 en
échec.

Deux garde-fous mécaniques posés au passage : `checkPlatformIsolation` (le module de
frontière ne peut pas référencer l'implémentation, sur le modèle de `checkApiIsolation`),
et la vérification que `META-INF/services` survit au remapping de Loom — c'était le risque
réel, un service perdu ne se voit qu'au démarrage.

**A2 (fait).** `PayloadTypeRegistry`, `ServerPlayNetworking` et `ClientPlayNetworking` ont
entièrement disparu de `core` et `client`, remplacés par `ServerNetwork`, `ClientNetwork`
et leurs deux contextes. **Aucune ligne de `BlueprintPayloads` n'a été touchée** : les
charges utiles sont du Minecraft pur, ce sont les trois verbes autour qui étaient du
Fabric.

Ce qui a coûté le plus n'était pas les imports mais un <b>type</b> :
`ServerPlayNetworking.Context` était écrit dans six signatures de `ServerBlueprintNet` et
`DebugNet` (`allowed`, `name`, `mayEdit`, `sendGraph`, `deny`, `handle`). Un type du
chargeur dans une signature ne se remplace pas, il se propage — c'est le même piège que
`FabricClientCommandSource` en A3.

Trois décisions à retenir :

- **`context.client()` est mort, pas remplacé.** Il valait exactement
  `Minecraft.getInstance()`, que le module client appelle déjà partout. Le garder aurait
  fait entrer une classe absente d'un serveur dédié dans `platform`, qui est chargé par
  le serveur.
- **`reply()` distinct de `send()`.** Indiscernables sur Fabric, séparés sur NeoForge —
  et à la lecture, `reply` dit que le paquet part *parce qu'*un autre est arrivé.
- **La poignée de réseau est hissée hors des boucles.** `flushScreenUpdates` tourne à
  chaque fin de tick ; `Platform.serverNetwork()` prend un moniteur. Minuscule, mais
  coding-standards §5 n'admet pas d'exception « parce que c'est petit ».

Vérifié par `check` complet, par le serveur des gametests (même unique échec préexistant,
mêmes comptages) et par la présence des quatre services dans le JAR remappé.

**Réserve honnête** : les gametests tournent sans client. Le réseau **serveur** est donc
exercé pour de vrai ; le réseau **client** n'est vérifié qu'à la compilation et par les
tests unitaires. Sa vraie épreuve est d'ouvrir l'éditeur dans `runClient`, ce qu'aucune
tâche automatique ne fait ici. Cette réserve vaut aussi pour A3 et A4 côté client.

**A3 (fait).** Touches, HUD et commandes client passent par `ClientPlatform`. Le piège
annoncé était réel et sa solution n'est pas celle qu'on croit : `FabricClientCommandSource`
était dans cinq signatures, mais **tout ce que le code lui demandait était de parler au
joueur**. Inventer un type de source à nous aurait obligé à reconstruire l'arbre Brigadier,
qui est générique sur la source ; on garde donc l'arbre générique sur `S`, chaque chargeur
le construit avec son propre type, et fournit un seul verbe — `ClientFeedback`.
`ClientCommandManager` disparaît au passage : ce n'était qu'un `LiteralArgumentBuilder`
déjà typé, et Brigadier brut est commun à tous les chargeurs.

> **Depuis, le piège a disparu au lieu d'être contourné.** Le mod n'a plus de commande
> cliente du tout : `/blueprint-edit` n'était qu'un alias qui réécrivait `/blueprint edit`,
> et `/blueprint-packs` est devenu `/blueprint packs` — une commande serveur qui transmet la
> demande par un paquet. `ClientFeedback` est donc supprimé, et avec lui le seul endroit où
> les deux chargeurs divergeaient sur les commandes. La bonne solution d'un problème de
> portage était de ne plus avoir le problème.

Deux abonnements au tick client se rejoignent en un `endClientTick`, ce qui fait de leur
ordre une propriété du code plutôt que de l'ordre d'enregistrement.

Du code mort est sorti au passage — `list`, `createAndEdit`, `parseId`, `lastEdited` :
plus aucun appelant dans tout le dépôt depuis que F6 ouvre le navigateur. Il fallait de
toute façon les traiter, puisqu'ils portaient le type Fabric dans leur signature ; les
convertir aurait maintenu en vie du code inatteignable. `LocalizationTest` a immédiatement
signalé la clé `blueprint.editor.cmd.unknown` devenue morte — elle est retirée.

**A4 (fait).** Les vingt événements sont dans `fabric/FabricServerEvents` (server) et
`FabricClientBootstrap` (client). La ligne de partage est celle du §2, et elle s'est
révélée fine :

- **Dans `core/event/WorldEvents`** — ce qui est vrai du jeu : la copie de la pile tenue
  en main, l'état du bloc plutôt qu'une relecture, le choix de `damageTaken` sur
  `baseAmount`, le nom `end_portal`. Écrit une fois.
- **Dans `fabric/`** — ce qui est vrai de *Fabric* : la garde de main principale (Fabric
  appelle son callback pour chaque main), le filtre des entités qui dorment (Fabric émet
  pour toute entité vivante), la traduction du drapeau `alive`. Recopié par chargeur, et
  c'est normal.

La règle pour trancher, écrite dans le javadoc de `WorldEvents` : *si la correction d'un
bug devait être recopiée dans chaque chargeur, elle est du mauvais côté de la ligne.*

Quatre abonnements de déconnexion se rejoignent en un `playerDisconnected` (et quatre
autres en `BlueprintClient.onDisconnect`), **dans l'ordre où ils s'exécutaient** — cet
ordre ne tenait qu'à l'ordre des appels dans `init()`, il est maintenant écrit.
`EventCoverageTest` a été étendu à `WorldEvents` mais **pas** à `FabricServerEvents` :
y admettre le fichier du chargeur ferait dire au test « cet événement se déclenche sur
Fabric » là où il dit aujourd'hui « cet événement se déclenche ».

Les lots A à D ne livrent aucun loader supplémentaire et doivent laisser le jeu **se
comporter exactement comme avant**. C'est voulu : ce sont eux la vraie charge, et ils
gardent leur valeur même si E n'est jamais fait.

---

## 0. Le résultat en une page

Le projet est en bien meilleure position qu'un mod Fabric ordinaire, pour une raison qui
n'a rien d'un hasard : **la séparation en modules et le passage par `BlueprintEvents` ont
déjà fait le travail d'abstraction, sans que ce soit leur but.**

Trois constats de lecture, chacun vérifié dans le code :

1. **16 fichiers sur 425** touchent une API Fabric. `api/` n'en touche **aucun** : la
   surface publique consommée par les mods tiers est déjà portable telle quelle.
2. **Aucun mixin** dans le dépôt. C'est ce qui coûte le plus cher en multiloader — les
   cibles et les noms diffèrent entre plateformes — et le projet n'en paie pas un seul.
3. **Les vingt événements du monde convergent tous vers `BlueprintEvents.fire(...)`.**
   Les enregistrements Fabric de `BlueprintMod` ne contiennent pas de logique : ce sont
   des adaptateurs. Le pont NeoForge est donc « rebrancher vingt fils sur la même
   borne », pas « réécrire la logique d'événements ».

Le seul obstacle de fond est ailleurs, et il est structurel : **le contenu déclaré
s'enregistre à un moment que NeoForge n'autorise pas.** Voir §3.

---

## 1. Où le projet touche la plateforme

Relevé exhaustif. Rien d'autre dans le dépôt n'importe `net.fabricmc`.

### `core/` — six fichiers

| Fichier | Ce qu'il utilise |
|---|---|
| `BlueprintMod` | `ModInitializer` ; cycle de vie (`SERVER_STARTING/STARTED/STOPPED`) ; `END_SERVER_TICK` ; `CommandRegistrationCallback` ; `ResourceLoader` ; réseau serveur ; **et les treize événements du monde** : `UseBlock`, `UseItem`, `UseEntity`, `AttackEntity`, `PlayerBlockBreak.AFTER`, `EntitySleep.START/STOP`, `ServerEntityCombat.AFTER_KILLED_OTHER_ENTITY`, `ServerEntityWorldChange.AFTER_PLAYER_CHANGE_WORLD`, `ServerLivingEntity.AFTER_DAMAGE/AFTER_DEATH`, `ServerPlayer.AFTER_RESPAWN`, `ServerMessage.CHAT_MESSAGE` |
| `BlueprintPaths` | `FabricLoader.getGameDir()`, `getConfigDir()` |
| `BlueprintCommand` | `CommandRegistrationCallback` |
| `ContentDrops` | `PlayerBlockBreakEvents` |
| `DebugNet` | `PayloadTypeRegistry`, `ServerPlayNetworking`, `ServerPlayConnectionEvents`, `END_SERVER_TICK` |
| `ServerBlueprintNet` | `PayloadTypeRegistry`, `ServerPlayNetworking`, `ServerPlayConnectionEvents` |
| `PluginLoader` | `getEntrypointContainers`, `getAllMods`, `CustomValue` |

### `client/` — sept fichiers

| Fichier | Ce qu'il utilise |
|---|---|
| `BlueprintClient` | `ClientModInitializer`, `ClientTickEvents`, `ClientCommandRegistrationCallback`, `ClientCommandManager`, `FabricClientCommandSource`, `KeyBindingHelper` |
| `BlueprintKeys` | `KeyBindingHelper`, `ClientPlayNetworking` |
| `BlueprintNet`, `DebugClient`, `RegistrySync`, `ScreenClient` | `ClientPlayNetworking`, `ClientPlayConnectionEvents` |
| `BlueprintHud` | `HudElementRegistry` |

### `compat/` — un fichier

`CompatPlugin` : `FabricLoader` pour savoir quels mods sont présents.

### `gametest/` — hors périmètre

`net.fabricmc.fabric.api.gametest.v1.GameTest`. Voir §6.

---

## 2. La frontière, et dans quel sens elle se traverse

C'est la décision de conception qui structure tout le reste. **Les deux sens existent et
ne se traitent pas pareil.**

**Sens « `core` appelle la plateforme » — par `ServiceLoader`.**
Le code commun a besoin de quelque chose et ne sait pas qui le fournit :

- `PlatformPaths` — `gameDir()`, `configDir()`
- `PlatformMods` — `isLoaded(id)`, et la liste des porteurs de nœuds déclarés
- `PlatformNetwork` — enregistrer un type de charge, envoyer, `canSend`, poser un
  récepteur
- `PlatformRegistrar` — « appelle-moi quand le registre est ouvert » (§3)

`ServiceLoader` est le mécanisme portable : il marche à l'identique sur les trois
loaders, il est dans le JDK, il n'ajoute aucune dépendance. Chaque module de loader dépose
son implémentation dans `META-INF/services`.

**Sens « la plateforme appelle `core` » — par simple appel.**
Le module de loader est le point d'entrée : c'est *lui* qui se réveille, donc c'est lui
qui pousse. Rien à abstraire, rien à découvrir :

- l'initialisation (`onInitialize` / `@Mod`)
- les vingt événements du monde → `BlueprintEvents.fire(...)`
- l'enregistrement des commandes, des touches, du HUD
- le rechargement des ressources

Confondre les deux sens est l'erreur classique : on écrit une `PlatformEvents` géante
avec vingt méthodes d'abonnement, et on se retrouve à réimplémenter le système
d'événements de Fabric dans le code commun. **Ce n'est pas nécessaire ici, précisément
parce que `BlueprintEvents` existe déjà et joue ce rôle.** Le module de loader appelle
`BlueprintEvents.fire` directement, avec ses propres événements natifs.

Conséquence concrète : `core` exposera une poignée de méthodes publiques d'amorçage
(`BlueprintServer.start(server)`, `stop()`, `tick(server)`, `registerCommands(...)`) que
chaque loader branche à sa manière. Les 755 lignes de `BlueprintMod` se scindent en
« la logique » (reste dans `core`) et « les vingt abonnements » (part dans `fabric/` et
`neoforge/`, dupliqués — et c'est très bien, ils ne partagent rien).

---

## 3. Le point dur : la fenêtre d'enregistrement

`ContentRegistrar.registerAll()` appelle `Registry.register(BuiltInRegistries.ITEM, …)`
directement, depuis `onInitialize()`. **Sur Fabric c'est légal. Sur NeoForge c'est
interdit** : hors de `RegisterEvent`, les registres sont gelés et l'appel lève.

Le fichier le sait déjà. Son en-tête dit :

> *« À appeler depuis `onInitialize()` et de nulle part ailleurs. C'est la seule fenêtre :
> après elle, `Registry#freeze()` passe, et toute tentative lève. […] c'est elle qui
> décide de toute la forme de l'épic 11 — d'où des définitions sur le disque plutôt que
> dans un blueprint. »*

Tout ce raisonnement reste juste. Ce qui change est **qui décide de la fenêtre** : ce
n'est plus une méthode nommée dans le code, c'est la plateforme. D'où `PlatformRegistrar`.

La bonne nouvelle, et elle est décisive : **`ContentLoader` lit des fichiers sur le
disque**, dans le dossier de configuration. Il n'a besoin ni du serveur, ni des registres,
ni d'un monde. Son rapport est donc calculable avant `RegisterEvent` sans rien changer à
sa logique. C'est un déplacement d'appel, pas une réécriture.

Deux détails qui mordront si on ne les prévoit pas :

- **`RegisterEvent` est par registre.** Le commentaire « les blocs APRÈS les items »
  garde son sens sur Fabric ; sur NeoForge, l'ordre entre `Registries.ITEM` et
  `Registries.BLOCK` n'est pas le nôtre. Or ce commentaire dit pourquoi l'ordre compte :
  *« les identifiants numériques du réseau »*. Il faut donc découpler l'ordre
  d'enregistrement de l'ordre d'attribution des identifiants réseau — sinon le même
  dossier de contenu produit deux numérotations selon le loader, et un client Fabric ne
  parle plus à un serveur NeoForge.
- **`ContentDrops`** dépend de `PlayerBlockBreakEvents` : à porter avec le reste des
  événements, mais il fait partie du contrat « un bloc déclaré lâche ce qu'on a dit »,
  donc il se teste avec le lot B, pas avec le lot A.

C'est le seul endroit du dépôt où la portabilité change une décision de conception. Il
mérite son propre lot et ses propres gametests.

### Ce qui a été fait (lot B)

`PlatformRegistrar` pose la question — « appelle-moi quand ce registre est ouvert » — et
`ContentRegistrar` s'enregistre en **deux passes**, les blocs puis les items, au lieu
d'une. Sur Fabric, `FabricRegistrar.whenOpen` exécute immédiatement : c'est la traduction
exacte de ce que fait ce chargeur, pas une paresse.

**La contrainte que cela impose est réelle et invisible sur Fabric** : l'action peut
partir plus tard que l'appel, donc tout ce qui dépend du résultat — le compte, la liste
des refus, la trace — doit vivre *dans* l'action. Le bilan du contenu déclaré a donc
déménagé à l'intérieur de la passe des items. Écrit après, il aurait annoncé zéro item
sur un dossier plein, sur le premier chargeur à ouvrir sa fenêtre plus tard.

**L'ordre est devenu une fonction pure** : `ContentRegistrar.itemOrder(report)` — les
items du dossier `items/`, puis l'item de chaque bloc — et `blockOrder(report)`. Les deux
passes itèrent ces listes, de sorte que le contrat et le code ne peuvent pas diverger.
Avant, « les blocs après les items » tenait au fait que la boucle des blocs venait après ;
sur NeoForge, l'ordre entre les registres n'est plus le nôtre, et il fallait que la suite
des items cesse d'en dépendre.

**Deux tests, et ils ne disent pas la même chose.** `ContentOrderTest` vérifie la fonction
pure, sans jeu lancé. Le gametest
`declaredContentKeepsTheOrderThatDecidesNetworkIds` lit le **vrai** registre du serveur et
compare les rangs réels à la suite annoncée — parce que deux tests d'accord entre eux ne
prouvent rien si aucun ne regarde le résultat. Vérifié en cassant l'ordre exprès : le
gametest échoue en nommant l'item et son rang.

Effet de bord assumé : **l'épic 11 n'avait aucune vérification en jeu**, alors que c'est le
seul code du projet dont l'échec se paie avant l'écran titre. `runGametest` copie
maintenant `docs/examples/content` dans le serveur de test — les mêmes fichiers que la
documentation, déjà validés par `ContentExamplesTest`, donc un échec parle du registre et
pas du JSON.

Un mensonge corrigé au passage : le bilan comptait les items par soustraction
(« tout, moins les blocs déclarés »). Un bloc refusé par la première passe n'ayant plus
d'item, la soustraction se serait mise à annoncer moins d'items qu'il n'y en a — voire un
nombre négatif. Ils sont comptés.

---

## 4. Les plugins tiers

`PluginLoader` découvre les plugins par les entrypoints de `fabric.mod.json`, et les
classes porteuses de `@BlueprintNode` par la clé `blueprint:node_holders`. NeoForge n'a
d'équivalent pour ni l'un ni l'autre.

Le mécanisme portable est **`ServiceLoader`**. `loadFromFabric()` est déjà écrit comme un
adaptateur — son propre commentaire dit *« ne contient aucune logique, tout est dans
`load` »* — donc ajouter `loadFromServices()` à côté ne touche pas au chargement lui-même.

**On garde les deux sur Fabric.** La story 8.1 est publiée, des mods tiers ont pu s'écrire
contre elle ; la retirer casserait un contrat annoncé pour économiser vingt lignes. Sur
Fabric : entrypoints **et** services, dédoublonnés par identifiant de mod. Sur NeoForge :
services seuls.

Ça implique de mettre à jour `docs/extension-api.md` : `ServiceLoader` devient la voie
**recommandée** (elle marche partout), l'entrypoint Fabric reste documenté comme
historique et toujours supporté.

### Ce qui a été fait (lot C)

`PluginLoader.discover()` réunit les deux voies, et `merge(...)` — **pure, donc testable
sans jeu ni chargeur** — les dédoublonne.

**Le point de conception ne se voyait pas d'avance** : la voie par service ne passe plus
par le chargeur, donc elle ne fournit plus le modid. Or ce modid n'est pas qu'un
identifiant de journal — il est **montré au joueur**, dans l'infobulle d'un nœud et sous
« fourni par » dans le panneau de détails. D'où `BlueprintPlugin.modId()`, et le refus,
nommé, d'un plugin déclaré par service qui ne le redéfinit pas : le charger sous un nom
de classe aurait mis `com.example.MyPlugin` dans la palette d'un joueur sans que personne
ne le voie venir.

**Le dédoublonnage se fait sur la classe, pas sur l'instance** — les deux voies
construisent chacune la leur. Sans lui, un mod qui se déclare des deux côtés — le cas
normal d'un mod qui veut marcher partout — verrait ses nœuds enregistrés deux fois,
refusés la seconde, et son plugin isolé pour un conflit avec lui-même : il perdrait ses
nœuds en *ajoutant* du support.

Blueprint est son propre premier utilisateur : `CompatPlugin` et `TestPlugin` sont
déclarés **des deux côtés**. Le serveur de test annonce toujours « 2 plugin(s) », pas 4.

Surface publique : `1.2.0` → **`1.3.0`**. Mineure, pas majeure — la méthode a un défaut,
aucun plugin existant n'a besoin d'être retouché. `ApiSurfaceTest` a exigé le geste, et
`docs/api-surface.txt` gagne exactement une ligne.

Les **porteuses de nœuds annotés** (`blueprint:node_holders`) restent, elles, attachées
aux métadonnées du chargeur : cette voie existe pour n'écrire aucune ligne de Java, et un
fichier de service exige une classe à instancier. `PlatformMods.nodeHolders()` l'abstrait
déjà.

---

## 5. Le réseau

C'est le plus gros volume — sept fichiers — et le plus mécanique.

Ce qui **ne bouge pas** : les charges utiles elles-mêmes. Ce sont des
`CustomPacketPayload` vanilla avec des codecs vanilla. Aucune classe de paquet n'est à
toucher, aucun format de fil ne change.

Ce qui bouge : trois verbes. `PayloadTypeRegistry.playC2S/playS2C().register(...)` →
`RegisterPayloadHandlersEvent` côté NeoForge ; `ServerPlayNetworking.send/canSend` ; la
pose des récepteurs. Plus `ServerPlayConnectionEvents.JOIN/DISCONNECT`, qui relèvent du
sens « plateforme → core ».

`canSend` mérite une note : il sert à ne pas envoyer un paquet à un client qui ne connaît
pas le mod. Les deux plateformes savent répondre à la question, mais pas avec la même
sémantique de négociation. Une divergence ici ne se voit pas en développement — les deux
côtés ont toujours le mod — et se voit très bien chez un joueur en vanilla sur un serveur
Blueprint. **À tester explicitement, client nu contre serveur moddé, dans les deux sens.**

---

## 6. Le client

Quatre points d'attache, tous petits sauf un piège.

- **Touches** : `KeyBindingHelper` → `RegisterKeyMappingsEvent`. Direct.
- **HUD** : `HudElementRegistry` → l'événement de rendu de HUD de NeoForge. Direct.
- **Tick client** : direct.
- **Commandes client** : le piège. Le code manipule `FabricClientCommandSource` **comme
  type dans ses signatures**. NeoForge donne un `CommandSourceStack` ordinaire. Ce n'est
  pas une méthode à renommer, c'est un type qui traverse le code des commandes client. À
  traiter en premier dans le lot A, parce que la solution — un type de source à nous,
  côté commun — se propage à tout ce qui déclare une commande client.

---

## 7. La forme cible

```
blueprint/
├── api/        ← inchangé. Zéro import de plateforme, déjà.
├── platform/   ← NOUVEAU. Les interfaces, et rien d'autre. Dépend de api/ seulement.
├── core/       ← commun. Perd ses six attaches, gagne des points d'amorçage publics.
├── client/     ← commun. Perd ses sept attaches.
├── compat/     ← commun. Passe par PlatformMods.
├── fabric/     ← ModInitializer + les vingt abonnements + fabric.mod.json + services
├── neoforge/   ← @Mod + les vingt abonnements + neoforge.mods.toml + services
└── gametest/   ← reste Fabric (§8)
```

Le build actuel — racine agrégeant tout dans un JAR unique, `fabric-loom` appliqué à
`allprojects` — devient : `fabric-loom` sur `fabric/`, ModDevGradle sur `neoforge/`,
et les modules communs compilés contre Minecraft **sans** API de loader. C'est la forme du
[MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template/tree/1.21.11),
branche `1.21.11`, qu'on suit sans le cloner : le dépôt a déjà six modules et un build
qu'on comprend.

Deux JARs sortent à la fin, `blueprint-fabric` et `blueprint-neoforge`, chacun contenant
`api + platform + core + client + compat`.

La contrainte à ne pas perdre de vue : **les modules communs ne doivent voir aucune API de
loader**, sinon la frontière fuit sans qu'on s'en aperçoive. Le meilleur garde-fou est
mécanique — leur classpath de compilation ne contient tout simplement pas fabric-api. Le
projet a déjà un test d'isolation de l'`api` dans `check` ; c'est le même principe, étendu.

---

## 8. Ce qu'on ne fait pas, et pourquoi

**Forge.** Il est vivant : une branche par version jusqu'à `26.2`, build 65.1.0 du
27 juillet 2026. Mais son dépôt commite aux quinzaines quand NeoForge commite tous les
jours, et surtout **la couche bibliothèque l'a quitté** — Architectury API n'a plus aucune
build Forge en 1.21.11. Le point qui tranche pour *ce* projet est `compat/` : son objet
est de s'intégrer aux mods tiers, et sur Forge 1.21.11 il y en a nettement moins à
intégrer. Le coût, lui, n'est pas ponctuel : chaque fonctionnalité future se paierait
trois fois.

L'abstraction de §2 le laisse possible. NeoForge étant un fork de Forge, une
implémentation Forge ressemblerait beaucoup à celle de NeoForge — ce serait quelques jours
le jour où la demande existe, pas une réécriture.

**Architectury API.** Aurait fait gagner du temps sur les événements. Écarté pour deux
raisons : il impose une dépendance à installer aux joueurs, et il n'existe pas pour Forge
en 1.21.11 — donc il ne ferme même pas la porte qu'on garde ouverte. Nos propres
interfaces sont petites (§2) parce que `BlueprintEvents` fait déjà le gros du travail.

**Sinytra Connector.** Fait tourner un mod Fabric sur NeoForge sans le porter. C'est une
couche côté joueur, pas une solution d'auteur : on ne contrôlerait ni les régressions ni
le calendrier. Sans compter que sa version de prédilection reste 1.21.1.

**Les gametests sur NeoForge.** Ils dépendent de `fabric-api gametest`. C'est du dev
uniquement : ça ne coûte rien aux joueurs, et porter le harnais de test **avant** d'avoir
quoi que ce soit à tester sur NeoForge serait l'ordre inverse. À reconsidérer une fois le
lot E vert.

---

## 9. L'ordre, et à quoi on reconnaît qu'un lot est fini

**A — `platform/`, `fabric/`, et les seize fichiers. FAIT.** Toujours 100 % Fabric, un
seul JAR. Quatre tranches (A1 à A4), chacune laissant le dépôt compilable.
*Critère tenu :* plus aucune mention de `net.fabricmc` hors de `fabric/` — vérifié par
`checkLoaderIsolation`, elle-même vérifiée en la violant — et `check` + `runGametest`
verts, à l'unique échec préexistant près (`func/call` et ses deux voisins, présent sur
`main` avant le lot).

**B — la fenêtre d'enregistrement. FAIT.** L'enregistrement sort de l'initialisation et
passe par `PlatformRegistrar`, en deux passes ; l'ordre des identifiants réseau se
découple de l'ordre d'ouverture des registres.
*Critère tenu :* `ContentOrderTest` (pur) et le gametest
`declaredContentKeepsTheOrderThatDecidesNetworkIds` (registre réel), le second vérifié en
cassant l'ordre exprès. C'est ce couple qui protège la compatibilité Fabric ↔ NeoForge
plus tard.

**C — `ServiceLoader`.** Les deux voies coexistent sur Fabric.
*Fini quand :* `testmod` déclare ses nœuds par service et par entrypoint, et qu'aucun
n'apparaît deux fois.

**D — le build. FAIT.** Le classpath des modules communs a perdu fabric-api et
fabric-loader : seuls `fabric`, `testmod`, `gametest` et le projet racine (qui porte les
configurations de lancement) les voient encore.
*Critère tenu :* `core` ne **compile** plus si on y écrit un `import net.fabricmc` —
vérifié, le message est `package net.fabricmc.loader.api does not exist`, à la ligne, dans
l'IDE. `checkLoaderIsolation` reste malgré tout : elle attrape ce que le compilateur ne
voit pas — un nom pleinement qualifié dans un commentaire, une chaîne, une réflexion — et
elle explique où poser la question plutôt que de dire qu'un paquetage manque.

Le renommage en `blueprint-fabric.jar` n'a pas été fait : tant qu'un seul artefact
existe, un nom qui le distingue d'un autre ne distingue rien. Il ira avec le lot E, quand
il y aura deux JARs à ne pas confondre.

**E — `neoforge/`. ÉCRIT, PAS FINI.** Le vrai portage.
*Fini quand :* le mod démarre, un blueprint s'exécute, un item déclaré apparaît, et un
client NeoForge parle à un serveur NeoForge. **Rien de tout cela n'a encore été observé** —
voir l'encadré en tête de document.

### Ce qui a été fait (lot E)

**La décision de build qui a évité la grosse chirurgie.** Le template multiloader fait
sortir les modules communs de leur chaîne d'outils pour les compiler contre un Minecraft
« nu ». Ce n'était pas nécessaire ici : en 1.21, Loom **et** ModDevGradle compilent contre
un Minecraft aux mappings officiels de Mojang. Les classes de `core` produites par Loom
nomment donc exactement ce que NeoForge attend. Le module `neoforge` les consomme en
**sortie de sourceSet** — jamais le JAR remappé de Loom, qui est écrit dans
l'intermédiaire de Fabric et serait illisible ici.

Conséquence : `allprojects` applique Loom à tout le monde **sauf** `:neoforge`, qui a
ModDevGradle. Les deux chaînes cohabitent sans se voir.

**L'inversion que le lot B avait anticipée se voit enfin.** Sur Fabric, « demander » et
« faire » se confondent. Sur NeoForge, tout ce que le code commun demande pendant la
construction du mod — contenu déclaré, types de paquets, touches, couches de HUD — est
**mis en file**, et les événements d'enregistrement vident ces files au moment où le jeu
l'autorise. Le code commun ne s'en aperçoit pas : c'est exactement le service que
`PlatformRegistrar` rendait.

**Le lot C se paie ici aussi** : `NeoForgeMods.plugins()` rend une liste **vide**, et ce
n'est pas un manque. NeoForge n'a pas d'entrypoints ; toute la découverte passe par
`ServiceLoader`. Sans le lot C, ce fichier n'aurait rien eu à rendre.

**Le piège du lot A3 se referme en deux lignes** : NeoForge donne aux commandes client un
`CommandSourceStack` ordinaire, Fabric un `FabricClientCommandSource`. Le code commun n'en
connaît aucun — il construit un arbre Brigadier générique et ne demande à la source qu'un
verbe.

#### Trois écarts connus, assumés et non résolus

| Écart | Ce qui se passe | Pourquoi c'est laissé |
|---|---|---|
| **Paquets volumineux** | NeoForge n'a pas d'équivalent du `registerLarge` de Fabric. Les types sont déclarés normalement, donc un graphe assez gros pour dépasser la limite du protocole passe sur Fabric et échoue ici. | Fragmenter nous-mêmes est un travail de **protocole**, pas de plateforme, et il doit être fait des deux côtés à l'identique sous peine de désaccord. |
| **`player_sleep`** | **Ne part jamais** sur NeoForge. Le chargeur n'expose que `CanPlayerSleepEvent`, une *question* posée avant de décider — répondre oui n'est pas dormir. | Mieux vaut un événement qui ne part pas qu'un événement qui ment : émettre depuis là déclencherait un graphe pour un joueur que le jeu refuse ensuite de coucher. |
| **Casse d'un bloc** | Fabric expose « le bloc vient d'être cassé », NeoForge « il va l'être ». L'état transmis est le même ; ce qui diffère, c'est que l'événement part même si la casse est empêchée ensuite. | Corriger demande de suivre la casse jusqu'à son terme — de la logique de chargeur, à écrire quand on pourra la tester. |

Ces trois lignes valent mieux qu'un portage qui prétend être complet. Elles sont aussi la
liste de courses des lancements suivants.

#### Ce que le premier lancement a trouvé — et que la compilation ne pouvait pas voir

Le module compilait proprement contre le vrai JAR NeoForge. Il a quand même échoué deux
fois au démarrage, sur deux choses qu'aucun compilateur ne peut attraper.

**1. Un `LinkageError` sur `CustomPacketPayload`.** Les classes communes étaient sur le
chemin d'exécution ordinaire, donc chargées par le classloader `app` ; NeoForge charge
Minecraft par le sien. Les deux voyaient deux `CustomPacketPayload` différents portant le
même nom, et le mod mourait avant la première ligne de code utile.

La correction n'est pas dans les dépendances mais dans la déclaration `mods { }` de
ModDevGradle : y inscrire les sourceSets communs les fait passer par le **même chargeur**
que le reste du mod. C'est une notion qui n'existe pas côté Fabric, où tout le monde
partage le classloader du jeu.

**2. « Cannot register payload blueprint:screen_close as it is already registered. »**
Fabric tient **deux** registres de paquets, montant et descendant, où le même identifiant
peut figurer des deux côtés — et `screen_close` en profite : le serveur l'envoie pour
fermer un écran, le client le renvoie quand le joueur appuie sur Échap. NeoForge n'en
tient qu'un.

La sortie est `playBidirectional` avec un **seul** traitement qui se décide sur le sens du
paquet reçu. Le variant à deux traitements existe, mais l'ordre de ses arguments ne se lit
pas dans la signature, et s'y tromper ferait traiter un paquet client comme un paquet
serveur — un plantage loin de sa cause.

**3. « Some clientbound payloads are missing client-side handlers: [blueprint:screen_close] »**
— au premier lancement du **client**, celui-là. NeoForge sépare l'événement qui déclare les
paquets de celui qui pose les traitements **descendants**, exprès : un traitement client
touche des classes absentes d'un serveur dédié, et les déclarer au même endroit que les
types les y ferait charger. Il ne fait pas que le permettre, il le **vérifie** au démarrage
et nomme les manquants.

La contrainte a corrigé une conception bancale : le réseau client déposait ses récepteurs
dans la classe du réseau serveur, faute d'endroit évident où les mettre. Il a maintenant
son propre événement, `RegisterClientPayloadHandlersEvent`, et la classe serveur ne pose
plus que les types et les traitements montants.

Ces trois pannes sont la meilleure justification qu'on puisse donner à la règle « écrit et
compilé ne veut pas dire livrable ». Aucune n'était visible au compilateur ; les trois
étaient visibles à la première seconde d'exécution.

#### Le serveur piloté, et pourquoi il existe

NeoForge 1.21.11 a bien un cadre de gametests, mais c'est le **nouveau**, orienté données
(`GameTestInstance`, `TestEnvironmentDefinition`), sans rapport avec les annotations que
`gametest/` utilise côté Fabric. Le porter est un chantier en soi.

En attendant, le serveur dédié lit ses commandes sur l'entrée console. `scenario-serveur.txt`,
à la racine, est joué **par les deux chargeurs** — `-Pblueprint.scenario` sur
`:runServer` comme sur `:neoforge:runServer`. Un fichier unique, parce que comparer deux
chargeurs sur deux entrées différentes revient à comparer deux souvenirs.

Le harnais a lui-même appris quelque chose : poussées d'un bloc, les commandes sont lues
avant que le monde principal existe, la source console n'a alors pas de niveau, et
**toutes** échouent sur `getLevel().getGameRules()`. Elles attendent donc trente secondes.
Ce n'était pas un défaut du portage — mais ça y ressemblait beaucoup dans le journal.

#### La faute du banc n'est pas une faute du portage

Le scénario finit sur :

```
Nœud « blueprint:player/send_message » : le pin « player » n'a ni valeur ni défaut
Blueprint « blueprint:bench » en faute — désactivé
```

C'est **le comportement attendu**, et il est le même sur les deux chargeurs par
construction : `BlueprintMod.runBpc` ne pose le pin `player` que si la source en a un
(`if (source.getPlayer() != null)`), et une commande tapée dans la console n'a pas de
joueur. Ce code est commun, aucun chargeur n'y intervient.

Ce que la faute prouve, au passage : la VM a exécuté le graphe, le nœud a levé, la faute a
été attribuée à son nœud, journalisée, et le blueprint désactivé — toute la chaîne
d'isolation a fonctionné.

Le rejouer à l'identique sur Fabric demanderait d'accepter l'EULA de Minecraft dans
`run/eula.txt`, qui est aujourd'hui à `false` : c'est une décision qui appartient au
propriétaire du dépôt, pas au portage. (Le serveur de développement NeoForge, lui, n'a pas
de fichier d'EULA du tout — ModDevGradle contourne la vérification.)

**Détail qui confirme le lot B.** Dans le journal, « Blueprint initialisé » sort sur
`modloading-worker-0` et « Contenu déclaré » sur `modloading-sync-worker` : le report
entre « demander » et « faire » a réellement eu lieu, sur deux phases distinctes. Le
choix du lot B de mettre le bilan **dans** l'action — et non après — n'était donc pas une
précaution théorique : écrit après, il aurait annoncé zéro item sur un dossier plein.

**F — métadonnées, CI, publication.** `check.yml` construit les deux, `build.yml` publie
deux JARs, `modrinth.md` et le `README` disent Fabric **et** NeoForge.
*Fini quand :* une étiquette de version produit les deux artefacts sans intervention.

Le point de non-retour est **D** : après lui, revenir à un JAR unique coûterait plus cher
que finir. Avant lui, tout ce qui a été fait est utile même si on s'arrête — A, B et C
sont du nettoyage de frontières, pas de la dette de portage.

---

## 10. Ce qui peut mal tourner

| Risque | Pourquoi c'est sérieux | Ce qui l'atténue |
|---|---|---|
| Numérotation réseau divergente entre loaders | Client Fabric ↔ serveur NeoForge se déconnectent sur un paquet mal lu, sans message clair | Le gametest du lot B, écrit **avant** le lot E |
| `canSend` de sémantique différente | Invisible en développement, visible chez le joueur en vanilla | Test explicite client nu ↔ serveur moddé |
| La frontière fuit dans les modules communs | Se découvre au lot E, quand il est cher de revenir | Le classpath commun sans fabric-api, vérifié par `check` |
| `BlueprintMod` scindé, un abonnement oublié | Un événement du monde ne déclenche plus rien sur NeoForge, en silence | Les vingt `fire` sont énumérables : un test qui compte les types d'événements branchés |
| Le contenu déclaré ne s'enregistre pas sur NeoForge | Épic 11 muette sur un loader entier | Lot B isolé et testé avant tout le reste du portage |

Le fil commun de ces cinq risques : **ils se manifestent tous par du silence**, pas par une
exception. C'est la raison pour laquelle les lots B et C portent leurs tests avant que
NeoForge existe — au moment où on peut encore comparer à un comportement de référence.
