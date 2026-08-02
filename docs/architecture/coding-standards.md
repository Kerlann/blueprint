# Coding Standards — Blueprint

> Shard chargé systématiquement par l'agent Dev. Règles **critiques** uniquement :
> ce fichier n'enseigne pas Java, il fixe ce qui est spécifique à ce projet.

## 1. Règles absolues

1. **Mappings Mojang 1.21.11.** `Identifier` (ex-`ResourceLocation`, renommé par Mojang),
   `CompoundTag`, `GuiGraphics`, `Level`, `Component`, `SavedData`. Voir la table et la
   procédure de vérification (`javap`) dans `tech-stack.md`. Ni noms Yarn, ni anciens noms Mojang.
2. **Le module `api` ne référence jamais `core`.** Il ne contient que des interfaces,
   des records, des enums et des builders. Aucune logique métier.
3. **Aucune exécution de graphe côté client.** Le paquet `fr.blueprint.client` ne doit
   contenir ni compilateur, ni VM, ni évaluation de nœud. Il n'affiche que des descripteurs.
4. **Aucune réflexion, aucun chargement de classe, aucune allocation superflue dans la
   boucle de tick** (`BlueprintScheduler`, `BlueprintVm#step`). Réutiliser les tampons.
5. **Aucune exception ne s'échappe vers la boucle de tick.** Tout `CALL` de nœud est
   entouré d'une capture qui journalise le nœud fautif et met le blueprint en `FAULTED`.
6. **Toute entrée réseau est revalidée côté serveur.** Un client est hostile par défaut.
7. **Aucun `System.out`.** Utiliser `BlueprintMod.LOGGER`.
8. **Aucune chaîne littérale visible par l'utilisateur.** Toujours `Component.translatable`
   avec une clé présente dans `en_us.json` **et** `fr_fr.json`.

## 2. Nommage et organisation

- Package racine `fr.blueprint`. Sous-paquets : `api`, `core.graph`, `core.registry`,
  `core.compile`, `core.vm`, `core.script`, `core.net`, `core.storage`, `core.nodes`,
  `client.editor`, `client.render`, `client.net`, `compat.<modid>`.
- Une classe publique par fichier, nommée comme le fichier.
- Les identifiants de nœud sont en `snake_case` sous le namespace du mod fournisseur :
  `blueprint:flow/branch`, `mymod:heal_player`.
- Les clés de traduction suivent le motif :
  `blueprint.node.<namespace>.<path>.name` / `.desc`,
  `blueprint.pin.<...>.name`, `blueprint.diag.<code>`.
- Les constantes `Identifier` sont statiques finales, jamais construites en boucle.

## 3. Immuabilité et nullité

- `NodeType`, `PinType`, `NodeDescriptor`, `Ir` et toutes les valeurs de l'`api` sont
  **immuables**. Les collections exposées passent par `List.copyOf` / `Map.copyOf`.
- `Blueprint` est mutable mais **uniquement** via des `EditOperation` réversibles :
  aucune mutation directe depuis l'extérieur du paquet `core.graph`.
- `@Nullable` (JetBrains) est obligatoire sur tout retour ou paramètre pouvant être nul
  dans `api`. L'absence d'annotation signifie non-nul.
- Pas d'`Optional` en champ ni en paramètre ; en retour uniquement quand l'absence est
  un cas normal.

## 4. Erreurs et diagnostics

- **Erreur de développeur** (mod tiers mal codé, pin inexistant, type incohérent) →
  exception au démarrage ou à la construction, jamais en jeu, message nommant le mod.
- **Erreur d'utilisateur** (graphe invalide) → `Diagnostic` avec code, sévérité, cible
  (UUID de nœud ou lien) et arguments de traduction. Jamais une exception.
- **Erreur d'exécution** → capture, journal, `FAULTED`. Jamais de propagation.
- Un message d'erreur cite toujours **quel nœud** et **quel mod**.

## 5. Performance

- Pas d'allocation dans `BlueprintVm#step` hors des valeurs produites par les nœuds.
- Pas de `stream()` dans le code exécuté par tick ni dans le rendu de l'éditeur.
- Le rendu de l'éditeur cull avant de calculer quoi que ce soit.
- Toute structure indexée par nœud utilise l'index de slot, pas une `HashMap<UUID, ?>`,
  dans les chemins chauds.

## 6. Sérialisation

- Toute structure persistée porte un `schemaVersion` et une migration.
- Un identifiant inconnu se **conserve**, ne se supprime pas (nœuds fantômes, principe P4).
- Tout codec a un test de round-trip.

## 7. Tests

- Toute story livre ses tests. Une story sans test n'est pas terminée.
- Les tests de `core` tournent **sans Minecraft démarré** (JUnit pur) : c'est une
  contrainte de conception, pas une préférence. Si un test de `core` a besoin d'un
  `MinecraftServer`, c'est que la couche est mal isolée.
- Tout codec, tout parseur et tout décodeur réseau a un test de fuzzing.
- Les seuils de performance (NFR1–NFR3) sont vérifiés en CI et font échouer le build.

## 8. Documentation

- Tout type public de `api` a une javadoc avec un exemple d'usage.
- Tout `NodeType` fournit une description traduite : elle alimente la palette **et** la
  référence des nœuds générée automatiquement (story 9.5).
- Les commentaires expliquent **pourquoi**, pas **quoi**. Le style du dépôt est le
  français, en cohérence avec les commentaires existants de `build.gradle.kts`.
