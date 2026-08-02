package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinKind;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * État de la palette : requête, résultats, sélection clavier, et filtre de
 * compatibilité quand elle s'ouvre depuis un lien relâché dans le vide. Pur — le
 * rendu et les entrées vivent dans {@link PalettePopup} et {@code CanvasWidget}.
 *
 * <p>Le filtre est structurel (kind + assignabilité sur les descripteurs) : le nœud
 * n'existe pas encore, {@code canLink} ne peut pas trancher. L'auto-connexion après
 * insertion, elle, repasse par {@code canLink} (source de vérité).
 */
public final class PaletteState {

    public static final int MAX_RESULTS = 8;

    private final NodeSearch search;
    private final Function<Identifier, NodeDescriptor> descriptors;

    private boolean open;
    private String query = "";
    private List<NodeSearch.Entry> results = List.of();
    private int selected;
    private double anchorX;
    private double anchorY;
    private double worldX;
    private double worldY;
    private @Nullable CanvasController.PinRef wireFrom;

    public PaletteState(NodeSearch search, Function<Identifier, NodeDescriptor> descriptors) {
        this.search = search;
        this.descriptors = descriptors;
    }

    /** Ouvre à l'ancre écran donnée ; {@code wireFrom} non nul = filtre par type. */
    public void open(double anchorX, double anchorY, double worldX, double worldY,
                     @Nullable CanvasController.PinRef wireFrom) {
        this.open = true;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.worldX = worldX;
        this.worldY = worldY;
        this.wireFrom = wireFrom;
        this.query = "";
        this.selected = 0;
        refresh();
    }

    public void close() {
        open = false;
        wireFrom = null;
    }

    public boolean isOpen() {
        return open;
    }

    public String query() {
        return query;
    }

    public List<NodeSearch.Entry> results() {
        return results;
    }

    public int selectedIndex() {
        return selected;
    }

    public @Nullable NodeSearch.Entry selectedEntry() {
        return selected >= 0 && selected < results.size() ? results.get(selected) : null;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double worldX() {
        return worldX;
    }

    public double worldY() {
        return worldY;
    }

    public @Nullable CanvasController.PinRef wireFrom() {
        return wireFrom;
    }

    public void type(String text) {
        query += text;
        selected = 0;
        refresh();
    }

    public void backspace() {
        if (!query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            selected = 0;
            refresh();
        }
    }

    public void moveSelection(int delta) {
        if (!results.isEmpty()) {
            selected = Math.clamp(selected + delta, 0, results.size() - 1);
        }
    }

    public void select(int index) {
        if (index >= 0 && index < results.size()) {
            selected = index;
        }
    }

    private void refresh() {
        results = search.search(query, this::compatible, MAX_RESULTS);
        selected = 0;
    }

    /** Sans lien source : tout passe. Sinon : au moins un pin compatible. */
    private boolean compatible(NodeSearch.Entry entry) {
        CanvasController.PinRef from = wireFrom;
        if (from == null) {
            return true;
        }
        NodeDescriptor desc = descriptors.apply(entry.id());
        if (desc == null) {
            return false;
        }
        List<NodeDescriptor.PinDescriptor> candidates = from.output() ? desc.inputs() : desc.outputs();
        for (int i = 0; i < candidates.size(); i++) {
            NodeDescriptor.PinDescriptor pin = candidates.get(i);
            if (pin.kind() != from.kind()) {
                continue;
            }
            if (pin.kind() == PinKind.EXEC) {
                return true;
            }
            boolean assignable = from.output()
                    ? pin.type().isAssignableFrom(from.type())
                    : from.type().isAssignableFrom(pin.type());
            if (assignable) {
                return true;
            }
        }
        return false;
    }
}
