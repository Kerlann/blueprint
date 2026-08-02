package fr.blueprint.core.vm;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Une exécution suspendue, prête à être persistée puis reprise — y compris à travers
 * un redémarrage du monde (story 3.4). La reprise recompile l'IR depuis le blueprint
 * (contrôle de {@code revision}) : l'IR n'est jamais persistée avec l'exécution.
 */
public record SuspendedExecution(Identifier blueprintId, int revision, int remainingTicks,
                                 ExecutionState state, Identifier eventId,
                                 Map<String, Object> triggerValues) {
}
