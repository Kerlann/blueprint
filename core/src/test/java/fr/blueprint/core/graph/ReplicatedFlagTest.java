package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le drapeau {@code @replicated} : posable, réversible, et refusé quand il ne pourrait pas
 * tenir sa promesse (épic 21, story 21.1).
 *
 * <p>Le drapeau existait depuis longtemps dans le modèle, le NBT, la grammaire BScript et
 * quatre exemples livrés — <b>sans qu'aucun code ne le lise</b>. C'est le motif de panne que
 * ce projet a déjà payé avec {@code event/signal} : une surface qui se déclare, se persiste,
 * et ne fait rien. Cette story ne le met pas encore sur le fil ; elle le rend <b>posable et
 * refusable là où l'auteur le voit</b>, ce qui est la seule façon de ne pas construire le
 * réseau au-dessus d'un drapeau que personne ne peut mettre.
 */
class ReplicatedFlagTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private Blueprint bp;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "repl"));
    }

    private boolean apply(EditOperation op) {
        return op.apply(bp, LOOKUP, GraphLimits.DEFAULT).applied();
    }

    /**
     * Sans valeur par défaut : ce test ne parle que du drapeau, et un défaut demanderait une
     * valeur par type — dont une {@code list<vec3>}, que {@code LiteralValue} refuse de
     * construire à partir d'un nombre. Le modèle accepte l'absence de défaut.
     */
    private void addVariable(String name, fr.blueprint.api.pin.PinType type, VarScope scope) {
        assertTrue(apply(new EditOperation.AddVariable(
                new Variable(name, type, null, scope, false))));
    }

    // ------------------------------------------------------------ poser et retirer

    @Test
    void leDrapeauSePoseEtSeRetire() {
        addVariable("or", PinTypes.DOUBLE, VarScope.PLAYER);
        assertFalse(bp.variables().get("or").replicated(), "faux par défaut");

        assertTrue(apply(new EditOperation.SetReplicated("or", true)));
        assertTrue(bp.variables().get("or").replicated());

        assertTrue(apply(new EditOperation.SetReplicated("or", false)));
        assertFalse(bp.variables().get("or").replicated());
    }

    /** Réversible comme toute opération d'édition : c'est ce qui la rend annulable. */
    @Test
    void lOperationRendSonInverse() {
        addVariable("or", PinTypes.DOUBLE, VarScope.PLAYER);
        var result = new EditOperation.SetReplicated("or", true)
                .apply(bp, LOOKUP, GraphLimits.DEFAULT);

        assertTrue(result.applied());
        assertNotNull(result.inverse());
        result.inverse().apply(bp, LOOKUP, GraphLimits.DEFAULT);
        assertFalse(bp.variables().get("or").replicated(), "l'inverse repose le drapeau à faux");
    }

    @Test
    void poserLeDrapeauFaitAvancerLaRevision() {
        addVariable("or", PinTypes.DOUBLE, VarScope.PLAYER);
        int before = bp.revision();

        apply(new EditOperation.SetReplicated("or", true));

        assertTrue(bp.revision() > before, "sans quoi le verrou optimiste ne verrait rien");
    }

    @Test
    void uneVariableInconnueEstRefusee() {
        assertFalse(apply(new EditOperation.SetReplicated("fantome", true)));
    }

    // ------------------------------------------------------------------- les refus

    /**
     * {@code LOCAL} ne survit pas à l'exécution qui l'écrit : il n'y a rien à répliquer, et
     * l'accepter aurait produit un drapeau qui ne fait jamais rien — exactement ce que cette
     * story existe pour supprimer.
     */
    @Test
    void uneVariableLocaleNeSeRepliquePas() {
        addVariable("compteur", PinTypes.DOUBLE, VarScope.LOCAL);

        assertFalse(apply(new EditOperation.SetReplicated("compteur", true)));
        assertFalse(bp.variables().get("compteur").replicated(), "et rien n'est écrit");
    }

    /** Une référence vivante n'a pas de codec réseau : elle ne peut pas voyager. */
    @Test
    void unTypeSansCodecReseauNeSeRepliquePas() {
        addVariable("cible", PinTypes.PLAYER, VarScope.PLAYER);
        assertFalse(PinTypes.PLAYER.hasStreamCodec(), "prémisse du test");

        assertFalse(apply(new EditOperation.SetReplicated("cible", true)));
    }

    @Test
    void unJokerNeSeRepliquePas() {
        addVariable("quelconque", PinTypes.ANY, VarScope.WORLD);

        assertFalse(apply(new EditOperation.SetReplicated("quelconque", true)));
    }

    /**
     * Une liste ne voyage que si son contenu voyage. C'est {@code ParameterizedPinType} qui
     * le dit, et le refus doit s'appuyer sur lui plutôt que sur une liste de types en dur.
     */
    @Test
    void uneListeSuitLeSortDeSonContenu() {
        addVariable("chemin", PinTypes.listOf(PinTypes.VEC3), VarScope.WORLD);
        addVariable("cibles", PinTypes.listOf(PinTypes.PLAYER), VarScope.WORLD);

        assertTrue(apply(new EditOperation.SetReplicated("chemin", true)),
                "une liste de vecteurs voyage");
        assertFalse(apply(new EditOperation.SetReplicated("cibles", true)),
                "une liste de joueurs, non");
    }

    /**
     * <b>Retirer</b> le drapeau reste permis même sur une variable que les contrôles
     * refuseraient. Sans cela, un graphe venu de BScript avec un {@code @replicated} illégal
     * serait impossible à réparer depuis l'éditeur : le bouton refuserait dans les deux sens.
     */
    @Test
    void retirerLeDrapeauResteToujoursPermis() {
        // Forgé directement, comme le ferait un .bp écrit à la main.
        bp.putVariable(new Variable("cible", PinTypes.PLAYER, null, VarScope.PLAYER, true));

        assertTrue(apply(new EditOperation.SetReplicated("cible", false)));
        assertFalse(bp.variables().get("cible").replicated());
    }

    // -------------------------------------------------------------- le validateur

    /**
     * Le validateur refait les deux contrôles, et ce n'est pas une redondance : un retypage
     * postérieur peut invalider un drapeau posé légitimement, et un graphe écrit en BScript
     * n'est jamais passé par une opération d'édition.
     */
    @Test
    void leValidateurVoitUnDrapeauDevenuInvalideParRetypage() {
        addVariable("valeur", PinTypes.DOUBLE, VarScope.PLAYER);
        assertTrue(apply(new EditOperation.SetReplicated("valeur", true)));

        // Le retypage préserve le drapeau — délibérément : l'effacer en silence serait
        // pire. C'est donc au validateur de le dire.
        bp.putVariable(new Variable("valeur", PinTypes.PLAYER, null, VarScope.PLAYER, true));

        assertTrue(GraphValidator.validate(bp, LOOKUP, GraphLimits.DEFAULT).diagnostics().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.REPLICATED_TYPE_NOT_SENDABLE),
                "le drapeau survit au retypage, le diagnostic doit le rattraper");
    }

    @Test
    void leValidateurVoitUnDrapeauSurUnePorteeLocale() {
        bp.putVariable(new Variable("compteur", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.LOCAL, true));

        assertTrue(GraphValidator.validate(bp, LOOKUP, GraphLimits.DEFAULT).diagnostics().stream()
                .anyMatch(d -> d.code() == DiagnosticCode.REPLICATED_SCOPE_LOCAL));
    }

    @Test
    void unDrapeauLegitimeNeProduitAucunDiagnostic() {
        addVariable("or", PinTypes.DOUBLE, VarScope.PLAYER);
        apply(new EditOperation.SetReplicated("or", true));

        assertTrue(GraphValidator.validate(bp, LOOKUP, GraphLimits.DEFAULT).diagnostics().stream()
                .noneMatch(d -> d.code() == DiagnosticCode.REPLICATED_SCOPE_LOCAL
                        || d.code() == DiagnosticCode.REPLICATED_TYPE_NOT_SENDABLE));
    }

    /** Une variable NON répliquée n'est jamais jugée sur sa capacité à voyager. */
    @Test
    void uneVariableNonRepliqueeEchappeAuxDeuxControles() {
        addVariable("cible", PinTypes.PLAYER, VarScope.PLAYER);
        addVariable("compteur", PinTypes.DOUBLE, VarScope.LOCAL);

        assertTrue(GraphValidator.validate(bp, LOOKUP, GraphLimits.DEFAULT).diagnostics().stream()
                        .noneMatch(d -> d.code() == DiagnosticCode.REPLICATED_SCOPE_LOCAL
                                || d.code() == DiagnosticCode.REPLICATED_TYPE_NOT_SENDABLE),
                "le contrôle ne juge que ce qui se déclare répliqué");
    }

    /**
     * L'opération et le validateur posent la MÊME question, par le même code. Deux
     * exemplaires auraient fini par diverger, et la divergence se serait vue comme un
     * drapeau que l'éditeur refuse de poser mais accepte d'afficher.
     */
    @Test
    void lOperationEtLeValidateurSAppuientSurLaMemeRegle() {
        var interdite = new Variable("cible", PinTypes.PLAYER, null, VarScope.PLAYER, true);
        var permise = new Variable("or", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.PLAYER, true);

        assertNotNull(GraphValidator.checkReplicable(interdite));
        assertNull(GraphValidator.checkReplicable(permise));
        assertEquals(DiagnosticCode.REPLICATED_TYPE_NOT_SENDABLE,
                GraphValidator.checkReplicable(interdite).code());
    }
}
