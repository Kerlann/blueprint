package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Texte riche avancé (batch 7) : mise en forme, infobulle, clic, traduction. */
class RichTextNodesTest {

    private static final PluginLoader.LoadedRegistries REGISTRIES =
            PluginLoader.load(List.of(), true);

    private static NodeType node(String path) {
        NodeType type = REGISTRIES.nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", path)).orElse(null);
        assertNotNull(type, "nœud absent : " + path);
        return type;
    }

    private static Component run(String path, Map<String, Object> inputs) {
        return (Component) FakeNodeRun.run(node(path), inputs).get("text");
    }

    @Test
    void miseEnFormeAppliqueLesTroisStyles() {
        Component out = run("text/styled", Map.of("text", Component.literal("gras"),
                "bold", true, "italic", true, "underlined", true));
        assertTrue(out.getStyle().isBold());
        assertTrue(out.getStyle().isItalic());
        assertTrue(out.getStyle().isUnderlined());
        assertEquals("gras", out.getString());
    }

    /**
     * <b>Le piège.</b> Les {@code Component} de Minecraft sont partagés, et ces nœuds
     * sont PURS donc mémoïsés : appliquer un style en place changerait aussi le texte
     * que d'autres nœuds lisent — exactement le défaut évité sur les listes en 7.8.
     */
    @Test
    void styliserNeModifiePasLeTexteDOrigine() {
        Component source = Component.literal("intact");
        Component out = run("text/styled", Map.of("text", source, "bold", true));

        assertTrue(out.getStyle().isBold(), "la sortie est en gras");
        assertFalse(source.getStyle().isBold(), "et le texte d'entrée n'a pas bougé");
    }

    @Test
    void infobulleEtClicSePosentSurLeStyle() {
        Component hover = run("text/hover", Map.of("text", Component.literal("survole-moi"),
                "tooltip", Component.literal("coucou")));
        assertInstanceOf(HoverEvent.ShowText.class, hover.getStyle().getHoverEvent());

        Component suggest = run("text/click_suggest",
                Map.of("text", Component.literal("clique"), "command", "/help"));
        assertInstanceOf(ClickEvent.SuggestCommand.class, suggest.getStyle().getClickEvent());

        Component copy = run("text/click_copy",
                Map.of("text", Component.literal("copie"), "value", "abc"));
        assertInstanceOf(ClickEvent.CopyToClipboard.class, copy.getStyle().getClickEvent());
    }

    /**
     * Un clic qui EXÉCUTE une commande s'exécute avec les droits de celui qui clique.
     * Un blueprint de faible permission qui pourrait en fabriquer un ferait lancer à
     * un opérateur une commande qu'il n'a pas écrite.
     */
    @Test
    void leClicQuiExecuteExigeLaPermissionAdmin() {
        assertEquals(Permission.ADMIN, node("text/click_command").permission(),
                "sinon un graphe GAMEPLAY fabrique un piège pour opérateur");
        assertEquals(Permission.GAMEPLAY, node("text/click_suggest").permission(),
                "suggérer n'exécute rien");
        assertEquals(Permission.GAMEPLAY, node("text/click_copy").permission());
    }

    @Test
    void texteTraduitPorteSaCleEtSonArgument() {
        Component sansArg = run("text/translate", Map.of("key", "blueprint.cmd.state.on"));
        assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class,
                sansArg.getContents());

        Component avecArg = run("text/translate",
                Map.of("key", "blueprint.cmd.created", "arg", "essai"));
        var contents = assertInstanceOf(
                net.minecraft.network.chat.contents.TranslatableContents.class,
                avecArg.getContents());
        assertEquals(1, contents.getArgs().length);
    }

    /** Un pin texte vide vaut le texte vide, pas null. */
    @Test
    void unTexteManquantVautLeTexteVide() {
        assertEquals("", run("text/styled", Map.of("bold", true)).getString());
    }

    @Test
    void tousCesNoeudsSontPurs() {
        for (String path : List.of("text/styled", "text/hover", "text/click_suggest",
                "text/click_copy", "text/click_command", "text/translate")) {
            assertTrue(node(path).pure(), path + " devrait être pur");
        }
    }
}
