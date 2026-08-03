package fr.blueprint.core.compile.ir;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.core.graph.VarScope;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jeu d'instructions de la VM (story 3.1). Machine à slots : chaque pin de sortie de
 * données possède un slot ; {@link Call} est aussi la table de sauts du flux exec —
 * les branches d'un nœud ({@code ctx.exec}) se résolvent par {@code execTargets},
 * une cible négative terminant l'exécution. Interface scellée : le {@code switch}
 * de la VM est exhaustif par construction.
 *
 * <p>Chaque instruction porte l'UUID du nœud source (diagnostics, débogage) — nul
 * pour les instructions synthétiques. {@code FRAME_PUSH/POP} arriveront avec les
 * fonctions utilisateur (v1.1) : l'interface scellée s'étend sans rien casser.
 */
public sealed interface Instruction {

    @Nullable UUID source();

    /** Liaison d'un pin à son slot. */
    record PinBinding(String pin, int slot) {
    }

    /** Charge un littéral dans un slot. */
    record Const(int slot, LiteralValue value, @Nullable UUID source) implements Instruction {
    }

    /**
     * Exécute un nœud : lit les entrées depuis les slots, écrit les sorties, puis
     * saute vers la cible exec choisie par l'action (ou la première déclarée).
     * Un nœud pur ({@code pure}) n'a pas de cibles et enchaîne sur l'instruction suivante.
     */
    record Call(Identifier type, List<PinBinding> inputs, List<PinBinding> outputs,
                Map<String, Integer> execTargets, int fuelCost, boolean pure,
                @Nullable UUID source) implements Instruction {
    }

    /** Saut inconditionnel. */
    record Jmp(int target, @Nullable UUID source) implements Instruction {
    }

    /** Continue si le slot contient {@code true}, sinon saute vers {@code elseTarget}. */
    record JmpIf(int conditionSlot, int elseTarget, @Nullable UUID source) implements Instruction {
    }

    /** Lit une variable vers un slot ({@code LOCAL} vit dans l'exécution, le reste dans le {@code VarStore}). */
    record LoadVar(VarScope scope, String name, int slot, @Nullable UUID source) implements Instruction {
    }

    /** Écrit un slot dans une variable. */
    record StoreVar(VarScope scope, String name, int slot, @Nullable UUID source) implements Instruction {
    }

    /**
     * Appel de sous-chaîne (flux structuré 7.1b) : empile l'adresse de retour
     * (instruction suivante) et saute. Une cible pendante (−1) dans la sous-chaîne
     * dépile et reprend — {@code Return} reste terminal pour toute l'exécution.
     */
    record CallSub(int target, @Nullable UUID source) implements Instruction {
    }

    /** Suspend l'exécution pour un nombre de ticks fixe ; reprise à l'instruction suivante. */
    record Yield(int ticks, @Nullable UUID source) implements Instruction {
    }

    /** Termine l'exécution. */
    record Return(@Nullable UUID source) implements Instruction {
    }
}
