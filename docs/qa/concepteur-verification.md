# Concepteur d'écrans — session de vérification manuelle

Ce qui suit ne se teste pas sans fenêtre. Le reste est couvert : ce document ne répète pas
ce que les tests protègent déjà, il liste ce qu'aucun d'eux ne peut voir.

Les décisions — quelle rangée où, ce que demande un clic, quel champ pour quel type, où
cadrer — sont pures et vérifiées dans `DesignerPaletteTest`, `ElementPropertiesStateTest`,
`ChipWrapTest`, `DesignCameraTest` et `ScreenCanvasControllerTest`. Ce qui reste ici est le
**dessin**, les **gestes continus** et ce qui dépend de la taille réelle de la fenêtre.

## Comment lire la colonne « garde »

Chaque ligne dit quel test protège déjà la **décision** correspondante.

- Une ligne avec une garde qui échoue en jeu ne signifie pas « la décision est fausse » :
  le test la tient. Elle signifie que **le dessin diverge de la décision** — c'est-à-dire
  qu'on peint ou qu'on clique ailleurs qu'à l'endroit décidé. C'est le piège qui a mordu ce
  panneau deux fois, et c'est là qu'il faut chercher en premier.
- Une ligne **sans** garde est un jugement à l'œil : elle n'a pas d'autre juge que vous.

## Mise en place

```
./gradlew runClient
/blueprint create test:menu
/blueprint edit test:menu        →  onglet Écrans
```

## 1. La colonne de gauche

| # | Geste | Attendu | Garde |
|---|---|---|---|
| 1.1 | Regarder la colonne | Trois sections **séparées** par un filet, chacune avec son en-tête ; aucun texte tronqué | — |
| 1.2 | Regarder les douze types | **Douze pictogrammes distincts**. Étiquette, Image, Barre, Emplacement et Entité ne doivent PAS partager la même forme | — |
| 1.3 | Cliquer le « + » de ÉCRANS | Un écran apparaît et devient actif | `lePlusVitDansLEnTete` |
| 1.4 | Regarder la ligne active | Elle porte trois signes à droite : `M`/`H`, `✎`, `×` — et **elle seule** | `lesActionsDUnEcranNexistentQueSurLaLigneActive` |
| 1.5 | Cliquer `✎`, taper, Entrée | Le nom **se voit pendant la frappe**, avec un curseur | `celuiQuOnRenommeMontreLaFrappe` |
| 1.6 | Créer huit écrans, molette sur la colonne | La colonne défile ; les calques restent atteignables | `lesCalquesRestentAtteignablesAvecBeaucoupDEcrans` |
| 1.7 | Poser un panneau puis un texte dedans | Le texte apparaît **sous** le panneau, indenté, avec un chevron sur le parent | `lesCalquesFormentUnArbre` |
| 1.8 | Cliquer le chevron | La branche se replie ; les petits-enfants disparaissent aussi | `unParentReplieCacheToutSaDescendance` |
| 1.9 | Cliquer l'œil d'un calque | Il bascule `◉`/`○` et l'élément disparaît du canevas — **sans** changer la sélection | `lOeilBasculeLaVisibiliteIlNeSelectionnePas` |
| 1.10 | Glisser un calque sur un autre | Un liseré marque la cible ; au relâchement il devient son enfant **sans bouger d'un pixel sur l'écran** | `glisserUnCalqueSurUnAutreLeLuiDonnePourParent` (la cible), `ReparentKeepsPlaceTest` (la place) |
| 1.11 | Glisser un calque sur l'en-tête CALQUES | Il ressort à la racine, toujours sans bouger | `lEnTeteDesCalquesSortDeSonConteneur`, `sortirUnElementNeLeDeplacePasNonPlus` |
| 1.12 | Glisser un parent sur son propre enfant | **Aucune** cible ne s'allume — le geste ne propose pas ce qu'il refuserait | `onNeProposeJamaisUneCibleQueLeModeleRefuserait` |

## 2. Le panneau de droite

| # | Geste | Attendu | Garde |
|---|---|---|---|
| 2.1 | Cliquer le vide du canevas | Le panneau décrit l'**écran** : nom, Modal/HUD, nombre d'éléments | — |
| 2.2 | Cliquer la pastille Modal | Elle passe à HUD, et le canevas le reflète | — |
| 2.3 | **Lire toutes les pastilles** | Aucun mot coupé au milieu. Ni `Detac`, ni `Activ`, ni `Visib`, ni `Forma` | `ChipWrapTest` |
| 2.4 | Regarder un libellé long (Arrière-plan) | Le libellé ne se peint **pas par-dessus** sa valeur | — |
| 2.5 | Regarder « Texte » et « Infobulle », **vides** | Un creux marque le champ : on voit où cliquer avant d'avoir tapé | — |
| 2.6 | Sélectionner un **texte** | Ni « Indication », ni « Pas », ni « Type d'entité », ni « Hauteur de ligne » | `unTypeNeMontreQueCeQuiLeConcerne` |
| 2.7 | Sélectionner une **saisie** | « Indication » et « Longueur max » apparaissent **une seule fois** | `aucunChampNestProposeDeuxFois` |
| 2.8 | Lier l'élément à une variable | Les **cinq** cibles se lisent en entier | `ChipWrapTest`, `ChipHitTest` — le clic de la seconde ligne est prouvé, il n'a plus à se vérifier à la main |
| 2.9 | Regarder Largeur | Sa valeur est **lisible**, et les quatre modes sont sur leur propre rangée | — |
| 2.10 | Passer la largeur en « Ajuster » | La valeur disparaît : elle vient des enfants | `laValeurDunAxeEnAjusterNeVeutRienDire` |
| 2.11 | Passer la largeur en « Remplir » | La valeur montre le **poids** (`fill:1`), pas le seul mot « fill » | `unRemplissageMontreSaPart` |
| 2.12 | Appliquer un style nommé, en avoir plusieurs | « Détacher » n'apparaît que sur le style **appliqué**, et s'écrit en entier | `unStyleNeSeDetacheQueSilEstApplique` (la règle), `PanelMessageWidthTest` (la largeur) |
| 2.13 | Sélectionner un conteneur lié, dérouler | Le panneau **défile** ; le bas est atteignable | — |
| 2.14 | Taper une largeur puis cliquer l'élément | La valeur est **validée**, pas jetée | — |

## 3. La zone de travail

| # | Geste | Attendu | Garde |
|---|---|---|---|
| 3.1 | Ouvrir un écran qui a des éléments | La vue s'ouvre **sur eux**, à un zoom où le texte se lit | `leCadrageSuitLeContenuEtNonLaPlaceDisponible` |
| 3.2 | Ouvrir un écran vide | La vue cadre la zone garantie, pas les 1920×1080 | `leCadrageMontreToutLeCanevas` |
| 3.3 | Sortir un élément de la zone garantie | Cerne orange **et** message dans la barre du bas | — |
| 3.4 | Corriger | Le message disparaît, sans avoir à changer d'onglet | — |
| 3.5 | Cliquer dans la bande du haut, juste sous la barre | Rien ne se sélectionne | — |
| 3.6 | Cliquer la dernière rangée de la colonne, tout en bas | Elle répond — plus de bande morte | — |
| 3.7 | Provoquer un refus (élément trop petit) | Le message est **au-dessus** des pastilles, pas dessus | — |

## 4. La barre du bas

| # | Geste | Attendu | Garde |
|---|---|---|---|
| 4.1 | Sélectionner deux éléments, cliquer `←` | Ils s'alignent à gauche | — |
| 4.2 | Les six flèches `← ↔ → ↑ ↕ ↓` | Chacune fait ce que fait sa touche du pavé numérique. `↔` agit en **largeur**, `↕` en **hauteur** | — |
| 4.3 | Sélectionner **un seul** élément, cliquer `↔` | Il se centre dans son **parent** | — |
| 4.4 | Les préréglages de fenêtre | Le canevas change de taille, la mise en page suit | — |

## 5. Ce qui doit tourner

| # | Geste | Attendu | Garde |
|---|---|---|---|
| 5.1 | `Ctrl+S` puis rouvrir | Tout est là, arbre compris | `ScreenNbtTest`, `ceQuiSafficheSeRetapeAlIdentique` |
| 5.2 | `Ctrl+Z` après un reparentage | L'élément retrouve son parent d'avant | `modifierUnElementSeDefait` (le reparentage passe par `SetElement`, il n'a pas d'opération à lui) |
| 5.3 | Ouvrir l'écran en jeu | Il ressemble à ce que montrait le concepteur | `aColumnLayoutScreenOpensAndTheRightButtonRuns` (gametest) |
| 5.4 | Sur un **serveur dédié** : éditer, `Ctrl+S` | Le blueprint arrive avec son écran | `GraphSyncTest`, `ScreenSyncTest` |

## Limites connues, à ne pas signaler comme défauts

- **`Source.CLIENT`, `maxVariable`, `Extent.min/max`, `enabled`, `align`, `borderWidth`,
  `pressedBackground`, `disabledBackground`, `live`** sont au modèle et n'ont aucune
  interface. Ce sont des **capacités manquantes**, pas des défauts d'ergonomie.
  `Source.CLIENT` mérite sa propre story : c'est la différence entre un HUD qui coûte un
  paquet par tick et un HUD qui ne coûte rien.
- **Un seul diagnostic à la fois** dans la barre. Corriger le premier fait apparaître le
  suivant ; une liste dépliable serait un quatrième panneau dans une fenêtre qui en compte
  trois.
- **Le clic droit n'a pas de menu contextuel** dans le concepteur, contrairement au canevas
  de nœuds. Il agit comme un clic gauche.
- **Pas de sélection multiple dans l'arbre des calques** : un clic, un élément. La
  sélection multiple se fait au canevas.
- **La largeur d'une pastille est estimée**, pas mesurée : six pixels par caractère, comme
  `NodeGeometry`. Mesurer demanderait la police et rendrait la décision invérifiable sans
  client. Une pastille un peu large n'est donc pas un défaut ; un mot **coupé** en est un.

## Historique des faux défauts

Ce que cette session a déjà fait signaler à tort. À relire avant d'ouvrir un ticket.

- **« Forma » entre « Fixed » et « Fill »** : ce n'était pas une troncature mais
  « Format error: blueprint.designer.size.percent ». Le libellé valait `%`, que
  `String.format` refuse. Corrigé, et gardé par `everyTranslationSurvivesFormatting` —
  **toute** valeur qui casse le formatage rougit désormais.
- **Une rangée `4` sous `fiche`** dans la liste des écrans : c'était un second écran,
  réellement nommé `4`, au même retrait que le premier.
