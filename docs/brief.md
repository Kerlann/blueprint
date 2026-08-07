# Project Brief — Blueprint

**Agent BMAD :** Analyst (Mary)
**Statut :** Approuvé — entrée du PRD
**Date :** 2026-08-02
**Version :** 1.0

---

## 1. Résumé exécutif

**Blueprint** est un mod Fabric pour Minecraft 1.21.11 qui apporte en jeu un
**éditeur de logique par nœuds** inspiré des Blueprints d'Unreal Engine : on pose
des nœuds, on tire des liens entre eux, on déclare des variables typées, et le
graphe s'exécute côté serveur sur des événements du monde (joueur qui clique un
bloc, tick, mort d'une entité, commande…).

Deux différenciateurs :

1. **Graphe ↔ script bidirectionnel.** Tout graphe se compile en un langage texte
   lisible (**BScript**) et tout BScript se re-parse en graphe. On peut donc
   versionner, diff-er, copier-coller et écrire à la main la logique visuelle.
2. **Système d'extension de première classe.** Les autres mods déclarent leurs
   propres nœuds, types de pins et événements via une API stable, une annotation,
   ou de simples fichiers JSON de datapack — sans dépendre du code interne de
   Blueprint.

## 2. Problème

Créer de la logique custom dans Minecraft impose aujourd'hui de choisir entre :

| Approche | Limite |
|---|---|
| Command blocks / datapacks | Syntaxe hostile, pas de types, pas de débogage, illisible au-delà de ~50 lignes |
| Redstone | Ne modélise pas des données (entités, items, texte) |
| Écrire un mod Java | Barrière d'entrée énorme, cycle recompile/relance |
| Mods de script (KubeJS…) | Puissants mais textuels : rebutent les non-programmeurs, aucune découvrabilité des API |

Et côté **auteurs de mods** : chaque mod qui veut exposer « du scriptable » ré-invente
son propre système. Il n'existe pas de surface commune où un mod déclare
« voici mes actions et mes événements » et où le joueur les câble avec ceux d'un autre mod.

## 3. Solution proposée

Un mod unique qui fournit :

- un **modèle de graphe** typé (nœuds, pins exec + data, liens, variables scopées) ;
- un **compilateur** graphe → IR → VM déterministe, avec budget d'instructions
  (impossible de figer le serveur avec une boucle infinie) et exécution
  **suspendable** (nœud `wait`, reprise après redémarrage du monde) ;
- un **éditeur graphique in-game** avec palette contextuelle, recherche floue,
  débogage live (surlignage du flux, valeurs des pins en survol) ;
- un **générateur/parseur BScript** (vue texte synchronisée avec le graphe) ;
- une **API d'extension** en trois niveaux — builder Java, annotation `@BlueprintNode`,
  définition JSON en datapack — plus un mécanisme de **nœuds fantômes** garantissant
  qu'un blueprint ne se casse pas quand un mod tiers est retiré.

## 4. Utilisateurs cibles

| Persona | Besoin | Attente clé |
|---|---|---|
| **Léa — map maker** (usage principal) | Scripter une aventure sans écrire de code | Palette découvrable, retour d'erreur clair, tout en jeu |
| **Karim — admin de serveur** | Automatiser règles et events, auditer ce que font les joueurs | Permissions, budget CPU, logs, import/export fichier |
| **Ana — développeuse de mod tiers** (usage secondaire, stratégique) | Exposer ses mécaniques aux joueurs sans écrire de GUI | API stable, ~10 lignes par nœud, zéro dépendance dure |
| **Tom — modpack maker** | Coller deux mods entre eux | Nœuds de mods différents dans un même graphe, blueprints livrables en datapack |

## 5. Périmètre MVP

**Inclus :**
- Modèle de graphe + validation + persistance (NBT gzip, sauvegarde du monde).
- Compilateur + VM avec budget de fuel et nœud `wait`.
- ~80 nœuds standard (flux, maths, logique, chaînes, monde, entité, item, joueur, texte).
  *(Cible du MVP. La v1.0 en a livré **236**, dont 34 pour les interfaces graphiques et 26
  événements — deux domaines nés après ce brief. Le chiffre ci-dessus est conservé tel
  quel : c'est ce qui était visé, et le réécrire ferait mentir la trace.)*
- Variables 4 scopes : `local`, `graph`, `world`, `player`.
- 10 événements déclencheurs de base. *(Livré : 26.)*
- Éditeur client complet (pan/zoom, palette contextuelle, liens, panneau variables, erreurs).
- Génération BScript + import BScript.
- API d'extension : builder + annotation + JSON datapack, avec synchro du registre au login.
- Permissions par nœud et gating serveur.

**Exclu du MVP (backlog) :**
- Édition collaborative temps réel à plusieurs curseurs (patchs op-based prévus dans l'archi, UI non livrée).
- Fonctions/macros définies par l'utilisateur (l'IR les supporte, l'UI arrive en v1.1).
- Navigateur de blueprints communautaires en ligne.
- Port Forge/NeoForge (l'archi isole les couches pour le rendre possible).

## 6. Objectifs et mesures

| Objectif | Métrique | Cible v1.0 |
|---|---|---|
| Accessibilité | Temps pour qu'un débutant produise un blueprint fonctionnel de 5 nœuds | < 10 min sans documentation |
| Performance éditeur | FPS avec 500 nœuds à l'écran | ≥ 60 fps |
| Performance runtime | Coût moyen par blueprint actif | ≤ 0,5 ms/tick |
| Sûreté | Blueprint capable de figer un serveur | 0 (budget de fuel) |
| Extensibilité | Effort pour un mod tiers d'ajouter un nœud | ≤ 10 lignes, dépendance douce |
| Robustesse | Blueprint corrompu par le retrait d'un mod | 0 (nœuds fantômes) |

## 7. Contraintes et hypothèses

- **Plateforme :** Fabric Loader 0.18.2, Fabric API 0.139.4+1.21.11, Minecraft 1.21.11, Java 21, **mappings officiels Mojang** (donc `Identifier`, `CompoundTag`, `GuiGraphics`, `Level`, `SavedData`…).
- **Exécution serveur uniquement.** Le client est un éditeur ; il n'exécute jamais un graphe reçu. Non négociable (sécurité).
- **Pas de bibliothèque GUI externe.** ACsGuis est Forge 1.12 uniquement ; l'éditeur est rendu maison sur `Screen`/`GuiGraphics`, avec un thème JSON à jetons inspiré de CSS pour rester stylable.
- **Aucune dépendance dure** à un mod tiers. Toute intégration passe par des entrypoints et du code conditionnel.
- Solo et multijoueur dédié doivent se comporter identiquement (le solo est un serveur intégré).

## 8. Risques

| Risque | Impact | Mitigation |
|---|---|---|
| Sécurité : un joueur non-op scripte un exploit | Critique | Niveaux de permission par nœud, plafond par blueprint, config serveur, audit log |
| Perf : graphes lourds au tick | Élevé | Fuel par tick, profileur intégré, désactivation auto d'un blueprint qui dépasse |
| Complexité de l'éditeur (rendu maison) | Élevé | Épic dédié, prototype canvas dès la story 5.1, budget de perf mesuré en CI |
| Dérive de l'API publique | Moyen | `fr.blueprint.api` figé, semver, `@ApiStatus`, tests de compatibilité binaire |
| Round-trip BScript imparfait (perte de mise en page) | Moyen | Métadonnées de position en commentaires structurés + auto-layout déterministe |

## 9. Livrables du dossier BMAD

| Document | Rôle |
|---|---|
| `docs/brief.md` | Ce document (Analyst) |
| `docs/prd.md` | Exigences FR/NFR, 9 épics, stories (PM) |
| `docs/architecture.md` | Architecture technique (Architect) |
| `docs/architecture/*.md` | Shards chargés par l'agent Dev |
| `docs/ux-ui-spec.md` | Spécification de l'éditeur (UX Expert) |
| `docs/bscript-spec.md` | Grammaire et sémantique du langage généré |
| `docs/extension-api.md` | Contrat d'intégration pour les mods tiers |
| `docs/stories/*.md` | Stories prêtes pour l'agent Dev (SM) |
