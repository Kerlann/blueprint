package fr.blueprint.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import fr.blueprint.core.config.BlueprintConfig;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structure de l'arbre Brigadier, vérifiée headless (story 1.5, AC1, AC5). */
class BlueprintCommandTreeTest {

    /**
     * {@code edit} vit dans le MÊME arbre que le reste, et c'est désormais le SEUL chemin :
     * l'alias client {@code /blueprint-edit} est supprimé. Il ne décidait plus rien — il
     * réécrivait la commande et la renvoyait ici — mais il obligeait à maintenir deux jeux
     * de suggestions, dont l'un lisait une liste reçue à la connexion, périmée dès la
     * première création.
     */
    @Test
    void editVitDansLeMemeArbreQueLeReste() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(BlueprintCommand.build(BlueprintConfig.DEFAULT));
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("blueprint");

        CommandNode<CommandSourceStack> edit = root.getChild("edit");
        assertNotNull(edit, "/blueprint edit absent");
        assertNotNull(edit.getCommand(), "sans argument, il ouvre le navigateur");
        assertInstanceOf(ArgumentCommandNode.class, edit.getChild("id"),
                "avec un argument, il ouvre ce blueprint");
    }

    /**
     * {@code enable}/{@code disable} acceptent « all » : une commande par blueprint
     * devient pénible dès qu'on charge les exemples. Le littéral doit précéder
     * l'argument, sinon Brigadier lit « all » comme un identifiant.
     */
    @Test
    void enableEtDisableAcceptentAll() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(BlueprintCommand.build(BlueprintConfig.DEFAULT));
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("blueprint");

        for (String sub : List.of("enable", "disable")) {
            CommandNode<CommandSourceStack> all = root.getChild(sub).getChild("all");
            assertNotNull(all, "« all » absent sur : " + sub);
            assertNotNull(all.getCommand(), sub + " all doit s'exécuter");
        }
    }

    @Test
    void treeExposesAllSubcommandsWithIdArguments() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(BlueprintCommand.build(BlueprintConfig.DEFAULT));

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("blueprint");
        assertNotNull(root, "racine /blueprint absente");

        for (String sub : List.of("list", "create", "delete", "enable", "disable", "info")) {
            assertNotNull(root.getChild(sub), "sous-commande absente : " + sub);
        }
        // list n'a pas d'argument ; les autres portent un argument « id ».
        assertNull(root.getChild("list").getChild("id"));
        for (String sub : List.of("create", "delete", "enable", "disable", "info")) {
            CommandNode<CommandSourceStack> id = root.getChild(sub).getChild("id");
            assertNotNull(id, "argument id absent sur : " + sub);
            assertInstanceOf(ArgumentCommandNode.class, id);
        }
    }

    @Test
    void adminSubcommandsCarryARequirement() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(BlueprintCommand.build(BlueprintConfig.DEFAULT));
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("blueprint");

        // Impossible d'évaluer le prédicat sans serveur, mais sa présence se vérifie :
        // un nœud sans requires expose un prédicat « toujours vrai » partagé.
        var open = root.getChild("list").getRequirement();
        for (String sub : List.of("create", "delete", "enable", "disable")) {
            assertTrue(root.getChild(sub).getRequirement() != open,
                    "requires manquant sur : " + sub);
        }
    }

    /**
     * Une seule racine, et c'est {@code blueprint}.
     *
     * <p>Le mod en posait trois : {@code /blueprint} côté serveur, {@code /blueprint-edit} et
     * {@code /blueprint-packs} côté client. Les deux dernières ont disparu — la première
     * n'était qu'un alias, la seconde est devenue {@code /blueprint packs}.
     *
     * <p>Les fondre dans une racine <b>cliente</b> nommée {@code blueprint} aurait été le
     * geste évident et aurait tout cassé : Fabric ne renvoie au serveur que les commandes
     * inconnues, pas celles dont seul le sous-chemin manque. C'est ce que ce test garde —
     * si {@code packs} réapparaît côté client, ce n'est plus une racine de plus, c'est
     * {@code /blueprint list} qui cesse d'atteindre le serveur.
     */
    @Test
    void uneSeuleRacineEtToutVitDedans() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(BlueprintCommand.build(BlueprintConfig.DEFAULT));

        assertEquals(1, dispatcher.getRoot().getChildren().size(),
                "l'arbre du mod ne pose qu'une racine");
        assertNotNull(dispatcher.getRoot().getChild("blueprint"));
    }

    /**
     * {@code packs} est ouverte à tous, contrairement à {@code vars} : ce sont les fichiers
     * du joueur, sur son disque, et il n'agit que sur les siens. Exiger la permission
     * d'administrateur pour lire ses propres images serait le contraire d'un diagnostic.
     */
    @Test
    void packsEstOuverteEtPorteSesDeuxGestes() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(BlueprintCommand.build(BlueprintConfig.DEFAULT));
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("blueprint");

        CommandNode<CommandSourceStack> packs = root.getChild("packs");
        assertNotNull(packs, "/blueprint packs absent");
        assertNotNull(packs.getCommand(), "sans argument, elle liste");
        assertNotNull(packs.getChild("list"));
        assertNotNull(packs.getChild("reload"));
        assertEquals(root.getChild("list").getRequirement(), packs.getRequirement(),
                "ouverte comme /blueprint list : ce sont les packs de l'appelant");
    }

    /** {@code vars}, elle, touche aux données d'un autre joueur : réservée aux admins. */
    @Test
    void varsExigeLaPermissionDAdministrateur() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(BlueprintCommand.build(BlueprintConfig.DEFAULT));
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("blueprint");

        CommandNode<CommandSourceStack> vars = root.getChild("vars");
        assertNotNull(vars, "/blueprint vars absent");
        assertTrue(vars.getRequirement() != root.getChild("list").getRequirement(),
                "requires manquant sur vars");
        for (String sub : List.of("info", "purge")) {
            assertInstanceOf(ArgumentCommandNode.class, vars.getChild(sub).getChild("player"),
                    "argument player absent sur : " + sub);
        }
    }

    @Test
    void configMapsLevelsToPermissions() {
        // Le mapping est total et borné (AC3) : 0 (et en-dessous) = ouvert, 1-4+ = permission.
        assertNull(new BlueprintConfig(-1).adminPermission());
        assertNull(new BlueprintConfig(0).adminPermission());
        for (int level = 1; level <= 5; level++) {
            assertNotNull(new BlueprintConfig(level).adminPermission(), "niveau " + level);
        }
    }
}
