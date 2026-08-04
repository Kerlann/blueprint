# Session de vérification en jeu

Tout ce qui se vérifie sans yeux l'est déjà : **1188 tests headless** et **20 gametests**
dans un vrai serveur. Ce document couvre ce qui reste — **le visuel et l'ergonomie**, que
seule une personne devant l'écran peut juger.

Cinq points ont quitté cette liste parce qu'ils sont devenus des gametests : la
persistance, les nœuds fantômes, les boucles `for_each`, la commande `/bpc` et le nœud
annoté du mod de test. Ils ne demandent plus de regard.

**Comptez une heure.** L'ordre importe : chaque bloc réutilise ce que le précédent a
construit, et repartir de zéro à chaque point ferait perdre le tiers du temps.

---

## Avant de commencer

Le nécessaire est déjà en place dans `run/` :

- `run/blueprint/exports/` — les huit exemples, la **démonstration « banque »**, la démo, et **deux bancs d'essai** (`banc_ecran`, `banc_graphe`). Tout le dossier se régénère d'une commande : `./gradlew :core:test --tests "*StressBlueprintTest" -Dblueprint.regenDocs=true`
- `run/blueprint/scripts/ma_boutique/` — le pack d'images (fond, bouton, survol)
- `run/blueprint/content/` — **un item et un bloc déclarés**, prêts à l'emploi — `rubis`, `granit_bleu`, et la monnaie de la banque (`piece`, `lingot`, `distributeur`) — avec leurs PNG. Régénérés d'une commande : `./gradlew :core:test --tests "*ContentExamplesTest" -Dblueprint.regenDocs=true`
- `run/blueprint/config.json` — configuration d'une version antérieure, **volontairement** :
  elle vérifie au passage que les quotas neufs reprennent leurs défauts

```bash
./gradlew runClient
```

Puis en jeu : créer un monde en **créatif**, tricheurs activés.

> Notez ce qui cloche **au fil de l'eau**, sans vous arrêter pour corriger. Une session
> interrompue à chaque défaut ne finit jamais, et les défauts de la fin sont ceux qu'on
> ne voit jamais.

---

## Bloc 1 — L'éditeur de graphe (13 points, ~20 min)

`/blueprint demo` puis `F6`.

| | À vérifier | Ce qui doit se produire |
|---|---|---|
| ☐ V1 | L'éditeur s'ouvre et se lit | Grille, nœuds, liens courbes, minimap. Fluide au déplacement et au zoom. |
| ☐ V32 | **Nœuds élargis** | Les champs de valeur montrent une dizaine de caractères, pas cinq. Deux champs voisins sont **détachés** — on voit à quelle entrée appartient chacun. Aucun libellé ne mord sur un champ. Un pin booléen au nom long (`through_fluids` sur `world/raycast`) s'affiche **en entier**. |
| ☐ V33 | **Onglets dans la barre** | « Graphe » et « Écrans » sont dans la barre du haut, entre le titre et les boutons. Ils **ne bougent pas** quand le titre gagne son ● de modification. Un identifiant long ne passe pas dessous. |
| ☐ V21 | Aspect | Châssis arrondi, en-tête en dégradé par catégorie, ombre portée, pictogramme de catégorie, pastille de permission. |
| ☐ V48 | **L'éditeur à l'usage (12.1 → 12.5)** | `Ctrl+Espace` dans le graphe : le menu s'ouvre sur son **index replié** — une trentaine d'en-têtes, pas 184 nœuds — et **aucun** bloc « récents » ni « favoris ». Descendre jusqu'à la vingtième catégorie et la déplier : **la vue ne remonte pas**, l'en-tête cliqué reste sous le curseur (c'est le point de la 12.3). Saisir la **barre de défilement** à la souris : elle fait six pixels et se prend du premier coup. Tirer un fil depuis un pin puis ouvrir le menu → seuls les nœuds compatibles ; **décocher « Contextuel »** → la liste entière revient. Puis, sur le graphe : poser une **lecture de variable**. Ce doit être une **capsule** — extrémités **rondes** et non en pointe, **aucun coin sombre** qui dépasse derrière, **aucune arête verticale** au milieu — dont la couleur vient du **type** ; en poser deux de types différents (un booléen, un nombre) → on les distingue **sans lire leur nom**. La sélectionner : le **halo épouse la capsule**. Son fil doit partir exactement du **milieu** de son bord droit, et le clic l'attraper au même endroit. |
| ☐ V2 | Éditer pour de vrai | `/blueprint create essai` → poser deux nœuds, câbler, taper un littéral, `Ctrl+S`. « Enregistré », le ● part **et ne revient pas**. |
| ☐ V3 | Le confort | Palette `Espace`, `Ctrl+Z`/`Ctrl+Y`, `Ctrl+C`/`Ctrl+V`, `Q` aligner, `C` commenter, `Ctrl+F` chercher, vue Script, panneau Détails. |
| ☐ V20 | Gestes d'Unreal | Clic droit sur un nœud / un pin / le vide → trois menus **différents**. Promouvoir un pin en variable. Lâcher un nœud **sur un fil** → il s'y insère. |
| ☐ V13 | Survol et liens | Infobulle au survol d'un pin et d'un bouton. Clic sur un lien → il se sélectionne. |
| ☐ V14 | Défilement | Les panneaux Variables et Détails défilent à la molette quand ils débordent. |
| ☐ V22 | Sous-catégories | La palette groupe par catégorie **et** sous-catégorie ; aucun repli ne dépasse douze entrées. |
| ☐ V4 | Clavier seul | Flèches entre nœuds, `Entrée` édite la première valeur, `Ctrl+S`. Sans toucher la souris. |
| ☐ V5 | Daltonisme | Les pins se distinguent **par leur forme** autant que par leur couleur : ▶ exec, ● donnée, ◆ objet, ▦ liste, ✚ dictionnaire. |

---

## Bloc 2 — Les écrans (15 points, ~35 min)

Restez dans le même blueprint, onglet **Écrans**.

| | À vérifier | Ce qui doit se produire |
|---|---|---|
| ☐ V38 | **Panneau défilant** | Un panneau de 100 de haut en **colonne**, **Défilant** coché, huit boutons dedans → la molette au-dessus le fait défiler, le contenu est **coupé net au bord du cadre**. **Cliquer là où un bouton sorti du cadre se trouverait n'active rien.** Défiler à fond dans les deux sens → il s'arrête, sans vide. Une **liste** dans le panneau : la molette sur la liste défile la liste, à côté d'elle le panneau. `gui/set_scroll` à 0 le ramène en haut, `gui/set_scroll_x` à 0 à gauche — et les deux dans le même tick font bien LES DEUX. Passer sa hauteur en **ajuster** → un **avertissement** dit qu'il ne défilera jamais. Dans le concepteur : la molette sur le panneau le fait défiler (elle ne zoome plus), et **sélectionner dans les calques un enfant sorti du cadre le ramène sous les yeux**. Le **curseur de défilement** se voit à droite du cadre, sa taille dit ce qui reste : le **tirer** suit le doigt **sans sauter**, cliquer ailleurs sur la glissière y amène le curseur d'un coup. Au clavier : `Page↑`/`Page↓`, `Origine`/`Fin`, `Ctrl+↑`/`Ctrl+↓` — et **`Tab` ramène dans le cadre l'élément qu'il atteint**. Les flèches **nues** continuent de déplacer le personnage. Passer le panneau en **V+H** avec des enfants qui dépassent des deux côtés : **deux curseurs**, chacun s'arrêtant avant le coin de l'autre ; `Maj`+molette défile à l'horizontale, `Ctrl+←`/`Ctrl+→` aussi, et `Origine` ramène **les deux** axes. Sur un panneau réglé sur **H seul**, la molette **sans** `Maj` le fait quand même défiler. |
| ☐ V37 | **Menus complets** | Une étiquette large, un paragraphe dedans, **Retour ligne** coché → le texte se coupe aux mots ; réduire le cadre → les lignes en trop sont coupées, rien ne déborde. Donner une **infobulle** à un bouton, à une étiquette et à un bouton **désactivé** : les trois s'affichent au survol, le grisé compris. **Cliquer fait le bruit d'un bouton du jeu** ; un bouton grisé reste muet. `gui/set_style` bascule deux styles nommés (« onglet actif » / « inactif ») sans dupliquer les éléments. Dans le concepteur : `G` montre et cache la **grille**, `F1` liste les raccourcis, et **un seul élément** se centre dans son cadre au pavé numérique 5. |
| ☐ V36 | **Zoom et grand canevas** | Le canevas s'ouvre en **1920×1080 cadré entier**. Molette vers l'avant en visant un bouton → **il reste sous le curseur**. Bouton du milieu (ou `Espace` + gauche) → la vue suit la souris ; pousser à fond dans une direction → **le canevas est toujours là**. `F` recadre, `Ctrl+0` revient à 1:1. Zoomé à 4×, poser un bouton depuis la palette → il atterrit **au centre de ce qu'on voit**, et ses poignées s'attrapent aussi facilement qu'à 1×. Poser un bouton en bas à droite, basculer sur **320×180** → toujours en bas à droite et **dans l'écran**. `Tab` replie les deux panneaux (le canevas double) ; cliquer là où était la palette **atteint le canevas**. À 2×, la troncature d'un texte est **la même** qu'en jeu. Un aperçu d'entité et un emplacement d'objet restent dans leur cadre à tous les zooms. |
| ☐ V25 | Concepteur | Créer un écran, poser un panneau puis deux boutons dedans. Les traîner (guides jaunes à l'accroche), redimensionner par les poignées. Renommer : un doublon vire au rouge **pendant** la frappe. `Ctrl+Z` défait, même après un aller-retour par l'onglet Graphe. |
| ☐ V29 | **Dispositions et styles** | Passer le panneau en **colonne** → les boutons se rangent seuls, sans qu'on touche une coordonnée. En insérer un **au milieu** → les suivants descendent. Le tirer ailleurs dans la colonne le **réordonne** (ses poignées de largeur ont disparu : elles ne feraient rien). Basculer 320×180 ↔ 960×540 → les proportions tiennent. Créer un style depuis un bouton, l'appliquer aux autres, changer sa couleur → tous changent. Un panneau en « ajuster » épouse ses enfants. |
| ☐ V34 | **Quotas et clavier** | `Tab` parcourt les boutons, `Entrée` active — le bouton ciblé **se voit**. `Échap` ferme. Les bordures par défaut se distinguent bien du fond. |
| ☐ V26 | Écran en jeu | Ouvrir un menu conçu : il s'affiche, suit le redimensionnement de la fenêtre **et** le GUI scale (essayer 1 puis 4). Nommer une texture absente → damier magenta portant son nom, le reste de l'écran intact. |
| ☐ V27 | Boutons vivants | Cliquer « acheter » déclenche le graphe. Masquer un bouton depuis le graphe le rend **vraiment** incliquable. Désactiver le blueprint referme le menu ouvert. |
| ☐ V31 | Liaison de données | Lier une étiquette à une variable (elle se **choisit** dans la liste, ne se tape pas), format `Or : %s`. À l'ouverture, l'étiquette montre déjà le **défaut** de la variable. Changer la variable puis `gui/refresh` → le texte suit. **Sans** `gui/refresh`, rien ne bouge — c'est voulu. Renommer la variable dans l'éditeur → **erreur** de diagnostic, pas un écran vide. |
| ☐ V35 | **Éléments riches** | Alimenter une liste par `gui/set_lines` → les lignes s'affichent, la molette défile, ce qui dépasse est **découpé** (rien ne déborde). Cliquer la troisième ligne → le graphe reçoit l'**indice 2**, et toujours 2 **après avoir défilé**. Taper dans un champ → un caractère hors filtre est refusé à la frappe ; `Entrée` valide ; `Échap` relâche le champ **avant** de fermer l'écran. `gui/set_item` affiche un objet avec son nombre. Un **aperçu d'entité** (`minecraft:pig`) montre la créature qui tourne — ouvrir/fermer vingt fois ne fait pas saccader. |
| ☐ V41 | **Reflet sur disque** | Éditer un blueprint, `Ctrl+S`, puis regarder `blueprint/exports/<id>.bp` **sans rien exporter** : il contient la modification. Recommencer : il suit. Supprimer le blueprint → **le fichier reste**. Mettre `autoExport: false` dans `blueprint/config.json`, redémarrer, réenregistrer → le fichier ne bouge plus. |
| ☐ V40 | **Bancs d'essai** | `/blueprint import banc_ecran`, `/blueprint run banc_ecran` : **110 éléments** d'un coup — onze types, trois panneaux défilants, dix-huit paragraphes enveloppés, infobulles partout. Le menu **s'ouvre** (c'est le paquet réseau qui est en jeu) et **ne rame pas** ; le HUD `bandeau` s'affiche à côté. Le même écran dans le concepteur reste **maniable** au zoom et au déplacement. Puis `banc_graphe` et `F6` : **361 nœuds**, minimap lisible, vue fluide, `Ctrl+F` qui retrouve, vue Script complète. |
| ☐ V39 | **Page qui se lit** | `/blueprint examples` → `reglement`, `Ctrl+S`, puis `/blueprint run reglement`. La page s'ouvre : les cinq règles **reviennent à la ligne** (aucune coupée au milieu d'un mot), le panneau **défile** à la molette **et** au clavier, les deux boutons ont leur **infobulle** au survol, et « Haut de page » remonte d'un coup. Dans l'éditeur, ouvrir le même écran : le concepteur montre exactement la même chose. |
| ☐ V30 | Packs d'images | Le pack est déjà dans `run/blueprint/scripts/`. `/blueprint-packs` → il est listé. `/blueprint import boutique.bp`, ouvrir l'écran → fond et boutons portent les images. **Renommer le dossier** et `/blueprint-packs reload` → damiers portant « pack ma_boutique absent », le menu reste cliquable. Le remettre, recharger **sans fermer le menu** → les images reviennent. |
| ☐ V28 | HUD permanent | `hud/show` : le bandeau s'affiche et **on continue de jouer** — marcher, frapper, ouvrir son inventaire. Deux HUD à la fois. **F7** les retire tous. Désactiver le blueprint retire le sien. |

---

## Bloc 3 — L'exécution (12 points, ~45 min)

| | À vérifier | Ce qui doit se produire |
|---|---|---|
| ☐ V6 | Débogueur | `/blueprint debug <id> on`, `B` sur un nœud, `F10` pas-à-pas, `F5` continuer. Les billes de flux circulent sur les fils, les valeurs s'affichent. |
| ☐ V7 | Profileur | `/blueprint profile <id> on` puis `show` : les nœuds coûteux ressortent. |
| ☐ V16 | Bibliothèque en monde réel | Un graphe qui lit le monde, requête des entités, donne un objet, écrit un score. |
| ☐ V23 | Bibliothèque élargie | Inventaire, tableau des scores, texte riche, raycast, barre de boss. |
| ☐ V24 | Les cinq derniers | `has_item`, score visible en affichage latéral, message cliquable, `entity/looking_at`, barre de boss qui ne s'empile pas. |
| ☐ V42 | **Items déclarés (11.1)** | `run/blueprint/content/` contient déjà `rubis` et `granit_bleu` — **redémarrer** suffit (les registres gèlent au démarrage, il n'y a pas de rechargement possible). En jeu : `/blueprint content` liste `blueprint:rubis`. `/give @s blueprint:rubis 8` → l'objet arrive, **son nom est « Rubis » en bleu**, il ne s'empile pas au-delà de 16. Il s'affiche en **damier magenta** — c'est attendu tant que la 11.2 n'est pas là, l'item existe bel et bien. Puis déposer un `Mon Item.json` et un JSON avec une virgule en trop, redémarrer : **le jeu démarre**, `rubis` est toujours là, et `/blueprint content` affiche les deux refus **en rouge** avec leur nom de fichier. |
| ☐ V43 | **Item habillé (11.2)** | Le `rubis.png` livré est déjà à côté de son JSON. **Redémarrer** : au lancement, `resourcepacks/blueprint_content/` apparaît, **coché tout seul**, et `/give @s blueprint:rubis` donne un objet **portant l'image** — en main, dans l'inventaire, jeté au sol. Relancer **sans rien toucher** : aucun rechargement de ressources au démarrage (le pack n'est pas réécrit). Supprimer `rubis.json` et redémarrer → plus d'item, et **plus aucun fichier `rubis`** dans le pack. Décocher le pack dans Options → Packs de ressources et redémarrer → **il reste décoché** (c'est le point : le réglage tient), et `/blueprint-packs` le dit. Enfin, remplacer le PNG par un fichier texte renommé → l'item est enregistré mais `/blueprint content` le marque **sans image**, et le jeu démarre. |
| ☐ V44 | **Bloc déclaré (11.3)** | `granit_bleu` est déjà livré — dureté 3, pioche **exigée**, lumière 7, avec sa texture. **Redémarrer**. `/give @s blueprint:granit_bleu` : l'objet montre un **cube**, pas une vignette plate. Le poser : il s'affiche avec sa texture sur les six faces, **éclaire** autour de lui, et fait le bruit de la pierre. Le miner **à la main** : c'est long, et **rien ne tombe** (outil exigé). Le miner **à la pioche** : nettement plus rapide, et **le bloc se ramasse**. En **créatif**, casser ne lâche rien. Enfin, copier `granit_bleu.json` **aussi** dans `items/` et redémarrer → le jeu démarre, l'**item** garde le nom, et `/blueprint content` explique en rouge que le bloc a été écarté. |
| ☐ V45 | **Les touches (11.4)** | Options → Commandes : **huit « Action Blueprint »** y figurent, toutes **non assignées**. En assigner une à `K`. Dans un blueprint, poser **« Une touche Blueprint est pressée »** (palette → Événements → **Commandes du joueur**), laisser le numéro à **1**, câbler un message au joueur, `Ctrl+S`. Presser `K` en jeu → le message arrive. Presser une **autre** touche assignée à l'emplacement 2 → rien (le filtre marche). **Maintenir** `K` trois secondes → **un seul** message, pas soixante. Désactiver le blueprint → la touche ne fait plus rien. |
| ☐ V46 | **Le contenu qui sert (11.5)** | Un graphe avec **« Un joueur utilise un objet »** : câbler sa sortie **item** vers un message. Clic droit avec un `blueprint:rubis` → le message dit `blueprint:rubis` ; clic droit avec une pomme → `minecraft:apple`. Puis **« Un bloc déclaré est posé »** : poser `blueprint:granit_bleu` → le graphe part avec la bonne position ; poser de la pierre → **rien** (c'est voulu, le nom du nœud le dit). Puis **« Un joueur casse un bloc »** → la sortie **block** donne l'identifiant du bloc cassé, pas de l'air. Enfin : `item/create` → `item/with_name` → `player/give_item` : l'objet arrive **renommé**, et celui de la pile d'origine ne l'est pas. |
| ☐ V47 | **La banque (11.9)** | Cliquer **Déposer sans rien taper** → un message « Rien à déposer », le solde ne bouge pas et **aucune pièce ne disparaît** (c'est le défaut de la 11.10, corrigé). Puis : Le contenu est **livré** dans `run/blueprint/content/` — `distributeur`, `piece`, `lingot`. Redémarrer, puis `/blueprint import banque`, `Ctrl+S`, `/blueprint enable blueprint:example/banque`. `/give @s blueprint:distributeur`, le poser, **clic droit** → l'écran s'ouvre ; clic droit sur un **autre** bloc → rien (c'est le point). `/give @s blueprint:piece 64`, taper **40**, **Déposer** → le solde monte de 40 et 40 pièces quittent l'inventaire. Taper **250**, **Retirer** avec un solde de 40 → **message de refus**, solde inchangé. Se donner de quoi monter à 250, **Retirer 250** → **2 lingots et 50 pièces** arrivent, solde à 0. Enfin, demander **1000** pièces qu'on n'a pas en dépôt → seules celles qu'on a partent, et le solde monte d'autant, jamais plus. |
| ☐ V18 | Quotas et audit | Baisser `maxNodes` dans `blueprint/config.json`, redémarrer → le dépassement est **signalé pendant qu'on dessine**. Un nœud ADMIN laisse une trace au log. |

---

## Bloc 4 — Ce qui demande une seconde fenêtre (3 points, ~5 min)

| | À vérifier | Ce qui doit se produire |
|---|---|---|
| ☐ V12 | Multijoueur | Un serveur dédié + deux clients : l'éditeur s'ouvre chez les deux, l'enregistrement du premier est vu du second, un joueur sans permission voit « lecture seule ». |
| ☐ V10 | Datapack | `shout_twice` apparaît dans la palette. Modifier son JSON puis `/reload` → le changement est pris. |
| ☐ V11 | Guide | Suivre `getting-started.md` §3 puis §8 **sans rien savoir d'autre** : le premier blueprint et le premier menu doivent tenir en dix minutes chacun. |

---

## Après la session

Pour chaque défaut noté :

1. S'il est **visuel et isolé** — une couleur, un espacement, un libellé — corrigez-le
   directement et notez-le dans le commit.
2. S'il touche au **comportement**, écrivez d'abord le test qui échoue. Un défaut trouvé
   en jeu et corrigé sans test reviendra, et il reviendra sans qu'on s'en aperçoive.
3. S'il révèle un **manque de conception**, c'est une story — pas un correctif glissé dans
   un commit de finition.

Le tableau des 48 points reste dans [`README.md`](README.md) : cochez-y ce qui est vu.
