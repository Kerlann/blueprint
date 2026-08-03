package fr.blueprint.api;

/**
 * Version de la surface publique de {@code fr.blueprint.api} (story 8.5) — la seule
 * chose qu'un mod tiers doit consulter pour savoir s'il parle la même langue que
 * Blueprint.
 *
 * <p><b>Ce que la version promet.</b> Elle suit le semver, et c'est la <i>surface</i>
 * qu'elle décrit, pas le contenu du mod :
 * <ul>
 *   <li><b>majeure</b> — une rupture : une méthode disparaît, une signature change,
 *       un comportement documenté cesse d'être vrai ;</li>
 *   <li><b>mineure</b> — un ajout compatible : un nœud, un type de pin, une méthode
 *       nouvelle. Un mod compilé contre une mineure antérieure continue de marcher ;</li>
 *   <li><b>corrective</b> — ni l'un ni l'autre : correction sans effet sur la surface.</li>
 * </ul>
 *
 * <p><b>Politique de dépréciation.</b> Rien ne disparaît sans avoir été
 * {@code @Deprecated} pendant au moins une version mineure, avec le remplacement nommé
 * dans le javadoc. Une suppression n'arrive qu'à une majeure. Ce contrat est vérifié
 * mécaniquement : {@code ApiSurfaceTest} compare la surface publique à une signature de
 * référence commitée, et la construction échoue sur toute rupture non intentionnelle.
 *
 * <pre>{@code
 * if (!BlueprintApi.isCompatibleWith(1, 0)) {
 *     LOGGER.warn("Blueprint {} : mon intégration attend 1.0", BlueprintApi.API_VERSION);
 *     return;
 * }
 * }</pre>
 */
public final class BlueprintApi {

    /** Version de la surface publique, au format {@code majeure.mineure.corrective}. */
    public static final String API_VERSION = "1.0.0";

    public static final int MAJOR = 1;
    public static final int MINOR = 0;
    public static final int PATCH = 0;

    private BlueprintApi() {
    }

    /**
     * Vrai si cette API satisfait le besoin exprimé : même majeure, et mineure au moins
     * égale. C'est le test qu'un mod tiers fait au démarrage plutôt que de découvrir
     * l'incompatibilité par un {@code NoSuchMethodError} au milieu d'une partie.
     */
    public static boolean isCompatibleWith(int requiredMajor, int requiredMinor) {
        return MAJOR == requiredMajor && MINOR >= requiredMinor;
    }
}
