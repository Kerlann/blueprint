package fr.blueprint.core.net;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.DemoBlueprint;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'abus du canal réseau (story 6.4, AC3) : paquet géant, nœud inexistant,
 * lien invalide, identifiant malformé — plus la limitation de taux. Les graphes
 * abusifs sont FORGÉS AU NIVEAU NBT, comme le ferait un client modifié : passer par
 * les opérations d'édition ne prouverait rien, elles refusent déjà tout ça.
 */
class NetSecurityTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Function<Identifier, PinType> TYPES =
            id -> LOADED.pinTypes().get(id).orElse(null);
    private static final Identifier ID = Identifier.fromNamespaceAndPath("blueprint", "demo");

    private static Blueprint demo() {
        return DemoBlueprint.build(LOADED.nodes());
    }

    private static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GraphGuard.Verdict inspect(Blueprint bp) {
        return GraphGuard.inspect(bp.id(), bp, LOADED.nodes(), NetLimits.DEFAULT);
    }

    // ------------------------------------------------------------------ témoin

    @Test
    void theHonestCaseGoesThrough() {
        Blueprint received = GraphSync.fromBytes(GraphSync.toBytes(demo()), TYPES);
        assertNotNull(received);
        assertTrue(inspect(received).accepted(), "un graphe légitime n'est pas refusé");
    }

    // ------------------------------------------------------------- paquet géant

    @Test
    void aGiantPacketNeverReachesTheDecoder() {
        assertNull(GraphSync.fromBytes(new byte[GraphSync.MAX_BYTES + 1], TYPES));
        assertTrue(NetLimits.DEFAULT.maxGraphBytes() < GraphSync.MAX_BYTES,
                "la borne de stockage reste sous la borne de décodage");
    }

    @Test
    void tooManyNodesIsRefused() {
        Blueprint bp = new Blueprint(ID);
        Identifier type = Identifier.fromNamespaceAndPath("blueprint", "flow/sequence");
        GraphLimits generous = new GraphLimits(10_000);
        for (int i = 0; i <= NetLimits.DEFAULT.maxNodes(); i++) {
            new EditOperation.AddNode(UUID.randomUUID(), type, new Vec2d(i, 0))
                    .apply(bp, LOADED.nodes(), generous);
        }
        GraphGuard.Verdict verdict = inspect(bp);
        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("nœuds"), verdict.reason());
    }

    @Test
    void anEnormousLiteralIsRefused() {
        Blueprint bp = new Blueprint(ID);
        UUID node = UUID.randomUUID();
        Identifier print = Identifier.fromNamespaceAndPath("blueprint", "debug/print");
        new EditOperation.AddNode(node, print, new Vec2d(0, 0)).apply(bp, LOADED.nodes());
        assertNotNull(bp.node(node), "nœud debug/print attendu dans la bibliothèque");

        String huge = "x".repeat(NetLimits.DEFAULT.maxTextLength() + 1);
        new EditOperation.SetLiteral(node, "text",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, huge))
                .apply(bp, LOADED.nodes());

        GraphGuard.Verdict verdict = inspect(bp);
        assertFalse(verdict.accepted(), "un littéral démesuré ne se stocke pas");
        assertTrue(verdict.reason().contains("littéral"), verdict.reason());
    }

    // --------------------------------------------------------- nœud inexistant

    /** FR40/FR41 : un type inconnu du serveur devient fantôme — refuser détruirait le graphe. */
    @Test
    void anUnknownNodeTypeIsKeptAsGhostButNeverRuns() {
        Identifier foreign = Identifier.fromNamespaceAndPath("othermod", "drain");
        NodeShape shape = new NodeShape(
                List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false)),
                List.of(), true, Permission.SAFE);
        NodeTypeLookup clientSide = typeId -> foreign.equals(typeId) ? shape : null;

        Blueprint bp = new Blueprint(ID);
        new EditOperation.AddNode(UUID.randomUUID(), foreign, new Vec2d(0, 0))
                .apply(bp, clientSide);

        assertTrue(inspect(bp).accepted(), "le graphe est conservé tel quel");
        assertFalse(GraphGuard.executable(bp, LOADED.nodes()),
                "mais il ne s'exécute pas tant que le mod manque");
    }

    @Test
    void aFloodOfGhostsIsRefused() {
        Identifier foreign = Identifier.fromNamespaceAndPath("othermod", "drain");
        NodeShape shape = new NodeShape(List.of(), List.of(), true, Permission.SAFE);
        NodeTypeLookup clientSide = typeId -> shape;

        Blueprint bp = new Blueprint(ID);
        for (int i = 0; i <= NetLimits.DEFAULT.maxGhosts(); i++) {
            new EditOperation.AddNode(UUID.randomUUID(), foreign, new Vec2d(i, 0))
                    .apply(bp, clientSide);
        }
        GraphGuard.Verdict verdict = inspect(bp);
        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("fantômes"), verdict.reason());
    }

    // ------------------------------------------------------------ lien invalide

    @Test
    void aDanglingLinkIsRefused() {
        CompoundTag tag = GraphNbt.encode(demo());
        CompoundTag forged = new CompoundTag();
        forged.putString("from", UUID.randomUUID().toString());
        forged.putString("fromPin", "exec_out");
        forged.putString("to", UUID.randomUUID().toString());
        forged.putString("toPin", "exec_in");
        ((ListTag) tag.get("links")).add(forged);

        Blueprint received = GraphSync.fromBytes(compress(tag), TYPES);
        assertNotNull(received, "le NBT reste lisible : c'est le garde qui doit trancher");
        GraphGuard.Verdict verdict = inspect(received);
        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("pendant"), verdict.reason());
    }

    @Test
    void aLinkThatTheEditorWouldNeverAllowIsRefused() {
        CompoundTag tag = GraphNbt.encode(demo());
        ListTag links = (ListTag) tag.get("links");
        assertFalse(links.isEmpty(), "la démo a des liens");
        // Un pin d'arrivée inventé : l'éditeur n'aurait jamais posé ça.
        ((CompoundTag) links.get(0)).putString("toPin", "pin_qui_nexiste_pas");

        Blueprint received = GraphSync.fromBytes(compress(tag), TYPES);
        assertNotNull(received);
        GraphGuard.Verdict verdict = inspect(received);
        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("lien refusé"), verdict.reason());
    }

    // ------------------------------------------------------ identifiant malformé

    @Test
    void aMalformedBlueprintIdIsRejectedAtDecoding() {
        CompoundTag tag = GraphNbt.encode(demo());
        tag.putString("id", "PAS UN IDENTIFIANT");
        assertNull(GraphSync.fromBytes(compress(tag), TYPES),
                "le décodage lève, le garde n'a même pas à se prononcer");
    }

    @Test
    void aMalformedNodeTypeCannotSmuggleADanglingLink() {
        CompoundTag tag = GraphNbt.encode(demo());
        ListTag links = (ListTag) tag.get("links");
        String victim = ((CompoundTag) links.get(0)).getStringOr("to", "");
        for (Tag entry : (ListTag) tag.get("nodes")) {
            CompoundTag node = (CompoundTag) entry;
            if (node.getStringOr("uuid", "").equals(victim)) {
                node.putString("type", "PAS UN IDENTIFIANT");
            }
        }

        Blueprint received = GraphSync.fromBytes(compress(tag), TYPES);
        assertNotNull(received);
        // Le nœud est tombé au décodage : ses liens pendent, le graphe est refusé.
        GraphGuard.Verdict verdict = inspect(received);
        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("pendant"), verdict.reason());
    }

    @Test
    void aSaveAnnouncingAnotherBlueprintIsRefused() {
        Blueprint bp = demo();
        GraphGuard.Verdict verdict = GraphGuard.inspect(
                Identifier.fromNamespaceAndPath("blueprint", "autre"), bp,
                LOADED.nodes(), NetLimits.DEFAULT);
        assertFalse(verdict.accepted(), "on n'écrit pas sous un identifiant qu'on n'a pas annoncé");
    }

    /** QA SEC-001 : le plafond de permission voyage dans le graphe — il se contrôle. */
    @Test
    void aClientCannotGrantItselfAnAdminCap() {
        Blueprint bp = new Blueprint(ID, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", Permission.ADMIN));
        assertFalse(GraphGuard.capAllowed(bp, Permission.WORLD),
                "un joueur ordinaire n'accorde pas ADMIN");
        assertTrue(GraphGuard.capAllowed(bp, Permission.ADMIN), "un opérateur, si");

        Blueprint ordinary = new Blueprint(ID, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", Permission.WORLD));
        assertTrue(GraphGuard.capAllowed(ordinary, Permission.WORLD),
                "poser des blocs reste l'usage normal du mod");
    }

    // ---------------------------------------------------------- taux par joueur

    @Test
    void theRateLimiterSpendsRefillsAndForgets() {
        long[] now = {1_000L};
        RateLimiter limiter = new RateLimiter(3, 1_000L, () -> now[0]);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertTrue(limiter.allow(alice));
        assertTrue(limiter.allow(alice));
        assertTrue(limiter.allow(alice));
        assertFalse(limiter.allow(alice), "quota épuisé");
        assertTrue(limiter.allow(bob), "le quota est PAR joueur");

        now[0] += 340; // un tiers de fenêtre → un jeton
        assertTrue(limiter.allow(alice));
        assertFalse(limiter.allow(alice));

        now[0] += 10_000; // largement plus qu'une fenêtre : plafonné à la capacité
        assertEquals(3.0, limiter.tokens(alice), 0.001);

        limiter.forget(alice);
        limiter.forget(bob);
        assertEquals(0, limiter.tracked(), "un joueur parti ne laisse pas de trace");
    }

    // --------------------------------------------------------- écrans (épic 10)

    private static Blueprint withScreens(fr.blueprint.core.graph.screen.Screen... screens) {
        Blueprint bp = new Blueprint(ID);
        for (var screen : screens) {
            fr.blueprint.core.graph.GraphLoader.addScreen(bp, screen);
        }
        return bp;
    }

    private static fr.blueprint.core.graph.screen.Screen filled(String name, int count) {
        List<fr.blueprint.core.graph.screen.ScreenElement> elements = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            elements.add(fr.blueprint.core.graph.screen.ScreenElement.of("e" + i,
                    fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 40, 10));
        }
        return new fr.blueprint.core.graph.screen.Screen(name, false, elements);
    }

    /**
     * Les écrans voyagent dans l'instantané comme le reste du graphe, et rien ne les
     * regardait : un client pouvait remplir la sauvegarde du monde sans être arrêté.
     */
    @Test
    void unDelugeDEcransEstRefuse() {
        var screens = new fr.blueprint.core.graph.screen.Screen[NetLimits.DEFAULT.maxScreens() + 1];
        for (int i = 0; i < screens.length; i++) {
            screens[i] = fr.blueprint.core.graph.screen.Screen.empty("ecran" + i);
        }
        assertFalse(inspect(withScreens(screens)).accepted());
    }

    @Test
    void unEcranSurchargeEstRefuse() {
        assertTrue(inspect(withScreens(
                filled("ok", NetLimits.DEFAULT.maxElementsPerScreen()))).accepted());
        assertFalse(inspect(withScreens(
                filled("trop", NetLimits.DEFAULT.maxElementsPerScreen() + 1))).accepted());
    }

    @Test
    void unNomDElementDemesureEstRefuse() {
        String huge = "a".repeat(NetLimits.DEFAULT.maxTextLength() + 1);
        assertFalse(inspect(withScreens(new fr.blueprint.core.graph.screen.Screen(
                "menu", false, List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of(huge,
                                fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 40, 10)))))
                .accepted());

        assertFalse(inspect(withScreens(new fr.blueprint.core.graph.screen.Screen(
                "menu", false, List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("a",
                                        fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 40, 10)
                                .withText(fr.blueprint.core.graph.screen.ScreenText.literal(huge))))))
                .accepted(), "et le libellé compte autant que le nom");
    }

    /**
     * La MÊME règle que l'éditeur. Un bouton dans un HUD ne se pose pas dans le
     * concepteur ; il ne doit pas davantage arriver par le réseau, sans quoi la règle
     * ne protège que les auteurs honnêtes.
     */
    @Test
    void unElementQueLEditeurRefuseNArrivePasParLeReseau() {
        assertFalse(inspect(withScreens(new fr.blueprint.core.graph.screen.Screen(
                "barre", true, List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("piege",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 40, 10)))))
                .accepted(), "bouton interactif dans un HUD");

        assertFalse(inspect(withScreens(new fr.blueprint.core.graph.screen.Screen(
                "menu", false, List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("orphelin",
                                        fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 40, 10)
                                .withParent("inexistant")))))
                .accepted(), "parent qui n'existe pas");
    }

    @Test
    void unEcranNormalPasse() {
        assertTrue(inspect(withScreens(new fr.blueprint.core.graph.screen.Screen(
                "menu", false, List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("cadre",
                                fr.blueprint.core.graph.screen.ElementKind.PANEL, 10, 10, 200, 120),
                        fr.blueprint.core.graph.screen.ScreenElement.of("ok",
                                        fr.blueprint.core.graph.screen.ElementKind.BUTTON, 4, 4, 60, 20)
                                .withParent("cadre")))))
                .accepted());
    }
}
