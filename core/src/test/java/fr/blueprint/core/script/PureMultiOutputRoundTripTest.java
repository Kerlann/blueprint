package fr.blueprint.core.script;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les nœuds purs à <b>plusieurs sorties</b> survivent à l'aller-retour BScript.
 *
 * <p>Ils n'y survivaient pas. Un appel inliné rend sa <b>première</b> sortie de donnée,
 * si bien que le générateur refusait d'inliner tout nœud pur qui en avait plusieurs — et
 * un nœud pur n'est atteignable que par des liens de donnée. Il n'était donc jamais émis :
 * le script produit ne le contenait pas, et le relire perdait le nœud <b>et tous les liens
 * qui y entraient</b>.
 *
 * <p>Quatre nœuds livrés étaient dans ce cas : {@code vec/split}, {@code pos/split},
 * {@code map/get} et {@code convert/to_number}. Ils sont dans la palette et dans la
 * référence générée.
 *
 * <p>Ce que cela coûtait vraiment : depuis la 10.16, un {@code .bp} est écrit à
 * <b>chaque enregistrement</b>. Un graphe qui décompose un vecteur produisait donc, à
 * chaque Ctrl+S, un export silencieusement amputé — avec un avertissement dans le journal
 * du serveur, c'est-à-dire nulle part.
 *
 * <p>La réparation tient en une annotation : {@code @out("pin")} nomme la sortie choisie
 * quand ce n'est pas la première. Elle n'apparaît que dans ce cas — l'ajouter partout
 * alourdirait chaque ligne pour le plus rare des usages.
 */
class PureMultiOutputRoundTripTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        assertTrue(result.applied(), () -> "opération refusée : " + result.refusal());
    }

    /**
     * Un graphe qui décompose un vecteur et n'utilise que sa <b>deuxième</b> sortie —
     * le cas que l'annotation existe pour porter.
     */
    private static Blueprint splitting() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "split"));
        UUID event = UUID.nameUUIDFromBytes("e".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID vec = UUID.nameUUIDFromBytes("v".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID split = UUID.nameUUIDFromBytes("s".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID wait = UUID.nameUUIDFromBytes("w".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID toInt = UUID.nameUUIDFromBytes("i".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        apply(bp, new EditOperation.AddNode(event, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(vec, node("vec/make"), new Vec2d(100, 100)));
        apply(bp, new EditOperation.AddNode(split, node("vec/split"), new Vec2d(200, 100)));
        apply(bp, new EditOperation.AddNode(wait, node("flow/wait"), new Vec2d(300, 0)));
        apply(bp, new EditOperation.SetLiteral(vec, "y", LiteralValue.of(PinTypes.DOUBLE, 7.0)));
        apply(bp, new EditOperation.AddLink(new Link(vec, "vec", split, "vec")));
        // « y » : la DEUXIÈME sortie. C'est tout l'objet du test — la première passerait
        // sans annotation, et le défaut serait resté invisible.
        apply(bp, new EditOperation.AddNode(toInt, node("convert/to_int"), new Vec2d(250, 100)));
        apply(bp, new EditOperation.AddLink(new Link(split, "y", toInt, "value")));
        apply(bp, new EditOperation.AddLink(new Link(toInt, "result", wait, "ticks")));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", wait, "exec_in")));
        return bp;
    }

    /**
     * <b>Le test qui compte.</b> Le nœud à trois sorties est émis, et le lien vers sa
     * deuxième sortie revient intact.
     */
    @Test
    void unNoeudPurAPlusieursSortiesSurvitALAllerRetour() {
        Blueprint before = splitting();
        var generated = ScriptGenerator.generate(before, LOADED.nodes());

        assertEquals(List.of(), generated.issues(),
                "le script ne doit rien laisser derrière lui");
        assertTrue(generated.text().contains("@out(\"y\")"),
                "la sortie choisie doit être nommée :\n" + generated.text());

        var parsed = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(parsed.success(), () -> "relecture refusée : " + parsed.error());
        Blueprint after = parsed.blueprint();

        assertEquals(before.nodes().size(), after.nodes().size(),
                "un nœud a été perdu à la relecture");
        assertEquals(before.links().size(), after.links().size(),
                "un lien a été perdu à la relecture");
        assertTrue(after.links().stream().anyMatch(l -> l.fromPin().equals("y")
                        && l.toPin().equals("value")),
                "le lien vers la deuxième sortie n'est pas revenu : " + after.links());
    }

    /** Et le texte régénéré depuis le graphe relu est identique — la fidélité, pas l'à-peu-près. */
    @Test
    void leSecondAllerRetourEstIdentiqueAuPremier() {
        String first = ScriptGenerator.generate(splitting(), LOADED.nodes()).text();
        var reread = ScriptParser.parse(first, LOADED);
        assertTrue(reread.success(), () -> "relecture refusée : " + reread.error());
        assertEquals(first, ScriptGenerator.generate(reread.blueprint(), LOADED.nodes()).text());
    }

    /**
     * Les quatre nœuds concernés sont nommés, pour que la liste ne se périme pas en
     * silence : si l'un cesse d'avoir plusieurs sorties, ou si un cinquième apparaît,
     * c'est ici qu'on veut le voir.
     */
    @Test
    void lesNoeudsPursAPlusieursSortiesSontCeuxQuOnCroit() {
        var found = new java.util.TreeSet<String>();
        for (var type : LOADED.nodes().all()) {
            boolean pure = type.inputs().stream()
                    .noneMatch(p -> p.kind() == fr.blueprint.api.pin.PinKind.EXEC)
                    && type.outputs().stream()
                    .noneMatch(p -> p.kind() == fr.blueprint.api.pin.PinKind.EXEC)
                    && !type.entryPoint();
            long outs = type.outputs().stream()
                    .filter(p -> p.kind() == fr.blueprint.api.pin.PinKind.DATA).count();
            if (pure && outs > 1) {
                found.add(type.id().getPath());
            }
        }
        assertEquals(java.util.Set.of("convert/to_number", "map/get", "pos/split", "vec/split"),
                found, "la liste des nœuds purs à plusieurs sorties a changé");
    }
}
