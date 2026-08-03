package fr.blueprint.client.theme;

/**
 * Jetons de style de l'éditeur (story 5.7, UX §12). Les valeurs par défaut sont la
 * palette actuelle ; un thème « fort contraste » est fourni (NFR11). Le singleton
 * {@link #current} est rechargé à chaque ouverture de l'éditeur par
 * {@link ThemeLoader} — modifiable sans recompiler.
 */
public record Theme(int canvasBackground, int grid, int gridMajor,
                    int nodeBackground, int nodeBorder, int nodeSelected,
                    int ghost, int error, int warning, int execWire) {

    public static final Theme DEFAULT = new Theme(
            0xFF1A1B1E, 0xFF242629, 0xFF2E3135,
            0xFF2B2D31, 0xFF3A3D42, 0xFF7AA2F7,
            0xFFC74A5B, 0xFFF7768E, 0xFFE0AF68, 0xFFE6E6E6);

    /** Fort contraste : fonds francs, bordures blanches, états saturés. */
    public static final Theme HIGH_CONTRAST = new Theme(
            0xFF000000, 0xFF2E2E2E, 0xFF4A4A4A,
            0xFF101014, 0xFFFFFFFF, 0xFF00AAFF,
            0xFFFF4455, 0xFFFF3355, 0xFFFFB300, 0xFFFFFFFF);

    private static volatile Theme current = DEFAULT;

    public static Theme current() {
        return current;
    }

    public static void set(Theme theme) {
        current = theme;
    }
}
