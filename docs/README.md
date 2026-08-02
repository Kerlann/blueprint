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
| 1 | Fondations et modèle de graphe | 1.1 | Ready for Review (reste : vérif `runClient` + review QA) |
| 1 | (suite) | 1.2 | **Done** (gate QA : CONCERNS, DOC-001 traité par correct-course) |
| 1 | (suite) | 1.3 | Draft enrichi, prêt à développer (inclut reprises QA) |
| 1 | (suite) | 1.4, 1.5 | À rédiger par le SM |
| 2–9 | — | — | Spécifiés dans le PRD, à découper par le SM |

**Prochaine action :** développer la story 1.3 (`/BMad:agents:dev 1.3`).

## Ordre de lecture recommandé

1. `brief.md` — pourquoi
2. `prd.md` §2 — quoi (exigences)
3. `architecture.md` §2 — comment (vue d'ensemble)
4. `extension-api.md` §0 — l'intégration tierce en une minute
5. Le reste selon le rôle
