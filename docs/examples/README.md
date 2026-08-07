# Les blueprints livrés

Deux, et chacun a un travail précis.

| | |
|---|---|
| [`vitrine.bp`](vitrine.bp) | **les onze types d'éléments d'écran, tous câblés** — `/blueprint showcase` puis `/bpc vitrine` |
| [`bench.bp`](bench.bp) | **un banc de performance** — `/blueprint bench` puis `/bpc bench` |

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
| `TOGGLE` | grise le bouton `+10` — un widget qui en pilote un autre |
| `SLIDER` | écrit `score` en continu, par pas de 5 |
| `SLOT` | un emplacement d'objet |
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

---

# Le banc de performance

[`bench.bp`](bench.bp), un **banc de performance jouable**.

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
| [`content/`](content/) | contenu déclaré — blocs et items en JSON, à copier dans `blueprint/content/`, puis `/blueprint content` |
| [`packs/`](packs/) | pack d'images d'exemple, à copier dans `blueprint/scripts/`, puis `/blueprint-packs reload` |
