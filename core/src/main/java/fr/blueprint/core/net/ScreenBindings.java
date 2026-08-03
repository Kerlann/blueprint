package fr.blueprint.core.net;

import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Ce qu'un rafraîchissement de liaisons produit (story 10.7).
 *
 * <p>Rend les modifications que les liaisons DEMANDENT, sans se demander lesquelles sont
 * nouvelles : {@link ScreenSessions#queue} compare déjà chacune à ce que le client
 * affiche et écarte celles qui ne changeraient rien (10.4, AC3c). Tenir ici une seconde
 * mémoire de ce qui a été envoyé aurait donné deux tables à garder d'accord — et la
 * première divergence se serait vue comme un écran figé sur une vieille valeur, sans que
 * rien ne l'explique.
 *
 * <p>Ce qui compte pour le diff est donc ce que le spectateur VERRA. Deux valeurs
 * distinctes qui s'affichent pareil produisent la même modification et ne partent qu'une
 * fois : « 3,0001 » et « 3,0002 » à zéro décimale donnent tous deux « 3 ».
 *
 * <p>Logique <b>pure</b> : un écran et une façon de lire les variables. Aucun réseau,
 * aucun serveur — c'est ce qui rend « rien ne part quand rien ne change » vérifiable
 * sans lancer de partie, alors que c'est la promesse la plus difficile à observer en jeu.
 */
public final class ScreenBindings {

    private ScreenBindings() {
    }

    /**
     * Les modifications que les liaisons de cet écran demandent.
     *
     * @param values ce que vaut une variable, par son nom ; {@code null} si elle n'existe
     *               pas ou n'a jamais été écrite
     */
    public static List<ScreenUpdate> updates(Screen screen, Function<String, Object> values) {
        List<ScreenUpdate> out = new ArrayList<>();
        for (ScreenElement element : screen.elements().values()) {
            if (!element.isBound()) {
                continue;
            }
            ElementBinding binding = element.binding();
            Object value = values.apply(binding.variable());
            ScreenUpdate update = updateOf(screen.name(), element.name(), binding, value);
            if (update != null) {
                out.add(update);
            }
        }
        return out;
    }

    private static @Nullable ScreenUpdate updateOf(String screen, String element,
                                                   ElementBinding binding,
                                                   @Nullable Object value) {
        return switch (binding.target()) {
            case TEXT -> new ScreenUpdate(screen, element, ScreenUpdate.Kind.TEXT,
                    binding.renderText(value), false, 0);
            case PROGRESS -> new ScreenUpdate(screen, element, ScreenUpdate.Kind.PROGRESS,
                    "", false, binding.renderProgress(value));
            case ENABLED -> new ScreenUpdate(screen, element, ScreenUpdate.Kind.ENABLED,
                    "", binding.renderFlag(value), 0);
            case VISIBLE -> new ScreenUpdate(screen, element, ScreenUpdate.Kind.VISIBLE,
                    "", binding.renderFlag(value), 0);
            case TEXTURE -> {
                // Une valeur illisible n'efface PAS la texture en place : le joueur
                // verrait son image disparaître sans rien pour l'expliquer, là où garder
                // l'ancienne laisse au moins l'écran cohérent.
                String raw = binding.renderText(value);
                Identifier texture = raw.isBlank() ? null
                        : fr.blueprint.core.graph.screen.PackRef.texture(raw);
                yield texture == null && !raw.isBlank() ? null
                        : new ScreenUpdate(screen, element, ScreenUpdate.Kind.TEXTURE,
                                texture == null ? "" : texture.toString(), false, 0);
            }
        };
    }

    /**
     * Le texte qu'une liaison afficherait — utile au concepteur pour montrer un aperçu
     * sans avoir à exécuter quoi que ce soit.
     */
    public static ScreenText previewText(ElementBinding binding, @Nullable Object value) {
        return ScreenText.literal(binding.renderText(value));
    }
}
