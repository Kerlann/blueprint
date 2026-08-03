package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Texte riche avancé (batch 7).
 *
 * <p>Trois nœuds existaient : littéral, coloré, concaténé. Un message ne pouvait donc
 * ni s'écrire en gras, ni porter une infobulle, ni se cliquer — alors que c'est
 * exactement ce qui distingue un message de serveur d'un message de joueur.
 *
 * <p><b>Sur le clic qui exécute une commande</b> : il s'exécute avec les permissions
 * de CELUI QUI CLIQUE. Un blueprint de faible permission qui pourrait en fabriquer un
 * ferait exécuter à un opérateur une commande qu'il n'a pas écrite. Le nœud
 * correspondant exige donc {@code ADMIN} — un auteur qui l'a déjà peut de toute façon
 * lancer des commandes. Suggérer et copier, eux, n'exécutent rien : {@code GAMEPLAY}.
 */
public final class RichTextNodes {

    private RichTextNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("text/styled"))
                .category(NodeCategories.TEXT).pure()
                .in("text", PinTypes.TEXT)
                .in("bold", PinTypes.BOOL, false)
                .in("italic", PinTypes.BOOL, false)
                .in("underlined", PinTypes.BOOL, false)
                .out("text", PinTypes.TEXT)
                .action(ctx -> ctx.out("text", copy(ctx.in("text")).withStyle(style -> style
                        .withBold(ctx.in("bold"))
                        .withItalic(ctx.in("italic"))
                        .withUnderlined(ctx.in("underlined")))))
                .build());

        r.register(NodeType.builder(id("text/hover"))
                .category(NodeCategories.TEXT).pure()
                .in("text", PinTypes.TEXT)
                .in("tooltip", PinTypes.TEXT)
                .out("text", PinTypes.TEXT)
                .action(ctx -> ctx.out("text", copy(ctx.in("text")).withStyle(style ->
                        style.withHoverEvent(new HoverEvent.ShowText(component(ctx.in("tooltip")))))))
                .build());

        r.register(NodeType.builder(id("text/click_suggest"))
                .category(NodeCategories.TEXT).pure().permission(Permission.GAMEPLAY)
                .in("text", PinTypes.TEXT)
                .in("command", PinTypes.STRING, "")
                .out("text", PinTypes.TEXT)
                .action(ctx -> ctx.out("text", copy(ctx.in("text")).withStyle(style ->
                        style.withClickEvent(new ClickEvent.SuggestCommand(str(ctx.in("command")))))))
                .build());

        r.register(NodeType.builder(id("text/click_copy"))
                .category(NodeCategories.TEXT).pure().permission(Permission.GAMEPLAY)
                .in("text", PinTypes.TEXT)
                .in("value", PinTypes.STRING, "")
                .out("text", PinTypes.TEXT)
                .action(ctx -> ctx.out("text", copy(ctx.in("text")).withStyle(style ->
                        style.withClickEvent(new ClickEvent.CopyToClipboard(str(ctx.in("value")))))))
                .build());

        // ADMIN : voir le commentaire de classe. Ce n'est pas une précaution de
        // principe — le clic s'exécute avec les droits de qui clique.
        r.register(NodeType.builder(id("text/click_command"))
                .category(NodeCategories.TEXT).pure().permission(Permission.ADMIN)
                .in("text", PinTypes.TEXT)
                .in("command", PinTypes.STRING, "")
                .out("text", PinTypes.TEXT)
                .action(ctx -> ctx.out("text", copy(ctx.in("text")).withStyle(style ->
                        style.withClickEvent(new ClickEvent.RunCommand(str(ctx.in("command")))))))
                .build());

        /*
         * Le texte TRADUIT : c'est la seule façon qu'un message s'affiche dans la
         * langue de chaque joueur. Un serveur international n'a pas d'autre moyen —
         * un littéral est figé dans la langue de celui qui a écrit le graphe.
         */
        r.register(NodeType.builder(id("text/translate"))
                .category(NodeCategories.TEXT).pure()
                .in("key", PinTypes.STRING, "")
                .in("arg", PinTypes.STRING, "")
                .out("text", PinTypes.TEXT)
                .action(ctx -> {
                    String arg = str(ctx.in("arg"));
                    // translatableEscape : un argument venu du chat ne doit pas
                    // pouvoir injecter de mise en forme dans le message final.
                    ctx.out("text", arg.isEmpty()
                            ? Component.translatable(str(ctx.in("key")))
                            : Component.translatableEscape(str(ctx.in("key")), arg));
                })
                .build());
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Une copie MUTABLE du texte d'entrée. Les {@code Component} de Minecraft sont
     * partagés ; appliquer un style en place changerait aussi le texte que d'autres
     * nœuds lisent — le piège de la mémoïsation des nœuds purs, déjà vu sur les listes.
     */
    private static MutableComponent copy(Object pin) {
        return component(pin).copy();
    }

    private static Component component(Object pin) {
        return pin instanceof Component c ? c : Component.empty();
    }

    private static String str(Object pin) {
        return pin instanceof String s ? s : "";
    }
}
