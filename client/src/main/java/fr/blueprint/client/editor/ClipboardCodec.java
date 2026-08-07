package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le presse-papier de l'éditeur EST du BScript (story 5.8, principe U6) : la
 * sélection s'extrait en sous-graphe, se sérialise en texte partageable hors du jeu,
 * et se re-parse à l'insertion. Pur : le presse-papier système est injecté par le
 * widget.
 */
public final class ClipboardCodec {

    private final PluginLoader.LoadedRegistries registries;

    public ClipboardCodec(PluginLoader.LoadedRegistries registries) {
        this.registries = registries;
    }

    /** Résultat d'un collage : le fragment parsé, ou l'erreur de parse. */
    public record PasteResult(@Nullable Blueprint fragment, @Nullable String error) {
        public boolean success() {
            return fragment != null;
        }
    }

    /**
     * Position sentinelle des événements synthétiques : le générateur n'émet que des
     * blocs {@code on <event>}, donc chaque chaîne exec orpheline est greffée sous un
     * {@code signal} synthétique posé ici — {@code @pos} survit au round-trip et sert
     * de marqueur pour le retirer au collage.
     */
    static final double SENTINEL = -1_000_000;

    /**
     * Extrait la sélection en fragment autonome : nœuds (mêmes UUID — le collage
     * remappe), liens INTERNES à la sélection, littéraux, et les variables que les
     * nœuds var référencent (déclarées en tête de script).
     */
    public static Blueprint extract(Blueprint src, Set<UUID> selection, NodeTypeLookup lookup) {
        Blueprint fragment = new Blueprint(src.id());
        for (UUID id : selection) {
            Node node = src.node(id);
            if (node == null) {
                continue;
            }
            new EditOperation.AddNode(id, node.typeId(), node.position()).apply(fragment, lookup);
            for (Map.Entry<String, LiteralValue> literal : node.literals().entrySet()) {
                new EditOperation.SetLiteral(id, literal.getKey(), literal.getValue())
                        .apply(fragment, lookup);
            }
            if (VarNodes.isVarNode(node.typeId())) {
                String name = VarNodes.boundName(node);
                Variable variable = name == null ? null : src.variables().get(name);
                if (variable != null && !fragment.variables().containsKey(name)) {
                    new EditOperation.AddVariable(variable).apply(fragment, lookup);
                }
            }
        }
        for (Link link : src.links()) {
            if (selection.contains(link.fromNode()) && selection.contains(link.toNode())) {
                new EditOperation.AddLink(link).apply(fragment, lookup);
            }
        }
        graftSyntheticEvents(fragment, lookup);
        return fragment;
    }

    /** Greffe un événement synthétique en tête de chaque chaîne exec orpheline. */
    private static void graftSyntheticEvents(Blueprint fragment, NodeTypeLookup lookup) {
        List<Node> heads = new ArrayList<>();
        for (Node node : fragment.nodes().values()) {
            NodeShape shape = lookup.shape(fragment, node);
            if (shape == null) {
                continue;
            }
            for (NodeShape.PinDef def : shape.inputs()) {
                if (def.kind() == PinKind.EXEC
                        && fragment.linksInto(node.uuid(), def.name()).isEmpty()) {
                    heads.add(node);
                    break;
                }
            }
        }
        int i = 0;
        for (Node head : heads) {
            NodeShape shape = lookup.shape(fragment, head);
            String execIn = null;
            for (NodeShape.PinDef def : shape.inputs()) {
                if (def.kind() == PinKind.EXEC
                        && fragment.linksInto(head.uuid(), def.name()).isEmpty()) {
                    execIn = def.name();
                    break;
                }
            }
            UUID event = UUID.randomUUID();
            new EditOperation.AddNode(event, StandardEvents.SIGNAL.id(),
                    new Vec2d(SENTINEL, SENTINEL + i++ * 100)).apply(fragment, lookup);
            new EditOperation.AddLink(new Link(event, "exec_out", head.uuid(), execIn))
                    .apply(fragment, lookup);
        }
    }

    /** Retire du fragment parsé les événements synthétiques (position sentinelle). */
    private static void stripSyntheticEvents(Blueprint fragment, NodeTypeLookup lookup) {
        for (Node node : List.copyOf(fragment.nodes().values())) {
            if (node.position().x() <= SENTINEL / 2) {
                new EditOperation.RemoveNode(node.uuid()).apply(fragment, lookup);
            }
        }
    }

    /** Sélection → BScript (texte du presse-papier). */
    public String copy(Blueprint src, Set<UUID> selection, NodeTypeLookup lookup) {
        return ScriptGenerator.generate(extract(src, selection, lookup), lookup).text();
    }

    /** Presse-papier → fragment, ou erreur de parse (rien n'est inséré). */
    public PasteResult paste(String text) {
        if (text == null || text.isBlank()) {
            return new PasteResult(null, "vide");
        }
        ScriptParser.ParseResult parsed = ScriptParser.parse(text, registries);
        if (!parsed.success()) {
            return new PasteResult(null, parsed.error());
        }
        stripSyntheticEvents(parsed.blueprint(), registries.nodes());
        return new PasteResult(parsed.blueprint(), null);
    }

    /** L'identifiant du blueprint n'a pas d'importance pour un fragment. */
    static Identifier fragmentId() {
        return Identifier.fromNamespaceAndPath("blueprint", "fragment");
    }
}
