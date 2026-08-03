package fr.blueprint.core.nodes;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stories 7.3-7.5 : formes, permissions et pureté de la bibliothèque étendue —
 * et le verrou de symétrie des langues : tout nœud enregistré a sa clé {@code .name}
 * en anglais ET en français (AC5).
 */
class StandardLibraryTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private static NodeType node(String path) {
        NodeType type = LOADED.nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", path)).orElse(null);
        assertNotNull(type, "nœud absent : " + path);
        return type;
    }

    @Test
    void permissionsEtPurete() {
        // Lectures du monde : EXEC (jamais purs — contrat de mémoïsation) et SAFE.
        assertFalse(node("world/get_block").pure());
        assertEquals(Permission.SAFE, node("world/get_block").permission());
        assertEquals(Permission.GAMEPLAY, node("world/play_sound").permission());
        assertEquals(Permission.GAMEPLAY, node("player/give_item").permission());
        // Mutations du monde : WORLD ; explosion : ADMIN, comme set_gamemode.
        assertEquals(Permission.WORLD, node("world/set_block").permission());
        assertEquals(Permission.WORLD, node("world/spawn_entity").permission());
        assertEquals(Permission.WORLD, node("world/set_weather").permission());
        assertEquals(Permission.ADMIN, node("world/explosion").permission());
        assertEquals(Permission.ADMIN, node("player/set_gamemode").permission());
        assertEquals(Permission.WORLD, node("entity/teleport").permission());
        // Constructions item/texte : pures et déterministes.
        assertTrue(node("item/create").pure());
        assertTrue(node("item/matches").pure());
        assertTrue(node("text/literal").pure());
        assertTrue(node("text/concat").pure());
        assertTrue(node("text/concat").deterministic());
    }

    @Test
    void toutesLesFamillesSontEnregistrees() {
        for (String path : new String[]{
                "world/get_block", "world/set_block", "world/is_block", "world/spawn_entity",
                "world/play_sound", "world/particles", "world/set_weather", "world/set_time",
                "world/explosion", "world/drop_item",
                "entity/position", "entity/teleport", "entity/health", "entity/set_health",
                "entity/heal", "entity/add_effect",
                "player/give_item", "player/title", "player/give_xp", "player/set_gamemode",
                "item/create", "item/count", "item/with_count", "item/matches",
                "text/literal", "text/colored", "text/concat"}) {
            node(path);
        }
    }

    /** Chaque nœud enregistré a sa clé .name dans les DEUX fichiers de langue. */
    @Test
    void symetrieDesLangues() {
        JsonObject en = lang("en_us");
        JsonObject fr = lang("fr_fr");
        for (NodeType type : LOADED.nodes().all()) {
            // La clé RÉELLE du type (les nœuds d'événement synthétisés portent la
            // clé blueprint.event.* de leur événement).
            String key = type.titleKey();
            assertTrue(en.has(key), "clé anglaise manquante : " + key);
            assertTrue(fr.has(key), "clé française manquante : " + key);
        }
    }

    private static JsonObject lang(String code) {
        var stream = StandardLibraryTest.class.getResourceAsStream(
                "/assets/blueprint/lang/" + code + ".json");
        assertNotNull(stream, "fichier de langue absent : " + code);
        return new Gson().fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    }
}
