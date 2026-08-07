# Blueprint — prise en main

Ce guide s'adresse au **joueur**. Il ne demande pas de savoir programmer, et pas une
ligne de Java. Comptez dix minutes pour votre premier blueprint qui fait quelque chose.

![L'éditeur ouvert sur un graphe de huit nœuds](images/editeur-graphe.png)

*Ce que vous aurez sous les yeux : les nœuds au centre, les variables à gauche, les
informations du blueprint à droite, et la barre de diagnostics tout en bas.*

---

## 1. Qu'est-ce qu'un blueprint ?

Un **graphe** que vous posez en jeu : des **nœuds** reliés par des fils. Un nœud fait une
chose (« envoyer un message », « poser un bloc », « comparer deux nombres »), un fil dit
dans quel ordre — ou quelle valeur va où.

Deux sortes de fils, reconnaissables **à la forme des pins** autant qu'à leur couleur :

- **Exécution** (pins en flèche, fils épais) : l'ordre des actions.
- **Données** (pins ronds, losanges, triangles, anneaux, croix) : les valeurs qui
  circulent. La forme change avec le type, exprès — elle reste lisible en cas de
  daltonisme.

Un blueprint part d'un **événement** (un joueur se connecte, un bloc est cassé, une
commande est tapée…) et s'exécute **côté serveur**.

---

## 2. Ouvrir l'éditeur

| Ce que vous voulez | Ce que vous tapez |
|---|---|
| Lancer le banc de performance | `/blueprint bench` puis `/bpc bench` |
| Créer le vôtre | `/blueprint create mon_premier` |
| Ouvrir le navigateur | Touche **F6** — double-clic pour ouvrir, Ctrl+N pour créer |
| Voir ce qui existe | **F6**, ou `/blueprint edit` — dossiers, création, import |

> **En solo**, tout fonctionne, y compris sans « autoriser les tricheurs ».
> **Sur un serveur**, l'édition demande la permission configurée par l'administrateur ;
> sans elle, l'éditeur s'ouvre en **lecture seule** et vous le dit.

!!! note "📷 Capture à faire — `images/navigateur.png`"

    Le navigateur ouvert avec **F6** : la liste des blueprints, un dossier, et la barre du
    haut. Remplacez ce bloc par :
    `![Le navigateur de blueprints](images/navigateur.png)`

---

## 3. Votre premier blueprint en cinq gestes

1. `/blueprint create bonjour`
2. **Espace** ouvre la palette. Tapez `join`, choisissez **`blueprint:player_join`** :
   c'est l'événement de départ.

    !!! note "📷 Capture à faire — `images/palette.png`"

        La palette ouverte, `join` tapé dans le champ de recherche, la liste filtrée
        en dessous. C'est le geste que tout le monde répète : il mérite une image.


3. **Espace** encore, tapez `message`, choisissez **« Envoyer un message »**.
4. Tirez un fil depuis la **flèche de sortie** de l'événement vers la **flèche d'entrée**
   du message. Puis du pin `player` de l'événement vers le pin `player` du message.
5. Cliquez le champ texte du message, écrivez `Bonjour !`, **Ctrl+S**.

Cliquez **Tester** : le blueprint est enregistré et activé. Reconnectez-vous — le message
s'affiche.

!!! note "📷 Capture à faire — `images/premier-graphe.png`"

    Les deux nœuds câblés, avec les deux fils bien visibles : le fil épais d'exécution et
    le fil fin de données. C'est l'image qui explique la différence mieux qu'un
    paragraphe.

Si un pin refuse de se connecter, ce n'est pas un bug : les types doivent correspondre.
Le fil devient rouge et la barre du bas dit pourquoi.

---

## 4. Les gestes qui servent tous les jours

| Souris | | Clavier | |
|---|---|---|---|
| Molette | Zoom | `Espace` | Palette |
| Clic milieu / clic droit glissé | Déplacer la vue | `Flèches` | Passer d'un nœud à l'autre |
| Clic sur un pin puis glisser | Câbler | `Entrée` | Éditer la première valeur du nœud |
| Alt + clic sur un pin | Détacher un fil | `Ctrl+S` | Enregistrer |
| Clic droit sur le vide | Palette au curseur | `Ctrl+Z` / `Ctrl+Y` | Annuler / Rétablir |
| Double-clic sur une valeur | La modifier | `Ctrl+C` / `Ctrl+V` | Copier / Coller |
| | | `Suppr` | Supprimer la sélection |
| | | `F` | Tout recadrer |
| | | `Q` | Aligner la sélection |
| | | `C` | Commentaire autour de la sélection |
| | | `Ctrl+F` | Chercher un nœud |
| | | `Tab` | Replier les panneaux |

---

## 5. Variables

Le panneau **Variables** (à gauche) crée des valeurs qui survivent entre deux
exécutions : un compteur, un état, un nom. Chaque variable a une **portée** :

- `graph` — propre au blueprint ;
- `player` — une valeur **par joueur** ;
- `world` — une pour le monde entier ;
- `local` — le temps d'une exécution seulement.

Glissez une variable sur le canevas pour obtenir un nœud **lire** ou **écrire**.

![Un nœud de lecture de la variable « compte »](images/noeud-variable.png)

*Un nœud **lire** : la couleur et la forme du pin disent le type — ici un nombre. La forme
change avec le type exprès, pour rester lisible en cas de daltonisme.*

---

## 6. Quand ça ne marche pas

| Symptôme | Cause la plus fréquente |
|---|---|
| « Le blueprint de démo ne s'enregistre pas » | C'est la démo : elle est jetable. Créez-en un vrai. |
| « Lecture seule » | Le serveur ne vous accorde pas l'édition — voyez l'administrateur. |
| Le graphe ne se déclenche jamais | Pas de nœud d'événement, ou blueprint désactivé (`/blueprint info`). |
| « mod(s) absent(s) » dans `/blueprint info` | Un mod qui fournissait des nœuds a disparu. Vos graphes sont **intacts** : réinstallez-le et tout repart. |
| Un nœud est barré / en pointillés | C'est un **fantôme** : même cause que ci-dessus. |
| Le blueprint s'est désactivé tout seul | Il a dépassé son budget de calcul, ou un nœud a fauté. Le log serveur nomme le nœud. |

!!! note "📷 Capture à faire — `images/diagnostics.png`"

    Un graphe volontairement cassé — un pin obligatoire non câblé, ou deux types
    incompatibles — avec la **barre de diagnostics** rouge en bas. Montrer le mod en train
    de dire ce qui ne va pas vaut mieux que dix captures où tout marche.

Pour regarder ce qui se passe vraiment, un administrateur dispose du **débogueur** :

```
/blueprint debug <id> on          puis  break <nœud>, step, continue, values <nœud>
/blueprint profile <id> on        puis  show   (quels nœuds coûtent cher)
```

---

## 7. Partager un blueprint

Tout graphe s'écrit en **BScript**, un texte lisible :

- `/blueprint export <id>` écrit `blueprint/exports/<id>.bp` (dossier `blueprint/` à la **racine du jeu**, à côté de `saves`) ;
- `/blueprint import <fichier>` le relit ;
- **Ctrl+C** dans l'éditeur copie la sélection en BScript — collable dans un message.

La vue **Script** (bouton de la barre d'outils) montre le texte du graphe en direct.

!!! note "📷 Capture à faire — `images/vue-script.png`"

    La vue Script à côté du graphe qu'elle décrit. C'est la garantie centrale du mod, et
    c'est la seule qui ne se raconte pas : il faut voir les deux ensemble.

> **Chaque `Ctrl+S` rafraîchit le fichier.** Le dossier `blueprint/exports/` est donc un
> reflet fidèle de ce que contient votre monde, pas une photo du jour où vous avez pensé à
> exporter — vous pouvez le suivre dans git, l'ouvrir dans un éditeur de texte, en envoyer
> un fichier à quelqu'un, sans rien avoir à faire d'abord.
>
> C'est un **reflet**, dans un seul sens : rien n'est relu depuis ce dossier au démarrage,
> et vos blueprints vivent dans la **sauvegarde du monde** — copier un monde les emporte,
> le restaurer les restaure. Modifier un `.bp` à la main ne change donc rien tant que vous
> ne l'avez pas réimporté. Et **supprimer un blueprint n'efface pas son fichier** : c'est
> souvent la dernière copie, et c'est là qu'on vient la rechercher.
>
> Sur un serveur, `autoExport: false` dans `blueprint/config.json` coupe le reflet.

---

## 8. Faire un menu

Un blueprint peut ouvrir un **écran** chez un joueur : une boutique, un distributeur, un
tableau de scores. En dix minutes, de zéro à un guichet qui compte des jetons.

![Le concepteur d'écrans](images/concepteur-ecran.png)

*Le concepteur : la palette d'éléments et les calques à gauche, l'aperçu au centre, les
réglages de l'élément sélectionné à droite, et les résolutions à tester en bas.*

### Se déplacer dans le concepteur

Le canevas s'ouvre en **1920×1080, cadré entier**. Trois gestes suffisent, et ils sont les
mêmes que dans l'onglet Graphe :

| | |
|---|---|
| **molette** | zoomer — le point visé reste sous le curseur |
| **bouton du milieu**, ou `Espace` + clic gauche | déplacer la vue |
| `F` / `Ctrl+0` | cadrer le canevas / revenir à 1:1 |
| `Tab` | replier les deux panneaux latéraux ; le canevas double de largeur |
| **`F1`** | **la liste de tous les raccourcis** |

Le reste se découvre par `F1` plutôt qu'en le retenant d'ici. Deux qui rendent service
tout de suite : `G` montre et cache la grille d'accroche, et le **pavé numérique** aligne
la sélection — avec un seul élément sélectionné, il l'aligne sur son **parent**, ce qui
est la façon courte de dire « centre ce bouton dans son cadre ».

### Dessiner l'écran

Onglet **Écrans** dans la barre du haut. À gauche, la liste des écrans de ce blueprint et
`+ nouvel écran`.

1. Créez un écran, nommez-le `guichet`.
2. Posez un **panneau** depuis la palette : c'est le cadre du menu.
3. Dans le panneau de droite, passez sa **disposition** en `colonne`, écart `4`,
   en travers `étiré`.
4. Déposez dedans une **étiquette** et deux **boutons**. Ils se rangent tout seuls —
   vous n'écrirez aucune coordonnée.
5. Donnez à chacun une taille `remplir` en largeur.

Nommez les boutons `prendre` et `fermer` : **c'est par ce nom que le graphe les
désignera**, jamais par leur position.

> La barre du bas simule la fenêtre du joueur. Basculez entre 320×180 et 960×540 : un
> menu qui tient dans les deux tiendra chez tout le monde.

### Un style pour les deux boutons

Peignez un bouton comme il vous plaît, puis **Styles → depuis la sélection**. Appliquez
le style à l'autre bouton. Désormais, en changer un les change tous les deux — c'est ce
qui évite de retaper neuf couleurs par élément.

### Afficher une variable

Créez une variable `jetons` (entier, portée **joueur**). Sélectionnez l'étiquette, et
dans **Montre**, choisissez `jetons` — la variable se choisit dans la liste, elle ne se
tape pas. Format : `Jetons : %s`.

L'étiquette **déclare** ce qu'elle affiche. Un seul nœud la mettra à jour, au lieu d'un
`gui/set_text` à chaque endroit du graphe où la valeur bouge.

### Câbler

Retour à l'onglet **Graphe**. Trois morceaux :

| Ce qu'on veut | Les nœuds |
|---|---|
| Ouvrir le menu | `event/command` → `gui/open(screen: "guichet")` → `gui/refresh` |
| Le bouton compte | `event/gui_clicked(element: "prendre")` → `var/set` → `gui/refresh` |
| Le bouton ferme | `event/gui_clicked(element: "fermer")` → `gui/close` |

Le littéral `element` sur l'événement de clic est **ce qui filtre** : sans lui, les deux
boutons réveilleraient les deux branches.

**`gui/refresh` n'est pas optionnel.** Rien ne part tant que le graphe ne le demande pas
— c'est ce qui fait qu'un écran ouvert et immobile ne coûte rien, ni au serveur ni au
réseau. En contrepartie, un écran qui ne se met pas à jour est presque toujours un
`gui/refresh` oublié après le `var/set`.

### Essayer

`Ctrl+S`, puis en jeu la commande que vous avez câblée. `Échap` ferme. `Tab` parcourt les
boutons et `Entrée` active : un joueur qui ne vise pas bien à la souris doit pouvoir s'en
servir.

!!! note "📷 Capture à faire — `images/menu-en-jeu.png`"

    Le guichet **ouvert en jeu**, par-dessus le monde. Le concepteur montre ce qu'on
    dessine ; celle-ci montre ce qu'on obtient, et c'est la deuxième moitié de la
    promesse.

Pour voir tous les widgets d'un coup plutôt que ces deux boutons, la **vitrine** est
livrée : `/blueprint showcase` puis `/bpc vitrine` — les douze types, tous câblés. Elle est
décrite dans [Les blueprints livrés](examples/README.md).

### Une page qu'on lit

Un menu se manipule ; une page se **parcourt** — un règlement, une aide, la description
d'un objet. Trois réglages suffisent, et ils vont ensemble.

**Le texte qui revient à la ligne.** Cochez `Retour ligne` sur une étiquette : son texte se
coupe aux mots au lieu d'être tronqué. Ce qui ne tient pas en hauteur est coupé net —
mieux vaut ça qu'un paragraphe qui traverse le menu par-dessus ses voisins.

> Mettez le réglage dans un **style nommé** plutôt que sur chaque étiquette. Ajouter un
> sixième paragraphe ne demandera alors que d'écrire son texte.

**Le panneau défilant.** Sur un conteneur, le réglage `Défilant` accepte `V`, `H` ou
`V+H`. Donnez-lui une hauteur **à lui** : en `ajuster`, il grandirait avec son contenu,
donc rien ne dépasserait, donc il ne défilerait jamais — l'éditeur vous le dira, mais
autant le savoir avant.

En jeu : la molette, `Maj`+molette pour l'horizontale, `Page↑`/`Page↓`, `Origine`/`Fin`,
et le curseur à droite du cadre, qui se tire. `Tab` ramène dans le cadre l'élément qu'il
atteint. Depuis le graphe, `gui/set_scroll` à `0` remet en haut — utile après avoir changé
le contenu, et indispensable pour un bouton « Haut de page ».

**L'infobulle.** Le champ `Infobulle` de n'importe quel élément — y compris une étiquette,
une image, et surtout un bouton **désactivé**, où c'est le moment exact où le joueur se
demande pourquoi il ne peut pas cliquer. Un `#` en tête en fait une clé de traduction,
comme pour le texte.

Là encore, plus d'exemple livré — seul le banc de performance l'est.

### Changer l'apparence en cours de partie

`gui/set_style` fait suivre à un élément un **style nommé de l'écran**. C'est ainsi qu'on
fait des onglets sans dupliquer quoi que ce soit : décrivez `onglet_actif` et
`onglet_inactif` une fois dans le concepteur, et un seul nœud bascule les six boutons.
`gui/set_tooltip` change de même ce que le survol explique.

### Un HUD plutôt qu'un menu

Un écran marqué **HUD** s'affiche par-dessus le jeu sans rien capter : on continue de
marcher et de frapper. Pas d'`Échap` — **F7** les masque tous, et c'est la seule sortie de
secours si un graphe en affiche un opaque.

Un HUD n'accepte pas de bouton, et l'éditeur le refuse : il ne capte pas la souris, un
bouton y serait un leurre.

---

## 9. Donner un menu avec ses images

Un menu se dessine **chez le joueur**. Le serveur envoie la description de l'écran — les
positions, les couleurs, les textes — mais il ne peut pas pousser de fichiers sur la
machine de quelqu'un. Les images voyagent donc à part, dans un **dossier de pack** :

```
blueprint/scripts/
└── ma_boutique/
    ├── pack.json          nom, version, auteur (facultatif)
    ├── textures/
    │   ├── fond.png
    │   └── bouton.png
    └── boutique.bp        le blueprint, pour l'importer
```

Dans l'écran, une image se désigne par `<pack>/<fichier>` — `ma_boutique/fond`.

- **Donner son menu** = donner le dossier.
- **Le recevoir** = le déposer dans `blueprint/scripts/`, puis `/blueprint-packs reload`
  — sans quitter la partie.
- `/blueprint-packs` liste ce qui est installé, et **ce qui a été écarté avec la raison**.

Un exemple complet, prêt à copier : [`examples/packs/ma_boutique/`](https://github.com/Kerlann/blueprint/tree/main/docs/examples/packs/ma_boutique).

### La contrepartie, à savoir avant de partager

En multijoueur, le blueprint vit sur le **serveur** et les images vivent chez **chaque
joueur**. Un menu à images n'est donc joli que pour ceux qui ont le pack ; les autres
voient la mise en page, les couleurs et les textes — mais un damier magenta à la place
des images, avec le nom du pack qui leur manque écrit dessus.

Ce n'est pas un défaut à contourner, c'est la conséquence directe du fait qu'un serveur
ne peut pas déposer de fichiers chez vous. Deux façons de faire avec :

- **Concevoir sans image d'abord.** Couleurs, bordures, marges et alignements ne
  demandent aucun pack, et un menu bien réglé s'en passe très bien.
- **Donner le dossier en même temps que l'adresse du serveur**, si les images comptent.

L'onglet **Écrans** de l'éditeur affiche les packs dont l'écran dépend, en rouge ceux qui
ne sont pas installés chez vous — de quoi voir en concevant ce que verront les autres.

### Les bornes

PNG uniquement, 2048×2048 au maximum, 4 Mo par fichier, 256 images par pack. Au-delà,
l'image est écartée **et nommée** ; le reste du pack charge quand même. Un pack illisible
n'empêche jamais les autres de fonctionner, ni le jeu de démarrer.

---

## 10. Déclarer vos propres items et blocs

Un blueprint pilote le jeu ; il peut aussi **y ajouter des objets**. Pas des items vanilla
renommés — de vrais items et de vrais blocs, que `/give` connaît et qu'on pose.

### En deux minutes

Copiez [`examples/content/`](https://github.com/Kerlann/blueprint/tree/main/docs/examples/content) dans `blueprint/content/`, puis
**redémarrez le jeu**. Ensuite :

```
/blueprint content
/give @s blueprint:rubis 8
/give @s blueprint:granit_bleu
```

Le rubis a un nom bleu et ne s'empile qu'à seize ; le granit bleu se pose, éclaire autour
de lui, et demande une pioche pour être récupéré.

### Ce que contient un fichier

`blueprint/content/items/rubis.json` — **rien n'est obligatoire** :

```json
{ "name": "Rubis", "stackSize": 16, "rarity": "rare" }
```

`blueprint/content/blocks/granit_bleu.json` :

```json
{ "name": "Granit bleu", "hardness": 3.0, "tool": "pickaxe",
  "requiresTool": true, "light": 7, "sound": "stone" }
```

**Le nom du fichier devient l'identifiant** (`blueprint:rubis`), et l'image est le **PNG du
même nom, à côté** — `rubis.json` et `rubis.png`. Rien d'autre à écrire : pas de chemin,
donc pas de chemin à écrire faux.

### Le redémarrage, et pourquoi il n'y a pas moyen de faire autrement

Minecraft **gèle** ses registres à la fin du chargement des mods, avant qu'aucun monde ne
soit ouvert. Ajouter un item après, c'est-à-dire depuis un blueprint — qui vit dans une
sauvegarde — est structurellement impossible, pour ce mod comme pour tous les autres.

Deux conséquences à connaître avant de compter dessus :

- **Ajouter ou modifier un item demande un redémarrage.** `/reload` n'y peut rien.
- **En multijoueur, chaque joueur a besoin du mod et des mêmes fichiers.** C'est vrai de
  tout mod qui ajoute du contenu ; un client vanilla ne verra pas vos items.

### Quand ça ne marche pas

`/blueprint content` est fait pour ça. Il liste ce qui est enregistré et, **en rouge**, ce
qui a été écarté avec la raison — un JSON mal formé, un nom de fichier impossible, un nom
déclaré deux fois. Un fichier fautif n'emporte jamais les autres et n'empêche jamais le jeu
de démarrer.

Un item marqué **sans image** s'affichera en damier magenta : il lui manque son PNG voisin.
Les textures passent par un pack de ressources que Blueprint génère tout seul dans
`resourcepacks/blueprint_content/` et coche à sa création. Si vous le décochez, il **reste**
décoché — c'est votre réglage, et `/blueprint-packs` vous le rappellera.

### Les faire agir

Un item déclaré ne *fait* rien par lui-même. C'est le graphe qui lui donne un rôle :

- **« Un joueur utilise un objet »** → sa sortie `item` dit lequel ; comparez-la à
  `blueprint:rubis` et branchez la suite.
- **« Un bloc déclaré est posé »** et **« Un joueur casse un bloc »** → la position et
  l'identifiant.
- **Les huit « Action Blueprint »** dans Options → Commandes : non assignées au départ,
  et le nœud **« Une touche Blueprint est pressée »** écoute leur numéro. C'est le joueur
  qui choisit la touche, pas vous — donc pas de conflit avec ses autres mods.

---

## 11. Pour aller plus loin

- [`node-reference.md`](node-reference.md) — tous les nœuds livrés, leurs pins et leur coût.
- [`bscript-spec.md`](bscript-spec.md) — la grammaire du texte généré.
- [`extension-api.md`](extension-api.md) — ajouter vos propres nœuds (mod ou datapack).
- [`ux-ui-spec.md`](https://github.com/Kerlann/blueprint/blob/main/docs/ux-ui-spec.md) —
  la logique d'ensemble de l'éditeur. *(Document de conception, hors du site.)*
