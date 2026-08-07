package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinShape;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Rendu d'un nœud depuis son descripteur seul (principe P1 : aucun accès au
 * {@code NodeType} ni au mod fournisseur). Tout se dessine sous la pose 2D translatée
 * puis mise à l'échelle : les coordonnées locales sont en unités monde entières.
 *
 * <p>La forme du pin double sa couleur (NFR11) : ▶ exec, ● data, ◆ objet, ▦ liste.
 */
public final class NodeWidget {

    // Les couleurs structurelles viennent du thème (5.7) ; le texte reste local.
    /**
     * La teinte du type est MÉLANGÉE au fond du nœud plutôt que posée en transparence.
     *
     * <p>Un aplat translucide laisse voir la grille au travers et change d'aspect selon
     * ce qui passe dessous ; un mélange donne une couleur stable, plus sourde, sur
     * laquelle le nom reste lisible quel que soit le type — y compris les clairs.
     */
    private static final double PILL_MIX_TOP = 0.62;
    private static final double PILL_MIX_BOTTOM = 0.38;
    private static final int PILL_BORDER = 0xFF14171C;

    private static final int TITLE_COLOR = 0xFFE6E6E6;
    private static final int PIN_LABEL_COLOR = 0xFFABB2BF;

    private static int nodeBackground() {
        return fr.blueprint.client.theme.Theme.current().nodeBackground();
    }

    private static int nodeBorder() {
        return fr.blueprint.client.theme.Theme.current().nodeBorder();
    }

    private static int selectedBorder() {
        return fr.blueprint.client.theme.Theme.current().nodeSelected();
    }

    private static int ghostColor() {
        return fr.blueprint.client.theme.Theme.current().ghost();
    }

    /**
     * Pastille de permission, ou 0 pour les niveaux ordinaires. SAFE et GAMEPLAY sont
     * l'immense majorité des nœuds : les marquer serait du bruit sur tout le graphe.
     */
    static int permissionBadge(fr.blueprint.api.node.Permission permission) {
        return switch (permission) {
            case WORLD -> 0xFFE0AF68;
            case ADMIN -> 0xFFF7768E;
            default -> 0;
        };
    }

    // ------------------------------------------- châssis du nœud (5.13, aspect UE5)

    /** Rayon d'arrondi, en unités monde. Deux pixels suffisent à casser l'angle droit. */
    private static final int CORNER = 2;
    private static final int SHADOW_LAYERS = 2;
    private static final int SHADOW_OFFSET = 2;
    private static final int SHADOW_COLOR = 0x40000000;
    private static final int GLOW_RINGS = 3;
    private static final int GLOW_ALPHA = 0x60;
    /** Centre du pictogramme et retrait du titre, qui lui laisse la place. */
    private static final int GLYPH_X = 9;
    private static final int TITLE_INDENT = 9;

    /**
     * Rectangle à coins abattus. Pas d'anticrénelage — à cette échelle, retirer un
     * pixel à chaque coin suffit à supprimer l'angle droit, et c'est tout ce qui
     * distingue une boîte d'un nœud.
     */
    private static void roundedFill(GuiGraphics g, int x0, int y0, int x1, int y1,
                                    int color, int radius) {
        int r = Math.min(radius, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        g.fill(x0 + r, y0, x1 - r, y1, color);
        for (int i = 0; i < r; i++) {
            // Chaque rangée d'extrémité rentre d'un pixel de plus : l'escalier fait
            // l'arrondi.
            int inset = r - i;
            g.fill(x0 + inset - 1, y0 + i, x0 + r, y0 + i + 1, color);
            g.fill(x1 - r, y0 + i, x1 - inset + 1, y0 + i + 1, color);
            g.fill(x0 + inset - 1, y1 - i - 1, x0 + r, y1 - i, color);
            g.fill(x1 - r, y1 - i - 1, x1 - inset + 1, y1 - i, color);
        }
        // Les flancs entre les deux arrondis.
        g.fill(x0, y0 + r, x0 + r, y1 - r, color);
        g.fill(x1 - r, y0 + r, x1, y1 - r, color);
    }

    /** Bandeau dégradé aux coins hauts abattus, pour épouser l'arrondi du nœud. */
    private static void roundedTopFill(GuiGraphics g, int x0, int y0, int x1, int y1,
                                       int top, int bottom) {
        g.fillGradient(x0 + CORNER, y0, x1 - CORNER, y1, top, bottom);
        for (int i = 0; i < CORNER; i++) {
            int inset = CORNER - i;
            g.fillGradient(x0 + inset - 1, y0 + i, x0 + CORNER, y1, top, bottom);
            g.fillGradient(x1 - CORNER, y0 + i, x1 - inset + 1, y1, top, bottom);
        }
        g.fillGradient(x0, y0 + CORNER, x0 + CORNER, y1, top, bottom);
        g.fillGradient(x1 - CORNER, y0 + CORNER, x1, y1, top, bottom);
    }

    /**
     * Plaque du châssis : rectangle abattu, ou capsule pour une pastille.
     *
     * <p>L'ombre, le halo de sélection, le liseré et le fond passent <b>tous</b> par ici.
     * Une capsule posée sur une ombre rectangulaire laisse dépasser quatre coins — c'est
     * exactement ce que montrait la capture, et la raison pour laquelle la décision de
     * forme doit être prise à un seul endroit.
     */
    private static void plate(GuiGraphics g, int x0, int y0, int x1, int y1,
                              int colour, int radius, boolean pill) {
        if (pill) {
            capsule(g, x0, y0, x1, y1, colour, colour);
        } else {
            roundedFill(g, x0, y0, x1, y1, colour, radius);
        }
    }

    /** Mélange opaque de deux couleurs : {@code amount} = part de la première. */
    private static int blend(int colour, int onto, double amount) {
        int out = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int a = (colour >>> shift) & 0xFF;
            int b = (onto >>> shift) & 0xFF;
            out |= (int) Math.round(b + (a - b) * amount) << shift;
        }
        return out;
    }

    /** Même teinte, alpha imposé — pour les anneaux du halo. */
    private static int fade(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Alpha appliqué à la couleur de catégorie sur l'en-tête. */
    /** Dégradé de l'en-tête : plus dense en haut, il s'éteint vers le corps du nœud. */
    private static final int HEADER_ALPHA_TOP = 0x80;
    private static final int HEADER_ALPHA_BOTTOM = 0x33;

    /** Sous ce zoom, plus de titre du tout ; en dessous de 0,5×, boîte + titre seul (UX §3). */
    private static final double TITLE_FADE_ZOOM = 0.35;
    private static final double DETAIL_FADE_ZOOM = 0.5;

    /** Pin à atténuer pendant un tracé de lien (cible incompatible, UX §5). */
    public interface PinDimmer {
        boolean dimmed(String pin, boolean output);
    }

    /** Valeur littérale à afficher pour un pin d'entrée — null = masquée (câblé). */
    public interface LiteralProvider {
        @Nullable LiteralValue literalOf(String pin);
    }

    private static final int LITERAL_COLOR = 0xFFD5D8DC;
    /** Champ creusé : le littéral doit se VOIR comme un champ, même vide (retour terrain). */
    private static final int LITERAL_FIELD_BG = 0xFF14171C;
    private static final int LITERAL_FIELD_BORDER = 0xFF3A3D42;
    private static final int LITERAL_EDIT_BG = 0xFF141922;
    private static final int LITERAL_EDIT_BORDER = 0xFF7AA2F7;
    private static final int LITERAL_INVALID_BORDER = 0xFFF7768E;
    private static final int BOOL_ON = 0xFF9ECE6A;

    private NodeWidget() {
    }

    public static void render(GuiGraphics g, Font font, NodeGeometry.Box box,
                              @Nullable NodeDescriptor desc, boolean selected,
                              double zoom, double screenX, double screenY,
                              @Nullable PinDimmer dimmer, @Nullable LiteralProvider literals,
                              @Nullable LiteralEditState edit, int outlineColor, int tint) {
        int w = (int) Math.round(box.width());
        int h = (int) Math.round(box.height());
        g.pose().pushMatrix();
        g.pose().translate((float) screenX, (float) screenY);
        g.pose().scale((float) zoom, (float) zoom);

        boolean ghost = desc == null;
        // La forme est décidée UNE fois, ici, et tout le châssis la suit.
        boolean pill = !ghost && NodeGeometry.isPill(box.node());
        // Ombre portée : c'est elle qui décolle le nœud de la grille et qui donne la
        // profondeur d'Unreal. Dessinée en premier, décalée, sous tout le reste.
        for (int i = 0; i < SHADOW_LAYERS; i++) {
            plate(g, -i, SHADOW_OFFSET + i, w + i, h + SHADOW_OFFSET + i,
                    SHADOW_COLOR, CORNER + 1, pill);
        }
        if (selected) {
            // Halo : plusieurs anneaux de plus en plus transparents, plutôt qu'un
            // liseré d'un pixel qui se perdait sur un fond clair.
            for (int i = GLOW_RINGS; i >= 1; i--) {
                plate(g, -i, -i, w + i, h + i,
                        fade(selectedBorder(), GLOW_ALPHA / i), CORNER + i, pill);
            }
            plate(g, 0, 0, w, h, selectedBorder(), CORNER, pill);
            plate(g, 1, 1, w - 1, h - 1, nodeBackground(), CORNER, pill);
        } else if (outlineColor != 0) {
            // Nœud fautif : liseré de la couleur de la sévérité (UX §8).
            plate(g, 0, 0, w, h, outlineColor, CORNER, pill);
            plate(g, 1, 1, w - 1, h - 1, nodeBackground(), CORNER, pill);
        } else if (ghost) {
            roundedFill(g, 0, 0, w, h, nodeBackground(), CORNER);
            dashedBorder(g, w, h, ghostColor());
        } else {
            plate(g, 0, 0, w, h, pill ? PILL_BORDER : nodeBorder(), CORNER, pill);
            plate(g, 1, 1, w - 1, h - 1, nodeBackground(), CORNER, pill);
        }

        if (ghost) {
            renderGhostContent(g, font, box.node().typeId(), w, zoom);
        } else {
            renderContent(g, font, box, desc, w, zoom, dimmer, literals, edit, tint);
        }
        g.pose().popMatrix();
    }

    private static void renderContent(GuiGraphics g, Font font, NodeGeometry.Box box,
                                      NodeDescriptor desc, int w, double zoom,
                                      @Nullable PinDimmer dimmer, @Nullable LiteralProvider literals,
                                      @Nullable LiteralEditState edit, int tint) {
        // Une PASTILLE : la lecture d'une variable, à la manière d'Unreal. Ni bandeau ni
        // titre — le nom et sa sortie, teintés par le type de la variable. Un nœud de
        // lecture n'a rien de plus à dire, et lui donner le châssis complet d'un nœud
        // d'action annonçait une machinerie là où il n'y a qu'une valeur.
        if (NodeGeometry.isPill(box.node())) {
            renderPill(g, font, box, desc, w, zoom, dimmer, literals, tint);
            return;
        }
        // Le SET d'une variable prend la couleur de CETTE variable, pas celle de sa
        // catégorie : c'est ce qui fait qu'on repère un booléen d'un flottant à travers
        // tout un graphe, sans lire un seul nom.
        int category = tint != 0 ? tint : categoryColor(desc.category());
        int header = (int) NodeGeometry.TITLE_HEIGHT;
        // Dégradé plutôt qu'aplat : c'est ce qui donne le relief de l'en-tête d'Unreal.
        // Le haut est arrondi comme le nœud, sinon le bandeau déborderait des coins.
        roundedTopFill(g, 1, 1, w - 1, header, fade(category, HEADER_ALPHA_TOP),
                fade(category, HEADER_ALPHA_BOTTOM));
        // Filet vif sous le titre : la catégorie se lit d'un coup d'œil, même quand le
        // dégradé est sombre.
        g.fill(1, header - 1, w - 1, header, fade(category, 0xC0));
        if (zoom < TITLE_FADE_ZOOM) {
            return;
        }
        int badge = permissionBadge(desc.permission());
        categoryGlyph(g, desc.category(), GLYPH_X, header / 2, fade(category, 0xFF));
        // Le titre laisse la place au pictogramme ET au badge, sinon il passe dessous.
        String title = font.plainSubstrByWidth(
                NodeTitle.of(box.node(), desc, I18n::get),
                w - 12 - TITLE_INDENT - (badge == 0 ? 0 : 10));
        // Centré dans l'en-tête plutôt que posé à cinq pixels du haut : la valeur en dur
        // convenait à un bandeau de 18, elle laissait le titre collé au bord dès qu'il a
        // grandi.
        g.drawString(font, title, 6 + TITLE_INDENT,
                (header - font.lineHeight) / 2 + 1, TITLE_COLOR, false);
        if (badge != 0) {
            // Un nœud qui modifie le monde ou exige l'op doit se repérer sans survol :
            // c'est ce qui décide si un blueprint est refusé par le plafond du serveur.
            g.fill(w - 11, header / 2 - 3, w - 5, header / 2 + 3, badge);
        }

        if (zoom < DETAIL_FADE_ZOOM) {
            return;
        }
        for (int i = 0; i < desc.inputs().size(); i++) {
            NodeDescriptor.PinDescriptor pin = desc.inputs().get(i);
            int cy = rowCenterY(box, i);
            boolean dim = dimmer != null && dimmer.dimmed(pin.name(), false);
            drawPin(g, pin.type().shape(), dim(pin.type().color(), dim),
                    (int) NodeGeometry.PIN_INSET, cy);
            // Le libellé s'arrête au bord du CHAMP quand la rangée en porte un. Il était
            // borné à « w / 2 - 14 », ce qui tenait par coïncidence à 140 de large et
            // recouvrait le champ dès qu'on élargissait le nœud.
            boolean hasField = pin.kind() == PinKind.DATA && literals != null
                    && literals.literalOf(pin.name()) != null;
            // Un booléen se CLIQUE, il ne se saisit pas : sa case fait douze pixels, et
            // le libellé peut occuper tout le reste. Le traiter comme un champ de saisie
            // laissait sept caractères à « through_fluids ».
            double room = hasField && pin.type() == PinTypes.BOOL
                    ? NodeGeometry.checkboxLabelWidth(w, i < desc.outputs().size())
                    : NodeGeometry.inputLabelWidth(w, hasField);
            String label = font.plainSubstrByWidth(pin.name(), (int) room);
            g.drawString(font, label,
                    (int) (NodeGeometry.PIN_INSET + NodeGeometry.LABEL_GAP), cy - 4,
                    dim(PIN_LABEL_COLOR, dim), false);
            if (pin.kind() == PinKind.DATA && literals != null) {
                renderLiteral(g, font, box, w, i, pin, literals, edit,
                        i < desc.outputs().size());
            }
        }
        for (int i = 0; i < desc.outputs().size(); i++) {
            NodeDescriptor.PinDescriptor pin = desc.outputs().get(i);
            int cy = rowCenterY(box, i);
            int cx = w - (int) NodeGeometry.PIN_INSET;
            boolean dim = dimmer != null && dimmer.dimmed(pin.name(), true);
            drawPin(g, pin.type().shape(), dim(pin.type().color(), dim), cx, cy);
            boolean facingField = i < desc.inputs().size()
                    && desc.inputs().get(i).kind() == PinKind.DATA && literals != null
                    && literals.literalOf(desc.inputs().get(i).name()) != null;
            String label = font.plainSubstrByWidth(pin.name(),
                    (int) NodeGeometry.outputLabelWidth(w, facingField));
            g.drawString(font, label,
                    cx - (int) NodeGeometry.LABEL_GAP - font.width(label), cy - 4,
                    dim(PIN_LABEL_COLOR, dim), false);
        }
    }

    /**
     * Une <b>capsule</b> : des extrémités réellement demi-circulaires, dessinées rangée
     * par rangée à partir de l'équation du cercle.
     *
     * <p>{@link #roundedFill} ne convient pas ici. Son escalier rentre d'un pixel par
     * rangée, ce qui donne un joli coin abattu pour un rayon de deux — et un <b>biseau à
     * quarante-cinq degrés</b> pour un rayon de onze. La première pastille livrée
     * ressemblait ainsi à un hexagone, pas à une pastille.
     *
     * <p>Le dégradé est vertical, clair en haut et sombre en bas, comme les bandeaux des
     * nœuds. Le premier essai le voulait horizontal et le simulait par deux passages
     * superposés : cela laissait une <b>couture verticale</b> au milieu de la pastille,
     * qui se lisait comme un défaut d'affichage plutôt que comme un dégradé.
     */
    private static void capsule(GuiGraphics g, int x0, int y0, int x1, int y1,
                                int top, int bottom) {
        int height = y1 - y0;
        for (int row = 0; row < height; row++) {
            int inset = capsuleInset(row, height);
            int colour = lerp(top, bottom, row / Math.max(1.0, height - 1.0));
            g.fill(x0 + inset, y0 + row, x1 - inset, y0 + row + 1, colour);
        }
    }

    /**
     * Retrait horizontal de la rangée {@code row} d'une capsule de {@code height} de haut.
     *
     * <p>C'est <b>toute</b> la forme de la pastille, isolée ici pour être vérifiable sans
     * client. Le retrait suit le cercle — {@code r − √(r² − dy²)} — là où l'escalier de
     * {@code roundedFill} rentrait d'un pixel par rangée, c'est-à-dire d'exactement 45°.
     */
    static int capsuleInset(int row, int height) {
        double r = height / 2.0;
        double dy = (row + 0.5) - r;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** Interpolation composante par composante, alpha compris. */
    private static int lerp(int from, int to, double t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            out |= (int) Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }

    /**
     * La pastille d'une lecture de variable.
     *
     * <p>Un liseré sombre puis le corps teinté : sans le liseré, la capsule se fond dans
     * la grille dès que la teinte est sombre — un booléen rouge sur fond gris n'avait plus
     * de contour du tout.
     */
    private static void renderPill(GuiGraphics g, Font font, NodeGeometry.Box box,
                                   NodeDescriptor desc, int w, double zoom,
                                   @Nullable PinDimmer dimmer,
                                   @Nullable LiteralProvider literals, int tint) {
        int h = (int) Math.round(box.height());
        int colour = tint != 0 ? tint : categoryColor(desc.category());
        // Le liseré et l'ombre ont déjà été posés par le châssis, en capsule : ne reste
        // que le corps. Le redessiner ici écraserait le halo d'une pastille sélectionnée.
        capsule(g, 1, 1, w - 1, h - 1,
                blend(colour, nodeBackground(), PILL_MIX_TOP),
                blend(colour, nodeBackground(), PILL_MIX_BOTTOM));
        if (zoom < TITLE_FADE_ZOOM) {
            return;
        }
        var literal = literals == null ? null : literals.literalOf("var");
        String name = literal != null && literal.value() instanceof String s
                ? s : I18n.get(desc.titleKey());
        g.drawString(font, font.plainSubstrByWidth(name, w - 22), 8,
                (h - font.lineHeight) / 2 + 1, TITLE_COLOR, false);
        if (!desc.outputs().isEmpty()) {
            var pin = desc.outputs().getFirst();
            boolean dim = dimmer != null && dimmer.dimmed(pin.name(), true);
            // rowCenterY, et non h / 2 : les deux donnent 11 aujourd'hui, mais l'un lit
            // la géométrie que le fil et le clic lisent aussi, l'autre la devine.
            drawPin(g, pin.type().shape(), dim(pin.type().color(), dim),
                    w - (int) NodeGeometry.PIN_INSET, rowCenterY(box, 0));
        }
    }

    /** Cible incompatible pendant un tracé : ~30 % d'opacité (UX §5). */
    private static int dim(int color, boolean dim) {
        return dim ? (color & 0x00FFFFFF) | 0x4D000000 : color;
    }

    /**
     * Valeur littérale d'un pin d'entrée data (5.2b) : texte, case bool, ou champ
     * d'édition actif. Coordonnées locales (pose déjà à l'échelle).
     */
    private static void renderLiteral(GuiGraphics g, Font font, NodeGeometry.Box box,
                                      int w, int row, NodeDescriptor.PinDescriptor pin,
                                      LiteralProvider literals, @Nullable LiteralEditState edit,
                                      boolean rowHasOutput) {
        int x1 = (int) (w * NodeGeometry.LITERAL_LEFT);
        int x2 = (int) (w * (rowHasOutput ? NodeGeometry.LITERAL_RIGHT
                : NodeGeometry.LITERAL_WIDE_RIGHT));
        // Le champ DESSINÉ et la zone CLIQUABLE partent du même calcul : les deux se
        // déduisaient chacune leurs bords, et toute marge ajoutée d'un côté faisait
        // cliquer à côté de ce qu'on voyait.
        int rowTop = (int) (NodeGeometry.titleHeight(box) + row * NodeGeometry.ROW_HEIGHT);
        int top = rowTop + (int) NodeGeometry.FIELD_INSET_Y;
        int bottom = rowTop + (int) NodeGeometry.ROW_HEIGHT - (int) NodeGeometry.FIELD_INSET_Y;
        int cy = rowTop + (int) NodeGeometry.ROW_HEIGHT / 2;

        boolean editing = edit != null && edit.isOpen()
                && box.node().uuid().equals(edit.node()) && pin.name().equals(edit.pin());
        if (editing) {
            g.fill(x1 - 1, top, x2 + 1, bottom, LITERAL_EDIT_BG);
            int border = edit.isValid() ? LITERAL_EDIT_BORDER : LITERAL_INVALID_BORDER;
            g.fill(x1 - 1, top, x2 + 1, top + 1, border);
            g.fill(x1 - 1, bottom - 1, x2 + 1, bottom, border);
            g.fill(x1 - 1, top, x1, bottom, border);
            g.fill(x2, top, x2 + 1, bottom, border);
            String shown = edit.mode() == LiteralEditState.Mode.ENUM
                    ? "‹ " + edit.options().get(edit.optionIndex()).getName() + " ›"
                    : edit.text() + "_";
            // Garder la fin visible : c'est là qu'on tape.
            while (font.width(shown) > x2 - x1 - 4 && shown.length() > 1) {
                shown = shown.substring(1);
            }
            g.drawString(font, shown, x1 + 2, cy - 4, TITLE_COLOR, false);
            return;
        }

        LiteralValue value = literals.literalOf(pin.name());
        if (value == null) {
            return; // câblé, ou rien à montrer
        }
        if (pin.type() == PinTypes.BOOL) {
            boolean on = value.value() instanceof Boolean b && b;
            g.fill(x2 - 8, cy - 4, x2, cy + 4, nodeBorder());
            g.fill(x2 - 7, cy - 3, x2 - 1, cy + 3, on ? BOOL_ON : nodeBackground());
            return;
        }
        // Champ CREUSÉ, visible même vide : un littéral qu'on peut modifier doit se
        // voir comme un champ, pas comme du texte gris posé là. C'était le premier
        // reproche fait à l'éditeur en jeu.
        g.fill(x1 - 1, top, x2 + 1, bottom, LITERAL_FIELD_BG);
        g.fill(x1 - 1, top, x2 + 1, top + 1, LITERAL_FIELD_BORDER);
        g.fill(x1 - 1, bottom - 1, x2 + 1, bottom, LITERAL_FIELD_BORDER);
        g.fill(x1 - 1, top, x1, bottom, LITERAL_FIELD_BORDER);
        g.fill(x2, top, x2 + 1, bottom, LITERAL_FIELD_BORDER);

        String text = LiteralEditState.display(pin.type(), value);
        if (text.isEmpty()) {
            return;
        }
        text = font.plainSubstrByWidth(text, x2 - x1 - 4);
        g.drawString(font, text, x2 - 2 - font.width(text), cy - 4, LITERAL_COLOR, false);
    }

    private static void renderGhostContent(GuiGraphics g, Font font, Identifier typeId,
                                           int w, double zoom) {
        if (zoom < TITLE_FADE_ZOOM) {
            return;
        }
        String title = font.plainSubstrByWidth(typeId.toString(), w - 12);
        g.drawString(font, title, 6,
                ((int) NodeGeometry.TITLE_HEIGHT - font.lineHeight) / 2 + 1,
                ghostColor(), false);
        if (zoom < DETAIL_FADE_ZOOM) {
            return;
        }
        String message = font.plainSubstrByWidth(
                I18n.get("blueprint.editor.ghost", typeId.getNamespace()), w - 12);
        g.drawString(font, message, 6, (int) NodeGeometry.TITLE_HEIGHT + 6,
                PIN_LABEL_COLOR, false);
    }

    /**
     * Centre vertical LOCAL d'une rangée — la même formule que {@link NodeGeometry}, avec
     * son décalage d'en-tête pris à la boîte plutôt qu'à la constante.
     *
     * <p>Le dessin et le clic doivent lire le même décalage : c'est là que ce projet a
     * déjà vu diverger deux calculs qui avaient tous les deux l'air justes.
     */
    private static int rowCenterY(NodeGeometry.Box box, int row) {
        return (int) (NodeGeometry.titleHeight(box) + row * NodeGeometry.ROW_HEIGHT
                + NodeGeometry.ROW_HEIGHT / 2);
    }

    // --------------------------------------------- pictogramme de catégorie (5.13)

    /**
     * Petit pictogramme dans l'en-tête, à la manière des icônes d'Unreal. Il vient de
     * la <b>catégorie</b>, que le descripteur porte déjà : une vraie icône par nœud
     * demanderait un champ de plus dans le descripteur, donc dans la surface d'API et
     * dans la synchro réseau — beaucoup de tuyauterie pour ce que la catégorie dit
     * déjà. Et une catégorie se reconnaît de plus loin qu'un nom.
     *
     * <p>Dessiné dans un carré de 7×7 centré sur ({@code cx}, {@code cy}).
     */
    private static void categoryGlyph(GuiGraphics g, String category, int cx, int cy, int color) {
        // Même règle que la couleur : le pictogramme est celui de la catégorie parente.
        switch (fr.blueprint.api.node.NodeCategory.parentOf(category)) {
            case "flow" -> { // ▶ un chevron
                for (int dx = 0; dx <= 3; dx++) {
                    int hh = 3 - dx;
                    g.fill(cx - 2 + dx, cy - hh, cx - 1 + dx, cy + hh + 1, color);
                }
            }
            case "math" -> { // +
                g.fill(cx - 3, cy - 1, cx + 4, cy + 1, color);
                g.fill(cx - 1, cy - 3, cx + 1, cy + 4, color);
            }
            case "logic" -> { // = deux barres
                g.fill(cx - 3, cy - 2, cx + 4, cy - 1, color);
                g.fill(cx - 3, cy + 1, cx + 4, cy + 2, color);
            }
            case "string", "text" -> { // deux guillemets
                g.fill(cx - 3, cy - 3, cx - 1, cy, color);
                g.fill(cx + 1, cy - 3, cx + 3, cy, color);
            }
            case "list", "struct" -> { // trois lignes
                for (int dy = -3; dy <= 3; dy += 3) {
                    g.fill(cx - 3, cy + dy, cx + 4, cy + dy + 1, color);
                }
            }
            case "world" -> { // un bloc en perspective
                g.fill(cx - 3, cy - 1, cx + 4, cy + 4, color);
                g.fill(cx - 2, cy - 3, cx + 3, cy - 1, color);
            }
            case "entity" -> { // un losange
                for (int dy = -3; dy <= 3; dy++) {
                    int hw = 3 - Math.abs(dy);
                    g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
                }
            }
            case "player" -> { // tête + épaules
                g.fill(cx - 1, cy - 3, cx + 2, cy, color);
                g.fill(cx - 3, cy + 1, cx + 4, cy + 4, color);
            }
            case "item" -> { // un carré évidé
                g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
                g.fill(cx - 1, cy - 1, cx + 2, cy + 2, nodeBackground());
            }
            case "debug" -> { // un point d'exclamation
                g.fill(cx - 1, cy - 3, cx + 1, cy + 1, color);
                g.fill(cx - 1, cy + 2, cx + 1, cy + 4, color);
            }
            case "event" -> { // un éclair
                g.fill(cx, cy - 3, cx + 3, cy - 2, color);
                g.fill(cx - 1, cy - 2, cx + 2, cy, color);
                g.fill(cx - 2, cy, cx + 3, cy + 1, color);
                g.fill(cx - 3, cy + 1, cx, cy + 2, color);
                g.fill(cx - 2, cy + 2, cx + 1, cy + 4, color);
            }
            case "function" -> { // ƒ — la barre montante, sa boucle et sa traverse
                g.fill(cx, cy - 3, cx + 2, cy + 4, color);
                g.fill(cx + 1, cy - 4, cx + 3, cy - 2, color);
                g.fill(cx - 2, cy - 1, cx + 3, cy, color);
            }
            default -> g.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
        }
    }

    // ------------------------------------------------------------------- formes

    private static void drawPin(GuiGraphics g, PinShape shape, int color, int cx, int cy) {
        switch (shape) {
            case EXEC -> {
                for (int dx = 0; dx <= 4; dx++) {
                    int hh = Math.max(0, 4 - dx);
                    g.fill(cx - 2 + dx, cy - hh, cx - 1 + dx, cy + hh + 1, color);
                }
            }
            case CIRCLE -> {
                int[] hw = {1, 2, 3, 3, 3, 2, 1};
                for (int dy = -3; dy <= 3; dy++) {
                    int h = hw[dy + 3];
                    g.fill(cx - h, cy + dy, cx + h + 1, cy + dy + 1, color);
                }
            }
            case DIAMOND -> {
                for (int dy = -3; dy <= 3; dy++) {
                    int h = 3 - Math.abs(dy);
                    g.fill(cx - h, cy + dy, cx + h + 1, cy + dy + 1, color);
                }
            }
            case TRIANGLE -> {
                for (int dy = -3; dy <= 3; dy++) {
                    int h = (dy + 3) / 2;
                    g.fill(cx - h, cy + dy, cx + h + 1, cy + dy + 1, color);
                }
            }
            case RING -> {
                int[] hw = {1, 2, 3, 3, 3, 2, 1};
                for (int dy = -3; dy <= 3; dy++) {
                    int h = hw[dy + 3];
                    g.fill(cx - h, cy + dy, cx + h + 1, cy + dy + 1, color);
                }
                g.fill(cx - 1, cy - 1, cx + 2, cy + 2, nodeBackground());
            }
            case CROSS -> {
                g.fill(cx - 3, cy - 1, cx + 4, cy + 2, color);
                g.fill(cx - 1, cy - 3, cx + 2, cy + 4, color);
            }
            case ARRAY -> g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
            case MAP -> {
                g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
                g.fill(cx - 1, cy - 1, cx + 2, cy + 2, nodeBackground());
            }
        }
    }

    private static void dashedBorder(GuiGraphics g, int w, int h, int color) {
        for (int x = 0; x < w; x += 7) {
            int x2 = Math.min(x + 4, w);
            g.fill(x, 0, x2, 1, color);
            g.fill(x, h - 1, x2, h, color);
        }
        for (int y = 0; y < h; y += 7) {
            int y2 = Math.min(y + 4, h);
            g.fill(0, y, 1, y2, color);
            g.fill(w - 1, y, w, y2, color);
        }
    }

    /** Teintes d'en-tête par catégorie (UX §12, complétées pour les catégories standard). */
    static int categoryColor(String category) {
        // Une sous-catégorie porte la couleur de sa parente : c'est le parent qui
        // identifie d'un coup d'œil, la sous-catégorie qui range dans le menu. Deux
        // teintes pour « math/arithmetic » et « math/function » ne feraient que
        // brouiller la lecture d'un graphe.
        return switch (fr.blueprint.api.node.NodeCategory.parentOf(category)) {
            case "flow" -> 0xFF8A8F98;
            case "math" -> 0xFF7DCFFF;
            case "logic" -> 0xFF56B6C2;
            case "string", "text" -> 0xFFE5C07B;
            case "list", "struct" -> 0xFFABB2BF;
            case "world" -> 0xFF9ECE6A;
            case "entity" -> 0xFFE0AF68;
            case "player" -> 0xFFBB9AF7;
            case "item" -> 0xFFD19A66;
            case "debug" -> 0xFFF7768E;
            case "event" -> 0xFFE06C75;
            // Le violet des fonctions, comme Unreal : c'est la convention que tout auteur
            // venant du Blueprint d'Unreal reconnaîtra sans qu'on la lui explique.
            case "function" -> 0xFF9D7CD8;
            default -> 0xFF8A8F98;
        };
    }
}
