package fr.blueprint.core.vm;

import fr.blueprint.core.graph.VarScope;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les valeurs répliquées qui ont changé depuis le dernier envoi (épic 21, story 21.3).
 *
 * <p>Un ensemble de <b>désignations</b> et non de valeurs : la valeur est relue au moment de
 * l'envoi, à la fin du tick. Garder les valeurs aurait demandé de choisir laquelle conserver
 * quand un graphe écrit trois fois dans le même tick, et la réponse — la dernière — est
 * exactement ce que la relecture donne gratuitement.
 *
 * <p>Un {@link LinkedHashSet} : le dédoublonnage est le but (un graphe qui écrit l'or à chaque
 * itération d'une boucle ne doit produire qu'une marque), et l'ordre d'insertion rend l'envoi
 * reproductible, donc les tests lisibles.
 *
 * <h2>Borné, comme tout le reste</h2>
 *
 * <p>Le plafond est celui du protocole ({@code BlueprintPayloads.MAX_VALUES}) multiplié par une
 * marge, parce que plusieurs blueprints marquent dans le même tick. Au-delà, les marques
 * <b>supplémentaires sont refusées</b> et non les anciennes évincées : perdre la plus ancienne
 * ferait disparaître un changement pour toujours, alors que refuser la plus récente le laisse
 * revenir au prochain tick où la variable change — et, en attendant, l'écran affiche une valeur
 * périmée d'un tick plutôt qu'une valeur fausse pour toujours.
 */
public final class VarDirty {

    /**
     * Une valeur désignée : sa portée, ce qui l'isole, son nom.
     *
     * <p>Les deux champs d'isolation sont <b>normalisés selon la portée</b> par
     * {@link #mark}, et c'est ce qui rend le dédoublonnage juste. Une variable {@code WORLD}
     * écrite par un graphe que dix joueurs viennent de déclencher est <b>une</b> valeur : si
     * le joueur déclencheur entrait dans la désignation, elle produirait dix marques pour une
     * seule valeur, donc dix fois le même envoi. À l'inverse, une variable {@code GRAPH} de
     * deux blueprints différents est bien deux valeurs, et les confondre en enverrait une
     * pour l'autre.
     *
     * <p>La normalisation suit exactement la règle de possession de {@link VarStore#owns} :
     * ce qui identifie une valeur est ce sans quoi on ne peut pas la lire.
     */
    public record Mark(VarScope scope, @Nullable UUID player,
                       @Nullable net.minecraft.resources.Identifier blueprint, String name) {
    }

    /**
     * Plafond du carnet. Généreux par rapport à une trame — plusieurs blueprints marquent dans
     * le même tick — et fini, parce que rien n'est illimité (principe P3).
     */
    public static final int MAX_MARKS = 512;

    private final Set<Mark> marks = new LinkedHashSet<>();

    /**
     * Note qu'une valeur a changé.
     *
     * @return faux si le carnet est plein et que la marque a été refusée
     */
    public synchronized boolean mark(VarScope scope, VarOwner owner, String name) {
        Mark mark = designate(scope, owner, name);
        if (marks.contains(mark)) {
            return true;   // déjà notée : c'est le dédoublonnage, pas un refus
        }
        if (marks.size() >= MAX_MARKS) {
            return false;
        }
        marks.add(mark);
        return true;
    }

    /**
     * Ne garde de l'écrivain que ce qui <b>isole la valeur</b> pour cette portée.
     *
     * <p>Même découpage que {@link VarBuckets#of} et que {@link VarStore#owns}, et pour la
     * même raison qu'eux : trois exemplaires de la règle d'isolation auraient fini par
     * diverger, et la divergence se serait vue comme une valeur envoyée à un joueur qui n'est
     * pas le sien.
     */
    static Mark designate(VarScope scope, VarOwner owner, String name) {
        return switch (scope) {
            case WORLD -> new Mark(scope, null, null, name);
            case GRAPH -> new Mark(scope, null, owner.blueprint(), name);
            case PLAYER_SHARED -> new Mark(scope, owner.player(), null, name);
            case PLAYER -> new Mark(scope, owner.player(), owner.blueprint(), name);
            // Refusée par le validateur, jamais marquée par le magasin : la désignation
            // existe pour que le switch reste exhaustif, pas pour servir.
            case LOCAL -> new Mark(scope, null, null, name);
        };
    }

    /** Vide le carnet et rend ce qu'il contenait, dans l'ordre où les marques sont venues. */
    public synchronized List<Mark> drain() {
        if (marks.isEmpty()) {
            return List.of();
        }
        List<Mark> out = List.copyOf(marks);
        marks.clear();
        return out;
    }

    /** Y a-t-il quelque chose à envoyer ? Lu à chaque fin de tick, donc gardé trivial. */
    public synchronized boolean isEmpty() {
        return marks.isEmpty();
    }

    /** Nombre de marques en attente (diagnostic et tests). */
    public synchronized int size() {
        return marks.size();
    }
}
