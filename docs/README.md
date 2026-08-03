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
| 1 | Fondations et modèle de graphe | 1.1 → 1.5 | **Complet** — 5 Done, gates PASS (session en jeu du 2026-08-02 : VERIFY-001/002/003 clos) |
| 1 | (spike) | 1.6 | Draft (gametest — automatisera ce que la session manuelle vérifie) |
| 2 | Registre de nœuds et API d'extension | 2.1 → 2.5 | **Complet** — 5 gates PASS |
| 3 | Compilateur et VM | 3.1+3.2+3.3, 3.4+3.5 | **Complet** — 2 gates PASS (3 corrections en review dont un high sémantique) |
| 7 | Événements et bibliothèque | 7.1a+7.2+7.6 (groupées) | **Complet** — gate PASS, démo ping/pong vérifiée en jeu (VERIFY-004) |
| 7 | (restes) | 7.1b, 7.3-7.5, 7.7 | À rédiger (flux structuré + frames VM ; nœuds monde en gametest ; commande) |
| 4 | Démo, export/import | 4.4a | **Complet** — gate PASS |
| 4 | BScript v1 | 4.1+4.2+4.3 (groupées) | **Complet** — gate PASS (4 corrections en review dont un high de fidélité) ; round-trip exact, `.bp` = texte |
| 6 | Persistance monde | 6.1 | **Complet** — gate PASS (VERIFY-005 : redémarrage à confirmer en jeu) |
| 5 | **Éditeur visuel — COMPLET** | 5.1 → 5.11 (15 stories) | **15 gates PASS** — 1 high (crash Ctrl+S) + 6 medium corrigés en review, 117 tests client ; l'éditeur fait tout le backlog UE (littéraux+sélecteurs, undo, Ctrl+S réel, diagnostics cliquables, variables, copier/coller BScript, détails, palette complète, vue script, commentaires/minimap/thème) |
| 4 (4.2b), 6 (reste), 8, 9 | — | — | Spécifiés dans le PRD, à découper par le SM |

**Feuille de route éditeur (ordre recommandé)** :
1. **5.2b** littéraux inline (éditer les valeurs sur le nœud) → 2. **5.6a** annuler/rétablir (avant les grosses features, tout naît annulable) → 3. **5.9** éditer/enregistrer/tester un VRAI blueprint en solo (`Ctrl+S`, la story qui rend l'éditeur utile) → 4. **5.6b** barre d'outils + compilation à la volée + diagnostics cliquables → 5. **5.5** panneau des variables + nœuds var/get-set (⚠ touche `core`) → 6. **5.8** copier/coller/dupliquer via BScript (⚠ touche `core/script`) → 7. **5.10** panneau de détails → 8. **5.4b** palette récents/favoris/catégories/Espace → 9. **5.2c** sélecteurs riches (item, bloc, position) → 10. **5.11** vue script → 11. **5.7** confort (commentaires, alignement, minimap, thème JSON).

**Prochaine action :** session en jeu : VERIFY-005 (`/blueprint demo` → redémarrage du monde → « Persistance : … » au log et ping sans réimport) + regarder le `.bp` texte exporté ; puis 4.2b (sucre BScript), 7.1b, ou 6.2 (sync registres — coordonner avec la session épic 5).

## Ordre de lecture recommandé

1. `brief.md` — pourquoi
2. `prd.md` §2 — quoi (exigences)
3. `architecture.md` §2 — comment (vue d'ensemble)
4. `extension-api.md` §0 — l'intégration tierce en une minute
5. Le reste selon le rôle
