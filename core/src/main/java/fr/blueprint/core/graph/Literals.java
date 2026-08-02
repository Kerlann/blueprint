package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.ParameterizedPinType;
import fr.blueprint.api.pin.PinType;

import java.util.List;
import java.util.Map;

/**
 * Validation profonde des littéraux (reprise QA TYPE-001) : {@code LiteralValue.of}
 * ne contrôle que le conteneur ({@code List.class}) ; ici on vérifie récursivement
 * le type des éléments — c'est le validateur qui est l'autorité, pas la construction.
 */
final class Literals {

    private Literals() {
    }

    static boolean matches(PinType type, Object value) {
        if (type instanceof ParameterizedPinType p) {
            if (p.container() == ParameterizedPinType.Container.LIST) {
                if (!(value instanceof List<?> list)) {
                    return false;
                }
                for (Object element : list) {
                    if (!matches(p.args().get(0), element)) {
                        return false;
                    }
                }
                return true;
            }
            if (!(value instanceof Map<?, ?> map)) {
                return false;
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!matches(p.args().get(0), e.getKey()) || !matches(p.args().get(1), e.getValue())) {
                    return false;
                }
            }
            return true;
        }
        return type.javaType().isInstance(value);
    }

    static boolean matches(LiteralValue literal) {
        return matches(literal.type(), literal.value());
    }
}
