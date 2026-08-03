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
| Voir un exemple qui marche | `/blueprint demo` puis `/blueprint-edit blueprint:demo` |
| Créer le vôtre | `/blueprint-edit create mon_premier` |
| Rouvrir le dernier édité | Touche **F6** |
| Voir ce qui existe | `/blueprint-edit` (liste) ou `/blueprint list` |

> **En solo**, tout fonctionne, y compris sans « autoriser les tricheurs ».
> **Sur un serveur**, l'édition demande la permission configurée par l'administrateur ;
> sans elle, l'éditeur s'ouvre en **lecture seule** et vous le dit.

---

## 3. Votre premier blueprint en cinq gestes

1. `/blueprint-edit create bonjour`
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

- `/blueprint export <id>` écrit `config/blueprint/exports/<id>.bp` ;
- `/blueprint import <fichier>` le relit ;
- **Ctrl+C** dans l'éditeur copie la sélection en BScript — collable dans un message.

La vue **Script** (bouton de la barre d'outils) montre le texte du graphe en direct.

---

## 8. Pour aller plus loin

- [`node-reference.md`](node-reference.md) — tous les nœuds livrés, leurs pins et leur coût.
- [`bscript-spec.md`](bscript-spec.md) — la grammaire du texte généré.
- [`extension-api.md`](extension-api.md) — ajouter vos propres nœuds (mod ou datapack).
- [`ux-ui-spec.md`](ux-ui-spec.md) — la logique d'ensemble de l'éditeur.
