# Concepteur d'écrans — session de vérification manuelle

Ce qui suit ne se teste pas sans fenêtre. Le reste est couvert : ce document ne répète pas
ce que les tests protègent déjà, il liste ce qu'aucun d'eux ne peut voir.

Les décisions — quelle rangée où, ce que demande un clic, quel champ pour quel type, où
cadrer — sont pures et vérifiées dans `DesignerPaletteTest`, `ElementPropertiesStateTest`,
`DesignCameraTest` et `ScreenCanvasControllerTest`. Ce qui reste ici est le **dessin**, les
**gestes continus** et ce qui dépend de la taille réelle de la fenêtre.

## Mise en place

```
./gradlew runClient
/blueprint create test:menu
/blueprint edit test:menu        →  onglet Écrans
```

## 1. La colonne de gauche

| # | Geste | Attendu |
|---|---|---|
| 1.1 | Regarder la colonne | Trois sections **séparées** par un filet, chacune avec son en-tête ; aucun texte tronqué |
| 1.2 | Cliquer le « + » de ÉCRANS | Un écran apparaît et devient actif |
| 1.3 | Regarder la ligne active | Elle porte trois signes à droite : `M`/`H`, `✎`, `×` — et **elle seule** |
| 1.4 | Cliquer `✎`, taper, Entrée | Le nom **se voit pendant la frappe**, avec un curseur |
| 1.5 | Créer huit écrans, molette sur la colonne | La colonne défile ; les calques restent atteignables |
| 1.6 | Poser un panneau puis un texte dedans | Le texte apparaît **sous** le panneau, indenté, avec un chevron sur le parent |
| 1.7 | Cliquer le chevron | La branche se replie ; les petits-enfants disparaissent aussi |
| 1.8 | Cliquer l'œil d'un calque | Il bascule `◉`/`○` et l'élément disparaît du canevas — **sans** changer la sélection |
| 1.9 | Glisser un calque sur un autre | Un liseré marque la cible ; au relâchement il devient son enfant |
| 1.10 | Glisser un calque sur l'en-tête CALQUES | Il ressort à la racine |
| 1.11 | Glisser un parent sur son propre enfant | **Aucune** cible ne s'allume — le geste ne propose pas ce qu'il refuserait |

## 2. Le panneau de droite

| # | Geste | Attendu |
|---|---|---|
| 2.1 | Cliquer le vide du canevas | Le panneau décrit l'**écran** : nom, Modal/HUD, nombre d'éléments |
| 2.2 | Cliquer la pastille Modal | Elle passe à HUD, et le canevas le reflète |
| 2.3 | Sélectionner un **texte** | Ni « Indication », ni « Pas », ni « Type d'entité », ni « Hauteur de ligne » |
| 2.4 | Sélectionner une **saisie** | « Indication » et « Longueur max » apparaissent **une seule fois** |
| 2.5 | Lier l'élément à une variable | Les **cinq** cibles tiennent dans le panneau et se cliquent toutes |
| 2.6 | Regarder Largeur | Sa valeur est **lisible**, et les quatre modes sont sur leur propre rangée |
| 2.7 | Passer la largeur en « Ajuster » | La valeur disparaît : elle vient des enfants |
| 2.8 | Sélectionner un conteneur lié, dérouler | Le panneau **défile** ; le bas est atteignable |
| 2.9 | Taper une largeur puis cliquer l'élément | La valeur est **validée**, pas jetée |

## 3. La zone de travail

| # | Geste | Attendu |
|---|---|---|
| 3.1 | Ouvrir un écran qui a des éléments | La vue s'ouvre **sur eux**, à un zoom où le texte se lit |
| 3.2 | Ouvrir un écran vide | La vue cadre la zone garantie, pas les 1920×1080 |
| 3.3 | Sortir un élément de la zone garantie | Cerne orange **et** message dans la barre du bas |
| 3.4 | Corriger | Le message disparaît, sans avoir à changer d'onglet |
| 3.5 | Cliquer dans la bande du haut, juste sous la barre | Rien ne se sélectionne |
| 3.6 | Cliquer la dernière rangée de la colonne, tout en bas | Elle répond — plus de bande morte |
| 3.7 | Provoquer un refus (élément trop petit) | Le message est **au-dessus** des pastilles, pas dessus |

## 4. La barre du bas

| # | Geste | Attendu |
|---|---|---|
| 4.1 | Sélectionner deux éléments, cliquer `⊢` | Ils s'alignent à gauche |
| 4.2 | Les six signes `⊢⊹⊣⊤⊸⊥` | Chacun fait ce que fait sa touche du pavé numérique |
| 4.3 | Sélectionner **un seul** élément, cliquer `⊹` | Il se centre dans son **parent** |
| 4.4 | Les préréglages de fenêtre | Le canevas change de taille, la mise en page suit |

## 5. Ce qui doit tourner

| # | Geste | Attendu |
|---|---|---|
| 5.1 | `Ctrl+S` puis rouvrir | Tout est là, arbre compris |
| 5.2 | `Ctrl+Z` après un reparentage | L'élément retrouve son parent d'avant |
| 5.3 | Ouvrir l'écran en jeu | Il ressemble à ce que montrait le concepteur |
| 5.4 | Sur un **serveur dédié** : éditer, `Ctrl+S` | Le blueprint arrive avec son écran |

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
