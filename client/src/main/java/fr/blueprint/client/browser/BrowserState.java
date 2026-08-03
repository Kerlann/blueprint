package fr.blueprint.client.browser;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * L'état du navigateur de blueprints (F6) : la liste, les dossiers, le filtre et ce
 * qui est sélectionné.
 *
 * <p><b>Des dossiers, parce qu'un identifiant en est déjà un.</b>
 * {@code demo:boutique/etage_1} porte un espace de noms et un chemin ; les afficher à
 * plat donnait une liste où l'on cherchait à l'œil. L'arborescence ne change rien au
 * modèle — elle lit ce qui était déjà écrit dedans.
 *
 * <p>Pur et testable : il ne connaît que des identifiants et des noms de fichiers.
 */
public final class BrowserState {

    /** Ce que le navigateur montre : un dossier repliable, ou quelque chose d'ouvrable. */
    public enum Kind { FOLDER, BLUEPRINT, FILE }

    /**
     * Une ligne affichée.
     *
     * @param path       le chemin complet, qui sert de clé de repli ({@code demo:boutique})
     * @param label      ce qui s'écrit à l'écran — le dernier segment seulement
     * @param depth      profondeur d'indentation
     * @param blueprint  l'identifiant, pour une ligne {@code BLUEPRINT}
     * @param file       le nom de fichier, pour une ligne {@code FILE}
     */
    public record Row(Kind kind, String path, String label, int depth,
                      @Nullable Identifier blueprint, @Nullable String file) {
    }

    private List<Identifier> blueprints = List.of();
    private List<String> files = List.of();
    private final Set<String> collapsed = new LinkedHashSet<>();
    private String filter = "";
    private @Nullable String selected;
    private boolean showFiles = true;

    public void setBlueprints(List<Identifier> ids) {
        this.blueprints = List.copyOf(ids);
    }

    public void setFiles(List<String> names) {
        this.files = List.copyOf(names);
    }

    public String filter() {
        return filter;
    }

    public void setFilter(String text) {
        this.filter = text == null ? "" : text;
    }

    public boolean showFiles() {
        return showFiles;
    }

    public void toggleFiles() {
        showFiles = !showFiles;
    }

    public @Nullable String selected() {
        return selected;
    }

    /**
     * Clic sur une ligne : un dossier se replie, le reste se sélectionne.
     *
     * @return vrai si la ligne est ouvrable (un second clic l'ouvre)
     */
    public boolean click(Row row) {
        if (row.kind() == Kind.FOLDER) {
            if (!collapsed.remove(row.path())) {
                collapsed.add(row.path());
            }
            selected = null;
            return false;
        }
        selected = row.path();
        return true;
    }

    public boolean isCollapsed(String path) {
        return collapsed.contains(path);
    }

    /**
     * Les lignes visibles, dossiers repliés exclus.
     *
     * <p>Un filtre non vide <b>déplie tout</b> : chercher puis ne rien voir parce que
     * le dossier était replié serait exactement le contraire du service rendu.
     */
    public List<Row> rows() {
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        boolean searching = !needle.isEmpty();
        List<Row> out = new ArrayList<>();

        // L'espace de noms est le dossier racine, chaque « / » du chemin un
        // sous-dossier : « blueprint:exemple/porte » se lit exemple ▸ porte, comme le
        // chemin qu'il est déjà. Trier d'abord met chaque dossier d'un seul tenant.
        List<Identifier> visible = new ArrayList<>();
        for (Identifier id : blueprints) {
            if (!searching || id.toString().toLowerCase(Locale.ROOT).contains(needle)) {
                visible.add(id);
            }
        }
        visible.sort(java.util.Comparator.comparing(Identifier::toString));

        List<String> openFolders = new ArrayList<>();
        for (Identifier id : visible) {
            List<String> folders = new ArrayList<>();
            folders.add(id.getNamespace());
            String[] segments = id.getPath().split("/");
            for (int i = 0; i < segments.length - 1; i++) {
                folders.add(folders.getLast() + "/" + segments[i]);
            }
            // Ferme les dossiers qu'on quitte, ouvre ceux qu'on entre — un seul
            // parcours, sans arbre intermédiaire à construire puis à reparcourir.
            int common = 0;
            while (common < openFolders.size() && common < folders.size()
                    && openFolders.get(common).equals(folders.get(common))) {
                common++;
            }
            while (openFolders.size() > common) {
                openFolders.removeLast();
            }
            for (int i = common; i < folders.size(); i++) {
                String path = folders.get(i);
                String label = i == 0 ? path : path.substring(path.lastIndexOf('/') + 1);
                out.add(new Row(Kind.FOLDER, path, label, i, null, null));
                openFolders.add(path);
            }
            boolean hidden = !searching && folders.stream().anyMatch(collapsed::contains);
            if (!hidden) {
                out.add(new Row(Kind.BLUEPRINT, id.toString(),
                        segments[segments.length - 1], folders.size(), id, null));
            }
        }

        if (showFiles && !files.isEmpty()) {
            List<String> matching = new ArrayList<>();
            for (String file : files) {
                if (!searching || file.toLowerCase(Locale.ROOT).contains(needle)) {
                    matching.add(file);
                }
            }
            if (!matching.isEmpty()) {
                out.add(new Row(Kind.FOLDER, FILES_FOLDER, FILES_FOLDER, 0, null, null));
                if (searching || !collapsed.contains(FILES_FOLDER)) {
                    for (String file : matching) {
                        out.add(new Row(Kind.FILE, "file:" + file, file, 1, null, file));
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /** Le dossier des fichiers importables — un nom qui ne peut pas être un espace de noms. */
    public static final String FILES_FOLDER = "exports/";

    /** La ligne sélectionnée, si elle est encore affichée. */
    public @Nullable Row selectedRow() {
        if (selected == null) {
            return null;
        }
        for (Row row : rows()) {
            if (row.path().equals(selected)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Un identifiant tapé par le joueur. {@code menu} devient {@code blueprint:menu} :
     * exiger l'espace de noms pour créer son premier graphe serait une leçon avant le
     * premier geste.
     */
    public static @Nullable Identifier parseId(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.contains(":")
                ? Identifier.tryParse(text)
                : Identifier.tryParse("blueprint:" + text);
    }
}
