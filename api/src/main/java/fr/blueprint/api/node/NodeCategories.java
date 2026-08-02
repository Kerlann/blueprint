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

    private NodeCategories() {
    }
}
