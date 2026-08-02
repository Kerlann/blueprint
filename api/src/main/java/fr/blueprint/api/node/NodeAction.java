package fr.blueprint.api.node;

/**
 * Corps d'un nœud, appelé par la VM à chaque exécution. Une exception levée ici est
 * capturée, journalisée avec le nœud fautif, et met le blueprint en faute — elle
 * n'atteint jamais la boucle de tick.
 */
@FunctionalInterface
public interface NodeAction {

    void run(NodeContext ctx) throws Exception;
}
