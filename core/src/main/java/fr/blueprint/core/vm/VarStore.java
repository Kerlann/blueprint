package fr.blueprint.core.vm;

import fr.blueprint.core.graph.VarScope;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Stockage des variables non locales ({@code GRAPH}, {@code WORLD}, {@code PLAYER}).
 * La portée {@code LOCAL} vit dans l'{@link ExecutionState}, jamais ici.
 *
 * <p><b>Chaque valeur a un propriétaire</b>, donné par {@link VarOwner}. Ce n'était pas
 * le cas : le rangement se faisait par {@code (portée, nom)} seul, si bien que
 * {@code VarScope.PLAYER} — documenté « persistante par joueur » — mettait tous les
 * joueurs dans le même panier. Le deuxième à écrire son prénom effaçait celui du premier,
 * et l'écran de chacun montrait l'identité du dernier arrivé. {@code GRAPH} avait le même
 * trou entre deux blueprints portant chacun une variable {@code score}.
 *
 * <p>{@code PLAYER} est isolé <b>par blueprint autant que par joueur</b>. Il ne l'était
 * pas : deux graphes déclarant chacun un {@code prenom} écrivaient au même endroit, avec
 * deux types possiblement différents et rien pour le signaler. Le partage entre graphes
 * existe toujours, mais il se déclare — {@code PLAYER_SHARED}.
 *
 * <p>Un accès {@code PLAYER} sans joueur ne retombe <b>pas</b> sur un panier commun : il
 * faute, et la faute nomme la variable. Un graphe branché sur {@code server_tick} n'a
 * aucun joueur ; lui laisser lire « la » valeur aurait rendu celle d'un joueur au hasard,
 * ce qui est plus difficile à diagnostiquer qu'un refus.
 */
public interface VarStore {

    @Nullable Object get(VarScope scope, VarOwner owner, String name);

    void set(VarScope scope, VarOwner owner, String name, @Nullable Object value);

    /**
     * Le propriétaire porte-t-il ce qu'il faut pour cette portée ?
     *
     * <p>Posé <b>avant</b> l'accès plutôt que dedans : la VM veut fauter en nommant le
     * nœud, ce qu'un magasin qui ne connaît que la portée et le nom ne peut pas faire.
     */
    static boolean owns(VarScope scope, VarOwner owner) {
        return switch (scope) {
            // PLAYER veut les DEUX : le joueur, et le blueprint qui l'isole des autres.
            case PLAYER -> owner.player() != null && owner.blueprint() != null;
            case PLAYER_SHARED -> owner.player() != null;
            case GRAPH -> owner.blueprint() != null;
            case WORLD, LOCAL -> true;
        };
    }

    /**
     * Applique les valeurs par défaut déclarées par un blueprint, <b>sans jamais
     * écraser</b> ce qui existe déjà.
     *
     * <p>Sans cela, un {@code var double or = 20} lu avant d'avoir été écrit rendait
     * {@code null}, et le nœud consommateur tombait en faute avec « le pin n'a ni
     * valeur ni défaut ». Le défaut était donc purement décoratif : l'éditeur
     * l'affichait, la sérialisation le transportait, l'exécution l'ignorait.
     *
     * <p>Appelé au lancement de chaque exécution : c'est le seul moment où l'on
     * connaît à la fois les déclarations et le magasin. Le coût est un parcours des
     * variables du graphe, borné à 256 par les plafonds réseau.
     *
     * <p>Les variables dont le propriétaire manque sont <b>sautées</b>, pas fautées : un
     * graphe de tick qui déclare une variable joueur est parfaitement légitime tant qu'il
     * ne la lit qu'à travers {@code var/get_for}. Semer sa valeur par défaut n'aurait de
     * toute façon eu aucun sens sans savoir chez qui.
     */
    default void seedDefaults(fr.blueprint.core.graph.Blueprint blueprint, VarOwner owner) {
        for (var variable : blueprint.variables().values()) {
            if (variable.defaultValue() == null || variable.scope() == VarScope.LOCAL
                    || !owns(variable.scope(), owner)) {
                continue;
            }
            if (get(variable.scope(), owner, variable.name()) == null) {
                set(variable.scope(), owner, variable.name(), variable.defaultValue().value());
            }
        }
    }

    static VarStore inMemory() {
        return new VarStore() {
            private final VarBuckets buckets = new VarBuckets();

            @Override
            public @Nullable Object get(VarScope scope, VarOwner owner, String name) {
                if (!owns(scope, owner)) {
                    return null;
                }
                Map<String, Object> bucket = buckets.of(scope, owner, false);
                return bucket == null ? null : bucket.get(name);
            }

            @Override
            public void set(VarScope scope, VarOwner owner, String name,
                            @Nullable Object value) {
                if (!owns(scope, owner)) {
                    return;
                }
                Map<String, Object> bucket = buckets.of(scope, owner, true);
                if (bucket != null) {
                    bucket.put(name, value);
                }
            }
        };
    }
}
