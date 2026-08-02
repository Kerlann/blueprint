# Documentation Blueprint — dossier BMAD

Dossier de planification suivant la **méthode BMAD** (Analyst → PM → Architect → UX → SM → Dev → QA).

## Index

| Document | Agent | Rôle |
|---|---|---|
| [`brief.md`](brief.md) | Analyst | Problème, personas, périmètre MVP, risques |
| [`prd.md`](prd.md) | PM | 44 exigences fonctionnelles, 15 non fonctionnelles, 9 épics, stories |
| [`architecture.md`](architecture.md) | Architect | Modules, modèle, compilation, VM, réseau, décisions |
| [`architecture/tech-stack.md`](architecture/tech-stack.md) | Architect | Versions, bibliothèques autorisées, **table Mojang/Yarn** |
| [`architecture/coding-standards.md`](architecture/coding-standards.md) | Architect | Règles absolues du projet |
| [`architecture/source-tree.md`](architecture/source-tree.md) | Architect | Où placer chaque classe |
| [`ux-ui-spec.md`](ux-ui-spec.md) | UX Expert | Éditeur : disposition, interactions, raccourcis, thème, accessibilité |
| [`bscript-spec.md`](bscript-spec.md) | Architect | Grammaire du langage généré et correspondance avec le graphe |
| [`extension-api.md`](extension-api.md) | Architect | **Contrat d'intégration pour les mods tiers** |
| [`stories/`](stories/) | SM | Stories prêtes pour l'agent Dev |

Configuration : [`../.bmad-core/core-config.yaml`](../.bmad-core/core-config.yaml)

## Le produit en trois phrases

Blueprint est un mod Fabric 1.21.11 qui apporte en jeu un **éditeur de logique par nœuds** :
on pose des nœuds, on les relie, on déclare des variables typées, et le graphe s'exécute
côté serveur sur des événements du monde. Tout graphe se **compile en un script texte
lisible (BScript)** et tout BScript se re-parse en graphe. Les **autres mods déclarent
leurs propres nœuds** via un entrypoint Fabric, une annotation, ou un simple JSON de
datapack — sans dépendance dure et sans casser les graphes existants quand ils sont retirés.

## Cycle BMAD

```
brief.md ──► prd.md ──► architecture.md + ux-ui-spec.md
                                │
                                ▼
                      SM : rédige stories/<épic>.<n>.md
                                │
                                ▼
                      Dev : implémente (charge les 3 shards architecture/)
                                │
                                ▼
                      QA : remplit la section QA Results de la story
```

## État

| Épic | Titre | Stories rédigées | Statut |
|---|---|---|---|
| 1 | Fondations et modèle de graphe | 1.1 | Ready for Review — gate CONCERNS (VERIFY-001 : vérif `runClient` par l'utilisateur) |
| 1 | (suite) | 1.2 | Done (gate CONCERNS, tout soldé depuis) |
| 1 | (suite) | 1.3 | Done (gate PASS) |
| 1 | (suite) | 1.4 | Done (gate PASS) |
| 1 | (suite) | 1.5 | Ready for Review — gate CONCERNS (TEST-002 soldé par la 1.6 ; reste VERIFY-002) |
| 1 | (suite) | 1.6 | Draft (spike gametest — trace la dette de test en jeu) |
| 2 | Registre de nœuds et API d'extension | 2.1 → 2.5 | **Complet** — 5 gates PASS |
| 3 | Compilateur et VM | 3.1+3.2+3.3 (groupées) | **Ready for Review** — des graphes s'exécutent (IR, compilateur 26 ms/1000 nœuds, VM à fuel) |
| 3 | (suite) | 3.4, 3.5 | À rédiger (suspension persistée, ordonnanceur) |
| 4–9 | — | — | Spécifiés dans le PRD, à découper par le SM |

**Prochaine action :** QA du jalon 3.1-3.3 ; puis 3.4 (suspension persistée — le « wait qui survit au redémarrage ») et 3.5 (ordonnanceur). Session en jeu toujours en attente (VERIFY-001/002/003).

## Ordre de lecture recommandé

1. `brief.md` — pourquoi
2. `prd.md` §2 — quoi (exigences)
3. `architecture.md` §2 — comment (vue d'ensemble)
4. `extension-api.md` §0 — l'intégration tierce en une minute
5. Le reste selon le rôle
