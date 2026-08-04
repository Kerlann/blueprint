package fr.blueprint.core.event;

import fr.blueprint.api.event.Dispatch;
import fr.blueprint.api.event.EventRegistry;
import fr.blueprint.api.event.EventType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;

/**
 * Les événements standard du monde (story 7.6, FR19 ; élargis au batch 4). Les ponts
 * Fabric qui les émettent vivent dans {@code BlueprintMod} ; {@code signal} est émis
 * par les graphes eux-mêmes (nœud {@code signal/emit}), par {@code /blueprint signal}
 * ou par un mod via {@code BlueprintEvents}. {@code command} = story 7.7.
 *
 * <p><b>Tout événement déclaré ici doit avoir une source</b> — {@code EventCoverageTest}
 * le vérifie en lisant les sources. {@code signal} a vécu plusieurs stories sans que
 * rien ne le déclenche : il se posait, se câblait, et ne s.exécutait jamais.
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

    /**
     * Un joueur se sert de ce qu'il tient.
     *
     * <p>{@code stack} et {@code item} sont arrivés avec la story 11.5. L'événement ne
     * disait <b>pas quel objet</b> avait été utilisé : un graphe savait qu'on avait
     * cliqué droit, et rien d'autre. Il ne pouvait donc pas réagir à un item déclaré, ce
     * qui vidait l'épic 11 de sa promesse — et il ne pouvait pas davantage réagir à une
     * pomme.
     *
     * <p>Deux sorties plutôt qu'une : {@code stack} porte tout (nombre, composants), et
     * {@code item} donne l'identifiant, qui est ce qu'on compare neuf fois sur dix. Sans
     * {@code item}, chaque graphe devrait le tirer de la pile par un nœud de plus, pour
     * la comparaison la plus banale de toutes.
     */
    public static final EventType PLAYER_USE_ITEM = EventType.builder(id("player_use_item"))
            .out("player", PinTypes.PLAYER)
            .out("stack", PinTypes.ITEMSTACK)
            .out("item", PinTypes.RESOURCE_LOCATION)
            .dispatch(Dispatch.PER_PLAYER).build();

    /** Un joueur casse un bloc. {@code block} est arrivé avec la 11.5, même raison. */
    public static final EventType PLAYER_BREAK_BLOCK = EventType.builder(id("player_break_block"))
            .out("player", PinTypes.PLAYER)
            .out("pos", PinTypes.BLOCKPOS)
            .out("block", PinTypes.RESOURCE_LOCATION)
            .dispatch(Dispatch.PER_PLAYER).build();

    /**
     * Un <b>bloc déclaré</b> vient d'être posé par un joueur (story 11.5).
     *
     * <p>Déclaré, et pas n'importe lequel : il n'existe aucun point d'accroche public
     * pour la pose d'un bloc quelconque — Fabric expose la casse, pas la pose. Ce qui le
     * rend possible ici, c'est que les blocs déclarés sont <b>nos</b> blocs, et qu'un bloc
     * sait quand on le pose.
     *
     * <p>Le nom du nœud le dit ; le prétendre général aurait été pire que de ne rien
     * livrer, puisque l'auteur ne l'aurait découvert qu'en essayant sur de la pierre.
     */
    public static final EventType BLOCK_PLACED = EventType.builder(id("block_placed"))
            .out("player", PinTypes.PLAYER)
            .out("pos", PinTypes.BLOCKPOS)
            .out("block", PinTypes.RESOURCE_LOCATION)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType ENTITY_DEATH = EventType.builder(id("entity_death"))
            .out("entity", PinTypes.ENTITY)
            .dispatch(Dispatch.PER_LEVEL).build();

    public static final EventType PLAYER_CHAT = EventType.builder(id("player_chat"))
            .out("player", PinTypes.PLAYER)
            .out("message", PinTypes.STRING)
            .dispatch(Dispatch.PER_PLAYER).build();

    // ------------------------------------------------------ événements du lot 4
    // Fabric les expose et nous ne les exposions pas. Les dégâts subis, en
    // particulier, sont le socle de tout script de combat : sans eux, un graphe ne
    // sait d'un joueur que sa mort.

    /** Dégâts subis par une entité : combien, et de la part de qui quand on le sait. */
    public static final EventType ENTITY_DAMAGED = EventType.builder(id("entity_damaged"))
            .out("entity", PinTypes.ENTITY)
            .out("amount", PinTypes.DOUBLE)
            .out("attacker", PinTypes.ENTITY)
            .dispatch(Dispatch.GLOBAL).build();

    /**
     * Une entité en a tué une autre. {@code entity_death} ne dit pas QUI a tué :
     * l'information existait dans Fabric et n'arrivait pas jusqu'au graphe.
     */
    public static final EventType ENTITY_KILLED = EventType.builder(id("entity_killed"))
            .out("killer", PinTypes.ENTITY)
            .out("victim", PinTypes.ENTITY)
            .dispatch(Dispatch.GLOBAL).build();

    public static final EventType PLAYER_RESPAWN = EventType.builder(id("player_respawn"))
            .out("player", PinTypes.PLAYER)
            .out("end_portal", PinTypes.BOOL)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType PLAYER_CHANGE_WORLD = EventType.builder(id("player_change_world"))
            .out("player", PinTypes.PLAYER)
            .out("from", PinTypes.RESOURCE_LOCATION)
            .out("to", PinTypes.RESOURCE_LOCATION)
            .dispatch(Dispatch.PER_PLAYER).build();

    /** Frapper une entité — distinct de la tuer, et bien plus fréquent. */
    public static final EventType PLAYER_ATTACK_ENTITY =
            EventType.builder(id("player_attack_entity"))
                    .out("player", PinTypes.PLAYER)
                    .out("target", PinTypes.ENTITY)
                    .dispatch(Dispatch.PER_PLAYER).build();

    /** Clic droit sur une entité (marchand, animal, monture). */
    public static final EventType PLAYER_USE_ENTITY = EventType.builder(id("player_use_entity"))
            .out("player", PinTypes.PLAYER)
            .out("target", PinTypes.ENTITY)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType PLAYER_SLEEP = EventType.builder(id("player_sleep"))
            .out("player", PinTypes.PLAYER)
            .out("pos", PinTypes.BLOCKPOS)
            .dispatch(Dispatch.PER_PLAYER).build();

    public static final EventType PLAYER_WAKE_UP = EventType.builder(id("player_wake_up"))
            .out("player", PinTypes.PLAYER)
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

    /**
     * Une <b>touche de Blueprint</b> a été pressée (story 11.4).
     *
     * <p>Huit emplacements, tous <b>non assignés au départ</b>. Le nœud porte le numéro
     * en littéral — cinquième cas de la même règle après {@code command}, {@code signal},
     * {@code gui_clicked} et les événements d'éléments riches : sans ce filtre, chaque
     * pression réveillerait chaque écouteur de chaque blueprint du serveur, et l'auteur
     * devrait comparer le numéro à la main dans tous ses graphes.
     *
     * <p>Ce sont des <b>emplacements</b> et non des touches : le graphe ne sait pas, et
     * n'a pas à savoir, quelle touche du clavier le joueur a choisie. Il verrait sinon un
     * code de touche physique, différent d'un clavier à l'autre, et un script AZERTY
     * cesserait de marcher en QWERTY.
     */
    public static final EventType KEY_PRESSED = EventType.builder(id("key_pressed"))
            .out("player", PinTypes.PLAYER)
            .out("key", PinTypes.INT)
            .dispatch(Dispatch.PER_PLAYER).build();

    /** Un écran de blueprint vient de s'ouvrir chez ce joueur (story 10.4). */
    public static final EventType GUI_OPENED = EventType.builder(id("gui_opened"))
            .out("player", PinTypes.PLAYER)
            .out("screen", PinTypes.STRING)
            .dispatch(Dispatch.PER_PLAYER).build();

    /**
     * L'écran s'est refermé — Échap, mort, déconnexion, ou un autre écran qui prend la
     * place. C'est le pendant obligatoire de {@link #GUI_OPENED} : sans lui, un graphe
     * qui prépare quelque chose à l'ouverture n'aurait aucun endroit où le défaire.
     */
    public static final EventType GUI_CLOSED = EventType.builder(id("gui_closed"))
            .out("player", PinTypes.PLAYER)
            .out("screen", PinTypes.STRING)
            .dispatch(Dispatch.PER_PLAYER).build();

    /**
     * Un élément cliquable a été cliqué. Comme {@link #COMMAND} et {@link #SIGNAL}, le
     * nœud porte le nom écouté en <b>littéral</b> et n'est donc PAS synthétisé : sans
     * ce filtre, chaque clic réveillerait chaque écouteur de chaque écran, et l'auteur
     * devrait comparer le nom à la main dans tous ses graphes.
     */
    public static final EventType GUI_ELEMENT_CLICKED = EventType.builder(id("gui_clicked"))
            .out("player", PinTypes.PLAYER)
            .out("screen", PinTypes.STRING)
            .out("element", PinTypes.STRING)
            .dispatch(Dispatch.GLOBAL).build();

    /**
     * Une ligne de liste a été cliquée (story 10.8, AC2).
     *
     * <p>Rend l'<b>indice dans la liste</b> et le texte de la ligne, pas seulement le nom
     * de l'élément : une liste n'a qu'un nom pour cent entrées, et sans l'indice le
     * graphe ne saurait pas laquelle on a choisie.
     */
    public static final EventType GUI_LIST_CLICKED = EventType.builder(id("gui_list_clicked"))
            .out("player", PinTypes.PLAYER)
            .out("screen", PinTypes.STRING)
            .out("element", PinTypes.STRING)
            .out("index", PinTypes.INT)
            .out("line", PinTypes.STRING)
            .dispatch(Dispatch.GLOBAL).build();

    /**
     * Le contenu d'un champ de saisie a changé, ou a été validé par {@code Entrée}.
     *
     * <p>{@code submitted} distingue les deux : réagir à chaque frappe convient à une
     * recherche qui filtre, valider convient à un formulaire. Deux événements séparés
     * auraient obligé l'auteur à en câbler deux pour le cas courant.
     */
    public static final EventType GUI_INPUT_CHANGED = EventType.builder(id("gui_input_changed"))
            .out("player", PinTypes.PLAYER)
            .out("screen", PinTypes.STRING)
            .out("element", PinTypes.STRING)
            .out("text", PinTypes.STRING)
            .out("submitted", PinTypes.BOOL)
            .dispatch(Dispatch.GLOBAL).build();

    /** Une case a été cochée ou un curseur déplacé (story 10.8, AC5b). */
    public static final EventType GUI_VALUE_CHANGED = EventType.builder(id("gui_value_changed"))
            .out("player", PinTypes.PLAYER)
            .out("screen", PinTypes.STRING)
            .out("element", PinTypes.STRING)
            .out("value", PinTypes.DOUBLE)
            .out("checked", PinTypes.BOOL)
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
        registry.register(ENTITY_DAMAGED);
        registry.register(ENTITY_KILLED);
        registry.register(PLAYER_RESPAWN);
        registry.register(PLAYER_CHANGE_WORLD);
        registry.register(PLAYER_ATTACK_ENTITY);
        registry.register(PLAYER_USE_ENTITY);
        registry.register(PLAYER_SLEEP);
        registry.register(PLAYER_WAKE_UP);
        registry.register(SIGNAL);
        registry.register(COMMAND);
        registry.register(GUI_OPENED);
        registry.register(GUI_LIST_CLICKED);
        registry.register(GUI_INPUT_CHANGED);
        registry.register(GUI_VALUE_CHANGED);
        registry.register(GUI_CLOSED);
        registry.register(GUI_ELEMENT_CLICKED);
        registry.register(KEY_PRESSED);
        registry.register(BLOCK_PLACED);
    }
}
