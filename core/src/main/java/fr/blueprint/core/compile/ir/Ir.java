package fr.blueprint.core.compile.ir;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Représentation intermédiaire d'un point d'entrée compilé : la liste d'instructions,
 * le nombre de slots à allouer, et la provenance (blueprint + révision — le cache
 * s'invalide quand la révision change).
 */
public record Ir(Identifier blueprintId, int revision, List<Instruction> instructions, int slotCount) {

    public Ir {
        // Normalisation : les Call sortent du compilateur avec des tables mutables
        // (rétro-remplissage des cibles) — l'Ir publiée est intégralement immuable.
        List<Instruction> normalized = new ArrayList<>(instructions.size());
        for (Instruction ins : instructions) {
            if (ins instanceof Instruction.Call call) {
                // PAS Map.copyOf : l'ordre d'insertion des cibles exec est un contrat
                // (la VM prend la première déclarée quand l'action ne choisit pas).
                normalized.add(new Instruction.Call(call.type(), List.copyOf(call.inputs()),
                        List.copyOf(call.outputs()),
                        Collections.unmodifiableMap(new LinkedHashMap<>(call.execTargets())),
                        call.fuelCost(), call.pure(), call.source()));
            } else {
                normalized.add(ins);
            }
        }
        instructions = List.copyOf(normalized);
    }
}
