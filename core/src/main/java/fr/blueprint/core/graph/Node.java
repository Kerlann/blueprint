package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nœud d'un blueprint. Ne porte <b>pas</b> ses pins : ils viennent du type résolu
 * depuis le registre (décision AD3) — c'est ce qui permet à un mod de faire évoluer
 * ses nœuds sans casser les graphes, et ce qui rend le nœud fantôme possible.
 * Le {@code typeId} est conservé même si aucun type ne le résout (principe P4).
 *
 * <p>Mutable uniquement depuis ce paquet : toute modification passe par une
 * {@link EditOperation}.
 */
public final class Node {

    private final UUID uuid;
    private final Identifier typeId;
    private Vec2d position;
    private final Map<String, LiteralValue> literals = new LinkedHashMap<>();
    private CompoundTag config = new CompoundTag();
    // Littéraux dont le type de pin est irrésoluble (mod retiré) : conservés en NBT
    // brut et ré-émis tels quels à l'encodage (P4). Vidés quand le type revient.
    private net.minecraft.nbt.ListTag preservedLiterals = new net.minecraft.nbt.ListTag();

    public Node(UUID uuid, Identifier typeId, Vec2d position) {
        this.uuid = uuid;
        this.typeId = typeId;
        this.position = position;
    }

    public UUID uuid() {
        return uuid;
    }

    public Identifier typeId() {
        return typeId;
    }

    public Vec2d position() {
        return position;
    }

    public Map<String, LiteralValue> literals() {
        return Collections.unmodifiableMap(literals);
    }

    public @Nullable LiteralValue literal(String pin) {
        return literals.get(pin);
    }

    public CompoundTag config() {
        return config.copy();
    }

    // --- mutations réservées aux EditOperation (même paquet) ---

    void moveTo(Vec2d position) {
        this.position = position;
    }

    void setLiteral(String pin, @Nullable LiteralValue value) {
        if (value == null) {
            literals.remove(pin);
        } else {
            literals.put(pin, value);
        }
    }

    void setConfig(CompoundTag config) {
        this.config = config.copy();
    }

    net.minecraft.nbt.ListTag preservedLiterals() {
        return preservedLiterals;
    }

    /** Vrai si le nœud porte des littéraux préservés en brut (P4) — l'export texte doit le signaler. */
    public boolean hasPreservedLiterals() {
        return !preservedLiterals.isEmpty();
    }

    void setPreservedLiterals(net.minecraft.nbt.ListTag preserved) {
        this.preservedLiterals = preserved;
    }

    Node copy() {
        Node n = new Node(uuid, typeId, position);
        n.literals.putAll(literals);
        n.config = config.copy();
        n.preservedLiterals = preservedLiterals.copy();
        return n;
    }

    boolean contentEquals(@Nullable Node other) {
        return other != null && uuid.equals(other.uuid) && typeId.equals(other.typeId)
                && position.equals(other.position) && literals.equals(other.literals)
                && config.equals(other.config)
                && preservedLiterals.equals(other.preservedLiterals);
    }

    @Override
    public String toString() {
        return typeId + "#" + uuid.toString().substring(0, 8);
    }
}
