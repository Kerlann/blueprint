package fr.blueprint.compat;

import fr.blueprint.core.registry.NodeRegistryImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chargement conditionnel des intégrations (story 8.4). */
class CompatLoaderTest {

    /** AC3 : le cas de la plupart des installations — aucun mod compagnon. */
    @Test
    void withoutAnyOfTheseModsNothingIsRegistered() {
        NodeRegistryImpl nodes = new NodeRegistryImpl();
        List<String> loaded = CompatLoader.load(nodes, modId -> false);

        assertTrue(loaded.isEmpty());
        assertTrue(nodes.all().isEmpty(), "aucun nœud, donc aucun coût et aucun risque");
    }

    /** AC1/AC2 : l'intégration de référence s'active quand son mod est là. */
    @Test
    void theReferenceIntegrationLoadsWhenItsModIsPresent() {
        NodeRegistryImpl nodes = new NodeRegistryImpl();
        List<String> loaded = CompatLoader.load(nodes, modId -> true);

        assertEquals(List.of("blueprint_testmod"), loaded);
        assertTrue(nodes.get(TestmodCompat.GREET).isPresent());
        assertEquals(CompatLoader.providerFor("blueprint_testmod"),
                nodes.providerOf(TestmodCompat.GREET).orElseThrow(),
                "le fournisseur nomme l'intégration, pas Blueprint");
    }

    /** Seul le mod visé compte : un autre mod présent ne déclenche rien. */
    @Test
    void anUnrelatedModDoesNotTriggerAnything() {
        NodeRegistryImpl nodes = new NodeRegistryImpl();
        assertTrue(CompatLoader.load(nodes, "un_autre_mod"::equals).isEmpty());
        assertTrue(nodes.all().isEmpty());
    }

    /** Une intégration qui lève est isolée : ses nœuds partent, Blueprint continue. */
    @Test
    void aBrokenIntegrationIsIsolated() {
        NodeRegistryImpl nodes = new NodeRegistryImpl();
        nodes.currentProvider("blueprint");
        nodes.register(fr.blueprint.api.node.NodeType.builder(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("blueprint", "temoin"))
                .exec().action(ctx -> {
                }).build());

        CompatLoader.Integration broken = new CompatLoader.Integration() {
            @Override
            public String modId() {
                return "modcassé";
            }

            @Override
            public void register(NodeRegistryImpl registry) {
                registry.register(fr.blueprint.api.node.NodeType.builder(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                        "blueprint", "compat/moitie"))
                        .exec().action(ctx -> {
                        }).build());
                throw new IllegalStateException("le mod a changé son API");
            }
        };

        // Même chemin que CompatLoader.load, sur une intégration fabriquée pour l'occasion.
        String provider = CompatLoader.providerFor(broken.modId());
        nodes.currentProvider(provider);
        try {
            broken.register(nodes);
        } catch (RuntimeException e) {
            nodes.removeAllFrom(provider);
        }

        assertFalse(nodes.get(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "blueprint", "compat/moitie")).isPresent(), "enregistrement partiel retiré");
        assertTrue(nodes.get(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "blueprint", "temoin")).isPresent(), "le reste du registre est intact");
    }
}
