package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.Variable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Ce que le <b>blueprint</b> ajoute à la palette : ses variables et ses fonctions
 * (story 5.13, étendue par la 20.2).
 *
 * <p>Pur, et séparé du widget pour cette seule raison : le choix de ce qui apparaît dans la
 * palette porte des règles qu'on ne voit pas à l'œil — une fonction ne se propose pas depuis
 * son propre corps, une variable donne deux entrées et non une — et qu'un widget rendrait
 * invérifiables.
 *
 * <p>Recalculé à chaque ouverture, jamais mémorisé : la palette est construite une fois pour
 * la session, et une fonction créée cinq minutes plus tard n'y apparaîtrait pas (AC6).
 */
public final class BlueprintPaletteEntries {

    private BlueprintPaletteEntries() {
    }

    /**
     * @param openBody  le corps de fonction ouvert, ou {@code null} — la fonction qu'on est
     *                  en train d'éditer ne s'y propose pas.
     * @param translate clé + argument → texte, injecté pour rester testable sans jeu lancé.
     * @param typeName  le nom lisible d'un type de pin.
     */
    public static List<NodeSearch.Entry> of(Blueprint bp, @Nullable String openBody,
                                            BiFunction<String, String, String> translate,
                                            Function<fr.blueprint.api.pin.PinType, String> typeName) {
        List<NodeSearch.Entry> out = new ArrayList<>();
        for (Variable variable : bp.variables().values()) {
            String type = typeName.apply(variable.type());
            out.add(new NodeSearch.Entry(VarNodes.GET,
                    translate.apply("blueprint.editor.palette.var_get", variable.name()),
                    type, PaletteState.VARIABLES, variable.name()));
            out.add(new NodeSearch.Entry(VarNodes.SET,
                    translate.apply("blueprint.editor.palette.var_set", variable.name()),
                    type, PaletteState.VARIABLES, variable.name()));
        }
        for (BlueprintFunction function : bp.functions().values()) {
            // Une fonction ne s'appelle pas depuis son propre corps : la récursion est
            // refusée par le validateur, et proposer l'appel mènerait à un diagnostic
            // plutôt qu'à un nœud utilisable.
            if (function.name().equals(openBody)) {
                continue;
            }
            out.add(new NodeSearch.Entry(FuncNodes.CALL,
                    translate.apply("blueprint.editor.palette.func_call", function.name()),
                    FunctionPanelLayout.label(function), PaletteState.FUNCTIONS,
                    function.name()));
        }
        return out;
    }
}
