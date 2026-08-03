# Journal des modifications

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
versionnage [SemVer](https://semver.org/lang/fr/). Les dates sont celles du dépôt.

L'**API d'extension** (module `api`) porte sa propre version, indépendante de celle du
mod : voir `BlueprintApi.API_VERSION` et `docs/api-surface.txt`, verrouillé par un test.

## [Non publié]

### Ajouté

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

### Corrigé

- **`event/signal` ne se déclenchait jamais.** Il apparaissait dans la palette, se
  posait, se câblait, se sauvegardait — et rien au monde ne l'émettait. C'était la
  primitive « un blueprint en appelle un autre ». Il a désormais son nœud
  `signal/emit`, sa commande `/blueprint signal`, un filtre par nom et une borne
  anti-récursion.

### Modifié

- **API 1.0.0 → 1.1.0** : `NodeCategory` accepte un chemin à deux niveaux et gagne
  `parent()`, `leaf()`, `isSub()` ; `NodeCategories` gagne 13 constantes. Rien de
  retiré ni de modifié — `isCompatibleWith(1, 0)` reste vrai.

## [0.1.0] — 2026-08-03

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

[Non publié]: https://github.com/Kerlann/blueprint/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Kerlann/blueprint/releases/tag/v0.1.0
