package fr.blueprint.core;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logique des sous-commandes (story 1.5, AC2, AC5, AC6) — sans serveur. */
class BlueprintManagerTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    @Test
    void createThenDuplicateIsRefused() {
        var manager = new BlueprintManager();
        assertTrue(manager.create(id("porte")).isPresent());
        assertTrue(manager.create(id("porte")).isEmpty(), "doublon refusé (AC2)");
        assertEquals(1, manager.all().size());
    }

    @Test
    void deleteRemovesAndReportsAbsence() {
        var manager = new BlueprintManager();
        manager.create(id("a"));
        assertTrue(manager.delete(id("a")));
        assertFalse(manager.delete(id("a")), "supprimer un absent = faux, pas d'exception");
        assertTrue(manager.all().isEmpty());
    }

    @Test
    void enableDisableLifecycle() {
        // MODEL-001 : enabled est piloté ici, pas par une EditOperation.
        var manager = new BlueprintManager();
        var bp = manager.create(id("x")).orElseThrow();
        assertTrue(bp.enabled(), "activé par défaut (FR20)");
        assertTrue(manager.setEnabled(id("x"), false));
        assertFalse(bp.enabled());
        assertTrue(manager.setEnabled(id("x"), true));
        assertTrue(bp.enabled());
        assertFalse(manager.setEnabled(id("absent"), true), "cible absente = faux");
    }

    @Test
    void getAndAllExposeBlueprints() {
        var manager = new BlueprintManager();
        manager.create(id("a"));
        manager.create(id("b"));
        assertEquals(2, manager.all().size());
        assertTrue(manager.get(id("a")).isPresent());
        assertTrue(manager.get(id("absent")).isEmpty());
        // La désactivation ne supprime pas (FR20).
        manager.setEnabled(id("a"), false);
        assertEquals(2, manager.all().size());
    }
}
