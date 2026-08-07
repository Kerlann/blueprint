package fr.blueprint.core.vm;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.Function;

/**
 * Tout ce dont la VM a besoin pour exécuter une IR. Les types de nœuds sont résolus
 * par identifiant à l'exécution — l'IR n'en référence jamais directement, condition
 * de sa sérialisabilité et du comportement fantôme (type disparu → faute propre).
 *
 * <p>{@code owner} est construit <b>ici</b>, une fois par lancement, et non dans la
 * boucle de la VM : {@code step} n'alloue pas (coding-standards §5).
 */
public record ExecutionEnvironment(Function<Identifier, NodeType> nodeResolver,
                                   BlueprintHandle blueprint,
                                   TriggerContext trigger,
                                   VarStore vars,
                                   VarOwner owner,
                                   @Nullable MinecraftServer server,
                                   @Nullable ServerLevel level,
                                   Logger logger) {

    /**
     * Un environnement <b>sans joueur</b> : il appartient à son blueprint et à personne
     * d'autre.
     *
     * <p>C'est exactement ce qu'est une exécution déclenchée par {@code server_tick}, et
     * c'est le défaut sûr : une variable de portée joueur y faute au lieu de retomber sur
     * une valeur arbitraire. La fabrique du mod passe le propriétaire complet quand
     * l'événement nomme un joueur.
     */
    public ExecutionEnvironment(Function<Identifier, NodeType> nodeResolver,
                                BlueprintHandle blueprint,
                                TriggerContext trigger,
                                VarStore vars,
                                @Nullable MinecraftServer server,
                                @Nullable ServerLevel level,
                                Logger logger) {
        this(nodeResolver, blueprint, trigger, vars, new VarOwner(blueprint.id(), null),
                server, level, logger);
    }
}
