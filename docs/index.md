# Blueprint

**Un éditeur de logique par nœuds, en jeu, pour Minecraft 1.21.11 (Fabric).**

On pose des nœuds, on les relie, on déclare des variables typées — et le graphe s'exécute
**côté serveur** sur de vrais événements du monde.

![L'éditeur, avec un graphe de huit nœuds branché sur deux événements](images/editeur-graphe.png)

*L'éditeur : deux événements — un joueur se connecte, un joueur écrit — et ce qui en
découle. La barre du bas dit ce qui bloque, ici rien.*

---

## Commencer

<div class="grid cards" markdown>

- :material-play: **[Prise en main](getting-started.md)**

    Votre premier blueprint en dix minutes. Aucune ligne de code.

- :material-package-variant: **[Les blueprints livrés](examples/README.md)**

    Une vitrine des douze widgets, et un banc de performance qu'on peut lire.

- :material-format-list-bulleted: **[Référence des nœuds](node-reference.md)**

    Les 236 types de nœuds, par domaine.

- :material-puzzle: **[Ajouter ses propres nœuds](extension-api.md)**

    Builder Java, annotation, ou simple JSON de datapack.

</div>

---

## Ce qui distingue ce mod

### Un graphe est un texte, et un texte est un graphe

Tout graphe se compile en un script lisible — **BScript** — et tout BScript se re-parse en
le **même** graphe. Ce n'est pas un export : c'est un aller-retour, vérifié à chaque
construction.

```
node "ajoute_score" blueprint:math/add {
    a = var "score"
    b = 10
}
```

C'est ce qui rend un graphe relisible dans une pull request, comparable d'une version à
l'autre, et collable dans un salon de discussion. On copie dans l'éditeur, on colle dans
Discord, on recolle dans l'éditeur.

Le format est décrit dans [la spécification BScript](bscript-spec.md).

### Ça ne coûte rien au serveur, et c'est mesuré

Un éditeur par nœuds évoque volontiers un interpréteur qui rame. Voici des mesures, prises
par des bancs commités qui **font échouer la construction** s'ils dérivent.

| Graphes actifs sur `server_tick` | Ordonnancement par tick | Part du budget de 50 ms |
|---|---|---|
| 50 | ~0,25 ms | **0,5 %** |
| 200 | ~0,6 ms | **1,2 %** |

Chaque graphe tourne sur un **budget de carburant**. Une boucle folle est coupée et
signalée, jamais laissée faire tomber le serveur ; un blueprint qui faute en boucle se
désactive tout seul.

Et ça se vérifie soi-même, en jeu, en trois commandes — le banc est un vrai graphe qu'on
peut ouvrir et modifier :

```
/blueprint bench
/bench
/blueprint profile blueprint:bench
```

### Des menus, dessinés plutôt que codés

![Le concepteur d'écrans](images/concepteur-ecran.png)

*Le concepteur d'écrans : les éléments à gauche, les calques en dessous, l'aperçu au
centre, et le choix de la résolution en bas — parce qu'un menu doit tenir sur l'écran des
autres.*

Douze types de widgets, et des **liaisons** : un libellé peut *suivre* une variable au
lieu d'être écrit par un nœud. Un seul `gui/refresh` remet tout le monde d'accord.

### Un mod retiré ne casse rien

Les autres mods ajoutent leurs nœuds sans dépendance dure. Retirez un tel mod : les graphes
existants ne cassent pas. Ses nœuds deviennent des **fantômes** qui gardent leur câblage et
reprennent vie à la réinstallation.

---

## Installer

1. [Fabric Loader](https://fabricmc.net/) 0.16 ou plus, et Java 21
2. [Fabric API](https://modrinth.com/mod/fabric-api) 0.139.4 ou plus
3. Le jar dans `mods/`

Puis, en jeu :

| | |
|---|---|
| `/blueprint create mon_premier` | créer son premier graphe |
| **F6** | rouvrir le dernier édité |
| `/blueprint showcase` puis `/vitrine` | voir les douze widgets, tous câblés |
| `/blueprint bench` puis `/bench` | lancer le banc de performance |

L'éditeur est côté client : pour éditer, il faut le mod. Mais les graphes s'exécutent sur
le serveur, et un client vanilla peut jouer sur un serveur qui en fait tourner.

---

MIT. [Code source](https://github.com/Kerlann/blueprint) ·
[Signaler un problème](https://github.com/Kerlann/blueprint/issues)
