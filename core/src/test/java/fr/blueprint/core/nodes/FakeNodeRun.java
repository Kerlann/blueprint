package fr.blueprint.core.nodes;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.vm.NodeContextImpl;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Exécute un nœud <b>pur</b> hors VM, pour les tests. Extrait de
 * {@code StandardNodesTest}, qui l'avait inventé le premier : deux copies de ce
 * harnais divergeraient sur le contexte factice, et un test passerait pour une
 * raison que l'autre ignore.
 *
 * <p>Serveur et niveau sont nuls — un nœud qui les touche n'est pas pur et ne se
 * teste pas ici mais en gametest.
 */
public final class FakeNodeRun {

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("test", "harness");
        }

        @Override
        public boolean enabled() {
            return true;
        }
    };

    private static final TriggerContext TRIGGER = new TriggerContext() {
        @Override
        public Identifier eventId() {
            return Identifier.fromNamespaceAndPath("test", "manual");
        }

        @Override
        public Object output(String name) {
            return null;
        }
    };

    private FakeNodeRun() {
    }

    /** Le contexte après exécution : sorties, branche empruntée, faute éventuelle. */
    public static NodeContextImpl invoke(NodeType type, Map<String, Object> inputs) {
        try {
            return NodeContextImpl.invoke(type, new NodeContextImpl(type, inputs, null, null,
                    HANDLE, TRIGGER, LoggerFactory.getLogger("blueprint-test")));
        } catch (Exception e) {
            throw new IllegalStateException("échec d'exécution de " + type.id(), e);
        }
    }

    /** Les seules sorties, quand c'est tout ce qui intéresse le test. */
    public static Map<String, Object> run(NodeType type, Map<String, Object> inputs) {
        return invoke(type, inputs).outputs();
    }
}
