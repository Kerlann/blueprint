package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Scores et équipes (batch 7).
 *
 * <p>Le tableau des scores est la mémoire partagée de Minecraft : c'est là que vivent
 * les points d'un mini-jeu, et c'est ce que l'affichage latéral et les commandes
 * vanilla savent lire. Un blueprint pouvait stocker un score dans ses variables — mais
 * personne d'autre ne pouvait le voir, ni une commande, ni un autre mod, ni le joueur.
 *
 * <p>L'objectif est <b>créé s'il n'existe pas</b>. Fauter parce qu'un objectif manque
 * obligerait à lancer une commande avant chaque graphe, et un script qui se déploie
 * sur un serveur neuf doit fonctionner du premier coup.
 */
public final class ScoreboardNodes {

    /** Longueur maximale d'un nom d'objectif, comme la commande vanilla. */
    public static final int MAX_OBJECTIVE_NAME = 16;

    private ScoreboardNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("score/get")).fuelCost(5)
                .category(NodeCategories.SCOREBOARD).exec().permission(Permission.SAFE)
                .in("entity", PinTypes.ENTITY)
                .in("objective", PinTypes.STRING, "points")
                .out("score", PinTypes.INT)
                .out("exists", PinTypes.BOOL)
                .action(ctx -> {
                    var board = ctx.server().getScoreboard();
                    Objective objective = board.getObjective(name(ctx.in("objective")));
                    // Ici on NE crée pas : lire un objectif absent doit se dire, sinon
                    // une faute de frappe rend zéro et le graphe a l'air de marcher.
                    if (objective == null || !(ctx.in("entity") instanceof Entity entity)) {
                        ctx.out("exists", false);
                        ctx.out("score", 0);
                        return;
                    }
                    ctx.out("exists", true);
                    ctx.out("score", board.getOrCreatePlayerScore(entity, objective).get());
                })
                .build());

        r.register(NodeType.builder(id("score/set")).fuelCost(5)
                .category(NodeCategories.SCOREBOARD).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .in("objective", PinTypes.STRING, "points")
                .in("score", PinTypes.INT, 0)
                .action(ctx -> access(ctx, score -> {
                    int value = ctx.in("score");
                    score.set(value);
                    return value;
                }))
                .build());

        r.register(NodeType.builder(id("score/add")).fuelCost(5)
                .category(NodeCategories.SCOREBOARD).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .in("objective", PinTypes.STRING, "points")
                .in("amount", PinTypes.INT, 1)
                .out("score", PinTypes.INT)
                .action(ctx -> {
                    var result = access(ctx, score -> score.add(ctx.<Integer>in("amount")));
                    ctx.out("score", result);
                })
                .build());

        r.register(NodeType.builder(id("score/reset")).fuelCost(5)
                .category(NodeCategories.SCOREBOARD).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .in("objective", PinTypes.STRING, "points")
                .action(ctx -> access(ctx, score -> {
                    score.reset();
                    return 0;
                }))
                .build());

        /*
         * L'équipe d'une entité — en LECTURE seulement. Créer ou peupler une équipe
         * touche à l'affichage, aux collisions et au feu allié de tout le serveur :
         * c'est un pouvoir d'administration, pas de script, et la commande vanilla
         * reste le bon endroit pour l'exercer.
         */
        r.register(NodeType.builder(id("team/of")).fuelCost(3)
                .category(NodeCategories.SCOREBOARD).exec().permission(Permission.SAFE)
                .in("entity", PinTypes.ENTITY)
                .out("team", PinTypes.STRING)
                .out("in_team", PinTypes.BOOL)
                .action(ctx -> {
                    PlayerTeam team = ctx.in("entity") instanceof Entity entity
                            ? entity.getTeam() : null;
                    ctx.out("in_team", team != null);
                    ctx.out("team", team == null ? "" : team.getName());
                })
                .build());

        r.register(NodeType.builder(id("team/same")).fuelCost(3)
                .category(NodeCategories.SCOREBOARD).exec().permission(Permission.SAFE)
                .in("a", PinTypes.ENTITY)
                .in("b", PinTypes.ENTITY)
                .out("same", PinTypes.BOOL)
                .action(ctx -> {
                    PlayerTeam first = ctx.in("a") instanceof Entity e ? e.getTeam() : null;
                    PlayerTeam second = ctx.in("b") instanceof Entity e ? e.getTeam() : null;
                    // Deux entités SANS équipe ne sont pas « de la même équipe » : le
                    // feu allié d'un mini-jeu s'appuierait dessus pour tout autoriser.
                    ctx.out("same", first != null && first == second);
                })
                .build());
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Applique une opération au score, en créant l'objectif au besoin. Rend la valeur
     * après opération, ou zéro si l'entité manque.
     */
    private static int access(fr.blueprint.api.node.NodeContext ctx,
                              java.util.function.ToIntFunction
                                      <net.minecraft.world.scores.ScoreAccess> operation) {
        if (!(ctx.in("entity") instanceof Entity entity)) {
            return 0;
        }
        ServerScoreboard board = ctx.server().getScoreboard();
        String objectiveName = name(ctx.in("objective"));
        Objective objective = board.getObjective(objectiveName);
        if (objective == null) {
            objective = board.addObjective(objectiveName, ObjectiveCriteria.DUMMY,
                    Component.literal(objectiveName), ObjectiveCriteria.RenderType.INTEGER,
                    true, null);
        }
        return operation.applyAsInt(board.getOrCreatePlayerScore(entity, objective));
    }

    /**
     * Un nom d'objectif vide ou trop long casserait la commande vanilla qui le lira
     * ensuite : on le ramène plutôt que de laisser un objectif inutilisable derrière.
     */
    private static String name(Object pin) {
        String raw = pin instanceof String s ? s.strip() : "";
        if (raw.isEmpty()) {
            return "points";
        }
        return raw.length() <= MAX_OBJECTIVE_NAME ? raw : raw.substring(0, MAX_OBJECTIVE_NAME);
    }
}
