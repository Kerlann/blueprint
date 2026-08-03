package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.Vec2d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Navigation au clavier entre nœuds (U5 : « le clavier suffit », story 9.4). Pur, donc
 * testable sans écran.
 *
 * <p>La règle : dans la direction demandée, on prend le nœud le plus proche <b>dans un
 * cône</b> — le décalage latéral compte double. Un simple « le plus proche » ferait
 * sauter la sélection en diagonale et on ne saurait jamais où l'on va ; un cône strict,
 * lui, bloquerait dès que deux nœuds ne sont pas parfaitement alignés.
 */
public final class KeyboardNav {

    /** Poids du décalage latéral : au-delà, on considère que ce n'est plus « dans cette direction ». */
    private static final double LATERAL_WEIGHT = 2.0;

    private KeyboardNav() {
    }

    /**
     * Le nœud suivant dans la direction {@code (dx, dy)} (une seule des deux valeurs
     * est non nulle), ou null s'il n'y a rien de ce côté.
     *
     * @param from nœud de départ ; null = prendre le plus proche de l'origine du regard
     */
    public static @Nullable UUID next(Blueprint blueprint, @Nullable UUID from, int dx, int dy) {
        if (blueprint.nodes().isEmpty()) {
            return null;
        }
        if (from == null || blueprint.node(from) == null) {
            return firstInReadingOrder(blueprint);
        }
        Vec2d origin = blueprint.node(from).position();
        UUID best = null;
        double bestScore = Double.MAX_VALUE;
        for (Node node : blueprint.nodes().values()) {
            if (node.uuid().equals(from)) {
                continue;
            }
            double ax = node.position().x() - origin.x();
            double ay = node.position().y() - origin.y();
            double forward = dx != 0 ? ax * dx : ay * dy;
            double lateral = dx != 0 ? Math.abs(ay) : Math.abs(ax);
            if (forward <= 0) {
                continue;   // derrière, ou exactement sur le côté
            }
            double score = forward + LATERAL_WEIGHT * lateral;
            // Départage par identifiant : deux nœuds superposés ne doivent pas faire
            // dépendre la navigation de l'ordre d'itération.
            if (score < bestScore || (score == bestScore && best != null
                    && node.uuid().compareTo(best) < 0)) {
                bestScore = score;
                best = node.uuid();
            }
        }
        return best;
    }

    /** Le nœud le plus « en haut à gauche » — point de départ quand rien n'est sélectionné. */
    public static @Nullable UUID firstInReadingOrder(Blueprint blueprint) {
        UUID best = null;
        double bestScore = Double.MAX_VALUE;
        for (Node node : blueprint.nodes().values()) {
            double score = node.position().x() + node.position().y();
            if (score < bestScore || (score == bestScore && best != null
                    && node.uuid().compareTo(best) < 0)) {
                bestScore = score;
                best = node.uuid();
            }
        }
        return best;
    }
}
