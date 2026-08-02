# Tech Stack — Blueprint

> Shard chargé systématiquement par l'agent Dev. **Aucune technologie hors de ce tableau
> ne doit être introduite sans mise à jour de ce fichier.**

## Plateforme

| Composant | Version | Notes |
|---|---|---|
| Minecraft | 1.21.11 | Cible unique v1.0 |
| Mappings | **Officiels Mojang** | `loom.officialMojangMappings()` — voir la section Nommage |
| Fabric Loom | 1.13.6 | |
| Fabric Loader | 0.18.2 | |
| Fabric API | 0.139.4+1.21.11 | Events, networking, commands, gametest |
| Java | 21 | `JavaLanguageVersion.of(21)`, sources **UTF-8** |
| Gradle | 8.14 | Kotlin DSL |

## Bibliothèques autorisées

| Usage | Bibliothèque | Portée |
|---|---|---|
| Journalisation | SLF4J (fourni par le loader) | Toutes |
| Sérialisation | `Codec` / `DynamicOps` de Mojang (DFU) + NBT | `core` |
| Tests unitaires | JUnit 5 | `test` |
| Tests par propriétés / fuzzing | jqwik | `test` |
| Tests en jeu | Fabric Gametest | `test` |
| Compatibilité binaire | japicmp | build de `api` |
| Annotations d'état d'API | `org.jetbrains:annotations` (`@ApiStatus`, `@Nullable`) | `api` |

**Interdit sans décision d'architecture** : toute bibliothèque GUI tierce, tout moteur de
script embarqué (Rhino, Nashorn, Groovy…), toute dépendance réseau externe, toute
réflexion dans la boucle de tick, tout chargement de classe à l'exécution.

> ACsGuis n'est **pas** utilisable : c'est un projet Forge 1.12. Le style de l'éditeur
> passe par `assets/blueprint/theme/*.json` (jetons inspirés de CSS), pas par une
> feuille de style CSS réelle.

## Nommage Mojang (piège fréquent)

Le projet utilise les mappings officiels. Les noms Yarn ne compilent pas.

> ⚠️ **Vérifié en 1.21.11 (story 1.2)** : Mojang a renommé `ResourceLocation` en
> **`Identifier`** (`net.minecraft.resources.Identifier`, mêmes méthodes :
> `fromNamespaceAndPath`, `getNamespace`, `getPath`, `CODEC`, `STREAM_CODEC`).
> Les documents de conception antérieurs à cette découverte écrivent encore
> `ResourceLocation` : lire `Identifier`. En cas de doute sur un nom, vérifier dans
> le JAR mergé de Loom (`javap`), pas de mémoire.

| ✅ Mojang 1.21.11 (à utiliser) | ❌ À ne pas écrire |
|---|---|
| `Identifier` (`net.minecraft.resources`) | `ResourceLocation` (renommé par Mojang en 1.21.x) |
| `CompoundTag`, `ListTag`, `Tag` | `NbtCompound`, `NbtList`, `NbtElement` |
| `GuiGraphics` | `DrawContext` |
| `Level`, `ServerLevel` | `World`, `ServerWorld` |
| `SavedData` | `PersistentState` |
| `Component`, `MutableComponent` | `Text`, `MutableText` |
| `ServerPlayer` | `ServerPlayerEntity` |
| `BlockPos`, `Vec3`, `Direction` | (identiques) |
| `Screen`, `AbstractWidget` | `Screen`, `ClickableWidget` |

## API Fabric utilisées

| Besoin | API |
|---|---|
| Entrypoints | `fabric.mod.json` → `main`, `client`, **`blueprint`** (custom) |
| Commandes | `CommandRegistrationCallback.EVENT` |
| Réseau | `CustomPacketPayload` + `StreamCodec` + `PayloadTypeRegistry` + `ServerPlayNetworking` / `ClientPlayNetworking` |
| Tick serveur | `ServerTickEvents.END_SERVER_TICK` |
| Connexion | `ServerPlayConnectionEvents.JOIN` / `DISCONNECT` |
| Interaction | `UseBlockCallback`, `UseItemCallback`, `AttackBlockCallback`, `PlayerBlockBreakEvents` |
| Entités | `ServerLivingEntityEvents.AFTER_DEATH` |
| Chat | `ServerMessageEvents.CHAT_MESSAGE` |
| Datapacks | `ResourceManagerHelper` (`SimpleSynchronousResourceReloadListener`) |
| Persistance | `SavedData` via `SavedDataType` + `Codec` sur `DimensionDataStorage` |
| Tests | `fabric-gametest-api-v1` |

## Contraintes de build

- `options.encoding = "UTF-8"` sur tous les `JavaCompile` (déjà en place ; sans ça les
  accents des messages français sortent en mojibake sous Windows).
- Le build doit échouer si le module `api` référence une classe de `core`.
- Le build doit échouer si les seuils de performance de la CI sont dépassés.
