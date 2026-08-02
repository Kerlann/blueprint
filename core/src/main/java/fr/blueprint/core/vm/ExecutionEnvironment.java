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
 */
public record ExecutionEnvironment(Function<Identifier, NodeType> nodeResolver,
                                   BlueprintHandle blueprint,
                                   TriggerContext trigger,
                                   VarStore vars,
                                   @Nullable MinecraftServer server,
                                   @Nullable ServerLevel level,
                                   Logger logger) {
}
