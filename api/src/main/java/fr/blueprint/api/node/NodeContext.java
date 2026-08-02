package fr.blueprint.api.node;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * Contexte d'exécution passé à {@link NodeAction} : lire ses entrées, écrire ses
 * sorties, choisir la branche suivante, ou suspendre l'exécution.
 *
 * <p>Le contexte n'est valide que pendant l'appel de l'action — le conserver dans un
 * champ lève une exception (garde anti-fuite, story 2.3). Les accesseurs
 * {@code blueprint()} et {@code trigger()} arriveront avec leurs types (stories
 * 2.3/2.5) sans casser les consommateurs existants.
 */
public interface NodeContext {

    /**
     * Valeur du pin d'entrée nommé, déjà évaluée et typée. Lire un pin inexistant ou
     * du mauvais type est une erreur de développeur : exception immédiate et explicite.
     */
    <T> T in(String pin);

    /** Écrit la valeur d'un pin de sortie. */
    void out(String pin, Object value);

    /** Choisit la sortie d'exécution à suivre (nœuds de flux à branches). */
    void exec(String pin);

    /** Suspend l'exécution ; elle reprend dans {@code ticks} ticks, même après un redémarrage. */
    void suspend(int ticks);

    /** Met le blueprint en faute proprement, avec un message destiné à l'auteur du graphe. */
    void fail(Component reason);

    MinecraftServer server();

    ServerLevel level();

    /** Le blueprint en cours d'exécution (lecture seule). */
    BlueprintHandle blueprint();

    /** L'événement qui a déclenché cette exécution. */
    fr.blueprint.api.event.TriggerContext trigger();

    /** Journal préfixé par l'identifiant du blueprint en cours. */
    Logger logger();
}
