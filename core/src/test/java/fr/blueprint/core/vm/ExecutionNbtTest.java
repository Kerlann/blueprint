package fr.blueprint.core.vm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Persistance d'une exécution suspendue (story 3.4, AC1-AC3). */
class ExecutionNbtTest {

    private static SuspendedExecution sample() {
        ExecutionState state = ExecutionState.ofSize(9);
        state.setPc(7);
        state.slots()[0] = null;
        state.slots()[1] = 42;
        state.slots()[2] = "château";
        state.slots()[3] = true;
        state.slots()[4] = Identifier.fromNamespaceAndPath("mymod", "clef");
        state.slots()[5] = new BlockPos(1, -60, 300);
        state.slots()[6] = new Vec3(0.5, 80.0, -2.25);
        state.slots()[7] = Direction.UP;
        state.slots()[8] = List.of(1, 2, 3);
        state.locals().put("compteur", 9L);
        return new SuspendedExecution(Identifier.fromNamespaceAndPath("test", "graph"), 5, 20,
                state, Identifier.fromNamespaceAndPath("test", "event"),
                Map.of("uuid", UUID.fromString("00000000-0000-0000-0000-000000000042")));
    }

    @Test
    void fullStateRoundTrips() {
        SuspendedExecution original = sample();
        CompoundTag encoded = ExecutionNbt.encode(original);
        assertNotNull(encoded);
        SuspendedExecution decoded = ExecutionNbt.decode(encoded, RefResolver.NONE);
        assertNotNull(decoded);

        assertEquals(original.blueprintId(), decoded.blueprintId());
        assertEquals(original.revision(), decoded.revision());
        assertEquals(original.remainingTicks(), decoded.remainingTicks());
        assertEquals(original.eventId(), decoded.eventId());
        assertEquals(original.state().pc(), decoded.state().pc());
        assertArrayEquals(original.state().slots(), decoded.state().slots());
        assertEquals(original.state().locals(), decoded.state().locals());
        assertEquals(original.triggerValues(), decoded.triggerValues());
    }

    @Test
    void unresolvableReferenceCancelsCleanly() {
        // AC3 : une référence vivante (joueur) irrésoluble → décodage null, jamais un trou.
        CompoundTag encoded = ExecutionNbt.encode(sample());
        CompoundTag playerRef = new CompoundTag();
        playerRef.putString("k", "player");
        playerRef.putString("v", UUID.randomUUID().toString());
        ((ListTag) encoded.get("slots")).set(1, playerRef);

        assertNull(ExecutionNbt.decode(encoded, RefResolver.NONE),
                "joueur absent = exécution annulée proprement");
    }

    @Test
    void unsupportedValueRefusesToPersistEntirely() {
        ExecutionState state = ExecutionState.ofSize(1);
        state.slots()[0] = new Object();   // non sérialisable
        var suspended = new SuspendedExecution(Identifier.fromNamespaceAndPath("test", "bad"),
                0, 10, state, Identifier.fromNamespaceAndPath("test", "event"), Map.of());
        assertNull(ExecutionNbt.encode(suspended),
                "jamais de sauvegarde partielle : tout ou rien");
    }

    @Test
    void malformedRootDecodesToNull() {
        assertNull(ExecutionNbt.decode(new CompoundTag(), RefResolver.NONE));
    }
}
