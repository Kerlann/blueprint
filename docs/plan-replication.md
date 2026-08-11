# Plan de réplication — épic 21

**État : les sept stories sont livrées, puis relues — quatre défauts corrigés (§8).** Une variable
`@replicated` se déclare dans l'éditeur, se refuse si elle ne peut pas voyager, se marque à
l'écriture, part en fin de tick chez les seuls destinataires légitimes, et le client la peint sans
aller-retour — en glissant. `@replicated` n'est plus une surface morte : c'était le point de départ
de ce plan.

Établi par une lecture complète du dépôt (cinq audits parallèles, 2026-08-10), dont chaque constat
portant a été revérifié ligne à ligne.

**Trois choses que ce plan avait mal vues**, et qui valent plus que le plan lui-même : l'**emplacement
de la marque** (au magasin et non dans l'environnement, sans quoi les valeurs par défaut n'étaient
pas répliquées), la **clé du protocole** (le blueprint et non la portée, que le client ne sait pas
interpréter) et, trouvé en relecture seulement, le fait qu'**enregistrer n'annonçait pas** — ce qui
rendait tout l'épic inopérant dans le flux normal d'un auteur.

### Le lissage, appliqué plus largement que prévu

Le plan scopait l'interpolation aux barres liées à une variable répliquée. Elle s'applique à
**toutes** les barres, pour deux raisons. Distinguer aurait demandé de savoir *pourquoi* une valeur
a changé — donc de tracer la source jusqu'au rendu, alors qu'une barre est déjà une valeur
d'exécution hors du modèle. Et le besoin est le même : une barre pilotée par `gui/set_progress`
sautait tout autant.

Cent millisecondes de **temps réel** et non deux ticks : une valeur arrive au mieux 20 fois par
seconde, l'écran se dessine 60 fois, et interpoler sur les ticks aurait donné trois images
identiques puis un saut — le défaut qu'on répare, à une échelle plus fine. Une nouvelle valeur
reprend depuis **là où la barre est** et non depuis sa cible précédente, sans quoi un flux à 20 Hz
— plus rapide que le glissement — ferait reculer la barre à chaque fois.

<details><summary>Avancement détaillé des stories précédentes</summary>

**21.1 à 21.4 — le serveur réplique.** Le drapeau se pose et se refuse là où l'auteur le voit ; le
canal est borné ; les changements se marquent au coût d'une lecture de champ pour qui ne réplique
pas ; et les valeurs partent, une trame par joueur et par tick, avec un instantané à l'arrivée. Il
n'y a plus de point d'entrée inerte.

</details>

### La révision de protocole qu'a imposée 21.5

Le payload de 21.2 portait la **portée** et non le blueprint, avec ce raisonnement : « une valeur
répliquée est identifiée par ce qu'elle est pour le joueur qui la regarde, pas par le graphe qui
l'a écrite ». **C'était faux**, et 21.5 l'a montré en essayant de s'en servir.

Une liaison d'écran ne nomme qu'une **variable** (`ElementBinding.variable()`). C'est la
déclaration du blueprint qui dit sa portée — et cette déclaration n'arrive jamais chez le client :
`ScreenOpen` envoie l'écran, pas les variables. Le client ne pouvait donc pas savoir quelle portée
une liaison visait, et la portée sur le fil était une donnée que le destinataire ne savait pas
interpréter.

La clé est désormais `(blueprint, nom)`, qui désigne exactement une valeur — un nom est unique
dans un blueprint. Conséquence assumée : une variable `WORLD` déclarée répliquée par deux
blueprints part **deux fois**, une par blueprint, parce que les écrans de chacun doivent la
trouver. `ReplicatedNames.declaringBlueprints` fait cette expansion.

C'est le genre d'erreur qu'on ne voit qu'en branchant le consommateur. Elle plaide pour livrer le
producteur et le consommateur plus près l'un de l'autre — ce que 21.2 et 21.3 n'ont pas fait, et
qui a coûté un aller-retour.

| Sujet | Décision | Nature |
|---|---|---|
| Exécution partagée client/serveur | **refusée** | reconduit P1 / AD2 / FR17 |
| `ExecSide.CLIENT` | **refusée** | l'énumération reste à une valeur |
| Prédiction client, réconciliation, rollback | **refusées** | rien ne les soutient dans le dépôt |
| `@replicated` | **livrée** | 21.1 : le drapeau se pose et se refuse |
| Réplication descendante en lecture seule | **livrée** | 21.2 → 21.5 |
| Lissage des barres | **livré** | 21.6, et c'est le gain visible |
| Élargir le catalogue `ClientValue` | **refusée** | la réplication le rend inutile |

---

## 0. Le constat en une page

La question posée était : *« est-ce bien de tout faire côté serveur, ou faut-il un système
de synchronisation de certaines variables ? »*. Les deux moitiés ont des réponses opposées,
et c'est ce qui rend ce plan possible sans toucher à un seul principe.

**L'exécution serveur est le bon choix, et il est déjà tenu de fait.** `BlueprintVm` a zéro
occurrence dans `client/`, `fabric/`, `neoforge/` ; le client ne compile rien (le bouton
« Compiler » de `ToolbarWidget.java:10` déclenche une validation) ; `ExecSide` n'est jamais
consulté — `BlueprintVm.call` lit `type.permission()`, jamais `type.side()`
(`BlueprintVm.java:202`).

**Mais « tout côté serveur » est déjà faux pour l'affichage, et c'est heureux.** Quatre
couches de données ne traversent pas le réseau, et elles sont bien conçues :

| Mécanisme | Où | Coût réseau |
|---|---|---|
| `ClientValue` — 14 valeurs vanilla du joueur local, résolues au tick client | `ClientValue.java:31-58`, `ClientValues.java:38-53`, `BlueprintClient.java:124` | **aucun** |
| État local de l'UI : sélection, curseur, cochage, défilement, dropdown, focus | `BlueprintScreen.java:160-183`, `:986-999` | aucun en aller |
| `ValueDrafts` — retient les valeurs jusqu'à la fin du geste | `ValueDrafts.java:22-30` | 13 paquets → 1 |
| Diff par joueur, une trame par tick | `ScreenSessions.queue:203-218`, `flushScreenUpdates:705-730` | ≤ 1 paquet/joueur/tick |

**Le manque est exactement délimité.** Le catalogue `ClientValue` est fermé par choix
(`ClientValue.java:13-16`). Dès qu'une valeur n'est pas une des 14 vanilla — mana,
endurance, réputation, or, score — elle retombe sur le chemin serveur intégral : le graphe
doit appeler `gui/refresh` (`GuiNodes.java:226`, coût 15) sinon **rien ne se met jamais à
jour** ; au mieux 20 Hz ; et **aucune interpolation nulle part** — recherche de
`partialTick|lerp|interpolat|smooth` dans `ScreenPainter.java` : 0 occurrence, le
remplissage est lu brut (`:340`).

Donc : une barre de vie est fluide et gratuite, une barre de mana saute par paliers de tick
et coûte un paquet. Le mécanisme qui supprime ce coût existe, et ne sert que les 14 cas
vanilla.

---

## 1. La surface morte

C'est le fait le plus important de l'audit, et il tranche la question tout seul.

```java
record Variable(String name, PinType type, LiteralValue defaultValue,
                VarScope scope, boolean replicated)   // Variable.java:13
```

Le drapeau est **modélisé**, **persisté en NBT** (`GraphNbt.java:134` encode, `:257`
décode avec défaut `false`), **dans la grammaire BScript** (`ScriptParser.java:263,274,278`
et `ScriptGenerator.java:163-165`), **préservé par les opérations d'édition**
(`EditOperation.java:264,287,301`), **spécifié** (`bscript-spec.md:188` : « Synchronisée
vers les clients (lecture seule) »), **documenté** (`Variable.java:9`) et **utilisé dans
quatre `.bp` livrés** (`docs/examples/rp.bp:8-13`, `parkour.bp:13`, `vitrine.bp:8`).

Et il ne produit **aucun paquet**. Recherche de `replicated` dans `api/ core/ client/
platform/` hors `build/` : 12 occurrences, **toutes de transport ou de recopie**. Zéro dans
`core/net/`, zéro dans `client/`, zéro test. Le panneau des variables force
`replicated = false` en dur (`VariablePanelState.java:117-118`) ; il n'existe pas
d'`EditOperation.SetReplicated`.

**Aucune story, aucun gate, aucun changelog, aucune ligne de plan ne le mentionne.** Ce
n'est donc pas une provision documentée pour une version future : c'est un trou de
traçabilité, et le motif de panne que ce projet nomme sa hantise — *« le point d'entrée
mort, celui que ce projet a déjà payé avec `signal` »* (`docs/qa/gates/11.4-les-touches.yml:5`),
`event/signal` *« se posait, se câblait, et rien ne le déclenchait »* (`docs/README.md`,
épic 7).

**Il faut trancher.** Le livrer, ou le retirer du modèle, du NBT, de la grammaire et des
quatre exemples. Le laisser en l'état est la seule mauvaise option, parce qu'un auteur qui
lit `rp.bp` et la spec croit aujourd'hui disposer d'une fonctionnalité qui n'existe pas.

Ce plan choisit de le livrer, pour une raison simple : la spec a raison. Le besoin est
réel, la sémantique écrite (« lecture seule ») est exactement la bonne, et la moitié de la
plomberie est déjà là.

---

## 2. Ce qui existe déjà et qu'il ne faut pas refaire

Cinq briques rendent l'épic abordable. En redévelopper une serait la vraie erreur.

1. **La ligne de partage est déjà nommée.** `ElementBinding.Source` vaut `VARIABLE`
   (calculé serveur, envoyé) ou `CLIENT` (jamais envoyé, calculé client) —
   `ScreenBindings.java:48-62`.
2. **Le rendu est déjà le même des deux côtés.** `ScreenBindings.updates(screen, values,
   source)` (`:63-84`) est appelé par le serveur avec `VARIABLE` et par le client avec
   `CLIENT` : *« Le serveur n'appelle jamais cette méthode avec `CLIENT` … Le client,
   symétriquement, ne calcule jamais les liaisons de variables : il ne connaît pas les
   variables, et ne doit pas »* (`:50-59`). Même format, mêmes décimales, mêmes bornes —
   c'est la protection que le projet s'est donnée contre la divergence au pixel, et la
   réplication doit passer par elle plutôt que par un second chemin de rendu.
3. **La résolution par destinataire existe.** `refreshBindings` lit les variables avec le
   **joueur destinataire** comme `VarOwner`, pas le déclencheur
   (`ServerBlueprintNet.java:566-570`, `readVariable:618-635`, avec repli sur le défaut
   déclaré). Les valeurs de portée joueur sont donc déjà correctement per-player.
4. **Le diff et le regroupement existent.** `ScreenSessions` tient `displayed` et
   `pending`, compare, retire même une entrée déjà en file si le client affiche déjà la
   valeur (`:203-218`), et `flushScreenUpdates` envoie une trame par joueur par tick.
5. **`VarOwner` est déjà construit une seule fois par lancement**
   (`BlueprintMod.java:530`), précisément pour respecter « aucune allocation dans `step` »
   (`VarOwner.java:17-22`). C'est le crochet dont §3 a besoin.

---

## 3. La décision

**Un canal de données descendant, en lecture seule, déclaré variable par variable.**

Le client ne compile rien, n'exécute rien, ne calcule rien et ne peut rien écrire : il
reçoit une valeur nommée et l'affiche. C'est le pendant « hors écran » de
`ElementBinding.Source.CLIENT`, à ceci près que le catalogue n'est plus fermé. Rien dans
P1 (`architecture.md:36`), AD2 (`:444`) ni FR17 (`prd.md:65`) n'est touché : ces trois
textes portent sur l'**exécution**, et l'exécution reste entière côté serveur.

### 3a. Le point délicat, et sa sortie

La story 10.7 a **explicitement écarté** l'instrumentation de `VarStore`
(`10.7:75-90`) : *« ça touche `VarStore` — donc le chemin chaud de **toute** exécution, y
compris des blueprints qui n'ont jamais ouvert d'écran »*. Le Change Log montre que c'était
une correction demandée : *« le sondage par tick est abandonné au profit d'un `gui/refresh`
explicite … Il avait raison : le coût existait même quand rien ne changeait »* (`10.7:118`).

Le précédent de gouvernance est clair : 15b a été **retiré** parce qu'il changeait une
garantie observable sous couvert d'optimisation, et la règle du plan d'optimisation est
citée telle quelle — *« une story qui oblige à modifier un test de comportement existant
est abandonnée, pas discutée »* (`plan-optimisation.md:418-448`).

**La sortie : résoudre l'ensemble des variables répliquées une fois**, et le tester avant
tout le reste dans le chemin d'écriture :

```java
if (!replicated.isEmpty() && replicated.contains(name)) markDirty(scope, owner, name, value);
```

Pour l'immense majorité des graphes l'ensemble est **vide** : une lecture de champ et une
branche. Le coût reste nul pour qui ne réplique pas — *« ce qu'on ne fait pas ne peut pas
être lent »* (`10.7:90`) — et l'objection de 10.7, qui portait sur un coût imposé à **tous
les graphes**, tombe littéralement. L'AC2 de 10.7 (« aucun balayage par tick ») reste vrai :
il n'y a pas de balayage, il y a une marque à l'écriture.

> **Livré autrement que décrit ci-dessus, et mieux.** Le plan plaçait l'ensemble dans
> l'`ExecutionEnvironment`, « là où `VarOwner` est déjà construit une seule fois ». Deux
> raisons l'ont déplacé dans `VarStorage`, et la seconde est celle qui compte :
>
> 1. `ExecutionEnvironment` est un record construit en **23 endroits**, dont 22 tests. Y
>    ajouter un composant aurait fait porter la réplication par tous.
> 2. Surtout, l'environnement n'aurait couvert que le chemin de la VM. `VarBuckets.put` est
>    l'entonnoir unique de **toutes** les écritures depuis NFR14, `seedDefaults` compris — et
>    les valeurs par défaut semées au lancement doivent être répliquées, sinon un joueur qui
>    se connecte ne voit pas sa valeur de départ. Le placement du plan aurait manqué ce cas
>    en silence ; un test le fige.
>
> L'ensemble est en outre recalculé **à la mutation du gestionnaire** et non par lancement,
> ce qui est encore moins souvent : quelques fois par heure au lieu de vingt fois par seconde.

### 3b. La subtilité à ne pas manquer

`WORLD` et `PLAYER_SHARED` sont partagées **entre** blueprints (`VarBuckets.java:30-32`).
Le blueprint A peut déclarer `or @world @replicated` pendant que B écrit `or @world` sans
le drapeau — et B ne saurait pas qu'il faut marquer.

L'ensemble résolu au lancement doit donc être l'**union** de :
- les variables répliquées **de ce blueprint** (toutes portées), et
- les noms `WORLD` / `PLAYER_SHARED` répliqués par **n'importe quel** blueprint.

Le second terme se recalcule quand le gestionnaire mute — il diffuse déjà `announceList`
à ces cinq endroits : `BlueprintManager.java:56,65,122,137,216`. Un ensemble immuable
partagé, remplacé et non muté, pour que la lecture au lancement n'ait pas à se synchroniser.

### 3c. Destinataires — c'est une surface d'attaque neuve

| Portée | Destinataires | Note |
|---|---|---|
| `WORLD`, `GRAPH` | tous les spectateurs concernés | pas de propriétaire joueur |
| `PLAYER`, `PLAYER_SHARED` | **le propriétaire seul** | jamais un autre joueur |
| `LOCAL` | aucun | refusé au validateur |

La règle `PLAYER` est la seule qui puisse créer une fuite qui n'existe pas aujourd'hui :
répliquer par mégarde la réputation de tous les joueurs à tous les clients serait une
divulgation, et rien dans le modèle actuel ne la produirait. `VarStore.owns`
(`VarStore.java:41-49`) est déjà la règle unique de possession ; le filtre de destinataire
doit s'y adosser et non la réécrire.

### 3d. Types transportables

Seuls ceux qui ont un `streamCodec` : `PinType.hasStreamCodec()` (`BasePinType.java:86-88`).
`player`, `entity` et les génériques sont **refusés au validateur**, pas silencieusement
sautés — une variable `@replicated entity` est une erreur d'auteur, et l'éditeur doit le
dire à l'édition.

**Tranché en 21.2, et autrement que prévu.** La question posée était : exclure
`itemstack`/`text`/`blockstate` du premier lot, ou faire de ce payload le premier sur
`RegistryFriendlyByteBuf` ? Aucune des deux. Le transport réutilise le **format étiqueté de
la sauvegarde du monde**, extrait de `VarStorage` dans `core/vm/VarValueNbt`, et le refus se
juge par `VarValueNbt.carries(PinType)` et non par `PinType.hasStreamCodec()`.

Conséquence, qui est la vraie raison de ce choix : **ce qui se réplique est exactement ce qui
survit à un redémarrage.** Une variable ne peut pas arriver chez un client sans pouvoir être
sauvegardée. S'appuyer sur `hasStreamCodec` aurait accepté ces trois types — ils *ont* un
codec réseau — pour les laisser tomber en silence à l'encodage, soit précisément la panne que
cet épic répare. Et une seule liste de types, dans un seul fichier, sert les deux usages :
`VarBuckets` existe pour avoir fermé la même question sur le rangement.

---

## 4. Ce qui est écarté, et pourquoi

| Écarté | Raison |
|---|---|
| `ExecSide.CLIENT`, exécution partagée | un graphe est une donnée non fiable (AD2). Exécuter chez le client, c'est le laisser décider de son or |
| Prédiction, réconciliation, rollback | aucune infrastructure : `BlueprintScreen.java:990-999` écrit optimiste et **ne revient jamais** en arrière si le serveur refuse. Et `rapport-de-fin.md:128` : *« le multijoueur n'est éprouvé qu'à un seul client »* |
| Un chemin C2S d'écriture de variable | FR52 (`prd.md:123`) : *« le serveur ne fait jamais confiance à ce qu'un client déclare »*. La réplication est **strictement descendante** |
| Sondage par tick des variables répliquées | c'est l'option 1 de 10.7, écartée pour la bonne raison |
| Élargir `ClientValue` en langage d'expressions | refusé à dessein (`ClientValue.java:13-16` : *« aurait donné un langage de plus à apprendre »*), et la réplication le rend inutile |
| Une seconde table de signatures par spectateur | `10.7:135-147` : c'était la première version de `ScreenBindings`, retirée — *« Deux tables à garder d'accord, et la première divergence se serait vue comme un écran figé sur une vieille valeur, sans que rien ne l'explique »*. Le diff de `ScreenSessions` est la seule mémoire, et le reste ainsi |
| **Un `VarSessions` par joueur** (le plan le prévoyait pourtant) | Même raison, et c'est 21.4 qui l'a compris : **le carnet des marques EST le diff**. `VarStorage` ne marque que les écritures qui changent réellement la valeur — comparaison exacte, à la source — le carnet dédoublonne, et il se vide à chaque tick. Une table par joueur de ce que son client affiche aurait été exactement la structure que 10.7 a rejetée, reproduite en connaissance de cause. Le coût de son absence est **un cas nommé** et non une table à tenir : un joueur qui arrive reçoit l'état complet une fois (`VarReplication.greet`), parce que le carnet répond à « qu'est-ce qui a changé » et jamais à « qu'est-ce qui existe » |

---

## 5. Découpage en stories

| Story | Objet | Dépend de |
|---|---|---|
| ~~**21.1**~~ **livrée** | `EditOperation.SetReplicated` + pastille `»` dans `VariablePanel` + deux diagnostics (`REPLICATED_SCOPE_LOCAL`, `REPLICATED_TYPE_NOT_SENDABLE`). La règle vit dans **un seul** endroit, `GraphValidator.checkReplicable`, que l'opération et la validation appellent tous les deux | — |
| ~~**21.2**~~ **livrée** | Payload `VarValues` (S2C, portée + nom + tag étiqueté), codec borné à 32 valeurs et 8 Kio par valeur, `NetLimits.maxReplicatedVariables = 32` appliqué par `GraphGuard`. Format extrait dans `VarValueNbt` : le fil et le disque portent le même. **Aucun émetteur** — c'est 21.4 | 21.1 |
| ~~**21.3**~~ **livrée** | `ReplicatedNames` (résolu à la mutation du gestionnaire, pas au lancement — voir ci-dessous) + `VarDirty` + marque dans `VarStorage.set`. Coût nul quand rien n'est répliqué : une lecture de champ | 21.2 |
| ~~**21.4**~~ **livrée** | `VarReplication` : envoi en fin de tick, instantané à l'arrivée, canal enregistré. **Pas** de `VarSessions` — voir ci-dessous : le carnet des marques *est* le diff | 21.3 |
| ~~**21.5**~~ **livrée** | `ReplicatedVars` (cache client) + `ScreenBindings.updates(..., Source.VARIABLE)` appelé **côté client** avec lui, pour les HUD et les modaux. A imposé une **révision du protocole** : la clé passe de la portée au blueprint | 21.4 |
| ~~**21.6**~~ **livrée** | Lissage des barres sur **100 ms de temps réel**, HUD et modaux. Appliqué à toutes les barres et non aux seules liées à une variable — voir ci-dessous | 21.5 |
| ~~**21.7**~~ **livrée** | `bscript-spec.md` (section `@replicated`), `architecture.md` (P1 nuancé, paquet, règles), `getting-started.md` (à quoi ça sert, quoi répliquer) | 21.6 |

**Bornes posées en 21.2.** Le plafond général reste de 256 variables par graphe reçu ; les
**répliquées** ont le leur, **32**, appliqué par `GraphGuard`. Un ordre de grandeur d'écart,
et non un ajustement : une variable ordinaire coûte de la mémoire serveur une fois, une
répliquée coûte un envoi par joueur qui la regarde, à chaque changement — 256 × N joueurs ×
20 Hz était un budget que personne n'avait chiffré. Trente-deux couvrent tous les usages
qu'on sait nommer (une barre, un solde, un compteur, un état) et laissent le pire cas
explicable.

Le plafond de la **trame** est le même nombre, exprès : un tick qui change toutes les valeurs
répliquées d'un graphe tient dans un seul envoi. Les laisser diverger aurait demandé un
découpage en trames, c'est-à-dire du code pour un cas que le garde réseau refuse déjà. Une
valeur est bornée à part, à 8 Kio décompressés, et c'est ce plafond-là qui protège le
décodeur — le plafond de 64 Ko par joueur (NFR14) borne le total, pas une valeur.

Reste non chiffré, et il faudra le faire en 21.4 : **aucune cible de latence** n'existe dans
le PRD. `NetLimits` ne connaît que des tailles et des débits, jamais un délai.

---

## 6. Prérequis manquants, hérités d'autres plans

Trois, tous déjà consignés ailleurs :

1. **Signal d'invalidation sur `Blueprint`** — *« les éditions n'incrémentent qu'une
   révision que personne n'observe »* (`plan-optimisation.md:445-447`). §3b en a besoin
   pour recalculer l'ensemble des noms `WORLD`/`PLAYER_SHARED` répliqués.
2. **Fragmentation de protocole sur NeoForge** — `plan-multiloader.md` §E :
   *« NeoForge n'a pas d'équivalent du `registerLarge` de Fabric … Fragmenter nous-mêmes
   est un travail de protocole, pas de plateforme, et il doit être fait des deux côtés à
   l'identique sous peine de désaccord »*. Une trame de valeurs répliquées reste petite,
   donc ce n'est **pas bloquant** — mais le noter évite de croire la parité acquise.
3. ~~**NFR14 n'est pas implémenté**~~ — **fait** (voir §7). Le quota *« variables `PLAYER`
   ≤ 64 Ko par joueur, supprimables »* (`prd.md:140`) n'existait nulle part et
   `architecture.md:369` situait à tort ces variables dans les données persistantes du
   joueur. Répliquer des variables dont la taille n'est mesurée par personne aurait été
   construire à l'envers, donc c'était le prérequis de 21.4 : il est levé. `VarQuota` donne
   au passage à 21.2 la mesure dont son propre plafond réseau a besoin — le poids d'une
   valeur, estimé sans l'encoder.

---

## 7. Ce qui a été corrigé en chemin

L'audit a trouvé cinq défauts sans rapport avec la réplication. Trois touchaient le même
chemin — la trame groupée des modifications d'écran — et sont corrigés :

| Défaut | Où | Correction |
|---|---|---|
| `Kind` décodé par `Kind.values()[i]` **sans borne** | `BlueprintPayloads.java:520-522` | codec écrit à la main ; une entrée illisible est lue jusqu'au bout puis **jetée**, la trame est gardée. La liste des natures a déjà grandi deux fois (5 en 10.4, 12 en 10.13) : un client plus ancien qu'un serveur est un cas attendu |
| Texte non borné avant l'encodage | `ScreenUpdate` | `MAX_TEXT` vit désormais dans le modèle et le constructeur le fait respecter. `GraphGuard` plafonne les textes **du graphe** à 4 096, mais `string/concat` en fabrique de bien plus longs à l'exécution |
| `LINES` joint sans plafond ; la file peut dépasser `MAX_UPDATES` | `ScreenUpdate.lines`, `flushScreenUpdates` | troncature **par lignes entières** (une ligne coupée se lit comme une donnée) ; la trame se **découpe** au lieu de lever à l'encodage — ce qui faisait perdre au joueur **toutes** ses modifications du tick |
| `CLICKS` et `OPENS` jamais vidés à la déconnexion | `ServerBlueprintNet.forget` | `forget` parcourt maintenant une liste unique, `quotaBuckets()`, et le test la compte. Deux endroits à tenir d'accord étaient la cause |
| Faute qui recommande `var/get_for` / `var/set_for`, **jamais enregistrés** | `BlueprintVm.java:142-153`, `VarStore.java:66` | le message nomme les deux sorties réelles : brancher sur un événement qui porte un joueur, ou changer la portée |

Puis **NFR14**, qui était le prérequis de la story 21.4 (§6.3) et n'était pas un défaut mais
une exigence jamais écrite :

| Manquait | Livré |
|---|---|
| Le plafond de 64 Ko par joueur | `VarQuota` : poids **estimé** et non mesuré par encodage — encoder à chaque écriture aurait mis un `NbtIo` dans le chemin de la VM. L'estimation est volontairement **majorante** : se tromper vers le bas laisserait passer ce que le plafond existe pour interdire, et c'est la seule des deux erreurs qui ne se voit pas |
| Le total par joueur | tenu **au fil des écritures** par `VarBuckets.put`, recalé par `recount()` après un chargement. Reparcourir les variables d'un joueur à chaque écriture aurait transformé une borne en ralentissement |
| Les deux portées joueur | `PLAYER` **et** `PLAYER_SHARED` partagent le budget. NFR14 ne nomme que la première, née avant la seconde ; les compter séparément donnerait un plafond contournable en changeant un mot-clé, et une suppression qui laisse la moitié des données |
| Le comportement au dépassement | une **faute nommée**, pas une perte silencieuse. Un graphe qui croit avoir enregistré la progression du joueur est une panne que le joueur découvre à sa reconnexion, sans que rien ne relie les deux |
| Le cas du joueur déjà au-delà | seule une écriture qui **fait grossir** est refusée. Sans cela, un joueur au-delà du plafond — monde écrit avant que la borne n'existe — ne pourrait plus rien écrire, pas même pour se réduire |
| La suppression | `/blueprint vars purge <joueur\|uuid>`, réservée aux administrateurs, journalisée. Par nom ou UUID et non par sélecteur d'entité : celui qui demande l'effacement de ses données a en général quitté le serveur |
| La récursion | `VarQuota` borne sa profondeur. Ce n'est pas théorique : `list/add` peut ajouter une liste à elle-même, et une mesure récursive finirait en `StackOverflowError` dans le chemin d'écriture — l'endroit exact que NFR4 interdit d'atteindre |

Reste ouvert, hors périmètre de cet épic : l'absence de vérification
mécanique de la règle *« aucune exécution de graphe côté client »* — `coding-standards.md`
§1.3 n'a pas d'équivalent de `checkApiIsolation`, et rien n'empêche `client` d'appeler
`core.vm`. Une tâche `checkClientIsolation` serait le pendant naturel, et elle vaudrait
plus cher que ce plan une fois la réplication livrée : c'est à ce moment que la tentation
d'« un petit calcul côté client » apparaît.

---

## Change Log

| Date | Version | Description |
|---|---|---|
| 2026-08-10 | 0.1 | Plan initial. Cinq audits parallèles du dépôt ; `@replicated` identifié comme surface déclarative morte ; les trois défauts de la trame d'écran corrigés au passage. |
| 2026-08-10 | 1.0 | **Épic terminé.** 21.6 (lissage des barres, 100 ms de temps réel, appliqué à toutes les barres et non aux seules répliquées) et 21.7 (spec BScript, architecture, guide joueur). |
| 2026-08-10 | 0.7 | Story 21.5 livrée : `ReplicatedVars`, liaisons de variable résolues côté client. **Révision du protocole** : la clé du fil passe de la portée au blueprint, la portée étant inutilisable par le client. |
| 2026-08-10 | 0.6 | Story 21.4 livrée : `VarReplication`, envoi en fin de tick, instantané à l'arrivée, canal enregistré. `VarSessions` **abandonné** — le carnet des marques est le diff, et une seconde table aurait reproduit ce que 10.7 a rejeté. |
| 2026-08-10 | 0.5 | Story 21.3 livrée : `ReplicatedNames` + `VarDirty` + marque dans `VarStorage.set`. L'ensemble vit au magasin et non dans l'environnement — §3a corrigé en conséquence, avec la raison. |
| 2026-08-10 | 0.4 | Story 21.2 livrée : payload `VarValues`, bornes, quota. La question du buffer est tranchée autrement que proposé — le fil réutilise le format étiqueté du disque, si bien que ce qui se réplique est exactement ce qui se persiste. |
| 2026-08-10 | 0.3 | Story 21.1 livrée : le drapeau devient éditable et validé. `@replicated` cesse d'être une surface morte, sans qu'un octet ne circule encore. |
| 2026-08-10 | 0.2 | NFR14 livré (`VarQuota`, `VarBuckets.put/forget/recount`, `/blueprint vars`) : le prérequis de la story 21.4 est levé, et `VarQuota` donne à 21.2 la mesure de poids dont son plafond réseau aura besoin. |

---

## 8. Ce que la relecture a trouvé

L'épic livré, une relecture indépendante a passé les 5 571 lignes ajoutées. Elle a rendu
quatre défauts, tous **réels** après vérification ligne à ligne. Ils partagent une forme : chacun
demande une **combinaison** que mes tests n'avaient pas assemblée.

### 8.1 `save` n'annonçait pas — l'épic ne marchait pas (grave)

`BlueprintManager.save` était le **seul** chemin de mutation qui n'appelait pas `announceList`,
et `announceList` est le seul appelant de `refreshReplicatedNames`.

Le flux normal d'un auteur — ouvrir l'éditeur, cliquer `»`, `Ctrl+S` — laissait donc les
déclarations à leur valeur précédente. Sur un monde fraîchement lancé, l'ensemble restait vide,
`VarStorage.set` prenait son chemin rapide, et **rien ne se répliquait**. Le drapeau était
persisté, visible dans le graphe, et sans effet — jusqu'à ce qu'une création ou une activation
sans rapport passe par là. Symétriquement, *retirer* le drapeau continuait de répliquer.

C'est exactement le motif de panne que cet épic existe pour réparer, reproduit dans l'épic qui
le répare. Et le commentaire que j'avais écrit au-dessus d'`announceList` affirmait que
l'enregistrement en faisait partie : une documentation qui décrit ce qu'on croyait avoir fait.

Le gametest existant n'exerçait qu'`adopt`, qui annonce. La combinaison non couverte était
« drapeau posé **après** l'adoption, par un enregistrement ». Un second gametest la tient, et il
a été vu rougir.

**Effet de bord bénéfique** : les commandes déclarées souffraient du même manque. Ajouter un
`event/command` et enregistrer ne posait pas la racine avant qu'autre chose ne l'annonce.

### 8.2 `WORLD` et `PLAYER_SHARED` se confondaient

`ReplicatedNames` rangeait les deux portées partagées dans **une** table indexée par le seul nom.
Or ce sont deux casiers distincts : le même nom dans les deux est **deux valeurs**.

Un graphe déclarant `solde @player_shared @replicated` et un autre `solde @world @replicated` se
voyaient donc mutuellement envoyer la valeur de l'autre — le solde du monde écrasant chez le
client celui du joueur, sous la même clé. Une table par portée, désormais.

### 8.3 La purge laissait la valeur à l'écran

`forget` effaçait les casiers sans rien marquer. Le client gardait donc sa dernière valeur
**pour toujours** : elle ne serait jamais réécrite — elle n'existe plus — donc rien ne viendrait
la corriger. NFR14 (« les données d'un joueur sont supprimables ») n'était tenu qu'à moitié :
effacé au serveur, toujours affiché. La purge marque maintenant ce qui était répliqué, et la
valeur vide qui part dit « effacée » au client.

### 8.4 L'encodeur récursait sans borne, dans le tick

`VarValueNbt.encode` n'avait **aucune** borne de profondeur, et `list/add` permet d'imbriquer
d'un cran par appel. Sur le disque, cette récursion ne s'exécutait qu'à la sauvegarde du monde ;
depuis 21.4 elle tourne **dans `endServerTick`**, où un `StackOverflowError` emporte le tick et
la sauvegarde — ce que NFR4 interdit d'atteindre. `VarQuota` bornait déjà sa propre récursion
pour cette raison exacte ; l'encodeur n'avait pas reçu le même soin en sortant de `VarStorage`.

Second volet : `VarQuota` bornait à **huit** niveaux quand l'encodeur n'en avait aucun. Tout ce
qui était plus profond était facturé un forfait de 64 octets alors qu'il se persistait en
entier — le plafond de 64 Ko se contournait donc en imbriquant. Les deux bornes valent
maintenant 16, et coïncident par construction : au-delà, l'encodeur refuse.

### Ce que j'en retire

Les quatre défauts sont invisibles à la lecture d'un fichier et évidents à la lecture de deux.
Écrire les tests en même temps que le code m'a fait vérifier **ce que j'avais pensé**, jamais les
croisements que je n'avais pas imaginés. Une relecture indépendante sur un épic de cette taille
n'est pas une formalité : elle a trouvé une panne totale que la construction verte, 90 tests
neufs et un gametest ne voyaient pas.
