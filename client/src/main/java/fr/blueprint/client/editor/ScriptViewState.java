package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.script.ScriptGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * État de la vue script (story 5.11) : le BScript régénéré débouncé après chaque
 * mutation, la correspondance nœud↔ligne reconstruite depuis les annotations
 * {@code @id(uuid)} du texte — pur, testé sans Minecraft. v1.0 : lecture seule.
 */
public final class ScriptViewState {

    public static final long DEBOUNCE_MS = 300;

    /** Classification d'une ligne pour la coloration minimale. */
    public enum LineKind { EVENT, VARIABLE, COMMENT, PLAIN }

    /** Le générateur émet {@code @id("uuid")} — guillemets optionnels tolérés. */
    private static final Pattern ID_ANNOTATION = Pattern.compile(
            "@id\\(\"?([0-9a-fA-F-]{36})\"?\\)");

    private final LongSupplier clock;
    private boolean visible;
    private List<String> lines = List.of();
    private List<String> issues = List.of();
    private final Map<UUID, Integer> lineOfNode = new HashMap<>();
    private final List<@Nullable UUID> nodeOfLine = new ArrayList<>();
    private long dirtySince;
    private int scroll;
    private int highlighted = -1;
    private @Nullable UUID lastSynced;
    /** Import armé : premier clic avertit, second remplace (U2). */
    private boolean importArmed;

    public ScriptViewState(LongSupplier clock) {
        this.clock = clock;
        this.dirtySince = clock.getAsLong();
    }

    public boolean visible() {
        return visible;
    }

    public void toggle() {
        visible = !visible;
        importArmed = false;
        if (visible) {
            dirtySince = clock.getAsLong() - DEBOUNCE_MS; // régénérer tout de suite
        }
    }

    public void invalidate() {
        dirtySince = clock.getAsLong();
    }

    public boolean shouldRegenerate() {
        return visible && dirtySince >= 0 && clock.getAsLong() - dirtySince >= DEBOUNCE_MS;
    }

    /** Régénère le texte et reconstruit la correspondance nœud↔ligne. */
    public void regenerate(Blueprint bp, NodeTypeLookup lookup) {
        ScriptGenerator.Result result = ScriptGenerator.generate(bp, lookup);
        lines = List.of(result.text().split("\n", -1));
        issues = List.copyOf(result.issues());
        lineOfNode.clear();
        nodeOfLine.clear();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = ID_ANNOTATION.matcher(lines.get(i));
            UUID found = null;
            if (matcher.find()) {
                found = UUID.fromString(matcher.group(1));
                lineOfNode.putIfAbsent(found, i);
            }
            nodeOfLine.add(found);
        }
        dirtySince = -1;
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - 1));
        // Les indices de lignes viennent de changer : forcer la resynchronisation du
        // surlignage au prochain rendu (QA 5.11 — surlignage périmé sinon).
        lastSynced = null;
        highlighted = -1;
    }

    public List<String> lines() {
        return lines;
    }

    public List<String> issues() {
        return issues;
    }

    public int scroll() {
        return scroll;
    }

    public void scrollBy(int delta, int visibleLines) {
        scroll = Math.clamp(scroll + delta, 0, Math.max(0, lines.size() - visibleLines));
    }

    public int highlightedLine() {
        return highlighted;
    }

    /** Sélection canevas → surligne et fait défiler vers le bloc du nœud. */
    public void syncSelection(@Nullable UUID selected, int visibleLines) {
        if (java.util.Objects.equals(selected, lastSynced)) {
            return;
        }
        lastSynced = selected;
        Integer line = selected == null ? null : lineOfNode.get(selected);
        highlighted = line == null ? -1 : line;
        if (line != null && (line < scroll || line >= scroll + visibleLines)) {
            scroll = Math.clamp(line - visibleLines / 2, 0,
                    Math.max(0, lines.size() - visibleLines));
        }
    }

    /** Clic sur une ligne → le nœud de l'annotation la plus proche au-dessus. */
    public @Nullable UUID nodeAtLine(int line) {
        for (int i = Math.min(line, nodeOfLine.size() - 1); i >= 0; i--) {
            if (nodeOfLine.get(i) != null) {
                return nodeOfLine.get(i);
            }
        }
        return null;
    }

    public String fullText() {
        return String.join("\n", lines);
    }

    /** Coloration minimale par ligne (UX §9). */
    public static LineKind kindOf(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
            return LineKind.COMMENT;
        }
        if (trimmed.startsWith("on ")) {
            return LineKind.EVENT;
        }
        if (trimmed.startsWith("var ")) {
            return LineKind.VARIABLE;
        }
        return LineKind.PLAIN;
    }

    // ------------------------------------------------------------------- import

    public boolean importArmed() {
        return importArmed;
    }

    /** Premier appel : arme la confirmation ; second : l'appelant remplace. */
    public boolean armImport() {
        if (importArmed) {
            importArmed = false;
            return true;
        }
        importArmed = true;
        return false;
    }

    public void disarmImport() {
        importArmed = false;
    }
}
