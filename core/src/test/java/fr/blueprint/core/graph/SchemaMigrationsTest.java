package fr.blueprint.core.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Migrations de schéma (story 1.4, AC2). */
class SchemaMigrationsTest {

    @Test
    void currentSchemaPassesThrough() {
        CompoundTag root = new CompoundTag();
        root.putInt("schema", SchemaMigrations.CURRENT);
        root.putString("marqueur", "intact");
        CompoundTag migrated = SchemaMigrations.DEFAULT.migrate(root);
        assertEquals("intact", migrated.getStringOr("marqueur", ""));
    }

    @Test
    void chainedMigrationsAreApplied() {
        // Migration factice v0 → v1 : renomme un champ, comme le ferait une vraie.
        var migrations = new SchemaMigrations();
        migrations.register(0, tag -> {
            tag.putString("id", tag.getStringOr("ancien_id", ""));
            tag.remove("ancien_id");
            return tag;
        });
        CompoundTag v0 = new CompoundTag();
        v0.putInt("schema", 0);
        v0.putString("ancien_id", "test:migre");

        CompoundTag migrated = migrations.migrate(v0);
        assertEquals(SchemaMigrations.CURRENT, migrated.getIntOr("schema", -1));
        assertEquals("test:migre", migrated.getStringOr("id", ""));
        assertEquals("", migrated.getStringOr("ancien_id", ""));
        // L'original n'est pas muté (migration sur copie).
        assertEquals("test:migre", v0.getStringOr("ancien_id", ""));
    }

    @Test
    void newerSchemaIsRefusedExplicitly() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema", SchemaMigrations.CURRENT + 1);
        var ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaMigrations.DEFAULT.migrate(future));
        assertTrue(ex.getMessage().contains("plus récent"));
    }

    @Test
    void missingMigrationFailsExplicitly() {
        CompoundTag old = new CompoundTag();
        old.putInt("schema", 0);
        assertThrows(IllegalArgumentException.class, () -> SchemaMigrations.DEFAULT.migrate(old));
    }

    @Test
    void decodeAppliesMigrationsBeforeReading() {
        var migrations = new SchemaMigrations();
        migrations.register(0, tag -> {
            tag.putString("id", "test:issu_de_migration");
            return tag;
        });
        CompoundTag v0 = new CompoundTag();
        v0.putInt("schema", 0);
        Blueprint bp = GraphNbt.decode(v0, (Function<net.minecraft.resources.Identifier,
                fr.blueprint.api.pin.PinType>) id -> null, migrations);
        assertEquals("test:issu_de_migration", bp.id().toString());
    }
}
