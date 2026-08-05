package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Barres de boss (batch 7).
 *
 * <p>C'est le seul affichage persistant du jeu : un titre disparaît, une barre reste
 * tant qu'on ne la retire pas. D'où son intérêt — un compte à rebours, un objectif de
 * mini-jeu — et d'où son <b>danger</b> : contrairement à tous les autres nœuds, elle
 * laisse un état vivant derrière elle.
 *
 * <p>Trois gardes en conséquence :
 * <ul>
 *   <li>Les barres sont nommées par le graphe : réutiliser le même nom RÉUTILISE la
 *       barre au lieu d'en empiler une nouvelle à chaque passage. Sans cela, une barre
 *       posée dans une boucle en créerait soixante par seconde.</li>
 *   <li>Leur nombre est plafonné : au-delà, la création est refusée avec une faute
 *       plutôt que d'emplir l'écran et la mémoire.</li>
 *   <li>{@link #clear()} vide tout à l'arrêt du serveur — une barre qui survit à un
 *       rechargement n'appartiendrait plus à personne.</li>
 * </ul>
 */
public final class BossBarNodes {

    /** Nombre maximal de barres vivantes, toutes origines confondues. */
    public static final int MAX_BARS = 32;

    /**
     * Les barres vivantes, par nom. {@code LinkedHashMap} : l'ordre de création est
     * stable, donc la liste affichée au joueur ne se réorganise pas toute seule.
     */
    private static final Map<String, ServerBossEvent> BARS = new LinkedHashMap<>();

    private BossBarNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /** Retire toutes les barres — arrêt du serveur, ou rechargement des blueprints. */
    public static synchronized void clear() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    /** Le nombre de barres vivantes — pour les tests et le diagnostic. */
    public static synchronized int liveBars() {
        return BARS.size();
    }

    public static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("player/bossbar_show")).fuelCost(5)
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("bar", PinTypes.STRING, "principale")
                .in("title", PinTypes.STRING, "")
                .in("progress", PinTypes.DOUBLE, 1.0)
                .in("color", PinTypes.STRING, "white")
                .action(ctx -> {
                    if (!(ctx.in("player") instanceof ServerPlayer player)) {
                        return;
                    }
                    ServerBossEvent bar = obtain(name(ctx.in("bar")), colorOf(ctx.in("color")));
                    if (bar == null) {
                        ctx.fail(Component.translatable("blueprint.fault.bossbar_limit",
                                MAX_BARS));
                        return;
                    }
                    bar.setName(Component.literal(str(ctx.in("title"))));
                    bar.setColor(colorOf(ctx.in("color")));
                    // Une progression hors [0,1] fait disparaître la barre côté client
                    // sans un mot : la ramener vaut mieux qu'un affichage fantôme.
                    bar.setProgress((float) Math.clamp(ctx.<Double>in("progress"), 0.0, 1.0));
                    bar.addPlayer(player);
                })
                .build());

        r.register(NodeType.builder(id("player/bossbar_hide")).fuelCost(5)
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("bar", PinTypes.STRING, "principale")
                .action(ctx -> {
                    if (ctx.in("player") instanceof ServerPlayer player) {
                        removePlayer(name(ctx.in("bar")), player);
                    }
                })
                .build());

        /* Retirer la barre pour TOUT LE MONDE, et la libérer. Sans ce nœud, une barre
         * de mini-jeu survivrait à la fin de la partie jusqu'au prochain arrêt. */
        r.register(NodeType.builder(id("world/bossbar_remove")).fuelCost(5)
                .category(NodeCategories.WORLD_STATE).exec().permission(Permission.GAMEPLAY)
                .in("bar", PinTypes.STRING, "principale")
                .action(ctx -> remove(name(ctx.in("bar"))))
                .build());
    }

    // ------------------------------------------------------------------ outillage

    /**
     * La barre de ce nom, créée au besoin ; null si le plafond est atteint.
     *
     * <p>Visible du paquet pour le test : réutilisation par nom et plafond sont la
     * seule logique qui peut fuir, et elle ne doit pas exiger un serveur pour être
     * vérifiée.
     */
    static synchronized ServerBossEvent obtain(String name, BossEvent.BossBarColor color) {
        ServerBossEvent existing = BARS.get(name);
        if (existing != null) {
            return existing;
        }
        if (BARS.size() >= MAX_BARS) {
            return null;
        }
        ServerBossEvent bar = new ServerBossEvent(Component.literal(name), color,
                BossEvent.BossBarOverlay.PROGRESS);
        BARS.put(name, bar);
        return bar;
    }

    private static synchronized void removePlayer(String name, ServerPlayer player) {
        ServerBossEvent bar = BARS.get(name);
        if (bar != null) {
            bar.removePlayer(player);
            // Une barre que plus personne ne voit n'a plus de raison d'occuper une
            // place du plafond : sans ce nettoyage, trente-deux mini-jeux terminés
            // interdiraient le trente-troisième.
            if (bar.getPlayers().isEmpty()) {
                BARS.remove(name);
            }
        }
    }

    static synchronized void remove(String name) {
        ServerBossEvent bar = BARS.remove(name);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** Une couleur inconnue vaut blanc : ce n'est pas une faute, seulement un défaut. */
    static BossEvent.BossBarColor colorOf(Object pin) {
        String raw = str(pin).strip().toUpperCase(Locale.ROOT);
        for (BossEvent.BossBarColor color : BossEvent.BossBarColor.values()) {
            if (color.name().equals(raw)) {
                return color;
            }
        }
        return BossEvent.BossBarColor.WHITE;
    }

    static String name(Object pin) {
        String raw = str(pin).strip();
        return raw.isEmpty() ? "principale" : raw;
    }

    private static String str(Object pin) {
        return pin instanceof String s ? s : "";
    }
}
