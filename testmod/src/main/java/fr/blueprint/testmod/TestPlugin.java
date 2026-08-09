package fr.blueprint.testmod;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.resources.Identifier;

/**
 * Mod d'exemple : il ne dépend que du module {@code api} et déclare trois nœuds — la
 * preuve vivante du contrat d'{@code extension-api.md}.
 *
 * <p>Il se déclare par les <b>deux</b> voies, entrypoint et service, comme un mod tiers
 * qui voudrait marcher sur tous les chargeurs. C'est aussi ce qui vérifie en vrai que
 * Blueprint ne le charge qu'une fois.
 */
public final class TestPlugin implements BlueprintPlugin {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint_testmod", path);
    }

    @Override
    public String modId() {
        return "blueprint_testmod";
    }

    @Override
    public void registerNodes(NodeRegistry registry) {
        // 1. Nœud à exec avec entrée par défaut : renvoie son message en écho.
        registry.register(NodeType.builder(id("ping"))
                .category(NodeCategories.DEBUG)
                .exec()
                .in("message", PinTypes.STRING, "ping")
                .out("echo", PinTypes.STRING)
                .action(ctx -> ctx.out("echo", ctx.<String>in("message")))
                .build());

        // 2. Nœud pur : double un entier.
        registry.register(NodeType.builder(id("double_it"))
                .category(NodeCategories.MATH)
                .pure()
                .in("value", PinTypes.INT)
                .out("result", PinTypes.INT)
                .action(ctx -> ctx.out("result", ctx.<Integer>in("value") * 2))
                .build());

        // 3. Nœud de flux à branches : pair ou impair.
        registry.register(NodeType.builder(id("odd_or_even"))
                .category(NodeCategories.FLOW)
                .execIn("exec_in")
                .execOut("even")
                .execOut("odd")
                .in("value", PinTypes.INT, 0)
                .action(ctx -> ctx.exec(ctx.<Integer>in("value") % 2 == 0 ? "even" : "odd"))
                .build());

        // 4. Nœuds déduits d'une signature de méthode (story 8.1) : même registre,
        // mêmes règles — seule la façon de les déclarer change.
        fr.blueprint.api.annotation.AnnotatedNodes.register(registry, ShoutNodes.class);
    }
}
