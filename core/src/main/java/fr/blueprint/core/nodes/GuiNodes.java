package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.core.graph.screen.ScreenText;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import fr.blueprint.core.net.ServerBlueprintNet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;



/**
 * Les nœuds d'écran (story 10.4) : ouvrir, fermer, et les cinq modificateurs.
 *
 * <p>Chaque modificateur existe en <b>deux variantes</b> : sur un joueur, et sur tous
 * ceux qui regardent le même écran. Sans la seconde, mettre à jour un tableau des scores
 * chez vingt joueurs demanderait une boucle {@code for_each} et vingt appels — un coût
 * de rédaction ET d'exécution pour quelque chose que le serveur sait déjà faire d'un
 * seul parcours de sa table.
 *
 * <p>Tous désignent l'élément par son <b>nom</b> (FR47). Un index se décalerait au
 * premier ajout dans le concepteur, et le graphe modifierait le mauvais bouton sans la
 * moindre erreur.
 */
public final class GuiNodes {

    private GuiNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        registerClickedEvent(r);
        registerOpenClose(r);

        // Les cinq modificateurs, chacun en deux variantes. La fabrique évite dix
        // blocs quasi identiques — c'est là que les divergences se glissent.
        modifier(r, "set_text", "text", PinTypes.STRING, "",
                (screen, element, ctx) -> ScreenUpdate.text(screen, element,
                        ScreenText.literal(ctx.in("text"))));
        modifier(r, "set_text_key", "key", PinTypes.STRING, "",
                (screen, element, ctx) -> ScreenUpdate.text(screen, element,
                        ScreenText.key(ctx.in("key"))));
        // STRING et non RESOURCE_LOCATION : il faut pouvoir ENLEVER une texture, et un
        // identifiant n'a pas de valeur « aucune ». La chaîne vide l'exprime ; un
        // identifiant illisible n'enlève rien plutôt que de faire tomber le nœud.
        modifier(r, "set_texture", "texture", PinTypes.STRING, "",
                (screen, element, ctx) -> ScreenUpdate.texture(screen, element,
                        textureOf(ctx.in("texture"))));
        modifier(r, "set_visible", "visible", PinTypes.BOOL, true,
                (screen, element, ctx) -> ScreenUpdate.visible(screen, element, ctx.in("visible")));
        modifier(r, "set_enabled", "enabled", PinTypes.BOOL, true,
                (screen, element, ctx) -> ScreenUpdate.enabled(screen, element, ctx.in("enabled")));
        modifier(r, "set_progress", "value", PinTypes.DOUBLE, 0.0,
                (screen, element, ctx) -> ScreenUpdate.progress(screen, element, ctx.in("value")));
    }

    /**
     * Le nœud d'événement du clic, enregistré <b>à la main</b> — la synthèse ne sait
     * pas produire un nœud d'événement avec une ENTRÉE.
     *
     * <p>C'est le troisième cas exact après {@code command} (7.7) et {@code signal}
     * (batch 1), et pour la même raison : le littéral « element » déclare ce qu'on
     * écoute. Sans lui, chaque clic de chaque écran réveillerait chaque écouteur, et
     * l'auteur devrait comparer le nom à la main dans tous ses graphes.
     */
    private static void registerClickedEvent(NodeRegistry r) {
        var event = fr.blueprint.core.event.StandardEvents.GUI_ELEMENT_CLICKED;
        r.register(NodeType.builder(event.id())
                .category(NodeCategories.GUI)
                .entryPoint()
                .titleKey(event.titleKey())
                .execOut("exec_out")
                .in("element", PinTypes.STRING, "")
                .out("player", PinTypes.PLAYER)
                .out("screen", PinTypes.STRING)
                .out("element", PinTypes.STRING)
                .action(ctx -> relayOutputs(ctx, event))
                .build());

        // Ouverture et fermeture : enregistrés à la main eux aussi, pour la CATÉGORIE.
        // Synthétisés, ils atterriraient dans « event/player » — qui portait déjà
        // treize nœuds — au lieu de rejoindre les autres nœuds d'écran, là où l'auteur
        // les cherchera.
        for (var lifecycle : java.util.List.of(
                fr.blueprint.core.event.StandardEvents.GUI_OPENED,
                fr.blueprint.core.event.StandardEvents.GUI_CLOSED)) {
            r.register(NodeType.builder(lifecycle.id())
                    .category(NodeCategories.GUI)
                    .entryPoint()
                    .titleKey(lifecycle.titleKey())
                    .execOut("exec_out")
                    .out("player", PinTypes.PLAYER)
                    .out("screen", PinTypes.STRING)
                    .action(ctx -> relayOutputs(ctx, lifecycle))
                    .build());
        }
    }

    /** Recopie les sorties du déclencheur sur les pins du nœud d'événement. */
    private static void relayOutputs(fr.blueprint.api.node.NodeContext ctx,
                                     fr.blueprint.api.event.EventType event) {
        for (var out : event.outputs()) {
            Object value = ctx.trigger().output(out.name());
            if (value != null) {
                ctx.out(out.name(), value);
            }
        }
    }

    private static void registerOpenClose(NodeRegistry r) {
        // GAMEPLAY, et non SAFE : ouvrir un écran chez QUELQU'UN D'AUTRE est un acte.
        // Un blueprint qui rouvrirait un menu plein écran en boucle sortirait le joueur
        // du jeu — d'où la permission, la cadence bornée côté serveur, et le fait
        // qu'Échap ferme toujours (AC5b).
        r.register(NodeType.builder(id("gui/open"))
                .category(NodeCategories.GUI).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("screen", PinTypes.STRING, "")
                .action(ctx -> {
                    if (!(ctx.in("player") instanceof ServerPlayer player)) {
                        ctx.fail(Component.translatable("blueprint.fault.gui_no_player"));
                        return;
                    }
                    String screen = String.valueOf(ctx.<Object>in("screen"));
                    if (!ServerBlueprintNet.openScreen(player, ctx.blueprint().id(), screen)) {
                        // Nommer l'écran manquant : sans lui, l'auteur cherche une
                        // faute de frappe entre seize écrans sans savoir lequel.
                        ctx.fail(Component.translatable("blueprint.fault.gui_no_screen", screen));
                    }
                })
                .build());

        // ------------------------------------------------------------ HUD (10.9)
        // Un HUD n'est pas un écran qu'on OUVRE : il s'affiche par-dessus le jeu sans
        // rien capter, et plusieurs coexistent. D'où des nœuds distincts : « ouvrir »
        // et « afficher » ne veulent pas dire la même chose, et les confondre a
        // justement produit un HUD qui figeait le joueur.
        r.register(NodeType.builder(id("hud/show"))
                .category(NodeCategories.GUI).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("screen", PinTypes.STRING, "")
                .action(ctx -> {
                    if (!(ctx.in("player") instanceof ServerPlayer player)) {
                        ctx.fail(Component.translatable("blueprint.fault.gui_no_player"));
                        return;
                    }
                    String screen = String.valueOf(ctx.<Object>in("screen"));
                    if (!ServerBlueprintNet.showHud(player, ctx.blueprint().id(), screen)) {
                        ctx.fail(Component.translatable("blueprint.fault.gui_not_hud", screen));
                    }
                })
                .build());

        // SAFE : retirer un affichage ne prend rien au joueur.
        r.register(NodeType.builder(id("hud/hide"))
                .category(NodeCategories.GUI).exec().permission(Permission.SAFE)
                .in("player", PinTypes.PLAYER)
                .in("screen", PinTypes.STRING, "")
                .action(ctx -> {
                    if (ctx.in("player") instanceof ServerPlayer player) {
                        ServerBlueprintNet.hideHud(player,
                                String.valueOf(ctx.<Object>in("screen")));
                    }
                })
                .build());

        r.register(NodeType.builder(id("hud/hide_all"))
                .category(NodeCategories.GUI).exec().permission(Permission.SAFE)
                .in("player", PinTypes.PLAYER)
                .action(ctx -> {
                    if (ctx.in("player") instanceof ServerPlayer player) {
                        ServerBlueprintNet.hideAllHuds(player);
                    }
                })
                .build());

        // SAFE : fermer ne prend rien au joueur, et lui rend même la main.
        r.register(NodeType.builder(id("gui/close"))
                .category(NodeCategories.GUI).exec().permission(Permission.SAFE)
                .in("player", PinTypes.PLAYER)
                .action(ctx -> {
                    if (ctx.in("player") instanceof ServerPlayer player) {
                        ServerBlueprintNet.closeScreen(player);
                    }
                })
                .build());
    }

    /** Construit un modificateur et sa variante « à tous les spectateurs ». */
    private static void modifier(NodeRegistry r, String name, String pin,
                                 fr.blueprint.api.pin.PinType type, Object defaultValue,
                                 Builder build) {
        // « screen » vide = l'écran modal ouvert. Depuis la 10.9, plusieurs surfaces
        // coexistent (un modal et des HUD) : une modification doit donc dire LAQUELLE
        // elle vise, sinon deux écrans portant un élément « or » se disputeraient.
        r.register(NodeType.builder(id("gui/" + name))
                .category(NodeCategories.GUI_UPDATE).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("screen", PinTypes.STRING, "")
                .in("element", PinTypes.STRING, "")
                .in(pin, type, defaultValue)
                .action(ctx -> {
                    if (!(ctx.in("player") instanceof ServerPlayer player)) {
                        ctx.fail(Component.translatable("blueprint.fault.gui_no_player"));
                        return;
                    }
                    String element = String.valueOf(ctx.<Object>in("element"));
                    if (element.isBlank()) {
                        ctx.fail(Component.translatable("blueprint.fault.gui_no_element"));
                        return;
                    }
                    ServerBlueprintNet.queueUpdate(player, build.apply(
                            String.valueOf(ctx.<Object>in("screen")), element, ctx));
                })
                .build());

        r.register(NodeType.builder(id("gui/" + name + "_all"))
                .category(NodeCategories.GUI_UPDATE).exec().permission(Permission.GAMEPLAY)
                .in("screen", PinTypes.STRING, "")
                .in("element", PinTypes.STRING, "")
                .in(pin, type, defaultValue)
                .action(ctx -> {
                    String element = String.valueOf(ctx.<Object>in("element"));
                    if (element.isBlank()) {
                        ctx.fail(Component.translatable("blueprint.fault.gui_no_element"));
                        return;
                    }
                    String screen = String.valueOf(ctx.<Object>in("screen"));
                    ServerBlueprintNet.queueUpdateForAll(ctx.blueprint().id(), screen,
                            build.apply(screen, element, ctx));
                })
                .build());
    }

    /** Construit la modification à envoyer : l'écran visé, l'élément, et le contexte. */
    @FunctionalInterface
    private interface Builder {
        ScreenUpdate apply(String screen, String element, fr.blueprint.api.node.NodeContext ctx);
    }

    /** Un identifiant vide efface la texture ; un identifiant illisible ne fait rien. */
    private static @org.jetbrains.annotations.Nullable Identifier textureOf(Object raw) {
        if (raw instanceof Identifier identifier) {
            return identifier;
        }
        String text = String.valueOf(raw);
        return text.isBlank() ? null : Identifier.tryParse(text);
    }
}
