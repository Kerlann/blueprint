package fr.blueprint.client;

import fr.blueprint.client.editor.BlueprintEditorScreen;
import fr.blueprint.client.editor.EditorSession;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.BenchBlueprint;
import fr.blueprint.platform.Platform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Le démarrage du client.
 *
 * <p>Comme {@code BlueprintMod} côté serveur, cette classe <b>était</b> un point d'entrée
 * Fabric ({@code implements ClientModInitializer}) et ne l'est plus : le module du
 * chargeur appelle {@link #init()} et lui pousse chaque fin de tick par
 * {@link #endClientTick}.
 *
 * <p>Elle <b>ne construit plus aucune commande</b>. Elle en portait deux racines,
 * {@code /blueprint-edit} et {@code /blueprint-packs} : la première n'était qu'un alias qui
 * réécrivait {@code /blueprint edit} et le renvoyait au serveur, la seconde est devenue
 * {@code /blueprint packs} — une commande serveur qui transmet la demande par
 * {@link #applyPacksAction}. Le mod n'a donc plus qu'une seule racine.
 *
 * <p>Les fondre dans une racine <i>cliente</i> nommée {@code blueprint} aurait été le geste
 * évident et il aurait tout cassé : Fabric ne renvoie au serveur que les commandes
 * <b>inconnues</b> ({@code dispatcherUnknownCommand}), pas celles dont seul le sous-chemin
 * manque. Un {@code /blueprint list} aurait levé {@code dispatcherUnknownArgument} chez le
 * client, qui aurait répondu « argument incorrect » sans jamais rien envoyer au serveur.
 */
public class BlueprintClient {

    /** Les deux touches du mod, gardées pour la boucle de tick. */
    private static KeyMapping openEditor;
    private static KeyMapping toggleHud;

    /** Le premier tick où le gestionnaire de textures existe (voir {@link #endClientTick}). */
    private static boolean texturesLoaded;

    public static void init() {
        // Le pack du contenu déclaré (11.2), AVANT tout le reste : le jeu lit ses
        // ressources une première fois pendant son démarrage, et écrire après lui
        // reviendrait à imposer un rechargement à chaque lancement.
        fr.blueprint.client.content.DeclaredPack.install(
                fr.blueprint.core.BlueprintPaths.content(),
                Platform.paths().gameDir().resolve("resourcepacks"));

        var client = Platform.client();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(BlueprintMod.MOD_ID, "main"));
        openEditor = client.registerKey(new KeyMapping(
                "key.blueprint.open_editor", GLFW.GLFW_KEY_F6, category));
        // La bascule des HUD est une GARDE DE SÉCURITÉ, pas un confort (10.9, AC5).
        // Un écran modal a toujours Échap ; un HUD n'a rien. Un graphe fautif affichant
        // un panneau opaque plein écran laisserait le joueur sans aucun recours : il
        // verrait son monde caché sans que rien de ce qu'il tape ne le retire.
        toggleHud = client.registerKey(new KeyMapping(
                "key.blueprint.toggle_hud", GLFW.GLFW_KEY_F7, category));

        // Les huit touches d'action (11.4), toutes NON ASSIGNÉES : en prendre huit à
        // quelqu'un qui n'a peut-être aucun blueprint serait indéfendable. Le jeu les
        // montre dans ses commandes, où elles attendent sans rien coûter.
        fr.blueprint.client.content.BlueprintKeys.register(category);

        // Synchro du registre serveur (6.2) puis ouverture/enregistrement réseau (6.3).
        fr.blueprint.client.net.RegistrySync.register();
        fr.blueprint.client.net.BlueprintNet.register();
        fr.blueprint.client.net.DebugClient.register();
        fr.blueprint.client.net.ScreenClient.register();
        fr.blueprint.client.screen.BlueprintHud.register();

        BlueprintMod.LOGGER.info("Blueprint client initialisé");
    }

    /**
     * Le client rejoint un serveur.
     *
     * <p>Un seul fil, comme pour le tick : le chargeur ne connaît pas les quatre parties
     * qui s'y intéressent, et il n'a pas à les connaître.
     */
    public static void onJoin() {
        fr.blueprint.client.net.BlueprintNet.onJoin();
    }

    /**
     * Le client quitte un serveur. Rien de ce serveur ne doit lui survivre — ni le
     * registre reçu, ni les bornes, ni l'éditeur ouvert, ni les écrans à l'image.
     *
     * <p>L'ordre est celui dans lequel les quatre abonnements s'exécutaient : il tenait à
     * l'ordre des appels dans {@link #init()}, il est maintenant écrit ici.
     */
    public static void onDisconnect() {
        fr.blueprint.client.net.RegistrySync.onDisconnect();
        fr.blueprint.client.net.BlueprintNet.onDisconnect();
        fr.blueprint.client.net.DebugClient.onDisconnect();
        fr.blueprint.client.net.ScreenClient.onDisconnect();
    }

    /**
     * Tout ce qui se fait à chaque fin de tick client. Appelé par le module du chargeur.
     *
     * <p>Les deux abonnements d'origine sont réunis ici : le chargeur n'a plus qu'un fil à
     * brancher, et leur ordre relatif — le travail par tick d'abord, l'amorçage unique des
     * textures ensuite — devient une propriété du code plutôt que de l'ordre
     * d'enregistrement.
     */
    public static void endClientTick(Minecraft mc) {
        // F6 ouvre le NAVIGATEUR, pas une démo. Reprendre le dernier blueprint édité
        // paraissait pratique et ne l'était pas : on ne pouvait plus en atteindre un
        // autre sans passer par la commande, l'identifiant complet tapé de mémoire.
        while (openEditor.consumeClick()) {
            if (fr.blueprint.client.net.BlueprintNet.connected()) {
                mc.setScreen(new fr.blueprint.client.browser.BlueprintBrowserScreen());
            } else {
                openDemoEditor(mc);
            }
        }
        fr.blueprint.client.content.BlueprintKeys.tick();
        // Les liaisons de source CLIENT, recalculées ici et nulle part ailleurs.
        // C'est le partage du travail : le serveur pousse ce que lui seul sait — un
        // prénom, un métier, un solde — et le client peint ce qu'il a déjà. Une barre
        // de vie tenue par le serveur lui coûterait, à cinquante joueurs, mille
        // lectures et jusqu'à mille paquets par seconde pour une valeur affichée
        // depuis toujours dans les cœurs du joueur.
        fr.blueprint.client.screen.BlueprintHud.view().refreshClientBindings(
                name -> fr.blueprint.client.screen.ClientValues.of(mc.player, name));
        // Et les liaisons de VARIABLE depuis les valeurs répliquées (épic 21) — ici et pas au
        // moment où le paquet arrive : le serveur peut envoyer plusieurs trames dans le même
        // tick, et recalculer à chacune referait le travail autant de fois.
        //
        // Ce que le catalogue fermé de ClientValues ne pouvait pas donner : une barre de mana
        // aussi fluide qu'une barre de vie. C'était le manque que cet épic existe pour combler.
        fr.blueprint.client.screen.BlueprintHud.view().refreshVariableBindings();
        if (mc.screen instanceof fr.blueprint.client.screen.BlueprintScreen bp) {
            bp.refreshClientBindings(mc.player);
        }
        while (toggleHud.consumeClick()) {
            var view = fr.blueprint.client.screen.BlueprintHud.view();
            view.toggleHidden();
            mc.gui.setOverlayMessage(view.hidden()
                    ? Component.translatable("blueprint.hud.hidden")
                    : Component.translatable("blueprint.hud.shown"), false);
        }

        // Au démarrage, une fois : sans cela le premier menu à images d'une session
        // montrerait des damiers, et le joueur croirait son pack cassé.
        if (!texturesLoaded && mc.getTextureManager() != null) {
            texturesLoaded = true;
            fr.blueprint.client.pack.PackTextures.reload(
                    fr.blueprint.core.BlueprintPaths.scripts());
            // Le dépôt de packs n'existe pas à l'initialisation du mod : c'est
            // ici, et pas plus tôt, qu'on peut activer celui du contenu déclaré.
            fr.blueprint.client.content.DeclaredPack.activate(mc);
        }
    }

    /**
     * Ce que demande {@code /blueprint packs} — la commande vit côté serveur, le travail
     * ici (story 10.5, paquet {@code PacksAction}).
     *
     * <p>Le geste arrive par le réseau et non par une commande cliente pour que
     * {@code /blueprint} reste la seule racine du mod : une racine cliente du même nom
     * aurait avalé tout l'arbre serveur, Fabric ne renvoyant au serveur que les commandes
     * inconnues — pas celles dont seul le sous-chemin manque.
     */
    public static void applyPacksAction(boolean reload, Consumer<Component> say) {
        if (reload) {
            reloadPacks(say);
        } else {
            listPacks(say);
        }
    }

    /**
     * Les packs installés, et ce qui a été écarté.
     *
     * <p>Les rejets sont montrés <b>au joueur</b>, pas seulement au log : celui dont
     * l'image ne s'affiche pas ne pense pas à ouvrir {@code latest.log}, et c'est
     * exactement lui qui a besoin de la raison.
     */
    private static void listPacks(Consumer<Component> say) {
        var packs = fr.blueprint.client.pack.PackTextures.packs();
        if (packs.isEmpty()) {
            say.accept(Component.translatable("blueprint.pack.none",
                    fr.blueprint.core.BlueprintPaths.scripts().toString()));
        } else {
            say.accept(Component.translatable("blueprint.pack.header", packs.size()));
            for (var pack : packs.values()) {
                say.accept(Component.literal("- " + pack.summary()));
            }
        }
        for (var rejection : fr.blueprint.client.pack.PackTextures.rejections()) {
            say.accept(Component.translatable("blueprint.pack.rejected",
                    rejection.pack(), rejection.detail()));
        }
        // Le pack du contenu déclaré (11.2) est de nature différente — il est généré, pas
        // déposé — mais il vit sur le même disque et se diagnostique avec les mêmes yeux.
        // Le taire ici obligerait à ouvrir le journal pour savoir pourquoi un item reste
        // en damier, ce qui est exactement ce que ces commandes existent pour éviter.
        say.accept(Component.translatable("blueprint.cmd.content_pack",
                fr.blueprint.client.content.DeclaredPack.dressed()));
        if (fr.blueprint.client.content.DeclaredPack.disabledByPlayer()) {
            say.accept(Component.translatable("blueprint.cmd.content_pack_off"));
        }
        for (var notice : fr.blueprint.client.content.DeclaredPack.notices()) {
            say.accept(Component.translatable("blueprint.cmd.content_pack_notice", notice));
        }
    }

    private static void reloadPacks(Consumer<Component> say) {
        var result = fr.blueprint.client.pack.PackTextures.reload(
                fr.blueprint.core.BlueprintPaths.scripts());
        say.accept(Component.translatable("blueprint.pack.reloaded",
                result.packs().size(), result.rejections().size()));
        for (var rejection : result.rejections()) {
            say.accept(Component.translatable("blueprint.pack.rejected",
                    rejection.pack(), rejection.detail()));
        }
    }

    private static void openDemoEditor(Minecraft mc) {
        var registries = BlueprintMod.registries();
        mc.setScreen(new BlueprintEditorScreen(
                EditorSession.scratch(BenchBlueprint.build(registries.nodes())),
                registries, fr.blueprint.client.net.RegistrySync.descriptors(),
                fr.blueprint.client.net.RegistrySync.lookup()));
    }
}
