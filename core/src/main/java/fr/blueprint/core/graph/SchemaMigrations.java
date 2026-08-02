package fr.blueprint.core.graph;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Migrations de schéma NBT, appliquées en chaîne {@code v(n) → v(n+1)} avant décodage.
 * Chaque migration transforme le NBT brut ; le numéro de schéma est incrémenté par le
 * cadre, pas par la migration. Un schéma plus récent que {@link #CURRENT} est refusé
 * explicitement — jamais de décodage silencieusement faux.
 */
public final class SchemaMigrations {

    public static final int CURRENT = 1;

    /** Instance de production : aucune migration enregistrée tant que le schéma n'a pas bougé. */
    public static final SchemaMigrations DEFAULT = new SchemaMigrations();

    private final Map<Integer, UnaryOperator<CompoundTag>> migrations = new HashMap<>();

    /** Enregistre la migration {@code from → from + 1}. */
    public void register(int from, UnaryOperator<CompoundTag> migration) {
        if (migrations.put(from, migration) != null) {
            throw new IllegalStateException("Migration " + from + " → " + (from + 1) + " déjà enregistrée");
        }
    }

    /**
     * Amène le NBT au schéma courant.
     *
     * @throws IllegalArgumentException si le schéma est plus récent que {@link #CURRENT}
     *         (monde ouvert avec une version antérieure du mod) ou si une migration manque.
     */
    public CompoundTag migrate(CompoundTag root) {
        int schema = root.getIntOr("schema", CURRENT);
        if (schema > CURRENT) {
            throw new IllegalArgumentException("Blueprint au schéma " + schema
                    + " plus récent que cette version du mod (schéma " + CURRENT
                    + ") — mettre à jour Blueprint avant d'ouvrir ce monde");
        }
        CompoundTag current = root;
        while (schema < CURRENT) {
            UnaryOperator<CompoundTag> migration = migrations.get(schema);
            if (migration == null) {
                throw new IllegalArgumentException("Aucune migration du schéma " + schema
                        + " vers " + (schema + 1));
            }
            current = migration.apply(current.copy());
            schema++;
            current.putInt("schema", schema);
        }
        return current;
    }
}
