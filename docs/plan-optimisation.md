# Plan d'optimisation — épics 13 à 19

**État : le plan est terminé, sauf 15b — tenté puis retiré à dessein.** Établi par une
lecture complète du dépôt (six audits parallèles), dont chaque constat portant a été
revérifié ligne à ligne, puis livré par lots avec un banc par correction.

| Épic | État | Résultat mesuré |
|---|---|---|
| 18 — bancs | **fait** | `VmAllocationTest` (neuf), `CompilerPerfTest` (second cas + agrégation) |
| 14 — index des liens | **fait** | compilation dense **112 ms → 6,2 ms** ; coût par lien **× 3,59 → × 1,00** |
| 13a — bornes manquantes | **fait** | `string/concat`, `string/replace`, `world/particles`, `list/add`, `map/put` |
| 15a — corrections mécaniques | **fait** | double `varsOf`, double `all()`, `Optional` de purge |
| 19a — copies gratuites | **fait** | `writeList`, double `node.config()` |
| 13b — calibrer les `fuelCost` | **fait** | 95 nœuds tarifés, garde-fou en place, voir §2 bis |
| 15b — abonnement sélectif | à faire | risque le plus élevé du plan |
| 16 — boucle de la VM | **fait** | **744 → 288 octets/appel** ; ordonnanceur en temps linéaire |
| 17a — minimap et `isWired` | **fait** | coût d'`isWired` **× 4,18 → × 1,01** en pente, **12 869 → 441 µs** en absolu |
| 19b — reflet disque hors du tick | **fait** | écriture sur `nonCriticalIoPool`, sérialisée par blueprint |
| 17b — HUD et `childrenOf` | **fait (2 points sur 3)** | `childrenOf` **× 3,67 → × 0,42** ; layout du HUD mémoïsé |
| 19c — cache d'encodage | **fait** | encodage sauté pour tout graphe inchangé ; clef `(revision, enabled)` |
| 17b — `show_hud` idempotent | **fait** | même écran, même version → aucun paquet ; épic 17 complet |
| garde de chargement | **fait** | `world/get_block` et `is_block` ne génèrent plus de chunk |
| 15b — abonnement sélectif | **suspendu** | tenté puis retiré : change la sémantique, pas seulement le coût |

Construction complète verte : 760 tests, couverture, isolation de l'api, référence des
nœuds, surface publique.

---

## 0. Le résultat en une page

La première version de ce plan visait la boucle de la VM. Elle avait raison sur le fond et
tort sur l'ordre : **trois problèmes plus graves étaient hors de son champ**, et deux
d'entre eux ne sont pas des problèmes de performance.

| | Sujet | Nature | Ampleur |
|---|---|---|---|
| **13** | Le fuel ne tarife rien | **Sûreté** | facteur 10⁴ entre nœuds au même prix |
| **14** | `Blueprint` n'indexe pas ses liens | Performance | O(N·L + L²) → O(N + L) |
| **15** | La paresse des événements est neutralisée | Performance | ~10 000 `Optional`/tick pour rien |
| **16** | La boucle de la VM alloue | Performance | 5 objets par appel de nœud |
| **17** | L'éditeur recalcule par image | Confort | minimap ≈ 480 k quads/s |
| **18** | Le banc ne voit pas ce qui coûte | Méthode | transversal, à faire en premier |
| **19** | La sauvegarde réencode tout, toujours | Performance | tout l'état toutes les 5 min, puis recopié |

Un fait qui traverse les six : **le projet a déjà écrit les règles qu'il enfreint.**
`docs/architecture/coding-standards.md` §5 dit *« Pas d'allocation dans `BlueprintVm#step`
hors des valeurs produites par les nœuds »*, *« Pas de `stream()` dans le code exécuté par
tick ni dans le rendu de l'éditeur »*, et *« Toute structure indexée par nœud utilise
l'index de slot, pas une `HashMap<UUID, ?>`, dans les chemins chauds »*. Les épics 14, 16
et 17 sont exactement ces trois règles, non tenues. Il ne s'agit donc pas d'ajouter une
exigence au projet, mais de payer une dette qu'il a lui-même inscrite.

---

## 1. Sur le « système de pool » — la nuance qui décide

L'intuition de départ était bonne, mais elle ne s'applique qu'à un seul endroit.

Sur un JVM moderne, HotSpot C2 fait de l'*escape analysis* : un objet dont il prouve qu'il
ne quitte pas la méthode n'est pas alloué, ses champs deviennent des variables locales
(*scalar replacement*). Pooler un tel objet est un recul net — on échange une allocation
gratuite contre une indirection, un état partagé et un risque de fuite.

**Mais l'analyse échoue dès que l'objet s'échappe**, et c'est le cas de `NodeContextImpl` :
il est passé à `type.action().run(ctx)` (`BlueprintVm.java:166`), un appel virtuel
**mégamorphique** — quatre-vingts implémentations derrière un même site. C2 ne peut ni
l'inliner ni prouver le confinement. L'objet est réellement alloué, à chaque appel de
nœud, et sa réutilisation est réellement gagnante.

C'est la seule chose qu'on poole (épic 16). Partout ailleurs, le gain vient de **travail
supprimé** — un index qui remplace un balayage, un cache qui remplace un recalcul. Du
travail supprimé vaut toujours mieux qu'un objet recyclé.

> [JVM Anatomy Quark #18: Scalar Replacement](https://shipilev.net/jvm/anatomy-quarks/18-scalar-replacement/) ·
> [HotSpot Escape Analysis Status (OpenJDK)](https://cr.openjdk.org/~cslucas/escape-analysis/EscapeAnalysis.html)

---

## 2. Épic 13 — Le fuel ne tarife rien *(sûreté, priorité absolue)*

### Le constat

`grep fuelCost core/src/main/java/fr/blueprint/core/nodes/` → **zéro résultat**, sur 3 568
lignes et ~80 nœuds. Le défaut du builder est `1` (`NodeType.java:161`).

`math/add` et `world/raycast` sur 128 blocs coûtent donc **le même fuel**. Le mécanisme est
pourtant complet de bout en bout : `NodeType.Builder.fuelCost()`, l'annotation
`@BlueprintNode(fuelCost=…)`, `Compiler.java:186`, `IrNbt.java:74`, jusqu'à
`spent += call.fuelCost()` (`BlueprintVm.java:72`). Il est câblé, persisté, testé — et
jamais renseigné.

Avec `fuelPerTick = 10_000` (`BlueprintConfig.java:30`), le budget autorise **dix mille
appels de nœud par tick quel que soit leur poids**. Le README annonce une « VM bornée par
un budget de fuel » (NFR4) : c'est vrai en *nombre*, aveugle en *coût*. Et la police de
`BlueprintScheduler.java:181-189` ne se déclenche que sur `OUT_OF_FUEL` — un graphe qui
brûle ses dix mille fuel en nœuds lourds puis rend `Done` n'est jamais inquiété, tick après
tick, indéfiniment.

### Quatre conséquences vérifiées ligne à ligne

**`string/concat` n'est pas borné** — `StandardNodes.java:176` :
`ctx.<String>in("a") + ctx.<String>in("b")`, sans clamp, alors que toute sa famille dans
`TextMathNodes` clampe à `MAX_LENGTH = 32_768`, borne dont le commentaire dit
explicitement qu'elle « borne la mémoire d'une boucle ». En boucle via `var/set`,
`s = concat(s, s)` double à chaque tour : le gigaoctet en une trentaine d'itérations, soit
~2 % d'un seul tick. L'oubli paraît accidentel — le nœud date de la story 7.2, la borne lui
est postérieure.

**`string/replace` clampe après avoir alloué** — `TextMathNodes.java:113-114` :
`clamp(text.replace(search, replacement))`. Le `replace` construit le résultat entier avant
la troncature. Texte de 32 768 caractères, `search` = `"a"`, remplacement de 32 768
caractères → ~10⁹ caractères, ~2 Go, **en un appel à 1 fuel**.

**`world/particles` ne clampe pas `count`** — `WorldNodes.java:169-170` passe
`ctx.<Integer>in("count")` brut à `sendParticles`, alors que son jumeau `player/particles`
écrit `Math.clamp(count, 0, 512)` (`ClientNodes.java:135`). Asymétrie entre deux nœuds
jumeaux, en permission `GAMEPLAY`, avec diffusion à tous les joueurs du niveau.

**`world/get_block` peut générer un chunk** — `WorldNodes.java:47-48` appelle
`ctx.level().getBlockState(pos)` sur une position arbitraire, sans test de chargement. En
vanilla, cela peut déclencher une génération synchrone sur le thread serveur : des dizaines
de millisecondes, pour 1 fuel, en permission `SAFE`.

S'y ajoutent `list/add` et `map/put` (`ListNodes.java:147-149`) qui font **deux copies
complètes** par ajout — construire une liste de N éléments coûte O(N²) — et sans aucune
borne de taille, contrairement à `MAX_PARTS`, `MAX_RESULTS` et `MAX_LINES` ailleurs.

### Ce qu'il faut faire

1. **Borner ce qui ne l'est pas** : `string/concat`, `text/concat`, `world/particles`,
   la taille des listes et des maps. Ce sont des oublis ponctuels dans un travail de
   bornage par ailleurs sérieux et bien commenté — c'est ce qui rend ces trous crédibles
   comme oublis plutôt que comme choix.
2. **Calibrer les `fuelCost`** à partir de mesures, pas d'intuitions. L'outil existe déjà :
   `debug/Profiler.java` enregistre `nanos` **et** `fuel` par nœud. Il n'a jamais servi à
   ça.
3. **Tester `world/get_block` contre le chargement** plutôt que forcer la génération.
4. **Un test qui interdit `fuelCost == 1`** pour tout nœud non pur touchant au monde, aux
   entités ou au réseau. Sans ce garde-fou, le prochain nœud ajouté reprendra le défaut.

> **Ce n'est pas une story de performance.** Un joueur sans permission peut geler ou faire
> tomber un serveur en restant dans le budget annoncé. Cela passe devant tout le reste,
> y compris devant les gains les plus spectaculaires du plan.

---

### 2 bis. Épic 13b — Calibration des `fuelCost`, journal de bord

Mesure : `FuelCalibrationTest`, coût réel de chaque nœud rapporté à `math/add`, l'unité
naturelle. La mesure passe par le même harnais pour tous — exact plutôt que commode, car
dans la VM aussi un appel coûte l'enveloppe *plus* le travail propre.

**Lot 1 — nœuds purs exécutables sans serveur. Fait.**

Deux résultats, dont un inattendu :

*Aux entrées par défaut, tous les nœuds purs mesurent entre ×0,8 et ×1,8.* Leur travail
propre est noyé dans l'enveloppe d'appel — les 744 octets et le coût fixe du contexte. Leur
tarif de 1 était donc **juste**, et il est désormais vérifié plutôt que supposé.

*Au pire cas borné, cinq nœuds dépassent quatre fois l'unité.* C'est là que le tarif plat
mentait :

| Nœud | Pire cas mesuré | Tarif posé |
|---|---|---|
| `text/colored` | ×20 | 20 |
| `string/upper` | ×10 | 10 |
| `string/lower` | ×5 | 5 |
| `string/join` | ×4 | 4 |
| `convert/to_number` | ×4 | 4 |

Règle appliquée : on tarife dès que le pire cas dépasse quatre fois l'unité — en deçà,
l'imprécision de mesure domine et surtarifer n'apporte rien.

**Ce que le lot 1 a appris sur la méthode.** Le pire cas n'est mesurable que parce que les
bornes existent (épic 13a). Sans `MAX_LENGTH` ni `MAX_ELEMENTS`, le coût de ces nœuds n'a
aucun majorant, et **aucun `fuelCost` constant n'aurait pu être juste**. Les deux épics ne
sont pas seulement voisins : le premier rend le second décidable.

**Lot 2 — rayons et requêtes de monde. Fait, par ANALYSE et non par mesure.**

Ces nœuds demandent un monde vivant : `ctx.level()` est nul headless, `FuelCalibrationTest`
ne peut pas les exécuter. Le tarif vient donc d'une lecture du travail réel, et chaque
`fuelCost` porte en commentaire la mention explicite « tarif par analyse » avec son
raisonnement. L'incertitude est d'un facteur trois environ — sans importance devant l'écart
au tarif précédent, qui était de **un**.

| Nœud | Travail réel | Tarif |
|---|---|---|
| `query/entities_near` | au rayon max, boîte de 256 blocs de côté ≈ 17 M de blocs, ~1000 sections d'entités balayées | 200 |
| `world/raycast_entity` | boîte jusqu'à 128 blocs de long + un `box.clip` par candidat | 150 |
| `world/raycast` | DDA sur ~380 positions à la portée max, `getBlockState` + VoxelShape à chacune | 100 |
| `world/surface` | résolution du chunk puis lecture de la carte de hauteurs | 10 |
| `world/light` | deux consultations du moteur de lumière | 5 |

Le tarif couvre le **pire cas**, car `distance` et `radius` sont des entrées que l'auteur du
graphe choisit, alors que le fuel d'un nœud est fixé à la compilation. Leurs bornes
limitaient l'amplitude d'un appel ; rien n'en limitait le nombre.

**Lot 3 — blocs, inventaire, interfaces, et le reste du monde. Fait, par ANALYSE.**

| Nœud | Tarif | Motif |
|---|---|---|
| `world/explosion` | 100 | destruction de blocs, dégâts au rayon, propagation, paquets |
| `world/spawn_entity` | 30 | création d'entité et inscription dans le niveau |
| `world/set_block`, `world/drop_item` | 20 | mutation du monde, mises à jour de voisinage |
| `gui/refresh_all` | 50 | toutes les liaisons recalculées **par spectateur** |
| `gui/refresh` | 15 | toutes les liaisons d'un écran, un joueur |
| `gui/<x>_all` | 10 | une modification en file par spectateur |
| `gui/<x>` | 3 | une modification en file, un joueur |
| `player/count_item`, `player/has_item` | 10 | registre + 36 emplacements parcourus |
| `world/set_time`, `world/set_weather` | 10 | écriture d'état diffusée à tous les clients |
| `world/get_block`, `world/is_block`, `world/block_state`, `world/play_sound`, `world/particles`, `world/bossbar_remove`, `query/nearest_player` | 5 | chunk, registre, ou paquet aux joueurs proches |
| `query/players` | 3 | copie de la liste des joueurs |
| `world/get_time`, `world/is_day`, `world/get_weather`, `world/dimension` | 2 | lecture d'un champ déjà résolu |

Le barème est consigné dans la javadoc de `WorldNodes`, pour que le prochain nœud ajouté
s'y range sans avoir à redécouvrir le raisonnement.

**Le garde-fou est en place** — `FuelFloorTest`. Il refuse qu'un nœud atteignant le monde
reste au défaut du builder : `fuelCost` vaut 1 tant que personne ne l'a choisi, donc un
nœud de monde à 1 n'a pas été tarifé, il a été oublié.

À son premier passage, il a compté **97 nœuds atteignant le monde, dont 56 encore au tarif
par défaut**. Les tarifer tous dans la même séance aurait été cinquante-six jugements
bâclés : il garde donc les quatre catégories calibrées (`world/block`, `world/state`,
`world/effect`, `entity/query`, soit 23 nœuds) et tient la liste explicite de celles qui
restent. Chaque lot en déplace une. **L'épic 13b se termine quand cette liste est vide.**

**Lot 4 — `entity/read` et `entity/act`. Fait, par ANALYSE.**

| Nœud | Tarif | Motif |
|---|---|---|
| `entity/looking_at` | **100** | un `Level.clip` complet — voir ci-dessous |
| `entity/teleport` | 20 | déplacement, suivi de chunk, paquets |
| `entity/add_effect` | 10 | registre, effet appliqué, recalcul d'attributs, paquet |
| `entity/set_health`, `entity/heal` | 5 | écriture synchronisée vers le client |
| `entity/name`, `entity/type` | 3 | construction d'un `Component`, recherche inverse de registre |
| `entity/position`, `entity/health`, `entity/max_health`, `entity/is_alive`, `entity/as_player` | 2 | lecture d'un champ d'une entité déjà résolue |

**La prise de ce lot : `entity/looking_at` était un raycast déguisé en getter.** Il est
rangé dans `entity/read`, à côté de `entity/health` et `entity/name` qui coûtent deux — et
il exécute le même `Level.clip` que `world/raycast`, borné par la même `MAX_DISTANCE` de
128 blocs. Cinquante fois le prix de ses voisins de catégorie.

C'est l'argument le plus net en faveur du garde-fou : **la catégorie décrit ce qu'un nœud
répond, pas ce qu'il dépense**. Un tarif déduit du rangement dans la palette aurait laissé
celui-là à deux.

Garde-fou : 35 nœuds gardés, aucun au tarif par défaut.

**Lot 5 — `player/*`, `gui/*`, `scoreboard`. Fait, par ANALYSE. L'épic est clos.**

| Nœud | Tarif | Motif |
|---|---|---|
| `gui/open`, `hud/show` | 30 | l'écran entier réencodé en NBT puis gzippé (`ScreenSync.toBytes`) |
| `hud/hide_all` | 10 | un paquet par écran affiché |
| `player/give_item`, `player/remove_item` | 10 | parcours et mutation de l'inventaire |
| `player/*` (retours, sons, particules, titres), `score/*`, `gui/close`, `hud/hide` | 5 | un paquet vers une connexion, ou la résolution d'un objectif |
| `team/of`, `team/same` | 3 | consultation d'équipe |
| `player/main_hand`, `player/off_hand` | 2 | lecture d'un champ |

**État final : 95 nœuds gardés, aucun au tarif par défaut, aucune catégorie exemptée.**

La constante `NOT_YET_CALIBRATED` reste dans le test, vide, plutôt que supprimée : elle est
le seul endroit où l'on puisse différer délibérément une catégorie, et une assertion exige
désormais qu'elle le demeure. Y ajouter une ligne fait échouer la construction — un report
doit se discuter, pas se glisser.

### Ce que l'épic 13b a coûté et rendu

Cinq lots. Un banc de mesure (`FuelCalibrationTest`), un garde-fou (`FuelFloorTest`), et
95 nœuds tarifés — dont **un seul** l'a été par mesure directe, les autres par analyse
documentée, faute de pouvoir exécuter un monde headless. La distinction est portée dans
chaque commentaire ; aucune analyse n'est présentée comme une mesure.

Trois choses apprises, dans l'ordre où elles ont fait mal :

1. **Un banc peut passer à vide.** Deux fois : `CompilerPerfTest` quand l'index des liens
   l'a rendu trop rapide pour l'horloge, et la première version du pire cas de
   `FuelCalibrationTest`, qui mesurait ×0,0 sur tous les nœuds et **passait**. Les deux se
   corrigent par agrégation et par une assertion de non-nullité — c'est le §7.1 qui le dit,
   et il le dit parce que ce projet s'est déjà fait prendre.
2. **Les bornes rendent la tarification décidable.** Sans `MAX_LENGTH` ni `MAX_ELEMENTS`,
   le coût d'un nœud de chaîne n'a aucun majorant, et aucun `fuelCost` constant ne peut
   être juste. L'épic 13a n'était pas voisin du 13b : il en était la condition.
3. **La catégorie ne dit rien du coût.** `entity/looking_at` était rangé parmi les lectures
   et faisait un `Level.clip` de 128 blocs — cinquante fois le prix de ses voisins. Tarifer
   au barème de la catégorie l'aurait laissé à 2.

**Signalé au passage, hors périmètre du tarif** : `world/get_block` appelle `getBlockState`
sur une position arbitraire sans test de chargement, ce qui peut déclencher une génération
de chunk synchrone — des dizaines de millisecondes. Aucun `fuelCost` ne peut couvrir cela
sans rendre le nœud inutilisable ; le remède est une garde de chargement, pas un tarif.

> **Livré.** `world/get_block` et `world/is_block` testent désormais `Level.isLoaded(pos)`
> et portent une sortie **`loaded`**. Sur un chunk non chargé, `state` rend de l'air et
> `matches` rend faux, avec `loaded` à faux pour le dire.
>
> **Une sortie explicite plutôt qu'une valeur muette**, parce que c'est la règle du dépôt —
> « touché » de `world/raycast`, « valide » de `world/block_state`, « trouvé » de
> `query/nearest_player`. Rendre de l'air sans le dire ferait croire à un graphe qu'il a
> regardé, et qu'il n'y avait rien.
>
> **C'est un changement de comportement d'un nœud livré en v1.0.0**, consigné comme tel au
> CHANGELOG. Ajouter une *sortie* est additif : aucun lien existant ne la référence, donc
> aucun graphe enregistré n'a besoin d'être retouché. Nom vérifié au `javap` dans le jar
> mergé, pas de mémoire.

---

## 3. Épic 14 — Indexer les liens dans `Blueprint` *(un changement, six chemins)*

### Le constat

`Blueprint.java:104-118` répond à **toute** question sur les liens par un balayage complet :

```java
public List<Link> linksFrom(UUID node, String pin) {
    return links.stream().filter(l -> l.fromNode().equals(node) && l.fromPin().equals(pin)).toList();
}
```

`linksInto` et `linksTouching` sont identiques. Aucun index. Chaque appel : un pipeline de
stream, un balayage de tous les liens, une liste allouée. Et `links()` (`:76-78`) alloue en
prime un `Collections.unmodifiableSet` **à chaque appel**.

C'est la violation la plus littérale de la règle §5 (*« pas de `stream()` dans le code
exécuté par tick ni dans le rendu de l'éditeur »*), et elle irrigue six chemins :

| Chemin | Coût | Fréquence |
|---|---|---|
| `GraphValidator.validate` (`:104-121`) | **O(N·L + L²)** | chaque compilation, chaque validation débouncée |
| `GraphGuard` (`:85-104`) | **O(L²)** sur le fil serveur | chaque enregistrement depuis l'éditeur |
| `canConnect` pendant un drag de fil | O(pins visibles × L) | **par image**, sans débouncé |
| `ScriptGenerator` (`:383-498`) | O(N × L), facteur 4 à 6 | vue script, copier, export |
| `ClipboardCodec` (`:81-123`) | O(K²) | chaque Ctrl+C / Ctrl+V |
| `AutoLayout` (`:75-111`) | O(N × L), **O(N²·L) au pire** | bouton de mise en page |

Aux plafonds en vigueur (N ≤ 1 000, L ≤ 4 000), la validation atteint l'ordre de **vingt
millions de comparaisons d'UUID à travers des streams**. Et `GraphGuard` fait ce travail
**sur le fil serveur, à chaque enregistrement** — un client peut en déclencher dix par
fenêtre de quotas.

### Ce qu'il faut faire

Deux `Map` d'index (`byFrom`, `byTo`) entretenues dans `putLink`/`dropLink` — les deux
seuls points de mutation, tous deux *package-private*, avec six appelants connus. Plus la
reconstruction dans `Blueprint.copy()` (`:225`). `linksFrom`/`linksInto`/`linksTouching`
deviennent O(degré), et **toute la chaîne redescend en O(N + L)**.

C'est le meilleur rapport gain/risque du dépôt : deux structures, deux points d'écriture,
six chemins débloqués, aucun changement de comportement observable. Seule contrainte :
l'ordre de `linksTouching` doit rester déterministe — des `ArrayList` en ordre d'insertion
suffisent.

### La faille du banc, à corriger d'abord

`CompilerPerfTest` mesure 1 000 nœuds en chaîne — mais **ses nœuds n'ont aucun pin de
données** (`CompilerPerfTest.java:28-31` : un nœud `exec` sans entrée ni sortie). Cela
neutralise `GraphValidator:78` et la moitié des balayages. Le banc ne voit donc pas la
quadratique qu'il est censé surveiller. Ajouter un second cas — même nombre de nœuds, liens
de données denses — la ferait apparaître avant qu'on la corrige, ce qui est l'ordre correct
(cf. §7, *« un banc qu'on n'a jamais vu échouer ne prouve rien »*).

---

## 4. Épic 15 — Rendre la paresse des événements vraie

### Le constat

`EventDispatcher` documente une garantie (AC4) : *« sans abonné, le constructeur de charge
utile n'est même pas invoqué — émettre un événement que personne n'écoute coûte une lecture
de map »*. La garde existe bien (`EventDispatcher.java:71-74`).

Elle n'est **jamais vraie en production**. `BlueprintMod.java:124` appelle
`bridge.wire(dispatcher, registries.events().all())`, et `wire`
(`BlueprintEventBridge.java:59-63`) abonne le pont aux **vingt-six** événements du
registre, écoutés ou non. La liste n'est donc jamais vide : toute émission construit sa
charge utile et entre dans `launchMatching`. La paresse que les tests mesurent existe parce
qu'en test rien n'est câblé.

Le coût ne se paie pas sur `server_tick` (une émission par tick) mais sur `entity_damaged`,
`entity_killed`, `player_attack_entity` — des événements `GLOBAL` déclenchés à chaque
instance de dégât du serveur : ferme à mobs, feu, lave, noyade. Avec cinquante blueprints
et deux cents dégâts par tick, l'ordre de grandeur est **1 % du budget de tick et ~10 000
`Optional` alloués par tick**, pour ne rien lancer.

Deux aggravations dans le même chemin :

- `BlueprintEventBridge.java:86` purge tout le cache d'entrées **à chaque émission**, avec
  un `Optional` par entrée (`manager.get(id).isEmpty()`). Le commentaire dit « peu
  fréquent » ; c'est le chemin le plus chaud du mod.
- `manager.all()` est appelé **deux fois** par émission (`:66` et `:69`), chacune allouant
  un `unmodifiableCollection`.
- `BlueprintMod.java:504-526` appelle `varsOf(server)` **deux fois** par lancement — deux
  prises de moniteur sur un `WeakHashMap` synchronisé — et `seedDefaults` parcourt les
  variables du graphe à **chaque déclenchement**, alors que les déclarations ne changent
  qu'avec la révision.

### Ce qu'il faut faire

Un **compteur de génération** sur `BlueprintManager`, incrémenté par
`create`/`delete`/`adopt`/`save`/`setEnabled`. Le pont recalcule alors, et seulement alors :
l'ensemble des événements réellement écoutés (`subscribe`/`unsubscribe` en conséquence), la
purge du cache d'entrées, et le drapeau « ce blueprint a-t-il des défauts à semer ». La
paresse redevient vraie : `fire()` sur un événement que personne n'écoute coûte un
`ConcurrentHashMap.get`.

**Risque : le plus élevé du plan.** Un point d'invalidation oublié produit un blueprint qui
cesse silencieusement de se déclencher — exactement la panne muette que le projet redoute.
D'où la contrainte : **une seule** source d'invalidation, jamais cinq appels dispersés, et
un test « j'ajoute un nœud d'événement, il part au tick suivant ». Les corrections
mécaniques (double `varsOf`, double `all()`, `containsKey` au lieu d'`Optional`) sont sans
risque et sont parties séparément, en 15a.

### 15b — tenté, puis retiré

L'implémentation a été écrite en entier : compteur de composition sur `BlueprintManager`,
signature combinant ce compteur et les révisions de tous les graphes (parce que le compteur
seul ne voit pas une édition appliquée directement à un blueprint vivant), recalcul de
l'ensemble abonné en fin de tick, purge du cache d'entrées déplacée hors du chemin chaud.

**Quatre tests de comportement existants sont passés au rouge**, dont
`serverTickTriggersActiveBlueprints` : il crée un blueprint portant un nœud d'événement puis
**émet immédiatement**, en attendant qu'il se déclenche. Avec un abonnement recalculé en fin
de tick, il ne se déclenche qu'au tick suivant.

Ce n'est pas un défaut de l'implémentation, c'est ce que l'approche coûte : la sensibilité
d'un graphe passerait de « dès qu'il existe » à « au tick suivant ». **C'est un changement
de sémantique déguisé en optimisation**, et la règle du plan est explicite — une story qui
oblige à modifier un test de comportement existant est abandonnée, pas discutée.

Le tout a donc été retiré, et la construction est verte sans lui.

**Ce qu'il faudrait pour le reprendre**, dans l'ordre de préférence :

1. **Décider que la sémantique peut bouger.** Le délai réel est inférieur à un tick et
   aucun joueur ne le percevrait ; mais c'est une décision de conception, à prendre les
   yeux ouverts, pas un effet de bord. Les quatre tests seraient alors à réécrire
   *délibérément*, avec la nouvelle garantie écrite dans leur javadoc.
2. **Pousser au lieu de sonder** : que le graphe prévienne quand ses points d'entrée
   changent. Cela demande un signal sur `Blueprint`, que rien n'émet aujourd'hui — les
   éditions n'incrémentent qu'une révision que personne n'observe.
3. **Ne rien changer.** Le coût mesuré est de l'ordre de 1 % du budget de tick sur un
   serveur à cinquante graphes et deux cents dégâts par tick. Ce n'est pas rien, mais c'est
   le seul point du plan dont le prix se paie en garantie plutôt qu'en travail.

---

## 5. Épic 16 — La boucle de la VM

Le sujet initial, toujours valable, mais désormais quatrième.

**Cinq allocations par appel de nœud** : le `LinkedHashMap` des entrées
(`BlueprintVm.java:149`), un `Map.copyOf` qui en fait une **deuxième copie**
(`NodeContextImpl.java:57`), le `LinkedHashMap` des sorties (`:29`), le contexte lui-même
(`BlueprintVm.java:156`), et un `Optional` par résolution de type
(`NodeRegistryImpl.java:120`).

**a. Un contexte réutilisé par exécution.** L'`ExecutionState` le porte, la VM appelle
`reset(...)` au lieu de construire. Un par exécution et non un pool global : pas de
synchronisation, pas de vol, durée de vie exactement celle de l'état qui le porte.

**b. La garde anti-fuite doit survivre — non négociable.** La story 2.3 (AC5) garantit
qu'un mod conservant le contexte et le rappelant lève (`NodeContextImpl.java:236-241`).
Remettre `valid = true` à chaque réutilisation détruirait cette garantie **en silence** :
le mod fautif lirait les entrées d'un autre nœud sans rien remarquer. Il faut un **jeton de
génération** — un `long` incrémenté à chaque `reset`, capturé par l'appelant, comparé à
chaque accès. Sans cette pièce, la story ne passe pas.

**c. `Map.copyOf` disparaît** : la copie défensive n'a plus d'objet avec une durée de vie
contrôlée.

**d. La résolution de type sort de la boucle** : un `NodeType[]` parallèle aux
instructions, résolu une fois par `Ir` et invalidé avec elle. Le `Faulted` des nœuds
fantômes (`BlueprintVm.java:146`) est préservé par une entrée nulle.

**e. `findPin` est un scan linéaire** (`NodeContextImpl.java:243-250`) : chaque `ctx.in()`
parcourt la liste des pins en comparant des chaînes. Deux `Map<String, PinSpec>` privées
construites une fois dans `NodeType` corrigent cela **sans toucher la surface publique** —
`NodeType` est une classe finale à champs privés, `docs/api-surface.txt` ne bouge pas,
`:api:checkApiIsolation` reste vert. Même remède pour `EventType.output(String)`
(`api/.../EventType.java:52-56`), sur le chemin de chaque émission.

**f. L'ordonnanceur en temps linéaire.** `BlueprintScheduler.tick` (`:150-178`) est
quadratique : `ready.contains(e)` en O(n) **dans** la boucle, `remove` en O(n), une copie
de la file et un `HashSet` par tick. À deux cents exécutions simultanées, des dizaines de
milliers de comparaisons par tick pour rien. Un champ `alive` remplace `contains`, un
balayage à curseur d'écriture remplace les `remove`, le `Set` devient un champ vidé.
**Bonus** : `slice = budget / ready.size()` ne récupère jamais le reliquat d'une exécution
qui finit tôt ; un second passage sur ce qui reste prêt remonte le débit sans toucher à
l'équité du premier tour.

Restent deux pistes **conditionnées au banc**, parce qu'elles touchent l'IR persistée et
donc la reprise après redémarrage (story 6.1) : les pins en tableaux indexés plutôt qu'en
maps à clés `String`, et `ExecutionState.frames` en `int[]` plutôt qu'en `Deque<Integer>`
(`ExecutionState.java:21`, autoboxing par `CallSub`). Un gain de quelques pourcents ne vaut
pas un format persistant plus fragile.

### Livré — 744 → 288 octets par appel de nœud

| Ce qui est parti | Comment |
|---|---|
| la copie défensive des entrées | `Map.copyOf` supprimé : la table reçue est construite pour cet appel et n'est plus touchée |
| l'`Optional` de résolution de type | `ResolvedIr`, calculé une fois par exécution au lieu d'une fois par nœud |
| la recherche de pin par balayage de chaînes | index nom → `PinSpec`, dans le même `ResolvedIr` |
| les deux tables d'entrées et de sorties | prêtées par l'`ExecutionState` et réemployées |
| le quadratique de l'ordonnanceur | drapeau `alive` + compaction unique, à la place de `contains`/`remove` linéaires **dans** la boucle |

**Ce qui n'a délibérément pas été fait, et pourquoi.**

*Le contexte reste neuf à chaque appel.* Le plan proposait de le mutualiser aussi — c'était
le « pool » du sujet initial. Mais la garde anti-fuite d'AC5 tient précisément parce que
chaque appel a son objet, invalidé à la sortie et jamais revalidé. Un objet réutilisé
redevient valide, et un mod ayant conservé le contexte lirait alors les entrées du nœud
suivant. Ce n'est pas théorique : en désactivant la garde, `VmBufferSharingTest` montre que
le contexte fuité rend **222**, la valeur du nœud d'après, sans lever. Le contexte coûte peu
et paie une garantie ; ce sont les tables qui coûtaient. On a donc mutualisé les tables et
gardé la garantie — et ajouté le test qui la surveille, celui-là même qui manquait puisque
le risque n'existait pas avant.

*Le reliquat de budget n'est pas redistribué.* Le plan proposait un second passage avec le
fuel non dépensé. Cela change **quand** la police des dépassements se déclenche : une
exécution qui finissait en `OUT_OF_FUEL` pourrait désormais terminer, et le compteur de
ticks consécutifs ne monterait plus. C'est une décision de politique, pas une optimisation,
et elle ne se prend pas au détour d'un épic de performance.

---

## 6. Épic 17 — L'éditeur et les écrans, par image

L'éditeur est **globalement sain** : le culling des nœuds est correct et alloue zéro
(`CanvasWidget.java:440-449`, tampon `visible` réutilisé), les fils sont cullés
(`WireLayer.java:46-58`), la validation et la vue script sont débouncées à 300 ms, le cache
d'infobulle est clefé en coordonnées monde avec plafond temporel. Ce sont des oublis
ponctuels, pas une architecture fautive.

**Les deux vrais postes ne sont pas ceux qu'on croit.**

**a. La minimap coûte plus cher que tous les nœuds visibles réunis.** `Minimap.java:35-47`
trace chaque lien **pixel par pixel** — un `g.fill` par pixel — sans aucun cache et sans
culling, et réalloue une `HashMap` de tous les nœuds par image. Sur 150 nœuds et 250 liens :
~8 000 `g.fill` et ~900 `double[]` **par image**, soit ~480 000 quads/s. Le contenu ne
dépend que du graphe : un cache invalidé par `blueprint.revision()`, avec seulement le
rectangle de vue redessiné par image, supprime tout.

> **Livré (17a).** `isWired` vit désormais dans `CanvasController`, adossé à un index
> `Map<UUID, Set<String>>` reconstruit à la révision — le motif déjà employé pour
> `boxIndex`, deux méthodes plus haut. Banc `WiredPinsCacheTest`, en **pente** comme le
> §7.1 le préfère : quadrupler les liens ne doit pas quadrupler le coût d'une question qui
> n'en concerne aucun. **Vu rougir avant correction à ×4,18** — exactement le rapport des
> tailles, signature du balayage — puis **×1,01** après, et 12 869 µs → 441 µs en absolu.
>
> La minimap ne reconstruit plus sa table de nœuds par image (elle est réemployée), et un
> segment qui tient dans un pixel n'entre plus dans la boucle de tracé : sur un grand
> graphe, la vignette écrase la plupart des liens sous le pixel, et chacun était pourtant
> peint point par point.
>
> **Écarté délibérément** : mettre la minimap entière en cache d'image. Cela demanderait un
> tampon de rendu hors écran, donc une dépendance graphique nouvelle pour une vignette
> décorative. Les deux corrections ci-dessus suffisent et ne changent rien à l'architecture.

**b. `isWired` promet ce qu'il ne tient pas.** `CanvasWidget.java:521` porte le commentaire
*« Sans allocation (appelé dans la passe de rendu) »* — mais il itère
`controller.blueprint().links()`, qui alloue un `unmodifiableSet` **à chaque appel**
(`Blueprint.java:76-78`), et fait un balayage linéaire de tous les liens **par pin de
chaque nœud visible**, trois fois par rangée. Ordre de grandeur : ~110 000 comparaisons par
image sur un graphe réaliste. Un `Map<UUID, Set<String>>` des pins câblés, invalidé par
révision — le motif déjà employé pour `boxIndex` juste à côté — le ramène à une lecture.

**c. Le glisser-déposer invalide bien toute la géométrie à chaque image** — chaîne vérifiée
de `CanvasWidget.java:1326` à `EditOperation.java:118` (`bumpRevision`) puis
`NodeGeometry.java:56-65`. Réel, mais **moins cher** que (a) et (b), et la correction
propre (un compteur de révision « structurelle » distinct) introduit exactement la
duplication que le projet évite volontairement. À ne faire que si le profilage l'exige sur
de gros graphes. Note : l'accroche à la grille masque le problème, mais elle est **désactivée
par défaut** (`Camera.java:38`).

**d. Le HUD refait sa disposition complète à chaque image.** `BlueprintHud.java:47` appelle
`ScreenPainter.paint`, qui appelle `ScreenLayout.solve` (`ScreenPainter.java:173`) sans
cache — alors que le modal (`BlueprintScreen.layout()`) et le concepteur
(`ScreenCanvasController.rects()`) mémoïsent tous deux correctement, sur l'identité de
l'instance `Screen`. Le motif à recopier est à deux fichiers de là. Même oubli pour
`ScreenDesignerWidget.java:362` (`renderOverflow`), qui appelle `solve` à côté du cache.
Accessoirement `Screen.childrenOf` (`:124`) est en O(N) et appelé une fois par élément,
rendant `solve` quadratique : un index parent → enfants construit dans le constructeur du
record immuable le rend linéaire.

**e. `gui/show_hud` réencode et regzippe l'écran entier à chaque appel**
(`ServerBlueprintNet.java:644-659`), sans quota — seule voie d'ouverture d'écran qui
échappe au limiteur, contrairement à `openScreen` (`:510`). Un graphe branché sur le tick
envoie jusqu'à 64 Kio par joueur et par tick.

> **Livré (17b, deux points sur trois).**
>
> *`Screen.childrenOf` est indexé.* L'index parent → enfants est construit dans le
> constructeur — l'écran est immuable, il ne peut donc pas se périmer, et il est payé une
> fois par écran au lieu d'une fois par question. `ScreenLayout` en posait une par élément
> en descendant l'arbre : la passe de disposition était quadratique. Banc
> `ScreenChildrenIndexTest`, en pente, **vu rougir à ×3,67 avant correction** (le
> quadratique théorique vaut 4), **×0,42 après**, et 21 425 → 594 µs sur un écran de 128
> éléments. L'ordre de dessin est préservé — c'est un contrat, et le test le vérifie
> élément par élément.
>
> *Le HUD mémoïse sa disposition.* Sur l'**identité** de l'instance `Screen`, comme
> `BlueprintScreen.layout()` et `ScreenCanvasController.rects()` le font déjà :
> `HudView.show()` remplace l'instance à chaque mise à jour, donc une instance inchangée
> signifie un écran inchangé, et l'invalidation est exacte par construction. Au passage,
> `visible()` n'est plus appelé deux fois par image, et le `Visuals` anonyme alloué par
> écran et par image est devenu une instance unique réemployée.
>
> La javadoc de `BlueprintHud` promettait « rien n'est calculé ici que la géométrie — pas
> d'allocation par élément ». Elle était démentie par les trois points ci-dessus. Elle
> l'est maintenant tenue.
>
> **Livré au lot suivant : `show_hud` idempotent.** La description ne repart que si le
> client ne l'a pas déjà — même écran, **même version**.
>
> La correction évidente aurait été de se fier au booléen que `SCREENS.showHud` rendait
> déjà. **Elle introduisait un bug**, et le report d'un lot a servi à ne pas le commettre :
> un HUD serait resté figé à sa version d'ouverture, parce que `refreshScreensOf` ne
> parcourt que les écrans **modaux** (`SCREENS.of(uuid)`) et ne rafraîchit aucun HUD après
> un enregistrement.
>
> La version mémorisée vit **dans la table `huds` elle-même**, dont la valeur est passée
> d'un `Identifier` à un `Shown(blueprint, sent)`. Une seconde table en parallèle aurait
> dû être purgée aux cinq endroits où un HUD disparaît — masquage, masquage total, oubli
> du joueur, retrait par blueprint, fermeture d'un blueprint — et aurait fini par diverger
> sur l'un d'eux. Une seule table, un seul cycle de vie.
>
> La comparaison porte sur l'**identité** de l'instance `Screen` : un enregistrement en
> produit une nouvelle, donc une instance inchangée signifie un écran inchangé.
>
> **Vérifié en le cassant** : en revenant à `return previous == null` — la version naïve —
> `unEcranModifieRepartMemeSiLeHudEstDejaAffiche` passe au rouge. Quatre autres tests
> couvrent les chemins de purge (masquer, oublier le joueur, retirer par blueprint), parce
> que c'est là qu'une version mémorisée devient périmée sans bruit.
>
> La surcharge à trois arguments est conservée avec exactement l'ancienne sémantique
> (« ce HUD n'y était pas ») : les tests et gametests existants n'ont pas eu à bouger.

---

## 7. Épic 19 — La persistance, trop généreuse dans sa cadence

La persistance n'est pas malsaine : aucune écriture disque de la sauvegarde monde ne
bloque le tick (le gzip et le fichier partent bien sur `Util.ioPool()` par le mécanisme
vanilla), la capture des exécutions ne vide pas les files et passe l'état **par
référence** sans le cloner, et `Blueprint.copy()` n'a aucun appelant côté serveur. Le
problème n'est pas la méthode, c'est le rythme.

**a. Tout est réencodé à chaque sauvegarde, qu'il ait changé ou non.**
`BlueprintStorage.isDirty()` (`:66-69`) retourne `true` **en dur** ; `refreshFromLive()`
(`:96-99`) réencode alors **intégralement chaque blueprint** via `GraphNbt.encode`, sans
aucun cache par révision. Un graphe inchangé depuis l'ouverture du monde est réencodé à
l'identique toutes les cinq minutes. Puis `writeList()` (`:120-126`) fait `tag.copy()` —
une **copie profonde du NBT qui vient d'être construit**, dont le stockage est seul
propriétaire et qu'il jettera au passage suivant. Le CPU et le pic mémoire de la
sauvegarde sont donc doublés pour rien.

Un cache de `CompoundTag` par blueprint suffit — invalidé sur `(revision, enabled)`, et
**pas sur la révision seule** : `setEnabled` mute l'état sans incrémenter la révision
(`BlueprintManager.java:155-165`). Les octets produits sont identiques, aucun format
persisté ne bouge.

> **Livré (19c).** Cache `Map<Identifier, Encoded>` où `Encoded` porte `(revision, enabled,
> tag)`. Un graphe inchangé n'est plus réencodé ; un graphe supprimé sort du cache.
>
> **Le point sur `enabled` a été vérifié en le cassant.** En clefant sur la révision seule,
> `desactiverEstEcritMemeSansEdition` passe au rouge : un blueprint coupé par un
> administrateur repartirait vivant au redémarrage suivant. C'est exactement le genre de
> régression qu'un cache introduit sans bruit, et la raison pour laquelle ce test existe.
>
> **Compromis assumé sur la copie.** Les tags de blueprints sont désormais partagés avec le
> cache, donc `writeList` les recopie à nouveau — ce que l'épic 19a avait supprimé pour eux.
> Le marché est franchement gagnant : on paie une copie pour économiser un **encodage**
> complet (nœuds, littéraux à travers leurs codecs, liens, variables, écrans, commentaires),
> et cela pour chaque graphe inchangé, toutes les cinq minutes. Les tags d'exécutions
> suspendues, eux, restent reconstruits à chaque passage et ne se copient toujours pas.
>
> **Écarté délibérément : `isDirty()` reste `true`.** Le remplacer par un vrai drapeau
> ferait sauter jusqu'à l'appel de `toTag`, mais un point de mutation oublié perdrait des
> modifications au prochain crash. Le cache rend déjà `toTag` bon marché quand rien n'a
> bougé ; troquer une écriture inutile contre un risque de perte de données ne se décide
> pas dans un épic de performance.

**b. Le reflet sur le disque écrit sur le fil serveur.** `BlueprintManager.save()`
(`:143`) appelle `mirror`, qui exécute `BlueprintFiles.export` (`:36-47`) :
`Files.createDirectories`, puis `ScriptGenerator.generate` (génération BScript complète),
puis `Files.writeString` — **le tout synchrone, sur le thread serveur**, à chaque
enregistrement depuis l'éditeur, `autoExport` valant `true` par défaut. La latence disque
n'est pas bornée : quelques millisecondes sur SSD, bien davantage sur disque mécanique,
antivirus ou stockage réseau. Un joueur qui enregistre fait donc hoqueter tout le monde.

Confier l'écriture au pool d'entrées-sorties respecte **mieux** le contrat que le code
s'est donné — « au mieux, jamais au prix de l'enregistrement » (`:183-186`). Seule
précaution : sérialiser les écritures d'un même identifiant, pour que deux enregistrements
rapprochés n'arrivent pas dans le désordre.

> **Livré (19b).** `BlueprintFiles.exportAsync` : le texte BScript est produit sur le fil
> serveur — il lit le graphe vivant, qui n'appartient qu'à lui — et seules la création du
> dossier et l'écriture partent sur `nonCriticalIoPool()`. Les écritures d'un même
> blueprint se **chaînent** (une file par identifiant), donc deux enregistrements
> rapprochés arrivent dans l'ordre ; deux blueprints différents s'écrivent en parallèle.
> Une écriture en échec n'emporte pas les suivantes : le reflet est au mieux, et un disque
> momentanément plein se libère.
>
> `BlueprintPaths.exports()` est désormais résolu **une fois** au câblage, et non à chaque
> enregistrement — il créait deux niveaux de répertoires au passage.
>
> **`nonCriticalIoPool` et non `ioPool`** : perdre le reflet ne coûte qu'un fichier
> réimportable, alors qu'une sauvegarde de monde qui attendrait derrière lui coûterait le
> travail de tout le monde. Le choix du pool dit ce que vaut la tâche.
>
> **Piège de nommage rencontré**, ajouté à `docs/architecture/tech-stack.md` : la classe
> est `net.minecraft.util.Util` en 1.21.11, et non `net.minecraft.Util` comme dans les
> versions antérieures et dans la quasi-totalité des exemples publiés.
>
> **Écarté délibérément** : rendre `/blueprint export` asynchrone lui aussi. La commande
> attend son chemin pour le montrer à celui qui l'a tapée, et il l'a explicitement demandé —
> une attente voulue n'est pas un à-coup subi.

**c. `GraphNbt.encodeNode` copie deux fois pour n'en garder qu'une.** Lignes 98-99 :
`node.config()` est appelé **deux fois** — une pour le `isEmpty()`, une pour le `put` — et
`Node.config()` retourne `config.copy()` (`Node.java:59-61`), une copie profonde. La
première est intégralement jetée. Une variable locale suffit. Même défaut à
`ScriptGenerator.java:438`.

**d. `ScreenSync` regzippe le même écran pour chaque joueur.** Un `Screen` est immuable et
ne change qu'à un enregistrement : les octets sont identiques d'un joueur à l'autre. Un
cache `byte[]` porté par l'instance, naturellement invalidé puisqu'un enregistrement
produit une nouvelle instance, supprime le travail. C'est le même remède que l'épic 17e.

**Une observation, pas un constat** : `IrNbt` (229 lignes) n'est appelé **nulle part en
production** — seul `CompilerVmTest.java:277` l'utilise. Le « cache d'IR entre démarrages »
qu'annonce sa javadoc (story 3.1, AC3) n'est pas câblé. Ne pas le câbler pour économiser la
recompilation à la reprise : cela créerait un **nouveau format persisté**, donc une
contrainte de compatibilité permanente, pour quelques millisecondes une fois par démarrage.
Soit on assume le code mort, soit on le retire — mais c'est une décision de conception, pas
d'optimisation.

---

## 8. Épic 18 — Le banc, à faire en premier

Le projet a déjà une doctrine de mesure sérieuse, née de dix-huit constructions rouges
(`coding-standards.md` §7.1). Elle classe les formes par ordre de préférence : rapport
entre deux mesures prises au même moment, puis temps processeur du fil, puis temps mural.
Avec deux règles universelles :

- **un banc qu'on n'a jamais vu échouer ne prouve rien** — remettre le défaut, vérifier
  qu'il rougit ;
- **un banc dont on n'a pas mesuré la marge n'est pas fini** — viser un ordre de grandeur,
  pas 0,51 contre 0,50.

Trois bancs à ajouter, dans cette doctrine :

1. **Octets alloués par appel de nœud**, via
   `com.sun.management.ThreadMXBean.getThreadAllocatedBytes`. C'est une **quatrième forme**,
   plus forte que les trois autres là où elle s'applique : le nombre d'octets est
   déterministe, il ne dépend ni de la charge de la machine ni du JIT. Un budget d'octets ne
   rougit pas quand la CI est occupée — donc il n'enseigne jamais à relancer plutôt qu'à
   chercher.
2. **Un second cas dans `CompilerPerfTest`** : 1 000 nœuds avec liens de données denses,
   pour faire apparaître la quadratique que le cas actuel dissimule (§3).
3. **Un banc d'émission sur `entity_damaged`**, pas seulement sur `launchSignal` :
   `EventDispatchPerfTest` ne traverse pas la purge de `BlueprintEventBridge.java:86`, qui
   est précisément le coût de l'épic 15.

Le diff du plafond de chaque banc **est** le rapport de gain. C'est ce qui remplace les
promesses chiffrées que ce plan se refuse à faire avant mesure.

---

## 9. Ordre, et règle d'arrêt

```
18  bancs                        ── bloquant : sans eux, aucun gain n'est démontrable
13  fuel et bornes               ── SÛRETÉ, passe devant tout le reste
14  index des liens              ── un changement, six chemins ; meilleur ratio du dépôt
15a corrections mécaniques       ── double varsOf, double all(), containsKey : sans risque
19a copies gratuites             ── writeList, double node.config() : sans risque, immédiat
19b reflet disque hors du tick   ── à-coup ressenti par tous, corrigé par ioPool
19c cache d'encodage par révision── invalidation sur (revision, enabled), PAS revision seule
15b abonnement sélectif          ── gain élevé, RISQUE LE PLUS ÉLEVÉ : invalidation unique
16  boucle de la VM              ── gain élevé ; la garde anti-fuite conditionne la fusion
17a minimap + isWired            ── l'essentiel du coût par image, motif de cache existant
17b HUD, childrenOf, show_hud    ── gain net, patron déjà présent à côté
17c géométrie pendant le drag    ── conditionné au profilage
16b pins indexés, frames int[]   ── conditionné au banc (touche l'IR persistée)
```

Les quatre premières lignes après les bancs (13, 14, 15a, 19a) ne changent aucun
comportement observable et ne touchent aucun format persisté. C'est par là qu'il faut
commencer.

**La règle qui vaut pour toute la série** : une story qui ne fait pas bouger un plafond de
banc, ou qui oblige à modifier un test de comportement existant, est abandonnée — pas
discutée. Le mod est en v1.0.0. La performance qu'on achèterait en fragilisant la reprise
après redémarrage, la garde anti-fuite ou le déclenchement des graphes est une performance
qu'on paierait deux fois.
