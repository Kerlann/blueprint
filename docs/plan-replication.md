# Plan de réplication — épic 21

**État : plan écrit, aucune story livrée.** Établi par une lecture complète du dépôt (cinq
audits parallèles, 2026-08-10), dont chaque constat portant a été revérifié ligne à ligne.

**Avancement : 21.1 livrée**, le drapeau se pose et se refuse là où l'auteur le voit. Rien
ne circule encore — c'est délibéré : construire le réseau au-dessus d'un drapeau que personne
ne peut mettre aurait été construire à l'envers.

| Sujet | Décision | Nature |
|---|---|---|
| Exécution partagée client/serveur | **refusée** | reconduit P1 / AD2 / FR17 |
| `ExecSide.CLIENT` | **refusée** | l'énumération reste à une valeur |
| Prédiction client, réconciliation, rollback | **refusées** | rien ne les soutient dans le dépôt |
| `@replicated` | **à livrer** | surface déclarative morte depuis son écriture |
| Réplication descendante en lecture seule | **à livrer** | 21.1 → 21.5 |
| Interpolation des barres | **à livrer** | 21.6, et c'est le gain visible |
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

**La sortie : résoudre l'ensemble des variables répliquées une fois par lancement**, dans
la fabrique d'environnement, là où `VarOwner` est déjà construit une seule fois
(`BlueprintMod.java:530`). Le chemin d'écriture devient :

```java
if (!replicated.isEmpty() && replicated.contains(name)) markDirty(scope, owner, name, value);
```

Pour l'immense majorité des graphes l'ensemble est **vide** : une lecture de champ et une
branche. Le coût reste nul pour qui ne réplique pas — *« ce qu'on ne fait pas ne peut pas
être lent »* (`10.7:90`) — et l'objection de 10.7, qui portait sur un coût imposé à **tous
les graphes**, tombe littéralement. L'AC2 de 10.7 (« aucun balayage par tick ») reste vrai :
il n'y a pas de balayage, il y a une marque à l'écriture.

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

`itemstack`, `text` et `blockstate` ont un `streamCodec` **paresseux** qui exige un
`RegistryFriendlyByteBuf`, alors que tous les payloads actuels sont sur `ByteBuf` nu
(`BlueprintPayloads.java:5`). Deux options, à trancher en 21.2 : les exclure du premier
lot, ou faire de ce payload le premier sur `RegistryFriendlyByteBuf`. Le premier lot les
exclut ; la raison est qu'ils ne sont pas non plus persistables par `VarStorage`
(`:297`, journalisé `:197-200`), donc une variable de ce type ne survit déjà pas à un
redémarrage — répliquer avant de persister serait construire à l'envers.

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

---

## 5. Découpage en stories

| Story | Objet | Dépend de |
|---|---|---|
| ~~**21.1**~~ **livrée** | `EditOperation.SetReplicated` + pastille `»` dans `VariablePanel` + deux diagnostics (`REPLICATED_SCOPE_LOCAL`, `REPLICATED_TYPE_NOT_SENDABLE`). La règle vit dans **un seul** endroit, `GraphValidator.checkReplicable`, que l'opération et la validation appellent tous les deux | — |
| **21.2** | Payload `VarValues` (S2C), codec borné, quota `maxReplicatedVariables` dans `NetLimits` | 21.1 |
| **21.3** | Ensemble résolu au lancement (§3a, §3b) + marque à l'écriture dans `VarStore` | 21.2 |
| **21.4** | `VarSessions` : diff par joueur, clé `(scope, owner, name)`, une trame par tick dans `endServerTick` — calqué sur `ScreenSessions`, pas réinventé | 21.3 |
| **21.5** | Cache client en lecture seule + `ScreenBindings.updates(..., Source.VARIABLE)` appelé **côté client** avec ce cache. C'est ici que le même code de rendu sert des deux côtés | 21.4 |
| **21.6** | Interpolation des barres liées à une variable répliquée. Le gain visible : une barre de mana devient aussi fluide qu'une barre de vie | 21.5 |
| **21.7** | Mise à jour de `bscript-spec.md`, `architecture.md:131-134` (l'énumération `VarScope` y est **incomplète** : `PLAYER_SHARED` manque) et `node-reference.md` | 21.6 |

Bornes à poser en 21.2 : le plafond actuel est de **256 variables par graphe reçu du
réseau** (`NetLimits.java:24-27`, appliqué `GraphGuard.java:59-62`). Un plafond distinct
sur les **répliquées** est nécessaire : 256 variables × N joueurs × 20 Hz est un budget que
personne n'a chiffré, et `NetLimits` n'a aujourd'hui aucune borne de latence ni de
fréquence — seulement des tailles et des quotas de débit.

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
| 2026-08-10 | 0.3 | Story 21.1 livrée : le drapeau devient éditable et validé. `@replicated` cesse d'être une surface morte, sans qu'un octet ne circule encore. |
| 2026-08-10 | 0.2 | NFR14 livré (`VarQuota`, `VarBuckets.put/forget/recount`, `/blueprint vars`) : le prérequis de la story 21.4 est levé, et `VarQuota` donne à 21.2 la mesure de poids dont son plafond réseau aura besoin. |
