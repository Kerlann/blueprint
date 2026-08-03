package fr.blueprint.core.event;

import fr.blueprint.api.event.Dispatch;
import fr.blueprint.api.event.EventRegistry;
import fr.blueprint.api.event.EventType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;

/**
 * Les 9 événements standard du monde (story 7.6, FR19). Les ponts Fabric qui les
 * émettent vivent dans {@code BlueprintMod} ; {@code signal} est émis par les graphes
 * eux-mêmes (nœud émetteur à venir) ou par des mods via {@code BlueprintEvents}.
 * {@code command} (arguments déclarés) = story 7.7.
 */
public final class StandardEvents {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", "event/" + path);
    }

    public static final EventType SERVER_TICK = EventType.builder(id("server_tick"))
            .dispatch(Dispatch.GLOBAL).build();

    public static final EventType PLAYER_JOIN = EventType.builder(id("player_join"))
            .out("player", PinTypes.PLAYER)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType PLAYER_QUIT = EventType.builder(id("player_quit"))
            .out("player", PinTypes.PLAYER)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType PLAYER_USE_BLOCK = EventType.builder(id("player_use_block"))
            .out("player", PinTypes.PLAYER)
            .out("pos", PinTypes.BLOCKPOS)
            .out("face", PinTypes.DIRECTION)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType PLAYER_USE_ITEM = EventType.builder(id("player_use_item"))
            .out("player", PinTypes.PLAYER)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType PLAYER_BREAK_BLOCK = EventType.builder(id("player_break_block"))
            .out("player", PinTypes.PLAYER)
            .out("pos", PinTypes.BLOCKPOS)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType ENTITY_DEATH = EventType.builder(id("entity_death"))
            .out("entity", PinTypes.ENTITY)
            .dispatch(Dispatch.PER_LEVEL).build();

    public static final EventType PLAYER_CHAT = EventType.builder(id("player_chat"))
            .out("player", PinTypes.PLAYER)
            .out("message", PinTypes.STRING)
            .dispatch(Dispatch.PER_PLAYER).build();

    /**
     * Signal nommé, émis par un autre blueprint ({@code signal/emit}) ou par
     * {@code /blueprint signal}. C'est la primitive « un blueprint en appelle un
     * autre » : le nœud d'événement porte le nom écouté en littéral, exactement
     * comme {@link #COMMAND}, et n'est donc PAS synthétisé.
     */
    public static final EventType SIGNAL = EventType.builder(id("signal"))
            .out("payload", PinTypes.STRING)
            .dispatch(Dispatch.GLOBAL).build();

    /**
     * Commande déclarée par un blueprint (story 7.7) : le nœud d'événement porte le
     * nom en littéral, /bpc &lt;nom&gt; [texte] la déclenche. Le nœud n'est PAS
     * synthétisé (enregistré à la main avec son entrée « name »).
     */
    public static final EventType COMMAND = EventType.builder(id("command"))
            .out("player", PinTypes.PLAYER)
            .out("name", PinTypes.STRING)
            .out("arg", PinTypes.STRING)
            .dispatch(Dispatch.GLOBAL).build();

    private StandardEvents() {
    }

    public static void register(EventRegistry registry) {
        registry.register(SERVER_TICK);
        registry.register(PLAYER_JOIN);
        registry.register(PLAYER_QUIT);
        registry.register(PLAYER_USE_BLOCK);
        registry.register(PLAYER_USE_ITEM);
        registry.register(PLAYER_BREAK_BLOCK);
        registry.register(ENTITY_DEATH);
        registry.register(PLAYER_CHAT);
        registry.register(SIGNAL);
        registry.register(COMMAND);
    }
}
