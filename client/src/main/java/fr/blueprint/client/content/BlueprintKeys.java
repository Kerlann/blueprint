package fr.blueprint.client.content;

import fr.blueprint.core.net.BlueprintPayloads;
import fr.blueprint.core.net.ServerBlueprintNet;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Les <b>touches de Blueprint</b> — story 11.4.
 *
 * <p>Huit emplacements, que le joueur assigne dans le menu des commandes du jeu, et qu'un
 * graphe écoute par leur <b>numéro</b>. Presser la touche exécute le graphe côté serveur.
 *
 * <h2>Pourquoi des emplacements et non des touches</h2>
 * <p>Le graphe ne voit jamais quelle touche a été pressée : il voit l'emplacement 3. Un
 * code de touche physique varie d'un clavier à l'autre — un script écrit en AZERTY
 * cesserait de fonctionner en QWERTY — et il dirait au serveur ce que le joueur tape, ce
 * qu'aucun graphe n'a de raison de savoir.
 *
 * <p>Cette indirection a un second effet, plus important encore : le <b>joueur</b> choisit
 * la touche. L'auteur du graphe déclare « emplacement 3 », le joueur décide que ce sera
 * {@code K}, et le conflit avec un autre mod se règle là où le jeu règle déjà tous les
 * conflits de touches.
 *
 * <h2>Non assignées au départ, et c'est délibéré</h2>
 * <p>Assigner huit touches par défaut reviendrait à en prendre huit à quelqu'un qui
 * n'a peut-être aucun blueprint. Le jeu affiche les commandes non assignées ; celles-ci
 * y attendent, visibles, sans rien coûter.
 */
public final class BlueprintKeys {

    private static final KeyMapping[] KEYS = new KeyMapping[ServerBlueprintNet.KEY_SLOTS];

    private BlueprintKeys() {
    }

    /** Déclare les touches. À appeler à l'initialisation du client. */
    public static void register(KeyMapping.Category category) {
        for (int slot = 1; slot <= ServerBlueprintNet.KEY_SLOTS; slot++) {
            KEYS[slot - 1] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.blueprint.action_" + slot, GLFW.GLFW_KEY_UNKNOWN, category));
        }
    }

    /**
     * Envoie au serveur les pressions du tick. À appeler dans la boucle de tick client.
     *
     * <p>{@code consumeClick} ne rend vrai qu'une fois par pression : maintenir la touche
     * n'envoie rien de plus. C'est ce qu'on veut — une touche maintenue vaut une action,
     * pas soixante par seconde, et l'inverse aurait fait du quota de cadence une limite
     * qu'on atteint sans le vouloir.
     */
    public static void tick() {
        if (!ClientPlayNetworking.canSend(BlueprintPayloads.KeyPress.TYPE)) {
            // Serveur sans Blueprint, ou pas encore connecté. Les pressions sont tout de
            // même consommées : les garder en attente ferait partir une rafale au moment
            // de la connexion, pour des touches pressées longtemps avant.
            drain();
            return;
        }
        for (int slot = 1; slot <= KEYS.length; slot++) {
            KeyMapping key = KEYS[slot - 1];
            if (key == null) {
                continue;
            }
            boolean pressed = false;
            while (key.consumeClick()) {
                pressed = true;
            }
            // Une seule fois par tick, même si la touche a cliqué plusieurs fois : le
            // graphe verrait sinon deux exécutions pour un geste que le joueur croit
            // unique.
            if (pressed) {
                ClientPlayNetworking.send(new BlueprintPayloads.KeyPress(slot));
            }
        }
    }

    private static void drain() {
        for (KeyMapping key : KEYS) {
            if (key != null) {
                while (key.consumeClick()) {
                    // vidée, ignorée
                }
            }
        }
    }
}
