# Blueprint — prise en main

Ce guide s'adresse au **joueur**. Il ne demande pas de savoir programmer, et pas une
ligne de Java. Comptez dix minutes pour votre premier blueprint qui fait quelque chose.

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
| Voir un exemple qui marche | `/blueprint demo` puis `/blueprint edit blueprint:demo` |
| Créer le vôtre | `/blueprint create mon_premier` |
| Ouvrir le navigateur | Touche **F6** — double-clic pour ouvrir, Ctrl+N pour créer |
| Voir ce qui existe | **F6**, ou `/blueprint edit` — dossiers, création, import |

> **En solo**, tout fonctionne, y compris sans « autoriser les tricheurs ».
> **Sur un serveur**, l'édition demande la permission configurée par l'administrateur ;
> sans elle, l'éditeur s'ouvre en **lecture seule** et vous le dit.

---

## 3. Votre premier blueprint en cinq gestes

1. `/blueprint create bonjour`
2. **Espace** ouvre la palette. Tapez `join`, choisissez **`blueprint:player_join`** :
   c'est l'événement de départ.
3. **Espace** encore, tapez `message`, choisissez **« Envoyer un message »**.
4. Tirez un fil depuis la **flèche de sortie** de l'événement vers la **flèche d'entrée**
   du message. Puis du pin `player` de l'événement vers le pin `player` du message.
5. Cliquez le champ texte du message, écrivez `Bonjour !`, **Ctrl+S**.

Cliquez **Tester** : le blueprint est enregistré et activé. Reconnectez-vous — le message
s'affiche.

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

---

## 8. Faire un menu

Un blueprint peut ouvrir un **écran** chez un joueur : une boutique, un distributeur, un
tableau de scores. En dix minutes, de zéro à un guichet qui compte des jetons.

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

L'exemple complet est livré : [`examples/guichet.bp`](examples/guichet.bp), ou
`/blueprint examples` pour le créer directement en jeu.

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

Un exemple complet, prêt à copier : [`examples/packs/ma_boutique/`](examples/packs/ma_boutique/).

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

## 10. Pour aller plus loin

- [`node-reference.md`](node-reference.md) — tous les nœuds livrés, leurs pins et leur coût.
- [`bscript-spec.md`](bscript-spec.md) — la grammaire du texte généré.
- [`extension-api.md`](extension-api.md) — ajouter vos propres nœuds (mod ou datapack).
- [`ux-ui-spec.md`](ux-ui-spec.md) — la logique d'ensemble de l'éditeur.
