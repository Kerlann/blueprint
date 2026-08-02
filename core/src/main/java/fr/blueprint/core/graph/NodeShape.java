package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Forme d'un type de nœud vue par le validateur : ses pins, sa permission, et s'il
 * s'agit d'un point d'entrée (nœud d'événement).
 */
public record NodeShape(List<PinDef> inputs, List<PinDef> outputs,
                        boolean entryPoint, Permission permission) {

    /** Définition d'un pin. {@code required} : doit être câblé ou porter un littéral explicite. */
    public record PinDef(String name, PinKind kind, PinType type, boolean required) {
        public static PinDef in(String name, PinType type) {
            return new PinDef(name, type == fr.blueprint.api.pin.PinTypes.EXEC ? PinKind.EXEC : PinKind.DATA, type, false);
        }

        public static PinDef required(String name, PinType type) {
            return new PinDef(name, PinKind.DATA, type, true);
        }
    }

    public NodeShape {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public @Nullable PinDef input(String name) {
        for (PinDef p : inputs) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }

    public @Nullable PinDef output(String name) {
        for (PinDef p : outputs) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }
}
