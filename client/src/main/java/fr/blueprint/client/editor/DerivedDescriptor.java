package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.registry.NodeDescriptor;
import org.jetbrains.annotations.Nullable;

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
                pins(shape.inputs(), true), pins(shape.outputs(), false), base.pure(),
                base.permission(), base.fuelCost(), base.deterministic(), base.entryPoint());
    }

    private static List<NodeDescriptor.PinDescriptor> pins(List<NodeShape.PinDef> defs,
                                                           boolean inputs) {
        List<NodeDescriptor.PinDescriptor> out = new ArrayList<>(defs.size());
        for (NodeShape.PinDef def : defs) {
            out.add(new NodeDescriptor.PinDescriptor(def.name(), def.kind(), def.type(),
                    inputs ? blankFor(def.type()) : null));
        }
        return out;
    }

    /**
     * La valeur qu'un paramètre non câblé <b>propose</b>, ou {@code null} s'il doit être
     * câblé.
     *
     * <p>Un champ de saisie n'apparaît sur une entrée que si elle porte une valeur — c'est
     * ce qui distingue « on peut taper ici » de « il faut brancher quelque chose ». Une
     * forme, elle, ne porte pas de défaut : sans cette valeur, appeler une fonction avec la
     * constante 3 demandait de poser un nœud littéral et de le câbler, là où le nœud
     * d'appel offre la case.
     *
     * <p>La liste suit celle que les nœuds du registre se donnent à eux-mêmes : les scalaires
     * qui se tapent ont un zéro, les objets — entité, objet, état de bloc — n'en ont pas,
     * parce qu'aucune valeur ne s'y écrit au clavier et qu'un champ vide y serait une
     * promesse fausse.
     */
    private static @Nullable LiteralValue blankFor(PinType type) {
        if (type.equals(PinTypes.BOOL)) {
            return LiteralValue.of(PinTypes.BOOL, false);
        }
        if (type.equals(PinTypes.INT)) {
            return LiteralValue.of(PinTypes.INT, 0);
        }
        if (type.equals(PinTypes.LONG)) {
            return LiteralValue.of(PinTypes.LONG, 0L);
        }
        if (type.equals(PinTypes.DOUBLE)) {
            return LiteralValue.of(PinTypes.DOUBLE, 0.0);
        }
        if (type.equals(PinTypes.STRING)) {
            return LiteralValue.of(PinTypes.STRING, "");
        }
        return null;
    }
}
