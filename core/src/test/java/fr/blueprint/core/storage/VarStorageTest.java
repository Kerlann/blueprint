package fr.blueprint.core.storage;

import com.mojang.serialization.Codec;
import fr.blueprint.core.MinecraftBootstrap;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.vm.VarOwner;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <b>Les variables survivent au redémarrage.</b>
 *
 * <p>Elles ne survivaient pas : {@code varsOf} rendait un magasin en mémoire, si bien
 * qu'un serveur de jeu de rôle perdait tous ses personnages au premier redémarrage. La
 * portée {@code PLAYER} se déclare pourtant « persistante par joueur ».
 *
 * <p>Le tour complet — écrire, encoder, décoder, relire — plutôt qu'une vérification du
 * NBT produit : c'est la relecture qui compte, et un encodage qu'on ne relit jamais peut
 * être faux longtemps sans que rien ne le dise.
 */
class VarStorageTest {

    static {
        MinecraftBootstrap.ensure();
    }

    private static final Identifier RP = Identifier.fromNamespaceAndPath("test", "rp");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** Le codec de {@link VarStorage#TYPE}, obtenu comme Minecraft l'obtient. */
    @SuppressWarnings("unchecked")
    private static Codec<VarStorage> codec() {
        return (Codec<VarStorage>) VarStorage.TYPE.codec();
    }

    private static VarStorage roundTrip(VarStorage storage) {
        var encoded = codec().encodeStart(NbtOps.INSTANCE, storage)
                .getOrThrow(m -> new IllegalStateException("encodage : " + m));
        return codec().parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(m -> new IllegalStateException("décodage : " + m));
    }

    /** Ce que fait vraiment un serveur RP : deux joueurs, quatre champs chacun. */
    @Test
    void deuxIdentitesDeJoueurSurviventAuTour() {
        VarStorage before = new VarStorage();
        before.set(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom", "Alice");
        before.set(VarScope.PLAYER, new VarOwner(RP, ALICE), "age", 27.0);
        before.set(VarScope.PLAYER, new VarOwner(RP, BOB), "prenom", "Bob");
        before.set(VarScope.PLAYER, new VarOwner(RP, BOB), "cree", true);

        VarStorage after = roundTrip(before);

        assertEquals("Alice", after.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom"));
        assertEquals(27.0, after.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "age"));
        assertEquals("Bob", after.get(VarScope.PLAYER, new VarOwner(RP, BOB), "prenom"));
        assertEquals(true, after.get(VarScope.PLAYER, new VarOwner(RP, BOB), "cree"));
        assertNull(after.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "cree"),
                "le tour ne doit pas mélanger les joueurs");
    }

    /** Les quatre portées se rangent séparément, et se relisent séparément. */
    @Test
    void lesQuatrePorteesSeRelisentChacuneChezSoi() {
        VarStorage before = new VarStorage();
        before.set(VarScope.WORLD, new VarOwner(RP, ALICE), "saison", "hiver");
        before.set(VarScope.GRAPH, new VarOwner(RP, ALICE), "compteur", 3);
        before.set(VarScope.PLAYER, new VarOwner(RP, ALICE), "metier", "Forgeron");
        before.set(VarScope.PLAYER_SHARED, new VarOwner(RP, ALICE), "prenom", "Alice");

        VarStorage after = roundTrip(before);

        assertEquals("hiver", after.get(VarScope.WORLD, VarOwner.NONE, "saison"));
        assertEquals(3, after.get(VarScope.GRAPH, new VarOwner(RP, null), "compteur"));
        assertEquals("Forgeron", after.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "metier"));
        assertEquals("Alice",
                after.get(VarScope.PLAYER_SHARED, new VarOwner(RP, ALICE), "prenom"));
    }

    /**
     * <b>L'isolation par blueprint traverse la sauvegarde.</b>
     *
     * <p>Elle pourrait tenir en jeu et se perdre à l'écriture : le NBT était plat par
     * joueur, et deux blueprints s'y seraient retrouvés fondus au redémarrage — un défaut
     * qui ne se voit qu'après un arrêt du serveur, donc le plus tard possible.
     */
    @Test
    void deuxBlueprintsRestentSeparesApresLeTour() {
        Identifier autre = Identifier.fromNamespaceAndPath("test", "metiers");
        VarStorage before = new VarStorage();
        before.set(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom", "Alice");
        before.set(VarScope.PLAYER, new VarOwner(autre, ALICE), "prenom", 42);
        before.set(VarScope.PLAYER_SHARED, new VarOwner(RP, ALICE), "titre", "Baron");

        VarStorage after = roundTrip(before);

        assertEquals("Alice", after.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom"));
        assertEquals(42, after.get(VarScope.PLAYER, new VarOwner(autre, ALICE), "prenom"));
        assertEquals("Baron",
                after.get(VarScope.PLAYER_SHARED, new VarOwner(autre, ALICE), "titre"),
                "ce qui est déclaré partagé le reste après un redémarrage");
        assertNull(after.get(VarScope.PLAYER, new VarOwner(RP, BOB), "prenom"),
                "et les joueurs restent séparés, comme avant");
    }

    /**
     * <b>Le type est conservé, pas seulement la valeur.</b> Un {@code int} relu en
     * {@code double} suffit à faire fauter un nœud qui attend l'un ou l'autre, et
     * l'encodage NBT nu ne les distingue pas.
     */
    @Test
    void lesTypesNeSeConfondentPasAuRetour() {
        VarStorage before = new VarStorage();
        var alice = new VarOwner(RP, ALICE);
        before.set(VarScope.PLAYER, alice, "entier", 7);
        before.set(VarScope.PLAYER, alice, "reel", 7.0);
        before.set(VarScope.PLAYER, alice, "vrai", true);
        before.set(VarScope.PLAYER, alice, "liste", List.of("Fermier", "Garde"));

        VarStorage after = roundTrip(before);

        assertEquals(Integer.class, after.get(VarScope.PLAYER, alice, "entier").getClass(),
                "un entier relu en double ferait fauter le nœud qui le consomme");
        assertEquals(Double.class, after.get(VarScope.PLAYER, alice, "reel").getClass());
        assertEquals(Boolean.class, after.get(VarScope.PLAYER, alice, "vrai").getClass());
        assertEquals(List.of("Fermier", "Garde"), after.get(VarScope.PLAYER, alice, "liste"));
    }

    /**
     * Les trois types de géométrie font le tour complet.
     *
     * <p>Ils sont devenus des types de variable proposés dans l'éditeur. Sans cet
     * encodage, un point de retour rangé dans une variable {@code vec3} de portée monde
     * serait journalisé comme non persisté à chaque sauvegarde, et le graphe repartirait
     * du défaut au redémarrage — c'est-à-dire l'origine du monde.
     *
     * <p>La position se vérifie sur des coordonnées <b>négatives et asymétriques</b> : le
     * long empaqueté de {@code BlockPos} encode trois champs de largeurs différentes, et
     * un signe mal relu ne se voit pas sur (0, 0, 0).
     */
    @Test
    void laGeometrieSurvitAuTour() {
        VarStorage before = new VarStorage();
        var alice = new VarOwner(RP, ALICE);
        before.set(VarScope.PLAYER, alice, "point",
                new net.minecraft.world.phys.Vec3(1.5, -64.25, 300.75));
        before.set(VarScope.PLAYER, alice, "bloc",
                new net.minecraft.core.BlockPos(-1200, -59, 4096));
        before.set(VarScope.PLAYER, alice, "face", net.minecraft.core.Direction.WEST);

        VarStorage after = roundTrip(before);

        assertEquals(new net.minecraft.world.phys.Vec3(1.5, -64.25, 300.75),
                after.get(VarScope.PLAYER, alice, "point"));
        assertEquals(new net.minecraft.core.BlockPos(-1200, -59, 4096),
                after.get(VarScope.PLAYER, alice, "bloc"));
        assertEquals(net.minecraft.core.Direction.WEST,
                after.get(VarScope.PLAYER, alice, "face"));
    }

    /**
     * Ce qui ne s'écrit pas ne fait pas tomber la sauvegarde.
     *
     * <p>Une pile d'objets dans une variable n'a pas de type à l'écriture. Elle est
     * sautée, le journal la nomme, et <b>tout le reste passe</b> — perdre le monde entier
     * pour une variable serait une bien plus mauvaise affaire.
     */
    @Test
    void uneValeurNonPersistableNEmportePasLesAutres() {
        VarStorage before = new VarStorage();
        var alice = new VarOwner(RP, ALICE);
        before.set(VarScope.PLAYER, alice, "prenom", "Alice");
        before.set(VarScope.PLAYER, alice, "objet", new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.EMERALD));

        VarStorage after = roundTrip(before);

        assertEquals("Alice", after.get(VarScope.PLAYER, alice, "prenom"));
        assertNull(after.get(VarScope.PLAYER, alice, "objet"),
                "la valeur non persistable est sautée, pas devinée");
    }
}
