package fr.blueprint.core.graph;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PinTypeRegistryImpl;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Encodage structurel des types (story 1.4, AC6 — reprise QA SER-001). */
class PinTypeNbtTest {

    private static final Function<Identifier, PinType> RESOLVER = buildResolver();

    private static Function<Identifier, PinType> buildResolver() {
        var registry = new PinTypeRegistryImpl();
        registry.registerBuiltins();
        return id -> registry.get(id).orElse(null);
    }

    static Stream<PinType> roundTrips() {
        return Stream.concat(
                PinTypes.builtin().stream(),
                Stream.of(
                        PinTypes.listOf(PinTypes.INT),
                        PinTypes.listOf(PinTypes.STRING),
                        PinTypes.listOf(PinTypes.listOf(PinTypes.INT)),
                        PinTypes.mapOf(PinTypes.STRING, PinTypes.listOf(PinTypes.INT)),
                        PinTypes.generic("T")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTrips")
    void roundTripPreservesIdentity(PinType type) {
        Tag encoded = PinTypeNbt.encode(type);
        // Identité stricte, pas seulement égalité : les caches de PinTypes font foi.
        assertSame(type, PinTypeNbt.decode(encoded, RESOLVER));
    }

    @Test
    void listOfIntAndListOfStringAreDistinct() {
        // Le cœur de SER-001 : même id de conteneur, structures distinctes.
        Tag ints = PinTypeNbt.encode(PinTypes.listOf(PinTypes.INT));
        Tag strings = PinTypeNbt.encode(PinTypes.listOf(PinTypes.STRING));
        assertSame(PinTypes.listOf(PinTypes.INT), PinTypeNbt.decode(ints, RESOLVER));
        assertSame(PinTypes.listOf(PinTypes.STRING), PinTypeNbt.decode(strings, RESOLVER));
    }

    @Test
    void unresolvableTypeDecodesToNull() {
        Tag unknown = net.minecraft.nbt.StringTag.valueOf("absentmod:mana");
        assertNull(PinTypeNbt.decode(unknown, RESOLVER));
        // Un paramétré dont UN argument est irrésoluble est irrésoluble en bloc.
        Tag listOfUnknown = PinTypeNbt.encode(PinTypes.listOf(PinTypes.INT));
        var registryWithoutInt = (Function<Identifier, PinType>) id -> null;
        assertNull(PinTypeNbt.decode(listOfUnknown, registryWithoutInt));
    }

    @Test
    void malformedTagsDecodeToNull() {
        assertNull(PinTypeNbt.decode(null, RESOLVER));
        assertNull(PinTypeNbt.decode(net.minecraft.nbt.StringTag.valueOf("pas un id §§"), RESOLVER));
        assertNull(PinTypeNbt.decode(new net.minecraft.nbt.CompoundTag(), RESOLVER));
    }
}
