package fr.blueprint.core.docs;

import fr.blueprint.api.BlueprintApi;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compatibilité binaire de {@code fr.blueprint.api} (story 8.5, AC2). La surface
 * publique est relevée par réflexion et comparée à une <b>signature de référence
 * commitée</b> : toute disparition ou changement de signature fait échouer la
 * construction.
 *
 * <p>Ce n'est pas un test de style, c'est un contrat : un mod tiers compilé contre une
 * version antérieure doit continuer de se lancer. Un ajout est accepté après
 * régénération (et demande une version mineure) ; une <b>suppression</b> doit sauter aux
 * yeux de celui qui la commet — d'où la comparaison ligne à ligne.
 *
 * <pre>./gradlew :core:test --tests "*ApiSurfaceTest" -Dblueprint.regenDocs=true</pre>
 */
class ApiSurfaceTest {

    private static final Path REFERENCE = Path.of("docs", "api-surface.txt");

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("racine du dépôt introuvable");
        }
        return path;
    }

    @Test
    void thePublicSurfaceMatchesTheCommittedSignature() {
        String surface = surface();
        Path file = repoRoot().resolve(REFERENCE);
        if (Boolean.getBoolean("blueprint.regenDocs")) {
            try {
                Files.writeString(file, surface, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return;
        }
        assertTrue(Files.isRegularFile(file), REFERENCE + " manquant — régénérer");
        String committed;
        try {
            committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(committed, surface, """
                La surface publique de fr.blueprint.api a changé.
                  • Ajout ? Régénérer (-Dblueprint.regenDocs=true) et monter la MINEURE de BlueprintApi.
                  • Suppression ou signature modifiée ? C'est une RUPTURE : déprécier d'abord,
                    et ne supprimer qu'à une version MAJEURE (voir BlueprintApi).""");
    }

    @Test
    void theVersionIsCoherentAndUsable() {
        assertEquals(BlueprintApi.MAJOR + "." + BlueprintApi.MINOR + "." + BlueprintApi.PATCH,
                BlueprintApi.API_VERSION);
        assertTrue(BlueprintApi.isCompatibleWith(BlueprintApi.MAJOR, BlueprintApi.MINOR));
        assertTrue(BlueprintApi.isCompatibleWith(BlueprintApi.MAJOR, 0),
                "une mineure plus ancienne reste satisfaite");
        assertFalse(BlueprintApi.isCompatibleWith(BlueprintApi.MAJOR + 1, 0),
                "une majeure différente ne l'est jamais");
        assertFalse(BlueprintApi.isCompatibleWith(BlueprintApi.MAJOR, BlueprintApi.MINOR + 1),
                "une mineure plus récente non plus");
    }

    // ------------------------------------------------------------------ relevé

    private static String surface() {
        List<String> classNames = new ArrayList<>();
        Path sources = repoRoot().resolve("api/src/main/java");
        try (Stream<Path> files = Files.walk(sources)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .forEach(path -> classNames.add(
                            sources.relativize(path).toString()
                                    .replace(java.io.File.separatorChar, '.')
                                    .replaceAll("\\.java$", "")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        classNames.sort(String::compareTo);

        StringBuilder out = new StringBuilder();
        out.append("# Surface publique de fr.blueprint.api — FICHIER GÉNÉRÉ (story 8.5)\n")
                .append("# Comparé à chaque build par ApiSurfaceTest : une ligne qui disparaît\n")
                .append("# est une rupture de compatibilité pour les mods tiers.\n\n");
        for (String className : classNames) {
            Class<?> type;
            try {
                type = Class.forName(className);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                continue;
            }
            appendType(out, type);
        }
        return out.toString();
    }

    private static void appendType(StringBuilder out, Class<?> type) {
        if (!Modifier.isPublic(type.getModifiers())) {
            return;
        }
        out.append(kind(type)).append(' ').append(type.getName());
        if (type.getSuperclass() != null && type.getSuperclass() != Object.class
                && type.getSuperclass() != Record.class && type.getSuperclass() != Enum.class) {
            out.append(" extends ").append(type.getSuperclass().getName());
        }
        Class<?>[] interfaces = type.getInterfaces();
        if (interfaces.length > 0) {
            TreeSet<String> names = new TreeSet<>();
            for (Class<?> face : interfaces) {
                names.add(face.getName());
            }
            out.append(" implements ").append(String.join(", ", names));
        }
        out.append('\n');

        TreeSet<String> members = new TreeSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (visible(field.getModifiers())) {
                members.add("  field " + Modifier.toString(field.getModifiers()) + " "
                        + field.getType().getName() + " " + field.getName());
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (visible(constructor.getModifiers())) {
                members.add("  ctor " + Modifier.toString(constructor.getModifiers()) + " ("
                        + parameters(constructor.getParameterTypes()) + ")");
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (visible(method.getModifiers()) && !method.isSynthetic()) {
                members.add("  method " + Modifier.toString(method.getModifiers()) + " "
                        + method.getReturnType().getName() + " " + method.getName()
                        + "(" + parameters(method.getParameterTypes()) + ")");
            }
        }
        members.forEach(member -> out.append(member).append('\n'));
        out.append('\n');

        Class<?>[] nested = type.getDeclaredClasses();
        List<Class<?>> sorted = new ArrayList<>(List.of(nested));
        sorted.sort(java.util.Comparator.comparing(Class::getName));
        for (Class<?> inner : sorted) {
            appendType(out, inner);
        }
    }

    /** Public et protégé : les deux sont visibles d'un mod tiers, donc du contrat. */
    private static boolean visible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String kind(Class<?> type) {
        if (type.isInterface()) {
            return type.isAnnotation() ? "annotation" : "interface";
        }
        if (type.isEnum()) {
            return "enum";
        }
        if (type.isRecord()) {
            return "record";
        }
        return "class";
    }

    private static String parameters(Class<?>[] types) {
        List<String> names = new ArrayList<>(types.length);
        for (Class<?> type : types) {
            names.add(type.getName());
        }
        return String.join(", ", names);
    }
}
