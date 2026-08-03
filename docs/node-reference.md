# Référence des nœuds

> **Fichier généré** — ne pas modifier à la main. Il est produit depuis le
> registre par `NodeReferenceTest` ; la construction échoue s'il diverge.
> Régénérer : `./gradlew :core:test --tests "*NodeReferenceTest" -Dblueprint.regenDocs=true`

121 nœuds dans 25 catégories.

Légende : **P** = nœud pur (sans pin d'exécution) · **E** = point d'entrée (événement) · *fuel* = coût d'un passage.

## debug

### `blueprint:debug/log` — Log

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `value` | `any` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## entity/act

### `blueprint:entity/add_effect` — Add effect

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `effect` | `blueprint:resourcelocation` | — |
| `duration` | `blueprint:int` | `200` |
| `amplifier` | `blueprint:int` | `0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:entity/heal` — Heal

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `amount` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:entity/set_health` — Set health

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `health` | `blueprint:double` | `20.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:entity/teleport` — Teleport

permission `WORLD` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `pos` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## entity/query

### `blueprint:query/entities_near` — Nearby entities

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:vec3` | — |
| `radius` | `blueprint:double` | `8.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `entities` | `list<blueprint:entity>` | — |

### `blueprint:query/nearest_player` — Nearest player

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:vec3` | — |
| `radius` | `blueprint:double` | `16.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `found` | `blueprint:bool` | — |

### `blueprint:query/players` — Online players

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `players` | `list<blueprint:player>` | — |


## entity/read

### `blueprint:entity/as_player` — Entity as player

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `is_player` | `blueprint:bool` | — |

### `blueprint:entity/health` — Get health

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `health` | `blueprint:double` | — |

### `blueprint:entity/is_alive` — Is entity alive

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `alive` | `blueprint:bool` | — |

### `blueprint:entity/max_health` — Max health

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `max` | `blueprint:double` | — |

### `blueprint:entity/name` — Entity name

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `name` | `blueprint:string` | — |

### `blueprint:entity/position` — Entity position

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `pos` | `blueprint:vec3` | — |

### `blueprint:entity/type` — Entity type

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `type` | `blueprint:resourcelocation` | — |


## event/player

### `blueprint:event/player_break_block` — Player breaks block

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `pos` | `blueprint:blockpos` | — |

### `blueprint:event/player_chat` — Player chats

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `message` | `blueprint:string` | — |

### `blueprint:event/player_join` — Player joins

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |

### `blueprint:event/player_quit` — Player leaves

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |

### `blueprint:event/player_use_block` — Player uses block

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `pos` | `blueprint:blockpos` | — |
| `face` | `blueprint:direction` | — |

### `blueprint:event/player_use_item` — Player uses item

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |


## event/server

### `blueprint:event/command` — On command (/bpc)

permission `SAFE` · fuel 1 · E

| Entrées | Type | Défaut |
|---|---|---|
| `name` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `name` | `blueprint:string` | — |
| `arg` | `blueprint:string` | — |

### `blueprint:event/server_tick` — Server tick

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:event/signal` — Signal

permission `SAFE` · fuel 1 · E

| Entrées | Type | Défaut |
|---|---|---|
| `name` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `payload` | `blueprint:string` | — |

### `blueprint:signal/emit` — Emit signal

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `name` | `blueprint:string` | `` |
| `payload` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## event/world

### `blueprint:event/entity_death` — Entity dies

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `entity` | `blueprint:entity` | — |


## flow/branch

### `blueprint:flow/branch` — Branch

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `condition` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `true` | exec | — |
| `false` | exec | — |

### `blueprint:flow/do_once` — Do once

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:flow/gate` — Gate

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `enter` | exec | — |
| `open` | exec | — |
| `close` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exit` | exec | — |

### `blueprint:flow/return` — Return

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

### `blueprint:flow/select` — Select

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `condition` | `blueprint:bool` | `false` |
| `if_true` | `T` | — |
| `if_false` | `T` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `value` | `T` | — |

### `blueprint:flow/sequence` — Sequence

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `then_1` | exec | — |
| `then_2` | exec | — |
| `then_3` | exec | — |
| `then_4` | exec | — |

### `blueprint:flow/switch` — Switch

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `value` | `blueprint:int` | `0` |

| Sorties | Type | Défaut |
|---|---|---|
| `case_0` | exec | — |
| `case_1` | exec | — |
| `case_2` | exec | — |
| `case_3` | exec | — |
| `default` | exec | — |


## flow/loop

### `blueprint:flow/for` — For loop

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `first` | `blueprint:int` | `1` |
| `last` | `blueprint:int` | `10` |

| Sorties | Type | Défaut |
|---|---|---|
| `body` | exec | — |
| `completed` | exec | — |
| `index` | `blueprint:double` | — |

### `blueprint:flow/for_each` — For each

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `list` | `list<T>` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `body` | exec | — |
| `completed` | exec | — |
| `element` | `T` | — |
| `index` | `blueprint:int` | — |

### `blueprint:flow/wait` — Wait

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `ticks` | `blueprint:int` | `20` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:flow/wait_until` — Wait until

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `condition` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:flow/while` — While

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `condition` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `body` | exec | — |
| `completed` | exec | — |


## item

### `blueprint:item/count` — Item count

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `stack` | `blueprint:itemstack` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `count` | `blueprint:int` | — |

### `blueprint:item/create` — Create item

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `item` | `blueprint:resourcelocation` | — |
| `count` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `stack` | `blueprint:itemstack` | — |

### `blueprint:item/matches` — Item matches

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `stack` | `blueprint:itemstack` | — |
| `item` | `blueprint:resourcelocation` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `matches` | `blueprint:bool` | — |

### `blueprint:item/with_count` — With count

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `stack` | `blueprint:itemstack` | — |
| `count` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `stack` | `blueprint:itemstack` | — |


## list/build

### `blueprint:list/add` — Add to list

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |
| `value` | `T` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `list<T>` | — |

### `blueprint:list/empty` — Empty list

permission `SAFE` · fuel 1 · P

| Sorties | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |

### `blueprint:list/of` — Make list

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `T` | — |
| `b` | `T` | — |
| `c` | `T` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |

### `blueprint:list/remove` — Remove from list

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |
| `value` | `T` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `list<T>` | — |


## list/query

### `blueprint:list/contains` — List contains

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |
| `value` | `T` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `found` | `blueprint:bool` | — |

### `blueprint:list/get` — Get element

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |
| `index` | `blueprint:int` | `0` |

| Sorties | Type | Défaut |
|---|---|---|
| `element` | `T` | — |

### `blueprint:list/index_of` — Index of

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |
| `value` | `T` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `index` | `blueprint:int` | — |

### `blueprint:list/is_empty` — Is list empty

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `empty` | `blueprint:bool` | — |

### `blueprint:list/size` — List size

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `list` | `list<T>` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `size` | `blueprint:int` | — |


## logic/boolean

### `blueprint:logic/and` — And

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:bool` | `false` |
| `b` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/not` — Not

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/or` — Or

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:bool` | `false` |
| `b` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/xor` — Exclusive or

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:bool` | `false` |
| `b` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |


## logic/compare

### `blueprint:logic/equals` — Equals

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `any` | — |
| `b` | `any` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/greater` — Greater than

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/greater_eq` — Greater or equal

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/less` — Less than

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/less_eq` — Less or equal

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:logic/not_equals` — Not equals

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `any` | — |
| `b` | `any` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |


## math/arithmetic

### `blueprint:math/add` — Add

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/div` — Divide

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/mod` — Modulo

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/mul` — Multiply

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/sub` — Subtract

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |


## math/function

### `blueprint:convert/to_int` — To integer

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:int` | — |

### `blueprint:math/abs` — Absolute value

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/max` — Maximum

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/min` — Minimum

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:double` | `0.0` |
| `b` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/random` — Random (seeded)

permission `SAFE` · fuel 1 · P, non déterministe

| Entrées | Type | Défaut |
|---|---|---|
| `seed` | `blueprint:long` | `0` |
| `index` | `blueprint:int` | `0` |

| Sorties | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | — |

### `blueprint:math/round` — Round

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:int` | — |


## math/position

### `blueprint:pos/distance` — Distance between positions

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:blockpos` | — |
| `b` | `blueprint:blockpos` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `distance` | `blueprint:double` | — |

### `blueprint:pos/make` — Make position

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `x` | `blueprint:int` | `0` |
| `y` | `blueprint:int` | `0` |
| `z` | `blueprint:int` | `0` |

| Sorties | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |

### `blueprint:pos/offset` — Offset position

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |
| `dx` | `blueprint:int` | `0` |
| `dy` | `blueprint:int` | `0` |
| `dz` | `blueprint:int` | `0` |

| Sorties | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |

### `blueprint:pos/relative` — Position in direction

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |
| `direction` | `blueprint:direction` | `up` |
| `distance` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |

### `blueprint:pos/split` — Break position

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `x` | `blueprint:int` | — |
| `y` | `blueprint:int` | — |
| `z` | `blueprint:int` | — |

### `blueprint:pos/to_vec` — Position to vector

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |
| `centered` | `blueprint:bool` | `true` |

| Sorties | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

### `blueprint:vec/to_pos` — Vector to position

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `pos` | `blueprint:blockpos` | — |


## math/vector

### `blueprint:vec/add` — Add vectors

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:vec3` | — |
| `b` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

### `blueprint:vec/distance` — Distance

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:vec3` | — |
| `b` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `distance` | `blueprint:double` | — |

### `blueprint:vec/dot` — Dot product

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:vec3` | — |
| `b` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `dot` | `blueprint:double` | — |

### `blueprint:vec/length` — Vector length

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `length` | `blueprint:double` | — |

### `blueprint:vec/make` — Make vector

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `x` | `blueprint:double` | `0.0` |
| `y` | `blueprint:double` | `0.0` |
| `z` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

### `blueprint:vec/normalize` — Normalize

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

### `blueprint:vec/offset` — Offset vector

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |
| `dx` | `blueprint:double` | `0.0` |
| `dy` | `blueprint:double` | `0.0` |
| `dz` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

### `blueprint:vec/scale` — Scale vector

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |
| `factor` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

### `blueprint:vec/split` — Break vector

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `x` | `blueprint:double` | — |
| `y` | `blueprint:double` | — |
| `z` | `blueprint:double` | — |

### `blueprint:vec/sub` — Subtract vectors

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:vec3` | — |
| `b` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `vec` | `blueprint:vec3` | — |


## misc

### `blueprint:var/get` — Get variable

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `var` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `value` | `any` | — |

### `blueprint:var/set` — Set variable

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `var` | `blueprint:string` | `` |
| `value` | `any` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## player

### `blueprint:player/give_item` — Give item

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `item` | `blueprint:itemstack` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/give_xp` — Give XP

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `amount` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/send_message` — Send message

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/set_gamemode` — Set game mode

permission `ADMIN` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `mode` | `blueprint:string` | `survival` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/title` — Show title

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## string

### `blueprint:convert/to_string` — To text

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `any` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:string` | — |

### `blueprint:string/concat` — Concatenate

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `a` | `blueprint:string` | `` |
| `b` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:string` | — |

### `blueprint:string/contains` — Contains

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |
| `search` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:string/length` — Length

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:int` | — |

### `blueprint:string/lower` — Lowercase

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:string` | — |

### `blueprint:string/upper` — Uppercase

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:string` | — |


## text

### `blueprint:text/colored` — Colored text

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |
| `color` | `blueprint:string` | `white` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |

### `blueprint:text/concat` — Join text

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `left` | `blueprint:text` | — |
| `right` | `blueprint:text` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |

### `blueprint:text/literal` — Text

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |


## world/block

### `blueprint:world/get_block` — Get block

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:blockpos` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `state` | `blueprint:blockstate` | — |

### `blueprint:world/is_block` — Is block

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:blockpos` | — |
| `block` | `blueprint:resourcelocation` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `matches` | `blueprint:bool` | — |

### `blueprint:world/light` — Light level

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:blockpos` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `light` | `blueprint:int` | — |

### `blueprint:world/set_block` — Set block

permission `WORLD` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:blockpos` | — |
| `state` | `blueprint:blockstate` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:world/surface` — Surface height

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:blockpos` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `pos` | `blueprint:blockpos` | — |


## world/effect

### `blueprint:world/explosion` — Explosion

permission `ADMIN` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:vec3` | — |
| `power` | `blueprint:double` | `2.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:world/particles` — Particles

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `particle` | `blueprint:resourcelocation` | — |
| `pos` | `blueprint:vec3` | — |
| `count` | `blueprint:int` | `10` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:world/play_sound` — Play sound

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `sound` | `blueprint:resourcelocation` | — |
| `pos` | `blueprint:vec3` | — |
| `volume` | `blueprint:double` | `1.0` |
| `pitch` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## world/state

### `blueprint:world/dimension` — Dimension

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `dimension` | `blueprint:resourcelocation` | — |

### `blueprint:world/drop_item` — Drop item

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `pos` | `blueprint:vec3` | — |
| `item` | `blueprint:itemstack` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:world/get_time` — World time

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `day_time` | `blueprint:long` | — |
| `game_time` | `blueprint:long` | — |
| `day` | `blueprint:long` | — |

### `blueprint:world/get_weather` — Weather

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `raining` | `blueprint:bool` | — |
| `thundering` | `blueprint:bool` | — |

### `blueprint:world/is_day` — Is daytime

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `is_day` | `blueprint:bool` | — |

### `blueprint:world/set_time` — Set time

permission `WORLD` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `time` | `blueprint:long` | `1000` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:world/set_weather` — Set weather

permission `WORLD` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `rain` | `blueprint:bool` | `false` |
| `thunder` | `blueprint:bool` | `false` |
| `duration` | `blueprint:int` | `6000` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:world/spawn_entity` — Spawn entity

permission `WORLD` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `type` | `blueprint:resourcelocation` | — |
| `pos` | `blueprint:vec3` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `entity` | `blueprint:entity` | — |

