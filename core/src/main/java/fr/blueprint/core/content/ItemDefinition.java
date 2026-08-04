package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * Un item <b>déclaré</b> par un fichier, et réellement enregistré (épic 11).
 *
 * <p>C'est un item de plein droit : il a une entrée de registre, il se donne au
 * {@code /give}, il apparaît dans les recettes, il traverse le réseau. Ce n'est pas un
 * item vanilla déguisé.
 *
 * <p><b>Pourquoi un fichier et pas un blueprint.</b> Les registres de Minecraft sont
 * <b>gelés</b> à la fin de l'initialisation des mods, avant qu'aucun monde ne soit chargé.
 * Un blueprint, lui, vit dans la sauvegarde d'un monde — donc après le gel. Il ne peut
 * structurellement pas enregistrer un item, et aucun effort d'ingénierie n'y changera
 * rien. Les définitions sont donc lues sur le disque au démarrage, comme le fait tout mod
 * de contenu.
 *
 * <p>Conséquence à assumer : <b>ajouter un item demande un redémarrage</b>. Le dire est
 * plus honnête que de faire semblant de recharger et de laisser le joueur découvrir que
 * son item n'existe pas.
 *
 * <p>Record pur, sans Minecraft au-delà des types de valeurs : la lecture, la validation
 * et le refus se vérifient sans lancer de jeu — et c'est nécessaire, puisque le code qui
 * les utilise tourne <i>avant</i> qu'un serveur existe.
 *
 * @param id        l'identifiant de registre, {@code blueprint:<nom>}
 * @param name      le nom affiché ; vide = la clé de traduction par défaut de l'item
 * @param translate vrai si {@code name} est une clé de langue plutôt qu'un texte
 * @param stackSize taille de pile, de 1 à 99
 * @param rarity    la couleur du nom dans l'infobulle
 * @param texture   le PNG déposé à côté du fichier, ou {@code null} — damier magenta
 */
public record ItemDefinition(Identifier id, String name, boolean translate,
                             int stackSize, Rarity rarity, @Nullable String texture) {

    /** Ce que Minecraft accepte comme taille de pile. */
    public static final int MAX_STACK = 99;

    /**
     * Les caractères d'un nom de fichier utilisable comme chemin de registre.
     *
     * <p>Minecraft refuserait un identifiant hors de cet alphabet en levant, à
     * l'initialisation, c'est-à-dire <b>avant que le jeu ne démarre</b> : un seul fichier
     * mal nommé empêcherait le mod entier de se charger. On le refuse donc nous-mêmes,
     * avec un message qui nomme le fichier.
     */
    public static final String PATH = "[a-z0-9_.-]+";

    public ItemDefinition {
        if (id == null) {
            throw new IllegalArgumentException("un item déclaré doit avoir un identifiant");
        }
        if (name == null) {
            name = "";
        }
        if (rarity == null) {
            rarity = Rarity.COMMON;
        }
        // Borné plutôt que refusé : une pile de zéro est une faute de frappe évidente, et
        // rendre l'item inutilisable pour cela serait disproportionné.
        stackSize = Math.clamp(stackSize, 1, MAX_STACK);
    }

    /** Un item minimal : ce que produit un fichier ne portant qu'un nom. */
    public static ItemDefinition of(Identifier id) {
        return new ItemDefinition(id, "", false, 64, Rarity.COMMON, null);
    }

    /** Vrai si l'auteur a fourni une texture à côté de son fichier. */
    public boolean hasTexture() {
        return texture != null && !texture.isBlank();
    }
}
