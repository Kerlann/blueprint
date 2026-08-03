# PRD — Blueprint

**Agent BMAD :** Product Manager (John)
**Statut :** Approuvé pour architecture
**Version :** 1.0 — 2026-08-02
**Entrée :** `docs/brief.md`

---

## 1. Objectifs et contexte

### Objectifs

- Permettre à un joueur sans compétence en programmation de créer de la logique
  Minecraft en câblant des nœuds, entièrement en jeu.
- Garantir qu'un graphe créé par un joueur ne peut ni figer ni compromettre le serveur.
- Fournir une représentation texte (**BScript**) équivalente au graphe, dans les deux sens.
- Faire de Blueprint un **socle partagé** : tout mod tiers doit pouvoir exposer ses
  actions et ses événements en quelques lignes, sans dépendance dure.
- Rester compatible Minecraft 1.21.11 / Fabric, avec une architecture qui n'interdit
  pas un futur portage.

### Contexte

Le dépôt contient aujourd'hui un squelette Fabric fonctionnel (`fr.blueprint.BlueprintMod`,
`fr.blueprint.client.BlueprintClient`, `fabric.mod.json`, Loom 1.13.6, Java 21,
mappings Mojang). Tout le produit décrit ici est à construire par-dessus.

### Journal des versions

| Date | Version | Description | Auteur |
|---|---|---|---|
| 2026-08-02 | 1.0 | PRD initial, 9 épics | PM (John) |
| 2026-08-02 | 1.1 | Correct-course DOC-001 : `ResourceLocation` → `Identifier` (renommage Mojang 1.21.11 découvert en story 1.2) | SM (Bob) |

---

## 2. Exigences

### 2.1 Fonctionnelles

**Modèle de graphe**

- **FR1** — Un blueprint est identifié par un `Identifier` et contient des nœuds, des liens, des variables, des commentaires et des métadonnées (auteur, version, description).
- **FR2** — Un nœud possède des pins d'entrée et de sortie, chacun de nature `EXEC` ou `DATA`, avec un type de donnée pour les pins `DATA`.
- **FR3** — Le système applique les règles de câblage : un pin `EXEC` de sortie a au plus un lien, un pin `DATA` d'entrée a au plus un lien (sinon sa valeur littérale par défaut s'applique), les pins `EXEC` d'entrée et `DATA` de sortie acceptent N liens.
- **FR4** — Un lien n'est acceptable que si le type source est assignable au type cible, directement ou via une conversion implicite déclarée (ex. `int` → `double`).
- **FR5** — Les pins de type joker (`any`) se résolvent au moment du câblage et propagent leur type résolu aux autres pins jokers du même nœud.
- **FR6** — Un pin `DATA` d'entrée non connecté expose une valeur littérale éditable dans l'éditeur.
- **FR7** — Les cycles dans le flux `DATA` sont interdits et signalés ; les cycles dans le flux `EXEC` sont autorisés (boucles).

**Variables**

- **FR8** — L'utilisateur déclare des variables typées avec un nom, un type, une valeur par défaut et une portée parmi `LOCAL` (durée d'une exécution), `GRAPH` (persistante par blueprint), `WORLD` (globale au monde), `PLAYER` (persistante par joueur).
- **FR9** — Chaque variable génère automatiquement un nœud `Get` (pur) et un nœud `Set` (à exec).
- **FR10** — Renommer ou retyper une variable met à jour tous ses nœuds ; un retypage incompatible invalide les liens concernés avec un message explicite plutôt qu'une suppression silencieuse.

**Compilation et exécution**

- **FR11** — Le graphe se compile en une représentation intermédiaire linéaire (IR) exécutée par une VM à registres.
- **FR12** — La compilation produit une liste de diagnostics typés (erreur/avertissement) rattachés à un nœud ou un lien précis.
- **FR13** — Les nœuds marqués « purs » (sans pin `EXEC`) sont évalués à la demande et mémoïsés pour la durée d'une étape d'exécution.
- **FR14** — L'exécution est limitée par un budget d'instructions par tick, configurable ; le dépassement suspend le blueprint et journalise l'incident.
- **FR15** — Un nœud d'attente (`wait <durée>`) suspend l'exécution et la reprend plus tard ; l'état suspendu survit à une sauvegarde/rechargement du monde.
- **FR16** — Une profondeur de récursion maximale et un nombre maximal de nœuds par blueprint sont appliqués.
- **FR17** — Un blueprint s'exécute exclusivement côté serveur (y compris serveur intégré en solo).

**Déclencheurs**

- **FR18** — Un blueprint expose des nœuds d'événement servant de points d'entrée, avec leurs sorties de données contextuelles.
- **FR19** — Le MVP fournit au minimum : `server_tick`, `player_join`, `player_quit`, `player_use_block`, `player_use_item`, `player_break_block`, `entity_death`, `player_chat`, `command` (avec arguments déclarés), `signal` (signal nommé émis par un autre blueprint).
- **FR20** — Un blueprint peut être activé ou désactivé sans être supprimé.

**Bibliothèque de nœuds standard**

- **FR21** — Le MVP fournit au minimum 80 nœuds couvrant : flux (`branch`, `while`, `for`, `sequence`, `gate`, `switch`), maths, comparaison, logique booléenne, chaînes, listes, monde (lire/poser un bloc, spawner, explosion, son, particules), entité, item, joueur, texte/chat, temps, aléatoire déterministe, débogage.
- **FR22** — Un nœud `execute_command` existe mais est classé permission `ADMIN`.

**Script (BScript)**

- **FR23** — Tout graphe valide se génère en un fichier BScript lisible et déterministe (même graphe → mêmes octets).
- **FR24** — Tout BScript valide se parse en un graphe équivalent, avec mise en page automatique si les positions ne sont pas présentes.
- **FR25** — Les positions des nœuds sont conservées dans le BScript sous forme de métadonnées structurées, de sorte que le round-trip graphe → script → graphe préserve la mise en page.
- **FR26** — L'éditeur propose une vue texte synchronisée (lecture seule au MVP, édition en v1.1) et des actions Exporter / Importer.
- **FR27** — Le copier-coller de nœuds passe par le presse-papier système au format BScript.

**Éditeur**

- **FR28** — L'éditeur s'ouvre via la commande `/blueprint edit <id>` et via un objet dédié, et présente un canevas pan/zoom avec grille.
- **FR29** — Tirer un lien depuis un pin puis relâcher dans le vide ouvre une palette de recherche filtrée par le type du pin, et le nœud choisi se connecte automatiquement.
- **FR30** — L'éditeur affiche les diagnostics de compilation sur les nœuds concernés et dans un panneau dédié.
- **FR31** — Un mode débogage surligne le flux d'exécution en direct et affiche la valeur courante des pins.
- **FR32** — Annuler/rétablir couvre toutes les opérations d'édition (au moins 50 niveaux).
- **FR33** — L'éditeur permet commentaires, boîtes de regroupement, alignement et mise en page automatique.

**Persistance et réseau**

- **FR34** — Les blueprints sont stockés dans la sauvegarde du monde en NBT compressé et rechargés au démarrage.
- **FR35** — Les descripteurs de nœuds du serveur (y compris ceux ajoutés par des mods tiers) sont synchronisés vers le client au login, avec comparaison de hash pour éviter un renvoi inutile.
- **FR36** — Les modifications d'un blueprint transitent en patchs incrémentaux, pas en renvoi complet du graphe.
- **FR37** — Un blueprint peut être exporté vers un fichier et importé depuis un fichier, avec validation et re-application des permissions à l'import.

**Extension par des mods tiers**

- **FR38** — Un mod tiers enregistre nœuds, types de pins et événements via un entrypoint Fabric `blueprint` implémentant une interface publique stable.
- **FR39** — Un nœud peut être déclaré par un builder Java, par une annotation `@BlueprintNode` sur une méthode statique, ou par un fichier JSON de datapack.
- **FR40** — Un nœud absent du registre au chargement d'un graphe devient un **nœud fantôme** qui conserve son identifiant, ses liens et sa configuration ; le nœud redevient fonctionnel si le mod est réinstallé.
- **FR41** — Un blueprint contenant un nœud fantôme ne s'exécute pas et signale précisément le mod manquant.
- **FR42** — Chaque nœud déclare un niveau de permission (`SAFE`, `GAMEPLAY`, `WORLD`, `ADMIN`) et un côté d'exécution ; le serveur refuse d'exécuter un nœud au-delà du plafond du blueprint.
- **FR43** — Les traductions des nœuds tiers utilisent les clés de langue du mod fournisseur.
- **FR44** — Les intégrations avec des mods spécifiques sont chargées conditionnellement et n'empêchent jamais le démarrage si le mod est absent.

#### Interfaces graphiques (épic 10)

- **FR45** — Un blueprint peut posséder un ou plusieurs **écrans**, **modaux ou permanents (HUD)**, composés d'éléments imbricables (panneau, étiquette, bouton, image, barre, liste défilante, champ de saisie, emplacement d'objet, case, curseur, aperçu d'entité) positionnés librement.
- **FR46** — Les écrans se conçoivent **à la souris** : poser, déplacer, redimensionner, sélectionner, aligner — sans écrire une ligne.
- **FR46b** — Un écran s'**adapte** : ancres, tailles en pourcentage et bornes lui permettent de rester utilisable de 320×180 à 960×540 unités d'interface, c'est-à-dire à toutes les résolutions et tous les réglages de *GUI scale* courants.
- **FR47** — Chaque élément porte un **nom** unique dans son écran ; c'est par ce nom que le graphe le désigne.
- **FR48** — Un nœud ouvre un écran pour un joueur donné, un autre le ferme ; un événement se déclenche quand un élément est **cliqué**, quand l'écran s'ouvre et quand il se ferme. `Échap` ferme toujours : aucun écran ne peut refuser de se fermer.
- **FR49** — Le graphe peut **modifier** un écran ouvert : texte d'une étiquette, image, visibilité, activation d'un bouton, valeur d'une barre. Les modifications d'un même tick partent groupées, seules celles qui ont changé sont transmises, et une mise à jour destinée à un écran refermé est ignorée.
- **FR49b** — Un élément peut être **lié** à une variable du blueprint, avec un format d'affichage. Un seul nœud `gui/refresh` met alors à jour tout l'écran ; rien ne circule tant qu'il n'est pas appelé.
- **FR50** — L'apparence est **personnalisable** : couleurs, bordures, neuf-tranches, et images fournies par des **packs** — des dossiers de `blueprint/scripts/` qu'un joueur dépose chez lui et peut donner à un autre. Une texture absente affiche un remplaçant qui **nomme le pack manquant** et ne fait jamais planter le client.
- **FR51** — Les écrans traversent la sauvegarde, la synchronisation réseau et BScript comme le reste du blueprint : même enregistrement, même permission, même verrou.
- **FR52** — Le serveur ne fait **jamais** confiance à ce qu'un client déclare avoir cliqué : l'écran ouvert, l'existence de l'élément et la cadence sont vérifiés côté serveur.

### 2.2 Non fonctionnelles

- **NFR1** — L'éditeur maintient ≥ 60 fps avec 500 nœuds et 800 liens visibles sur un GPU intégré récent.
- **NFR2** — La compilation d'un graphe de 1 000 nœuds prend < 50 ms.
- **NFR3** — Un blueprint actif coûte en moyenne ≤ 0,5 ms par tick serveur ; le profileur intégré expose le coût par blueprint et par nœud.
- **NFR4** — Aucun graphe, quelle que soit sa forme, ne peut provoquer une boucle infinie bloquante, un `StackOverflowError` ou un `OutOfMemoryError` (budget de fuel, profondeur, quota d'allocation de listes).
- **NFR5** — Le blueprint sérialisé pèse < 1 Mo pour 1 000 nœuds ; la synchronisation réseau est fragmentée et compressée.
- **NFR6** — Le démarrage du serveur n'est pas rallongé de plus de 200 ms par le mod, hors blueprints utilisateur.
- **NFR7** — L'API publique (`fr.blueprint.api`) suit le semver ; une rupture n'intervient qu'en version majeure et toute suppression est précédée d'un cycle de dépréciation.
- **NFR8** — Le mod fonctionne sans aucun mod tiers installé et ne plante pas si un mod attendu disparaît.
- **NFR9** — Solo et serveur dédié produisent des comportements identiques ; tout test d'intégration passe dans les deux modes.
- **NFR10** — Toute chaîne visible par l'utilisateur est traduisible ; `en_us` et `fr_fr` sont fournis et complets.
- **NFR11** — La palette de couleurs des types de pins reste lisible en deutéranopie et protanopie ; les pins portent aussi un code de forme.
- **NFR12** — Le code compile sans avertissement en Java 21, sources UTF-8, et respecte `docs/architecture/coding-standards.md`.
- **NFR13** — Couverture de tests : ≥ 80 % sur `core` (modèle, compilateur, VM, BScript) ; l'éditeur est couvert par des tests de rendu headless sur les composants pures.
- **NFR14** — Les données d'un joueur (variables `PLAYER`) sont supprimables et n'excèdent pas 64 Ko par joueur.
- **NFR15** — Toute exécution de nœud de niveau `ADMIN` est journalisée avec blueprint, nœud, acteur et horodatage.

---

## 3. Épics

| # | Épic | Objectif | Dépend de |
|---|---|---|---|
| 1 | Fondations et modèle de graphe | Socle du mod, modèle typé, sérialisation, commande de base | — |
| 2 | Registre de nœuds et API d'extension | Surface publique stable pour nœuds/types/événements | 1 |
| 3 | Compilateur et VM | Graphe → IR → exécution sûre, budget, suspension | 1, 2 |
| 4 | BScript | Génération et parsing bidirectionnels | 1, 3 |
| 5 | Éditeur visuel | Canevas, palette, liens, panneaux, undo/redo | 1, 2 |
| 6 | Persistance et réseau | Sauvegarde monde, synchro registre, patchs | 1, 2, 5 |
| 7 | Événements et bibliothèque standard | Déclencheurs monde + 80 nœuds | 2, 3 |
| 8 | Intégration mods tiers | Annotation, JSON datapack, compat, nœuds fantômes | 2 |
| 9 | Débogage, perf et finition | Débogueur live, profileur, permissions, i18n, docs | 3, 5, 7 |
| 10 | Interfaces graphiques | Concepteur d'écrans, rendu en jeu, boutons câblés aux blueprints | 5, 6, 7 |

---

### Épic 1 — Fondations et modèle de graphe

*Objectif : disposer d'un modèle de graphe typé, validé, sérialisable, et d'une commande minimale prouvant le chargement en jeu.*

**Story 1.1 — Structure multi-module du projet**
En tant que développeur, je veux que le projet soit découpé en modules Gradle (`api`, `core`, `client`, `compat`) afin que l'API publique ne dépende pas de l'implémentation.
- AC1 : `settings.gradle.kts` inclut les sous-projets et le build produit un JAR unique embarquant tous les modules.
- AC2 : le module `api` ne référence aucune classe de `core` ; une règle de build échoue si c'est le cas.
- AC3 : `./gradlew build` et `./gradlew runClient` fonctionnent après le refactor.
- AC4 : `fabric.mod.json` déclare un entrypoint `blueprint` en plus de `main` et `client`.

**Story 1.2 — Types de pins et système de types**
En tant que développeur, je veux un registre de `PinType` avec règles d'assignabilité afin de valider les liens.
- AC1 : types de base fournis : `exec`, `bool`, `int`, `long`, `double`, `string`, `vec3`, `blockpos`, `direction`, `itemstack`, `entity`, `player`, `blockstate`, `resourcelocation`, `text`, `any`.
- AC2 : les types génériques `list<T>` et `map<K,V>` sont représentables.
- AC3 : `PinType.isAssignableFrom` gère les conversions implicites déclarées (`int`→`long`→`double`, `player`→`entity`).
- AC4 : chaque type porte une couleur et une forme pour le rendu, et une clé de traduction.
- AC5 : tests unitaires sur la matrice d'assignabilité, y compris jokers.

**Story 1.3 — Modèle de graphe et validation**
En tant que développeur, je veux les classes `Blueprint`, `Node`, `Pin`, `Link`, `Variable` et un validateur afin de garantir l'intégrité structurelle.
- AC1 : ajout/suppression de nœud, ajout/suppression de lien, avec application des règles de cardinalité (FR3).
- AC2 : `GraphValidator` retourne des `Diagnostic` typés (code, sévérité, cible, arguments de traduction).
- AC3 : détection des cycles de données ; les cycles d'exécution sont acceptés.
- AC4 : résolution des pins jokers propagée dans le nœud.
- AC5 : tests couvrant chaque code de diagnostic.

**Story 1.4 — Sérialisation NBT**
En tant que développeur, je veux sérialiser/désérialiser un blueprint en NBT afin de le stocker et le transmettre.
- AC1 : codecs pour blueprint, nœud, lien, variable, valeur littérale de chaque type de base.
- AC2 : le NBT porte un numéro de version de schéma et une table de migration extensible.
- AC3 : round-trip identique sur un graphe de test de 200 nœuds.
- AC4 : un identifiant de nœud inconnu est conservé tel quel (préparation des nœuds fantômes, FR40).
- AC5 : taille compressée mesurée et consignée dans le test (NFR5).

**Story 1.5 — Commande et cycle de vie**
En tant que joueur, je veux `/blueprint list|create|delete|enable|disable|info` afin de manipuler les blueprints sans éditeur.
- AC1 : commandes enregistrées via `CommandRegistrationCallback`, autocomplétion des identifiants.
- AC2 : `create` refuse un identifiant déjà pris ou invalide.
- AC3 : niveau de permission requis configurable, par défaut op 2 pour `create`/`delete`.
- AC4 : messages traduits `en_us` / `fr_fr`.
- AC5 : test d'intégration serveur couvrant chaque sous-commande.

---

### Épic 2 — Registre de nœuds et API d'extension

*Objectif : figer la surface publique qui permettra à Blueprint et aux mods tiers de déclarer des nœuds.*

**Story 2.1 — `NodeType` et builder**
En tant que développeuse de mod, je veux déclarer un type de nœud par un builder fluide afin d'ajouter une action en quelques lignes.
- AC1 : `NodeType.builder(id)` expose `.category`, `.exec()`, `.in`, `.out`, `.pure()`, `.side`, `.permission`, `.action`, `.build`.
- AC2 : un `NodeType` est immuable et décrit intégralement ses pins.
- AC3 : les pins d'entrée acceptent une valeur par défaut typée.
- AC4 : construire un nœud incohérent (pur avec pin exec, deux pins de même nom) lève une exception explicite au démarrage, pas en jeu.
- AC5 : javadoc complète sur les types publics.

**Story 2.2 — Registre et entrypoint**
En tant que développeuse de mod, je veux enregistrer mes nœuds via l'entrypoint Fabric `blueprint` afin de ne pas dépendre du code interne.
- AC1 : interface publique `BlueprintPlugin` avec `registerNodes`, `registerTypes`, `registerEvents` (les deux derniers par défaut vides).
- AC2 : le registre est figé après la phase d'enregistrement ; toute écriture ultérieure lève une exception.
- AC3 : un plugin qui lève une exception est isolé, journalisé, et n'empêche pas le chargement des autres.
- AC4 : les identifiants de nœud sont préfixés par le namespace du mod fournisseur ; un doublon est refusé avec un message nommant les deux mods.
- AC5 : un mod d'exemple dans `src/testmod` enregistre 3 nœuds et le test d'intégration vérifie leur présence.

**Story 2.3 — Contexte d'exécution des nœuds**
En tant que développeuse de mod, je veux un `NodeContext` afin de lire mes entrées, écrire mes sorties et accéder au monde.
- AC1 : `ctx.in(name)` typé, `ctx.out(name, value)`, `ctx.level()`, `ctx.server()`, `ctx.blueprint()`, `ctx.trigger()`.
- AC2 : lire un pin inexistant ou d'un mauvais type lève une exception de développement claire.
- AC3 : `ctx.exec(pinName)` permet aux nœuds de flux de choisir la branche suivante.
- AC4 : `ctx.suspend(ticks)` permet à un nœud tiers d'être suspendable.
- AC5 : le contexte est invalide hors de l'appel du nœud (garde anti-fuite testée).

**Story 2.4 — Descripteurs de nœuds sérialisables**
En tant que développeur, je veux qu'un `NodeType` produise un descripteur transmissible au client afin que l'éditeur affiche des nœuds qu'il ne connaît pas côté code.
- AC1 : descripteur = identifiant, catégorie, clés de traduction, pins (nom, nature, type, défaut), permission, drapeaux.
- AC2 : encodage/décodage réseau testé en round-trip.
- AC3 : un hash stable de l'ensemble du registre est calculable.
- AC4 : l'éditeur ne dépend d'aucune classe du mod fournisseur pour rendre le nœud.

**Story 2.5 — Événements enregistrables**
En tant que développeuse de mod, je veux déclarer mes propres événements déclencheurs afin que les joueurs réagissent à mes mécaniques.
- AC1 : `EventType` déclare ses pins de sortie et son mode de dispatch (global, par joueur, par dimension).
- AC2 : `BlueprintEvents.fire(eventId, payload)` déclenche tous les blueprints abonnés actifs.
- AC3 : le déclenchement est asynchrone-sûr : un appel hors thread serveur est reporté sur le thread serveur.
- AC4 : un événement sans abonné a un coût négligeable (mesuré).

---

### Épic 3 — Compilateur et VM

*Objectif : exécuter un graphe de façon déterministe, bornée et suspendable.*

**Story 3.1 — Représentation intermédiaire**
- AC1 : jeu d'instructions défini et documenté (`CONST`, `CALL`, `JMP`, `JMP_IF`, `LOAD_VAR`, `STORE_VAR`, `YIELD`, `RETURN`, `FRAME_PUSH/POP`).
- AC2 : chaque instruction porte l'UUID du nœud source pour le diagnostic et le débogage.
- AC3 : l'IR est sérialisable pour être mise en cache entre deux démarrages.

**Story 3.2 — Compilateur graphe → IR**
- AC1 : linéarisation du flux exec, allocation de registres pour chaque pin de sortie de données.
- AC2 : les nœuds purs sont ordonnés avant leur consommateur et mémoïsés par étape.
- AC3 : les branches (`branch`, `switch`) émettent des sauts corrects, testés sur des graphes de référence.
- AC4 : erreurs de compilation rattachées à un nœud, jamais un message générique.
- AC5 : compilation d'un graphe de 1 000 nœuds < 50 ms (NFR2), mesurée par un test de performance.

**Story 3.3 — VM d'exécution**
- AC1 : `BlueprintVm` exécute une IR avec pile de frames et tableau de slots.
- AC2 : compteur de fuel décrémenté par instruction ; dépassement → suspension + diagnostic + journal.
- AC3 : profondeur de frames bornée.
- AC4 : une exception levée par un nœud est capturée, journalisée avec le nœud fautif, et désactive le blueprint plutôt que de propager.
- AC5 : tests sur boucles, branches, récursion, dépassement de fuel.

**Story 3.4 — Suspension et reprise**
- AC1 : `YIELD` sauvegarde l'état d'exécution complet (pc, slots, frames) dans une structure sérialisable.
- AC2 : les exécutions suspendues sont persistées dans la sauvegarde du monde et reprises au chargement.
- AC3 : une exécution dont le contexte a disparu (joueur déconnecté, entité morte) est annulée proprement.
- AC4 : test : `wait 40t` traversant une sauvegarde/rechargement reprend correctement.

**Story 3.5 — Ordonnanceur**
- AC1 : les blueprints actifs sont exécutés sur le tick serveur avec un budget global partagé.
- AC2 : les exécutions en attente sont réparties pour lisser la charge.
- AC3 : un blueprint dépassant son budget N ticks d'affilée est désactivé et l'admin est notifié.
- AC4 : statistiques par blueprint exposées (temps moyen, pic, instructions).

---

### Épic 4 — BScript

*Objectif : équivalence texte ↔ graphe.*

**Story 4.1 — Grammaire et AST** — voir `docs/bscript-spec.md`.
- AC1 : grammaire complète documentée et gelée pour la v1.
- AC2 : AST couvrant déclarations, événements, instructions, expressions.
- AC3 : les opérateurs infixes se mappent sur des nœuds purs déclarés.

**Story 4.2 — Générateur graphe → BScript**
- AC1 : sortie déterministe (mêmes octets pour le même graphe).
- AC2 : reconstruction des structures de contrôle (`if/else`, `while`, `for`) depuis le flux exec quand le motif est reconnaissable ; sinon repli sur des étiquettes et `goto` explicites.
- AC3 : positions des nœuds émises en métadonnées (FR25).
- AC4 : test round-trip sur 20 graphes de référence.

**Story 4.3 — Parseur BScript → graphe**
- AC1 : erreurs de syntaxe avec ligne, colonne et message traduit.
- AC2 : un identifiant de nœud inconnu produit un nœud fantôme, pas un échec total.
- AC3 : mise en page automatique déterministe si les positions sont absentes.
- AC4 : fuzzing : aucun crash sur entrée malformée.

**Story 4.4 — Import/export et presse-papier**
- AC1 : `/blueprint export <id>` écrit un `.bp` dans `blueprint/exports/`.
- AC2 : `/blueprint import <fichier>` valide, applique les permissions et refuse les nœuds au-dessus du plafond.
- AC3 : Ctrl+C/Ctrl+V dans l'éditeur passe par le presse-papier système au format BScript (FR27).

---

### Épic 5 — Éditeur visuel

*Objectif : l'expérience d'édition. Voir `docs/ux-ui-spec.md`.*

**Story 5.1 — Canevas**
- AC1 : `BlueprintEditorScreen` avec pan (clic milieu / espace+glisser) et zoom molette 0,25×–2×.
- AC2 : grille avec accroche optionnelle, rendu correct à tous les niveaux de zoom.
- AC3 : culling : seuls les éléments visibles sont dessinés.
- AC4 : 60 fps avec 500 nœuds (NFR1), mesuré par un banc de rendu.

**Story 5.2 — Rendu et manipulation des nœuds**
- AC1 : rendu d'un nœud depuis son descripteur seul (couleur de catégorie, titre traduit, pins).
- AC2 : sélection simple, multiple, rectangle de sélection, déplacement, suppression.
- AC3 : édition des valeurs littérales en place selon le type (booléen, nombre, texte, énumération, sélecteur d'item).
- AC4 : les nœuds fantômes ont un rendu distinct et un message nommant le mod manquant.

**Story 5.3 — Liens**
- AC1 : tracé de lien par glisser, courbes de Bézier colorées par type, exec plus épais.
- AC2 : les cibles incompatibles sont grisées pendant le glisser.
- AC3 : relâcher dans le vide ouvre la palette contextuelle filtrée (FR29).
- AC4 : re-câbler ou détacher un lien existant par Alt+clic.

**Story 5.4 — Palette et recherche**
- AC1 : palette catégorisée + recherche floue sur nom, description, mod fournisseur.
- AC2 : filtrage par type de pin quand elle est ouverte depuis un lien.
- AC3 : nœuds récents et favoris.
- AC4 : résultats ≤ 5 ms pour 2 000 types de nœuds.

**Story 5.5 — Panneau des variables**
- AC1 : créer, renommer, retyper, changer la portée, définir la valeur par défaut.
- AC2 : glisser une variable dans le canevas crée `Get` ; avec Ctrl, crée `Set`.
- AC3 : le retypage incompatible avertit avant d'invalider des liens (FR10).

**Story 5.6 — Annuler/rétablir et panneau de diagnostics**
- AC1 : pile de commandes réversibles, ≥ 50 niveaux, couvrant toutes les mutations.
- AC2 : compilation à la volée (débouncée) affichant les diagnostics.
- AC3 : cliquer un diagnostic recentre la vue sur le nœud fautif.

**Story 5.7 — Confort d'édition**
- AC1 : commentaires et boîtes de regroupement déplaçant leur contenu.
- AC2 : alignement, distribution, mise en page automatique.
- AC3 : minimap, recentrage, « aller au nœud ».
- AC4 : thème chargé depuis `assets/blueprint/theme/*.json`.

---

### Épic 6 — Persistance et réseau

**Story 6.1 — Stockage monde**
- AC1 : blueprints persistés via `SavedData` dans la sauvegarde, écriture atomique.
- AC2 : chargement au démarrage du serveur avec rapport des erreurs, jamais de crash.
- AC3 : migration de schéma appliquée automatiquement, sauvegarde de l'ancienne version conservée.

**Story 6.2 — Synchronisation du registre**
- AC1 : au login, comparaison de hash ; envoi des descripteurs uniquement si divergence (FR35).
- AC2 : envoi fragmenté et compressé, testé avec 5 000 descripteurs.
- AC3 : un client sans les mods du serveur peut quand même éditer et voir les nœuds concernés.

**Story 6.3 — Patchs d'édition**
- AC1 : opérations d'édition modélisées (`AddNode`, `RemoveNode`, `MoveNode`, `Link`, `Unlink`, `SetLiteral`, `VarOp`).
- AC2 : le client envoie des patchs ; le serveur valide, applique, rediffuse aux autres éditeurs.
- AC3 : un patch refusé provoque une resynchronisation ciblée, pas une perte de travail.
- AC4 : verrouillage optimiste par numéro de révision.

**Story 6.4 — Sécurité réseau**
- AC1 : taille maximale par paquet et par blueprint, taux limité par joueur.
- AC2 : toute entrée réseau est validée côté serveur ; aucune confiance au client.
- AC3 : tests d'abus : paquet géant, nœud inexistant, lien invalide, identifiant malformé.

---

### Épic 7 — Événements et bibliothèque standard

**Story 7.1 — Nœuds de flux** — `branch`, `sequence`, `for`, `for_each`, `while`, `gate`, `do_once`, `switch`, `wait`, `wait_until`, `return`, plus le nœud pur `select` (requis par l'opérateur ternaire de BScript). AC : chaque nœud a un test d'exécution dédié.

**Story 7.2 — Nœuds purs maths / logique / chaînes** — arithmétique, comparaison, booléens, conversion, aléatoire à graine, chaînes (concat, format, split, contient). AC : tests de valeurs limites (division par zéro → diagnostic, pas d'exception).

**Story 7.3 — Nœuds monde** — lire/poser un bloc, tester un bloc, spawner une entité, jouer un son, particules, météo, heure, explosion, drop d'item. AC : chaque nœud déclare correctement sa permission et son côté.

**Story 7.4 — Nœuds entité et joueur** — position, téléportation, santé, effets, inventaire, message, titre, expérience, mode de jeu. AC : sécurité sur entité invalide ou déchargée.

**Story 7.5 — Nœuds item et texte** — construction d'`ItemStack`, comparaison, composants, construction de `Component` avec styles et couleurs.

**Story 7.6 — Événements du monde** — les 10 événements du FR19, branchés sur les callbacks Fabric API, avec test d'intégration par événement.

**Story 7.7 — Événement `command`** — un blueprint déclare une commande avec ses arguments typés ; la commande est enregistrée dynamiquement et retirée à la désactivation.

---

### Épic 8 — Intégration des mods tiers

**Story 8.1 — Annotation `@BlueprintNode`**
- AC1 : annoter une méthode statique dérive automatiquement les pins depuis la signature (`@In`, `@Out`, valeurs par défaut).
- AC2 : un processeur d'annotations optionnel génère la classe de plugin à la compilation ; en son absence, un scan des classes déclarées prend le relais.
- AC3 : les erreurs de déclaration sont signalées à la compilation quand le processeur est utilisé.
- AC4 : documentation et exemple complet dans `docs/extension-api.md`.

**Story 8.2 — Nœuds définis en datapack**
- AC1 : `data/<modid>/blueprint/nodes/<nom>.json` décrit un nœud composite dont le corps est une séquence de nœuds existants ou un fragment BScript.
- AC2 : rechargement à `/reload`.
- AC3 : un JSON invalide est signalé sans casser le rechargement des autres.
- AC4 : un nœud datapack ne peut pas dépasser la permission `GAMEPLAY`.

**Story 8.3 — Nœuds fantômes**
- AC1 : un identifiant inconnu produit un nœud fantôme conservant pins déduits des liens, littéraux et configuration (FR40).
- AC2 : le blueprint refuse de s'exécuter et nomme le mod manquant (FR41).
- AC3 : réinstaller le mod restaure le nœud sans perte.
- AC4 : test : sauvegarder avec un mod, charger sans, recharger avec.

**Story 8.4 — Couche de compatibilité**
- AC1 : les intégrations vivent dans `fr.blueprint.compat.<modid>` et sont chargées seulement si `FabricLoader.isModLoaded`.
- AC2 : au moins une intégration de référence livrée et documentée comme modèle.
- AC3 : le démarrage sans aucun de ces mods est vérifié en test.

**Story 8.5 — Stabilité de l'API**
- AC1 : `BlueprintApi.API_VERSION` exposé et vérifiable par un mod tiers.
- AC2 : un test de compatibilité binaire compare la surface publique à une signature de référence et échoue sur rupture non intentionnelle.
- AC3 : politique de dépréciation documentée.

---

### Épic 9 — Débogage, performance et finition

**Story 9.1 — Débogueur live** — surlignage du flux, pulsation des liens, valeurs des pins, pas-à-pas, points d'arrêt sur nœud. AC : le débogage n'est actif que pour les éditeurs autorisés et coûte 0 quand il est éteint.

**Story 9.2 — Profileur** — coût par blueprint, par nœud, top 10, export. AC : intégré à `/blueprint profile`.

**Story 9.3 — Permissions et audit** — plafond par blueprint, config serveur, journal des nœuds `ADMIN` (NFR15). AC : tests d'escalade de privilèges refusée.

**Story 9.4 — Localisation et accessibilité** — `en_us` et `fr_fr` complets, palette daltonien-compatible avec code de forme (NFR10, NFR11), navigation clavier de l'éditeur.

**Story 9.5 — Documentation utilisateur** — guide de prise en main, référence des nœuds générée depuis le registre, guide d'intégration pour les mods tiers.

---

### Épic 10 — Interfaces graphiques

*Objectif : qu'un blueprint puisse MONTRER quelque chose et RÉAGIR à ce qu'on y clique.*

*Aujourd'hui un graphe ne parle au joueur que par du texte : chat, titre, barre
d'action. Un menu de boutique, un choix de camp, un tableau de scores — tout ce qui
demande de choisir plutôt que de subir — est hors de portée.*

#### Épreuve de l'épic : quatre cas réels

*Une spécification ne vaut que si on la fait passer par ce que les gens vont
réellement construire. Les quatre suivants ont servi à trouver ce qui manquait ;
ils restent la liste de contrôle avant de déclarer l'épic terminé.*

| Cas | Ce qu'il exige | Où |
|---|---|---|
| **Distributeur** | ouvrir sur un bloc, solde par joueur, saisie d'un montant, retrait d'objets, message d'erreur | 10.1–10.4, 10.7, 10.8 |
| **Boutique** | liste défilante d'articles, icônes d'objets, prix, achat à la ligne, solde | 10.8 (liste, emplacement), 10.7 |
| **Création de personnage** | pages successives, choix exclusifs, saisie du nom, **aperçu du personnage** | 10.8 (case, curseur, aperçu), 10.1 (imbrication : masquer le parent masque la page) |
| **HUD** | visible en jouant, **sans figer ni capter la souris**, plusieurs à la fois, dessiné à chaque frame | **10.9** — rien dans 10.1–10.8 ne le permettait |

**Story 10.1 — Modèle d'écran** *(cœur)*
- AC1 : un `Screen` porte des éléments typés (panneau, étiquette, bouton, image, barre) ; chacun a un nom unique, une position, une taille, une ancre et un style.
- AC2 : les écrans appartiennent au blueprint — même sérialisation NBT, même préservation intégrale, même révision.
- AC3 : opérations d'édition réversibles (ajouter, déplacer, redimensionner, renommer, styliser, supprimer), comme les nœuds.
- AC4 : validation : noms uniques, tailles minimales, plafond d'éléments, référence à un élément inexistant signalée par un diagnostic.

**Story 10.2 — Concepteur visuel** *(client)*
- AC1 : un second onglet de l'éditeur ; poser un élément depuis une palette, le déplacer, le redimensionner par ses poignées.
- AC2 : sélection, multi-sélection, alignement, annuler/rétablir — la même infrastructure que le canevas de nœuds.
- AC3 : panneau de propriétés pour l'élément sélectionné (nom, position, taille, ancre, couleurs, texture).
- AC4 : aperçu fidèle : ce que montre le concepteur est ce que verra le joueur.

**Story 10.3 — Ouverture et rendu en jeu**
- AC1 : paquets d'ouverture et de fermeture ; l'écran se rend côté client depuis sa description, sans code par écran.
- AC2 : ancrage et mise à l'échelle corrects de 1× à 4× de GUI scale et sur toutes les résolutions.
- AC3 : une texture absente affiche un remplaçant visible ; jamais de plantage, jamais d'écran vide sans explication.

**Story 10.4 — Interactions câblées au graphe**
- AC1 : événements `gui/opened`, `gui/closed`, `gui/element_clicked` — ce dernier filtré par nom d'élément, comme `command` et `signal`.
- AC2 : nœuds `gui/open`, `gui/close`, et les modificateurs (texte, image, visibilité, activation, valeur).
- AC2b : les modifications d'un tick partent en UN paquet, seules celles qui ont CHANGÉ sont envoyées, et chaque ouverture porte un numéro d'instance — une mise à jour en retard ne s'applique jamais à un écran rouvert.
- AC3 : le serveur vérifie que l'écran est bien ouvert pour ce joueur et que l'élément existe ; la cadence des clics est limitée (FR52).

**Story 10.5 — Packs : un dossier échangeable**
- AC1 : un pack est un DOSSIER de `blueprint/scripts/` : `pack.json`, `textures/*.png`, et le `.bp` qui les utilise. On le donne, l'autre le dépose, `/blueprint-packs reload`.
- AC2 : un pack invalide est nommé et ignoré, jamais bloquant ; une texture absente affiche un remplaçant qui NOMME le pack manquant, comme un nœud fantôme nomme son mod.
- AC3 : bornes (2048×2048, PNG seul, nombre de textures) ; le style — couleurs, bordures, neuf-tranches — marche sans aucun pack installé.

**Story 10.7 — Liaison de données**
- AC1 : un élément se lie à une variable (étiquette → texte, barre → valeur) avec un format d'affichage ; l'écran suit sans qu'aucun nœud ne soit appelé.
- AC2 : la mise à jour part quand le graphe appelle `gui/refresh`, JAMAIS toute seule — coût nul au repos, aucun balayage par tick, et le client ne recalcule sa mise en page que sur paquet ou redimensionnement.
- AC3 : une variable liée qui disparaît produit un diagnostic à l'édition, pas un écran vide en jeu.

**Story 10.8 — Liste défilante, champ de saisie, emplacement d'objet**
- AC1 : `LIST` (gabarit de ligne répété, défilement, découpe), `INPUT` (filtre, longueur, revérifiés côté serveur), `SLOT` (affiche un itemstack et son infobulle).
- AC2 : la liste est alimentée par une `list<T>` du graphe ; un clic rend l'INDICE et la valeur, pas seulement le nom de l'élément.
- AC3 : pas de glisser-déposer d'objets — un slot qui accepte un dépôt est un CONTENEUR, avec ses transactions et ses duplications ; on passe par des boutons et les nœuds d'inventaire.

**Story 10.9 — HUD : afficher sans interrompre**
- AC1 : un écran peut être PERMANENT plutôt que modal — aucune capture d'entrée, le joueur continue de jouer ; plusieurs HUD coexistent, contrairement aux écrans modaux.
- AC2 : les éléments interactifs y sont refusés à l'ÉDITION (le curseur appartient au jeu), et une touche client masque TOUS les HUD Blueprint — le pendant de « Échap ferme toujours ».
- AC3 : dessiné à chaque frame, donc mise en page mise en cache et banc de rendu contre le budget NFR1.

**Story 10.6 — Quotas, accessibilité et documentation**
- AC1 : plafond d'éléments par écran et d'écrans par blueprint, configurables comme les quotas de graphe.
- AC2 : navigation clavier dans un écran ouvert, contraste vérifié comme NFR11.
- AC3 : un **pack d'exemple complet** rejoint `docs/examples/` — dossier, `pack.json`, textures et le `.bp` qui les utilise, validé par le même test que les six autres.

---

## 4. Ordre de livraison recommandé

1. Épic 1 → 2 → 3 (le socle exécutable sans interface, testable en tests unitaires)
2. Épic 7 partiellement (assez de nœuds pour valider le runtime)
3. Épic 5 + 6 en parallèle (l'éditeur a besoin du réseau)
4. Épic 4 (BScript peut arriver après l'éditeur, mais avant le copier-coller)
5. Épic 8 puis 9

Un jalon « démo » est atteint à la fin de l'étape 2 : un blueprint écrit en test unitaire
s'exécute en jeu sur un événement réel.
