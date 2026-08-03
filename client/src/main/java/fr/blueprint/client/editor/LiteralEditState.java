package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Édition en place d'une valeur littérale (story 5.2b). Pur et testé headless : le
 * widget ne fait que dessiner le champ et router les frappes. Deux modes : champ
 * texte (nombres, chaînes, identifiants) et énumération (direction). Le booléen n'a
 * pas de mode : il bascule au clic.
 *
 * <p>{@link #parse()} construit le {@code LiteralValue} typé ou null si la saisie
 * est invalide — {@code SetLiteral} revalide de toute façon côté modèle : aucune
 * règle n'est dupliquée, seul le feedback (champ rouge) vit ici.
 */
public final class LiteralEditState {

    public enum Mode { NONE, TEXT, ENUM }

    private Mode mode = Mode.NONE;
    private UUID node;
    private String pin = "";
    private PinType type = PinTypes.STRING;
    private int row;
    private String buffer = "";
    private List<Direction> options = List.of();
    private int optionIndex;

    // ------------------------------------------------------------------ ouverture

    /** Un littéral de ce type s'édite-t-il en place ? (bool = bascule directe) */
    public static boolean editableAsText(PinType type) {
        return type == PinTypes.INT || type == PinTypes.LONG || type == PinTypes.DOUBLE
                || type == PinTypes.STRING || type == PinTypes.TEXT
                || type == PinTypes.RESOURCE_LOCATION
                || type == PinTypes.VEC3 || type == PinTypes.BLOCKPOS;
    }

    /** Remplit le tampon (bouton « position du joueur », Ctrl+P — story 5.2c). */
    public void setText(String text) {
        if (mode == Mode.TEXT) {
            buffer = text;
        }
    }

    public void openText(UUID node, String pin, int row, PinType type, String initial) {
        this.mode = Mode.TEXT;
        this.node = node;
        this.pin = pin;
        this.row = row;
        this.type = type;
        this.buffer = initial;
    }

    public void openEnum(UUID node, String pin, int row, @Nullable Direction current) {
        this.mode = Mode.ENUM;
        this.node = node;
        this.pin = pin;
        this.row = row;
        this.type = PinTypes.DIRECTION;
        this.options = List.of(Direction.values());
        this.optionIndex = current == null ? 0 : Math.max(0, options.indexOf(current));
    }

    public void close() {
        mode = Mode.NONE;
        buffer = "";
    }

    // ----------------------------------------------------------------- accesseurs

    public boolean isOpen() {
        return mode != Mode.NONE;
    }

    public Mode mode() {
        return mode;
    }

    public UUID node() {
        return node;
    }

    public String pin() {
        return pin;
    }

    public int row() {
        return row;
    }

    public PinType type() {
        return type;
    }

    public String text() {
        return buffer;
    }

    public List<Direction> options() {
        return options;
    }

    public int optionIndex() {
        return optionIndex;
    }

    // --------------------------------------------------------------------- saisie

    public void type(String text) {
        if (mode == Mode.TEXT) {
            buffer += text;
        }
    }

    public void backspace() {
        if (mode == Mode.TEXT && !buffer.isEmpty()) {
            buffer = buffer.substring(0, buffer.length() - 1);
        }
    }

    public void moveOption(int delta) {
        if (mode == Mode.ENUM && !options.isEmpty()) {
            optionIndex = Math.floorMod(optionIndex + delta, options.size());
        }
    }

    public void selectOption(int index) {
        if (mode == Mode.ENUM && index >= 0 && index < options.size()) {
            optionIndex = index;
        }
    }

    /** Molette sur un champ numérique : ±delta (le widget passe ±1 ou ±10). */
    public void adjustNumber(long delta) {
        if (mode != Mode.TEXT) {
            return;
        }
        if (type == PinTypes.INT || type == PinTypes.LONG) {
            try {
                long next = Long.parseLong(buffer.trim()) + delta;
                // La molette compte en long : sur un pin int, elle poussait le tampon
                // au-delà d'Integer.MAX_VALUE et laissait le champ rouge, sans que
                // rien n'indique qu'il fallait retaper la valeur à la main.
                if (type == PinTypes.INT) {
                    next = Math.clamp(next, Integer.MIN_VALUE, Integer.MAX_VALUE);
                }
                buffer = String.valueOf(next);
            } catch (NumberFormatException e) {
                buffer = String.valueOf(delta);
            }
        } else if (type == PinTypes.DOUBLE) {
            try {
                double v = Double.parseDouble(buffer.trim()) + delta;
                buffer = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
            } catch (NumberFormatException e) {
                buffer = String.valueOf(delta);
            }
        }
    }

    // --------------------------------------------------------------------- valeur

    /** La valeur typée de la saisie courante, ou null si invalide (champ rouge). */
    public @Nullable LiteralValue parse() {
        try {
            if (mode == Mode.ENUM) {
                return LiteralValue.of(PinTypes.DIRECTION, options.get(optionIndex));
            }
            if (type == PinTypes.INT) {
                return LiteralValue.of(type, Integer.parseInt(buffer.trim()));
            }
            if (type == PinTypes.LONG) {
                return LiteralValue.of(type, Long.parseLong(buffer.trim()));
            }
            if (type == PinTypes.DOUBLE) {
                return LiteralValue.of(type, Double.parseDouble(buffer.trim()));
            }
            if (type == PinTypes.STRING) {
                return LiteralValue.of(type, buffer);
            }
            if (type == PinTypes.TEXT) {
                return LiteralValue.of(type, Component.literal(buffer));
            }
            if (type == PinTypes.RESOURCE_LOCATION) {
                Identifier id = Identifier.tryParse(buffer.trim());
                return id == null ? null : LiteralValue.of(type, id);
            }
            if (type == PinTypes.VEC3) {
                double[] v = threeNumbers(buffer);
                return v == null ? null : LiteralValue.of(type,
                        new net.minecraft.world.phys.Vec3(v[0], v[1], v[2]));
            }
            if (type == PinTypes.BLOCKPOS) {
                double[] v = threeNumbers(buffer);
                return v == null ? null : LiteralValue.of(type,
                        new net.minecraft.core.BlockPos((int) Math.floor(v[0]),
                                (int) Math.floor(v[1]), (int) Math.floor(v[2])));
            }
            return null;
        } catch (RuntimeException e) {
            return null; // nombre malformé, valeur hors type : champ rouge, rien d'appliqué
        }
    }

    public boolean isValid() {
        return parse() != null;
    }

    /** « x y z » (espaces, virgules ou point-virgules), ou null si illisible. */
    private static double @Nullable [] threeNumbers(String text) {
        String[] parts = text.trim().split("[,;\\s]+");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ affichage

    /** Texte affiché sur le nœud pour une valeur (ou son défaut), tronqué au rendu. */
    public static String display(PinType type, @Nullable LiteralValue value) {
        Object v = value == null ? null : value.value();
        if (v == null) {
            return "";
        }
        if (v instanceof Component c) {
            return c.getString();
        }
        if (v instanceof Direction d) {
            return d.getName();
        }
        if (v instanceof net.minecraft.world.phys.Vec3 vec) {
            return trim(vec.x) + " " + trim(vec.y) + " " + trim(vec.z);
        }
        if (v instanceof net.minecraft.core.BlockPos pos) {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }
        if (v instanceof net.minecraft.world.item.ItemStack stack) {
            return stack.isEmpty() ? "" : stack.getHoverName().getString();
        }
        if (v instanceof net.minecraft.world.level.block.state.BlockState state) {
            return state.getBlock().getName().getString();
        }
        if (v instanceof Double d && d == Math.rint(d) && !d.isInfinite()) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(v).toLowerCase(Locale.ROOT).equals("null") ? "" : String.valueOf(v);
    }

    private static String trim(double v) {
        return v == Math.rint(v) && !Double.isInfinite(v)
                ? String.valueOf((long) v) : String.valueOf(v);
    }
}
