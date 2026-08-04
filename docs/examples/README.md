# Blueprints d'exemple

Six graphes courts, prêts à lire et à charger. Chacun tient en moins de douze nœuds
et montre **un** mécanisme.

> **Fichiers générés** — ne pas modifier à la main. Ils sont produits depuis
> `ExampleBlueprints.java` par `ExampleBlueprintsTest`, qui les valide un par un et
> fait échouer la construction s'ils divergent. Un exemple qui ne compile pas est pire
> que pas d'exemple : il apprend une erreur, et il l'apprend avec autorité.
>
> Régénérer : `./gradlew :core:test --tests "*ExampleBlueprintsTest" -Dblueprint.regenDocs=true`

## Les charger

**En jeu**, tous d'un coup :

```
/blueprint examples
```

Ils arrivent **désactivés** — l'un d'eux pose des blocs, et un exemple qui se met à
tourner avant qu'on l'ait lu changerait le monde sans prévenir. Ouvre-les dans
l'éditeur (`/blueprint-edit blueprint:example/<nom>`), lis-les, puis :

```
/blueprint enable blueprint:example/jour_et_nuit
```

**Depuis un fichier** : copie le `.bp` dans `blueprint/exports/`, puis
`/blueprint import <nom>`. C'est du texte — tu peux aussi le coller dans la vue script
de l'éditeur.

## Les huit

Les six premiers enseignent le **graphe** ; les deux derniers, ce qu'on met **devant le
joueur**.

| Fichier | Ce qu'il fait | Ce qu'il apprend |
|---|---|---|
| [`jour_et_nuit.bp`](jour_et_nuit.bp) | À la connexion : « Bonjour » ou « Bonne nuit » en titre | Le plus simple des huit — un événement, une lecture du monde, un choix |
| [`porte_secrete.bp`](porte_secrete.bp) | Clic droit sur un bloc → pose de la pierre sur la face touchée | `pos/relative`, `world/block_state`, plafond `WORLD` |
| [`compteur_de_blocs.bp`](compteur_de_blocs.bp) | Compte les blocs cassés, affiche le total en barre d'action | Le **scoreboard** plutôt qu'une variable : `/scoreboard` et l'affichage latéral le voient aussi |
| [`balise_de_soin.bp`](balise_de_soin.bp) | Toutes les 5 s, soigne le joueur le plus proche d'un point | Requête → branchement → action : le squelette de la plupart des scripts |
| [`relais_de_signal.bp`](relais_de_signal.bp) | « alerte » dans le chat → titre d'alarme pour tout le monde | Le **signal** : les deux moitiés ne sont reliées que par une chaîne de caractères, donc l'une peut vivre dans un autre blueprint |
| [`annonce_de_mort.bp`](annonce_de_mort.bp) | Annonce la mort d'un joueur, avec une infobulle au survol | `entity/as_player` (l'événement rend une *entité*) et le **texte riche** |
| [`guichet.bp`](guichet.bp) | `/blueprint run guichet` : un menu avec un compteur de jetons et deux boutons | Un **écran** de bout en bout : une colonne qui range ses enfants sans qu'aucune coordonnée soit écrite, un **style nommé** porté par les deux boutons, une étiquette **liée** à une variable, et le clic qui repart dans le graphe |
| [`reglement.bp`](reglement.bp) | `/blueprint run reglement` : une page de règles qu'on parcourt | Un écran qu'on **lit** plutôt qu'on manipule : un **panneau défilant**, du texte qui **revient à la ligne** (porté par le style, donc un sixième paragraphe n'a qu'à être écrit), des **infobulles**, et `gui/set_scroll` pour le bouton « Haut de page » |

## Les modifier

Ils sont à toi une fois créés : renomme-les, change les valeurs, recâble. Deux réflexes
qui évitent les surprises :

- **`/blueprint disable` avant de modifier** un graphe qui tourne sur `server_tick`.
- La **barre de diagnostics** en bas de l'éditeur dit ce qui bloque ; un pin obligatoire
  non câblé y apparaît avant même d'essayer d'activer.

Pour comprendre un nœud que tu ne connais pas, **survole-le** — l'infobulle donne son
rôle, et l'infobulle d'un pin donne son type.
