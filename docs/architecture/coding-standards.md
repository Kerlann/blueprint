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

### 7.1 Comment mesurer, pour que le banc mesure le code

Cinq bancs de ce projet ont porté un budget en **temps mural**. Trois ont fini par rougir
sur la machine d'intégration **sans qu'aucun code n'ait changé** — dix-huit constructions
rouges, réparties sur `PaletteTest`, `CompilerPerfTest` et `DesignerLayoutCacheTest`.

Ce n'est pas de la malchance : une machine partagée met parfois trois fois plus longtemps
à faire la même chose, et un banc en temps mural mesure alors sa charge. Le coût réel n'est
pas la construction rouge, c'est ce qu'elle enseigne — **relancer plutôt que chercher**. Une
vraie régression finit ainsi par passer pour un caprice de la machine.

Trois formes, dans cet ordre de préférence :

1. **Un rapport entre deux mesures prises au même moment.** Les deux subissent la même
   machine, donc leur rapport n'en dépend plus. À privilégier dès qu'il existe une
   référence naturelle — le même travail sans le cache, quatre fois plus de données.
   *Exemples : `EventDispatchPerfTest`, `DesignerLayoutCacheTest`.*
2. **Le temps processeur du fil** (`ThreadMXBean#getCurrentThreadCpuTime`), quand le seuil
   est une **exigence du produit** qu'on ne veut pas diluer. Il ne compte que les instants
   où le fil a réellement tourné : une préemption ne s'y voit pas, une régression si.
   *Exemples : `CompilerPerfTest` (NFR2), `PaletteTest` (AC4).*

   **Toujours l'agréger.** Cette horloge est grossière — environ 15 ms sous Windows — et
   mesurer une opération plus courte rend `0`. Le test passe alors **à vide**, ce qui est
   pire que rouge : il ne vérifie plus rien et personne ne s'en aperçoit. Mesurer assez
   d'itérations pour dépasser largement la granularité, puis diviser, et **asserter que la
   mesure n'est pas nulle**. Le piège s'est refermé deux fois dans la même session.
3. **Le temps mural**, seulement avec une marge d'un ordre de grandeur. C'est le cas des
   bancs de rendu, qui mesurent des images entières : ils n'ont jamais rougi.

Et la règle qui vaut pour les trois : **un banc qu'on n'a jamais vu échouer ne prouve
rien.** Avant de le commiter, remettre le défaut qu'il surveille et vérifier qu'il rougit.

## 8. Documentation

- Tout type public de `api` a une javadoc avec un exemple d'usage.
- Tout `NodeType` fournit une description traduite : elle alimente la palette **et** la
  référence des nœuds générée automatiquement (story 9.5).
- Les commentaires expliquent **pourquoi**, pas **quoi**. Le style du dépôt est le
  français, en cohérence avec les commentaires existants de `build.gradle.kts`.
