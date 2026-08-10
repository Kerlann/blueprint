package fr.blueprint.core.vm;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.VarScope;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quelles écritures comptent comme un changement à répliquer (épic 21, story 21.3).
 *
 * <p>Deux propriétés portent tout le reste. D'abord le <b>coût nul quand rien n'est
 * répliqué</b> : c'est ce qui répond à l'objection de la story 10.7 contre l'instrumentation
 * du magasin — l'objection portait sur un coût imposé à toute exécution, et il n'y en a pas.
 * Ensuite la <b>bonne unité d'isolation par portée</b> : {@code WORLD} est partagée entre
 * blueprints, {@code GRAPH} ne l'est pas, et confondre les deux enverrait la valeur d'un
 * graphe pour celle d'un autre.
 */
class ReplicatedNamesTest {

    private static final Identifier BOUTIQUE =
            Identifier.fromNamespaceAndPath("test", "boutique");
    private static final Identifier BANQUE = Identifier.fromNamespaceAndPath("test", "banque");

    /** Par les opérations d'édition : {@code putVariable} n'est pas public, à dessein. */
    private static Blueprint withVariable(Identifier id, String name, VarScope scope,
                                          boolean replicated) {
        Blueprint bp = new Blueprint(id);
        var limits = fr.blueprint.core.graph.GraphLimits.DEFAULT;
        fr.blueprint.core.graph.NodeTypeLookup lookup = typeId -> null;
        assertTrue(new fr.blueprint.core.graph.EditOperation.AddVariable(
                new fr.blueprint.core.graph.Variable(name, PinTypes.DOUBLE,
                        LiteralValue.of(PinTypes.DOUBLE, 0.0), scope, false))
                .apply(bp, lookup, limits).applied());
        if (replicated) {
            // Une portée LOCAL est refusée par l'opération, à juste titre : le drapeau y est
            // dépourvu de sens. Le forger par un retypage serait tordu — on passe par le
            // décodage NBT, qui est la voie d'un .bp écrit à la main.
            boolean posed = new fr.blueprint.core.graph.EditOperation.SetReplicated(name, true)
                    .apply(bp, lookup, limits).applied();
            if (!posed) {
                return forgeReplicated(id, name, scope);
            }
        }
        return bp;
    }

    /**
     * Un graphe portant un drapeau que l'éditeur refuserait — ce qu'un {@code .bp} écrit à la
     * main peut produire. {@code ReplicatedNames} doit s'en défendre, pas le supposer absent.
     */
    private static Blueprint forgeReplicated(Identifier id, String name, VarScope scope) {
        Blueprint bp = new Blueprint(id);
        var tag = fr.blueprint.core.graph.GraphNbt.encode(bp);
        var variables = new net.minecraft.nbt.ListTag();
        var entry = new net.minecraft.nbt.CompoundTag();
        entry.putString("name", name);
        entry.putString("type", PinTypes.DOUBLE.id().toString());
        entry.putString("scope", scope.name());
        entry.putBoolean("replicated", true);
        variables.add(entry);
        tag.put("variables", variables);
        Blueprint forged = fr.blueprint.core.graph.GraphNbt.decode(tag,
                typeId -> PinTypes.DOUBLE.id().equals(typeId) ? PinTypes.DOUBLE : null);
        assertTrue(forged != null && forged.variables().containsKey(name),
                "le graphe forgé porte bien la variable");
        return forged;
    }

    private static VarOwner owner(Identifier blueprint, UUID player) {
        return new VarOwner(blueprint, player);
    }

    // ------------------------------------------------------------------- le coût nul

    @Test
    void sansAucuneDeclarationLensembleEstVideEtPartage() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "or", VarScope.PLAYER, false)));

        assertTrue(names.isEmpty());
        assertSame(ReplicatedNames.NONE, names,
                "la même instance : le cas courant ne coûte pas même une allocation");
    }

    @Test
    void unePorteeLocaleNeCompteJamais() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "tampon", VarScope.LOCAL, true)));

        assertTrue(names.isEmpty(), "elle ne survit pas à l'exécution qui l'écrit");
    }

    @Test
    void uneDeclarationSuffitAReveillerLensemble() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "or", VarScope.PLAYER, true)));

        assertFalse(names.isEmpty());
        assertTrue(names.covers(VarScope.PLAYER, BOUTIQUE, "or"));
    }

    // -------------------------------------------------------- les portées partagées

    /**
     * Le cas que le plan appelait « la subtilité à ne pas manquer ». Le blueprint A déclare
     * {@code or @world @replicated}, B écrit {@code or @world} sans le drapeau. B ne sait pas
     * qu'il faut marquer, et pourtant c'est bien la valeur que les clients regardent qu'il
     * vient de changer.
     */
    @Test
    void unePorteeMondeEstCouverteQuelQueSoitLeGrapheQuiEcrit() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "or", VarScope.WORLD, true),
                withVariable(BANQUE, "or", VarScope.WORLD, false)));

        assertTrue(names.covers(VarScope.WORLD, BOUTIQUE, "or"), "le graphe qui déclare");
        assertTrue(names.covers(VarScope.WORLD, BANQUE, "or"),
                "et celui qui écrit sans avoir déclaré : c'est la MÊME valeur");
        assertTrue(names.covers(VarScope.WORLD, null, "or"),
                "y compris sans blueprint : la portée monde n'en dépend pas");
    }

    @Test
    void unePorteeJoueurPartageeSuitLaMemeRegle() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "prenom", VarScope.PLAYER_SHARED, true)));

        assertTrue(names.covers(VarScope.PLAYER_SHARED, BANQUE, "prenom"),
                "elle est commune à tous les blueprints, par définition");
    }

    // --------------------------------------------------------- les portées isolées

    /**
     * L'inverse, et il compte autant : {@code GRAPH} et {@code PLAYER} sont isolées par
     * blueprint. Les traiter globalement aurait fait répliquer le {@code score} de A parce
     * que B en déclare un du même nom — deux variables qui n'ont rien à voir.
     */
    @Test
    void unePorteeGrapheNeCouvreQueSonBlueprint() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "score", VarScope.GRAPH, true),
                withVariable(BANQUE, "score", VarScope.GRAPH, false)));

        assertTrue(names.covers(VarScope.GRAPH, BOUTIQUE, "score"));
        assertFalse(names.covers(VarScope.GRAPH, BANQUE, "score"),
                "le même nom, un autre graphe, une autre valeur");
    }

    @Test
    void unePorteeJoueurNeCouvreQueSonBlueprint() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "or", VarScope.PLAYER, true)));

        assertTrue(names.covers(VarScope.PLAYER, BOUTIQUE, "or"));
        assertFalse(names.covers(VarScope.PLAYER, BANQUE, "or"));
        assertFalse(names.covers(VarScope.PLAYER, null, "or"),
                "sans blueprint, une portée isolée ne désigne rien");
    }

    @Test
    void unNomNonDeclareNestJamaisCouvert() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "or", VarScope.WORLD, true)));

        assertFalse(names.covers(VarScope.WORLD, BOUTIQUE, "argent"));
    }

    /** Une portée locale n'est jamais couverte, même si son nom l'est ailleurs. */
    @Test
    void unePorteeLocaleNestJamaisCouverteMemeSousUnNomReplique() {
        var names = ReplicatedNames.of(List.of(
                withVariable(BOUTIQUE, "or", VarScope.WORLD, true)));

        assertFalse(names.covers(VarScope.LOCAL, BOUTIQUE, "or"));
    }

    // ------------------------------------------------------- la désignation des marques

    /**
     * Une variable {@code WORLD} écrite par un graphe que dix joueurs viennent de déclencher
     * est <b>une</b> valeur. Si le joueur déclencheur entrait dans la désignation, elle
     * produirait dix marques pour un seul envoi.
     */
    @Test
    void uneValeurMondeEstUneSeuleMarqueQuelQueSoitLeDeclencheur() {
        var dirty = new VarDirty();

        dirty.mark(VarScope.WORLD, owner(BOUTIQUE, UUID.randomUUID()), "or");
        dirty.mark(VarScope.WORLD, owner(BANQUE, UUID.randomUUID()), "or");
        dirty.mark(VarScope.WORLD, owner(null, null), "or");

        assertEquals(1, dirty.size(), "une valeur, une marque");
    }

    @Test
    void uneValeurGrapheEstUneMarqueParBlueprint() {
        var dirty = new VarDirty();

        dirty.mark(VarScope.GRAPH, owner(BOUTIQUE, null), "score");
        dirty.mark(VarScope.GRAPH, owner(BANQUE, null), "score");

        assertEquals(2, dirty.size(), "deux graphes, deux valeurs");
    }

    @Test
    void uneValeurJoueurEstUneMarqueParJoueurEtParBlueprint() {
        var dirty = new VarDirty();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        dirty.mark(VarScope.PLAYER, owner(BOUTIQUE, alice), "or");
        dirty.mark(VarScope.PLAYER, owner(BOUTIQUE, bob), "or");
        dirty.mark(VarScope.PLAYER, owner(BANQUE, alice), "or");
        dirty.mark(VarScope.PLAYER, owner(BOUTIQUE, alice), "or");   // déjà notée

        assertEquals(3, dirty.size());
    }

    @Test
    void unePorteeJoueurPartageeIgnoreLeBlueprint() {
        var dirty = new VarDirty();
        UUID alice = UUID.randomUUID();

        dirty.mark(VarScope.PLAYER_SHARED, owner(BOUTIQUE, alice), "prenom");
        dirty.mark(VarScope.PLAYER_SHARED, owner(BANQUE, alice), "prenom");

        assertEquals(1, dirty.size(), "commune à tous les blueprints : une seule valeur");
    }

    /** Le dédoublonnage est le but : une boucle qui écrit mille fois produit une marque. */
    @Test
    void milleEcrituresDansUnTickNeFontQuUneMarque() {
        var dirty = new VarDirty();
        for (int i = 0; i < 1_000; i++) {
            assertTrue(dirty.mark(VarScope.WORLD, owner(BOUTIQUE, null), "or"));
        }

        assertEquals(1, dirty.size());
    }

    @Test
    void leCarnetSeVideEtRendCeQuIlContenait() {
        var dirty = new VarDirty();
        dirty.mark(VarScope.WORLD, owner(BOUTIQUE, null), "or");
        dirty.mark(VarScope.GRAPH, owner(BOUTIQUE, null), "score");

        var drained = dirty.drain();

        assertEquals(2, drained.size());
        assertEquals("or", drained.get(0).name(), "l'ordre d'insertion, donc reproductible");
        assertEquals("score", drained.get(1).name());
        assertTrue(dirty.isEmpty());
        assertTrue(dirty.drain().isEmpty(), "et vidé une fois suffit");
    }

    /**
     * Plein, le carnet <b>refuse la nouvelle</b> au lieu d'évincer l'ancienne. Perdre la plus
     * ancienne ferait disparaître un changement pour toujours ; refuser la plus récente la
     * laisse revenir au prochain tick où la variable change.
     */
    @Test
    void unCarnetPleinRefuseLaNouvelleEtGardeLesAnciennes() {
        var dirty = new VarDirty();
        for (int i = 0; i < VarDirty.MAX_MARKS; i++) {
            assertTrue(dirty.mark(VarScope.WORLD, owner(BOUTIQUE, null), "v" + i));
        }

        assertFalse(dirty.mark(VarScope.WORLD, owner(BOUTIQUE, null), "uneDeTrop"));
        assertEquals(VarDirty.MAX_MARKS, dirty.size());
        assertEquals("v0", dirty.drain().get(0).name(), "la plus ancienne est toujours là");
    }

    /** Un carnet plein accepte toujours une marque qu'il a DÉJÀ : ce n'est pas un ajout. */
    @Test
    void unCarnetPleinAccepteEncoreCeQuIlContientDeja() {
        var dirty = new VarDirty();
        for (int i = 0; i < VarDirty.MAX_MARKS; i++) {
            dirty.mark(VarScope.WORLD, owner(BOUTIQUE, null), "v" + i);
        }

        assertTrue(dirty.mark(VarScope.WORLD, owner(BOUTIQUE, null), "v0"));
    }
}
