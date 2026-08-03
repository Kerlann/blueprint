package fr.blueprint.api.annotation;

import fr.blueprint.api.node.NodeCategory;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.resources.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dérive des {@link NodeType} depuis des méthodes annotées {@link BlueprintNode}
 * (story 8.1). C'est le <b>scan à l'exécution</b> : le mod passe ses classes, on lit
 * les signatures. Aucun processeur d'annotations n'est requis (AC2, variante « en son
 * absence ») — et donc aucune étape de compilation à ajouter chez le mod tiers.
 *
 * <p>Toute déclaration douteuse est <b>refusée avec un message qui nomme la méthode</b> :
 * un nœud à moitié déduit finirait dans les graphes des joueurs, et un pin mal nommé ne
 * se renomme plus sans casser leurs sauvegardes.
 */
public final class AnnotatedNodes {

    private AnnotatedNodes() {
    }

    /** Types de base reconnus dans une signature — la table est ouverte par surcharge. */
    private static final Map<Class<?>, PinType> BUILTIN = builtin();

    private static Map<Class<?>, PinType> builtin() {
        Map<Class<?>, PinType> map = new HashMap<>();
        for (PinType type : List.of(PinTypes.BOOL, PinTypes.INT, PinTypes.LONG, PinTypes.DOUBLE,
                PinTypes.STRING, PinTypes.VEC3, PinTypes.BLOCKPOS, PinTypes.DIRECTION,
                PinTypes.ITEMSTACK, PinTypes.PLAYER, PinTypes.ENTITY, PinTypes.BLOCKSTATE,
                PinTypes.RESOURCE_LOCATION, PinTypes.TEXT)) {
            map.put(type.javaType(), type);
        }
        map.put(boolean.class, PinTypes.BOOL);
        map.put(int.class, PinTypes.INT);
        map.put(long.class, PinTypes.LONG);
        map.put(double.class, PinTypes.DOUBLE);
        return Map.copyOf(map);
    }

    /** Enregistre tous les nœuds annotés des classes données. */
    public static void register(NodeRegistry registry, Class<?>... holders) {
        register(registry, Map.of(), holders);
    }

    /**
     * Variante pour les mods qui déclarent leurs propres types de pins : {@code extra}
     * associe une classe Java au {@link PinType} qui la transporte.
     */
    public static void register(NodeRegistry registry, Map<Class<?>, PinType> extra,
                                Class<?>... holders) {
        for (Class<?> holder : holders) {
            for (NodeType type : derive(holder, extra)) {
                registry.register(type);
            }
        }
    }

    public static List<NodeType> derive(Class<?> holder) {
        return derive(holder, Map.of());
    }

    /** Ordre déterministe (tri par nom) : deux chargements donnent le même registre. */
    public static List<NodeType> derive(Class<?> holder, Map<Class<?>, PinType> extra) {
        List<Method> methods = new ArrayList<>();
        for (Method method : holder.getDeclaredMethods()) {
            if (method.isAnnotationPresent(BlueprintNode.class)) {
                methods.add(method);
            }
        }
        methods.sort(Comparator.comparing(Method::getName));
        List<NodeType> out = new ArrayList<>(methods.size());
        for (Method method : methods) {
            out.add(toNodeType(method, extra));
        }
        return out;
    }

    // ------------------------------------------------------------------ dérivation

    private static NodeType toNodeType(Method method, Map<Class<?>, PinType> extra) {
        BlueprintNode declaration = method.getAnnotation(BlueprintNode.class);
        Identifier id = Identifier.tryParse(declaration.value());
        if (id == null) {
            throw declarationError(method, "identifiant « " + declaration.value()
                    + " » invalide (attendu « monmod:chemin »)");
        }
        if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
            throw declarationError(method, "la méthode doit être publique et statique");
        }

        NodeType.Builder builder = NodeType.builder(id)
                .category(new NodeCategory(declaration.category()))
                .permission(declaration.permission())
                .fuelCost(declaration.fuelCost())
                .deterministic(declaration.deterministic());
        if (!declaration.titleKey().isEmpty()) {
            builder.titleKey(declaration.titleKey());
        }
        if (!declaration.descKey().isEmpty()) {
            builder.descKey(declaration.descKey());
        }

        boolean returns = !void.class.equals(method.getReturnType());
        if (declaration.pure()) {
            if (!returns) {
                throw declarationError(method,
                        "un nœud pur doit retourner une valeur (sinon il ne sert à rien)");
            }
            builder.pure();
        } else {
            // AVANT les entrées : par convention, exec_in ouvre la liste des pins.
            builder.exec();
        }

        // Entrées : un paramètre = un pin, sauf le contexte qui est injecté.
        List<String> pinNames = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            if (NodeContext.class.equals(parameter.getType())) {
                pinNames.add(null);
                continue;
            }
            String name = pinName(method, parameter);
            PinType type = pinType(method, parameter.getType(), extra, "le paramètre « " + name + " »");
            In in = parameter.getAnnotation(In.class);
            if (in != null && !in.def().isEmpty()) {
                builder.in(name, type, parseDefault(method, name, type, in.def()));
            } else {
                builder.in(name, type);
            }
            pinNames.add(name);
        }

        Out out = method.getAnnotation(Out.class);
        if (!returns && out != null) {
            throw declarationError(method, "@Out sur une méthode sans valeur de retour");
        }
        String outName = out == null ? "result" : out.value();
        if (returns) {
            builder.out(outName, pinType(method, method.getReturnType(), extra,
                    "la valeur de retour"));
        }

        String[] pins = pinNames.toArray(new String[0]);
        method.setAccessible(true);
        return builder.action(ctx -> invoke(method, pins, returns, outName, ctx)).build();
    }

    private static void invoke(Method method, String[] pins, boolean returns, String outName,
                               NodeContext ctx) throws Exception {
        Object[] args = new Object[pins.length];
        for (int i = 0; i < pins.length; i++) {
            args[i] = pins[i] == null ? ctx : ctx.in(pins[i]);
        }
        try {
            Object result = method.invoke(null, args);
            if (returns) {
                ctx.out(outName, result);
            }
        } catch (InvocationTargetException e) {
            // L'exception du mod remonte telle quelle : la VM la journalise avec le
            // nœud fautif, la pile du proxy réflexif n'apporterait rien.
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static String pinName(Method method, Parameter parameter) {
        In in = parameter.getAnnotation(In.class);
        if (in != null && !in.value().isEmpty()) {
            return in.value();
        }
        if (!parameter.isNamePresent()) {
            throw declarationError(method, "nom de pin introuvable pour un paramètre — "
                    + "compiler avec l'option « -parameters » ou nommer le pin avec @In(\"nom\")");
        }
        return parameter.getName();
    }

    private static PinType pinType(Method method, Class<?> javaType,
                                   Map<Class<?>, PinType> extra, String what) {
        PinType type = extra.get(javaType);
        if (type == null) {
            type = BUILTIN.get(javaType);
        }
        if (type == null) {
            throw declarationError(method, "aucun type de pin pour " + what + " ("
                    + javaType.getSimpleName() + ") — déclarer le type et le passer "
                    + "à AnnotatedNodes.register(registry, Map.of(" + javaType.getSimpleName()
                    + ".class, MON_TYPE), …)");
        }
        return type;
    }

    private static Object parseDefault(Method method, String pin, PinType type, String raw) {
        try {
            if (type == PinTypes.INT) {
                return Integer.valueOf(raw.trim());
            }
            if (type == PinTypes.LONG) {
                return Long.valueOf(raw.trim());
            }
            if (type == PinTypes.DOUBLE) {
                return Double.valueOf(raw.trim());
            }
            if (type == PinTypes.BOOL) {
                if (!"true".equals(raw.trim()) && !"false".equals(raw.trim())) {
                    throw new NumberFormatException(raw);
                }
                return Boolean.valueOf(raw.trim());
            }
        } catch (NumberFormatException e) {
            throw declarationError(method, "valeur par défaut « " + raw + " » illisible pour le pin « "
                    + pin + " » (" + type.id() + ")");
        }
        if (type == PinTypes.STRING) {
            return raw;
        }
        throw declarationError(method, "valeur par défaut non prise en charge pour le pin « "
                + pin + " » de type " + type.id() + " — la déclarer via NodeType.builder");
    }

    private static IllegalStateException declarationError(Method method, String problem) {
        return new IllegalStateException("@BlueprintNode " + method.getDeclaringClass().getName()
                + "#" + method.getName() + " : " + problem);
    }
}
