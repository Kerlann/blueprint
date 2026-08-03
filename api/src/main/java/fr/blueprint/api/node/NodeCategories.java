package fr.blueprint.api.node;

/** Catégories standard de la palette. */
public final class NodeCategories {

    public static final NodeCategory FLOW = new NodeCategory("flow");
    public static final NodeCategory MATH = new NodeCategory("math");
    public static final NodeCategory LOGIC = new NodeCategory("logic");
    public static final NodeCategory STRING = new NodeCategory("string");
    public static final NodeCategory LIST = new NodeCategory("list");
    public static final NodeCategory STRUCT = new NodeCategory("struct");
    public static final NodeCategory WORLD = new NodeCategory("world");
    public static final NodeCategory ENTITY = new NodeCategory("entity");
    public static final NodeCategory PLAYER = new NodeCategory("player");
    public static final NodeCategory ITEM = new NodeCategory("item");
    public static final NodeCategory TEXT = new NodeCategory("text");
    public static final NodeCategory DEBUG = new NodeCategory("debug");
    public static final NodeCategory EVENT = new NodeCategory("event");
    /** Catégorie par défaut d'un builder qui n'en précise pas. */
    public static final NodeCategory MISC = new NodeCategory("misc");

    // ------------------------------------------------------------ sous-catégories
    // Les catégories qui dépassaient la dizaine de nœuds : au-delà, la liste dépliée
    // ne se parcourt plus du regard. Les autres restent plates — sous-diviser une
    // catégorie de quatre nœuds ne ferait qu'ajouter un clic.

    /** Branchement, aiguillage, séquence : ce qui choisit un chemin. */
    public static final NodeCategory FLOW_BRANCH = new NodeCategory("flow/branch");
    /** Boucles et attentes : ce qui répète ou suspend. */
    public static final NodeCategory FLOW_LOOP = new NodeCategory("flow/loop");

    /** Les quatre opérations et le modulo. */
    public static final NodeCategory MATH_ARITHMETIC = new NodeCategory("math/arithmetic");
    /** Minimum, maximum, valeur absolue, arrondi, aléatoire, conversions. */
    public static final NodeCategory MATH_FUNCTION = new NodeCategory("math/function");
    /** Racine, puissance, plancher, plafond, borner, interpoler. */
    public static final NodeCategory MATH_NUMERIC = new NodeCategory("math/numeric");
    /** Sinus, cosinus, angle : la trigonométrie. */
    public static final NodeCategory MATH_TRIG = new NodeCategory("math/trig");

    /** Vecteurs : construction, décomposition, arithmétique. */
    public static final NodeCategory MATH_VECTOR = new NodeCategory("math/vector");
    /** Positions de bloc, et passage vec3 ↔ blockpos. */
    public static final NodeCategory MATH_POSITION = new NodeCategory("math/position");

    /** Comparaisons : inférieur, supérieur, égal. */
    public static final NodeCategory LOGIC_COMPARE = new NodeCategory("logic/compare");
    /** Opérateurs booléens : et, ou, non, ou exclusif. */
    public static final NodeCategory LOGIC_BOOLEAN = new NodeCategory("logic/boolean");

    /** Transformer une chaîne : casse, découpe, extraction, remplacement. */
    public static final NodeCategory STRING_EDIT = new NodeCategory("string/edit");
    /** L'interroger sans la changer : longueur, contient, commence par. */
    public static final NodeCategory STRING_QUERY = new NodeCategory("string/query");

    /** Créer un dictionnaire, y placer, en retirer. */
    public static final NodeCategory MAP_BUILD = new NodeCategory("map/build");
    /** L'interroger : clé présente, valeur, taille, clés, valeurs. */
    public static final NodeCategory MAP_QUERY = new NodeCategory("map/query");

    /** Créer une liste, y ajouter, en retirer. */
    public static final NodeCategory LIST_BUILD = new NodeCategory("list/build");
    /** L'interroger sans la modifier : taille, élément, recherche. */
    public static final NodeCategory LIST_QUERY = new NodeCategory("list/query");

    /** Poser, lire et casser des blocs. */
    public static final NodeCategory WORLD_BLOCK = new NodeCategory("world/block");
    /** Sons, particules, explosions — ce qui se voit et s'entend. */
    public static final NodeCategory WORLD_EFFECT = new NodeCategory("world/effect");
    /** Heure, météo, entités déposées dans le monde. */
    public static final NodeCategory WORLD_STATE = new NodeCategory("world/state");

    /** Lire une entité sans la modifier : nom, type, vie, position. */
    public static final NodeCategory ENTITY_READ = new NodeCategory("entity/read");
    /** Agir sur une entité : soigner, téléporter, effets. */
    public static final NodeCategory ENTITY_ACT = new NodeCategory("entity/act");
    /** Chercher des entités dans le monde. */
    public static final NodeCategory ENTITY_QUERY = new NodeCategory("entity/query");

    /** Lire et retirer ce que porte le joueur. */
    public static final NodeCategory PLAYER_INVENTORY = new NodeCategory("player/inventory");

    /** Ce que le joueur VOIT et ENTEND : titre, barre d'action, son, particules. */
    public static final NodeCategory PLAYER_FEEDBACK = new NodeCategory("player/feedback");
    /** Agir sur le joueur : donner, retirer, changer son état. */
    public static final NodeCategory PLAYER_ACT = new NodeCategory("player/act");

    /** Événements déclenchés par un joueur. */
    public static final NodeCategory EVENT_PLAYER = new NodeCategory("event/player");
    /** Événements du monde et des entités. */
    public static final NodeCategory EVENT_WORLD = new NodeCategory("event/world");
    /** Scores, objectifs et équipes : la mémoire partagée de Minecraft. */
    public static final NodeCategory SCOREBOARD = new NodeCategory("scoreboard");

    /** Écrans de blueprint : ouvrir, fermer, modifier ce qui est affiché (épic 10). */
    public static final NodeCategory GUI = new NodeCategory("gui");
    /** Modifier ce qu'un écran DÉJÀ OUVERT affiche : texte, texture, visibilité, barre. */
    public static final NodeCategory GUI_UPDATE = new NodeCategory("gui/update");

    /** Tick, commande, signal : ce qui ne vient ni d'un joueur ni du monde. */
    public static final NodeCategory EVENT_SERVER = new NodeCategory("event/server");

    private NodeCategories() {
    }
}
