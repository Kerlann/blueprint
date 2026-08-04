# Documentation Blueprint — dossier BMAD

Dossier de planification suivant la **méthode BMAD** (Analyst → PM → Architect → UX → SM → Dev → QA).

## Index

| Document | Agent | Rôle |
|---|---|---|
| [`getting-started.md`](getting-started.md) | — | **Guide joueur** : premier blueprint en dix minutes, raccourcis, dépannage |
| [`examples/`](examples/README.md) | — | **Huit blueprints d'exemple** prêts à charger : `/blueprint examples` |
| [`node-reference.md`](node-reference.md) | — | **Généré** depuis le registre : tous les nœuds, leurs pins, leur coût |
| [`brief.md`](brief.md) | Analyst | Problème, personas, périmètre MVP, risques |
| [`prd.md`](prd.md) | PM | 52 exigences fonctionnelles, 15 non fonctionnelles, 10 épics, stories |
| [`architecture.md`](architecture.md) | Architect | Modules, modèle, compilation, VM, réseau, décisions |
| [`architecture/tech-stack.md`](architecture/tech-stack.md) | Architect | Versions, bibliothèques autorisées, **table Mojang/Yarn** |
| [`architecture/coding-standards.md`](architecture/coding-standards.md) | Architect | Règles absolues du projet |
| [`architecture/source-tree.md`](architecture/source-tree.md) | Architect | Où placer chaque classe |
| [`ux-ui-spec.md`](ux-ui-spec.md) | UX Expert | Éditeur : disposition, interactions, raccourcis, thème, accessibilité |
| [`bscript-spec.md`](bscript-spec.md) | Architect | Grammaire du langage généré et correspondance avec le graphe |
| [`extension-api.md`](extension-api.md) | Architect | **Contrat d'intégration pour les mods tiers** |
| [`stories/`](stories/) | SM | Stories prêtes pour l'agent Dev |
| [`session-de-verification.md`](session-de-verification.md) | QA | **Ce qui reste à voir en jeu** : 38 points en quatre blocs, une heure |

Configuration : [`../.bmad-core/core-config.yaml`](../.bmad-core/core-config.yaml)

## Le produit en trois phrases

Blueprint est un mod Fabric 1.21.11 qui apporte en jeu un **éditeur de logique par nœuds** :
on pose des nœuds, on les relie, on déclare des variables typées, et le graphe s'exécute
côté serveur sur des événements du monde. Tout graphe se **compile en un script texte
lisible (BScript)** et tout BScript se re-parse en graphe. Les **autres mods déclarent
leurs propres nœuds** via un entrypoint Fabric, une annotation, ou un simple JSON de
datapack — sans dépendance dure et sans casser les graphes existants quand ils sont retirés.

## Cycle BMAD

```
brief.md ──► prd.md ──► architecture.md + ux-ui-spec.md
                                │
                                ▼
                      SM : rédige stories/<épic>.<n>.md
                                │
                                ▼
                      Dev : implémente (charge les 3 shards architecture/)
                                │
                                ▼
                      QA : remplit la section QA Results de la story
```

## État

| Épic | Titre | Stories rédigées | Statut |
|---|---|---|---|
| 1 | Fondations et modèle de graphe | 1.1 → 1.5 | **Complet** — 5 Done, gates PASS (session en jeu du 2026-08-02 : VERIFY-001/002/003 clos) |
| 1 | (spike) gametests | 1.6 | **Complet** — gate PASS ; `./gradlew runGametest` : 5 tests dans un serveur réel, quatre VERIFY automatisés |
| 2 | Registre de nœuds et API d'extension | 2.1 → 2.5 | **Complet** — 5 gates PASS |
| 3 | Compilateur et VM | 3.1+3.2+3.3, 3.4+3.5 | **Complet** — 2 gates PASS (3 corrections en review dont un high sémantique) |
| 7 | Événements et bibliothèque | 7.1a+7.2+7.6 (groupées) | **Complet** — gate PASS, démo ping/pong vérifiée en jeu (VERIFY-004) |
| 7 | **Bibliothèque complète** | 7.1b, 7.3-7.5, 7.7, **7.9, 7.10** | **Complet** — 5 gates PASS ; audit de couverture : 89 → **184 nœuds**, 10 → **18 événements** (1 high : `event/signal` était un point d'entrée MORT depuis plusieurs stories — il se posait, se câblait, et rien ne le déclenchait) |
| 4 | Démo, export/import | 4.4a | **Complet** — gate PASS |
| 4 | BScript v1 | 4.1+4.2+4.3 (groupées) | **Complet** — gate PASS (4 corrections en review dont un high de fidélité) ; round-trip exact, `.bp` = texte |
| 6 | Persistance monde | 6.1 | **Complet** — gate PASS (VERIFY-005 : redémarrage à confirmer en jeu) |
| 6 | **Réseau multijoueur** | 6.2, 6.3(lite), 6.4 | **Complet** — 3 gates PASS (1 high + 4 medium corrigés en review dont une perte de travail et une escalade de permission) ; synchro du registre au join, ouverture/enregistrement par paquets avec verrou optimiste, garde de graphe + quotas ; reste v1.1 : patchs par opération et multi-éditeur |
| 5 | **Éditeur visuel — COMPLET** | 5.1 → 5.14 (18 stories) | **18 gates PASS** — 1 high (crash Ctrl+S) + 6 medium corrigés en review, 117 tests client ; l'éditeur fait tout le backlog UE (littéraux+sélecteurs, undo, Ctrl+S réel, diagnostics cliquables, variables, copier/coller BScript, détails, palette complète, vue script, commentaires/minimap/thème) ; **5.12** née de l'usage réel : panneaux qui défilent, infobulles au survol, clic sur les liens, pastille de permission et losange de conversion (1 high corrigé : un geste d'annulation laissé ouvert) ; **5.13** l'écart avec l'éditeur d'Unreal : menus contextuels par cible, promotion d'un pin en variable, insertion d'un nœud sur un fil, châssis arrondi et ombré, billes de flux en débogage (2 high corrigés) |
| 8 | **Intégration des mods tiers** | 8.1 → 8.5 | **Complet** — 5 gates PASS (3 medium + 1 low corrigés en review dont une violation d'AC : un JSON au mauvais type emportait tout le rechargement) ; annotation `@BlueprintNode`, nœuds composites de datapack rechargeables, fantômes prouvés de bout en bout, couche de compatibilité et surface d'API verrouillée par un test |
| 9 | **Débogage, performance, finition** | 9.1a, 9.1b, 9.2, 9.3, 9.4, 9.5 | **Complet** — 6 gates PASS (1 medium NFR11 + 3 medium débogueur corrigés en review) ; débogueur pas-à-pas visible dans l'éditeur, profileur par nœud, quotas configurables + audit ADMIN, i18n vérifiée par les sources, palette daltonienne à cinq formes, guide joueur et référence générée |
| 4 | 4.2b (sucre BScript) | — | **Reste v1.1** — seul morceau du PRD non livré, consigné dans la story 4.1-4.3 |
| 10 | **Interfaces graphiques** | 10.1 → 10.16 | **Complet** — 16 gates PASS. Modèle d'écran, concepteur, ouverture et rendu, boutons câblés, packs d'images échangeables, quotas et clavier, liaison de données, éléments riches, HUD permanent, **10.10** née de l'usage (conteneurs qui rangent, tailles `fill`/`hug`, styles nommés) et **10.11** de même (zoom sur le curseur, canevas 1920×1080, panneaux repliables), puis **10.12** (infobulles, paragraphes, styles nommés basculés par le graphe, déclic au clic) et **10.13** (le panneau défilant), puis **10.14**, un audit à froid : le concepteur relançait la passe de disposition huit fois par image, et quatre stories de nouveautés n'étaient montrées par aucun exemple ; **10.15** enfin : deux bancs d'essai volumineux (110 éléments, 361 nœuds) et un dossier `run/` régénéré depuis le modèle, qui datait d'avant trois stories ; **10.16** enfin : `blueprint/exports/` devient un **reflet** fidèle du monde à chaque enregistrement, au lieu d'une photo du jour où l'on a pensé à exporter. Quatre défauts que seule la mesure a trouvés : une bordure par défaut invisible (1,74:1), des libellés qui recouvraient déjà les champs, un concepteur qui **peignait à 320×180 pendant que le clic résolvait ailleurs**, et une ancre laissée en haut à gauche à la création — celle qui envoyait hors écran tout élément posé sur un grand canevas |

| 11 | **Contenu déclaré** | 11.1 → 11.2 | **En cours** — 2 gates PASS. **11.2** : l'item déclaré **s'affiche enfin**, avec le PNG déposé à côté de son JSON — un pack de ressources est généré dans `resourcepacks/blueprint_content/` et activé une fois. La difficulté n'était pas le rendu mais le **voisinage** : ce code supprime des fichiers dans un dossier que le joueur possède. Trois règles en découlent — ne jamais écrire dans un dossier qu'on n'a pas créé, ne supprimer que ce qu'on a écrit, ne rien faire quand rien n'a changé — et la première n'est pas théorique : **un export du joueur a été écrasé dans cette même session**, dans un dossier que git n'indexe pas. Un défaut trouvé en relecture : l'activation automatique **reprenait au joueur une décision qu'il venait de prendre**, en recochant à chaque démarrage un pack qu'il avait décoché. **11.1** : de vrais items, réellement enregistrés dans le registre du jeu depuis `blueprint/content/items/*.json` — `/give` les connaît, une recette peut les utiliser. Trois contraintes assumées et nommées, parce qu'elles viennent de Minecraft et non du mod : les registres **gèlent** avant qu'un monde existe (donc des fichiers lus au démarrage, donc un redémarrage pour ajouter un item), le mod et les mêmes fichiers **des deux côtés** en multijoueur, et les textures par un **resource pack** (11.2). Le risque dominant n'était pas l'item manquant mais le **jeu qui ne démarre pas** : un identifiant invalide fait lever Minecraft avant l'écran titre, sans nommer le fichier fautif. Et `Map.copyOf`, qui ne préserve pas l'ordre d'insertion, a repiégé le projet exactement comme en 10.5 — ici l'ordre décide des identifiants numériques du réseau, donc un ordre instable aurait permuté les items d'un monde existant, en silence |

**Feuille de route éditeur (ordre recommandé)** :
1. **5.2b** littéraux inline (éditer les valeurs sur le nœud) → 2. **5.6a** annuler/rétablir (avant les grosses features, tout naît annulable) → 3. **5.9** éditer/enregistrer/tester un VRAI blueprint en solo (`Ctrl+S`, la story qui rend l'éditeur utile) → 4. **5.6b** barre d'outils + compilation à la volée + diagnostics cliquables → 5. **5.5** panneau des variables + nœuds var/get-set (⚠ touche `core`) → 6. **5.8** copier/coller/dupliquer via BScript (⚠ touche `core/script`) → 7. **5.10** panneau de détails → 8. **5.4b** palette récents/favoris/catégories/Espace → 9. **5.2c** sélecteurs riches (item, bloc, position) → 10. **5.11** vue script → 11. **5.7** confort (commentaires, alignement, minimap, thème JSON).

**Les neuf épics du PRD initial sont livrés.** Ce qui reste tient dans la liste
ci-dessous et dans le v1.1 consigné story par story (sucre BScript 4.2b, patchs par
opération et multi-éditeur 6.3, processeur d'annotations 8.1, corps BScript de
datapack 8.2).

**L'épic 10 (interfaces graphiques) est livré** : **seize** stories, de la structure de
données au concepteur à la souris, jusqu'aux listes défilantes, aux champs de saisie et
au HUD permanent. Il ouvre un second type de document éditable dans le produit — c'est
un épic, pas une story, et il a été découpé comme tel.

Les deux dernières n'étaient pas au plan, et viennent toutes deux de l'usage réel.
**10.10** : tout se plaçait à la main, rien ne suivait la taille d'écran, le style se
recopiait partout — elle a remplacé la remontée par élément par une passe descendante sur
tout l'arbre, le seul changement de l'épic qui touche à ce que la 10.1 avait posé.
**10.11** : on dessinait sur un timbre-poste sans pouvoir s'en approcher, et un élément
posé sur un grand canevas partait hors écran chez les autres joueurs. Elle apporte le zoom
et le déplacement de vue, un canevas de 1920×1080, des panneaux repliables — et l'ancre
automatique, sans laquelle le grand canevas aurait été un piège.
**10.12** : ce qu'il fallait pour des menus *complets* plutôt que des maquettes de
boutons — une infobulle sur n'importe quel élément, du texte qui revient à la ligne, un
style nommé que le graphe bascule en cours de partie (c'est ainsi qu'on fait des onglets
sans dupliquer les éléments), et le déclic qu'un bouton du jeu fait toujours.
**10.13** : le panneau défilant, écarté du périmètre de la 10.12 avec une raison explicite
puis demandé — une page de réglages ou un règlement tient enfin dans un écran au lieu
d'être découpé en « suivant ». Le décalage est appliqué **dans la passe de disposition
unique**, seule façon d'être sûr que le clic et le dessin s'accordent.

**L'épic 11 (contenu déclaré) est ouvert.** Il répond à une demande simple à énoncer et
lourde à tenir : créer de *vrais* blocs et items, pas des items vanilla renommés. J'avais
recommandé l'inverse ; la demande a été réaffirmée, et l'épic la prend telle quelle. Ce
qui rendait la chose coûteuse n'a pas disparu pour autant — c'est écrit en tête de la
story 11.1, en trois contraintes, dont celle qui décide de toute la forme de l'épic : les
registres du jeu **gèlent** avant qu'un monde soit chargé, si bien qu'un blueprint, qui
vit dans une sauvegarde, ne peut structurellement pas enregistrer un item. Les définitions
sont donc des fichiers sur le disque, lus au démarrage.

**Les 73 stories du projet sont closes**, chacune avec son gate PASS.

> **Relecture finale** : [`rapport-de-fin.md`](rapport-de-fin.md) — l'état complet, ce
> que la QA a réellement trouvé, ce qui reste, et ce que le harnais ne peut pas garantir.

## Prochaine action : la session en jeu

> **Plan de session** : [`session-de-verification.md`](session-de-verification.md) — les
> trente-huit points restants, groupés par ce qu'il faut ouvrir plutôt que par numéro de story,
> avec des cases à cocher. Le nécessaire est déjà en place dans `run/` : exemples, pack
> d'images, configuration.

Tout ce qui se vérifie sans yeux l'est déjà : **996 tests headless** (build vert) et
`./gradlew runGametest` (**17 tests** dans un vrai serveur). **Il ne reste que le visuel
et l'ergonomie.** À regarder, dans l'ordre, en une seule session :

| # | À vérifier | Comment |
|---|---|---|
| V1 | L'éditeur s'ouvre et se lit | `F6`, puis `/blueprint-edit demo` — grille, nœuds, liens, minimap, 60 fps |
| V2 | Éditer un vrai blueprint | `/blueprint-edit create essai` → poser deux nœuds, câbler, littéral, `Ctrl+S` : « enregistré », ● part **et ne revient pas** |
| V3 | Le confort | palette `Espace`, `Ctrl+Z/Y`, `Ctrl+C/V`, `Q`, `C`, `Ctrl+F`, vue script, panneau détails |
| V4 | Clavier seul (U5) | flèches entre nœuds, `Entrée` sur un littéral |
| V5 | Daltonisme (NFR11) | les cinq formes de pins se distinguent d'un coup d'œil |
| V6 | Débogueur (9.1a/9.1b) | bouton *Déboguer*, `B` sur un nœud, déclencher : surlignage + valeurs, `F10`, `F5` |
| V7 | Profileur (9.2) | `/blueprint profile <id> on`, déclencher, `show` puis `export` |
| V8 | Persistance (VERIFY-005) | **Automatisé** — gametest persistenceGivesBackWhatItTook : plus rien à voir en jeu |
| V9 | Fantômes (8.3) | **Automatisé** — gametest aGhostNodeSurvivesSaveAndLoadWithoutLosingAnything : plus rien à voir en jeu |
| V10 | Datapack (8.2) | `shout_twice` dans la palette ; modifier son JSON puis `/reload` |
| V11 | Guide (9.5) | suivre `getting-started.md` §3 à la lettre, sans rien savoir d'autre |
| V12 | Multijoueur (6.2/6.3) | serveur dédié : édition à deux, verrou optimiste, joueur sans permission → lecture seule |
| V13 | Survol et liens (5.12) | poser la souris sur un pin, un nœud fauté, un fantôme, un bouton : l'infobulle explique ; cliquer un fil → halo, `Suppr` le retire, `Ctrl+Z` le remet ; pastille de permission et losange de conversion visibles |
| V14 | Défilement des panneaux (5.12) | un nœud à douze pins et un blueprint à vingt variables : molette dans chaque panneau, curseur visible, rien d'inatteignable |
| V15 | Boucles et listes (7.1b/7.8) | **Automatisé** — gametest aForEachLoopVisitsEveryItemAndStops : plus rien à voir en jeu |
| V16 | Bibliothèque en monde réel (7.3/7.4/7.5) | poser un bloc, jouer un son, faire apparaître une entité, lire l'heure : les nœuds monde/entité/temps agissent vraiment |
| V17 | Commande de blueprint (7.7) | **Automatisé** — gametest theBpcCommandTriggersItsBlueprint : plus rien à voir en jeu |
| V18 | Quotas et audit (9.3) | `maxNodes: 5` dans la config puis redémarrage → l'éditeur refuse le sixième nœud ; exécuter un nœud ADMIN → ligne `blueprint-audit` avec le nom du joueur |
| V19 | Nœud annoté et compat (8.1/8.4) | **Automatisé** — gametest theAnnotatedNodeOfTheTestModIsRegisteredWithItsDefaults : plus rien à voir en jeu |
| V20 | Gestes d'Unreal (5.13) | clic droit sur un nœud, un pin, un fil, le vide : quatre menus différents ; promouvoir un pin en variable ; **lâcher un nœud sur un fil** → halo vert puis insertion, `Ctrl+Z` défait tout d'un coup |
| V21 | Aspect d'Unreal (5.13) | coins arrondis, ombre portée, dégradé d'en-tête, pictogramme de catégorie, halo de sélection ; en débogage, les billes coulent sur les fils exec parcourus |
| V22 | Sous-catégories (5.14) | clic droit à vide : Événements ▸ Joueur/Monde/Serveur, Variables, Contrôle du flux ▸ Branchements/Boucles… ; replier une parente replie ses enfants, les comptes incluent la descendance |
| V23 | Bibliothèque élargie (7.9) | signal entre deux blueprints, particules privées à un seul joueur, requêtes d'entités et lecture de l'heure, dégâts subis en combat |
| V24 | Les cinq derniers (7.10) | `has_item` sur une clé, un score visible dans l'affichage latéral, un message cliquable, `entity/looking_at` sur un bloc visé, une barre de boss qui ne s'empile pas |

| V35 | Éléments riches (10.8) | poser une **liste**, un **champ de saisie**, un **emplacement**, une **case** et un **curseur** ; alimenter la liste par `gui/set_lines` → les lignes s'affichent, la molette défile, ce qui dépasse est **découpé** (rien ne déborde sur le reste du menu), le curseur de défilement se voit ; cliquer la troisième ligne → le graphe reçoit **l'indice 2**, et toujours 2 **après avoir défilé** ; taper dans le champ → un caractère hors filtre est refusé à la frappe ; `Entrée` valide, `Échap` relâche le champ **avant** de fermer l'écran ; `gui/set_item` affiche un objet avec son nombre ; la case bascule, le curseur s'aligne sur son pas ; un **aperçu d'entité** (`minecraft:pig`) montre la créature qui tourne, et ouvrir/fermer le menu vingt fois ne fait pas saccader — le modèle est mis en cache |
| V34 | Quotas et clavier (10.6) | sur un serveur dont `blueprint/config.json` fixe `maxElementsPerScreen` à 3, l'éditeur **signale le dépassement pendant qu'on dessine**, pas seulement à l'enregistrement ; `/blueprint examples` → ouvrir `guichet`, `Ctrl+S`, lancer la commande câblée : le menu s'ouvre, « Prendre un jeton » incrémente le compteur affiché ; **`Tab` parcourt les deux boutons, `Entrée` active** — le bouton ciblé se voit ; `Échap` ferme ; abaisser `maxElementsPerScreen` à 3 dans `blueprint/config.json`, redémarrer, réimporter → **refus nommant la borne**, pas un écran vide en jeu ; vérifier que les bordures des éléments par défaut se distinguent bien du fond |
| V33 | Onglets dans la barre d'outils | les onglets **Graphe / Écrans** sont dans la barre du haut, entre le titre et les boutons — plus de seconde bande sous elle ; ils ne bougent pas quand le titre gagne son « ● » de modification ; un identifiant long ne passe pas dessous ; cliquer un onglet ne déclenche aucune action de la barre ; le canevas et le concepteur ont récupéré les treize pixels |
| V32 | Nœuds élargis et aérés | ouvrir un graphe chargé : les champs de valeur montrent **une dizaine de caractères** au lieu de cinq, ils sont détachés les uns des autres (on voit à quelle entrée appartient chacun), aucun libellé ne mord sur un champ ; un pin booléen au nom long (`through_fluids` sur `world/raycast`) s'affiche **en entier** ; le titre est centré dans son bandeau ; un `.bp` importé sans positions se dispose sans que deux nœuds se touchent |
| V25 | Concepteur d'écrans (10.2) | onglet **Écrans**, créer un menu, poser un panneau puis deux boutons dedans, les traîner (guides jaunes à l'accroche), redimensionner par les poignées, renommer dans le panneau — un doublon vire au rouge **pendant** la frappe ; `Ctrl+Z` défait le dernier geste même après être repassé par l'onglet Graphe |
| V26 | Écran en jeu (10.3) | ouvrir un menu conçu : il s'affiche, s'adapte au redimensionnement de la fenêtre ET au GUI scale (essayer 1 puis 4) ; nommer une texture absente → damier magenta avec son nom, le reste de l'écran intact ; Échap ferme ; désactiver le blueprint pendant que le menu est ouvert → il se referme tout seul |
| V27 | Boutons vivants (10.4) | un menu de boutique : cliquer « acheter » déclenche le graphe ; masquer un bouton depuis le graphe le rend VRAIMENT incliquable ; un compteur d'or câblé sur `server_tick` ne fait pas ramer (le diff n'envoie rien quand rien ne change) ; désactiver le blueprint referme le menu ouvert |
| V28 | HUD permanent (10.9) | `hud/show` : le bandeau s'affiche et **on continue de jouer** — marcher, frapper, ouvrir son inventaire ; deux HUD à la fois ; **F7** les retire tous ; désactiver le blueprint retire le sien |
| V31 | Liaison de données (10.7) | lier une étiquette à une variable `argent` avec le format « Or : %s » depuis le panneau (la variable se **choisit**, elle ne se tape pas) ; ouvrir l'écran → il montre déjà le **défaut** de la variable, sans qu'aucun graphe n'ait tourné ; changer la variable puis `gui/refresh` → le texte suit ; **sans** `gui/refresh`, rien ne bouge (c'est voulu) ; forcer par `gui/set_text` → tient jusqu'au rafraîchissement suivant ; lier une barre à `pv` avec mini 0 / maxi 20 ; renommer la variable dans l'éditeur → **erreur** de diagnostic, pas un écran vide |
| V30 | Packs d'images (10.5) | copier `docs/examples/packs/ma_boutique/` dans `blueprint/scripts/`, `/blueprint-packs reload` → « 1 pack chargé » ; `/blueprint import boutique.bp` puis ouvrir l'écran → le fond et les boutons portent les images ; **renommer le dossier** et recharger → damiers magenta portant « pack ma_boutique absent », le reste du menu intact et cliquable ; remettre le dossier, recharger **sans fermer le menu** → les images reviennent ; déposer un dossier au nom majuscule et un PNG de 4000 px → tous deux écartés et **nommés** dans la commande, le bon pack chargeant quand même |
| V43 | Item habillé (11.2) | déposer `rubis.png` (16×16) à côté de `rubis.json` puis **redémarrer** : `resourcepacks/blueprint_content/` apparaît **coché tout seul**, et `/give @s blueprint:rubis` donne un objet **portant l'image** — en main, dans l'inventaire, jeté au sol ; relancer **sans rien toucher** → aucun rechargement de ressources (le pack n'est pas réécrit) ; supprimer le JSON et redémarrer → plus d'item et **plus aucun fichier `rubis`** dans le pack ; **décocher** le pack dans Options → Packs de ressources et redémarrer → **il reste décoché**, c'est le point, et `/blueprint-packs` le dit au lieu de le défaire ; remplacer le PNG par un fichier texte renommé → l'item s'enregistre quand même et `/blueprint content` le marque **sans image** |
| V42 | Items déclarés (11.1) | créer `blueprint/content/items/rubis.json` — `{"name": "Rubis", "stackSize": 16, "rarity": "rare"}` — puis **redémarrer** : les registres du jeu gèlent au démarrage, il n'existe aucun rechargement possible et c'est ce qui décide de toute la forme de l'épic 11 ; `/blueprint content` liste `blueprint:rubis` ; `/give @s blueprint:rubis 8` → l'objet arrive, **son nom est « Rubis » en bleu**, il ne s'empile pas au-delà de 16, et il s'affiche en **damier magenta** — attendu tant que la 11.2 n'est pas là, l'item existe bel et bien ; puis déposer un `Mon Item.json` et un JSON à la virgule en trop et redémarrer → **le jeu démarre** (c'est le point), `rubis` est toujours là, et `/blueprint content` affiche les deux refus **en rouge** avec leur nom de fichier |
| V41 | Reflet sur disque (10.16) | éditer un blueprint, `Ctrl+S`, puis regarder `blueprint/exports/<id>.bp` **sans rien exporter** : il contient la modification, et il suit à chaque enregistrement ; supprimer le blueprint → **le fichier reste** (c'est souvent la dernière copie) ; `autoExport: false` dans `blueprint/config.json` puis redémarrage → le fichier ne bouge plus |
| V40 | Bancs d'essai (10.15) | `/blueprint import banc_ecran` puis `/blueprint run banc_ecran` : **110 éléments** s'affichent d'un coup — les onze types, trois panneaux défilants (vertical, horizontal, les deux), dix-huit paragraphes qui reviennent à la ligne, des infobulles partout. Le menu **s'ouvre** (c'est le paquet réseau qui est en jeu), il **ne rame pas**, et le HUD `bandeau` s'affiche à côté. Ouvrir le même écran dans le concepteur : il reste **maniable** au zoom et au déplacement. Puis `/blueprint import banc_graphe`, `F6` : **361 nœuds**, la minimap montre la chaîne entière, le déplacement de vue et le zoom restent fluides, `Ctrl+F` retrouve un nœud, et la vue Script affiche le tout sans broncher |
| V39 | Page qui se lit (10.14) | `/blueprint examples` → `reglement`, `Ctrl+S`, `/blueprint run reglement` : les cinq règles **reviennent à la ligne** (aucune coupée au milieu d'un mot), le panneau **défile** à la molette et au clavier, les deux boutons ont leur **infobulle**, « Haut de page » remonte d'un coup ; ouvrir le même écran dans le concepteur montre exactement la même chose ; sur un écran chargé, le concepteur reste **fluide** au déplacement de la souris — c'est la passe de disposition qui n'est plus refaite huit fois par image |
| V38 | Panneau défilant (10.13) | poser un panneau de 100 de haut, le passer en **colonne** et cocher **Défilant**, y déposer huit boutons → la molette au-dessus du panneau le fait défiler, le contenu est **coupé net au bord du cadre** et ne déborde pas sur le reste du menu ; **cliquer là où un bouton sorti du cadre se trouverait n'active rien** ; défiler à fond puis en arrière → il s'arrête aux deux bouts, sans vide ; mettre une **liste** dans le panneau → la molette sur la liste défile la liste, à côté d'elle le panneau ; `gui/set_scroll` à 0 le ramène en haut et `gui/set_scroll_x` à 0 à gauche (les deux dans le même tick font bien les deux) ; passer la hauteur du panneau en **ajuster** → un **avertissement** dit qu'il ne défilera jamais ; dans le concepteur, la molette sur le panneau le fait défiler (elle ne zoome plus), et **sélectionner dans la liste des calques un enfant sorti du cadre le ramène sous les yeux** ; `Ctrl+S`, `/blueprint export`, relire le `.bp` (`scroll: true` lisible), réimporter → identique. Le **curseur** se voit à droite du cadre, sa taille dit ce qui reste, on peut le **tirer** (il suit le doigt sans sauter) et cliquer ailleurs sur la glissière y saute ; au clavier `Page↑`/`Page↓`, `Origine`/`Fin`, `Ctrl+↑`/`Ctrl+↓`, et **`Tab` ramène dans le cadre ce qu'il atteint** — les flèches nues déplacent toujours le personnage ; passer le panneau en **V+H** avec des enfants qui dépassent des deux côtés → **deux curseurs**, chacun s'arrêtant avant le coin de l'autre, `Maj`+molette et `Ctrl+←`/`Ctrl+→` pour l'horizontale, `Origine` qui ramène **les deux** axes, et un panneau réglé sur **H seul** qui répond à la molette **sans** `Maj` |
| V37 | Menus complets (10.12) | poser une étiquette large, y coller un paragraphe et cocher **Retour ligne** → le texte se coupe aux mots et se centre ; réduire le cadre → les lignes en trop sont **coupées**, rien ne déborde sur le voisin ; donner une **infobulle** à un bouton, à une étiquette et à un bouton **désactivé** → les trois s'affichent au survol en jeu (et dans le concepteur), y compris le grisé ; une infobulle en `#ma.cle` s'affiche traduite ; **cliquer un bouton fait le bruit d'un bouton du jeu**, un bouton grisé reste muet ; câbler `gui/set_style` sur deux styles nommés (« onglet actif » / « onglet inactif ») → les boutons changent d'aspect sans être dupliqués ; `gui/set_tooltip` change l'explication en cours de partie ; dans le concepteur, `G` fait apparaître et disparaître la **grille**, `F1` liste les raccourcis, et **un seul élément sélectionné** se centre dans son cadre au pavé numérique 5 ; `Ctrl+S`, `/blueprint export`, relire le `.bp` (`@tip`, `@tipkey`, `wrap` lisibles), réimporter → identique |
| V36 | Zoom et grand canevas (10.11) | onglet **Écrans** : le canevas s'ouvre en **1920×1080 cadré entier**, pas tronqué ni minuscule dans un coin ; molette vers l'avant en visant un bouton → **il reste sous le curseur** en grossissant ; bouton du milieu (ou `Espace` + gauche) → la vue suit la souris et **ne peut pas perdre le canevas** (pousser à fond dans une direction, le canevas est toujours là) ; `F` recadre, `Ctrl+0` revient à 1:1 ; zoomé à 4×, poser un bouton depuis la palette → il atterrit **au centre de ce qu'on voit**, et ses poignées s'attrapent aussi facilement qu'à 1× ; poser un bouton en bas à droite puis basculer sur **320×180** → toujours en bas à droite et **dans l'écran** ; `Tab` replie les deux panneaux (le canevas double) et **cliquer là où était la palette atteint le canevas**, pas un panneau invisible ; zoomer à 2× sur une étiquette au texte tronqué → la troncature est **la même** qu'en jeu (`/blueprint open` pour comparer) ; un **aperçu d'entité** et un **emplacement d'objet** restent dans leur cadre à tous les zooms |
| V29 | Dispositions et styles (10.10) | poser un panneau, le passer en **colonne**, y déposer trois boutons : ils se rangent seuls, espacés, **sans toucher une coordonnée** ; en insérer un quatrième au milieu → les suivants descendent tout seuls ; le tirer ailleurs dans la colonne le **réordonne** (et ses poignées de largeur ont disparu, elles ne feraient rien) ; basculer 320×180 → 960×540 dans la barre du bas → les proportions tiennent ; créer un style depuis un bouton, l'appliquer aux trois autres, changer sa couleur → les quatre changent ; un panneau en `ajuster` épouse ses enfants ; `Ctrl+S` puis `/blueprint export`, relire le `.bp` (dispositions et bloc `styles` lisibles), réimporter → écran identique |

**Déjà clos** : VERIFY-001/002/003 (session du 2026-08-02, épic 1) et VERIFY-004
(démo ping/pong). **Rien à voir en jeu** pour VERIFY-8.5 : la garde de surface d'API
s'exerce à chaque construction.

## Ordre de lecture recommandé

1. `brief.md` — pourquoi
2. `prd.md` §2 — quoi (exigences)
3. `architecture.md` §2 — comment (vue d'ensemble)
4. `extension-api.md` §0 — l'intégration tierce en une minute
5. Le reste selon le rôle
