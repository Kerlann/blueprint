package fr.blueprint.core.event;

import fr.blueprint.api.event.EventType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tout événement déclaré doit avoir une SOURCE.
 *
 * <p>Ce test naît d'un vrai défaut : {@code event/signal} a vécu plusieurs stories
 * dans la palette — il se posait, se câblait, se sauvegardait — et <b>rien au monde
 * ne le déclenchait</b>. Aucun test ne pouvait le voir, parce que chacun vérifiait
 * un événement qu'il déclenchait lui-même. Il fallait regarder l'ensemble.
 *
 * <p>La vérification lit les SOURCES : un événement est « branché » si son nom
 * apparaît dans le pont Fabric, dans le pont Blueprint, ou dans la bibliothèque
 * standard (le nœud émetteur du signal). C'est grossier, et c'est exactement ce
 * qu'il faut — un événement qu'on ne mentionne nulle part ne peut pas se déclencher.
 */
class EventCoverageTest {

    /** Les fichiers où un événement peut légitimement être déclenché. */
    private static final List<String> SOURCES = List.of(
            "core/src/main/java/fr/blueprint/core/BlueprintMod.java",
            "core/src/main/java/fr/blueprint/core/event/BlueprintEventBridge.java",
            "core/src/main/java/fr/blueprint/core/nodes/StandardNodes.java",
            "core/src/main/java/fr/blueprint/core/command/BlueprintCommand.java");

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("racine du dépôt introuvable");
        }
        return path;
    }

    private static String allSources() {
        StringBuilder text = new StringBuilder();
        for (String relative : SOURCES) {
            try {
                text.append(Files.readString(repoRoot().resolve(relative), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return text.toString();
    }

    /** Les constantes d'événement de {@link StandardEvents}, par nom de champ. */
    private static List<Field> declaredEvents() {
        List<Field> out = new ArrayList<>();
        for (Field field : StandardEvents.class.getDeclaredFields()) {
            if (field.getType().equals(EventType.class)) {
                out.add(field);
            }
        }
        return out;
    }

    @Test
    void everyDeclaredEventHasASource() {
        String sources = allSources();
        List<Field> events = declaredEvents();
        assertFalse(events.isEmpty(), "aucun événement trouvé : la réflexion est cassée");

        List<String> dead = new ArrayList<>();
        for (Field event : events) {
            if (!sources.contains("StandardEvents." + event.getName())) {
                dead.add(event.getName());
            }
        }
        assertTrue(dead.isEmpty(), """
                Événement(s) déclaré(s) que RIEN ne déclenche : %s
                Un point d'entrée mort se pose et se câble dans l'éditeur sans jamais \
                s'exécuter — c'est arrivé à SIGNAL, qui a vécu ainsi plusieurs stories.
                Soit tu ajoutes son pont dans BlueprintMod, soit tu retires l'événement.\
                """.formatted(dead));
    }

    /** Tout événement déclaré doit aussi être ENREGISTRÉ, sinon aucun nœud n'existe. */
    @Test
    void everyDeclaredEventIsRegistered() {
        String declaration;
        try {
            declaration = Files.readString(repoRoot().resolve(
                    "core/src/main/java/fr/blueprint/core/event/StandardEvents.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> unregistered = new ArrayList<>();
        for (Field event : declaredEvents()) {
            if (!declaration.contains("registry.register(" + event.getName() + ")")) {
                unregistered.add(event.getName());
            }
        }
        assertTrue(unregistered.isEmpty(),
                "déclarés mais jamais enregistrés (donc aucun nœud) : " + unregistered);
    }

    /**
     * Les deux événements à ENTRÉE ({@code command}, {@code signal}) portent un
     * littéral de filtre et sont enregistrés à la main. Si la synthèse les reprenait,
     * ils perdraient leur entrée et tout signal réveillerait tous les nœuds signal.
     */
    @Test
    void theFilteredEventsKeepTheirHandWrittenNode() {
        var registries = fr.blueprint.core.registry.PluginLoader.load(List.of(), true);
        for (EventType event : List.of(StandardEvents.COMMAND, StandardEvents.SIGNAL)) {
            var type = registries.nodes().get(event.id()).orElseThrow(
                    () -> new AssertionError("nœud absent pour " + event.id()));
            assertTrue(type.entryPoint(), event.id() + " doit rester un point d'entrée");
            assertTrue(type.inputs().stream().anyMatch(pin -> pin.name().equals("name")),
                    event.id() + " a perdu son entrée « name » : le filtrage par nom "
                            + "est mort, et tous les nœuds répondront à tout");
        }
    }
}
