package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Le dessin d'un écran de blueprint — <b>écrit une seule fois</b>, et partagé par le
 * concepteur (story 10.2) et le rendu en jeu (story 10.3).
 *
 * <p>La tentation est de dessiner « à peu près » dans le concepteur et « pour de vrai »
 * en jeu. Deux moteurs, deux résultats, et l'auteur découvre l'écart une fois en
 * partie — au moment où il ne peut plus rien corriger sans tout rouvrir. C'est la même
 * leçon que la géométrie d'un fil, partagée entre son tracé et son clic depuis la 5.12.
 *
 * <p>La géométrie vient de {@link ScreenLayout} ; ce fichier ne fait que peindre.
 */
public final class ScreenPainter {

    /** Ce que le peintre ignore : qui est survolé, qui est pressé, qui est mis en avant. */
    public interface Visuals {

        Visuals NONE = new Visuals() { };

        default boolean hovered(String element) {
            return false;
        }

        default boolean pressed(String element) {
            return false;
        }

        /**
         * Le remplissage d'une barre, de 0 à 1. C'est une valeur d'EXÉCUTION — propre à
         * un joueur et à une ouverture — et non une propriété du blueprint : la mettre
         * dans le modèle la ferait voyager dans la sauvegarde et l'export texte.
         */
        default double progress(String element) {
            return 0;
        }

        // ------------------------------------------- éléments riches (story 10.8)
        // Toutes ces valeurs sont des données d'EXÉCUTION : propres à ce joueur et à
        // cette ouverture, jamais des propriétés du blueprint. Les écrire dans le
        // modèle les ferait voyager dans la sauvegarde et l'export texte, où elles
        // n'ont rien à faire.

        /** Les lignes d'une liste, telles que le graphe les a envoyées. */
        default java.util.List<String> lines(String element) {
            return java.util.List.of();
        }

        /** De combien de lignes cette liste est défilée. */
        default int scroll(String element) {
            return 0;
        }

        /**
         * De combien un <b>panneau défilant</b> est remonté, en unités (story 10.13).
         *
         * <p>Distinct de {@link #scroll} juste au-dessus, qui compte les <i>lignes</i>
         * d'une liste : ce sont deux mécanismes différents, et leur donner le même nom
         * aurait invité à brancher l'un sur l'autre.
         */
        default double panelScroll(String element) {
            return 0;
        }

        /** De combien un panneau défilant est décalé vers la gauche. */
        default double panelScrollX(String element) {
            return 0;
        }

        /** Le texte saisi dans ce champ ; vide = l'indication s'affiche à la place. */
        default String input(String element) {
            return "";
        }

        /** Ce champ a-t-il le focus ? Le curseur de saisie ne clignote que là. */
        default boolean focused(String element) {
            return false;
        }

        /** L'état d'une case à cocher. */
        default boolean checked(String element) {
            return false;
        }

        /** La valeur d'un curseur, déjà ramenée dans sa plage. */
        default double value(String element) {
            return 0;
        }

        /** L'objet d'un emplacement, ou {@code null} s'il est vide. */
        default @Nullable net.minecraft.world.item.ItemStack item(String element) {
            return null;
        }

        /**
         * Ce qu'un élément LIÉ montrera, pour le concepteur seul (story 10.7).
         *
         * <p>Un élément lié n'a pas de texte dans le modèle : c'est la variable qui le
         * fournit, à l'exécution. En conception il apparaissait donc <b>vide</b> — une
         * ligne blanche au milieu du menu, dont rien ne disait qu'elle afficherait
         * quelque chose. L'auteur ne pouvait ni juger la place qu'elle prendrait, ni
         * vérifier son format.
         *
         * <p>{@code null} en jeu : là, la valeur réelle arrive du serveur, et montrer un
         * aperçu à sa place serait montrer une valeur inventée.
         */
        default @Nullable fr.blueprint.core.graph.screen.ScreenText preview(
                ScreenElement element) {
            return null;
        }

        /** Un élément masqué par le graphe (10.4) ne se dessine pas… sauf en conception. */
        default boolean forceVisible(String element) {
            return false;
        }

        /**
         * La texture est-elle absente des ressources chargées ? C'est le mode de panne
         * le plus probable de tout l'épic : un auteur nomme une texture d'un pack que le
         * joueur n'a pas.
         */
        default boolean textureMissing(net.minecraft.resources.Identifier texture) {
            return false;
        }

        /**
         * Le <b>pack</b> qui manque pour cette texture, ou {@code null} si l'absence ne
         * vient pas d'un pack (story 10.5, AC3).
         *
         * <p>Nommer le fichier ne suffit pas : le joueur ne sait pas d'où il devrait
         * venir. Nommer le pack lui dit ce qu'il doit demander à l'auteur du menu —
         * la même promesse que les nœuds fantômes, qui nomment le mod absent (FR41).
         */
        default @Nullable String missingPack(net.minecraft.resources.Identifier texture) {
            return null;
        }
    }

    /** Damier magenta : deux couleurs, comme la texture manquante de Minecraft. */
    private static final int MISSING_A = 0xFFF800F8;
    private static final int MISSING_B = 0xFF000000;
    private static final int MISSING_CELL = 8;

    private ScreenPainter() {
    }

    /**
     * Peint tout l'écran.
     *
     * @param originX  abscisse à l'écran de l'unité 0
     * @param originY  ordonnée à l'écran de l'unité 0
     * @param scale    pixels par unité d'interface (1 en jeu, davantage en conception)
     * @param unitsW   largeur de la fenêtre EN UNITÉS — la base des pourcentages
     * @param unitsH   hauteur de la fenêtre en unités
     */
    public static void paint(GuiGraphics g, Font font, Screen screen,
                             int originX, int originY, int scale,
                             double unitsW, double unitsH, Visuals visuals) {
        // UNE passe de disposition pour tout l'écran, puis le dessin lit la table. La
        // place d'un enfant rangé se décide entre frères : la calculer élément par
        // élément ne pourrait pas la connaître (story 10.10). Au passage c'est moins
        // cher — la chaîne de parenté n'est plus remontée une fois par élément.
        paint(g, font, screen, ScreenLayout.solve(screen, unitsW, unitsH),
                originX, originY, scale, visuals);
    }

    /** La même chose, quand l'appelant a déjà résolu l'écran (concepteur, hit-test). */
    public static void paint(GuiGraphics g, Font font, Screen screen,
                             java.util.Map<String, ScreenLayout.Rect> rects,
                             int originX, int originY, int scale, Visuals visuals) {
        for (ScreenElement element : screen.elements().values()) {
            if (!visible(screen, element, visuals)) {
                continue;
            }
            ScreenLayout.Rect rect = rects.get(element.name());
            if (rect == null) {
                continue;   // parenté cyclique : le validateur le dit, le dessin passe
            }
            // Un enfant de conteneur défilant est DÉCOUPÉ à son cadre (10.13). Sans
            // cela, ce qui est sorti du panneau continuerait d'être peint par-dessus le
            // reste du menu, et l'on chercherait longtemps d'où viennent les lignes qui
            // le traversent.
            ScreenLayout.Rect clip = ScreenLayout.clipOf(screen, element, rects);
            if (clip == null) {
                paintElement(g, font, screen, element, rect, originX, originY, scale, visuals);
                continue;
            }
            int[] box = pixels(clip, originX, originY, scale);
            if (box[2] <= box[0] || box[3] <= box[1]) {
                continue;   // cadre réduit à rien : rien à dessiner, et scissor l'exige
            }
            g.enableScissor(box[0], box[1], box[2], box[3]);
            paintElement(g, font, screen, element, rect, originX, originY, scale, visuals);
            g.disableScissor();
        }
        paintScrollBars(g, screen, rects, originX, originY, scale, visuals);
    }

    /**
     * Les curseurs de défilement des panneaux, dessinés <b>en dernier</b>.
     *
     * <p>Un panneau est peint avant ses enfants — c'est l'ordre de dessin — donc un
     * curseur tracé au passage passerait dessous et disparaîtrait derrière le contenu
     * qu'il sert à situer. Il vit aussi hors du découpage de son propre panneau : il en
     * borde le cadre, il n'en fait pas partie, et l'y découper le rognerait au dernier
     * cran. Le découpage d'un panneau défilant EXTÉRIEUR, lui, s'applique bien.
     */
    private static void paintScrollBars(GuiGraphics g, Screen screen,
                                        java.util.Map<String, ScreenLayout.Rect> rects,
                                        int originX, int originY, int scale, Visuals visuals) {
        for (ScreenElement element : screen.elements().values()) {
            if (!element.scrolls() || !visible(screen, element, visuals)) {
                continue;
            }
            ScreenLayout.Rect frame = rects.get(element.name());
            if (frame == null) {
                continue;
            }
            ElementStyle style = screen.styleOf(element);
            double offsetY = visuals.panelScroll(element.name());
            double offsetX = visuals.panelScrollX(element.name());
            ScreenLayout.ScrollBars bars = ScreenLayout.scrollBarsOf(frame,
                    ScreenLayout.scrollRange(screen, element, rects, offsetY),
                    ScreenLayout.scrollRangeX(screen, element, rects, offsetX),
                    offsetY, offsetX, style.padding());
            if (bars.vertical() == null && bars.horizontal() == null) {
                continue;   // tout tient : pas de curseur, il mentirait sur ce qui reste
            }
            ScreenLayout.Rect clip = ScreenLayout.clipOf(screen, element, rects);
            int[] box = clip == null ? null : pixels(clip, originX, originY, scale);
            if (box != null) {
                if (box[2] <= box[0] || box[3] <= box[1]) {
                    continue;
                }
                g.enableScissor(box[0], box[1], box[2], box[3]);
            }
            for (ScreenLayout.ScrollBar bar
                    : new ScreenLayout.ScrollBar[]{bars.vertical(), bars.horizontal()}) {
                if (bar == null) {
                    continue;
                }
                fillRect(g, bar.track(), originX, originY, scale, style.border());
                fillRect(g, bar.thumb(), originX, originY, scale, style.textColor());
            }
            if (box != null) {
                g.disableScissor();
            }
        }
    }

    private static void fillRect(GuiGraphics g, ScreenLayout.Rect rect,
                                 int originX, int originY, int scale, int colour) {
        int[] box = pixels(rect, originX, originY, scale);
        g.fill(box[0], box[1], box[2], box[3], colour);
    }

    /**
     * Un élément est-il visible ? Masquer un parent masque toute sa page — c'est ce qui
     * rend un menu à onglets praticable, et le vérifier ici évite que chaque appelant
     * remonte la chaîne à sa façon.
     */
    public static boolean visible(Screen screen, ScreenElement element, Visuals visuals) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        ScreenElement cursor = element;
        while (cursor != null && seen.add(cursor.name())) {
            if (!cursor.visible() && !visuals.forceVisible(cursor.name())) {
                return false;
            }
            cursor = cursor.parent() == null ? null : screen.element(cursor.parent());
        }
        return true;
    }

    private static void paintElement(GuiGraphics g, Font font, Screen screen,
                                     ScreenElement element,
                                     ScreenLayout.Rect rect, int originX, int originY,
                                     int scale, Visuals visuals) {
        int left = originX + (int) Math.round(rect.x() * scale);
        int top = originY + (int) Math.round(rect.y() * scale);
        int right = originX + (int) Math.round(rect.right() * scale);
        int bottom = originY + (int) Math.round(rect.bottom() * scale);
        if (right <= left || bottom <= top) {
            return;
        }
        // Le style NOMMÉ prend le pas sur celui porté par l'élément — c'est ce qui fait
        // qu'en changer un repeint tous ceux qui le suivent.
        ElementStyle style = screen.styleOf(element);
        int background = style.backgroundFor(visuals.hovered(element.name()),
                visuals.pressed(element.name()), element.enabled());

        switch (element.kind()) {
            case IMAGE -> {
                if (element.texture() == null) {
                    fillBox(g, left, top, right, bottom, background, style, scale);
                } else if (visuals.textureMissing(element.texture())) {
                    // Ni écran vide, ni exception : le joueur doit voir CE qui manque.
                    // Vider l'écran ou lever lui donnerait un menu blanc sans indice,
                    // et l'auteur ne saurait pas quel fichier livrer avec son pack.
                    paintMissing(g, font, element.texture(),
                            visuals.missingPack(element.texture()), left, top, right, bottom);
                } else {
                    // u = v = 0 et une région de la taille de la destination : les UV
                    // valent alors 0→1, donc la texture ENTIÈRE est étirée dans le
                    // rectangle — sans avoir à connaître sa taille en pixels, qu'un
                    // pack tiers ne nous dit pas.
                    g.blit(RenderPipelines.GUI_TEXTURED, element.texture(), left, top,
                            0f, 0f, right - left, bottom - top, right - left, bottom - top);
                }
            }
            case PROGRESS -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                // Le remplissage vient du graphe (10.4) ; sans lui, la barre se montre
                // vide plutôt qu'à moitié pleine — un aperçu ne doit pas inventer.
                int filled = left + (int) Math.round(
                        (right - left) * visuals.progress(element.name()));
                if (filled > left) {
                    g.fill(left, top, filled, bottom, style.textColor());
                }
            }
            // ------------------------------------------ éléments riches (10.8)
            case LIST -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                paintList(g, font, element, style, left, top, right, bottom, scale, visuals);
            }
            case INPUT -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                paintInput(g, font, element, style, left, top, right, bottom, scale, visuals);
            }
            case TOGGLE -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                paintToggle(g, element, style, left, top, right, bottom, scale, visuals);
            }
            case SLIDER -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                paintSlider(g, font, element, style, left, top, right, bottom, scale, visuals);
            }
            case SLOT -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                var stack = visuals.item(element.name());
                if (stack != null && !stack.isEmpty()) {
                    // Centré dans son cadre : un objet fait seize pixels, l'emplacement
                    // ce que l'auteur a décidé.
                    g.renderItem(stack, (left + right) / 2 - 8, (top + bottom) / 2 - 8);
                }
            }
            case ENTITY_PREVIEW -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                paintEntity(g, element, left, top, right, bottom);
            }
            case DROPDOWN -> {
                fillBox(g, left, top, right, bottom, background, style, scale);
                paintDropdown(g, font, element, style, left, top, right, bottom, scale, visuals);
            }
            default -> fillBox(g, left, top, right, bottom, background, style, scale);
        }

        // La liste déroulante est le seul type qui dessine son propre libellé, parce que
        // celui-ci n'est pas toujours son texte : c'est le choix courant s'il y en a un,
        // et « text() » ne sert alors que d'INVITE. Le laisser passer ici superposait les
        // deux — l'invite par-dessus le chiffre choisi, illisibles tous les deux.
        //
        // Les autres peintres spécialisés (case à cocher, curseur, emplacement) ne
        // dessinent que leur décoration et comptent sur cette passe pour leur étiquette :
        // la convention tient toujours, avec une exception nommée plutôt que subie.
        if (element.kind() == fr.blueprint.core.graph.screen.ElementKind.DROPDOWN) {
            return;
        }
        var preview = visuals.preview(element);
        if (preview != null && !preview.isEmpty()) {
            paintText(g, font, element.withText(preview), left, top, right, bottom, scale);
        } else if (!element.text().isEmpty()) {
            paintText(g, font, element, left, top, right, bottom, scale);
        }
    }

    /**
     * Le damier magenta et le nom de la texture absente (AC4). C'est ce que Minecraft
     * fait lui-même, et pour la même raison : le nom est la seule chose qui permette à
     * quelqu'un de réparer, et il ne se trouve nulle part ailleurs à ce moment-là.
     */
    private static void paintMissing(GuiGraphics g, Font font,
                                     net.minecraft.resources.Identifier texture,
                                     @Nullable String missingPack,
                                     int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y += MISSING_CELL) {
            for (int x = left; x < right; x += MISSING_CELL) {
                boolean even = ((x - left) / MISSING_CELL + (y - top) / MISSING_CELL) % 2 == 0;
                g.fill(x, y, Math.min(x + MISSING_CELL, right),
                        Math.min(y + MISSING_CELL, bottom), even ? MISSING_A : MISSING_B);
            }
        }
        // Tronqué à la largeur disponible : un nom trop long déborderait sur les
        // éléments voisins, et l'écran deviendrait illisible là où il devait aider.
        String name = font.plainSubstrByWidth(texture.toString(), Math.max(0, right - left - 2));
        if (!name.isEmpty() && bottom - top >= font.lineHeight) {
            g.drawString(font, name, left + 1, top + 1, 0xFFFFFFFF, true);
        }
        // Et SURTOUT le pack qui manque : le nom du fichier dit ce qui est absent, le
        // nom du pack dit à qui le demander. C'est la seule des deux lignes qui permette
        // au joueur d'agir sans deviner (story 10.5, AC3).
        if (missingPack != null && bottom - top >= font.lineHeight * 2 + 2) {
            String detail = font.plainSubstrByWidth(
                    net.minecraft.client.resources.language.I18n.get(
                            "blueprint.pack.missing", missingPack),
                    Math.max(0, right - left - 2));
            g.drawString(font, detail, left + 1, top + 1 + font.lineHeight, 0xFFFFFFFF, true);
        }
    }

    /**
     * Une liste : ses lignes visibles, DÉCOUPÉES à son cadre, et son curseur.
     *
     * <p>Le découpage n'est pas cosmétique : sans lui, une liste de cent entrées
     * dessinerait les cent, par-dessus tout le reste de l'écran — le panneau qui la
     * contient, les boutons voisins, et jusqu'aux bords de la fenêtre.
     */
    /**
     * La liste déroulante <b>repliée</b> : le choix courant, et la flèche qui dit qu'il y
     * en a d'autres.
     *
     * <p>Le panneau déplié n'est pas dessiné ici, et c'est délibéré : il sort de la case
     * de l'élément et doit se peindre par-dessus tout le reste. Ce peintre parcourt les
     * éléments dans l'ordre de la table de disposition — il n'a pas de notion de calque,
     * et lui en donner une pour un seul type d'élément aurait compliqué le dessin de tous
     * les autres. Le déplié vit donc dans {@code BlueprintScreen}, qui sait déjà quel
     * élément est ouvert puisque c'est lui qui reçoit les clics.
     *
     * <p>Rendu identique dans le concepteur, où rien n'est jamais ouvert : l'auteur voit
     * la case telle qu'elle occupera la place, ce qui est ce qu'il compose.
     */
    static void paintDropdown(GuiGraphics g, Font font, ScreenElement element,
                              ElementStyle style, int left, int top, int right, int bottom,
                              int scale, Visuals visuals) {
        int inset = Math.max(1, style.padding() * scale);
        int arrow = 3 * scale;

        java.util.List<String> lines = visuals.lines(element.name());
        int selected = (int) Math.round(visuals.value(element.name()));
        // Hors bornes = rien de choisi : on montre le texte de l'élément, qui sert
        // d'invite (« Choisir une couleur… »). Un index périmé après un set_lines plus
        // court ne doit pas afficher une ligne d'une autre liste.
        String label = selected >= 0 && selected < lines.size()
                ? lines.get(selected)
                : (element.text().translate()
                        ? Component.translatable(element.text().value()).getString()
                        : element.text().value());

        String shown = font.plainSubstrByWidth(label,
                Math.max(0, right - left - 2 * inset - arrow - 2));
        g.drawString(font, shown, left + inset + 1,
                (top + bottom) / 2 - font.lineHeight / 2, style.textColor(), false);

        // Le chevron : trois traits qui rétrécissent, dessinés à la main faute de pouvoir
        // tracer un triangle — la même contrainte que les fils du canevas.
        int cx = right - inset - arrow;
        int cy = (top + bottom) / 2 - scale;
        for (int i = 0; i < arrow; i++) {
            g.fill(cx + i, cy + i, cx + 2 * arrow - i, cy + i + 1, style.textColor());
        }
    }

    private static void paintList(GuiGraphics g, Font font, ScreenElement element,
                                  ElementStyle style, int left, int top, int right, int bottom,
                                  int scale, Visuals visuals) {
        java.util.List<String> lines = visuals.lines(element.name());
        int rowHeight = Math.max(1, (int) Math.round(element.options().rowHeight() * scale));
        int inset = style.padding() * scale;
        var view = new fr.blueprint.core.graph.screen.ListView(lines.size(),
                fr.blueprint.core.graph.screen.ListView.rowsThatFit(
                        (double) (bottom - top - 2 * inset) / scale, element.options().rowHeight()),
                visuals.scroll(element.name()));

        // La ligne retenue, surlignée. Sans cela, cliquer dans une liste ne laisse aucune
        // trace : le joueur ne sait plus ce qu'il a choisi dès qu'il regarde ailleurs, et
        // rien ne distingue une liste où l'on choisit d'une liste que l'on lit.
        int selected = (int) Math.round(visuals.value(element.name()));

        g.enableScissor(left + inset, top + inset, right - inset, bottom - inset);
        int y = top + inset;
        for (int index : view.visibleIndices()) {
            if (index == selected) {
                g.fill(left + inset, y, right - inset, y + rowHeight, style.hoverBackground());
            }
            String line = font.plainSubstrByWidth(lines.get(index),
                    right - left - 2 * inset - (view.scrollable() ? 4 : 0));
            g.drawString(font, line, left + inset + 1, y + 1, style.textColor(), false);
            y += rowHeight;
        }
        g.disableScissor();

        if (view.scrollable()) {
            // Le curseur vit HORS du découpage : il borde la liste, il n'en fait pas
            // partie, et le découper le ferait disparaître au dernier cran.
            int trackTop = top + inset;
            int trackHeight = bottom - top - 2 * inset;
            int thumbHeight = Math.max(4, (int) (trackHeight * view.thumbFraction()));
            int thumbTop = trackTop + (int) ((trackHeight - thumbHeight) * view.thumbPosition());
            g.fill(right - inset - 2, trackTop, right - inset, bottom - inset, style.border());
            g.fill(right - inset - 2, thumbTop, right - inset, thumbTop + thumbHeight,
                    style.textColor());
        }
    }

    /**
     * Un champ de saisie. L'<b>indication</b> se montre quand il est vide, en grisé : un
     * champ vide sans indication ne dit pas ce qu'on attend, et c'est la première chose
     * qu'on cherche en le regardant.
     */
    private static void paintInput(GuiGraphics g, Font font, ScreenElement element,
                                   ElementStyle style, int left, int top, int right, int bottom,
                                   int scale, Visuals visuals) {
        String typed = visuals.input(element.name());
        boolean focused = visuals.focused(element.name());
        int inset = Math.max(2, style.padding() * scale);
        int baseline = (top + bottom) / 2 - font.lineHeight / 2;

        String shown = typed.isEmpty() && !focused ? element.options().placeholder() : typed;
        int color = typed.isEmpty() && !focused
                ? (style.textColor() & 0x00FFFFFF) | 0x80000000 : style.textColor();
        // La FIN visible, comme dans tout champ de saisie : c'est là qu'on tape.
        int room = right - left - 2 * inset - (focused ? 3 : 0);
        while (font.width(shown) > room && shown.length() > 1) {
            shown = shown.substring(1);
        }
        g.drawString(font, shown, left + inset, baseline, color, false);
        if (focused) {
            g.fill(left + inset + font.width(shown) + 1, baseline - 1,
                    left + inset + font.width(shown) + 2, baseline + font.lineHeight,
                    style.textColor());
        }
    }

    /**
     * L'aperçu d'une entité. Le modèle est <b>construit hors du monde</b> et mis en
     * cache — voir {@link EntityPreviews} pour ce que cela évite.
     *
     * <p>La taille suit le CADRE : un cochon et un dragon n'ont pas la même stature, et
     * une échelle fixe montrerait l'un minuscule et l'autre débordant. Le facteur est
     * déduit de la hauteur du cadre, bornée pour qu'une entité géante reste dedans.
     */
    private static void paintEntity(GuiGraphics g, ScreenElement element,
                                    int left, int top, int right, int bottom) {
        var entity = EntityPreviews.of(element.options().entity());
        if (entity == null) {
            return;
        }
        int height = bottom - top;
        int size = (int) Math.max(4, height / Math.max(1.0, entity.getBbHeight()) * 0.8);
        // Découpé au cadre : une entité haute déborderait sur le reste du menu, et
        // l'auteur ne saurait pas d'où vient le morceau de créature qui traverse son
        // panneau voisin.
        g.enableScissor(left, top, right, bottom);
        net.minecraft.client.gui.screens.inventory.InventoryScreen
                .renderEntityInInventoryFollowsMouse(g, left, top, right, bottom, size,
                        0.0625f, 0, 0, entity);
        g.disableScissor();
    }

    /** Une case à cocher : le carré, et la marque quand elle est cochée. */
    private static void paintToggle(GuiGraphics g, ScreenElement element, ElementStyle style,
                                    int left, int top, int right, int bottom,
                                    int scale, Visuals visuals) {
        int size = Math.min(right - left, bottom - top) - 2 * scale;
        int boxLeft = left + scale;
        int boxTop = (top + bottom) / 2 - size / 2;
        g.fill(boxLeft, boxTop, boxLeft + size, boxTop + size, style.border());
        int innerInset = Math.max(1, scale);
        g.fill(boxLeft + innerInset, boxTop + innerInset,
                boxLeft + size - innerInset, boxTop + size - innerInset,
                visuals.checked(element.name()) ? style.textColor() : style.background());
    }

    /** Un curseur : la piste, la portion parcourue, et la poignée. */
    private static void paintSlider(GuiGraphics g, Font font, ScreenElement element,
                                    ElementStyle style,
                                    int left, int top, int right, int bottom,
                                    int scale, Visuals visuals) {
        var options = element.options();
        double value = visuals.value(element.name());
        double fraction = options.fractionOf(value);
        int inset = Math.max(2, style.padding() * scale);
        int trackTop = (top + bottom) / 2 - scale;
        int trackLeft = left + inset;
        int trackRight = right - inset;

        // La valeur en clair, à droite de la piste. Sans elle, un curseur ne dit rien de
        // ce qu'il vaut : le joueur glisse et devine. La piste lui cède la place plutôt
        // que de passer dessous, parce qu'un chiffre à moitié recouvert est pire que pas
        // de chiffre.
        String readout = readout(options, value);
        int reserved = sliderReadoutWidth(font, element, value,
                (double) (right - left) / scale) * scale;
        if (reserved > 0) {
            trackRight -= reserved;
            g.drawString(font, readout, trackRight + 2 * scale,
                    (top + bottom) / 2 - font.lineHeight / 2, style.textColor(), false);
        }

        g.fill(trackLeft, trackTop, trackRight, trackTop + 2 * scale, style.border());
        int filled = trackLeft + (int) ((trackRight - trackLeft) * fraction);
        g.fill(trackLeft, trackTop, filled, trackTop + 2 * scale, style.textColor());
        // La poignée est bornée dans la piste : à fraction 1 elle sortirait du cadre.
        int handle = Math.clamp(filled, trackLeft + 2 * scale, trackRight - 2 * scale);
        g.fill(handle - 2 * scale, top + inset, handle + 2 * scale, bottom - inset,
                style.textColor());
    }

    /**
     * La largeur que la lecture de valeur retire à la piste, <b>en unités de mise en
     * page</b> — donc avant l'échelle.
     *
     * <p>Publique et partagée avec l'écran, à dessein : le glissement calcule sa fraction
     * sur la même piste que le dessin. Deux calculs séparés donneraient une poignée qui
     * ne suit pas la souris, et c'est exactement le genre de décalage qu'on ne remarque
     * qu'aux extrémités — là où il est le plus gênant.
     *
     * @return 0 si l'élément est trop étroit pour porter la valeur : elle disparaît alors
     *         plutôt que de manger toute la piste.
     */
    static int sliderReadoutWidth(Font font, ScreenElement element, double value,
                                  double laidOutWidth) {
        String readout = readout(element.options(), value);
        if (readout.isEmpty()) {
            return 0;
        }
        int width = font.width(readout) + 2;
        double usable = laidOutWidth - 2 * Math.max(2, element.style().padding());
        return usable > width + 8 ? width : 0;
    }

    /**
     * La valeur d'un curseur telle qu'on l'écrit à côté de lui.
     *
     * <p>Le nombre de décimales suit le <b>pas</b> : un curseur qui avance de 5 en 5 et
     * qui afficherait « 45,000 » ferait croire à une précision qu'il n'a pas. Et
     * {@code placeholder} sert d'<b>unité</b> — il n'a aucun autre sens pour un curseur,
     * et lui donner celui-là évite un champ de plus à encoder partout.
     */
    private static String readout(fr.blueprint.core.graph.screen.ElementOptions options,
                                  double value) {
        double step = options.step();
        int decimals = step >= 1 || step == 0 ? 0 : step >= 0.1 ? 1 : 2;
        String number = String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
        String unit = options.placeholder();
        return unit.isEmpty() ? number : number + unit;
    }

    private static void fillBox(GuiGraphics g, int left, int top, int right, int bottom,
                                int background, ElementStyle style, int scale) {
        if ((background >>> 24) != 0) {
            g.fill(left, top, right, bottom, background);
        }
        int border = style.borderWidth() * scale;
        if (border > 0 && (style.border() >>> 24) != 0) {
            g.fill(left, top, right, top + border, style.border());
            g.fill(left, bottom - border, right, bottom, style.border());
            g.fill(left, top, left + border, bottom, style.border());
            g.fill(right - border, top, right, bottom, style.border());
        }
    }

    private static void paintText(GuiGraphics g, Font font, ScreenElement element,
                                  int left, int top, int right, int bottom, int scale) {
        Component text = element.text().translate()
                ? Component.translatable(element.text().value())
                : Component.literal(element.text().value());
        int padding = element.style().padding() * scale;
        int inner = right - left - padding * 2;
        if (inner <= 0) {
            return;
        }
        if (element.style().wrap()) {
            paintWrapped(g, font, element, text, left, top, right, bottom, padding, inner);
            return;
        }
        // Le texte n'est pas mis à l'échelle : agrandir la police d'un facteur entier
        // dans le concepteur donnerait un aperçu trompeur, puisqu'en jeu elle garde sa
        // taille. Mieux vaut un texte petit et juste qu'un texte gros et faux.
        String plain = font.plainSubstrByWidth(text.getString(), inner);
        int y = top + (bottom - top - font.lineHeight) / 2;
        int x = switch (element.style().align()) {
            case LEFT -> left + padding;
            case CENTER -> left + padding + (inner - font.width(plain)) / 2;
            case RIGHT -> right - padding - font.width(plain);
        };
        g.drawString(font, plain, x, y, element.style().textColor(), false);
    }

    /**
     * Le texte coupé aux mots, centré verticalement dans son cadre.
     *
     * <p>Sans lui, un paragraphe — description d'objet, règle de serveur, réplique de
     * dialogue — devait être découpé à la main en autant d'étiquettes empilées, qu'il
     * fallait repositionner à chaque retouche du texte. Et une traduction plus longue que
     * l'original cassait la mise en page sans que l'auteur le voie : il ne lit pas les
     * vingt langues de son serveur.
     *
     * <p>Les lignes qui ne tiennent pas en hauteur sont <b>coupées</b> plutôt que
     * débordées : un paragraphe qui dépasse de son cadre passerait par-dessus l'élément
     * voisin, et l'on chercherait longtemps d'où vient la ligne de texte qui traverse le
     * menu. Une ligne est dessinée quoi qu'il arrive, même dans un cadre trop bas — un
     * cadre vide ne dirait pas qu'il contient un texte trop long, il dirait qu'il est
     * vide, et l'auteur chercherait la panne ailleurs.
     */
    private static void paintWrapped(GuiGraphics g, Font font, ScreenElement element,
                                     Component text, int left, int top, int right,
                                     int bottom, int padding, int inner) {
        var lines = font.split(text, inner);
        if (lines.isEmpty()) {
            return;
        }
        int room = bottom - top - padding * 2;
        int fits = Math.max(1, Math.min(lines.size(), room / font.lineHeight));
        int blockHeight = fits * font.lineHeight;
        int y = top + (bottom - top - blockHeight) / 2;
        for (int i = 0; i < fits; i++) {
            var line = lines.get(i);
            int width = font.width(line);
            int x = switch (element.style().align()) {
                case LEFT -> left + padding;
                case CENTER -> left + padding + (inner - width) / 2;
                case RIGHT -> right - padding - width;
            };
            g.drawString(font, line, x, y + i * font.lineHeight,
                    element.style().textColor(), false);
        }
    }

    /** Le rectangle d'un élément converti en pixels — le concepteur en a besoin aussi. */
    public static int[] pixels(ScreenLayout.@Nullable Rect rect,
                               int originX, int originY, int scale) {
        if (rect == null) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{originX + (int) Math.round(rect.x() * scale),
                originY + (int) Math.round(rect.y() * scale),
                originX + (int) Math.round(rect.right() * scale),
                originY + (int) Math.round(rect.bottom() * scale)};
    }
}
