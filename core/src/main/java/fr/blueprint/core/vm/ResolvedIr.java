package fr.blueprint.core.vm;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.compile.ir.Ir;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Ce qu'on peut résoudre <b>une fois</b> pour une IR, plutôt qu'à chaque appel de nœud.
 *
 * <p>Deux choses vivaient dans la boucle chaude de la VM sans avoir de raison d'y être :
 *
 * <ul>
 *   <li><b>La résolution du type</b> — {@code env.nodeResolver().apply(call.type())}
 *       traversait une table et allouait un {@code Optional} à chaque appel de nœud, pour
 *       un identifiant qui ne change jamais de l'IR.</li>
 *   <li><b>La recherche d'un pin par son nom</b> — chaque {@code ctx.in("...")} parcourait
 *       la liste des pins en comparant des chaînes. Un nœud à six pins qui lit quatre
 *       entrées faisait jusqu'à vingt {@code String.equals} par exécution.</li>
 * </ul>
 *
 * <p>Les deux se calculent depuis l'IR seule, qui est immuable et mise en cache par
 * révision : ce travail est donc fait une fois par blueprint édité, et non des milliers de
 * fois par seconde.
 *
 * <h2>Pourquoi ici et non dans {@code NodeType}</h2>
 *
 * <p>L'index des pins aurait sa place naturelle sur {@link NodeType} — mais l'y mettre
 * demanderait un accesseur <b>public</b>, donc une modification de la surface publique de
 * l'api, figée dans {@code docs/api-surface.txt} et comparée à chaque construction. Une
 * optimisation interne n'a pas à élargir un contrat offert aux mods tiers ; elle vit donc
 * du côté qui en a besoin.
 *
 * <h2>Nœuds fantômes</h2>
 *
 * <p>Un type irrésoluble (mod retiré) donne une entrée <b>nulle</b>, jamais une exception :
 * la VM produit alors la même faute nommée qu'avant, au moment où elle atteint le nœud.
 * Résoudre à l'avance ne doit pas avancer l'échec.
 */
public final class ResolvedIr {

    /** Les pins d'un type, indexés par nom. Immuable, partagé entre les IR. */
    record PinIndex(Map<String, NodeType.PinSpec> inputs, Map<String, NodeType.PinSpec> outputs) {

        static PinIndex of(NodeType type) {
            return new PinIndex(byName(type.inputs()), byName(type.outputs()));
        }

        private static Map<String, NodeType.PinSpec> byName(java.util.List<NodeType.PinSpec> pins) {
            // Les noms sont uniques — le builder de NodeType le vérifie.
            Map<String, NodeType.PinSpec> index = new HashMap<>(Math.max(4, pins.size() * 2));
            for (NodeType.PinSpec pin : pins) {
                index.put(pin.name(), pin);
            }
            return Map.copyOf(index);
        }
    }

    /**
     * Index des types déjà vus, pour que deux IR partageant un type partagent son index.
     *
     * <p>Faible sur la clé : un rechargement de datapack remplace les {@code NodeType}, et
     * garder les anciens vivants par ce seul cache empêcherait de les collecter.
     */
    private static final Map<NodeType, PinIndex> PIN_INDEXES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private final NodeType[] types;
    private final PinIndex[] pins;

    private ResolvedIr(NodeType[] types, PinIndex[] pins) {
        this.types = types;
        this.pins = pins;
    }

    /** Résout tous les {@code Call} d'une IR ; les autres instructions n'ont pas de type. */
    public static ResolvedIr of(Ir ir, java.util.function.Function<
            net.minecraft.resources.Identifier, NodeType> resolver) {
        int size = ir.instructions().size();
        NodeType[] types = new NodeType[size];
        PinIndex[] pins = new PinIndex[size];
        for (int i = 0; i < size; i++) {
            if (ir.instructions().get(i) instanceof Instruction.Call call) {
                NodeType type = resolver.apply(call.type());
                types[i] = type;
                if (type != null) {
                    pins[i] = PIN_INDEXES.computeIfAbsent(type, PinIndex::of);
                }
            }
        }
        return new ResolvedIr(types, pins);
    }

    /** Le type du {@code Call} à cette adresse, ou {@code null} — mod retiré, ou pas un Call. */
    public @Nullable NodeType typeAt(int pc) {
        return pc >= 0 && pc < types.length ? types[pc] : null;
    }

    /** L'index des pins du {@code Call} à cette adresse, ou {@code null}. */
    @Nullable PinIndex pinsAt(int pc) {
        return pc >= 0 && pc < pins.length ? pins[pc] : null;
    }
}
