# Référence des nœuds

> **Fichier généré** — ne pas modifier à la main. Il est produit depuis le
> registre par `NodeReferenceTest` ; la construction échoue s'il diverge.
> Régénérer : `./gradlew :core:test --tests "*NodeReferenceTest" -Dblueprint.regenDocs=true`

226 nœuds dans 39 catégories.

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

### `blueprint:world/raycast_entity` — Raycast entity

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `from` | `blueprint:vec3` | — |
| `direction` | `blueprint:vec3` | — |
| `distance` | `blueprint:double` | `16.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `hit` | `blueprint:bool` | — |
| `entity` | `blueprint:entity` | — |


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

### `blueprint:entity/looking_at` — Entity is looking at

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `distance` | `blueprint:double` | `6.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `hit` | `blueprint:bool` | — |
| `pos` | `blueprint:blockpos` | — |
| `face` | `blueprint:direction` | — |

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

### `blueprint:event/player_attack_entity` — Player attacks entity

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `target` | `blueprint:entity` | — |

### `blueprint:event/player_break_block` — Player breaks block

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `pos` | `blueprint:blockpos` | — |

### `blueprint:event/player_change_world` — Player changes dimension

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `from` | `blueprint:resourcelocation` | — |
| `to` | `blueprint:resourcelocation` | — |

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

### `blueprint:event/player_respawn` — Player respawns

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `end_portal` | `blueprint:bool` | — |

### `blueprint:event/player_sleep` — Player goes to sleep

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `pos` | `blueprint:blockpos` | — |

### `blueprint:event/player_use_block` — Player uses block

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `pos` | `blueprint:blockpos` | — |
| `face` | `blueprint:direction` | — |

### `blueprint:event/player_use_entity` — Player uses entity

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `target` | `blueprint:entity` | — |

### `blueprint:event/player_use_item` — Player uses item

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |

### `blueprint:event/player_wake_up` — Player wakes up

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

### `blueprint:event/entity_damaged` — Entity takes damage

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `entity` | `blueprint:entity` | — |
| `amount` | `blueprint:double` | — |
| `attacker` | `blueprint:entity` | — |

### `blueprint:event/entity_death` — Entity dies

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `entity` | `blueprint:entity` | — |

### `blueprint:event/entity_killed` — Entity kills another

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `killer` | `blueprint:entity` | — |
| `victim` | `blueprint:entity` | — |


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


## gui

### `blueprint:event/gui_clicked` — On element clicked

permission `SAFE` · fuel 1 · E

| Entrées | Type | Défaut |
|---|---|---|
| `element` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | — |
| `element` | `blueprint:string` | — |

### `blueprint:event/gui_closed` — On screen closed

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | — |

### `blueprint:event/gui_input_changed` — Input changed

permission `SAFE` · fuel 1 · E

| Entrées | Type | Défaut |
|---|---|---|
| `element` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | — |
| `element` | `blueprint:string` | — |
| `text` | `blueprint:string` | — |
| `submitted` | `blueprint:bool` | — |

### `blueprint:event/gui_list_clicked` — List line clicked

permission `SAFE` · fuel 1 · E

| Entrées | Type | Défaut |
|---|---|---|
| `element` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | — |
| `element` | `blueprint:string` | — |
| `index` | `blueprint:int` | — |
| `line` | `blueprint:string` | — |

### `blueprint:event/gui_opened` — On screen opened

permission `SAFE` · fuel 1 · E

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | — |

### `blueprint:event/gui_value_changed` — Value changed

permission `SAFE` · fuel 1 · E

| Entrées | Type | Défaut |
|---|---|---|
| `element` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | — |
| `element` | `blueprint:string` | — |
| `value` | `blueprint:double` | — |
| `checked` | `blueprint:bool` | — |

### `blueprint:gui/close` — Close screen

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/open` — Open screen

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:hud/hide` — Hide HUD

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:hud/hide_all` — Hide all HUDs

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:hud/show` — Show HUD

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## gui/bind

### `blueprint:gui/refresh` — Refresh bindings

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `sent` | `blueprint:int` | — |

### `blueprint:gui/refresh_all` — Refresh bindings (everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `sent` | `blueprint:int` | — |


## gui/look

### `blueprint:gui/set_style` — Set named style

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `style` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_style_all` — Set named style (everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `style` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_tooltip` — Set tooltip

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_tooltip_all` — Set tooltip (everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_tooltip_key` — Set tooltip (key)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `key` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_tooltip_key_all` — Set tooltip (key, everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `key` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## gui/rich

### `blueprint:gui/set_input` — Set input text

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_input_all` — Set input text (everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_item` — Set slot item

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `item` | `blueprint:itemstack` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_item_all` — Set slot item (everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `item` | `blueprint:itemstack` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_lines` — Set list lines

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `lines` | `list<blueprint:string>` | `[]` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_lines_all` — Set list lines (everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `lines` | `list<blueprint:string>` | `[]` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_value` — Set value

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_value_all` — Set value (everyone)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## gui/update

### `blueprint:gui/set_enabled` — Set element enabled

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `enabled` | `blueprint:bool` | `true` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_enabled_all` — Set element enabled (all viewers)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `enabled` | `blueprint:bool` | `true` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_progress` — Set bar value

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_progress_all` — Set bar value (all viewers)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_text` — Set element text

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_text_all` — Set element text (all viewers)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_text_key` — Set element text (key)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `key` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_text_key_all` — Set element text key (all viewers)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `key` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_texture` — Set element texture

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `texture` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_texture_all` — Set element texture (all viewers)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `texture` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_visible` — Set element visible

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `visible` | `blueprint:bool` | `true` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:gui/set_visible_all` — Set element visible (all viewers)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `screen` | `blueprint:string` | `` |
| `element` | `blueprint:string` | `` |
| `visible` | `blueprint:bool` | `true` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


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


## map/build

### `blueprint:map/empty` — Empty map

permission `SAFE` · fuel 1 · P

| Sorties | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |

### `blueprint:map/put` — Put in map

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |
| `key` | `K` | — |
| `value` | `V` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |

### `blueprint:map/remove` — Remove from map

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |
| `key` | `K` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |


## map/query

### `blueprint:map/get` — Get from map

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |
| `key` | `K` | — |
| `fallback` | `V` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `value` | `V` | — |
| `found` | `blueprint:bool` | — |

### `blueprint:map/has` — Map contains key

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |
| `key` | `K` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `has` | `blueprint:bool` | — |

### `blueprint:map/is_empty` — Is map empty

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `empty` | `blueprint:bool` | — |

### `blueprint:map/keys` — Map keys

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `keys` | `list<K>` | — |

### `blueprint:map/size` — Map size

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `size` | `blueprint:int` | — |

### `blueprint:map/values` — Map values

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `map` | `map<K, V>` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `values` | `list<V>` | — |


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


## math/numeric

### `blueprint:math/ceil` — Ceiling

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/clamp` — Clamp

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |
| `min` | `blueprint:double` | `0.0` |
| `max` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/floor` — Floor

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/lerp` — Lerp

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `from` | `blueprint:double` | `0.0` |
| `to` | `blueprint:double` | `1.0` |
| `t` | `blueprint:double` | `0.5` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/pow` — Power

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `base` | `blueprint:double` | `1.0` |
| `exponent` | `blueprint:double` | `2.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/sign` — Sign

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/sqrt` — Square root

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |


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


## math/trig

### `blueprint:math/atan2` — Angle (atan2)

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `y` | `blueprint:double` | `0.0` |
| `x` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `angle` | `blueprint:double` | — |

### `blueprint:math/cos` — Cosine

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |

### `blueprint:math/sin` — Sine

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | `0.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:double` | — |


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


## player/act

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


## player/feedback

### `blueprint:player/action_bar` — Action bar

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/bossbar_hide` — Hide boss bar

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `bar` | `blueprint:string` | `principale` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/bossbar_show` — Show boss bar

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `bar` | `blueprint:string` | `principale` |
| `title` | `blueprint:string` | `` |
| `progress` | `blueprint:double` | `1.0` |
| `color` | `blueprint:string` | `white` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/particles` — Particles (private)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `particle` | `blueprint:resourcelocation` | — |
| `pos` | `blueprint:vec3` | — |
| `count` | `blueprint:int` | `8` |
| `spread` | `blueprint:double` | `0.5` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/play_sound` — Play sound (private)

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `sound` | `blueprint:resourcelocation` | — |
| `volume` | `blueprint:double` | `1.0` |
| `pitch` | `blueprint:double` | `1.0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/send_text` — Send rich text

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `text` | `blueprint:text` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/subtitle` — Subtitle

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `text` | `blueprint:string` | `` |

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

### `blueprint:player/title_text` — Rich text title

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `text` | `blueprint:text` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:player/title_times` — Title timings

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `fade_in` | `blueprint:int` | `10` |
| `stay` | `blueprint:int` | `70` |
| `fade_out` | `blueprint:int` | `20` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |


## player/inventory

### `blueprint:player/count_item` — Count item

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `item` | `blueprint:resourcelocation` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `count` | `blueprint:int` | — |

### `blueprint:player/has_item` — Has item

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `item` | `blueprint:resourcelocation` | — |
| `count` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `has` | `blueprint:bool` | — |

### `blueprint:player/main_hand` — Main hand item

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `item` | `blueprint:itemstack` | — |

### `blueprint:player/off_hand` — Off hand item

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `item` | `blueprint:itemstack` | — |

### `blueprint:player/remove_item` — Remove item

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `player` | `blueprint:player` | — |
| `item` | `blueprint:resourcelocation` | — |
| `count` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `removed` | `blueprint:int` | — |


## scoreboard

### `blueprint:score/add` — Add to score

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `objective` | `blueprint:string` | `points` |
| `amount` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `score` | `blueprint:int` | — |

### `blueprint:score/get` — Get score

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `objective` | `blueprint:string` | `points` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `score` | `blueprint:int` | — |
| `exists` | `blueprint:bool` | — |

### `blueprint:score/reset` — Reset score

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `objective` | `blueprint:string` | `points` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:score/set` — Set score

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |
| `objective` | `blueprint:string` | `points` |
| `score` | `blueprint:int` | `0` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

### `blueprint:team/of` — Entity team

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `entity` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `team` | `blueprint:string` | — |
| `in_team` | `blueprint:bool` | — |

### `blueprint:team/same` — Same team

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `a` | `blueprint:entity` | — |
| `b` | `blueprint:entity` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `same` | `blueprint:bool` | — |


## string/edit

### `blueprint:convert/to_number` — Text to number

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `value` | `blueprint:double` | — |
| `valid` | `blueprint:bool` | — |

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

### `blueprint:string/join` — Join

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `parts` | `list<blueprint:string>` | — |
| `separator` | `blueprint:string` | ` ` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | — |

### `blueprint:string/lower` — Lowercase

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:string` | — |

### `blueprint:string/replace` — Replace

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | `` |
| `search` | `blueprint:string` | `` |
| `replacement` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | — |

### `blueprint:string/split` — Split

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | `` |
| `separator` | `blueprint:string` | ` ` |

| Sorties | Type | Défaut |
|---|---|---|
| `parts` | `list<blueprint:string>` | — |

### `blueprint:string/substring` — Substring

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | `` |
| `from` | `blueprint:int` | `0` |
| `length` | `blueprint:int` | `1` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | — |

### `blueprint:string/trim` — Trim

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | — |

### `blueprint:string/upper` — Uppercase

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:string` | — |


## string/query

### `blueprint:string/contains` — Contains

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `value` | `blueprint:string` | `` |
| `search` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |

### `blueprint:string/ends_with` — Ends with

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | `` |
| `suffix` | `blueprint:string` | `` |

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

### `blueprint:string/starts_with` — Starts with

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:string` | `` |
| `prefix` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `result` | `blueprint:bool` | — |


## text

### `blueprint:text/click_command` — Click: run command

permission `ADMIN` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |
| `command` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |

### `blueprint:text/click_copy` — Click: copy

permission `GAMEPLAY` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |
| `value` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |

### `blueprint:text/click_suggest` — Click: suggest

permission `GAMEPLAY` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |
| `command` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |

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

### `blueprint:text/hover` — Hover text

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |
| `tooltip` | `blueprint:text` | — |

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

### `blueprint:text/styled` — Styled text

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |
| `bold` | `blueprint:bool` | `false` |
| `italic` | `blueprint:bool` | `false` |
| `underlined` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |

### `blueprint:text/translate` — Translated text

permission `SAFE` · fuel 1 · P

| Entrées | Type | Défaut |
|---|---|---|
| `key` | `blueprint:string` | `` |
| `arg` | `blueprint:string` | `` |

| Sorties | Type | Défaut |
|---|---|---|
| `text` | `blueprint:text` | — |


## world/block

### `blueprint:world/block_state` — Block state

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `block` | `blueprint:resourcelocation` | — |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `state` | `blueprint:blockstate` | — |
| `valid` | `blueprint:bool` | — |

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

### `blueprint:world/raycast` — Raycast

permission `SAFE` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `from` | `blueprint:vec3` | — |
| `direction` | `blueprint:vec3` | — |
| `distance` | `blueprint:double` | `16.0` |
| `through_fluids` | `blueprint:bool` | `false` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |
| `hit` | `blueprint:bool` | — |
| `pos` | `blueprint:blockpos` | — |
| `face` | `blueprint:direction` | — |
| `point` | `blueprint:vec3` | — |

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

### `blueprint:world/bossbar_remove` — Remove boss bar

permission `GAMEPLAY` · fuel 1

| Entrées | Type | Défaut |
|---|---|---|
| `exec_in` | exec | — |
| `bar` | `blueprint:string` | `principale` |

| Sorties | Type | Défaut |
|---|---|---|
| `exec_out` | exec | — |

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

