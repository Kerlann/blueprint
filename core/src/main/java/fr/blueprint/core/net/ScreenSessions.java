package fr.blueprint.core.net;

import fr.blueprint.core.graph.screen.ScreenUpdate;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quel écran chaque joueur a ouvert, et ce qu'il affiche (stories 10.3 et 10.4).
 *
 * <p><b>Un seul à la fois.</b> Empiler les écrans poserait aussitôt les questions du
 * focus, de la fermeture en cascade et de la pile à restaurer après une déconnexion.
 * Un menu à plusieurs pages se fait avec UN écran dont on change le contenu — c'est
 * aussi ce que font les interfaces vanilla.
 *
 * <p>C'est la table que le serveur consulte pour vérifier un clic (FR52) : il ne croit
 * jamais le client sur l'écran qu'il prétend avoir ouvert. Sans elle, n'importe quel
 * client modifié déclencherait le bouton d'un menu qu'il n'a jamais vu.
 *
 * <p>Elle porte aussi les <b>trois gardes</b> qui rendent tenable un graphe écrit
 * naïvement (« à chaque tick, affiche l'or ») : le numéro d'instance, la différence, et
 * le regroupement par tick. Pure et testable headless — elle ne connaît que des UUID.
 */
public final class ScreenSessions {

    /**
     * L'écran ouvert : de quel blueprint, lequel, et sous quel <b>numéro d'instance</b>.
     *
     * <p>Le numéro monte à chaque ouverture. Entre l'instant où le graphe demande une
     * mise à jour et celui où elle arrive, le joueur peut avoir fermé l'écran ou en
     * avoir rouvert un autre : sans numéro, elle s'appliquerait au nouvel écran, au
     * mauvais bouton, et sans la moindre erreur.
     */
    public record Open(Identifier blueprint, String screen, int instance) {
    }

    private final Map<UUID, Open> open = new HashMap<>();
    /** Ce que chaque client AFFICHE — la base de la comparaison (AC3c). */
    private final Map<UUID, Map<String, ScreenUpdate>> displayed = new HashMap<>();
    /** Ce qui partira en fin de tick, une entrée par clé (AC3b). */
    private final Map<UUID, Map<String, ScreenUpdate>> pending = new HashMap<>();

    /**
     * Les HUD affichés, par joueur. PLUSIEURS coexistent, contrairement au modal :
     * un HUD ne capte rien, donc deux HUD sont juste deux dessins au même endroit —
     * exactement comme la barre de vie et la barre d'expérience du jeu. En interdire
     * plusieurs obligerait à réunir dans un même document la mana et la quête.
     */
    private final Map<UUID, LinkedHashMap<String, Shown>> huds = new HashMap<>();

    /**
     * Un HUD affiché : son blueprint propriétaire, et la <b>version d'écran réellement
     * envoyée</b> au client.
     *
     * <p>La seconde sert à ne pas réémettre une description que le client possède déjà —
     * {@code gui/show_hud} réencodait et regzippait l'écran entier à chaque appel, et un
     * graphe branché sur le tick le fait vingt fois par seconde et par joueur.
     *
     * <p>Elle vit <b>dans la même table</b> que le reste, à dessein : les HUD sont retirés
     * en cinq endroits (masquage, masquage total, oubli du joueur, retrait par blueprint,
     * fermeture d'un blueprint), et une seconde table à tenir en parallèle aurait fini par
     * diverger de celle-ci sur l'un d'eux.
     */
    private record Shown(Identifier blueprint, fr.blueprint.core.graph.screen.@Nullable Screen sent) {
    }

    /**
     * Les lignes envoyées à chaque liste, par joueur (story 10.8).
     *
     * <p>Le serveur doit pouvoir vérifier qu'un indice cliqué EXISTE : un client peut
     * annoncer la ligne 900 d'une liste qui en compte trois. Sans cette mémoire, il
     * faudrait le croire — et rendre au graphe une ligne qui n'a jamais été affichée.
     *
     * <p>Clé : « écran/élément ». Vidée à chaque ouverture, comme le reste.
     */
    private final Map<UUID, Map<String, List<String>>> lines = new HashMap<>();

    private int nextInstance = 1;

    /**
     * Note l'ouverture, en remplaçant ce que le joueur avait — jamais en empilant.
     * Rend le numéro d'instance à transmettre.
     */
    public int opened(UUID player, Identifier blueprint, String screen) {
        int instance = nextInstance++;
        open.put(player, new Open(blueprint, screen, instance));
        // L'écran neuf n'affiche encore rien de modifié : repartir d'une table vide,
        // sinon la comparaison croirait déjà à jour ce que le nouvel écran n'a jamais
        // reçu — et le premier rafraîchissement ne partirait pas.
        displayed.remove(player);
        pending.remove(player);
        lines.remove(player);
        return instance;
    }

    public @Nullable Open of(UUID player) {
        return open.get(player);
    }

    /**
     * Le joueur a-t-il CET écran ouvert ? La question que pose chaque clic reçu : le
     * client annonce ce qu'il veut, seul le serveur sait ce qu'il a envoyé.
     */
    public boolean hasOpen(UUID player, Identifier blueprint, String screen) {
        Open current = open.get(player);
        return current != null && current.blueprint().equals(blueprint)
                && current.screen().equals(screen);
    }

    /** Ferme, et dit si quelque chose était bien ouvert. */
    public boolean closed(UUID player) {
        displayed.remove(player);
        pending.remove(player);
        return open.remove(player) != null;
    }

    // --------------------------------------------------------------- HUD (10.9)

    /** Affiche un HUD sans suivre sa version ; vrai s'il n'y était pas déjà. */
    public boolean showHud(UUID player, Identifier blueprint, String screen) {
        return showHud(player, blueprint, screen, null);
    }

    /**
     * Affiche un HUD ; vrai s'il faut <b>(r)envoyer sa description</b> au client.
     *
     * <p>Faux quand le même écran, dans la même version, y est déjà — le client possède
     * déjà ces octets, les réencoder et les regzipper ne lui apprendrait rien.
     *
     * <p>La comparaison porte sur l'<b>identité</b> de l'instance {@code Screen}, et c'est
     * ce qui rend la chose sûre : un enregistrement du blueprint produit une nouvelle
     * instance, donc une instance inchangée signifie un écran inchangé. Se contenter de
     * « ce HUD était-il déjà affiché ? » aurait figé le HUD à sa version d'ouverture —
     * {@code refreshScreensOf} ne parcourt que les écrans <b>modaux</b> et n'en rafraîchit
     * aucun.
     */
    public boolean showHud(UUID player, Identifier blueprint, String screen,
                           fr.blueprint.core.graph.screen.@Nullable Screen instance) {
        // Le blueprint PROPRIÉTAIRE voyage avec : sans lui, désactiver un graphe
        // laisserait son HUD à l'écran pour toujours, sans que personne puisse le
        // retirer — un modal, lui, se ferme au moins par Échap.
        Shown previous = huds.computeIfAbsent(player, p -> new LinkedHashMap<>())
                .put(screen, new Shown(blueprint, instance));
        return previous == null || previous.sent() != instance;
    }

    public boolean hideHud(UUID player, String screen) {
        var shownHuds = huds.get(player);
        return shownHuds != null && shownHuds.remove(screen) != null;
    }

    /**
     * Masque tout — la garde de sécurité. Un HUD n'a pas d'{@code Échap} : sans un
     * moyen de tout retirer, un graphe fautif affichant un panneau opaque laisserait
     * le joueur sans aucun recours.
     */
    public boolean hideAllHuds(UUID player) {
        var shownHuds = huds.remove(player);
        return shownHuds != null && !shownHuds.isEmpty();
    }

    /** Les HUD affichés chez ce joueur, dans l'ordre où ils ont été demandés. */
    public java.util.Set<String> hudsOf(UUID player) {
        var shownHuds = huds.get(player);
        return shownHuds == null ? java.util.Set.of()
                : java.util.Collections.unmodifiableSet(shownHuds.keySet());
    }

    /** Ce joueur voit-il cet écran — modal ou HUD ? La question que pose une mise à jour. */
    public boolean shows(UUID player, String screen) {
        Open current = open.get(player);
        if (current != null && current.screen().equals(screen)) {
            return true;
        }
        var shownHuds = huds.get(player);
        return shownHuds != null && shownHuds.containsKey(screen);
    }

    // ------------------------------------------------------- modifications (10.4)

    /**
     * Empile une modification pour la fin du tick.
     *
     * <p>Deux gardes, ensemble. La <b>comparaison</b> : réécrire ce que le client
     * affiche déjà n'envoie rien — c'est ce qui permet d'écrire « à chaque tick, affiche
     * l'or » sans payer vingt paquets par seconde et par joueur, dont dix-neuf
     * identiques. Le <b>regroupement</b> : deux écritures de même nature sur le même
     * élément dans un tick n'en font qu'une, la dernière.
     *
     * <p>Le cas subtil est la combinaison des deux : si le graphe écrit A puis B alors
     * que le client affiche déjà B, il faut <b>retirer</b> A de la file, et pas
     * seulement s'abstenir d'ajouter B — sinon A partirait pour rien et l'écran
     * clignoterait.
     *
     * @return vrai si quelque chose partira réellement
     */
    public boolean queue(UUID player, ScreenUpdate update) {
        if (!shows(player, update.screen())) {
            return false;
        }
        String key = update.key();
        Map<String, ScreenUpdate> shown = displayed.get(player);
        if (shown != null && update.equals(shown.get(key))) {
            Map<String, ScreenUpdate> waiting = pending.get(player);
            if (waiting != null) {
                waiting.remove(key);
            }
            return false;
        }
        pending.computeIfAbsent(player, p -> new LinkedHashMap<>()).put(key, update);
        return true;
    }

    /**
     * Vide la file d'un joueur et acte ce qui part comme étant désormais affiché.
     * Appelé une fois par tick — c'est ce qui fait qu'un panneau de cinq éléments
     * produit une trame et non cinq.
     */
    public List<ScreenUpdate> drain(UUID player) {
        Map<String, ScreenUpdate> waiting = pending.remove(player);
        if (waiting == null || waiting.isEmpty()) {
            return List.of();
        }
        Map<String, ScreenUpdate> shown =
                displayed.computeIfAbsent(player, p -> new HashMap<>());
        shown.putAll(waiting);
        // Les lignes sont retenues au moment où elles PARTENT, pas quand elles sont
        // mises en file : une modification écartée par la comparaison n'a rien changé
        // chez le client, et la retenir décalerait la mémoire de ce qu'il affiche.
        for (ScreenUpdate update : waiting.values()) {
            if (update.kind() == ScreenUpdate.Kind.LINES) {
                lines.computeIfAbsent(player, p -> new HashMap<>())
                        .put(update.screen() + "/" + update.element(), update.linesValue());
            }
        }
        return List.copyOf(waiting.values());
    }

    /** Les joueurs qui ont quelque chose à recevoir — la boucle de fin de tick. */
    public List<UUID> pendingPlayers() {
        return pending.isEmpty() ? List.of() : List.copyOf(pending.keySet());
    }

    /** Les joueurs qui ont un écran de ce blueprint ouvert, quel qu'il soit. */
    public List<UUID> viewersOfBlueprint(Identifier blueprint) {
        List<UUID> out = new ArrayList<>();
        open.forEach((player, current) -> {
            if (current.blueprint().equals(blueprint)) {
                out.add(player);
            }
        });
        return out;
    }

    /** Les joueurs qui regardent cet écran — la variante « à tous les spectateurs ». */
    public List<UUID> viewersOf(Identifier blueprint, String screen) {
        List<UUID> out = new ArrayList<>();
        open.forEach((player, current) -> {
            if (current.blueprint().equals(blueprint) && current.screen().equals(screen)) {
                out.add(player);
            }
        });
        // Les HUD comptent aussi : un « set_text_all » sur un tableau de scores
        // permanent viserait sinon les seuls joueurs ayant un menu ouvert.
        huds.forEach((player, screens) -> {
            if (screens.containsKey(screen) && !out.contains(player)) {
                out.add(player);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ nettoyage

    /**
     * Referme tous les écrans d'un blueprint — il vient d'être désactivé, rechargé ou
     * supprimé. Rend les joueurs concernés, à qui il faut envoyer la fermeture.
     *
     * <p>Sans cela, un menu resterait affiché en pointant un blueprint qui n'existe
     * plus : chaque clic serait refusé sans que le joueur comprenne pourquoi.
     */
    public List<UUID> closeAllOf(Identifier blueprint) {
        List<UUID> affected = new ArrayList<>();
        open.entrySet().removeIf(entry -> {
            if (entry.getValue().blueprint().equals(blueprint)) {
                affected.add(entry.getKey());
                return true;
            }
            return false;
        });
        affected.forEach(player -> {
            displayed.remove(player);
            pending.remove(player);
        });
        return affected;
    }

    /**
     * Les HUD de ce blueprint, par joueur — ce qu'il faut retirer quand il est
     * désactivé ou supprimé. Un HUD n'a pas d'{@code Échap} : le laisser affiché en
     * pointant un graphe disparu ne se réparerait plus que par la touche de masquage.
     */
    public Map<UUID, List<String>> takeHudsOf(Identifier blueprint) {
        Map<UUID, List<String>> affected = new LinkedHashMap<>();
        huds.forEach((player, screens) -> {
            List<String> mine = new ArrayList<>();
            screens.forEach((screen, shown) -> {
                if (shown.blueprint().equals(blueprint)) {
                    mine.add(screen);
                }
            });
            if (!mine.isEmpty()) {
                affected.put(player, mine);
            }
        });
        affected.forEach((player, screens) -> screens.forEach(huds.get(player)::remove));
        return affected;
    }

    /** Un joueur parti ne laisse pas d'écran fantôme. */
    /** Ce que ce joueur voit dans cette liste — vide s'il n'a rien reçu. */
    public List<String> linesOf(UUID player, String screen, String element) {
        Map<String, List<String>> byElement = lines.get(player);
        if (byElement == null) {
            return List.of();
        }
        return byElement.getOrDefault(screen + "/" + element, List.of());
    }

    public void forget(UUID player) {
        closed(player);
        huds.remove(player);
        lines.remove(player);
    }

    public int size() {
        return open.size();
    }
}
