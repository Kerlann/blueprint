# UX/UI Spec — éditeur Blueprint

**Agent BMAD :** UX Expert (Sally)
**Version :** 1.0 — 2026-08-02
**Couvre :** épic 5, plus les aspects UI des épics 8 et 9

---

## 1. Principes

| # | Principe | Traduction concrète |
|---|---|---|
| U1 | **Découvrable avant documenté** | Palette contextuelle filtrée par type : tirer un lien montre uniquement ce qui se branche |
| U2 | **Jamais de perte silencieuse** | Toute action destructrice est annulable ; un retypage prévient avant d'invalider |
| U3 | **L'erreur pointe un nœud** | Aucun message générique ; cliquer un diagnostic recentre sur le coupable |
| U4 | **Lisible en un coup d'œil** | Couleur ET forme par type, catégorie par teinte d'en-tête |
| U5 | **Le clavier suffit** | Toute action a un raccourci ; navigation clavier complète |
| U6 | **Le texte est une porte de sortie** | Vue BScript toujours accessible : copier, coller, partager |

---

## 2. Disposition

```
┌───────────────────────────────────────────────────────────────────────────┐
│  mypack:porte_secrete    [Compiler] [Tester] [Script] [Auto] [Aide]   ×    │  barre 24 px
├──────────────┬────────────────────────────────────────────┬───────────────┤
│ VARIABLES    │                                            │  DÉTAILS      │
│  ○ ouvertures│              CANEVAS                       │   nœud        │
│  ○ verrouil. │        (pan · zoom · grille)               │   sélectionné │
│  ○ essais    │                                            │               │
│              │      ┌─────────────┐    ┌────────────┐     │  type         │
│ ÉVÉNEMENTS   │   ▶──│ on use_block│────│  Branch    │──▶  │  permission   │
│  ▸ use_block │      │  player  ○──┼──○─│ condition  │     │  littéraux    │
│  ▸ signal    │      │  pos     ○  │    └────────────┘     │  config       │
│              │      └─────────────┘                       │               │
│ FONCTIONS    │                                            │               │
│  ƒ (v1.1)    │                                    ┌─────┐ │               │
│              │                                    │ mini│ │               │
├──────────────┴────────────────────────────────────┴─────┴─┴───────────────┤
│ ⚠ 2 avertissements  ✕ 1 erreur                                            │  barre d'état
│ ✕ Le pin « amount » de « heal_player » n'est pas connecté      → aller     │
└───────────────────────────────────────────────────────────────────────────┘
```

Panneaux latéraux repliables (Tab). En dessous de 800 px de large, les panneaux passent
en superposition plutôt qu'en colonne.

---

## 3. Canevas

| Action | Entrée |
|---|---|
| Déplacer la vue | Clic milieu glissé · Espace + clic gauche · bord de l'écran en glissant un nœud |
| Zoomer | Molette (0,25× → 2,00×, 8 crans) · Ctrl +/− |
| Recentrer sur tout | `F` |
| Recentrer sur la sélection | `Shift+F` |
| Sélection rectangle | Clic gauche glissé dans le vide |
| Ajouter à la sélection | Shift + clic |
| Menu contextuel | Clic droit |

- **Grille** : pas de 16 px, accroche optionnelle (`Ctrl+G`), atténuée sous 0,5× de zoom.
- **Niveau de détail** : sous 0,5×, les nœuds se rendent en boîtes colorées avec le seul
  titre ; sous 0,35×, sans titre. Le culling précède tout calcul (NFR1).

---

## 4. Nœuds

```
        ┌──────────────────────────────┐
   exec ▶│ ◆ Soigner le joueur      🔒 │  en-tête teinté par catégorie
        ├──────────────────────────────┤
        │ ● player                     │  pin data d'entrée (connecté)
        │ ● amount   [ 1.0        ]    │  pin data + champ littéral inline
        │                    healed ●  │  pin data de sortie
        │                        exec ▶│
        └──────────────────────────────┘
```

- **En-tête** : icône de catégorie, titre traduit, badge de permission (`🔒` = `ADMIN`),
  badge du mod fournisseur si ce n'est pas `blueprint`.
- **Pins** : ● cercle = data · ▶ triangle = exec · ◆ losange = objet · ▦ = liste.
  La **forme** double la couleur (NFR11).
- **Littéraux inline** : le contrôle dépend du type — case à cocher, champ numérique à
  molette, champ texte, liste déroulante d'énumération, sélecteur d'item/bloc avec aperçu,
  sélecteur de position avec bouton « position du joueur ».
- **Nœud fantôme** : bordure rouge hachurée, titre = identifiant brut, message
  « Fourni par `mymod` — mod absent ». Les pins déduits des liens sont conservés et grisés.
- **Nœud en faute** : bordure orange + icône, infobulle avec la dernière erreur.

**Sélection et manipulation** : déplacement (accroche si active), `Suppr`, `Ctrl+D`
dupliquer, `Ctrl+X/C/V` via BScript, `Q` aligner, `Ctrl+Shift+A` auto-layout.

---

## 5. Liens

- Courbe de Bézier, tangente horizontale, épaisseur 2 px (data) / 3 px (exec).
- **Couleur = type**, palette validée en deutéranopie et protanopie.
- **Pendant un glisser** : les pins compatibles pulsent, les incompatibles passent à 30 %
  d'opacité. Le curseur affiche le type transporté.
- **Relâcher dans le vide** → palette contextuelle **filtrée par le type du pin**, et le
  nœud choisi se connecte automatiquement au bon pin. C'est l'interaction centrale (U1).
- **Alt + clic** sur un pin détache son lien ; **Ctrl + glisser** depuis un pin connecté
  déplace le lien.
- **Conversion implicite** : si le lien nécessite une coercition (`int` → `double`), un
  petit losange apparaît au milieu du lien. Une conversion explicite requise propose
  d'insérer le nœud de conversion en un clic.
- **Reroute** : double-clic sur un lien insère un point de routage.

---

## 6. Palette

Ouverture : clic droit dans le vide, `Espace`, ou relâchement d'un lien dans le vide.

```
┌── Ajouter un nœud ───────────── filtre : ● double ──┐
│ 🔍 heal|                                            │
├─────────────────────────────────────────────────────┤
│ ★ Soigner le joueur            mymod    ENTITÉ      │
│   Soigner l'entité             mymod    ENTITÉ      │
│   Santé de                     blueprint PUR        │
├─────────────────────────────────────────────────────┤
│ Récents ▸  Favoris ▸  Toutes les catégories ▸       │
└─────────────────────────────────────────────────────┘
```

- Recherche **floue** sur titre, description, alias et nom du mod ; ≤ 5 ms pour
  2 000 types de nœuds.
- Quand elle est ouverte depuis un lien, seuls les nœuds ayant un pin compatible
  apparaissent, et l'entête rappelle le filtre actif.
- Les nœuds au-dessus du plafond de permission du blueprint sont visibles mais grisés,
  avec la raison — jamais masqués sans explication (U2).
- `↑/↓` naviguer, `Entrée` insérer, `Échap` fermer, `Ctrl+Entrée` insérer sans connecter.

---

## 7. Panneau des variables

- Liste par portée, pastille de couleur du type.
- `+` crée ; double-clic renomme ; menu contextuel pour retyper et changer la portée.
- **Glisser dans le canevas** → nœud `Get`. **Ctrl + glisser** → nœud `Set`.
- Un retypage incompatible ouvre une confirmation listant les liens qui seront invalidés,
  avec un aperçu — et reste annulable (U2).
- Une variable non utilisée est marquée d'un point discret, pas d'un avertissement bruyant.

---

## 8. Diagnostics

- Compilation **débouncée** (≈ 300 ms après la dernière frappe) ; jamais bloquante.
- Barre d'état : compteurs erreurs/avertissements ; cliquer déplie la liste.
- Chaque entrée : sévérité, message traduit, nom du nœud → **cliquer recentre et
  surligne** le nœud (U3).
- Le nœud fautif porte un liseré de la couleur de la sévérité ; l'infobulle donne le détail.
- Sur erreur bloquante, le bouton **Tester** est désactivé avec la raison en infobulle.

---

## 9. Vue script

`Script` bascule en vue partagée graphe | BScript.

- Coloration syntaxique, numéros de ligne.
- **Sélection synchronisée** : sélectionner un nœud surligne son bloc, et inversement.
- v1.0 : lecture seule + Copier / Exporter / Importer.
- v1.1 : édition avec re-parse à la volée.

---

## 10. Mode débogage

- Activé par **Tester** ou `/blueprint debug <id>` ; réservé aux joueurs autorisés.
- Le lien exec actif **pulse** dans le sens du flux ; le nœud courant a un halo.
- Chaque pin de données affiche sa valeur courante en surimpression (tronquée, complète
  au survol).
- Contrôles : `Pause`, `Pas à pas` (`F10`), `Continuer` (`F5`), points d'arrêt sur nœud
  (clic sur la pastille de l'en-tête).
- Panneau **Surveillance** : valeurs épinglées, historique des N derniers passages.
- Coût nul quand le débogage est éteint : le serveur n'émet aucune trace sans abonné.

---

## 11. Raccourcis

| Raccourci | Action | | Raccourci | Action |
|---|---|---|---|---|
| `Espace` | Palette | | `Ctrl+Z` / `Ctrl+Y` | Annuler / Rétablir |
| `Clic droit` | Menu contextuel | | `Ctrl+S` | Enregistrer |
| `F` / `Shift+F` | Recentrer tout / sélection | | `Ctrl+C/X/V` | Copier / Couper / Coller (BScript) |
| `Ctrl+G` | Accroche à la grille | | `Ctrl+D` | Dupliquer |
| `Suppr` | Supprimer | | `Ctrl+Shift+A` | Auto-layout |
| `Q` | Aligner | | `Ctrl+F` | Rechercher un nœud |
| `Tab` | Replier les panneaux | | `F5` / `F10` | Continuer / Pas à pas |
| `C` | Boîte de commentaire autour de la sélection | | `Échap` | Fermer / annuler l'action |

---

## 12. Thème

`assets/blueprint/theme/default.json` — jetons inspirés de CSS, rechargeables à chaud en dev :

```json
{
  "canvas":  { "background": "#1A1B1E", "grid": "#242629", "gridMajor": "#2E3135" },
  "node":    { "background": "#2B2D31", "border": "#3A3D42", "borderSelected": "#7AA2F7",
               "radius": 4, "titleHeight": 18, "shadow": "#00000055" },
  "category":{ "flow": "#8A8F98", "math": "#7DCFFF", "world": "#9ECE6A",
               "entity": "#E0AF68", "player": "#BB9AF7", "debug": "#F7768E" },
  "pin":     { "exec": "#E6E6E6", "bool": "#E06C75", "int": "#56B6C2",
               "double": "#98C379", "string": "#E5C07B", "any": "#ABB2BF" },
  "wire":    { "width": 2.0, "widthExec": 3.0, "tension": 0.55 },
  "state":   { "error": "#F7768E", "warning": "#E0AF68", "ghost": "#C74A5B",
               "debugActive": "#FFD866" }
}
```

> ACsGuis (CSS pour GUI Minecraft) est un projet **Forge 1.12** et n'est pas utilisable
> sur Fabric 1.21.11. Ce thème JSON en reprend l'idée — styliser sans recompiler — dans
> un format supporté par la plateforme cible.

---

## 13. Accessibilité

- **Daltonisme** : palette vérifiée en deutéranopie/protanopie ; la **forme du pin** porte
  l'information indépendamment de la couleur (NFR11). Un thème « fort contraste » est fourni.
- **Clavier** : navigation entre nœuds par flèches, `Entrée` pour éditer un pin,
  `Tab`/`Shift+Tab` entre pins, création et câblage possibles sans souris (U5).
- **Lisibilité** : taille de texte suivant l'échelle GUI de Minecraft ; jamais de texte
  sous 6 px effectifs — en dessous, on passe au niveau de détail réduit.
- **Mouvement** : les animations (pulsation des liens en débogage) sont désactivables.
- **Langue** : `en_us` et `fr_fr` complets, y compris les diagnostics (NFR10).

---

## 14. Premiers pas

Au premier blueprint créé, le canevas est vide avec une invite centrale :
« Appuyez sur **Espace** pour ajouter un nœud, ou commencez par un **événement** ».
Un modèle « Bonjour le monde » (événement `player_join` → message de chat) est insérable
en un clic. Aucun tutoriel modal, aucun blocage : l'objectif du brief est un premier
blueprint fonctionnel en moins de 10 minutes sans documentation.

---

## 15. Concepteur d'écrans

Cette section a été écrite **après** les seize stories de l'épic 10, et c'est le fait
notable : le concepteur est le seul mode de l'éditeur à n'avoir jamais eu de section ici.
Chaque story y a posé une capacité sans que personne ne révise la disposition. Résultat
mesuré à la relecture : trois listes en texte brut dans une colonne de 92 px, onze champs
sans objet sur chaque type, deux réglages hors d'atteinte faute de place, et des calques
inatteignables dès huit écrans.

Les principes U1–U6 du §1 s'appliquent ici sans exception. Ce qui suit ne fait que dire à
quoi ils ressemblent quand on dessine un menu au lieu de câbler un graphe.

### Disposition

```
┌───────────────────────────────────────────────────────────────────────────┐
│  mypack:porte_secrete    [Graphe] [Écrans] [Fonctions]      [Compiler] ×   │  barre 16 px
├──────────────┬────────────────────────────────────────────┬───────────────┤
│ ÉCRANS    +  │                                            │  ÉCRAN        │
│  guichet M✎× │                                            │   guichet     │
├──────────────┤            ZONE DE TRAVAIL                 │   Modal       │
│ ÉLÉMENTS     │       (canevas simulé · marge · grille)    │   3 éléments  │
│ Conteneurs   │                                            │               │
│  ▣ Panneau   │      ┌───────────────────────┐             │  — ou —       │
│ Affichage    │      │  Guichet              │             │               │
│  ▤ Texte     │      │  ┌─────────────────┐  │             │  Identité     │
│  ▣ Image     │      │  │ Prendre un jeton│  │             │  Position     │
│ Interactifs  │      │  └─────────────────┘  │             │  Taille       │
│  ▭ Bouton    │      └───────────────────────┘             │  Apparence    │
├──────────────┤                                            │  Liaison      │
│ CALQUES      │                                            │  Styles       │
│ ▾ ◉ cadre    │                                            │               │
│    ◉ titre   │                                            │               │
│  ◉ fermer    │                                            │               │
├──────────────┴────────────────────────────────────────────┴───────────────┤
│ ✕ « titre » sort de la zone garantie          1920×1080 · 412,88 · 60×20  │
│ Fenêtre : 320 480 640 960 1280 1920  la mienne  − 47% +  cadrer  ⊢⊹⊣⊤⊸⊥  F1│
└───────────────────────────────────────────────────────────────────────────┘
```

Deux colonnes repliables (`Tab`), comme le canevas de nœuds. Ce qui vaut pour lui vaut
ici : **une seule source pour la géométrie**, lue par le dessin *et* par le clic. Les
largeurs vivent dans `DesignerPanels`, les hauteurs dans `DesignerPalette`. Ce projet a
payé trois fois pour l'avoir oublié — le panneau des variables, les sections de la
palette, et le filet tracé au milieu de « supprimer l'écran ».

### La colonne de gauche

**Une seule liste défilante** pour les trois sections. Empilées sans défilement, elles se
poussaient l'une l'autre hors de la fenêtre.

**Les commandes ne sont pas des données.** Créer vit dans l'en-tête (`+`) ; renommer,
supprimer et changer de nature vivent sur la **ligne active**. Une commande qui a
l'apparence d'un nom d'écran se clique par erreur, et coûte un écran.

**La palette groupe par ce que le modèle sait dire d'un type** — conteneur, affichage,
interactif — avec un pictogramme et une teinte par famille. Un classement inventé se
dément au premier type ajouté ; `container()` et `interactive()`, non.

**Les calques sont un arbre**, pas une liste indentée : chaque enfant sous son parent, du
dessus vers le dessous. Un parent se replie et cache sa descendance entière. L'œil bascule
la visibilité — s'il est dessiné, il se clique. **Glisser un calque sur un autre le
reparente** ; le lâcher sur l'en-tête le sort de son conteneur. On ne propose jamais une
cible que le modèle refuserait.

### Le panneau de droite

**Rien de sélectionné → l'écran.** Son nom, sa nature, son compte d'éléments. Une colonne
de 128 px réservée à « Sélectionnez un élément » est une colonne perdue.

**Un champ n'apparaît que s'il agit.** La règle est un `switch` exhaustif par
`ElementKind`, sans `default` : un treizième type ne compilera pas tant qu'on n'aura pas
dit ce qu'il montre. Un champ rempli sans effet fait douter d'un outil plus sûrement qu'un
champ absent.

**Aucun réglage hors d'atteinte.** Au-delà de trois valeurs, les pastilles prennent leur
propre rangée : mieux vaut une ligne de plus qu'une pastille dehors. Le panneau défile.

### Les diagnostics, au moment du geste

Le premier reproche du validateur s'affiche dans la barre du bas **pendant qu'on dessine**.
Ils ne vivaient que dans l'onglet Graphe : l'auteur ne les voyait jamais, et un
débordement de la zone garantie revenait sous forme de rapport de bug d'un joueur en
*GUI scale* 4. La validation est débouncée, comme celle du graphe.

Le cerne orange du débordement reste : un diagnostic qui **désigne** vaut mieux qu'un
diagnostic qui décrit.

### Le cadrage

À l'ouverture, on cadre le **contenu** — les éléments posés, à défaut la fenêtre garantie
320×180. Cadrer le canevas entier de 1920×1080 donnait 21 % de zoom en échelle GUI 3 : le
texte y est illisible et la grille disparaît, si bien qu'on ignore si l'accroche est
active. C'est ce que la touche `F` du canevas de nœuds fait de ses nœuds depuis la 5.1.

Le canevas de conception reste 1920×1080 par défaut, et c'est délibéré : on dessine au
large, puis on vérifie en réduisant. L'ancre automatique de `addElement` en est la
contrepartie obligatoire.

### Raccourcis

| Geste | Effet |
|---|---|
| Molette | Défile le panneau survolé, sinon zoome le canevas |
| Clic milieu · `Espace` + clic | Déplacer la vue |
| `F` · `Ctrl+0` · `+` / `−` | Cadrer · 1:1 · un cran |
| `Tab` | Replier / rouvrir les deux colonnes |
| Flèches (`Maj` ×10) | Décaler d'une unité — ou changer de rang dans un conteneur qui range |
| `Suppr` · `Ctrl+D` · `Ctrl+C`/`V` · `Ctrl+A` | Supprimer · dupliquer · copier-coller · tout |
| `Ctrl+H` · `G` | Masquer la sélection · accroche à la grille |
| `Pg↑` / `Pg↓` | Ordre de dessin |
| Pavé num. `4`/`5`/`6` · `8`/`0`/`2` | Aligner — **et six boutons dans la barre**, pour les claviers sans pavé |
| Pavé num. `+` / `−` | Répartir (3 éléments et plus) |
| `Échap` | Désélectionne ; ne ferme qu'ensuite |
| `F1` | L'aide, qui liste tout ceci |

**Toute action a un bouton ou un raccourci découvrable** (U5, U1). Un geste réservé au
pavé numérique n'existe pas pour qui n'en a pas.
