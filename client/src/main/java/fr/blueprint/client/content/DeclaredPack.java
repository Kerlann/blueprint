package fr.blueprint.client.content;

import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.content.ContentLoader;
import fr.blueprint.core.content.ContentPack;
import fr.blueprint.core.content.ContentPackWriter;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Le pack de ressources du contenu déclaré, côté client — story 11.2.
 *
 * <p>Il n'existe que là. Un serveur dédié enregistre les items (11.1) et n'a aucun usage
 * d'une texture : les modèles sont une affaire de client, et le prétendre autrement
 * ferait écrire des fichiers inutiles sur une machine qui n'a pas d'écran.
 *
 * <h2>Pourquoi un vrai dossier dans {@code resourcepacks/}</h2>
 * <p>Fabric sait exposer un pack livré <i>dans le jar</i> d'un mod ; il ne sait pas
 * exposer un pack dont le contenu dépend de fichiers que le joueur vient d'écrire. Les
 * mods qui font cela injectent une source de packs par mixin. Ce projet n'a aucun mixin,
 * et en introduire un pour ce besoin coûterait une configuration de compilation, une
 * dépendance sur des noms internes du jeu et une fonctionnalité invérifiable en test —
 * pour économiser un dossier visible.
 *
 * <p>Un dossier visible est d'ailleurs préférable : le joueur <b>voit</b> le pack dans sa
 * liste, comprend d'où viennent ses textures, et peut le désactiver. Rien de ce que fait
 * Blueprint ici n'est caché, et rien n'est irréversible.
 *
 * <h2>Le rechargement, une seule fois et seulement s'il sert</h2>
 * <p>Recharger les ressources fige le jeu plusieurs secondes. Il n'a lieu que si le
 * contenu du pack a réellement changé, ou s'il a fallu l'activer — c'est-à-dire au premier
 * démarrage et après chaque modification d'un item, jamais autrement.
 */
public final class DeclaredPack {

    private static @Nullable ContentPack pack;
    private static List<String> notices = List.of();
    private static boolean rewritten;
    private static boolean created;
    private static boolean disabledByPlayer;

    private DeclaredPack() {
    }

    /**
     * Construit et écrit le pack. Appelé à l'initialisation du client, c'est-à-dire
     * <b>avant</b> que le jeu ne lise ses ressources la première fois : quand le contenu
     * n'a pas changé, ce démarrage ne coûte donc rien du tout.
     */
    public static void install(Path contentDir, Path resourcePacksDir) {
        ContentLoader.Report report = ContentLoader.load(contentDir);
        ContentPack built = ContentPack.of(report.items().values(), report.blocks().values());
        pack = built;
        var outcome = ContentPackWriter.writeIfChanged(built,
                resourcePacksDir.resolve(ContentPackWriter.DIRECTORY));

        var messages = new java.util.ArrayList<>(built.rejected());
        if (!outcome.ok()) {
            messages.add(outcome.refusal());
            BlueprintMod.LOGGER.warn("Pack du contenu déclaré : {}", outcome.refusal());
        }
        notices = List.copyOf(messages);
        rewritten = outcome.changed();
        created = outcome.created();
        if (outcome.changed()) {
            BlueprintMod.LOGGER.info("Pack du contenu déclaré réécrit : {} item(s) habillé(s)",
                    built.dressed());
        }
    }

    /**
     * Active le pack <b>la première fois seulement</b>, et ne recharge que si nécessaire.
     *
     * <p>À appeler une fois le jeu démarré : le dépôt de packs n'existe pas encore à
     * l'initialisation du mod.
     *
     * <p>L'activation n'a lieu qu'au démarrage qui <b>crée</b> le pack. Réactiver un pack
     * décoché à chaque lancement rendrait la case du menu inopérante — le joueur cocherait
     * et décocherait sans que rien ne tienne, et ne comprendrait pas pourquoi. Un pack
     * présent mais désactivé n'est donc pas une panne à réparer : c'est une décision, et
     * on la dit dans {@code /blueprint-packs} plutôt que de la défaire.
     */
    public static void activate(Minecraft client) {
        if (pack == null || pack.dressed() == 0) {
            return;
        }
        var repository = client.getResourcePackRepository();
        boolean selected = repository.getSelectedIds().contains(ContentPackWriter.PACK_ID);
        boolean justAdded = false;
        if (!selected && created) {
            repository.reload();
            justAdded = repository.addPack(ContentPackWriter.PACK_ID);
            if (justAdded) {
                // Enregistré dans les options : sans cela le pack serait à réactiver à
                // chaque démarrage, et le rechargement qui l'accompagne aussi.
                client.options.updateResourcePacks(repository);
                client.options.save();
            }
        }
        disabledByPlayer = !selected && !justAdded;
        if (justAdded || rewritten) {
            rewritten = false;
            client.reloadResourcePacks();
        }
    }

    /** Ce qu'il faut dire au joueur : items sans image, dossier refusé, pack décoché. */
    public static List<String> notices() {
        return notices;
    }

    /** Vrai quand le pack existe mais que le joueur l'a retiré de sa sélection. */
    public static boolean disabledByPlayer() {
        return disabledByPlayer;
    }

    /** Le nombre d'items réellement habillés. */
    public static int dressed() {
        return pack == null ? 0 : pack.dressed();
    }
}
