package fr.blueprint.core.vm;

import org.jetbrains.annotations.Nullable;

/**
 * Le poids d'une valeur de variable, et le plafond par joueur (NFR14).
 *
 * <p>NFR14 demande que les données d'un joueur « soient supprimables et n'excèdent pas
 * 64 Ko par joueur ». Ni le plafond ni la suppression n'existaient : tout vivait dans le
 * {@code SavedData} du monde sans aucune borne, et rien ne permettait d'effacer un joueur.
 * Sur un serveur public, un graphe qui écrit une entrée d'historique par mort — cas
 * parfaitement ordinaire — faisait grossir la sauvegarde du monde sans limite et sans
 * qu'aucun symptôme ne le dise avant que le fichier ne devienne difficile à écrire.
 *
 * <h2>Une estimation, pas une mesure</h2>
 *
 * <p>Le poids exact serait celui du NBT produit par {@code VarStorage}, donc un encodage
 * complet à <b>chaque écriture</b> — dans le chemin de la VM. L'estimation ci-dessous est
 * volontairement <b>majorante</b> : une chaîne compte deux octets par caractère alors que
 * l'UTF-8 en écrit souvent un. Se tromper vers le haut refuse un peu tôt ; se tromper vers
 * le bas laisse passer ce que le plafond existe pour interdire, et c'est la seule des deux
 * erreurs qui ne se voit pas.
 *
 * <h2>Pourquoi les deux portées joueur</h2>
 *
 * <p>NFR14 nomme {@code PLAYER}, mais {@code PLAYER_SHARED} est né après lui et porte tout
 * autant les données d'un joueur — un prénom de jeu de rôle, un solde de banque. Compter la
 * première sans la seconde donnerait un plafond qu'il suffit de contourner en changeant un
 * mot-clé, et une suppression qui laisse la moitié des données derrière elle : ce n'est pas
 * ce que « supprimables » veut dire.
 */
public final class VarQuota {

    /** 64 Ko par joueur, toutes portées joueur confondues (NFR14). */
    public static final int MAX_PLAYER_BYTES = 64 * 1024;

    /**
     * Coût forfaitaire d'une entrée, en plus de son nom et de sa valeur : l'étiquette de
     * type et l'en-tête de clé que le NBT écrit autour de chacune.
     */
    private static final int ENTRY_OVERHEAD = 8;

    /**
     * Profondeur au-delà de laquelle on cesse de descendre.
     *
     * <p>Ce n'est pas une précaution théorique : {@code list/add} peut ajouter une liste à
     * elle-même, et une mesure récursive sans borne se terminerait alors par un
     * {@code StackOverflowError} — dans le chemin d'écriture de la VM, c'est-à-dire à
     * l'endroit exact que NFR4 interdit d'atteindre. La valeur profonde compte pour son
     * forfait ; ce qui ne se persiste pas ne se mesure pas plus finement.
     *
     * <p><b>Alignée sur celle de {@code VarValueNbt}</b>, et pas plus basse. Elle valait huit
     * quand l'encodeur n'en avait aucune : tout ce qui était plus profond était donc facturé
     * un forfait de {@link #UNKNOWN} octets alors qu'il se persistait entièrement — un graphe
     * qui imbrique des listes passait le plafond de 64 Ko sans le voir. Les deux bornes
     * doivent être la même : au-delà, l'encodeur refuse, donc le forfait ne facture plus rien
     * qui existe.
     */
    private static final int MAX_DEPTH = 16;

    /** Ce que coûte une valeur dont on ne sait rien : assez pour qu'elle pèse. */
    private static final int UNKNOWN = 64;

    private VarQuota() {
    }

    /** Le poids estimé d'une entrée nommée, en octets. */
    public static int entrySize(String name, @Nullable Object value) {
        return ENTRY_OVERHEAD + 2 * name.length() + sizeOf(value);
    }

    /** Le poids estimé d'une valeur, en octets. Jamais négatif, ne lève jamais. */
    public static int sizeOf(@Nullable Object value) {
        return sizeOf(value, 0);
    }

    private static int sizeOf(@Nullable Object value, int depth) {
        if (value == null) {
            return 0;
        }
        if (depth >= MAX_DEPTH) {
            return UNKNOWN;
        }
        return switch (value) {
            case Boolean ignored -> 1;
            case Byte ignored -> 1;
            case Short ignored -> 2;
            case Integer ignored -> 4;
            case Float ignored -> 4;
            case Long ignored -> 8;
            case Double ignored -> 8;
            // Deux octets par caractère et non un : majorant sûr pour un texte accentué,
            // et le préfixe de longueur que le NBT écrit devant.
            case String s -> 2 + 2 * s.length();
            case net.minecraft.core.BlockPos ignored -> 8;
            case net.minecraft.world.phys.Vec3 ignored -> 26;
            case net.minecraft.core.Direction d -> 2 + 2 * d.getSerializedName().length();
            case java.util.UUID ignored -> 16;
            case net.minecraft.resources.Identifier id -> 2 + 2 * id.toString().length();
            case java.util.List<?> list -> {
                int total = 4;   // l'en-tête de liste NBT
                for (Object element : list) {
                    total += sizeOf(element, depth + 1);
                    if (total > MAX_PLAYER_BYTES) {
                        // Inutile de finir de compter : le plafond est déjà dépassé, et
                        // parcourir un million d'entrées pour l'apprendre serait le genre
                        // de coût que le plafond existe pour empêcher.
                        yield total;
                    }
                }
                yield total;
            }
            case java.util.Map<?, ?> map -> {
                int total = 4;
                for (var entry : map.entrySet()) {
                    total += ENTRY_OVERHEAD + sizeOf(entry.getKey(), depth + 1)
                            + sizeOf(entry.getValue(), depth + 1);
                    if (total > MAX_PLAYER_BYTES) {
                        yield total;
                    }
                }
                yield total;
            }
            default -> UNKNOWN;
        };
    }
}
