package fr.blueprint.core;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.api.node.Permission;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Le blueprint de démonstration (story 4.4a) : la vitrine du runtime tant que
 * l'éditeur n'existe pas. Deux scénarios dans un seul graphe :
 *
 * <ul>
 * <li><b>Accueil</b> : un joueur se connecte → attendre 2 s → message de bienvenue.
 *     Prouve : événement, charge utile (joueur), suspension, reprise.</li>
 * <li><b>Ping/pong</b> : un message de chat contenant « ping » → répondre « pong ! ».
 *     Prouve : chaîne de purs (contains), branchement, données d'événement.</li>
 * </ul>
 */
public final class DemoBlueprint {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("blueprint", "demo");

    private DemoBlueprint() {
    }

    public static Blueprint build(NodeTypeLookup lookup) {
        Blueprint bp = new Blueprint(ID, new BlueprintMeta(
                "Blueprint", "Démo : accueil à la connexion + ping/pong sur le chat",
                "1.0.0", Permission.GAMEPLAY));

        // --- Scénario 1 : accueil ---
        UUID join = add(bp, lookup, "join", StandardEvents.PLAYER_JOIN.id(), -400, -200);
        UUID wait = add(bp, lookup, "wait", node("flow/wait"), -150, -200);
        UUID welcome = add(bp, lookup, "welcome", node("player/send_message"), 100, -200);
        apply(bp, lookup, new EditOperation.SetLiteral(wait, "ticks", LiteralValue.of(PinTypes.INT, 40)));
        apply(bp, lookup, new EditOperation.SetLiteral(welcome, "text", LiteralValue.of(PinTypes.STRING,
                "Bienvenue ! Ce message vient d'un blueprint (join → wait 2s → message).")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(join, "exec_out", wait, "exec_in")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(wait, "exec_out", welcome, "exec_in")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(join, "player", welcome, "player")));

        // --- Scénario 2 : ping/pong ---
        UUID chat = add(bp, lookup, "chat", StandardEvents.PLAYER_CHAT.id(), -400, 100);
        UUID contains = add(bp, lookup, "contains", node("string/contains"), -150, 180);
        UUID branch = add(bp, lookup, "branch", node("flow/branch"), 100, 100);
        UUID pong = add(bp, lookup, "pong", node("player/send_message"), 350, 100);
        apply(bp, lookup, new EditOperation.SetLiteral(contains, "search", LiteralValue.of(PinTypes.STRING, "ping")));
        apply(bp, lookup, new EditOperation.SetLiteral(pong, "text", LiteralValue.of(PinTypes.STRING, "pong !")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(chat, "exec_out", branch, "exec_in")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(chat, "message", contains, "value")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(contains, "result", branch, "condition")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(branch, "true", pong, "exec_in")));
        apply(bp, lookup, new EditOperation.AddLink(new Link(chat, "player", pong, "player")));

        return bp;
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static UUID add(Blueprint bp, NodeTypeLookup lookup, String seed,
                            Identifier type, double x, double y) {
        UUID uuid = UUID.nameUUIDFromBytes(("demo-" + seed)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, lookup, new EditOperation.AddNode(uuid, type, new Vec2d(x, y)));
        return uuid;
    }

    private static void apply(Blueprint bp, NodeTypeLookup lookup, EditOperation op) {
        EditOperation.Result result = op.apply(bp, lookup);
        if (!result.applied()) {
            throw new IllegalStateException("Démo incohérente : " + result.refusal());
        }
    }
}
