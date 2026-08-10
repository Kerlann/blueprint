# Les blueprints livrés

Neuf, et chacun a un travail précis. Quatre s'installent par une commande :

| | |
|---|---|
| [`fonctions.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/fonctions.bp) | **définir une fois, appeler partout** — `/blueprint fonctions` puis `/fonctions` |
| [`rp.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/rp.bp) | **un serveur de jeu de rôle** — `/blueprint rp`, puis reconnecte-toi |
| [`vitrine.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/vitrine.bp) | **les douze types d'éléments d'écran, tous câblés** — `/blueprint showcase` puis `/vitrine` |
| [`bench.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/bench.bp) | **un banc de performance** — `/blueprint bench` puis `/bench` |

Cinq autres sont livrés en **fichiers**, pas en commande d'installation : ils sont écrits à
la main, et servent aussi à vérifier que du BScript tapé au clavier reste lisible par le
parseur. Déposez-les dans `blueprint/exports/`, puis `/blueprint import <nom>` et
`/blueprint enable blueprint:<nom>` — **l'import n'active pas**, et une commande déclarée
ne se pose qu'à l'activation.

| | |
|---|---|
| [`home.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/home.bp) | **un point de retour** — `/sethome` enregistre, `/home` ramène après trois secondes |
| [`back.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/back.bp) | **revenir sur ses pas** — `/back` ramène à l'endroit de votre mort |
| [`warp.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/warp.bp) | **des points nommés partagés** — `/setwarp` pose, `/warp` voyage, `/warps` liste |
| [`admin.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/admin.bp) | **un panneau d'administration** — `/admin` liste les connectés : soigner, mode de jeu, rejoindre |
| [`parkour.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/parkour.bp) | **un parkour chronométré** — départ, arrivée, meilleur temps par joueur |

Tous montrent la même chose : **un blueprint qui déclare une commande en obtient une
vraie.** Déclarez `home` dans un nœud `event/command`, activez le blueprint, et `/home`
existe — sans préfixe, avec l'autocomplétion, comme n'importe quelle commande du serveur.

Trois d'entre eux se lisent aussi comme des leçons de BScript écrit à la main :

- **`back.bp`** range une position par joueur dans une **table** `map<string, vec3>` de
  portée monde, clef sur le nom. C'est le contournement d'une limite réelle :
  `event/entity_death` ne déclare pas de `player`, donc les variables de portée joueur n'y
  ont pas de propriétaire et ne peuvent rien enregistrer.
- **`warp.bp`** montre le même rangement, mais partagé, et la lecture des clefs pour
  répondre à `/warps`.
- **`admin.bp`** parcourt `query/players`, lit chaque nom, et alimente une liste d'écran.
  C'est le seul qui montre un écran nourri par des données du serveur plutôt que par des
  valeurs posées à la main.

---

# Les fonctions

Une suite de nœuds définie **une fois** et appelée depuis plusieurs endroits.

```
/blueprint fonctions      # l'installe et l'active
/fonctions            # il répond « 3x3 + 4x4 = 25 »
```

Deux fonctions, dont l'une appelle l'autre :

```
func "carre"(n: double) returns (r: double) { … }
func "hypotenuse_carree"(a: double, b: double) returns (d: double) {
    blueprint:func/call(function: "carre", n: …)
    blueprint:func/call(function: "carre", n: …)
    …
}
```

## Pourquoi deux appels et pas deux multiplications

Parce qu'un seul appel ne prouverait rien. `carre(3)` et `carre(4)` vivent dans la **même
exécution**, et leurs résultats sont additionnés **après** que les deux ont tourné.

- Un corps partagé entre les deux sites rendrait **32** (2 × 16) ;
- une mémoïsation qui ne les distingue pas rendrait **18** (2 × 9).

Le blueprint range son résultat dans une variable en plus de l'annoncer au joueur, ce qui
le rend **vérifiable sans partie** : un test le compile, l'exécute et lit 25. Il est donc sa
propre preuve, et pas seulement une illustration.

## Ce qu'il faut savoir avant d'en écrire

- **Un corps est déplié à chaque appel**, pas sauté. Le carburant est donc exactement celui
  du code exécuté, et deux appels ne partagent rien — mais l'IR grossit du corps à chaque
  site.
- **La récursion est refusée**, directe comme mutuelle. Le message nomme le cycle : `a → b →
  a` se lit, là où « récursion détectée » enverrait chercher laquelle.
- **Une fonction ne blanchit pas les permissions.** Un nœud `ADMIN` posé dans un corps est
  confronté au plafond du blueprint exactement comme les autres.
- **Un événement dans un corps est refusé** : une fonction s'appelle, elle ne se déclenche
  pas.
- **Il n'y a pas encore d'interface** pour en créer. Elles s'écrivent en BScript, puis
  `/blueprint import`. Le panneau et l'onglet de corps sont la story 20.2.

---

# Le serveur de jeu de rôle

Deux écrans et un graphe. À la connexion, le joueur qui n'a pas de personnage reçoit un
formulaire — prénom, nom, âge, sexe, métier ; celui qui en a un reçoit directement sa
fiche. Tout est enregistré **chez le joueur** et survit au redémarrage du serveur.

```
/blueprint rp        # l'installe et l'active
                     # puis reconnecte-toi
/rp              # rouvrir le formulaire pour se corriger
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
progress "vie"      @bind("@health", progress, maxVar: "@max_health")
label "vie_texte"   @bind("@health", text, format: "%s / %m PV", maxVar: "@max_health")
label "metier"      @bind("metier", text, format: "Métier : %s")
```

**Le maximum est une valeur, pas le nombre vingt.** Un joueur sous effet, avec un artefact
ou un attribut modifié en a vingt-quatre : borné à vingt en dur, il aurait vu sa barre
pleine aux cinq sixièmes sans jamais pouvoir la remplir. `maxVar` la fait suivre, et `%m`
écrit le maximum dans le texte. Un maximum qui ne résout pas — `max_health` vaut zéro le
temps d'une image après un changement de dimension — retombe sur le nombre écrit dans le
blueprint plutôt que de diviser par zéro.

La version naïve de cette fiche lierait la vie à une variable. Il faudrait alors un
`server_tick` qui parcourt les joueurs connectés vingt fois par seconde, lit la vie de
chacun, l'écrit, et envoie une modification. **À cinquante joueurs : mille lectures et
jusqu'à mille paquets par seconde** — pour redire à chacun ce qu'il voit déjà dans ses
propres cœurs. Un test échoue si quelqu'un refait ce chemin.

## La portée des variables

Toutes appartiennent à un **joueur**, et ce n'est pas un détail. En `GRAPH` ou en `WORLD`,
le deuxième joueur à créer son personnage effacerait le prénom du premier, et chacun
verrait dans sa fiche l'identité du dernier arrivé.

Mais deux portées, pas une, et c'est la deuxième leçon de cet exemple :

```
var string prenom = ""    @player_shared    # le personnage : les autres scripts le lisent
var bool   cree   = false @player           # la mécanique de CE script, et de lui seul
```

Un serveur de jeu de rôle finit toujours par avoir plusieurs blueprints : la création, les
métiers, la banque, la police. Tous ont besoin du prénom ; aucun n'a besoin de savoir si le
formulaire a été rempli — et laisser `cree` ouvert exposerait ce script à un autre graphe
portant par hasard le même nom.

| Portée | par joueur | partagée entre blueprints |
|---|---|---|
| `graph` | non | non |
| `player` | **oui** | non |
| `player_shared` | **oui** | **oui** |
| `world` | non | **oui** |

C'est ce que la portée joueur promettait — « persistante par joueur » — sans le tenir. Le
magasin rangeait par `(portée, nom)` seul : ni clé de joueur, ni clé de blueprint, ni
écriture sur disque. Les trois sont réparés, et ce blueprint est ce qui les a fait
apparaître.

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
| `SLIDER` | `live` : écrit `score` **à chaque cran**, là où le défaut attend le relâchement |
| `SLOT` | autant d'émeraudes que le score — posées par `gui/set_item` |
| `IMAGE` | une texture du jeu |
| `ENTITY_PREVIEW` | une créature qui tourne |

**Ce qu'il faut en retenir**, et qui est le plus difficile à voir sans exemple : le titre et
la barre ne sont écrits par **aucun nœud**. Ils *déclarent* suivre `score`, et un seul
`gui/refresh` les remet tous les deux d'accord. C'est la différence entre un écran qu'on
repeint et un écran qui se lit.

Deux pièges que la vitrine désamorce au passage, tous deux vécus :

- le champ de saisie émettait à **chaque frappe** ; sans le branchement sur `soumis`, taper
  « 100 » écrivait successivement 1, puis 10, puis 100. Il ne le fait plus par défaut — voir
  `live` ci-dessous — mais le branchement reste la bonne habitude ;
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

## `live` : ce qu'une saisie et un glissement coûtent au réseau

Un `INPUT` envoyait un paquet **par frappe**, sans qu'on puisse s'en passer. Taper
« Jean-Baptiste » valait treize allers vers le serveur, treize exécutions du graphe et
treize écritures de variable — pour un nom qui n'intéresse personne avant d'être complet.
Sur un serveur où chacun remplit un formulaire à la connexion, cela se compte en milliers
de paquets pour rien.

Un `SLIDER` était pire, et ça se voyait dans le journal du serveur :

```
[Server thread/WARN] (blueprint) Interactions d'écran de Player848 au-delà du quota — ignorées
```

Traverser la plage d'âge du formulaire de jeu de rôle — de 16 à 90, par pas de 1 — envoyait
soixante-quatorze paquets en une seconde. Le serveur en accepte **quarante par dix
secondes** : régler son âge crevait le quota et se faisait ignorer, sans que le joueur
puisse le deviner.

Les deux rapportent maintenant à la **fin du geste** : perte du focus pour un champ (clic
ailleurs, `Tab`, `Échap`), relâchement de la souris pour un curseur, fermeture de l'écran
pour les deux.

C'est la fin du geste qui rend le report sûr : **cliquer sur « Valider » relâche le
champ**, donc le texte part *avant* le clic. Les deux paquets empruntent le même canal dans
l'ordre, et le graphe trouve la valeur en place quand il traite le bouton.

Et l'écran ne perd rien en réactivité : la poignée du curseur et le chiffre à côté sont
dessinés **chez le client**, qui connaît la valeur avant tout le monde. Ce qui attendait
n'était pas l'affichage, c'était le graphe.

Pour une recherche qui filtre pendant qu'on tape, ou une jauge que le graphe doit suivre en
continu — les seuls cas qui le justifient — le comportement d'avant se déclare :

```
input "recherche" @opts(placeholder: "Filtrer…", live: true)
```

> **Changement de comportement.** Un écran enregistré avant ce réglage devient économe sans
> que son auteur ait rien à faire. Un graphe qui comptait sur le report à chaque étape —
> sans avoir déclaré `live` — verra son événement arriver plus tard, et une seule fois.

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
/bench            # le lance ; il répond par un message
```

Puis, pour voir où le temps est passé :

```
/blueprint profile blueprint:bench on
/bench
/blueprint profile blueprint:bench
```

> **L'identifiant vient AVANT l'action.** `/blueprint profile show` se lit comme un
> blueprint nommé « show » — la commande le complète en `minecraft:show` et répond qu'il
> n'est pas profilé, ce qui est exact et sans aucun secours. Sans action, `<id>` seul
> affiche le rapport.

Le profilage n'enregistre que ce qui tourne **après** l'avoir activé : il faut donc
relancer `/bench` entre les deux, sinon le rapport annonce zéro appel sur zéro nœud.

Et pour un chiffre qui veut dire quelque chose, chauffer puis remettre à zéro :

```
/bench                                  # trois fois
/blueprint profile blueprint:bench reset
/bench                                  # le tour mesuré
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
