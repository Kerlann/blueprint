package fr.blueprint.core.vm;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Issue d'un {@code run} de la VM. Scellé : l'ordonnanceur (3.5) traite tous les cas. */
public sealed interface ExecResult {

    /** L'exécution est terminée. */
    record Done() implements ExecResult {
    }

    /** Suspendue volontairement ; reprendre dans {@code ticks} ticks. */
    record Suspended(int ticks) implements ExecResult {
    }

    /** Budget de fuel épuisé ; l'état est préservé, reprendre au prochain tick. */
    record OutOfFuel() implements ExecResult {
    }

    /** Faute : nœud fautif (si connu) et message. Le blueprint doit passer FAULTED. */
    record Faulted(@Nullable UUID node, String message) implements ExecResult {
    }

    ExecResult DONE = new Done();
    ExecResult OUT_OF_FUEL = new OutOfFuel();
}
