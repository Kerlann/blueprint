package fr.blueprint.core.graph.screen;

import org.jetbrains.annotations.Nullable;

/**
 * Ce qu'un écran peut lire <b>sans rien demander au serveur</b>.
 *
 * <p>Le client connaît déjà l'état de son joueur : il le reçoit dans les paquets du jeu,
 * il le dessine dans ses propres cœurs et sa propre barre de faim. Lier un élément d'écran
 * à l'une de ces valeurs ne coûte donc <b>rien</b> — ni variable côté serveur, ni tick, ni
 * paquet — et se rafraîchit à l'image plutôt qu'au prochain {@code gui/refresh}.
 *
 * <p>La liste est <b>fermée</b>, et c'est délibéré. Une expression libre du genre
 * {@code player.getAttribute(...)} aurait donné un langage de plus à apprendre, à valider
 * et à faire tenir entre deux versions de Minecraft ; une énumération se complète par
 * l'infobulle du concepteur et casse au moment de la compilation quand le jeu bouge.
 *
 * <p><b>Uniquement l'état du joueur qui regarde.</b> Rien ici ne parle des autres joueurs,
 * du monde ou du contenu d'un coffre : le client n'en sait pas plus que ce que le serveur
 * lui a déjà envoyé, et prétendre le contraire donnerait un écran qui ment hors de portée
 * de vue. Pour tout le reste, il y a les variables — c'est le partage.
 *
 * <h2>Ce que ça change en pratique</h2>
 *
 * <p>Une barre de vie liée à une variable oblige le serveur à parcourir les joueurs
 * connectés à chaque tick, lire la vie de chacun, l'écrire, et envoyer une modification.
 * À cinquante joueurs, c'est mille lectures et jusqu'à mille paquets par seconde pour une
 * information que chaque client possède déjà. La même barre liée à {@link #HEALTH} ne
 * déclenche aucune de ces opérations.
 */
public enum ClientValue {

    /** Points de vie du joueur, de 0 à son maximum. */
    HEALTH("health"),
    /** Le maximum courant — un attribut, donc pas toujours vingt. */
    MAX_HEALTH("max_health"),
    /** Le cœur jaune d'absorption, qui s'ajoute par-dessus. */
    ABSORPTION("absorption"),
    /** Niveau de nourriture, de 0 à 20. */
    FOOD("food"),
    /** Saturation : ce qui s'épuise avant la nourriture, et que rien n'affiche en vanilla. */
    SATURATION("saturation"),
    /** Points d'armure, de 0 à 20. */
    ARMOR("armor"),
    /** Niveau d'expérience. */
    XP_LEVEL("xp_level"),
    /** Progression dans le niveau courant, de 0 à 1. */
    XP_PROGRESS("xp_progress"),
    /** Air restant sous l'eau, en ticks. */
    AIR("air"),
    /** Le nom du compte — pas le nom de personnage, qui est affaire de graphe. */
    NAME("name"),
    /** Les trois coordonnées, séparées : une seule chaîne « x y z » serait infiltrable. */
    X("x"),
    Y("y"),
    Z("z"),
    /** L'identifiant de la dimension, {@code minecraft:overworld} et compagnie. */
    DIMENSION("dimension");

    /**
     * Le préfixe qui distingue une valeur client d'un nom de variable dans le texte.
     *
     * <p>En BScript et dans le concepteur, {@code @vie} ne peut pas se confondre avec une
     * variable nommée {@code vie} — et l'auteur voit d'un coup d'œil ce qui voyage et ce
     * qui ne voyage pas. Le modèle, lui, porte la distinction dans
     * {@link ElementBinding.Source} : le préfixe est une commodité d'écriture, jamais la
     * vérité.
     */
    public static final String PREFIX = "@";

    private final String key;

    ClientValue(String key) {
        this.key = key;
    }

    /** Le nom tel qu'il s'écrit dans une liaison, sans le préfixe. */
    public String key() {
        return key;
    }

    /** Le nom tel qu'il s'écrit en BScript et se lit dans le concepteur. */
    public String prefixed() {
        return PREFIX + key;
    }

    /** La valeur portant ce nom, préfixé ou non, ou {@code null}. */
    public static @Nullable ClientValue byKey(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String bare = name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
        for (ClientValue value : values()) {
            if (value.key.equals(bare)) {
                return value;
            }
        }
        return null;
    }
}
