package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import org.jetbrains.annotations.Nullable;

/**
 * Variable typée d'un blueprint. {@code defaultValue} est nul pour les types sans
 * littéral (références vivantes) ; {@code replicated} synchronise la valeur vers les
 * clients en lecture seule.
 */
public record Variable(String name, PinType type, @Nullable LiteralValue defaultValue,
                       VarScope scope, boolean replicated) {

    public Variable {
        if (defaultValue != null && defaultValue.type() != type) {
            throw new IllegalArgumentException("Valeur par défaut de « " + name
                    + " » : type " + defaultValue.type() + " ≠ " + type);
        }
    }
}
