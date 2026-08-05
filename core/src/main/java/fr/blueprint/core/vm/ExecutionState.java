package fr.blueprint.core.vm;

import fr.blueprint.core.compile.ir.Ir;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * État d'une exécution en cours : compteur de programme, slots, variables locales.
 * Survit entre deux {@code run} (fuel épuisé, suspension) — sa sérialisation pour
 * traverser un redémarrage est la story 3.4.
 */
public final class ExecutionState {

    private int pc;
    private final Object[] slots;
    private final Map<String, Object> locals = new HashMap<>();
    /** Adresses de retour des sous-chaînes (flux structuré 7.1b) ; persistée. */
    private final Deque<Integer> frames = new ArrayDeque<>();

    /**
     * Types de nœuds et index de pins de l'IR, résolus au premier {@code run}.
     *
     * <p><b>Pas persisté</b> — et il ne doit pas l'être : c'est un cache dérivé du registre
     * courant, qui peut avoir changé entre deux démarrages (mod retiré, datapack rechargé).
     * Une exécution reprise le reconstruit donc contre le registre du moment, ce qui est
     * exactement le comportement voulu pour les nœuds fantômes.
     *
     * <p>Une fois par exécution plutôt qu'une fois par appel de nœud : une exécution longue
     * traverse des milliers d'appels, et l'environnement ne change pas en cours de route.
     */
    private transient @org.jetbrains.annotations.Nullable ResolvedIr resolved;

    ResolvedIr resolvedFor(Ir ir, ExecutionEnvironment env) {
        if (resolved == null) {
            resolved = ResolvedIr.of(ir, env.nodeResolver());
        }
        return resolved;
    }

    /**
     * Tables d'entrées et de sorties <b>prêtées</b> au contexte d'un appel de nœud.
     *
     * <p>La VM en construisait deux neuves par nœud traversé — c'est-à-dire des milliers
     * par seconde sur un serveur actif — alors que leur contenu ne survit jamais à l'appel.
     * Les standards de code (§4) le demandaient déjà : « réutiliser les tampons ».
     *
     * <p><b>Le contexte, lui, reste neuf à chaque appel</b>, et c'est délibéré. La story 2.3
     * (AC5) garantit qu'un mod conservant le contexte dans un champ et le rappelant plus
     * tard lève : cette garantie tient parce que chaque appel a son objet, invalidé à la
     * sortie et jamais revalidé. Mutualiser le contexte lui-même aurait obligé à remplacer
     * cette garde par un jeton de génération — un objet réutilisé redevient valide, et un
     * mod fautif lirait alors les entrées du nœud suivant sans rien remarquer. Les deux
     * tables coûtent l'essentiel de l'allocation ; le contexte coûte peu et paie une
     * garantie. On mutualise donc les tables, pas la garantie.
     *
     * <p>Sûr parce qu'une exécution est strictement séquentielle : un nœud ne peut pas en
     * déclencher un autre dans le même {@code run}. Un {@code signal/emit} passe par
     * l'ordonnanceur, qui lance une exécution <b>distincte</b>, avec son propre état.
     */
    private final Map<String, Object> inputBuffer = new java.util.LinkedHashMap<>();
    private final Map<String, Object> outputBuffer = new java.util.LinkedHashMap<>();

    Map<String, Object> borrowInputs() {
        inputBuffer.clear();
        return inputBuffer;
    }

    Map<String, Object> borrowOutputs() {
        outputBuffer.clear();
        return outputBuffer;
    }

    private ExecutionState(int slotCount) {
        this.slots = new Object[slotCount];
    }

    public static ExecutionState fresh(Ir ir) {
        return new ExecutionState(ir.slotCount());
    }

    /** Reconstruction depuis la persistance (ExecutionNbt, même paquet). */
    static ExecutionState ofSize(int slotCount) {
        return new ExecutionState(slotCount);
    }

    public int slotCount() {
        return slots.length;
    }

    public int pc() {
        return pc;
    }

    void setPc(int pc) {
        this.pc = pc;
    }

    Object[] slots() {
        return slots;
    }

    Map<String, Object> locals() {
        return locals;
    }

    Deque<Integer> frames() {
        return frames;
    }
}
