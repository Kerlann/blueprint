package fr.blueprint.client.editor;

import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.registry.NodeDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Un descripteur dont les pins viennent d'une {@link NodeShape} plutôt que du registre
 * (story 20.2).
 *
 * <p>Le registre n'a d'un {@code func/call} que le <b>squelette</b> : le pin qui nomme la
 * fonction et ses deux pins d'exécution. Ni paramètres, ni sorties — ils dépendent de la
 * signature, qui vit dans le blueprint. Le dessin lisait ce squelette pendant que la
 * géométrie, elle, calculait déjà la boîte sur la forme complète : un nœud à la bonne
 * taille dont les pins n'apparaissaient nulle part, et des liens qu'on posait à l'aveugle.
 *
 * <p>La classe existe séparément du widget parce que la question — « quels pins ce nœud
 * montre-t-il ? » — se vérifie sans fenêtre, et que c'est précisément celle qu'on avait
 * cru régler en corrigeant le contrôleur.
 */
public final class DerivedDescriptor {

    private DerivedDescriptor() {
    }

    /**
     * {@code base}, mais avec les pins de {@code shape}.
     *
     * <p>Tout le reste est conservé : titre, catégorie, permission, coût. Seuls les pins
     * dépendent du blueprint ; les recopier depuis la forme et prendre le reste du registre
     * est ce qui garde un nœud de fonction identique aux autres pour tout le reste de
     * l'éditeur.
     */
    public static NodeDescriptor withPins(NodeDescriptor base, NodeShape shape) {
        return new NodeDescriptor(base.id(), base.category(), base.titleKey(), base.descKey(),
                pins(shape.inputs()), pins(shape.outputs()), base.pure(), base.permission(),
                base.fuelCost(), base.deterministic(), base.entryPoint());
    }

    private static List<NodeDescriptor.PinDescriptor> pins(List<NodeShape.PinDef> defs) {
        List<NodeDescriptor.PinDescriptor> out = new ArrayList<>(defs.size());
        for (NodeShape.PinDef def : defs) {
            // Pas de valeur par défaut : une forme n'en porte pas, et un paramètre de
            // fonction n'en a pas non plus — c'est l'appelant qui fournit.
            out.add(new NodeDescriptor.PinDescriptor(def.name(), def.kind(), def.type(), null));
        }
        return out;
    }
}
