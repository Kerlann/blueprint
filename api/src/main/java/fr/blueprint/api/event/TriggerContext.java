package fr.blueprint.api.event;

import net.minecraft.resources.Identifier;

/**
 * Contexte de l'événement qui a déclenché l'exécution, exposé via
 * {@code NodeContext.trigger()}. Surface minimale — complétée par la story 2.5
 * (charge utile typée de l'événement) sans casser les consommateurs.
 */
public interface TriggerContext {

    /** Identifiant de l'événement déclencheur ({@code blueprint:event/player_join}…). */
    Identifier eventId();
}
