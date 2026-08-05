package fr.blueprint.core.storage;

import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.DemoBlueprint;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La sauvegarde du monde ne réencode que ce qui a changé (épic 19c).
 *
 * <p>{@code refreshFromLive} réencodait <b>intégralement chaque blueprint</b> à chaque
 * sauvegarde du monde — toutes les cinq minutes, sur le fil serveur, qu'il ait bougé ou
 * non. Un graphe inchangé depuis l'ouverture du monde était donc réencodé à l'identique,
 * nœud par nœud, littéral par littéral à travers leurs codecs, indéfiniment.
 *
 * <h2>Ce que ce test défend</h2>
 *
 * <p>Deux choses, et la seconde est la plus facile à casser :
 *
 * <ol>
 *   <li>les octets produits ne changent pas — un cache qui modifierait la sauvegarde
 *       serait un cache à retirer, pas à régler ;</li>
 *   <li>l'invalidation couvre {@code enabled} <b>en plus</b> de la révision. Désactiver un
 *       blueprint n'incrémente pas la révision (MODEL-001 : c'est un état de cycle de vie
 *       serveur, pas une édition), donc un cache clefé sur la seule révision écrirait
 *       l'ancien état — et un blueprint coupé par un administrateur repartirait au
 *       redémarrage suivant.</li>
 * </ol>
 */
class StorageEncodeCacheTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static BlueprintScheduler newScheduler() {
        return new BlueprintScheduler(100, new BlueprintScheduler.Listener() {
            @Override
            public void disabled(Identifier blueprintId, int streakTicks) {
            }

            @Override
            public void faulted(Identifier blueprintId, UUID node, String message) {
            }
        });
    }

    private static CompoundTag save(BlueprintStorage storage) {
        return (CompoundTag) BlueprintStorage.TYPE.codec()
                .encodeStart(NbtOps.INSTANCE, storage).getOrThrow();
    }

    private record Fixture(BlueprintManager manager, BlueprintStorage storage, Blueprint demo) {
    }

    private static Fixture fixture() {
        BlueprintManager manager = new BlueprintManager();
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        assertTrue(manager.adopt(demo));
        BlueprintStorage storage = new BlueprintStorage();
        storage.bindLive(manager, newScheduler());
        return new Fixture(manager, storage, demo);
    }

    /** Sans édition, deux sauvegardes successives produisent exactement le même NBT. */
    @Test
    void deuxSauvegardesSansEditionDonnentLeMemeNbt() {
        Fixture f = fixture();
        CompoundTag first = save(f.storage());
        CompoundTag second = save(f.storage());

        assertEquals(first, second, "le cache a changé ce que la sauvegarde écrit");
        // Et ce sont bien deux objets : le tag remis à Minecraft ne doit jamais être
        // celui que le cache conserve, sinon une mutation en aval corromprait le cache.
        assertNotSame(first, second);
    }

    /** Une édition se voit : la révision bouge, le cache tombe. */
    @Test
    void uneEditionEstEcrite() {
        Fixture f = fixture();
        CompoundTag before = save(f.storage());

        assertTrue(new EditOperation.AddNode(UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("blueprint", "math/add"), new Vec2d(500, 500))
                .apply(f.demo(), LOADED.nodes()).applied());

        assertNotEquals(before, save(f.storage()),
                "un nœud ajouté n'apparaît pas dans la sauvegarde — le cache ne suit pas la révision");
    }

    /**
     * <b>Le test qui compte.</b> Désactiver un blueprint est écrit, alors que la révision
     * n'a pas bougé.
     *
     * <p>C'est le piège exact d'un cache clefé sur la seule révision : {@code setEnabled}
     * ne l'incrémente pas. Sans {@code enabled} dans la clef, ce test passe au rouge et le
     * blueprint désactivé revient vivant au redémarrage.
     */
    @Test
    void desactiverEstEcritMemeSansEdition() {
        Fixture f = fixture();
        int revision = f.demo().revision();
        CompoundTag before = save(f.storage());

        assertTrue(f.manager().setEnabled(f.demo().id(), false));
        assertEquals(revision, f.demo().revision(),
                "prémisse du test : setEnabled ne touche pas la révision (MODEL-001)");

        assertNotEquals(before, save(f.storage()),
                "la désactivation n'est pas écrite — le cache est clefé sur la révision"
                        + " seule, et un blueprint coupé repartira au redémarrage");
    }

    /** Un blueprint supprimé ne laisse pas son encodage derrière lui. */
    @Test
    void unBlueprintSupprimeSortDeLaSauvegarde() {
        Fixture f = fixture();
        save(f.storage());

        assertTrue(f.manager().delete(f.demo().id()));
        CompoundTag after = save(f.storage());

        assertEquals(0, after.getListOrEmpty("blueprints").size(),
                "le blueprint supprimé est encore écrit — le cache n'a pas été purgé");
    }
}
