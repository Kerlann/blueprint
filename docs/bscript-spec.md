# BScript — spécification du langage

**Statut :** décrit le langage **tel que le parseur l'accepte**, vérifié construction par
construction contre `ScriptParser`.
**Principe :** BScript et le graphe sont **deux vues de la même donnée**. Toute
construction du langage a une contrepartie exacte dans le graphe, et réciproquement.

> **Ce document a menti pendant longtemps.** Il décrivait un langage à syntaxe familière —
> `x = x + 1`, `if (a > b)`, `wait 20t`, commentaires `//` — dont **rien** n'est
> implémenté. Un moddeur qui le suivait écrivait du BScript refusé dès sa troisième ligne.
> Ce qui suit a été établi en soumettant chaque forme au parseur. La liste de ce qui
> n'existe pas est au §9, parce qu'un lecteur qui a connu l'ancienne version a besoin de
> savoir ce qui a disparu.

---

## 1. Objectifs

| Objectif | Conséquence sur la conception |
|---|---|
| Round-trip exact | Les positions des nœuds sont émises en métadonnées `@pos` |
| Sortie déterministe | Ordre stable des déclarations, indentation fixe, pas de hash aléatoire |
| Diff-able en Git | Une instruction par ligne, `@id` stable pour que les diffs restent minimaux |
| Tolérant | Un nœud inconnu se parse en nœud fantôme, pas en erreur fatale |

Un objectif a été **abandonné en chemin** : « lisible sans documentation ». Le langage
écrit des appels de nœuds pleinement qualifiés et imbriqués, pas des expressions. Il se lit
comme une sérialisation, ce qu'il est.

---

## 2. Exemple complet

Extrait réduit de
[`examples/home.bp`](https://github.com/Kerlann/blueprint/blob/main/docs/examples/home.bp) —
les lignes sont celles du
fichier, dont un test vérifie la lecture à chaque construction ; seul le refroidissement a
été retiré pour tenir sur un écran.

```bscript
blueprint blueprint:home {
  meta {
    author "Kerlann"
    description "Point de retour : /sethome enregistre, /home ramène"
    version "1.0.0"
    permission WORLD
  }

  var double tick = 0.0 @graph
  var vec3 point @player
  var bool defini = false @player

  on blueprint:event/server_tick() {
    blueprint:var/set(var: "tick", value: blueprint:math/add(a: blueprint:var/get(var: "tick"), b: 1))
  }

  on blueprint:event/command(player, name, arg) @with(name: "sethome") {
    blueprint:entity/position(entity: $player) @id("11111111-0000-4000-8000-000000000001")
    blueprint:var/set(var: "point", value: $node("11111111-0000-4000-8000-000000000001").pos)
    blueprint:var/set(var: "defini", value: true)
  }

  on blueprint:event/command(player, name, arg) @with(name: "home") {
    blueprint:flow/branch(condition: blueprint:var/get(var: "defini")) {
      true: {
        blueprint:flow/wait(ticks: 60)
        blueprint:entity/teleport(entity: $player, pos: blueprint:var/get(var: "point"))
      }
      false: {
        blueprint:player/send_message(player: $player, text: "Aucun point de retour — utilisez /sethome")
      }
    }
  }
}
```

Trois choses à remarquer, parce qu'elles sont les seules manières d'exprimer ce qu'on
voudrait écrire autrement :

- **une valeur se calcule en imbriquant des appels**, jamais avec un opérateur ;
- **seuls les nœuds purs s'imbriquent.** Un nœud **à exécution** — `entity/position`
  ci-dessus — se pose comme instruction avec un `@id`, et sa sortie se lit par
  `$node("…").<pin>`. Voir l'encadré ci-dessous : c'est le piège le plus coûteux du
  langage ;
- **les sorties d'exécution multiples s'écrivent en blocs nommés** (`true:`, `false:`,
  `body:`, `completed:`).

Et une quatrième qui ne se voit pas ici, faute d'occasion : quand un nœud a **plusieurs
sorties de valeur**, `@out("nom")` dit laquelle est reliée. Sans lui c'est la première —
un graphe faux, et silencieusement faux. `vec/split` en est le cas type ; cet exemple s'en
passe depuis que la position tient dans une seule variable `vec3`.

> **Pur ou à exécution : la distinction qui coûte le plus cher.**
>
> Un nœud **pur** (`math/add`, `vec/split`, `var/get`) se calcule à la demande : l'imbriquer
> dans un argument est la manière normale de l'écrire. Un nœud **à exécution**
> (`entity/position`, `player/send_message`, tout ce qui a un pin d'exécution) n'existe que
> dans la chaîne — il ne se calcule pas, il *arrive*.
>
> L'imbriquer dans un argument est donc refusé, avec un message qui nomme l'écriture
> correcte. Ce refus est récent : avant, ça se parsait, se validait sans un diagnostic, et
> ne marchait pas. Le compilateur n'émet un producteur de valeur que s'il est pur ; le nœud
> n'était jamais exécuté, et le consommateur retombait sur le **défaut du pin** — pas une
> erreur, pas un zéro visible, une valeur plausible. `home.bp` enregistrait ainsi l'origine
> du monde en croyant lire la position du joueur.
>
> ```
> blueprint:entity/position(entity: $player) @id("…")
> blueprint:var/set(var: "point", value: $node("…").pos)
> ```

---

## 3. Grammaire

```ebnf
File        ::= Blueprint
Blueprint   ::= "blueprint" ResourceId "{" MetaBlock? Decl* "}"
MetaBlock   ::= "meta" "{" MetaEntry* "}"
MetaEntry   ::= ("author" | "description" | "version" | "permission") (String | Ident)

Decl        ::= VarDecl | ScreenDecl | FuncDecl | NoteDecl | EventDecl
VarDecl     ::= "var" Type Ident ("=" Literal)? Annotation*
ScreenDecl  ::= "screen" String Annotation* "{" Element* "}"
Element     ::= Ident String Annotation*
FuncDecl    ::= "func" String "(" Params? ")" "returns" "(" Params? ")" Annotation* Block
Params      ::= Ident ":" Type ("," Ident ":" Type)*
NoteDecl    ::= "note" String Annotation*
EventDecl   ::= "on" NodePath "(" Ident* ")" Annotation* Block

Block       ::= "{" Stmt* "}"
Stmt        ::= NodePath "(" ArgList? ")" Annotation* ExecBlocks?
ExecBlocks  ::= "{" (Ident ":" Block)* "}"      ; un bloc par sortie d'exécution

ArgList     ::= Arg ("," Arg)*
Arg         ::= Ident ":" Expr                  ; le nom est OBLIGATOIRE
Expr        ::= Literal | PinRef | Call
PinRef      ::= "$" Ident                       ; une sortie de l'événement englobant
Call        ::= NodePath "(" ArgList? ")" Annotation*
NodePath    ::= Namespace ":" Ident "/" Ident   ; « blueprint:math/add »
Literal     ::= Number | String | "true" | "false"
Annotation  ::= "@" Ident ("(" AnnArgs? ")")?
Type        ::= Ident                           ; int, double, bool, string, vec3…
```

Un nom d'événement s'écrit en entier (`blueprint:event/player_join`) ou en **forme
courte** (`player_join`) — les deux sont acceptés, la seconde est plus lisible.

### Lexique

- **Il n'y a pas de commentaires.** Ni `//`, ni `--`, ni `#` : les trois sont des erreurs
  de lexique. Ce que vous auriez mis en commentaire va dans `meta.description`, dans un
  `note "…"`, ou dans le nom des variables.
- Pas de point-virgule : une instruction par ligne.
- Les nombres acceptent le signe et la décimale (`-1.5`).

---

## 4. Correspondance langage ↔ graphe

| Construction BScript | Graphe |
|---|---|
| `blueprint id { … }` | Le blueprint et ses métadonnées |
| `var T x = v @graph` | Une `Variable` de portée `GRAPH` |
| `on event(a, b) { … }` | Nœud d'événement ; `a` et `b` sont ses pins de sortie |
| `$a` | Un lien depuis le pin `a` de l'événement englobant |
| `ns:cat/action(x: 1)` | Nœud `ns:cat/action`, `1` posé sur son pin `x` |
| Argument littéral | Valeur littérale sur le pin, **sans** nœud constant |
| Appel imbriqué | Un nœud de plus, relié au pin de l'appelant |
| `@out("y")` | Le lien part du pin `y` plutôt que du premier |
| `nœud(…) { true: { … } }` | Le flux d'exécution partant du pin `true` |
| Deux instructions à la suite | Un lien d'exécution de la première vers la seconde |
| `note "texte" @pos(…)` | Un `CommentBox` |
| `screen "s" { … }` | Un écran et ses éléments |
| `func "f"(a: double) returns (r: double) { … }` | Une fonction utilisateur (story 20.1). `returns` et non `->` : le lexeur ne connaît le tiret qu'à l'intérieur d'un mot |

---

## 5. Annotations

| Annotation | Portée | Rôle |
|---|---|---|
| `@pos(x, y)` | Nœud, événement, note | Position dans le canevas |
| `@id("uuid")` | Nœud, événement, fonction | UUID stable, pour que les diffs Git restent minimaux |
| `@out("pin")` | Appel imbriqué | **Quelle sortie** est reliée, quand le nœud en a plusieurs |
| `@with(pin: valeur)` | Événement | Ne déclenche que si le pin vaut cela (`@with(name: "home")`) |
| `@local` `@graph` `@world` `@player` `@player_shared` | Variable | Portée |
| `@replicated` | Variable | Envoyée aux clients en **lecture seule** — voir ci-dessous |
| `@collapsed` | Nœud | Replié dans l'éditeur |
| `@hud` | Écran | Bandeau permanent au lieu d'un menu modal |
| `@at` `@size` `@in` `@text` `@bind` `@opts` `@style` `@tip` `@layout` | Élément d'écran | Voir `ux-ui-spec.md` |

Les annotations de mise en page sont **ignorées à l'exécution** : un BScript écrit à la
main sans `@pos` ni `@id` reste valide, l'auto-layout s'en charge.

### `@replicated` (épic 21)

La valeur est envoyée aux clients qui la regardent, et un écran ou un HUD lié à cette variable
s'affiche **sans aller-retour** : la mise à jour ne dépend plus d'un `gui/refresh`, et une barre
liée **glisse** au lieu de sauter.

Deux formes sont **refusées**, à l'édition comme au parsing :

| Refusé | Pourquoi |
|---|---|
| `@local @replicated` | une variable locale ne survit pas à l'exécution qui l'écrit ; il n'y a rien à répliquer |
| un type qui ne voyage pas | `player`, `entity`, un joker, `itemstack`, `text`, `blockstate`, `resourcelocation`, ou une collection qui en contient |

La seconde liste est exactement celle des types qui **ne se persistent pas** non plus, et ce n'est
pas une coïncidence : le fil et la sauvegarde du monde portent le **même** format étiqueté. Une
variable ne peut donc pas arriver chez un client sans pouvoir être sauvegardée.

Le sens est **descendant seulement**. Aucun paquet ne permet à un client d'écrire une valeur, et il
n'en existera pas : « le serveur ne fait jamais confiance à ce qu'un client déclare » (FR52).

Bornes : **32 variables répliquées par blueprint**, contre 256 variables au total. L'écart est
voulu — une variable ordinaire coûte de la mémoire serveur une fois, une répliquée coûte un envoi
par joueur qui la regarde à chaque changement. Un graphe qui dépasse est refusé à l'enregistrement.

---

## 6. Robustesse et erreurs

- Erreur de syntaxe → ligne, message traduit, aucune modification partielle du graphe.
- Nœud inconnu au parsing → **nœud fantôme** conservant nom, arguments et position.
- Type incompatible → diagnostic rattaché à l'appel, pas une exception.
- Le parseur est soumis à un fuzzing continu (story 4.3) : aucune entrée ne doit provoquer
  de crash, de boucle infinie ou d'explosion mémoire.
- Profondeur d'imbrication bornée (protection contre les entrées adverses).

---

## 7. Fichiers

| Extension | Contenu |
|---|---|
| `.bp` | Un blueprint complet, exporté ou importé |
| `.bps` | Un fragment (presse-papier, sélection de nœuds) |

---

## 8. Écrire un `.bp` à la main

C'est possible et vérifié — `examples/parkour.bp` et `examples/home.bp` le sont. Trois
conseils, dans l'ordre où ils vous serviront :

1. **Partez d'un export.** Construisez le graphe dans l'éditeur, exportez-le, modifiez le
   texte. La forme exacte d'un nœud se lit alors dans le fichier.
2. **Vérifiez avant de livrer.** Tout `.bp` déposé dans `docs/examples/` est relu et validé
   par `ExemplesLivresTest` à chaque construction.
3. **Cherchez les pins dans [`node-reference.md`](node-reference.md)**, qui est généré
   depuis le registre : les noms d'arguments y sont exacts par construction.

---

## 9. Ce que le langage n'a pas

Écrit pour que personne ne les cherche. Toutes ces formes étaient décrites par les
versions précédentes de ce document ; **aucune n'a jamais été implémentée.**

| Forme | Ce qu'il faut écrire à la place |
|---|---|
| `x = x + 1` | `blueprint:var/set(var: "x", value: blueprint:math/add(a: blueprint:var/get(var: "x"), b: 1))` |
| `a + b`, `a > b`, `a && b` | `blueprint:math/add(a:, b:)`, `blueprint:logic/greater(a:, b:)`, `blueprint:logic/and(a:, b:)` |
| `if (c) { … } else { … }` | `blueprint:flow/branch(condition: c) { true: { … } false: { … } }` |
| `while`, `for (i in 0..n)`, `foreach` | `blueprint:flow/while`, `flow/for`, `flow/for_each`, avec un bloc `body:` |
| `wait 20t` | `blueprint:flow/wait(ticks: 20)` |
| `player.send_message(…)` | `blueprint:player/send_message(player:, text:)` |
| `f($player, "x")` — argument positionnel | `f(player: $player, text: "x")` — le nom est obligatoire |
| `var int local = …` dans un bloc | Une variable de blueprint, ou un appel imbriqué |
| `[1, 2]`, `{ a: 1 }` | `blueprint:list/of(…)`, `blueprint:map/put(…)` |
| `c ? a : b` | `blueprint:flow/select` |
| `label:` / `goto` | N'existe pas ; le flux se décrit par imbrication |
| `//`, `--`, `#` | Rien : il n'y a pas de commentaires |
| `@disabled` | N'existe pas |
| `meta { desc … }` | `meta { description … }` |

Rien n'interdit d'implémenter ces formes un jour — le graphe les supporterait toutes. Mais
tant que ce n'est pas fait, ce document ne les annonce plus.
