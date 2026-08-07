# Les blueprints livrés

Trois, et chacun a un travail précis.

| | |
|---|---|
| [`rp.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/rp.bp) | **un serveur de jeu de rôle** — `/blueprint rp`, puis reconnecte-toi |
| [`vitrine.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/vitrine.bp) | **les douze types d'éléments d'écran, tous câblés** — `/blueprint showcase` puis `/bpc vitrine` |
| [`bench.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/bench.bp) | **un banc de performance** — `/blueprint bench` puis `/bpc bench` |

---

# Le serveur de jeu de rôle

Deux écrans et un graphe. À la connexion, le joueur qui n'a pas de personnage reçoit un
formulaire — prénom, nom, âge, sexe, métier ; celui qui en a un reçoit directement sa
fiche. Tout est enregistré **chez le joueur** et survit au redémarrage du serveur.

```
/blueprint rp        # l'installe et l'active
                     # puis reconnecte-toi
/bpc rp              # rouvrir le formulaire pour se corriger
```

## Ce qu'il montre et qu'aucun autre exemple ne montrait

**Le partage du travail entre le client et le serveur.** La fiche affiche cinq lignes.
Trois viennent du serveur, deux ne viennent de nulle part.

| Ligne | Source | Ce que ça coûte |
|---|---|---|
| Prénom, nom, métier | **variable** — seul le serveur les connaît | un paquet à la création, plus rien ensuite |
| Barre de vie, « 18 / 20 PV » | **valeur client** — le joueur l'a déjà | **rien** : ni variable, ni tick, ni paquet |

Cela se lit dans le fichier texte. Une source client s'y écrit avec un `@` :

```
progress "vie" @bind("@health", progress, max: 20)
label "metier" @bind("metier", text, format: "Métier : %s")
```

La version naïve de cette fiche lierait la vie à une variable. Il faudrait alors un
`server_tick` qui parcourt les joueurs connectés vingt fois par seconde, lit la vie de
chacun, l'écrit, et envoie une modification. **À cinquante joueurs : mille lectures et
jusqu'à mille paquets par seconde** — pour redire à chacun ce qu'il voit déjà dans ses
propres cœurs. Un test échoue si quelqu'un refait ce chemin.

## La portée des variables

Toutes en **`PLAYER`**, et ce n'est pas un détail. En `GRAPH`, le deuxième joueur à créer
son personnage effacerait le prénom du premier, et chacun verrait dans sa fiche l'identité
du dernier arrivé.

C'est ce que la portée joueur promettait — « persistante par joueur » — sans le tenir : le
magasin rangeait par `(portée, nom)` seul, sans clé de joueur et sans écriture sur disque.
Les deux sont réparés, et ce blueprint est ce qui l'a fait apparaître.

## Trois décisions qu'on peut copier

- **L'âge au curseur, pas au clavier.** Un champ numérique accepte « 700 » et oblige le
  graphe à le refuser ensuite. Un curseur borné de 16 à 90 ne peut pas produire de valeur
  invalide, donc il n'y a rien à valider.
- **On garde la ligne choisie, pas son indice.** Un indice se périme dès qu'on ajoute un
  métier au milieu de la liste : les personnages existants changeraient de métier sans que
  personne n'ait rien touché.
- **La vérification est côté serveur.** Un client modifié peut envoyer n'importe quel
  contenu de champ ; griser le bouton tant que les champs sont vides serait un confort,
  jamais une garantie.

Et le prénom apparaît **deux fois dans deux étiquettes** plutôt qu'une fois dans une
variable `identite` recomposée : une valeur dérivée se périme dès qu'on change une source
sans repasser par le nœud qui la recompose, et rien ne le signale.

## Le modifier

Les métiers et les sexes sont deux chaînes séparées par des virgules, découpées par
`string/split`. Un seul champ à changer dans l'éditeur pour ajouter « Aubergiste ».

---

# La vitrine

Montrer les widgets côte à côte serait une planche d'échantillons. Ici **chacun est relié à
de la logique** : les boutons changent une variable, la liste répond au clic, le curseur et
la case à cocher écrivent, le champ de saisie est relu à la validation.

| Type | Ce qu'il démontre |
|---|---|
| `PANEL` | quatre imbriqués : colonne, lignes, et un panneau qui défile |
| `LABEL` | **lié** à la variable `score`, par un format |
| `PROGRESS` | **liée** à la même variable, bornée de 0 à 100 |
| `BUTTON` | trois : `+10`, `-10`, `Fermer` |
| `LIST` | remplie par le graphe, et qui rend la ligne cliquée |
| `INPUT` | numérique, relu **à la validation** seulement |
| `DROPDOWN` | replié, il se **déplie par-dessus le reste** ; ses choix sont des lignes |
| `TOGGLE` | grise le bouton `+10` — un widget qui en pilote un autre |
| `SLIDER` | écrit `score` en continu, par pas de 5, **et affiche sa valeur** |
| `SLOT` | autant d'émeraudes que le score — posées par `gui/set_item` |
| `IMAGE` | une texture du jeu |
| `ENTITY_PREVIEW` | une créature qui tourne |

**Ce qu'il faut en retenir**, et qui est le plus difficile à voir sans exemple : le titre et
la barre ne sont écrits par **aucun nœud**. Ils *déclarent* suivre `score`, et un seul
`gui/refresh` les remet tous les deux d'accord. C'est la différence entre un écran qu'on
repeint et un écran qui se lit.

Deux pièges que la vitrine désamorce au passage, tous deux vécus :

- le champ de saisie émet à **chaque frappe** ; sans le branchement sur `soumis`, taper
  « 100 » écrirait successivement 1, puis 10, puis 100 ;
- sans `gui/refresh` après une écriture, l'écran se fige alors que la variable a bien
  changé — la panne la plus déroutante de tout l'épic des interfaces.

La variable est de portée **`PLAYER`** : deux joueurs ouvrant la vitrine ont chacun leur
score. En portée `GRAPH`, le second verrait celui du premier bouger sous ses yeux.

## La liste déroulante

Elle mérite un mot, parce qu'elle est le seul élément qui **sorte de sa case**.

Ses choix sont ses **lignes** — les mêmes qu'une `LIST`, posées par le même
`gui/set_lines`, validées par le même chemin serveur, et rendues au graphe par le même
événement `gui_list_clicked`. « Quel élément de cette liste » est la même question, qu'elle
soit posée dépliée ou repliée : inventer un second mécanisme aurait doublé la surface à
valider pour rien.

Ce qui la distingue est le **dessin**. Le panneau déplié se peint par-dessus tout le reste,
et ce calque vit dans l'écran client plutôt que dans le peintre — celui-ci parcourt les
éléments dans l'ordre de la table de disposition et n'a pas de notion de couche. Lui en
donner une pour un seul type aurait compliqué le dessin de tous les autres.

Les comportements que l'usage réclame, et qui sont dans le code :

- le panneau tombe **sous** l'élément, sauf s'il n'y a plus la place en bas — auquel cas il
  remonte au-dessus, sans quoi une liste en bas de fenêtre se déplierait hors de l'écran ;
- **Échap referme la liste avant l'écran**, comme il relâche un champ de saisie ;
- un clic à côté referme aussi — une liste qui resterait ouverte donnerait un menu dont on
  ne sait plus sortir ;
- au-delà de huit choix elle **défile** — molette, ou flèches, ou `Page haut` / `Page bas`,
  avec un curseur de défilement pour dire qu'il en reste ;
- **Entrée sur une liste repliée l'ouvre**. Elle envoyait un clic, c'est-à-dire un choix
  que personne n'avait fait ;
- **taper une lettre saute** au premier choix qui commence par elle, et la retaper passe au
  suivant. Sur trente paliers, y aller à la flèche demande trente pressions ;
- elle rouvre sur le **choix courant**, pas sur le début.

## Ce que les options règlent

Trois réglages existants prennent un sens de plus, plutôt qu'un champ neuf à encoder
partout :

| Réglage | Sur | Ce qu'il fait |
|---|---|---|
| `rowHeight` | `DROPDOWN` | la hauteur des rangées dépliées — le **même** réglage qu'une `LIST`, puisque les choix sont des lignes |
| `placeholder` | `SLIDER` | l'**unité** écrite derrière la valeur (`« 45 pts »`) ; il n'avait aucun autre sens pour ce type |
| `step` | `SLIDER` | il décidait déjà de l'alignement ; il décide maintenant du **nombre de décimales** affichées — un curseur qui avance de 5 en 5 n'écrit pas « 45,000 » |

Et deux surlignages qui manquaient : la ligne **retenue** d'une `LIST` (cliquer n'y laissait
aucune trace) et le choix visé au clavier dans une liste dépliée.

---

# Le banc de performance

[`bench.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/bench.bp), un **banc de performance jouable**.

Il ne cherche pas à enseigner. Il cherche à faire travailler la VM là où les bancs
headless ne vont pas — dans une partie, sur un serveur, avec quelqu'un qui tape une
commande et lit le résultat.

> **Fichier généré** — ne pas modifier à la main. Il est produit depuis
> `BenchBlueprint.java` par `StressBlueprintTest`. Un `.bp` écrit à la main ne se parse pas
> toujours : le projet l'a déjà appris avec le pack `ma_boutique`.
>
> Régénérer : `./gradlew :core:test --tests "*StressBlueprintTest" -Dblueprint.regenDocs=true`

## L'utiliser

En jeu, deux commandes :

```
/blueprint bench      # crée le banc, l'active, et l'exporte dans blueprint/exports/
/bpc bench            # le lance ; il répond par un message
```

Puis, pour voir où le temps est passé :

```
/blueprint profile blueprint:bench on
/bpc bench
/blueprint profile blueprint:bench
```

> **L'identifiant vient AVANT l'action.** `/blueprint profile show` se lit comme un
> blueprint nommé « show » — la commande le complète en `minecraft:show` et répond qu'il
> n'est pas profilé, ce qui est exact et sans aucun secours. Sans action, `<id>` seul
> affiche le rapport.

Le profilage n'enregistre que ce qui tourne **après** l'avoir activé : il faut donc
relancer `/bpc bench` entre les deux, sinon le rapport annonce zéro appel sur zéro nœud.

Et pour un chiffre qui veut dire quelque chose, chauffer puis remettre à zéro :

```
/bpc bench                                  # trois fois
/blueprint profile blueprint:bench reset
/bpc bench                                  # le tour mesuré
/blueprint profile blueprint:bench
```

Le profileur **cumule**, et le premier tour coûte **sept fois** plus que les suivants — le
compilateur JVM n'a pas encore chauffé. Relevés à chaud sur un lancement : **1 034 appels
de nœuds, 4 286 de carburant, ~350 µs**, deux mesures successives tenant dans 5 %.

Ce que le rapport apprend au passage : les quatre cents additions des boucles coûtent moins
cher qu'un **seul** `player/send_message`, qui construit et envoie un paquet réseau. Dans
un graphe de gameplay, ce sont les effets qui coûtent, pas le calcul.

Et les nœuds **abaissés** par le compilateur — les boucles — apparaissent sous une étiquette
comme `blueprint:list/size +3` : une boucle n'existe pas à l'exécution, elle devient
plusieurs sortes d'instructions qui gardent toutes l'identifiant du nœud posé.

Pour le lire ou le modifier : `/blueprint-edit blueprint:bench`.

## Ce qu'il exerce

Les **trois formes de boucle** du langage, parce que chacune s'abaisse en un jeu
d'instructions différent et qu'aucune n'exerce les mêmes chemins de la VM :

| Boucle | Ce qu'elle met sous tension |
|---|---|
| `flow/for` | bornes fixes — compteur, comparaison, saut arrière ; le fuel s'y consomme le plus régulièrement |
| `flow/while` | condition **recalculée** à chaque tour, par une chaîne de nœuds purs |
| `flow/for_each` | parcours d'une liste, un élément par tour |

Autour d'elles, ce qui coûte vraiment dans un graphe réel : des **variables** lues et
écrites à chaque tour, de l'arithmétique, une concaténation bornée, une découpe de chaîne,
et une conversion finale en texte — pour que le résultat des boucles remonte jusqu'au
joueur, sans quoi rien ne prouverait qu'elles ont tourné.

La boucle `while` est aussi un **test de non-régression du compilateur** : sa condition est
une chaîne de purs ré-évaluée à chaque tour, et si la mémoïsation des purs la figeait, la
boucle ne s'arrêterait jamais.

## Le régler

Les bornes sont modestes à dessein — deux cents tours — pour mesurer sans déclencher la
police du dépassement de budget. Pour éprouver **celle-là**, il suffit de monter le
littéral `last` du nœud `flow/for` dans l'éditeur : le serveur coupe le graphe au bout de
quelques ticks en dépassement, et le dit.

## Le modifier

Il est à toi une fois créé : renomme-le, change les bornes, recâble. Deux réflexes qui
évitent les surprises :

- **`/blueprint disable` avant de modifier** un graphe qui tourne.
- La **barre de diagnostics** en bas de l'éditeur dit ce qui bloque ; un pin obligatoire
  non câblé y apparaît avant même d'essayer d'activer.

Pour comprendre un nœud que tu ne connais pas, **survole-le** — l'infobulle donne son rôle,
et l'infobulle d'un pin donne son type.

## Le reste du dossier

| | |
|---|---|
| [`content/`](https://github.com/Kerlann/blueprint/tree/main/docs/examples/content) | contenu déclaré — blocs et items en JSON, à copier dans `blueprint/content/`, puis `/blueprint content` |
| [`packs/`](https://github.com/Kerlann/blueprint/tree/main/docs/examples/packs) | pack d'images d'exemple, à copier dans `blueprint/scripts/`, puis `/blueprint-packs reload` |
