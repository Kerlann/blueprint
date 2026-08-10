package fr.blueprint.core.net;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.vm.VarValueNbt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le canal des valeurs répliquées (épic 21, story 21.2) : ce qui voyage, ce qui est refusé,
 * et ce qui arrive intact.
 *
 * <p>Le paquet <b>n'a pas encore d'émetteur</b> : la story 21.4 le lui donnera. Ce qui se
 * vérifie ici est donc le contrat du fil, pas le flux — mais le vérifier maintenant est ce
 * qui permet aux stories suivantes de s'appuyer dessus au lieu de le découvrir.
 */
class VarValuesWireTest {

    private static BlueprintPayloads.VarValues roundTrip(BlueprintPayloads.VarValues sent) {
        ByteBuf buffer = Unpooled.buffer();
        BlueprintPayloads.VarValues.CODEC.encode(buffer, sent);
        var received = BlueprintPayloads.VarValues.CODEC.decode(buffer);
        assertFalse(buffer.isReadable(), "le décodeur lit tout ce que l'encodeur a écrit");
        return received;
    }

    private static BlueprintPayloads.VarValue value(VarScope scope, String name, Object raw) {
        var tag = VarValueNbt.encode(raw);
        assertNotNull(tag, "ce test ne parle que de valeurs que le format porte : " + raw);
        var wrapper = new CompoundTag();
        wrapper.put("v", tag);
        return new BlueprintPayloads.VarValue(scope, name, wrapper);
    }

    private static Object unwrap(BlueprintPayloads.VarValue value) {
        return VarValueNbt.decode(value.value().get("v"));
    }

    // ------------------------------------------------------- ce que le format transporte

    /**
     * Le point de conception de cette story : ce qui se réplique est <b>exactement</b> ce qui
     * survit à un redémarrage. Une seule liste de types, dans {@code VarValueNbt}, et la
     * sauvegarde du monde comme le fil y puisent.
     */
    @Test
    void lesTypesQuiVoyagentSontCeuxQuiSePersistent() {
        for (PinType type : List.of(PinTypes.BOOL, PinTypes.INT, PinTypes.LONG,
                PinTypes.DOUBLE, PinTypes.STRING, PinTypes.VEC3, PinTypes.BLOCKPOS,
                PinTypes.DIRECTION)) {
            assertTrue(VarValueNbt.carries(type), "devrait voyager : " + type);
        }
    }

    /**
     * Les quatre types qui ont un codec réseau mais que ce format ne porte pas. Ils sont le
     * piège de cette story : {@code PinType.hasStreamCodec()} rend vrai pour trois d'entre
     * eux, et s'y fier aurait produit un {@code @replicated} que le validateur approuve et
     * que l'encodeur laisse tomber en silence.
     */
    @Test
    void lesTypesQuiExigentLesRegistresNeVoyagentPas() {
        for (PinType type : List.of(PinTypes.ITEMSTACK, PinTypes.TEXT, PinTypes.BLOCKSTATE,
                PinTypes.RESOURCE_LOCATION)) {
            assertFalse(VarValueNbt.carries(type), "ne devrait pas voyager : " + type);
        }
        assertTrue(PinTypes.ITEMSTACK.hasStreamCodec(),
                "prémisse : il A un codec réseau, et c'est justement le piège");
    }

    @Test
    void lesReferencesVivantesEtLesJokersNeVoyagentPas() {
        assertFalse(VarValueNbt.carries(PinTypes.PLAYER));
        assertFalse(VarValueNbt.carries(PinTypes.ENTITY));
        assertFalse(VarValueNbt.carries(PinTypes.ANY));
    }

    /** Une collection ne voyage que si son contenu voyage : le conteneur ne dit rien. */
    @Test
    void uneCollectionSuitLeSortDeSonContenu() {
        assertTrue(VarValueNbt.carries(PinTypes.listOf(PinTypes.VEC3)));
        assertTrue(VarValueNbt.carries(PinTypes.mapOf(PinTypes.STRING, PinTypes.DOUBLE)));
        assertFalse(VarValueNbt.carries(PinTypes.listOf(PinTypes.PLAYER)));
        assertFalse(VarValueNbt.carries(PinTypes.mapOf(PinTypes.STRING, PinTypes.ITEMSTACK)));
        assertFalse(VarValueNbt.carries(PinTypes.listOf(PinTypes.listOf(PinTypes.ENTITY))),
                "et la récursion descend jusqu'au bout");
    }

    // ------------------------------------------------------------------ l'aller-retour

    @Test
    void chaqueTypeTraverseLeFilIntact() {
        var sent = new BlueprintPayloads.VarValues(List.of(
                value(VarScope.PLAYER, "or", 100.0),
                value(VarScope.PLAYER, "niveau", 7),
                value(VarScope.WORLD, "graine", 42L),
                value(VarScope.PLAYER_SHARED, "prenom", "Aliénor"),
                value(VarScope.GRAPH, "actif", true),
                value(VarScope.WORLD, "depart", new Vec3(1.5, 64.0, -3.25)),
                value(VarScope.WORLD, "coffre", new BlockPos(10, 70, -4)),
                value(VarScope.WORLD, "sens", Direction.NORTH)));

        var received = roundTrip(sent);

        assertEquals(sent.values().size(), received.values().size());
        assertEquals(100.0, unwrap(received.values().get(0)));
        assertEquals(7, unwrap(received.values().get(1)));
        assertEquals(42L, unwrap(received.values().get(2)));
        assertEquals("Aliénor", unwrap(received.values().get(3)));
        assertEquals(true, unwrap(received.values().get(4)));
        assertEquals(new Vec3(1.5, 64.0, -3.25), unwrap(received.values().get(5)));
        assertEquals(new BlockPos(10, 70, -4), unwrap(received.values().get(6)));
        assertEquals(Direction.NORTH, unwrap(received.values().get(7)));
    }

    @Test
    void laPorteeEtLeNomTraversent() {
        var received = roundTrip(new BlueprintPayloads.VarValues(
                List.of(value(VarScope.PLAYER_SHARED, "prenom", "Aliénor"))));

        assertEquals(VarScope.PLAYER_SHARED, received.values().get(0).scope());
        assertEquals("prenom", received.values().get(0).name());
    }

    /**
     * Le nom seul ne suffirait pas : {@code or} de portée monde et {@code or} de portée
     * joueur sont deux variables, et le client doit les ranger dans deux cases.
     */
    @Test
    void deuxPorteesDuMemeNomRestentDistinctes() {
        var received = roundTrip(new BlueprintPayloads.VarValues(List.of(
                value(VarScope.WORLD, "or", 1.0),
                value(VarScope.PLAYER, "or", 2.0))));

        assertEquals(VarScope.WORLD, received.values().get(0).scope());
        assertEquals(VarScope.PLAYER, received.values().get(1).scope());
        assertEquals(1.0, unwrap(received.values().get(0)));
        assertEquals(2.0, unwrap(received.values().get(1)));
    }

    @Test
    void uneCollectionTraverseLeFil() {
        var received = roundTrip(new BlueprintPayloads.VarValues(List.of(
                value(VarScope.WORLD, "chemin", List.of(new Vec3(0, 0, 0), new Vec3(1, 2, 3))),
                value(VarScope.WORLD, "soldes", Map.of("aliénor", 10.0, "bob", 20.0)))));

        assertEquals(List.of(new Vec3(0, 0, 0), new Vec3(1, 2, 3)),
                unwrap(received.values().get(0)));
        assertEquals(Map.of("aliénor", 10.0, "bob", 20.0), unwrap(received.values().get(1)));
    }

    /** Un tag vide dit « effacée » : le client a un seul cas à traiter, pas deux. */
    @Test
    void uneValeurEffaceeTraverseCommeUnTagVide() {
        var received = roundTrip(new BlueprintPayloads.VarValues(List.of(
                new BlueprintPayloads.VarValue(VarScope.PLAYER, "or", new CompoundTag()))));

        assertEquals("or", received.values().get(0).name());
        assertTrue(received.values().get(0).value().isEmpty());
    }

    // ----------------------------------------------------------------------- les refus

    /**
     * Une portée inconnue devient {@code LOCAL}, qui ne se réplique jamais : le client la
     * rangera donc nulle part. Un serveur d'une version où une portée aurait été ajoutée
     * n'emporte pas un client ancien — c'est la leçon de {@code ScreenUpdate.Kind}, dont
     * l'ordinal non borné faisait éclater tout le décodeur.
     */
    @Test
    void unePorteeInconnueNeFaitPasTomberLeClient() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 1);
        ByteBufCodecs.stringUtf8(BlueprintPayloads.MAX_NAME)
                .encode(buffer, "PORTEE_DUNE_VERSION_ULTERIEURE");
        ByteBufCodecs.stringUtf8(BlueprintPayloads.MAX_NAME).encode(buffer, "or");
        ByteBufCodecs.COMPOUND_TAG.encode(buffer, new CompoundTag());

        var received = BlueprintPayloads.VarValues.CODEC.decode(buffer);

        assertEquals(1, received.values().size());
        assertEquals(VarScope.LOCAL, received.values().get(0).scope(),
                "repliée sur une portée qui ne se réplique jamais");
    }

    /** Le nom voyage, jamais l'ordinal : la portée doit survivre à une réorganisation. */
    @Test
    void laPorteeVoyageParSonNom() {
        ByteBuf buffer = Unpooled.buffer();
        BlueprintPayloads.VarValue.CODEC.encode(buffer,
                new BlueprintPayloads.VarValue(VarScope.WORLD, "or", new CompoundTag()));

        assertTrue(buffer.toString(java.nio.charset.StandardCharsets.UTF_8).contains("WORLD"),
                "un ordinal se décalerait à la première portée insérée");
    }

    @Test
    void uneTrameAuDelaDuPlafondEstRefusee() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, BlueprintPayloads.MAX_VALUES + 1);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> BlueprintPayloads.VarValues.CODEC.decode(buffer));
    }

    @Test
    void uneTrameAuPlafondPasseEncore() {
        List<BlueprintPayloads.VarValue> pleine = new ArrayList<>();
        for (int i = 0; i < BlueprintPayloads.MAX_VALUES; i++) {
            pleine.add(value(VarScope.WORLD, "v" + i, (double) i));
        }

        assertEquals(BlueprintPayloads.MAX_VALUES,
                roundTrip(new BlueprintPayloads.VarValues(pleine)).values().size());
    }

    /**
     * Le plafond de la trame et celui du graphe sont le <b>même nombre</b>, exprès : un tick
     * qui change toutes les valeurs répliquées d'un graphe tient dans un seul envoi. Les
     * laisser diverger aurait demandé un découpage en trames, c'est-à-dire du code pour un
     * cas que le garde réseau refuse déjà.
     */
    @Test
    void leplafondDeLaTrameEtCeluiDuGrapheSontLeMeme() {
        assertEquals(NetLimits.DEFAULT.maxReplicatedVariables(), BlueprintPayloads.MAX_VALUES);
    }

    /**
     * Bien plus bas que le plafond des variables ordinaires, et c'est le point : une variable
     * ordinaire coûte de la mémoire serveur une fois, une répliquée coûte un envoi par joueur
     * qui la regarde, à chaque changement.
     */
    @Test
    void lesRepliqueesSontBienPlusBorneesQueLesOrdinaires() {
        assertTrue(NetLimits.DEFAULT.maxReplicatedVariables()
                        < NetLimits.DEFAULT.maxVariables() / 4,
                "un ordre de grandeur d'écart, pas un ajustement");
    }
}
