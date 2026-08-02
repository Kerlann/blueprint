# Source Tree — Blueprint

> Shard chargé systématiquement par l'agent Dev. **Toute nouvelle classe se place dans
> l'arborescence ci-dessous.** Si aucun emplacement ne convient, la story doit d'abord
> mettre à jour ce fichier.

## État actuel (avant épic 1)

```
D:\Blueprint\
├─ build.gradle.kts
├─ settings.gradle.kts          rootProject.name = "blueprint"
├─ gradle.properties
├─ README.md
└─ src/main/
   ├─ java/fr/blueprint/
   │  ├─ BlueprintMod.java              entrypoint main
   │  └─ client/BlueprintClient.java    entrypoint client
   └─ resources/
      ├─ fabric.mod.json
      └─ assets/blueprint/lang/{en_us,fr_fr}.json   (vides)
```

## Cible (après épic 1, story 1.1)

```
D:\Blueprint\
├─ build.gradle.kts                 build racine, config commune des sous-projets
├─ settings.gradle.kts              include("api", "core", "client", "compat", "testmod")
│
├─ api/src/main/java/fr/blueprint/api/
│  ├─ BlueprintApi.java             API_VERSION, points d'entrée statiques
│  ├─ BlueprintPlugin.java          interface d'entrypoint "blueprint"
│  ├─ node/
│  │  ├─ NodeType.java              + NodeType.Builder
│  │  ├─ NodeContext.java           in/out/exec/suspend/level/server
│  │  ├─ NodeCategory.java  NodeCategories.java   (constantes standard)
│  │  ├─ NodeDescriptor.java        forme transmissible au client
│  │  ├─ Permission.java            SAFE | GAMEPLAY | WORLD | ADMIN
│  │  ├─ ExecSide.java
│  │  └─ annotation/                @BlueprintNode, @In, @Out
│  ├─ pin/
│  │  ├─ PinType.java  PinTypes.java  PinKind.java  PinShape.java
│  │  └─ LiteralValue.java
│  ├─ event/
│  │  ├─ EventType.java  EventRegistry.java  BlueprintEvents.java
│  ├─ registry/
│  │  ├─ NodeRegistry.java  PinTypeRegistry.java
│  └─ package-info.java             @ApiStatus, politique de versionnage
│
├─ core/src/main/java/fr/blueprint/core/
│  ├─ BlueprintMod.java             (déplacé) entrypoint main
│  ├─ graph/                        ⚠ un seul paquet : le package-private Java ne
│  │  │                             traverse pas les sous-paquets, et les mutations
│  │  │                             du modèle sont réservées aux EditOperation
│  │  ├─ Blueprint.java  Node.java  Link.java  Variable.java  VarScope.java
│  │  ├─ CommentBox.java  BlueprintMeta.java  Vec2d.java  GraphLimits.java
│  │  ├─ GraphValidator.java  Diagnostic.java  DiagnosticCode.java  Literals.java
│  │  ├─ NodeTypeLookup.java  NodeShape.java   raccord provisoire vers NodeRegistry (2.2)
│  │  ├─ GhostNode.java             forme déduite d'un nœud au type absent
│  │  ├─ EditOperation.java         interface scellée + les 16 ops en records imbriqués
│  │  └─ GraphNbt.java  PinTypeNbt.java  SchemaMigrations.java   sérialisation (1.4)
│  ├─ registry/
│  │  ├─ NodeRegistryImpl.java  PinTypeRegistryImpl.java  RegistryHash.java
│  │  ├─ NodeDescriptor.java        forme transmissible au client (2.4 — vit ici, pas
│  │  │                             dans api : réutilise PinTypeNbt, consommé par core/client)
│  │  └─ PluginLoader.java          charge l'entrypoint "blueprint", isole les erreurs
│  ├─ compile/
│  │  ├─ Compiler.java  SlotAllocator.java  ExecLinearizer.java  PureScheduler.java
│  │  └─ ir/  Ir.java  Instruction.java  Opcode.java  IrCodec.java
│  ├─ vm/
│  │  ├─ BlueprintVm.java  ExecutionState.java  Frame.java  Fuel.java
│  │  ├─ ExecResult.java  BlueprintScheduler.java  ExecutionStore.java
│  │  └─ Profiler.java
│  ├─ script/
│  │  ├─ Lexer.java  Parser.java  Ast.java
│  │  ├─ ScriptGenerator.java       graphe → BScript
│  │  ├─ ScriptLoader.java          BScript → graphe
│  │  └─ AutoLayout.java
│  ├─ net/                          payloads + handlers serveur
│  ├─ storage/                      (les codecs du graphe vivent dans graph/, voir plus haut)
│  │  ├─ BlueprintStorage.java      SavedData (story 6.1)
│  │  └─ PlayerVarStore.java
│  ├─ nodes/                        bibliothèque standard
│  │  ├─ flow/  math/  logic/  string/  list/  struct/
│  │  ├─ world/  entity/  player/  item/  text/  debug/
│  │  └─ StandardNodes.java         plugin interne enregistrant tout
│  ├─ event/                        branchements sur les callbacks Fabric
│  ├─ command/  BlueprintCommand.java
│  ├─ datapack/  JsonNodeLoader.java
│  └─ config/  BlueprintConfig.java
│
├─ client/src/main/java/fr/blueprint/client/
│  ├─ BlueprintClient.java          (déplacé) entrypoint client
│  ├─ editor/
│  │  ├─ BlueprintEditorScreen.java
│  │  ├─ CanvasWidget.java  Camera.java  SelectionModel.java
│  │  ├─ NodeWidget.java  PinWidget.java  WireLayer.java
│  │  ├─ PalettePopup.java  NodeSearch.java
│  │  ├─ VariablePanel.java  DetailsPanel.java  DiagnosticsPanel.java
│  │  ├─ ScriptView.java  Minimap.java
│  │  └─ history/ UndoStack.java
│  ├─ debug/  DebugOverlay.java  WatchValues.java
│  ├─ theme/  Theme.java  ThemeLoader.java
│  ├─ registry/ ClientNodeRegistry.java   descripteurs reçus du serveur
│  └─ net/                          handlers client
│
├─ compat/src/main/java/fr/blueprint/compat/
│  ├─ CompatLoader.java             charge conditionnellement selon isModLoaded
│  └─ <modid>/…                     une intégration par mod
│
├─ testmod/src/main/java/fr/blueprint/testmod/
│  └─ TestPlugin.java               3 nœuds d'exemple validant l'API (story 2.2)
│
└─ src/main/resources/  →  réparti par module
   ├─ fabric.mod.json               entrypoints main / client / blueprint
   ├─ assets/blueprint/lang/{en_us,fr_fr}.json
   ├─ assets/blueprint/theme/default.json
   ├─ assets/blueprint/textures/gui/…
   └─ data/blueprint/blueprint/nodes/…    nœuds composites livrés en datapack
```

## Où mettre quoi — table de décision

| Ce que j'écris | Où |
|---|---|
| Une interface qu'un mod tiers implémente ou appelle | `api/` |
| Un nœud de la bibliothèque standard | `core/nodes/<catégorie>/` |
| Une règle de validation de graphe | `core/graph/GraphValidator` + un `DiagnosticCode` |
| Une instruction de la VM | `core/vm/` + `core/compile/ir/Opcode` |
| Un paquet réseau | payload dans `core/net/`, handler client dans `client/net/` |
| Un widget de l'éditeur | `client/editor/` |
| Une intégration avec un mod précis | `compat/<modid>/` — **jamais** ailleurs |
| Une chaîne visible | les deux fichiers `lang/` |

## Ressources et données

| Chemin | Rôle |
|---|---|
| `assets/blueprint/theme/default.json` | Jetons de style de l'éditeur |
| `data/<modid>/blueprint/nodes/*.json` | Nœuds composites de datapack |
| `config/blueprint/config.json` | Config serveur (fuel, permissions, limites) |
| `config/blueprint/exports/*.bp` | Exports BScript |
| Sauvegarde du monde (`SavedData`) | Blueprints, exécutions suspendues, variables `WORLD` |
