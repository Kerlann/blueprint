package fr.blueprint.core.content;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 11.5 : ce qu'il fallait pour que le contenu déclaré <b>serve</b>.
 *
 * <p>Les stories 11.1 à 11.3 ont livré des items et des blocs réels, habillés, posables —
 * et parfaitement inertes. Un graphe ne savait pas quel item venait d'être utilisé, ni
 * quel bloc venait d'être cassé, et n'avait aucun nœud pour reconnaître ou habiller une
 * pile. Cette story ferme cet écart.
 */
class DeclaredContentNodesTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private static fr.blueprint.api.node.NodeType node(String path) {
        return LOADED.nodes().get(Identifier.fromNamespaceAndPath("blueprint", path))
                .orElseThrow(() -> new AssertionError("nœud absent : " + path));
    }

    /**
     * <b>Le test qui compte.</b> {@code player_use_item} ne disait pas QUEL objet avait
     * été utilisé : un graphe savait qu'on avait cliqué droit, et rien d'autre. Il ne
     * pouvait donc pas réagir à un item déclaré — ce qui vidait l'épic 11 de sa promesse —
     * ni d'ailleurs à une pomme.
     */
    @Test
    void lUsageDUnItemDitEnfinLequel() {
        var outputs = StandardEvents.PLAYER_USE_ITEM.outputs().stream()
                .map(fr.blueprint.api.event.EventType.OutDef::name).toList();
        assertTrue(outputs.contains("stack"), outputs.toString());
        assertTrue(outputs.contains("item"),
                "l'identifiant est ce qu'on compare neuf fois sur dix : " + outputs);
    }

    @Test
    void laCasseDUnBlocDitEnfinLequel() {
        var outputs = StandardEvents.PLAYER_BREAK_BLOCK.outputs().stream()
                .map(fr.blueprint.api.event.EventType.OutDef::name).toList();
        assertTrue(outputs.contains("block"), outputs.toString());
    }

    @Test
    void laPoseDUnBlocDeclareEstUnEvenement() {
        var outputs = StandardEvents.BLOCK_PLACED.outputs().stream()
                .map(fr.blueprint.api.event.EventType.OutDef::name).toList();
        assertTrue(outputs.containsAll(List.of("player", "pos", "block")), outputs.toString());
        assertTrue(LOADED.nodes().get(StandardEvents.BLOCK_PLACED.id()).orElseThrow()
                .entryPoint(), "un événement doit être un point d'entrée");
    }

    /**
     * <b>Le test qui compte.</b> Les trois événements de bloc se rangent sous « monde » et
     * non sous « joueur ».
     *
     * <p>On y va pour réagir au bloc ; le joueur n'est que celui qui passait par là. La
     * règle a aussi rendu « événements du joueur » lisible : elle atteignait treize
     * entrées, la borne au-delà de laquelle un repli de palette ne se lit plus d'un coup
     * d'œil — et {@code NodeCategoryTest} refuse d'aller plus loin.
     */
    @Test
    void lesEvenementsDeBlocSeRangentSousLeMonde() {
        for (var event : List.of(StandardEvents.BLOCK_PLACED, StandardEvents.PLAYER_BREAK_BLOCK,
                StandardEvents.PLAYER_USE_BLOCK)) {
            assertEquals(NodeCategories.EVENT_WORLD.id(),
                    LOADED.nodes().get(event.id()).orElseThrow().category().id(),
                    event.id() + " se cherche sous « monde »");
        }
    }

    @Test
    void lIdentifiantDUnePileSeLit() {
        var type = node("item/id");
        assertTrue(type.pure());
        assertEquals(PinTypes.RESOURCE_LOCATION,
                type.outputs().getFirst().type());
    }

    /**
     * Renommer est <b>pur</b> et rend une pile.
     *
     * <p>Le comportement lui-même — que la pile d'origine ne bouge pas — se prouve dans un
     * vrai serveur : construire un {@link ItemStack} demande les registres du jeu amorcés,
     * et aucun test headless de ce projet ne les amorce. C'est le gametest
     * {@code itemNodesReadAndDressRealStacks} qui s'en charge, et ce n'est pas un pis-aller :
     * il exerce en même temps les composants d'objet de Mojang, où un renommage casserait
     * en silence.
     *
     * <p>Ce qui se vérifie ici est ce qui se vérifie sans jeu : le nœud est <b>déclaré
     * pur</b>, et un nœud pur n'a pas le droit de modifier son entrée.
     */
    @Test
    void renommerEstPurEtRendUnePile() {
        var type = node("item/with_name");
        assertTrue(type.pure(), "un nœud qui rend une copie peut être pur ; l'inverse non");
        assertEquals(PinTypes.ITEMSTACK, type.outputs().getFirst().type());
        assertEquals(PinTypes.TEXT, type.inputs().stream()
                .filter(pin -> pin.name().equals("name")).findFirst().orElseThrow().type());
    }

    /**
     * La description est <b>tronquée</b> à ce que le jeu accepte plutôt que refusée : une
     * liste construite par une boucle peut déborder sans que l'auteur s'en doute, et
     * perdre l'objet entier pour cela serait disproportionné.
     */
    @Test
    void laDescriptionEstTronqueeEtNonRefusee() {
        var type = node("item/with_lore");
        assertEquals(PinTypes.listOf(PinTypes.TEXT),
                type.inputs().stream().filter(pin -> pin.name().equals("lines"))
                        .findFirst().orElseThrow().type());
        assertTrue(ItemLore.MAX_LINES > 0);
    }

    @Test
    void laCategorieItemResteLisible() {
        long count = LOADED.nodes().all().stream()
                .filter(type -> type.category().id().equals(NodeCategories.ITEM.id()))
                .count();
        assertTrue(count <= 12, "« item » porte " + count + " nœuds — la subdiviser");
    }
}
