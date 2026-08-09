# Blueprints

**An in-game node editor for Minecraft 1.21.11 (Fabric).**

*[Version française](README.fr.md)*

Place nodes, wire them, declare typed variables — and the graph runs **server-side** on
real world events. Every graph compiles to a readable text script (**BScript**) and every
BScript parses back into a graph. Other mods declare their own nodes with no hard
dependency, and removing such a mod never breaks an existing graph: its nodes become
**ghosts** that come back to life when the mod is reinstalled.

> **Status: v1.0.0 released**, plus an optimisation pass since (epics 13–19) and the
> groundwork for multiple loaders (see below). The nine PRD epics, plus three born of
> actual use — **screens** (epic 10), **declared content** (epic 11) and **the editor in
> practice** (epic 12). 86 story documents, 85 QA gates, **1 418 headless tests** and
> **21 gametests** in a real server. What remains is the visual review session listed in
> [`docs/README.md`](docs/README.md): it covers only what no test can judge — look and
> feel.

> **Fabric is what ships.** A `neoforge` module lives in this repository and boots on both
> sides, but it is **not published**: three known gaps remain, and no gametest runs on that
> side. See [`docs/plan-multiloader.md`](docs/plan-multiloader.md) for exactly what has
> been verified and what has not.

---

## Getting started

```bash
./gradlew runClient
```

> The development client launches under a FIXED username (`build.gradle.kts`). Without it,
> Loom picks a random one on every launch, and since the offline UUID is derived from the
> username, every launch is a different player: anything stored per player — a roleplay
> character, say — starts from scratch with nothing to explain why.

Then, in game:

| | |
|---|---|
| `/blueprint showcase` then `/bpc vitrine` | see the twelve widget kinds, all wired |
| `/blueprint bench` then `/bpc bench` | run the performance bench |
| `/blueprint-edit create my_first` | create your own |
| `F6` | reopen the last one edited |

The step-by-step guide is in [`docs/getting-started.md`](docs/getting-started.md) — first
blueprint in ten minutes, shortcuts, troubleshooting.

## What the mod can do

- **A full editor**: palette, typed wiring, literals on nodes, variables and scopes,
  undo/redo, copy-paste as BScript, clickable diagnostics, script view, comments, minimap,
  a reloadable JSON theme, keyboard navigation.
- **Safe execution**: compiled to IR, a VM bounded by a fuel budget, suspension that
  survives a restart, and a greedy or faulting blueprint disabled automatically.
- **239 node types** shipped — screens (34), events (26), world (20), maths (20), player
  (19), strings (12), entities (12), flow (12), vectors (11), logic (10), lists, maps,
  text, items, positions, scoreboards — plus `/bpc <name>` to fire a graph from a command.
- **Multiplayer**: registry sync on login, packet-based open and save with optimistic
  locking, graph guards and server-side quotas.
- **Extensible three ways**: a Java builder, a `@BlueprintNode` annotation, or plain
  datapack JSON — see [`docs/extension-api.md`](docs/extension-api.md).
- **Debugging**: breakpoints, single-step and live values in the editor; a per-node
  profiler; an audit trail for `ADMIN` nodes.

## What it costs a server

A node editor readily suggests a sluggish interpreter. Here are the measurements, taken by
benchmarks committed to the repository that **fail the build** if they drift.

**In a real server**, with graphs wired to `server_tick`:

| Active graphs | Scheduling time per tick | Share of the 50 ms budget |
|---|---|---|
| 50 | ~0.25 ms | **0.5 %** |
| 200 | ~0.6 ms | **1.2 %** |

*(gametest `quadruplerLesGraphesNeQuadruplePasLeCoutParGraphe`, real dedicated server)*

**On isolated paths**, before and after the optimisation work:

| | Before | After |
|---|---|---|
| Allocation per node call | 744 B | **288 B** |
| Compiling a dense 1 000-node graph | 112 ms | **~4 ms** |
| Validation, when the graph quadruples | × 3.67 | **× 1.00** |
| Screen layout, when elements quadruple | × 3.67 | **× 0.44** |

**And you can check it yourself, in three commands.** The mod ships a
[performance bench](docs/examples/README.md): a graph with three nested loops — two hundred
`for` iterations, two hundred `while`, one list traversal — that you can open, read and
edit in the editor.

```
/blueprint bench                            # installs and enables it
/blueprint profile blueprint:bench on
/bpc bench                                  # three times, to warm the JIT
/blueprint profile blueprint:bench reset    # discard the warm-up rounds
/bpc bench                                  # the round we measure
/blueprint profile blueprint:bench
```

| Per run | |
|---|---|
| Node calls | **1 034** |
| Fuel consumed | **4 286** out of a tick's 10 000 |
| Time in the VM, warm | **~350 µs** |

All three reproduce identically from one run to the next — to the point that two
successive warm measurements land within 5 % of each other.

The reset is not a flourish: the profiler **accumulates**, and the very first round costs
**seven times** more than the ones after it. The JVM compiler has not warmed up yet — that
holds for any Java code and is nothing specific to this mod, but reading the number without
discarding it is wrong by an order of magnitude.

The report also shows where the time actually goes: the four hundred additions in the loop
cost less than a single `player/send_message`, which builds and sends a network packet. In
a gameplay graph, it is the **effects** that cost, not the arithmetic.

Three things explain these figures, and none of them is a sleight of hand:

- **a graph is compiled**, not interpreted node by node — the lowering to IR happens once
  and is cached per revision;
- **the execution loop barely allocates**: the call context reuses its execution's buffers,
  and types as well as pins are resolved once for the whole IR instead of being looked up
  at every node;
- **nothing is unbounded**: every node declares its fuel cost, a tick has a budget, and a
  graph that exceeds it is cut rather than left to slow everyone down.

The measurement method is described in
[`docs/architecture/coding-standards.md`](docs/architecture/coding-standards.md) §7.1, and
the detail of every optimisation — including what was **rejected** and why — in
[`docs/plan-optimisation.md`](docs/plan-optimisation.md).

## Building and testing

```bash
./gradlew build          # 9 modules, 1 418 headless tests, coverage, generated docs
./gradlew runGametest    # 21 tests in a real server, no window
./gradlew runClient      # play
```

`build` fails if: a test falls, `core` coverage drops below 80 %, the `api` module
references the implementation, a common module names a mod loader, the node reference no
longer matches the registry, or the public API surface changed without being regenerated.
The last two regenerate with `-Dblueprint.regenDocs=true`.

> **Do not build while the game is running**: Gradle rewrites the jars under the JVM and
> the classloader hits a half-written zip (`ZipException: invalid LOC header`).

## Structure

Multi-module, with **one JAR per loader** (`build/libs/blueprint-<version>.jar` for
Fabric):

```
api/        public surface for third-party mods (publishable alone: blueprint-api)
platform/   what common code asks of the loader — interfaces only, no answers
core/       model, registry, compiler, VM, BScript, persistence, server networking
client/     visual editor and client networking
compat/     conditional integrations with third-party mods
fabric/     Fabric entrypoint and platform implementations
neoforge/   the same for NeoForge — builds and boots, not published
testmod/    example mod validating the api (excluded from the shipped JAR)
gametest/   tests played in a real server (excluded from the shipped JAR)
```

**No common module may name a mod loader.** That is not discipline but a compile-time
fact: `api`, `platform`, `core`, `client` and `compat` do not have fabric-api on their
classpath, so writing `import net.fabricmc` there is an error on the line. The
`checkLoaderIsolation` task catches what the compiler cannot — a fully qualified name in a
comment, a string, a reflective lookup.

The `api` module cannot reference the implementation either: `:api:checkApiIsolation`,
wired into `check`, fails the build otherwise. Its public surface is frozen in
[`docs/api-surface.txt`](docs/api-surface.txt) and compared on every build.

```bash
./gradlew :api:publishToMavenLocal   # publishes fr.blueprint:blueprint-api
```

## Stack

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loom | 1.13.6 |
| Fabric Loader | 0.18.2 |
| Fabric API | 0.139.4+1.21.11 |
| NeoForge *(not published)* | 21.11.45 |
| ModDevGradle | 2.0.143 |
| Java | 21 |
| Gradle | 8.14 |

Official Mojang mappings. The naming traps encountered along the way are recorded in
[`docs/architecture/tech-stack.md`](docs/architecture/tech-stack.md) — worth reading before
calling a game API.

## Documentation

The full design record (BMAD method) is in [`docs/`](docs/README.md): brief, PRD,
architecture, BScript specification, extension API, editor UX spec, stories and QA gates.
Most of it is in French.

| To | Read |
|---|---|
| Play | [`docs/getting-started.md`](docs/getting-started.md) |
| Measure performance in game | [`docs/examples/`](docs/examples/README.md) |
| Look up a node | [`docs/node-reference.md`](docs/node-reference.md) *(generated)* |
| Write a companion mod | [`docs/extension-api.md`](docs/extension-api.md) |
| Understand the choices | [`docs/architecture.md`](docs/architecture.md) |
| Follow the multi-loader work | [`docs/plan-multiloader.md`](docs/plan-multiloader.md) |
| See what changed | [`CHANGELOG.md`](CHANGELOG.md) |
| Know where the project stands | [`docs/rapport-de-fin.md`](docs/rapport-de-fin.md) |

## Licence

[MIT](LICENSE) — © 2026 Kerlann.

You may use it, modify it, redistribute it and sell it, including in a closed project; the
only obligation is to keep the copyright notice. A companion mod built on `blueprint-api`
is free to pick its own licence.
