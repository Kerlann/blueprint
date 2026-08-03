# Rapport de fin — Blueprint 0.1.0

Relecture finale du 2026-08-03, à l'issue de la boucle de développement autonome.
Ce document répond à trois questions : **qu'est-ce qui est fait**, **qu'est-ce qui
reste**, et **qu'est-ce que je ne peux pas garantir**.

## 1. État

**Les neuf épics du PRD sont livrés.** 52 stories en statut *Done*, 51 gates QA
(certaines couvrent des stories groupées) toutes en verdict *PASS* — aucune en
*CONCERNS* ni en *FAIL*. La dernière *CONCERNS* restante, celle de la story 1.2,
a été réémise en *PASS* ce jour : ses deux points bloquants étaient du process
(balayage `Identifier`, couverture non mesurée), tous deux réglés depuis.

| Épic | Titre | Stories | Verdict |
|---|---|---|---|
| 1 | Fondations et modèle de graphe | 1.1 → 1.6 | 6 PASS |
| 2 | Registre de nœuds et API d'extension | 2.1 → 2.5 | 5 PASS |
| 3 | Compilateur et VM | 3.1 → 3.5 | 2 PASS (groupées) |
| 4 | BScript et démo | 4.1 → 4.4a | 2 PASS |
| 5 | Éditeur visuel | 5.1 → 5.13 | 17 PASS |
| 6 | Persistance et multijoueur | 6.1 → 6.4 | 4 PASS |
| 7 | Événements et bibliothèque | 7.1a → 7.8 | 5 PASS |
| 8 | Intégration des mods tiers | 8.1 → 8.5 | 5 PASS |
| 9 | Débogage, performance, finition | 9.1a → 9.5 | 6 PASS |

**Vérification automatique** : `./gradlew build` (541 tests headless, couverture
bloquante ≥ 80 % sur le cœur et ≥ 82 % sur la partie testable du client, référence
des nœuds et surface d'API régénérées et comparées) et `./gradlew runGametest`
(5 tests dans un serveur Minecraft réel). Les deux tournent en CI sur chaque push.

## 2. Ce que la QA a réellement trouvé

Une revue qui ne trouve rien n'a pas eu lieu. Sur l'ensemble du projet, les revues
ont produit **une correction haute par lot en moyenne**, et aucune n'était cosmétique.
Les plus coûteuses si elles étaient parties en production :

- **Perte de travail** (6.3) — un enregistrement refusé tard jetait le graphe du
  joueur au lieu de le garder.
- **Escalade de permission** (6.3) — le plafond du blueprint pouvait être relevé par
  celui-là même qu'il devait contraindre.
- **Portail inversé** (7.1b) — `JmpIf` saute vers *elseTarget* quand la condition est
  **fausse** ; le chemin ouvert avait été branché à l'envers, un portail fermé
  laissait passer.
- **Condition de boucle figée** (7.1b) — la mémoïsation des nœuds purs gelait la
  condition d'un `Tant que`, qui ne s'arrêtait jamais.
- **Rechargement de datapack emporté** (8.2) — un seul JSON au mauvais type tuait le
  rechargement entier, au lieu du seul fichier fautif.
- **Geste d'annulation laissé ouvert** (5.12) — un clic sur un fil aurait fait
  fusionner toutes les modifications suivantes en une seule annulation.
- **Treize paires de pins indiscernables en deutéranopie** (9.4) — violation franche
  de NFR11, trouvée par un test écrit pour l'occasion, corrigée à la racine.

La CI, dès sa mise en place, a immédiatement trouvé trois problèmes réels que la
machine de développement masquait — dont une NPE dans la reprise des exécutions
persistées, causée par des gametests tournant en parallèle.

## 3. Ce qui reste

### 3.1 La licence — tranchée

**MIT**, © 2026 Kerlann ([`LICENSE`](../LICENSE)). Le dépôt était jusque-là sous droit
d'auteur par défaut, ce qui interdisait à quiconque de le forker ou de le
redistribuer — l'exact contraire de ce que cherche un mod doté d'une API d'extension.
Le champ `license` des trois `fabric.mod.json` disait aussi « All Rights Reserved » :
c'est ce que le lanceur affiche aux joueurs, il est corrigé.

### 3.2 La session en jeu

**21 points à regarder**, listés dans [`README.md` §Prochaine action](README.md).
Tout ce qui se vérifie sans yeux l'est déjà ; ce qui reste est le visuel, l'ergonomie
et les comportements qui exigent un monde vivant (redémarrage, serveur dédié, retrait
d'un mod du dossier `mods`).

### 3.3 Reporté en v1.1, consigné story par story

| Sujet | Story | Pourquoi c'est reporté |
|---|---|---|
| Sucre syntaxique BScript | 4.2b | La grammaire v1 est complète, seulement verbeuse |
| Patchs par opération, multi-éditeur | 6.3 | Aujourd'hui : enregistrement complet sous verrou optimiste |
| Processeur d'annotations à la compilation | 8.1 | Aujourd'hui : réflexion, qui fonctionne |
| Corps BScript dans les nœuds de datapack | 8.2 | Demande le parseur côté serveur au rechargement |
| Reroutage d'un lien par double-clic | 5.12 | Demande que le modèle du cœur porte des points intermédiaires persistés et synchronisés |
| Câblage complet au clavier | 9.4 | La spécification UX se contredit entre §11 et §13 |
| Marqueur « variable inutilisée » | 5.5 | Demande une analyse d'atteignabilité que le validateur ne fait pas |

## 4. Ce que je ne peux pas garantir

Par honnêteté, les limites du harnais :

1. **Le rendu n'est vérifié par aucun test.** L'état de l'éditeur est couvert
   headless ; le dessin ne l'est pas, et ne peut pas l'être ici. C'est la raison
   d'être des 21 points de la session en jeu.
2. **Les 60 fps ne sont mesurés qu'en passe CPU** (banc `CanvasBenchTest`). Le coût
   GPU réel n'est pas borné en CI.
3. **Le multijoueur n'est éprouvé qu'à un seul client.** Le verrou optimiste est
   testé unitairement ; deux joueurs réels en simultané restent à voir (V12).
4. **Les codecs paresseux** (`itemstack`, `blockstate`, `text`) font leur round-trip
   en test, mais avec un registre de test, pas celui d'un serveur chargé de mods.
5. **La couverture du client exclut les classes de rendu.** La liste d'exclusions
   est dans `client/build.gradle.kts` : c'est l'énoncé de ce qui ne tourne pas sans
   jeu lancé, et elle doit rester courte. Toute logique qui y tomberait échapperait
   au seuil.

## 5. Où regarder

| Pour | Fichier |
|---|---|
| Jouer | [`getting-started.md`](getting-started.md) |
| Ce qui a changé | [`../CHANGELOG.md`](../CHANGELOG.md) |
| Chercher un nœud | [`node-reference.md`](node-reference.md) *(généré)* |
| Écrire un mod compagnon | [`extension-api.md`](extension-api.md) |
| Comprendre les choix | [`architecture.md`](architecture.md) |
| Ce qui reste à voir en jeu | [`README.md`](README.md) §Prochaine action |
