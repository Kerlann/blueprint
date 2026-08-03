package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vecteurs et positions (batch 2). Ces nœuds débloquent tout le scripting de monde :
 * avant eux, {@code vec3} n'avait aucun constructeur, et « deux blocs au-dessus du
 * joueur » était inexprimable.
 */
class VectorNodesTest {

    private static final PluginLoader.LoadedRegistries REGISTRIES =
            PluginLoader.load(List.of(), true);

    private static NodeType node(String path) {
        NodeType type = REGISTRIES.nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", path)).orElse(null);
        assertNotNull(type, "nœud absent du registre : " + path);
        return type;
    }

    /** Exécute un nœud avec les entrées données et rend ses sorties. */
    private static Map<String, Object> run(String path, Map<String, Object> inputs) {
        return FakeNodeRun.run(node(path), inputs);
    }

    @Test
    void construireEtDecomposerUnVecteur() {
        Map<String, Object> made = run("vec/make", Map.of("x", 1.5, "y", -2.0, "z", 3.25));
        assertEquals(new Vec3(1.5, -2.0, 3.25), made.get("vec"));

        Map<String, Object> split = run("vec/split", Map.of("vec", new Vec3(1.5, -2.0, 3.25)));
        assertEquals(1.5, split.get("x"));
        assertEquals(-2.0, split.get("y"));
        assertEquals(3.25, split.get("z"));
    }

    /** Le geste qui motivait tout le lot : deux blocs au-dessus d'une position. */
    @Test
    void decalerUnVecteurSurUnAxe() {
        Map<String, Object> out = run("vec/offset",
                Map.of("vec", new Vec3(10, 64, -5), "dy", 2.0));
        assertEquals(new Vec3(10, 66, -5), out.get("vec"));
    }

    @Test
    void arithmetiqueVectorielle() {
        assertEquals(new Vec3(4, 6, 8), run("vec/add",
                Map.of("a", new Vec3(1, 2, 3), "b", new Vec3(3, 4, 5))).get("vec"));
        assertEquals(new Vec3(-2, -2, -2), run("vec/sub",
                Map.of("a", new Vec3(1, 2, 3), "b", new Vec3(3, 4, 5))).get("vec"));
        assertEquals(new Vec3(2, 4, 6), run("vec/scale",
                Map.of("vec", new Vec3(1, 2, 3), "factor", 2.0)).get("vec"));
        assertEquals(32.0, run("vec/dot",
                Map.of("a", new Vec3(1, 2, 3), "b", new Vec3(4, 5, 6))).get("dot"));
    }

    @Test
    void longueurEtDistance() {
        assertEquals(5.0, (Double) run("vec/length",
                Map.of("vec", new Vec3(3, 4, 0))).get("length"), 1e-9);
        assertEquals(5.0, (Double) run("vec/distance",
                Map.of("a", new Vec3(0, 0, 0), "b", new Vec3(3, 4, 0))).get("distance"), 1e-9);
    }

    /**
     * Normaliser le vecteur nul doit rendre le vecteur nul, pas NaN — sinon la valeur
     * empoisonne tout ce qui la consomme, sans aucune erreur pour l'expliquer.
     */
    @Test
    void normaliserLeVecteurNulNeProduitPasDeNaN() {
        Vec3 out = (Vec3) run("vec/normalize", Map.of("vec", Vec3.ZERO)).get("vec");
        assertEquals(Vec3.ZERO, out);
        assertTrue(Double.isFinite(out.x) && Double.isFinite(out.y) && Double.isFinite(out.z));
    }

    /**
     * Un pin vec3 non câblé et sans littéral vaut null : retomber sur l'origine
     * plutôt que lever un NPE opaque au milieu d'un graphe.
     */
    @Test
    void unVecteurManquantVautLOrigine() {
        assertEquals(Vec3.ZERO, run("vec/normalize", Map.of()).get("vec"));
        assertEquals(0.0, run("vec/length", Map.of()).get("length"));
    }

    // ----------------------------------------------------------------- positions

    @Test
    void construireDecomposerEtDecalerUnePosition() {
        assertEquals(new BlockPos(1, 2, 3),
                run("pos/make", Map.of("x", 1, "y", 2, "z", 3)).get("pos"));

        Map<String, Object> split = run("pos/split", Map.of("pos", new BlockPos(1, 2, 3)));
        assertEquals(1, split.get("x"));
        assertEquals(2, split.get("y"));
        assertEquals(3, split.get("z"));

        assertEquals(new BlockPos(1, 5, 3), run("pos/offset",
                Map.of("pos", new BlockPos(1, 2, 3), "dy", 3)).get("pos"));
    }

    /** Le pendant de player_use_block : poser sur la face touchée. */
    @Test
    void positionRelativeAUneDirection() {
        assertEquals(new BlockPos(10, 65, 20), run("pos/relative",
                Map.of("pos", new BlockPos(10, 64, 20),
                        "direction", Direction.UP, "distance", 1)).get("pos"));
        assertEquals(new BlockPos(10, 64, 22), run("pos/relative",
                Map.of("pos", new BlockPos(10, 64, 20),
                        "direction", Direction.SOUTH, "distance", 2)).get("pos"));
    }

    /**
     * Une position de bloc vaut le CENTRE de son bloc en vecteur. Viser le coin ferait
     * apparaître particules et entités à cheval sur quatre blocs — ça se voit tout de
     * suite en jeu, et on cherche longtemps pourquoi.
     */
    @Test
    void positionVersVecteurViseLeCentreDuBloc() {
        assertEquals(new Vec3(10.5, 64.5, 20.5), run("pos/to_vec",
                Map.of("pos", new BlockPos(10, 64, 20), "centered", true)).get("vec"));
        assertEquals(new Vec3(10, 64, 20), run("pos/to_vec",
                Map.of("pos", new BlockPos(10, 64, 20), "centered", false)).get("vec"));
    }

    /** Le retour : un vecteur au milieu d'un bloc retombe sur ce bloc. */
    @Test
    void vecteurVersPositionArrondiVersLeBloc() {
        assertEquals(new BlockPos(10, 64, 20),
                run("vec/to_pos", Map.of("vec", new Vec3(10.9, 64.1, 20.5))).get("pos"));
        assertEquals(new BlockPos(-1, 0, 0),
                run("vec/to_pos", Map.of("vec", new Vec3(-0.5, 0.5, 0.5))).get("pos"),
                "les coordonnées négatives arrondissent VERS LE BAS, comme Minecraft");
    }

    /** Aller-retour : position → vecteur centré → position rend la même position. */
    @Test
    void allerRetourPositionVecteurPosition() {
        BlockPos origin = new BlockPos(-13, 7, 129);
        Object vec = run("pos/to_vec", Map.of("pos", origin, "centered", true)).get("vec");
        assertEquals(origin, run("vec/to_pos", Map.of("vec", vec)).get("pos"));
    }

    /** Tous purs : aucun n'a de pin d'exécution, tous sont mémoïsables. */
    @Test
    void tousLesNoeudsDeVecteurSontPurs() {
        for (String path : List.of("vec/make", "vec/split", "vec/add", "vec/sub", "vec/scale",
                "vec/offset", "vec/length", "vec/distance", "vec/normalize", "vec/dot",
                "pos/make", "pos/split", "pos/offset", "pos/relative", "pos/distance",
                "pos/to_vec", "vec/to_pos")) {
            assertTrue(node(path).pure(), path + " devrait être pur");
        }
    }
}
