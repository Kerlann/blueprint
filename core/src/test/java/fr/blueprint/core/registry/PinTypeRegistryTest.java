package fr.blueprint.core.registry;

import com.mojang.serialization.Codec;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registre des types de pins (story 1.2, AC6 et AC7). */
class PinTypeRegistryTest {

    private static Identifier rl(String ns, String path) {
        return Identifier.fromNamespaceAndPath(ns, path);
    }

    private static PinType completeType(String ns, String path) {
        return PinType.builder(rl(ns, path))
                .javaType(String.class)
                .codec(Codec.STRING)
                .streamCodec(ByteBufCodecs.STRING_UTF8)
                .build();
    }

    @Test
    void builtinsRegisterCleanly() {
        var registry = new PinTypeRegistryImpl();
        registry.registerBuiltins();
        assertEquals(16, registry.all().size());
        assertSame(PinTypes.INT, registry.get(rl("blueprint", "int")).orElseThrow());
        assertEquals("blueprint", registry.providerOf(rl("blueprint", "int")).orElseThrow());
    }

    @Test
    void duplicateNamesBothProviders() {
        var registry = new PinTypeRegistryImpl();
        registry.currentProvider("mod_a");
        registry.register(completeType("shared", "mana"));
        registry.currentProvider("mod_b");
        var ex = assertThrows(IllegalStateException.class,
                () -> registry.register(completeType("shared", "mana")));
        assertTrue(ex.getMessage().contains("mod_a"), "doit nommer le premier fournisseur : " + ex.getMessage());
        assertTrue(ex.getMessage().contains("mod_b"), "doit nommer le second fournisseur : " + ex.getMessage());
    }

    @Test
    void frozenRegistryRejectsLateRegistration() {
        var registry = new PinTypeRegistryImpl();
        registry.registerBuiltins();
        registry.freeze();
        assertTrue(registry.isFrozen());
        var ex = assertThrows(IllegalStateException.class,
                () -> registry.register(completeType("late", "type")));
        assertTrue(ex.getMessage().contains("gelé"));
    }

    @Test
    void literalTypeWithoutCodecIsRejectedNamingProvider() {
        var registry = new PinTypeRegistryImpl();
        registry.currentProvider("badmod");
        PinType sansCodec = PinType.builder(rl("badmod", "broken"))
                .javaType(String.class)
                .streamCodec(ByteBufCodecs.STRING_UTF8)
                .build();
        var ex = assertThrows(IllegalStateException.class, () -> registry.register(sansCodec));
        assertTrue(ex.getMessage().contains("badmod"), ex.getMessage());

        PinType sansStream = PinType.builder(rl("badmod", "broken2"))
                .javaType(String.class)
                .codec(Codec.STRING)
                .build();
        assertThrows(IllegalStateException.class, () -> registry.register(sansStream));
    }

    @Test
    void noLiteralTypeNeedsNoCodec() {
        var registry = new PinTypeRegistryImpl();
        registry.currentProvider("mymod");
        PinType ref = PinType.builder(rl("mymod", "live_ref"))
                .javaType(Object.class)
                .noLiteral()
                .build();
        registry.register(ref);
        assertSame(ref, registry.get(rl("mymod", "live_ref")).orElseThrow());
    }

    @Test
    void builderRejectsIncoherentDeclarations() {
        // javaType manquant
        assertThrows(IllegalStateException.class,
                () -> PinType.builder(rl("bad", "no_java_type")).build());
        // exec avec littéral (supportsLiteral par défaut)
        assertThrows(IllegalStateException.class,
                () -> PinType.builder(rl("bad", "exec_literal"))
                        .kind(fr.blueprint.api.pin.PinKind.EXEC)
                        .javaType(Void.class)
                        .build());
        // coercition déclarée deux fois
        assertThrows(IllegalStateException.class,
                () -> PinType.builder(rl("bad", "double_coerce"))
                        .javaType(Double.class)
                        .coerceFrom(PinTypes.INT, v -> v)
                        .coerceFrom(PinTypes.INT, v -> v));
    }

    @Test
    void translationKeyIsDerivedFromId() {
        PinType t = completeType("mymod", "mana");
        assertEquals("blueprint.pin.mymod.mana.name", t.translationKey());
    }
}
