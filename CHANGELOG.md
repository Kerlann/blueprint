# Journal des modifications

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
versionnage [SemVer](https://semver.org/lang/fr/). Les dates sont celles du dépôt.

L'**API d'extension** (module `api`) porte sa propre version, indépendante de celle du
mod : voir `BlueprintApi.API_VERSION` et `docs/api-surface.txt`, verrouillé par un test.

## [Non publié]

### Modifié — une seule commande racine

Le mod en posait **trois** : `/blueprint` côté serveur, `/blueprint-edit` et
`/blueprint-packs` côté client. Il n'en pose plus qu'une.

- **`/blueprint-edit` est supprimée.** Elle ne décidait plus rien depuis longtemps — son
  propre javadoc la décrivait comme « un ALIAS de `/blueprint edit` » : elle réécrivait la
  commande et la renvoyait au serveur. Elle obligeait en échange à maintenir deux jeux de
  suggestions, dont l'un lisait une liste reçue à la connexion, périmée dès la première
  création. `/blueprint edit` lit le gestionnaire vivant, et **F6** couvre le seul chemin qui
  était réellement local (l'éditeur de démo, hors serveur).
- **`/blueprint-packs` devient `/blueprint packs list|reload`**, une commande serveur qui
  transmet la demande au client par un paquet (`PacksAction`). Le serveur ne sait pas quels
  packs le joueur a et n'a pas à le savoir : tout le travail — le disque, les textures,
  l'affichage — reste chez le client, où les packs vivent. Ouverte à tous, et elle n'agit que
  sur les packs de l'appelant.
- **Ce n'était pas qu'un rangement.** Le geste évident — une racine *cliente* nommée
  `blueprint` — aurait intercepté tout l'arbre serveur : Fabric ne renvoie au serveur que les
  commandes **inconnues** (`dispatcherUnknownCommand`), pas celles dont seul le sous-chemin
  manque. Un `/blueprint list` aurait levé `dispatcherUnknownArgument` chez le client, qui
  aurait répondu « argument incorrect » sans rien envoyer. C'est pourquoi les packs passent
  par un paquet et non par un renommage.
- **`ClientFeedback` est supprimée** avec elles. Cette interface de `platform` n'existait que
  pour abstraire le type de source des commandes clientes, qui diverge entre Fabric et
  NeoForge — le seul endroit du portage où les deux chargeurs se séparaient sur les commandes.
  Sans commande cliente, le problème n'est plus contourné : il n'existe plus.

Les racines **dynamiques** ne changent pas : un blueprint qui déclare `home` obtient toujours
`/home`. Ce sont des commandes d'auteur, pas des commandes du mod.

### Ajouté — NFR14, les données d'un joueur

L'exigence était écrite depuis le PRD (`prd.md`, NFR14 : « les données d'un joueur sont
supprimables et n'excèdent pas 64 Ko par joueur ») et **rien ne l'implémentait**. Tout vivait
dans le `SavedData` du monde sans aucune borne, et aucun moyen n'existait d'effacer un joueur.
Un graphe qui ajoute une ligne d'historique à chaque mort — cas parfaitement ordinaire —
faisait grossir la sauvegarde du monde sans fin, sans qu'aucun symptôme ne le dise avant que
le fichier ne devienne pénible à écrire.

- **Plafond de 64 Ko par joueur**, portées `@player` et `@player_shared` **confondues**.
  NFR14 ne nomme que la première, née avant la seconde ; les compter séparément aurait donné
  un plafond qu'un changement de mot-clé suffit à contourner.
- **Le poids est estimé, pas mesuré.** Le poids exact serait celui du NBT, donc un encodage
  complet à chaque écriture, dans le chemin de la VM. L'estimation est volontairement
  **majorante** — deux octets par caractère là où l'UTF-8 en écrit souvent un : se tromper
  vers le haut refuse un peu tôt, se tromper vers le bas laisse passer ce que le plafond
  existe pour interdire, et c'est la seule des deux erreurs qui ne se voit pas.
- **Une écriture au-delà du plafond faute en le disant**, elle ne disparaît pas. Un graphe
  qui croit avoir enregistré la progression du joueur est une panne que le joueur découvre à
  sa reconnexion suivante, sans que rien ne relie les deux.
- **Un joueur déjà au-delà peut encore réduire** : seule une écriture qui fait grossir est
  refusée. Sans cela, un joueur au-delà du plafond ne pourrait plus rien écrire, pas même
  pour faire le ménage.
- **`/blueprint vars info <joueur|uuid>`** et **`/blueprint vars purge <joueur|uuid>`**,
  réservées aux administrateurs, la seconde journalisée. Par nom ou UUID et non par sélecteur
  d'entité : celui qui demande l'effacement de ses données a en général quitté le serveur, et
  un sélecteur ne résout que les joueurs connectés. La purge n'emporte que les portées
  joueur — jamais `@world` ni `@graph`, qui sont les données de la partie.

### Corrigé — robustesse du fil

Quatre défauts trouvés par un audit du partage client/serveur (`docs/plan-replication.md`).
Les trois premiers touchaient **le même chemin** : les modifications d'écran d'un tick
voyagent groupées, donc un seul échec ne coûtait pas une modification mais **toutes celles
du joueur** — sans rien signaler, le graphe continuant comme si de rien n'était.

- **Une nature de modification inconnue n'emporte plus la trame.** Le codec faisait
  `Kind.values()[ordinal]` sans borne : un ordinal hors plage levait une
  `ArrayIndexOutOfBoundsException` chez le client. Ce n'est pas une hypothèse — la liste des
  natures a grandi deux fois (cinq en 10.4, douze en 10.13), donc un client plus ancien que
  son serveur est un cas **attendu**. L'entrée illisible est maintenant lue jusqu'au bout
  puis jetée ; les suivantes arrivent.
- **Un texte ou une liste trop longs sont coupés au lieu de faire lever l'encodeur.** Rien
  ne bornait la longueur avant le fil : `GraphGuard` plafonne les textes **du graphe** à
  4 096 caractères, mais `string/concat` en fabrique de bien plus longs à l'exécution, et
  `gui/set_text` les transmettait tels quels. Le plafond vit désormais dans le modèle
  (`ScreenUpdate.MAX_TEXT`) et son constructeur le fait respecter. Les listes sont tronquées
  **par lignes entières** : une dernière ligne coupée en plein mot se lit comme une donnée,
  pas comme une limite atteinte.
- **Une file de modifications trop longue se découpe en plusieurs trames.** Elle est indexée
  par écran+élément+nature : 128 éléments et douze natures la portent bien au-delà du
  plafond d'une trame sans qu'aucun abus soit en cause. Découper plutôt que tronquer, parce
  qu'une modification jetée en silence laisse un élément sur une valeur périmée que le graphe
  croit avoir changée.
- **Les quotas de clics et d'ouvertures d'écran sont oubliés à la déconnexion.** Deux des
  quatre seaux ne l'étaient pas : chaque joueur ayant jamais cliqué laissait une entrée
  gardée jusqu'au redémarrage, alors que `RateLimiter` affirme que sa table est bornée par le
  nombre de joueurs connectés. `forget` parcourt maintenant une liste unique — deux endroits
  à tenir d'accord étaient la cause.

### Corrigé — messages

- **La faute d'une variable sans propriétaire ne renvoie plus vers des nœuds inexistants.**
  Elle recommandait `var/get_for` / `var/set_for`, jamais enregistrés : `StandardNodes` ne
  déclare que `var/get` et `var/set`. L'auteur cherchait dans la palette un mot qu'aucun
  nœud ne porte. Le message nomme désormais les deux sorties réelles — brancher le graphe sur
  un événement qui porte un joueur, ou donner à la variable une portée qui n'en demande pas.

### Modifié — comportement

- **`world/get_block` et `world/is_block` ne génèrent plus de chunk.** Les deux nœuds
  appelaient `getBlockState` sur la position reçue ; en vanilla, cela **génère le chunk**
  s'il manque — de façon synchrone, sur le fil serveur, pour des dizaines de
  millisecondes. Un graphe qui sondait des positions lointaines dans une boucle figeait
  donc le serveur, et aucun coût de fuel ne pouvait l'en empêcher : il aurait fallu tarifer
  ces nœuds à plusieurs milliers, soit deux appels par tick.

  Ils testent désormais le chargement et portent une sortie **`loaded`**. Sur un chunk non
  chargé, `state` rend de l'air et `matches` rend faux, avec `loaded` à faux pour le dire.

  **Ce que cela change pour un graphe existant** : une lecture hors des chunks chargés
  rendait auparavant l'état réel (au prix de la génération) ; elle rend maintenant de l'air.
  Un graphe qui s'appuyait sur ce comportement doit brancher `loaded` — la sortie est
  ajoutée, donc aucun lien existant n'est cassé et aucun graphe enregistré n'a besoin d'être
  retouché.

### Performance

Épics 13 à 19 du plan `docs/plan-optimisation.md`. Les chiffres sont mesurés par des bancs
commités, chacun vu rougir avant sa correction.

- **Boucle de la VM : 744 → 288 octets alloués par appel de nœud.** Copie défensive
  supprimée, résolution de type et index de pins calculés une fois par exécution au lieu
  d'une fois par nœud, tables d'entrées et de sorties prêtées par l'exécution.
- **Ordonnanceur en temps linéaire** : le parcours d'un tick était quadratique en nombre
  d'exécutions simultanées.
- **Liens indexés dans `Blueprint`** : la validation passait de `O(N·L + L²)` à `O(N + L)`.
  Compilation d'un graphe dense de mille nœuds : **112 ms → 6,2 ms**. Le NFR2 était déjà
  violé sans que le banc d'alors puisse le voir, ses nœuds n'ayant aucun pin de données.
- **`fuelCost` enfin renseigné sur 95 nœuds.** Le mécanisme existait de bout en bout et
  n'avait jamais servi : tous les nœuds coûtaient 1, du `math/add` au raycast de 128 blocs.
  Un test interdit désormais le retour au défaut.
- **Bornes manquantes** sur `string/concat`, `string/replace`, `world/particles`,
  `list/add` et `map/put` — les quatre premières permettaient d'épuiser la mémoire du
  serveur depuis un graphe trivial.
- **Éditeur** : « ce pin est-il câblé ? » ne balaie plus tous les liens, la minimap ne
  reconstruit plus sa table par image, `Screen.childrenOf` est indexé (la passe de
  disposition était quadratique), et le HUD mémorise sa disposition.
- **Sauvegarde** : le reflet sur disque ne bloque plus le fil serveur, et un graphe
  inchangé n'est plus réencodé à chaque sauvegarde du monde.
- **`gui/show_hud` est idempotent** : réafficher le même écran n'envoie plus la description
  entière, réencodée et regzippée, à chaque appel.

## [1.0.0] — 2026-08-04

Première version **publiée**. Aux neuf épics du PRD s'ajoutent trois épics nés de
l'usage réel : les interfaces graphiques, le contenu déclaré, et l'éditeur repris là
où il gênait. 86 documents de story, 85 gates QA, **1188 tests headless** et
**20 gametests** dans un serveur réel.

### Ajouté — interfaces graphiques (épic 10)

- **Concepteur d'écrans** dans l'éditeur : onze types d'éléments, dispositions en
  ligne/colonne/grille, ancres sur neuf cellules, styles nommés et réutilisables,
  guides d'alignement, calques, et un canevas **1920×1080** qu'on cadre et dans
  lequel on zoome à la molette.
- **Écrans ouverts en jeu** par le graphe, redimensionnés avec la fenêtre et l'échelle
  GUI du joueur, avec leurs événements en retour (clic, liste, saisie, fermeture).
- **HUD permanent** : un bandeau qui s'affiche pendant qu'on continue de jouer.
- **Liaison de données** : une étiquette suit une variable, format compris.
- **Panneaux défilants** (vertical, horizontal, les deux), avec curseurs qu'on tire,
  découpage au bord du cadre, molette, clavier — et **le clic qui ne traverse pas** ce
  que le cadre a coupé.
- **Packs d'images** échangeables pour habiller un menu sans toucher au graphe.
- **Reflet sur disque** : chaque enregistrement écrit aussi le `.bp` dans
  `blueprint/exports/`, qui survit à la suppression du blueprint.

### Ajouté — contenu déclaré (épic 11)

- **Items et blocs déclarés par fichiers** dans `blueprint/content/` : réellement
  enregistrés dans les registres du jeu — `/give` les connaît, une recette les utilise.
  Un fichier invalide est **écarté avec son nom en rouge** au lieu d'empêcher le jeu
  de démarrer.
- **Textures** : le PNG déposé à côté du JSON génère un pack de ressources dans
  `resourcepacks/blueprint_content/`, activé une fois — et **jamais réactivé** si le
  joueur l'a décoché.
- **Blocs réels** : dureté, outil exigé, lumière, son, butin, et une vitesse de minage
  obtenue en **posant la question au jeu**, ce qui répond juste pour les outils des
  mods qu'on ne connaît pas.
- **Huit touches** qu'un graphe écoute — par **emplacement**, jamais par code de
  touche : le joueur décide de la touche, et un conflit se règle là où le jeu règle
  déjà tous les conflits de touches.
- **Trois événements complétés** (quel objet, quel bloc) et quatre nœuds pour
  reconnaître et habiller une pile.
- **Démonstration « banque »** livrée : un distributeur qu'on pose et qu'on clique,
  deux coupures de monnaie, un compte par joueur, un retrait qui fait l'appoint.

### Ajouté — l'éditeur à l'usage (épic 12)

- **Menu d'ajout repensé** à la manière d'Unreal : il s'ouvre sur son **index replié**
  et non sur 184 nœuds, avec un vrai champ de recherche et une case « Contextuel ».
- **Déplier une catégorie ne perd plus la place** où l'on était, et la barre de
  défilement se saisit.
- **Les variables comme Unreal** : une lecture est une **capsule** teintée par le
  **type** de la variable — on distingue un booléen d'un flottant à travers tout un
  graphe sans lire un seul nom.

### Ajouté — éditeur et bibliothèque de nœuds (épics 5 et 7)

- Licence **MIT** (© 2026 Kerlann). Le dépôt n'en avait aucune, donc restait sous
  droit d'auteur par défaut : ni fork ni redistribution possibles. Le champ
  `license` des `fabric.mod.json` — celui que le lanceur montre aux joueurs —
  annonçait lui aussi « All Rights Reserved ».
- **Menus contextuels** au clic droit, distincts par cible : nœud (dupliquer,
  supprimer, casser les liens, commenter, aligner), pin (casser ses liens, valeur
  par défaut, **promouvoir en variable**), fil (le supprimer), canevas (la palette).
- **Insertion d'un nœud sur un fil** en le lâchant dessus, le fil se recâblant de
  part et d'autre — halo vert pendant le glissement.
- **Aspect** rapproché de l'éditeur d'Unreal : coins arrondis, ombre portée, en-tête
  en dégradé, pictogramme de catégorie, halo de sélection.
- **Billes de flux** sur les fils d'exécution parcourus, en débogage.
- **Sous-catégories** de nœuds (`math/arithmetic`, `event/player`…) : la palette les
  affiche en arbre repliable, les six catégories qui dépassaient la dizaine de nœuds
  sont rangées, et un mod tiers peut déclarer les siennes.
- **La bibliothèque passe de 89 à 184 nœuds et de 10 à 18 événements** (audit de
  couverture) :
  - **Vecteurs et positions** (17) — `vec3` était consommé par sept pins et produit
    par un seul : « des particules deux blocs au-dessus du joueur » était inexprimable.
  - **Interroger le monde** (14) — joueurs connectés, entités alentour, le plus
    proche, heure, météo, dimension, lumière, surface. Un graphe ne voyait rien
    au-delà de ce que son événement lui donnait.
  - **Retours ciblés vers un joueur** (5) — sous-titre, barre d'action, durées du
    titre, son et particules **privés** : ils partaient à tout le monde.
  - **Dictionnaires** (9) — le type existait depuis la 1.2 sans qu'aucun nœud ne
    l'utilise.
  - **Chaînes et maths** (18) — découper, remplacer, extraire, texte → nombre,
    racine, puissance, borner, interpoler, trigonométrie.
  - **Huit événements** que Fabric exposait : dégâts subis, qui a tué qui,
    réapparition, changement de dimension, frapper, interagir, dormir, se lever.
  - **Inventaire** (5) — lire ce que porte un joueur et le lui retirer : `give_item`
    existait seul, « s'il a la clé, ouvre la porte » était inexprimable.
  - **Scoreboard et équipes** (6) — la mémoire PARTAGÉE de Minecraft, celle que les
    commandes et l'affichage latéral savent lire.
  - **Texte riche avancé** (6) — gras, infobulle, clic, traduction.
  - **Raycast** (3) — « ce que le joueur regarde », impossible jusque-là.
  - **Barre de boss** (3) — le seul affichage persistant du jeu, avec réutilisation
    par nom, plafond et nettoyage à l'arrêt.

### Corrigé — depuis la 0.1.0

- **`event/signal` ne se déclenchait jamais.** Il apparaissait dans la palette, se
  posait, se câblait, se sauvegardait — et rien au monde ne l'émettait. C'était la
  primitive « un blueprint en appelle un autre ». Il a désormais son nœud
  `signal/emit`, sa commande `/blueprint signal`, un filtre par nom et une borne
  anti-récursion.
- **`world/raycast` et `world/raycast_entity` levaient à chaque exécution** — dans la
  palette et dans la référence générée, indiscernables d'un nœud sain, parce que
  **rien ne les avait jamais exécutés**. Un gametest fait désormais tourner les 99
  nœuds non purs dans un vrai serveur.
- **Quatre nœuds purs à sorties multiples n'étaient jamais émis en BScript**
  (`vec/split`, `pos/split`, `map/get`, `convert/to_number`) : perdus à la relecture,
  donc perte de données répétée et silencieuse sur la garantie centrale du produit.
- **`player/remove_item` mentait sur ce qu'il avait retiré.** Mojang traite
  `maxCount == 0` comme un **comptage** et rend le total possédé ; le premier clic sur
  un bouton « déposer » créditait donc tout l'inventaire sans rien prendre.
- **Un nœud de touche posé et jamais édité était muet** — le point d'entrée mort.
- **Un pack de ressources vide** apparaissait chez tous ceux qui n'avaient jamais rien
  déclaré.
- **Répartition des événements** : le pont tenait un index que quatre chemins filtrés
  ignoraient. À 12 000 nœuds, le budget d'un tick coûtait **8,5 ms** pour ne rien
  trouver, contre **0,15 ms** désormais.

### Modifié — depuis la 0.1.0

- **Dépendance Minecraft resserrée** de `~1.21` à `>=1.21.11 <1.22`. La borne large
  laissait le mod se charger sur 1.21.0 à 1.21.10, où il est compilé contre des
  signatures qui n'existent pas : le joueur récoltait une pile d'appels au démarrage
  au lieu d'un refus lisible.
- **Le texte de la licence MIT est joint au JAR.** Le `fabric.mod.json` l'annonçait
  sans le fournir, ce qui ne remplit pas la condition de redistribution.
- **API 1.0.0 → 1.2.0** : `NodeCategory` accepte un chemin à deux niveaux et gagne
  `parent()`, `leaf()`, `isSub()` ; `NodeCategories` gagne 13 constantes puis
  `EVENT_INPUT`. Rien de retiré ni de modifié — `isCompatibleWith(1, 0)` reste vrai.

## 0.1.0 — 2026-08-03 *(jalon interne, jamais publié)*

Première version complète : les neuf épics du PRD sont livrés. Un mod Fabric 1.21.11
qui ajoute un éditeur de logique par nœuds, à la manière des Blueprints d'Unreal, avec
son modèle de graphe, son compilateur, sa machine virtuelle bornée en carburant, son
éditeur visuel et son API d'extension pour les mods tiers.

### Ajouté — modèle et exécution

- Modèle de graphe versionné : nœuds, liens, variables, commentaires, métadonnées ;
  **18 opérations d'édition réversibles**, chacune portant son inverse.
- `GraphValidator` — source de vérité unique du câblage (`canLink`), partagée par
  l'éditeur, le serveur et le compilateur ; 19 diagnostics traduits et ciblés.
- Sérialisation NBT versionnée à **préservation intégrale** : un nœud d'un mod absent
  traverse une sauvegarde et un chargement sans rien perdre.
- Compilateur graphe → IR et machine virtuelle **bornée en carburant**, avec
  mémoïsation des nœuds purs par portée de branche, suspension persistée
  (`Attendre`, `Attendre que`) et ordonnanceur au tick.
- **89 nœuds** standard dans 13 catégories : flux (branchement, séquence, boucles,
  portail, pour-chaque), maths, logique, chaînes, listes génériques, joueur, monde,
  entités, temps — voir `docs/node-reference.md`, généré depuis le registre.
- 10 événements enregistrables (connexion, tick, chat, clic, casse, mort, commande…).

### Ajouté — éditeur visuel

- Canevas : panoramique, huit crans de zoom, grille, accroche, culling, minimap,
  thème JSON, commentaires, alignement (`Q`) et mise en page automatique.
- Câblage à la souris avec validation en direct, détachement `Alt`+clic, sélection
  d'un fil et suppression, littéraux éditables sur le nœud **et** dans le panneau de
  détails, sélecteurs riches (objet, bloc, position, direction).
- Palette (`Espace`) avec recherche floue, catégories, favoris et récents ;
  aller-au-nœud (`Ctrl+F`) ; navigation au clavier.
- Annuler/rétablir par gestes coalescés ; copier/coller **en BScript** (le
  presse-papier est du texte lisible et collable dans un salon).
- Panneaux variables, détails et diagnostics — tous défilants, tous cliquables.
- Vue script côte à côte, export et import de BScript.
- Infobulles au survol : type d'un pin, diagnostic d'un nœud fauté, mod manquant d'un
  fantôme, raccourci d'un bouton.
- Pastille de permission sur l'en-tête, losange sur les liens qui convertissent.

### Ajouté — serveur et multijoueur

- Persistance dans la sauvegarde du monde, exécutions suspendues comprises.
- Synchronisation du registre à la connexion, ouverture et enregistrement par
  paquets avec **verrou optimiste** (deux éditeurs ne s'écrasent plus en silence).
- Garde de graphe et quotas côté serveur ; permissions par niveau avec plafond par
  blueprint et journal d'audit des nœuds ADMIN.
- Commandes `/blueprint` (create, list, info, enable, export, import, demo, debug,
  profile) et `/blueprint-edit` côté client.

### Ajouté — outillage et extension

- **API d'extension** : `NodeType`, `PinType` avec coercitions déclarées, registre,
  entrypoint Fabric, annotation `@BlueprintNode`, nœuds composites de datapack
  rechargeables à chaud, couche de compatibilité par mod, surface verrouillée par test.
- **Nœuds fantômes** : un blueprint qui référence un mod absent s'ouvre, se lit,
  s'enregistre et retrouve ses nœuds quand le mod revient.
- Débogueur pas-à-pas visible dans l'éditeur (points d'arrêt, valeurs, `F10`, `F5`)
  et profileur par nœud, tous deux à **coût nul quand ils sont éteints**.
- BScript v1 : représentation textuelle déterministe, round-trip exact ; `.bp` = texte.
- Intégration continue : deux jobs (tests headless, gametests dans un serveur réel),
  couverture ≥ 80 % sur le cœur et ≥ 82 % sur la partie testable du client, référence
  des nœuds et surface d'API régénérées et comparées à chaque construction.
- Documentation : guide de démarrage, référence des nœuds générée, spécification de
  l'API d'extension, 51 stories et 50 gates QA.

### Connu — reporté en v1.1

- Sucre syntaxique BScript (`4.2b`) : la grammaire v1 est complète mais verbeuse.
- Patchs par opération et édition multi-joueur simultanée (`6.3`) : aujourd'hui, un
  enregistrement complet sous verrou optimiste.
- Processeur d'annotations à la compilation (`8.1`) : aujourd'hui par réflexion.
- Corps BScript dans les nœuds composites de datapack (`8.2`).
- Reroutage d'un lien par double-clic : demande que le modèle du cœur porte des
  points intermédiaires persistés et synchronisés.
- Câblage complet au clavier sans souris.

[1.0.0]: https://github.com/Kerlann/blueprint/releases/tag/v1.0.0
