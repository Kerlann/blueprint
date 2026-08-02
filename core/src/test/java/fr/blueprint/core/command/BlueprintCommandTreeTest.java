package fr.blueprint.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import fr.blueprint.core.config.BlueprintConfig;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structure de l'arbre Brigadier, vérifiée headless (story 1.5, AC1, AC5). */
class BlueprintCommandTreeTest {

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
