package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce qu'un nœud <b>affiche</b> comme titre (story 20.2).
 *
 * <p>Trois appels côte à côte lisaient tous « Appeler une fonction ». Le nom de la fonction,
 * seule chose qui les distingue, n'apparaissait nulle part : il fallait sélectionner chaque
 * nœud et regarder le panneau de détails pour savoir lequel fait quoi. C'est précisément le
 * défaut qu'un graphe est censé éviter.
 */
class NodeTitleTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    /** Traduction simulée : la clé entre crochets, pour lire ce qui a été choisi. */
    private static final java.util.function.Function<String, String> T = key -> "[" + key + "]";

    private Blueprint bp;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "titre"));
    }

    private Node poser(Identifier typeId, String function) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id, typeId, new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        if (function != null) {
            assertTrue(new EditOperation.SetLiteral(id, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, function))
                    .apply(bp, LOADED.nodes()).applied());
        }
        return bp.node(id);
    }

    private static NodeDescriptor desc(Identifier typeId) {
        return LOADED.nodes().get(typeId).map(NodeDescriptor::of).orElseThrow();
    }

    /**
     * <b>Un appel s'intitule du nom NU de sa fonction</b> — la convention d'Unreal.
     *
     * <p>Le pictogramme ƒ et la couleur de la catégorie disent déjà que c'est une fonction.
     * « Appeler » répété sur chaque nœud ne ferait que voler la place du seul mot qui
     * distingue les appels entre eux, et c'est ce mot qu'on cherche des yeux.
     */
    @Test
    void unAppelSIntituleDuNomNuDeSaFonction() {
        assertEquals("carre", NodeTitle.of(poser(FuncNodes.CALL, "carre"),
                desc(FuncNodes.CALL), T));
    }

    /**
     * <b>L'entrée porte le nom de la fonction, la sortie dit « Nœud de retour ».</b>
     *
     * <p>C'est la disposition d'Unreal, et elle se justifie : l'entrée d'un corps est ce
     * qu'on lit en arrivant, donc elle nomme ce qu'on édite ; la sortie, elle, n'a pas à
     * répéter un nom qu'on vient de lire à deux mètres — ce qu'on a besoin de savoir, c'est
     * que le flux s'arrête là.
     */
    @Test
    void lEntreePorteLeNomEtLaSortieDitRetour() {
        assertEquals("carre",
                NodeTitle.of(poser(FuncNodes.PARAM, "carre"), desc(FuncNodes.PARAM), T));
        assertEquals("[blueprint.editor.node.func_return]",
                NodeTitle.of(poser(FuncNodes.RESULT, "carre"), desc(FuncNodes.RESULT), T));
    }

    /**
     * <b>Le nom ne passe jamais par la traduction.</b>
     *
     * <p>Une fonction nommée {@code gui.done} s'afficherait « Terminé » si le nom traversait
     * {@code I18n.get} — c'est pour cela qu'il est rendu tel quel et jamais comme une clé.
     */
    @Test
    void leNomNePassePasParLaTraduction() {
        assertEquals("gui.done", NodeTitle.of(poser(FuncNodes.CALL, "gui.done"),
                desc(FuncNodes.CALL), T),
                "un nom qui ressemble à une clé de traduction doit s'afficher tel quel");
    }

    /** Un appel non encore lié garde le titre de son type, plutôt qu'un trou. */
    @Test
    void unAppelSansFonctionGardeLeTitreDeSonType() {
        Node nu = poser(FuncNodes.CALL, null);

        assertEquals("[" + desc(FuncNodes.CALL).titleKey() + "]",
                NodeTitle.of(nu, desc(FuncNodes.CALL), T));
    }

    /** Un nœud ordinaire n'est pas concerné. */
    @Test
    void unNoeudOrdinaireGardeSonTitre() {
        Identifier log = Identifier.fromNamespaceAndPath("blueprint", "debug/log");
        Node node = poser(log, null);

        assertEquals("[" + desc(log).titleKey() + "]", NodeTitle.of(node, desc(log), T));
    }

    /** Un fantôme dit quel type manque — c'est toute l'information utile. */
    @Test
    void unFantomeDitQuelTypeManque() {
        Node node = poser(Identifier.fromNamespaceAndPath("blueprint", "debug/log"), null);

        assertEquals("blueprint:debug/log", NodeTitle.of(node, null, T));
    }
}
