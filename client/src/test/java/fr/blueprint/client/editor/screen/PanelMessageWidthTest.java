package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.ElementKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un message qui ne tient pas dans son panneau est un message qu'on ne lit pas.
 *
 * <p>« No named style on this screen yet » se peignait « No named style on this scr », et
 * « Nothing placed — pick an element above » n'a jamais montré que son début. Ces deux-là
 * ne sont pas des libellés à côté d'une valeur, qu'on tronque sciemment pour garder la
 * valeur lisible : ce sont des <b>phrases</b>, seules sur leur rangée, dont la fin porte le
 * sens. « Rien de posé — » ne dit pas quoi faire.
 *
 * <p>Le contrôle porte sur ce qui s'énumère : les familles de la palette, les douze types,
 * les sections du panneau. Un treizième type ou une neuvième section entre donc dans le
 * test sans que personne n'y pense — c'est tout l'intérêt de partir des énumérations
 * plutôt que d'une liste écrite à la main.
 */
class PanelMessageWidthTest {

    /**
     * Largeur estimée d'un caractère, la même que celle du panneau.
     *
     * <p>Volontairement <b>optimiste</b> : la vraie police est proportionnelle et « il »
     * est plus étroit que « MM ». Un test qui surestime signalerait des messages qui
     * tiennent, on apprendrait à l'ignorer, et il ne servirait plus à rien. Celui-ci ne
     * rougit que sur ce qui dépasse à coup sûr.
     */
    private static final int CHAR = ElementPropertiesState.CHAR_WIDTH;

    /** La place d'une phrase seule sur sa rangée, dans chaque panneau. */
    private static final int PALETTE_ROOM = DesignerPanels.PALETTE_WIDTH - 8;
    private static final int PROPERTIES_ROOM = DesignerPanels.PROPERTIES_WIDTH - 8;

    /** Un type porte son pictogramme à gauche : seize pixels lui sont pris. */
    private static final int PALETTE_ELEMENT_ROOM = DesignerPanels.PALETTE_WIDTH - 20;

    /**
     * <b>Les phrases des panneaux tiennent dans leur panneau.</b>
     *
     * <p>Chaque entrée dit où le message se peint, donc combien de place il a. Les deux
     * langues sont vérifiées : une traduction plus longue que l'original est le cas le
     * plus fréquent, et c'est précisément celui qu'une relecture en anglais ne voit pas.
     */
    @Test
    void lesPhrasesDesPanneauxTiennentDansLeurPanneau() {
        Map<String, Integer> room = new LinkedHashMap<>();

        // La palette : trois familles, douze types, et les deux messages de vide.
        for (DesignerPalette.Group group : DesignerPalette.Group.values()) {
            room.put(group.key(), PALETTE_ROOM);
        }
        for (ElementKind kind : ElementKind.values()) {
            room.put("blueprint.designer.kind." + kind.name().toLowerCase(java.util.Locale.ROOT),
                    PALETTE_ELEMENT_ROOM);
        }
        room.put("blueprint.designer.screens", PALETTE_ROOM);
        room.put("blueprint.designer.elements", PALETTE_ROOM);
        room.put("blueprint.designer.layers", PALETTE_ROOM);
        room.put("blueprint.designer.screens.empty", PALETTE_ROOM);
        room.put("blueprint.designer.layers.empty", PALETTE_ROOM);

        // Le panneau de propriétés : les en-têtes de section et les phrases qui y vivent.
        for (ElementPropertiesState.Section section : ElementPropertiesState.Section.values()) {
            // Deux caractères de plus pour le chevron « ▾ » et son espace.
            room.put(section.key(), PROPERTIES_ROOM - 2 * CHAR);
        }
        room.put("blueprint.designer.styles.none", PROPERTIES_ROOM);
        room.put("blueprint.designer.styles.create", PROPERTIES_ROOM);

        List<String> offenders = new ArrayList<>();
        for (String locale : List.of("en_us", "fr_fr")) {
            Map<String, String> lang = lang(locale);
            for (Map.Entry<String, Integer> entry : room.entrySet()) {
                String value = lang.get(entry.getKey());
                if (value == null) {
                    continue;   // l'absence est le sujet du contrôle des clés mortes
                }
                int width = value.length() * CHAR;
                if (width > entry.getValue()) {
                    offenders.add(locale + " · " + entry.getKey() + " = « " + value
                            + " » : " + width + " px pour " + entry.getValue() + " de place");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "ces messages se peignent coupés au milieu d'un mot :\n  "
                        + String.join("\n  ", offenders));
    }

    private static Map<String, String> lang(String locale) {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("racine du dépôt introuvable");
        }
        Path file = path.resolve("core/src/main/resources/assets/blueprint/lang")
                .resolve(locale + ".json");
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Un analyseur au strict minimum : le client n'a pas Gson en test, et l'ajouter
        // pour lire des paires plates coûterait plus que ces trois lignes.
        Map<String, String> out = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(text);
        while (matcher.find()) {
            out.put(matcher.group(1), matcher.group(2).replace("\\\"", "\""));
        }
        return out;
    }
}
