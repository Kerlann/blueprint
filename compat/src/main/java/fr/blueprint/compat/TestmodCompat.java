package fr.blueprint.compat;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.NodeRegistryImpl;
import net.minecraft.resources.Identifier;

/**
 * Intégration de référence (story 8.4, AC2) : le modèle à copier pour brancher
 * Blueprint sur un mod tiers.
 *
 * <p>Elle vise {@code blueprint_testmod} — présent en développement, absent partout
 * ailleurs : exactement la situation d'une intégration réelle, et donc la bonne façon
 * de vérifier que Blueprint démarre pareil dans les deux cas.
 *
 * <p><b>Ce qu'elle ne fait PAS, et c'est le point important :</b> elle ne référence
 * aucune classe du mod visé. Une intégration qui importerait {@code com.exemple.Machin}
 * ferait tomber Blueprint au chargement de classe dès que le mod est absent — la garde
 * {@code isModLoaded} arriverait trop tard. Pour appeler l'API d'un mod tiers, deux
 * voies sûres : un module compilé contre lui avec {@code compileOnly} et une classe
 * séparée, chargée seulement après la garde ; ou la réflexion. Le modèle ci-dessous se
 * contente d'ajouter un nœud qui vit dans l'univers de Blueprint.
 */
final class TestmodCompat implements CompatLoader.Integration {

    static final Identifier GREET = Identifier.fromNamespaceAndPath("blueprint", "compat/testmod_greet");

    @Override
    public String modId() {
        return "blueprint_testmod";
    }

    @Override
    public void register(NodeRegistryImpl nodes) {
        nodes.register(NodeType.builder(GREET)
                .category(NodeCategories.STRING)
                .pure()
                .in("nom", PinTypes.STRING, "monde")
                .out("salutation", PinTypes.STRING)
                .permission(Permission.SAFE)
                .action(ctx -> ctx.out("salutation",
                        "Bonjour " + ctx.<String>in("nom") + ", depuis le mod d'exemple"))
                .build());
    }
}
