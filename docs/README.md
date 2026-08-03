# Documentation Blueprint — dossier BMAD

Dossier de planification suivant la **méthode BMAD** (Analyst → PM → Architect → UX → SM → Dev → QA).

## Index

| Document | Agent | Rôle |
|---|---|---|
| [`getting-started.md`](getting-started.md) | — | **Guide joueur** : premier blueprint en dix minutes, raccourcis, dépannage |
| [`examples/`](examples/README.md) | — | **Six blueprints d'exemple** prêts à charger : `/blueprint examples` |
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
| 10 | **Interfaces graphiques** | 10.1 → 10.8 | **Rédigé, non commencé** — concepteur d'écrans à la souris, boutons câblés aux blueprints. Ouvert à la demande de l'utilisateur ; **10.1 (modèle) est la prochaine à prendre** |

**Feuille de route éditeur (ordre recommandé)** :
1. **5.2b** littéraux inline (éditer les valeurs sur le nœud) → 2. **5.6a** annuler/rétablir (avant les grosses features, tout naît annulable) → 3. **5.9** éditer/enregistrer/tester un VRAI blueprint en solo (`Ctrl+S`, la story qui rend l'éditeur utile) → 4. **5.6b** barre d'outils + compilation à la volée + diagnostics cliquables → 5. **5.5** panneau des variables + nœuds var/get-set (⚠ touche `core`) → 6. **5.8** copier/coller/dupliquer via BScript (⚠ touche `core/script`) → 7. **5.10** panneau de détails → 8. **5.4b** palette récents/favoris/catégories/Espace → 9. **5.2c** sélecteurs riches (item, bloc, position) → 10. **5.11** vue script → 11. **5.7** confort (commentaires, alignement, minimap, thème JSON).

**Les neuf épics du PRD initial sont livrés.** Ce qui reste tient dans la liste
ci-dessous et dans le v1.1 consigné story par story (sucre BScript 4.2b, patchs par
opération et multi-éditeur 6.3, processeur d'annotations 8.1, corps BScript de
datapack 8.2).

**L'épic 10 (interfaces graphiques) est rédigé et non commencé** : **huit** stories, de la
structure de données au concepteur à la souris, jusqu'aux listes défilantes et aux
champs de saisie. Il ouvre un second type de document éditable dans le produit — c'est un
épic, pas une story, et il est découpé comme tel.

> **Relecture finale** : [`rapport-de-fin.md`](rapport-de-fin.md) — l'état complet, ce
> que la QA a réellement trouvé, ce qui reste, et ce que le harnais ne peut pas garantir.

## Prochaine action : la session en jeu

Tout ce qui se vérifie sans yeux l'est déjà : suites headless (build vert) et
`./gradlew runGametest` (5 tests dans un vrai serveur). **Il ne reste que le visuel et
l'ergonomie.** À regarder, dans l'ordre, en une seule session :

| # | À vérifier | Comment |
|---|---|---|
| V1 | L'éditeur s'ouvre et se lit | `F6`, puis `/blueprint-edit demo` — grille, nœuds, liens, minimap, 60 fps |
| V2 | Éditer un vrai blueprint | `/blueprint-edit create essai` → poser deux nœuds, câbler, littéral, `Ctrl+S` : « enregistré », ● part **et ne revient pas** |
| V3 | Le confort | palette `Espace`, `Ctrl+Z/Y`, `Ctrl+C/V`, `Q`, `C`, `Ctrl+F`, vue script, panneau détails |
| V4 | Clavier seul (U5) | flèches entre nœuds, `Entrée` sur un littéral |
| V5 | Daltonisme (NFR11) | les cinq formes de pins se distinguent d'un coup d'œil |
| V6 | Débogueur (9.1a/9.1b) | bouton *Déboguer*, `B` sur un nœud, déclencher : surlignage + valeurs, `F10`, `F5` |
| V7 | Profileur (9.2) | `/blueprint profile <id> on`, déclencher, `show` puis `export` |
| V8 | Persistance (VERIFY-005) | `/blueprint demo`, **redémarrer le monde**, « Persistance : … » au log et ping sans réimport |
| V9 | Fantômes (8.3) | retirer le testmod du dossier `mods` : `/blueprint info` nomme le mod, l'éditeur montre le fantôme ; le remettre restaure tout |
| V10 | Datapack (8.2) | `shout_twice` dans la palette ; modifier son JSON puis `/reload` |
| V11 | Guide (9.5) | suivre `getting-started.md` §3 à la lettre, sans rien savoir d'autre |
| V12 | Multijoueur (6.2/6.3) | serveur dédié : édition à deux, verrou optimiste, joueur sans permission → lecture seule |
| V13 | Survol et liens (5.12) | poser la souris sur un pin, un nœud fauté, un fantôme, un bouton : l'infobulle explique ; cliquer un fil → halo, `Suppr` le retire, `Ctrl+Z` le remet ; pastille de permission et losange de conversion visibles |
| V14 | Défilement des panneaux (5.12) | un nœud à douze pins et un blueprint à vingt variables : molette dans chaque panneau, curseur visible, rien d'inatteignable |
| V15 | Boucles et listes (7.1b/7.8) | un graphe qui parcourt une liste de trois noms et envoie un message à chacun : `Pour chaque` + nœuds de liste, sans boucle infinie ni carburant épuisé |
| V16 | Bibliothèque en monde réel (7.3/7.4/7.5) | poser un bloc, jouer un son, faire apparaître une entité, lire l'heure : les nœuds monde/entité/temps agissent vraiment |
| V17 | Commande de blueprint (7.7) | `/bpc <nom>` déclenche le graphe qui la déclare, avec suggestion du nom à la frappe |
| V18 | Quotas et audit (9.3) | `maxNodes: 5` dans la config puis redémarrage → l'éditeur refuse le sixième nœud ; exécuter un nœud ADMIN → ligne `blueprint-audit` avec le nom du joueur |
| V19 | Nœud annoté et compat (8.1/8.4) | `shout` dans la palette avec ses défauts « salut » et 1 ; `compat/testmod_greet` présent et « Intégration « blueprint_testmod » chargée » au log |
| V20 | Gestes d'Unreal (5.13) | clic droit sur un nœud, un pin, un fil, le vide : quatre menus différents ; promouvoir un pin en variable ; **lâcher un nœud sur un fil** → halo vert puis insertion, `Ctrl+Z` défait tout d'un coup |
| V21 | Aspect d'Unreal (5.13) | coins arrondis, ombre portée, dégradé d'en-tête, pictogramme de catégorie, halo de sélection ; en débogage, les billes coulent sur les fils exec parcourus |
| V22 | Sous-catégories (5.14) | clic droit à vide : Événements ▸ Joueur/Monde/Serveur, Variables, Contrôle du flux ▸ Branchements/Boucles… ; replier une parente replie ses enfants, les comptes incluent la descendance |
| V23 | Bibliothèque élargie (7.9) | signal entre deux blueprints, particules privées à un seul joueur, requêtes d'entités et lecture de l'heure, dégâts subis en combat |
| V24 | Les cinq derniers (7.10) | `has_item` sur une clé, un score visible dans l'affichage latéral, un message cliquable, `entity/looking_at` sur un bloc visé, une barre de boss qui ne s'empile pas |

**Déjà clos** : VERIFY-001/002/003 (session du 2026-08-02, épic 1) et VERIFY-004
(démo ping/pong). **Rien à voir en jeu** pour VERIFY-8.5 : la garde de surface d'API
s'exerce à chaque construction.

## Ordre de lecture recommandé

1. `brief.md` — pourquoi
2. `prd.md` §2 — quoi (exigences)
3. `architecture.md` §2 — comment (vue d'ensemble)
4. `extension-api.md` §0 — l'intégration tierce en une minute
5. Le reste selon le rôle
