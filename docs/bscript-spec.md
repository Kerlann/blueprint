# BScript — spécification du langage

**Statut :** spécification de référence (épic 4)
**Principe :** BScript et le graphe sont **deux vues de la même donnée**. Toute
construction du langage a une contrepartie exacte dans le graphe, et réciproquement.

---

## 1. Objectifs

| Objectif | Conséquence sur la conception |
|---|---|
| Round-trip exact | Les positions des nœuds sont émises en métadonnées `@pos` |
| Sortie déterministe | Ordre stable des déclarations, indentation fixe, pas de hash aléatoire |
| Lisible sans documentation | Syntaxe familière (C/Java), noms de nœuds en chemins pointés |
| Diff-able en Git | Une instruction par ligne, pas de reformatage spontané |
| Tolérant | Un nœud inconnu se parse en nœud fantôme, pas en erreur fatale |

---

## 2. Exemple complet

```bscript
blueprint mypack:porte_secrete {
  meta {
    author  "Lea"
    version "1.2.0"
    desc    "Ouvre la porte si le joueur tient la clef"
    permission GAMEPLAY
  }

  var int  ouvertures = 0        @graph
  var bool verrouillee = true    @graph
  var int  essais = 0            @player

  on player_use_block(player, pos, face) @pos(-320, -140) {
    if (verrouillee && !player.holds($player, item("mypack:clef"))) {
      essais = essais + 1
      player.send_message($player, text.styled("La porte est verrouillée", color: "red"))
      if (essais >= 3) {
        world.play_sound($pos, sound("minecraft:entity.wither.spawn"), 1.0, 0.5)
      }
      return
    }

    ouvertures = ouvertures + 1
    world.set_block($pos, block("minecraft:air"))
    world.play_sound($pos, sound("minecraft:block.iron_door.open"), 1.0, 1.0)

    wait 60t

    world.set_block($pos, block("minecraft:oak_door"))
    signal.emit("mypack:porte_refermee", { position: $pos })
  }

  on signal("mypack:porte_refermee") (position) @pos(-320, 240) {
    debug.log(text.format("Porte refermée en %s, total %d", $position, ouvertures))
  }
}
```

---

## 3. Grammaire (EBNF)

```ebnf
File        ::= Blueprint
Blueprint   ::= "blueprint" ResourceId "{" MetaBlock? Decl* "}"
MetaBlock   ::= "meta" "{" MetaEntry* "}"
MetaEntry   ::= Ident (String | Number | Ident)

Decl        ::= VarDecl | FuncDecl | EventDecl | CommentDecl
VarDecl     ::= "var" Type Ident ("=" Expr)? ScopeAnn? Annotation*
ScopeAnn    ::= "@local" | "@graph" | "@world" | "@player" | "@player_shared"
FuncDecl    ::= "func" Ident "(" ParamList? ")" ("->" Type)? Annotation* Block
EventDecl   ::= "on" EventRef "(" ParamList? ")" Annotation* Block
EventRef    ::= Ident | "signal" "(" String ")"
CommentDecl ::= "note" String Annotation*

Block       ::= "{" Stmt* "}"
Stmt        ::= CallStmt | AssignStmt | IfStmt | WhileStmt | ForStmt | ForEachStmt
              | WaitStmt | ReturnStmt | BreakStmt | ContinueStmt | LabelStmt
              | GotoStmt | LocalDecl | Block

LocalDecl   ::= "var" Type Ident ("=" Expr)?
CallStmt    ::= NodePath "(" ArgList? ")" Annotation*
AssignStmt  ::= LValue ("=" | "+=" | "-=" | "*=" | "/=") Expr
LValue      ::= Ident | "$" Ident
IfStmt      ::= "if" "(" Expr ")" Block ("else" (IfStmt | Block))?
WhileStmt   ::= "while" "(" Expr ")" Block
ForStmt     ::= "for" "(" Ident "in" Expr ".." Expr ")" Block
ForEachStmt ::= "foreach" "(" Ident "in" Expr ")" Block
WaitStmt    ::= "wait" (Duration | "until" "(" Expr ")")
ReturnStmt  ::= "return" Expr?
LabelStmt   ::= Ident ":"                     ; repli non structuré
GotoStmt    ::= "goto" Ident

Expr        ::= Ternary
Ternary     ::= Or ("?" Expr ":" Expr)?
Or          ::= And ("||" And)*
And         ::= Equality ("&&" Equality)*
Equality    ::= Compare (("==" | "!=") Compare)*
Compare     ::= Additive (("<" | "<=" | ">" | ">=") Additive)*
Additive    ::= Multiplicative (("+" | "-") Multiplicative)*
Multiplicative ::= Unary (("*" | "/" | "%") Unary)*
Unary       ::= ("!" | "-")? Postfix
Postfix     ::= Primary ("[" Expr "]" | "." Ident)*
Primary     ::= Literal | PinRef | Ident | Call | "(" Expr ")" | ListLit | MapLit
PinRef      ::= "$" Ident                     ; sortie d'un pin de l'événement / paramètre
Call        ::= NodePath "(" ArgList? ")"
NodePath    ::= (Namespace ":")? Ident ("." Ident)*
ArgList     ::= Arg ("," Arg)*
Arg         ::= (Ident ":")? Expr             ; arguments nommés autorisés

Literal     ::= Number | String | "true" | "false" | "null"
              | TypedLit
TypedLit    ::= Ident "(" ArgList? ")"        ; item(...), block(...), vec3(...), text(...)
ListLit     ::= "[" (Expr ("," Expr)*)? "]"
MapLit      ::= "{" (Ident ":" Expr ("," Ident ":" Expr)*)? "}"
Duration    ::= Number ("t" | "s" | "m")      ; ticks, secondes, minutes
Annotation  ::= "@" Ident ("(" ArgList? ")")?
Type        ::= Ident ("<" Type ("," Type)* ">")?
```

### Lexique

- **Commentaires** : `//` et `--` sont tous deux acceptés, jusqu'à la fin de ligne.
  Le générateur émet toujours `//`.
- **Synonymes** : `and`, `or`, `not` sont acceptés comme synonymes de `&&`, `||`, `!`
  (emprunt à Lua pour les non-programmeurs, décision AD12). Le générateur émet toujours
  la forme symbolique, ce qui préserve le déterminisme de la sortie.
- Pas de point-virgule : une instruction par ligne.

---

## 4. Correspondance langage ↔ graphe

| Construction BScript | Graphe |
|---|---|
| `blueprint id { … }` | Le blueprint et ses métadonnées |
| `var T x = v @graph` | Une `Variable` de portée `GRAPH` |
| `x` en lecture | Nœud `Get x` (pur) |
| `x = expr` | Nœud `Set x` (à exec) |
| `on event(a, b) { … }` | Nœud d'événement, `a` et `b` = ses pins de sortie |
| `$a` | Un lien depuis le pin `a` du nœud d'événement (ou d'un paramètre) |
| `ns:cat.action(x, y)` | Nœud `ns:cat/action`, `x` et `y` câblés sur ses entrées |
| `f(a: 1, b: 2)` | Arguments nommés → pins nommés (ordre libre) |
| Argument littéral | Valeur littérale sur le pin, **sans** nœud constant |
| Expression imbriquée | Chaîne de nœuds purs |
| `a + b` | Nœud `blueprint:math/add` |
| `if (c) A else B` | Nœud `blueprint:flow/branch` |
| `while (c) { … }` | Nœud `blueprint:flow/while` |
| `for (i in 0..n)` | Nœud `blueprint:flow/for` |
| `foreach (e in list)` | Nœud `blueprint:flow/for_each` |
| `wait 20t` | Nœud `blueprint:flow/wait` → `YIELD` |
| `wait until (c)` | Nœud `blueprint:flow/wait_until` |
| `return` | Nœud `blueprint:flow/return` |
| `label:` / `goto label` | Flux exec non structuré (repli du générateur) |
| `note "texte" @pos(...)` | Un `CommentBox` |
| `func "f"(a: type) returns (r: type) { … }` | Une fonction utilisateur (story 20.1). `returns` et non `->` : le lexeur ne connaît le tiret qu'à l'intérieur d'un mot |

### Opérateurs infixes

Chaque opérateur est un alias d'un nœud pur standard. La table est fixe et documentée :

| Opérateur | Nœud |
|---|---|
| `+ - * / %` | `blueprint:math/{add,sub,mul,div,mod}` |
| `== !=` | `blueprint:logic/{equals,not_equals}` |
| `< <= > >=` | `blueprint:logic/{less,less_eq,greater,greater_eq}` |
| `&& \|\| !` | `blueprint:logic/{and,or,not}` |
| `? :` | `blueprint:flow/select` (pur) |
| `[ ]` | `blueprint:list/get` |
| `.` sur une valeur | `blueprint:struct/field` |

`+` sur deux `string` se mappe sur `blueprint:string/concat`. Aucune autre surcharge
implicite : le typage est résolu à la compilation, pas à l'exécution.

---

## 5. Annotations

| Annotation | Portée | Rôle |
|---|---|---|
| `@pos(x, y)` | Nœud, événement, note | Position dans le canevas (préserve la mise en page) |
| `@size(w, h)` | Note, boîte | Taille d'une boîte de regroupement |
| `@color("#RRGGBB")` | Note, boîte | Couleur |
| `@id("uuid")` | Nœud | UUID stable, pour que les diffs Git restent minimaux |
| `@collapsed` | Nœud | Nœud replié dans l'éditeur |
| `@local` `@graph` `@world` `@player` `@player_shared` | Variable | Portée — `@player` est isolée par blueprint, `@player_shared` est commune aux blueprints du serveur |
| `@replicated` | Variable | Synchronisée vers les clients (lecture seule) |
| `@disabled` | Événement | Point d'entrée désactivé |

Les annotations de mise en page sont **ignorées** à l'exécution : un BScript écrit à la
main sans aucune annotation reste parfaitement valide, l'auto-layout s'en charge.

---

## 6. Structuration et repli

Le générateur tente de reconnaître, dans le flux exec, les motifs structurés :
`if/else`, `while`, `for`, `foreach`, `sequence`. Un flux qui ne correspond à aucun motif
(sauts croisés créés à la main dans l'éditeur) est émis avec des étiquettes et des `goto` :

```bscript
  loop_start:
    if (!(i < 10)) goto loop_end
    debug.log(text.of(i))
    i = i + 1
    goto loop_start
  loop_end:
```

C'est laid, et c'est voulu : le langage reste **total** (tout graphe s'écrit), et la
laideur signale au joueur que son graphe est spaghetti.

---

## 7. Robustesse et erreurs

- Erreur de syntaxe → ligne, colonne, message traduit, aucune modification partielle du graphe.
- Nœud inconnu au parsing → **nœud fantôme** conservant nom, arguments et position.
- Type incompatible → diagnostic rattaché à l'appel, pas une exception.
- Le parseur est soumis à un fuzzing continu (story 4.3) : aucune entrée ne doit provoquer
  de crash, de boucle infinie ou d'explosion mémoire.
- Profondeur d'imbrication bornée (protection contre les entrées adverses).

---

## 8. Fichiers

| Extension | Contenu |
|---|---|
| `.bp` | Un blueprint complet, exporté ou importé |
| `.bps` | Un fragment (presse-papier, sélection de nœuds) |

Encodage **UTF-8**, fins de ligne `\n`, indentation de 2 espaces, sortie du générateur
strictement déterministe (même graphe → mêmes octets, condition testée en CI).
