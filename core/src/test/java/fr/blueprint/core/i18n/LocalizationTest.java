package fr.blueprint.core.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeCategory;
import fr.blueprint.api.node.Permission;
import fr.blueprint.core.graph.DiagnosticCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR10 : « toute chaîne visible par l'utilisateur est traduisible ; en_us et fr_fr sont
 * fournis et <b>complets</b> ». Ce test lit les SOURCES de tous les modules, en extrait
 * les clés de traduction littérales, et vérifie qu'aucune ne manque d'un côté ou de
 * l'autre — une clé oubliée s'affiche en jeu comme du charabia
 * ({@code blueprint.editor.toolbar.save}), pas comme une erreur.
 *
 * <p>Il vérifie aussi le nombre de {@code %s} : une traduction qui en porte un de plus
 * que l'autre <b>lève</b> au formatage, en jeu, chez le seul joueur qui a cette langue.
 */
class LocalizationTest {

    /** Appels dont le premier argument littéral est une clé de traduction. */
    private static final Pattern KEY_CALL = Pattern.compile(
            "(?:Component\\.translatable|translatable|I18n\\.get|actionBar|hasTranslation)"
                    + "\\(\\s*\"(blueprint\\.[A-Za-z0-9_.\\-/]+)\"");
    /** Clés écrites en dur ailleurs (constantes, tableaux) : même exigence. */
    private static final Pattern KEY_LITERAL = Pattern.compile(
            "\"(blueprint\\.(?:cmd|editor|diag|fault|pin|key)\\.[A-Za-z0-9_.\\-/]+)\"");

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

    private static JsonObject lang(String locale) {
        Path file = repoRoot().resolve("core/src/main/resources/assets/blueprint/lang")
                .resolve(locale + ".json");
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Toutes les clés littérales trouvées dans les sources Java du projet. */
    private static Map<String, String> keysInSources() {
        Map<String, String> keyToFile = new LinkedHashMap<>();
        for (String module : List.of("api", "core", "client", "compat", "testmod")) {
            Path sources = repoRoot().resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sources)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(sources)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    String text;
                    try {
                        text = Files.readString(path, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    for (Pattern pattern : List.of(KEY_CALL, KEY_LITERAL)) {
                        Matcher matcher = pattern.matcher(text);
                        while (matcher.find()) {
                            String key = matcher.group(1);
                            // Un littéral qui se termine par un point est un PRÉFIXE
                            // (« blueprint.pin. » + id) : la clé se forme à l'exécution.
                            if (!key.endsWith(".")) {
                                keyToFile.putIfAbsent(key, path.getFileName().toString());
                            }
                        }
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return keyToFile;
    }

    // ------------------------------------------------------------------ NFR10

    @Test
    void everyKeyUsedInTheCodeExistsInBothLanguages() {
        JsonObject en = lang("en_us");
        JsonObject fr = lang("fr_fr");
        Map<String, String> used = keysInSources();
        assertFalse(used.isEmpty(), "l'extraction n'a rien trouvé : le motif est cassé");

        List<String> missing = new ArrayList<>();
        used.forEach((key, file) -> {
            if (!en.has(key)) {
                missing.add(key + " (en_us, vue dans " + file + ")");
            }
            if (!fr.has(key)) {
                missing.add(key + " (fr_fr, vue dans " + file + ")");
            }
        });
        assertTrue(missing.isEmpty(), "clés utilisées mais non traduites :\n  "
                + String.join("\n  ", missing));
    }

    @Test
    void bothLanguagesDeclareExactlyTheSameKeys() {
        Set<String> en = new TreeSet<>(lang("en_us").keySet());
        Set<String> fr = new TreeSet<>(lang("fr_fr").keySet());

        Set<String> onlyEn = new LinkedHashSet<>(en);
        onlyEn.removeAll(fr);
        Set<String> onlyFr = new LinkedHashSet<>(fr);
        onlyFr.removeAll(en);

        assertTrue(onlyEn.isEmpty(), "présentes seulement en anglais : " + onlyEn);
        assertTrue(onlyFr.isEmpty(), "présentes seulement en français : " + onlyFr);
        assertEquals(en.size(), fr.size());
    }

    /** Un {@code %s} de trop dans une seule langue = exception au formatage, en jeu. */
    @Test
    void placeholdersMatchBetweenLanguages() {
        JsonObject en = lang("en_us");
        JsonObject fr = lang("fr_fr");
        List<String> mismatched = new ArrayList<>();
        for (String key : en.keySet()) {
            if (!fr.has(key)) {
                continue;
            }
            int enCount = placeholders(en.get(key).getAsString());
            int frCount = placeholders(fr.get(key).getAsString());
            if (enCount != frCount) {
                mismatched.add(key + " : en=" + enCount + ", fr=" + frCount);
            }
        }
        assertTrue(mismatched.isEmpty(), "nombre de paramètres divergent :\n  "
                + String.join("\n  ", mismatched));
    }

    private static int placeholders(String text) {
        int count = 0;
        Matcher matcher = Pattern.compile("%(?:\\d+\\$)?[sd]").matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Les diagnostics sont traduits par leur code : la couverture doit être totale. */
    @Test
    void everyDiagnosticCodeIsTranslated() {
        JsonObject en = lang("en_us");
        JsonObject fr = lang("fr_fr");
        List<String> missing = new ArrayList<>();
        for (DiagnosticCode code : DiagnosticCode.values()) {
            String key = "blueprint.diag." + code.name().toLowerCase(Locale.ROOT);
            if (!en.has(key)) {
                missing.add(key + " (en_us)");
            }
            if (!fr.has(key)) {
                missing.add(key + " (fr_fr)");
            }
        }
        assertTrue(missing.isEmpty(), "diagnostics non traduits : " + missing);
    }

    /**
     * La famille {@code blueprint.permission.*} est construite à l'exécution depuis le
     * nom de l'enum (infobulle d'un nœud, éditeur). Sans ce test, ajouter un niveau
     * afficherait la clé brute au joueur.
     */
    @Test
    void everyPermissionLevelIsTranslated() {
        // Volontairement pas nommées « fr » : ce nom masque le paquet fr.blueprint.
        JsonObject english = lang("en_us");
        JsonObject french = lang("fr_fr");
        List<String> missing = new ArrayList<>();
        for (Permission permission : Permission.values()) {
            String key = "blueprint.permission." + permission.name().toLowerCase(Locale.ROOT);
            if (!english.has(key)) {
                missing.add(key + " (en_us)");
            }
            if (!french.has(key)) {
                missing.add(key + " (fr_fr)");
            }
        }
        assertTrue(missing.isEmpty(), "niveaux de permission non traduits : " + missing);
    }

    /**
     * Les catégories nomment les groupes du menu d'ajout de nœud : ce sont les seuls
     * repères du joueur qui cherche. Une catégorie standard sans traduction
     * s'afficherait telle quelle — « flow », « struct » — ce qui était le cas avant
     * la 5.13.
     */
    @Test
    void everyStandardCategoryIsTranslated() {
        JsonObject english = lang("en_us");
        JsonObject french = lang("fr_fr");
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (java.lang.reflect.Field field : NodeCategories.class.getDeclaredFields()) {
            if (!field.getType().equals(NodeCategory.class)) {
                continue;
            }
            String id;
            try {
                id = ((NodeCategory) field.get(null)).id();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(field.getName(), e);
            }
            checked++;
            String key = "blueprint.category." + id;
            if (!english.has(key)) {
                missing.add(key + " (en_us)");
            }
            if (!french.has(key)) {
                missing.add(key + " (fr_fr)");
            }
        }
        // Sans ce garde-fou, un renommage de NodeCategories ferait passer le test à vide.
        assertTrue(checked >= 10, "la réflexion n'a trouvé que " + checked + " catégories");
        assertTrue(missing.isEmpty(), "catégories standard non traduites : " + missing);
    }

    /**
     * Chaque type d'élément d'écran a son libellé dans les deux langues (story 10.2).
     * La clé se forme à l'exécution — {@code "blueprint.designer.kind." + type} — donc
     * l'extraction de sources ne peut pas la voir : sans ce test, ajouter un sixième
     * type afficherait sa clé brute dans la palette.
     */
    @Test
    void everyElementKindIsTranslated() {
        JsonObject english = lang("en_us");
        JsonObject french = lang("fr_fr");
        List<String> missing = new ArrayList<>();
        for (var kind : fr.blueprint.core.graph.screen.ElementKind.values()) {
            String key = "blueprint.designer.kind."
                    + kind.name().toLowerCase(java.util.Locale.ROOT);
            if (!english.has(key)) {
                missing.add(key + " (en_us)");
            }
            if (!french.has(key)) {
                missing.add(key + " (fr_fr)");
            }
        }
        assertTrue(missing.isEmpty(), "types d'élément non traduits : " + missing);
    }

    /** Aucune clé morte : ce qui est traduit doit servir (ou être une clé dérivée connue). */
    @Test
    void noDeadKeysBeyondTheDerivedFamilies() {
        Set<String> used = keysInSources().keySet();
        List<String> dead = new ArrayList<>();
        for (String key : lang("en_us").keySet()) {
            if (used.contains(key)) {
                continue;
            }
            // Familles construites à l'exécution : nœuds, pins, événements, catégories,
            // diagnostics, touches — vérifiées ailleurs (StandardLibraryTest, ci-dessus).
            if (key.startsWith("blueprint.node.") || key.startsWith("blueprint.pin.")
                    || key.startsWith("blueprint.event.") || key.startsWith("blueprint.category.")
                    || key.startsWith("blueprint.diag.") || key.startsWith("key.")
                    || key.startsWith("blueprint.fault.")
                    || key.startsWith("blueprint.permission.")
                    // Types d'élément et champs du concepteur (10.2) : construits à
                    // l'exécution, et vérifiés par les deux tests ci-dessous.
                    || key.startsWith("blueprint.designer.kind.")
                    || key.startsWith("blueprint.designer.field.")
                    // Modes de taille et de disposition (10.10) : une clé par valeur
                    // d'énumération, et le test ci-dessous les exige toutes.
                    || key.startsWith("blueprint.designer.size.")
                    || key.startsWith("blueprint.designer.layout.")
                    || key.startsWith("blueprint.designer.main.")
                    || key.startsWith("blueprint.designer.cross.")) {
                continue;
            }
            dead.add(key);
        }
        assertTrue(dead.isEmpty(), "clés traduites que plus personne n'utilise :\n  "
                + String.join("\n  ", dead));
    }
}
