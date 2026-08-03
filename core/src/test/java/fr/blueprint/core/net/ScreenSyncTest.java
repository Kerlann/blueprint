package fr.blueprint.core.net;

import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementOptions;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le transport d'un écran (story 10.3, AC1). Le serveur envoie une <b>description</b> ;
 * elle doit revenir identique, et rien de mal formé ne doit pouvoir passer.
 */
class ScreenSyncTest {

    @Test
    void unEcranRicheRevientIdentique() {
        ScreenElement panel = new ScreenElement("cadre", ElementKind.PANEL, null,
                Anchor.CENTER, 10, -20,
                Extent.percent(0.8, 100, 400), Extent.of(180),
                ScreenText.key("menu.titre"),
                Identifier.fromNamespaceAndPath("pack", "textures/gui/fond.png"),
                new ElementStyle(0xFF102030, 0xFF405060, 2, 0xFFFFFFFF,
                        0xFF203040, 0xFF001020, 0x40101010, 4,
                        ElementStyle.TextAlign.CENTER), "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, ElementOptions.NONE,
                false, false);
        ScreenElement child = ScreenElement.of("ok", ElementKind.BUTTON, 5, 5, 60, 20)
                .withParent("cadre")
                .withText(ScreenText.literal("Valider"));

        Screen before = new Screen("menu", false, List.of(panel, child));
        Screen after = ScreenSync.fromBytes(ScreenSync.toBytes(before));

        assertEquals(before, after);
    }

    @Test
    void leDrapeauHudEtLOrdreDeDessinSurvivent() {
        Screen before = new Screen("barre", true, List.of(
                ScreenElement.of("z", ElementKind.LABEL, 0, 0, 10, 10),
                ScreenElement.of("a", ElementKind.LABEL, 0, 0, 10, 10)));
        Screen after = ScreenSync.fromBytes(ScreenSync.toBytes(before));

        assertTrue(after.hud());
        assertEquals(List.of("z", "a"), List.copyOf(after.elements().keySet()));
    }

    @Test
    void unEcranPleinTientLargementSousLaBorne() {
        List<ScreenElement> elements = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            elements.add(ScreenElement.of("element_numero_" + i, ElementKind.BUTTON,
                            i % 16 * 20, i / 16 * 12, 60, 20)
                    .withText(ScreenText.literal("Un libellé de longueur plausible " + i)));
        }
        byte[] bytes = ScreenSync.toBytes(new Screen("plein", false, elements));

        assertTrue(bytes.length < ScreenSync.MAX_BYTES,
                "128 éléments = " + bytes.length + " octets, borne " + ScreenSync.MAX_BYTES);
        assertEquals(128, ScreenSync.fromBytes(bytes).size());
    }

    /** Rien d'illisible ne lève : le client se signale et laisse le joueur où il est. */
    @Test
    void unFluxAbimeNeLevePasEtNeRendRien() {
        assertNull(ScreenSync.fromBytes(new byte[0]));
        assertNull(ScreenSync.fromBytes(new byte[]{1, 2, 3, 4, 5}));
        assertNull(ScreenSync.fromBytes(new byte[ScreenSync.MAX_BYTES + 1]));

        byte[] valid = ScreenSync.toBytes(Screen.empty("menu"));
        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length / 2);
        assertNull(ScreenSync.fromBytes(truncated), "un flux coupé en deux non plus");
    }

    /**
     * Un écran sans nom n'est pas adressable : le décodeur le refuse plutôt que d'en
     * ouvrir un que personne ne pourra ensuite désigner.
     */
    @Test
    void unEcranSansNomEstRefuse() {
        var tag = fr.blueprint.core.graph.ScreenNbt.encodeOne(Screen.empty("menu"));
        tag.putString("name", "");
        assertNull(fr.blueprint.core.graph.ScreenNbt.decodeOne(tag));
    }

    @Test
    void unEcranVideEstUnEcranValide() {
        Screen after = ScreenSync.fromBytes(ScreenSync.toBytes(Screen.empty("menu")));
        assertNotNull(after);
        assertEquals(0, after.size());
    }
}
