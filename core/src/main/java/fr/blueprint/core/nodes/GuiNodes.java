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
        registerRefresh(r);
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

        // Les éléments riches (10.8). Les mêmes deux variantes, par la même fabrique :
        // un tableau de scores se remplit chez vingt joueurs sans boucle for_each.
        modifier(r, NodeCategories.GUI_RICH, "set_lines", "lines", PinTypes.listOf(PinTypes.STRING), java.util.List.of(),
                (screen, element, ctx) -> ScreenUpdate.lines(screen, element,
                        linesOf(ctx.in("lines"))));
        modifier(r, NodeCategories.GUI_RICH, "set_item", "item", PinTypes.ITEMSTACK, null,
                (screen, element, ctx) -> itemUpdate(screen, element, ctx.in("item")));
        modifier(r, NodeCategories.GUI_RICH, "set_value", "value", PinTypes.DOUBLE, 0.0,
                (screen, element, ctx) -> ScreenUpdate.value(screen, element,
                        ctx.in("value"), ((Number) ctx.<Object>in("value")).doubleValue() != 0, ""));
        modifier(r, NodeCategories.GUI_RICH, "set_input", "text", PinTypes.STRING, "",
                (screen, element, ctx) -> ScreenUpdate.value(screen, element, 0, false,
                        String.valueOf(ctx.<Object>in("text"))));

        // L'apparence en cours de route (10.12). Le style NOMMÉ plutôt que neuf
        // couleurs : un nœud à neuf entrées serait impraticable, et rien n'y garantirait
        // que deux onglets « actifs » se ressemblent. Décrit une fois dans le concepteur,
        // il bascule ici d'un seul fil — c'est ce qui rend un menu à onglets possible
        // sans dupliquer les éléments.
        modifier(r, NodeCategories.GUI_LOOK, "set_style", "style", PinTypes.STRING, "",
                (screen, element, ctx) -> ScreenUpdate.style(screen, element,
                        String.valueOf(ctx.<Object>in("style"))));
        modifier(r, NodeCategories.GUI_LOOK, "set_tooltip", "text", PinTypes.STRING, "",
                (screen, element, ctx) -> ScreenUpdate.tooltip(screen, element,
                        ScreenText.literal(ctx.in("text"))));
        modifier(r, NodeCategories.GUI_LOOK, "set_tooltip_key", "key", PinTypes.STRING, "",
                (screen, element, ctx) -> ScreenUpdate.tooltip(screen, element,
                        ScreenText.key(ctx.in("key"))));
    }

    /** Une liste de n'importe quoi devient une liste de lignes : rien n'est jeté. */
    private static java.util.List<String> linesOf(Object raw) {
        if (!(raw instanceof java.util.List<?> list)) {
            return java.util.List.of();
        }
        java.util.List<String> out = new java.util.ArrayList<>(list.size());
        for (Object value : list) {
            // Un saut de ligne dans une entrée couperait la liste en deux au décodage :
            // il est remplacé plutôt que d'inventer un second séparateur.
            out.add(String.valueOf(value).replace('\n', ' '));
        }
        return out;
    }

    private static ScreenUpdate itemUpdate(String screen, String element, Object raw) {
        if (raw instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
            return ScreenUpdate.item(screen, element,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount());
        }
        // Un objet vide VIDE l'emplacement : c'est le seul moyen de le faire, et
        // l'inverse — ignorer — laisserait l'ancien objet affiché pour toujours.
        return ScreenUpdate.item(screen, element, null, 0);
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

        // Les événements d'éléments riches (10.8) : même raison EXACTEMENT que le clic.
        // Ils portent le nom écouté en littéral, donc la synthèse ne sait pas les
        // produire — et sans ce littéral, launchGuiEvent ne saurait pas qui réveiller,
        // si bien que chaque frappe dans un champ réveillerait tous les écouteurs de
        // tous les écrans du serveur.
        for (var rich : java.util.List.of(
                fr.blueprint.core.event.StandardEvents.GUI_LIST_CLICKED,
                fr.blueprint.core.event.StandardEvents.GUI_INPUT_CHANGED,
                fr.blueprint.core.event.StandardEvents.GUI_VALUE_CHANGED)) {
            var builder = NodeType.builder(rich.id())
                    .category(NodeCategories.GUI)
                    .entryPoint()
                    .titleKey(rich.titleKey())
                    .execOut("exec_out")
                    .in("element", PinTypes.STRING, "");
            for (var out : rich.outputs()) {
                builder = builder.out(out.name(), out.type());
            }
            var richEvent = rich;
            r.register(builder.action(ctx -> relayOutputs(ctx, richEvent)).build());
        }

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

    /**
     * {@code gui/refresh} — relit les liaisons de l'écran et n'envoie que ce qui change
     * (story 10.7).
     *
     * <p>Le rafraîchissement part quand le graphe le <b>demande</b>, jamais tout seul.
     * Trois façons de savoir qu'une valeur liée a changé étaient possibles : sonder
     * chaque tick — le serveur travaillerait vingt fois par seconde pour un écran qui ne
     * bouge pas de la partie ; instrumenter chaque écriture de variable — donc alourdir
     * le chemin chaud de <i>toute</i> exécution, y compris des graphes qui n'ouvrent
     * jamais d'écran ; ou demander. C'est la troisième : coût <b>nul</b> au repos, aucun
     * code ajouté dans la machine virtuelle, et l'auteur garde la main sur le moment.
     *
     * <p>La même logique que le débogueur et le profileur de l'épic 9 : ce qu'on ne fait
     * pas ne peut pas être lent.
     */
    private static void registerRefresh(NodeRegistry r) {
        r.register(NodeType.builder(id("gui/refresh"))
                .category(NodeCategories.GUI_BIND).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("screen", PinTypes.STRING, "")
                .out("sent", PinTypes.INT)
                .action(ctx -> {
                    if (!(ctx.in("player") instanceof ServerPlayer player)) {
                        ctx.fail(Component.translatable("blueprint.fault.gui_no_player"));
                        return;
                    }
                    ctx.out("sent", ServerBlueprintNet.refreshBindings(
                            player, ctx.blueprint().id(),
                            String.valueOf(ctx.<Object>in("screen"))));
                })
                .build());

        r.register(NodeType.builder(id("gui/refresh_all"))
                .category(NodeCategories.GUI_BIND).exec().permission(Permission.GAMEPLAY)
                .in("screen", PinTypes.STRING, "")
                .out("sent", PinTypes.INT)
                .action(ctx -> ctx.out("sent", ServerBlueprintNet.refreshBindingsForAll(
                        ctx.server(), ctx.blueprint().id(), String.valueOf(ctx.<Object>in("screen")))))
                .build());
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
        modifier(r, NodeCategories.GUI_UPDATE, name, pin, type, defaultValue, build);
    }

    private static void modifier(NodeRegistry r, fr.blueprint.api.node.NodeCategory category,
                                 String name, String pin,
                                 fr.blueprint.api.pin.PinType type, Object defaultValue,
                                 Builder build) {
        // Un défaut nul signifie « ce pin se CÂBLE, il ne se tape pas ». Un ItemStack
        // n'a pas d'écriture littérale, et lui en inventer une faisait tomber
        // l'enregistrement de tout le registre à l'initialisation.
        java.util.function.UnaryOperator<NodeType.Builder> withPin =
                builder -> defaultValue == null
                        ? builder.in(pin, type) : builder.in(pin, type, defaultValue);
        // « screen » vide = l'écran modal ouvert. Depuis la 10.9, plusieurs surfaces
        // coexistent (un modal et des HUD) : une modification doit donc dire LAQUELLE
        // elle vise, sinon deux écrans portant un élément « or » se disputeraient.
        r.register(withPin.apply(NodeType.builder(id("gui/" + name))
                .category(category).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("screen", PinTypes.STRING, "")
                .in("element", PinTypes.STRING, ""))
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

        r.register(withPin.apply(NodeType.builder(id("gui/" + name + "_all"))
                .category(category).exec().permission(Permission.GAMEPLAY)
                .in("screen", PinTypes.STRING, "")
                .in("element", PinTypes.STRING, ""))
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
