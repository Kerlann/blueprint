package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le client peint une variable sans aller-retour (épic 21, story 21.5).
 *
 * <p>C'est le point où la promesse de l'épic devient visible : une barre de mana se rafraîchit
 * comme une barre de vie, au tick client, depuis une valeur déjà là. Ce que le catalogue fermé
 * de {@code ClientValue} ne pouvait pas donner — il ne contient que quatorze valeurs vanilla.
 *
 * <p>Le javadoc de {@code ScreenBindings} disait « le client ne calcule jamais les liaisons de
 * variables : il ne connaît pas les variables, et ne doit pas ». La phrase reste vraie au sens
 * où elle a été écrite : le client ne connaît ni portée, ni type déclaré, ni valeur par défaut.
 * Il connaît des valeurs nommées que le serveur lui a <b>envoyées</b>, et il appelle le
 * <b>même</b> code de rendu pour en tirer les mêmes pixels.
 */
class ReplicatedVarsTest {

    private static final Identifier BOUTIQUE =
            Identifier.fromNamespaceAndPath("test", "boutique");
    private static final Identifier BANQUE = Identifier.fromNamespaceAndPath("test", "banque");

    @BeforeEach
    void setUp() {
        // Table statique partagée par la JVM de test : vidée avant chaque cas, sans quoi ce
        // fichier dépendrait de l'ordre d'exécution.
        ReplicatedVars.clear();
    }

    /** Un HUD dont le libellé et la barre suivent une variable répliquée. */
    private static Screen hud() {
        return new Screen("fiche", true, List.of(
                ScreenElement.of("solde", ElementKind.LABEL, 0, 0, 120, 10)
                        .withBinding(ElementBinding.text("or", "%s pièces")),
                ScreenElement.of("mana", ElementKind.PROGRESS, 0, 0, 120, 6)
                        .withBinding(ElementBinding.progress("mana", 0, 100))),
                Map.of());
    }

    // ------------------------------------------------------------------- le cache

    @Test
    void uneValeurRangeeSeRelit() {
        ReplicatedVars.put(BOUTIQUE, "or", 100.0);

        assertEquals(100.0, ReplicatedVars.get(BOUTIQUE, "or"));
    }

    @Test
    void unNomInconnuRendNull() {
        assertNull(ReplicatedVars.get(BOUTIQUE, "or"));
    }

    /**
     * Deux blueprints déclarant le même nom sont deux valeurs. C'est ce qui a fait passer la clé
     * du fil de la portée au blueprint : les confondre aurait affiché le solde de l'un dans
     * l'écran de l'autre.
     */
    @Test
    void deuxBlueprintsNeSeMelangentPas() {
        ReplicatedVars.put(BOUTIQUE, "score", 1.0);
        ReplicatedVars.put(BANQUE, "score", 2.0);

        assertEquals(1.0, ReplicatedVars.get(BOUTIQUE, "score"));
        assertEquals(2.0, ReplicatedVars.get(BANQUE, "score"));
    }

    /** Une valeur nulle <b>efface</b> : le lecteur ne distingue pas « absente » de « nulle ». */
    @Test
    void unNulEffaceAuLieuDeRangerUnNul() {
        ReplicatedVars.put(BOUTIQUE, "or", 100.0);
        ReplicatedVars.put(BOUTIQUE, "or", null);

        assertNull(ReplicatedVars.get(BOUTIQUE, "or"));
        assertEquals(0, ReplicatedVars.trackedBlueprints(),
                "et le casier vidé ne reste pas derrière");
    }

    @Test
    void laDeconnexionOublieTout() {
        ReplicatedVars.put(BOUTIQUE, "or", 100.0);
        ReplicatedVars.put(BANQUE, "taux", 1.5);

        ReplicatedVars.clear();

        assertEquals(0, ReplicatedVars.trackedBlueprints(),
                "ces valeurs étaient celles de CE serveur");
    }

    // --------------------------------------------- le même code de rendu que le serveur

    /**
     * Le test qui porte la story : le client produit les modifications d'une liaison de
     * variable, par {@code ScreenBindings} — le code du serveur.
     */
    @Test
    void leClientResoutUneLiaisonDeVariableDepuisLeCache() {
        ReplicatedVars.put(BOUTIQUE, "or", 250.0);
        ReplicatedVars.put(BOUTIQUE, "mana", 75.0);

        var updates = fr.blueprint.core.net.ScreenBindings.updates(hud(),
                ReplicatedVars.lookup(BOUTIQUE), ElementBinding.Source.VARIABLE);

        assertEquals(2, updates.size());
        assertEquals("250 pièces", updates.stream()
                .filter(u -> u.element().equals("solde")).findFirst().orElseThrow().text());
        assertEquals(0.75, updates.stream()
                .filter(u -> u.element().equals("mana")).findFirst().orElseThrow().number(),
                "75 sur une plage 0..100 remplit la barre aux trois quarts");
    }

    /**
     * Et il donne le <b>même</b> résultat que le serveur sur les mêmes valeurs. C'est la
     * garantie que ce code partagé existe pour tenir : deux implémentations auraient divergé
     * sur la première valeur limite, et la divergence se serait vue au pixel.
     */
    @Test
    void leClientEtLeServeurDonnentLeMemeResultat() {
        ReplicatedVars.put(BOUTIQUE, "or", 250.0);
        ReplicatedVars.put(BOUTIQUE, "mana", 75.0);

        var cote_client = fr.blueprint.core.net.ScreenBindings.updates(hud(),
                ReplicatedVars.lookup(BOUTIQUE), ElementBinding.Source.VARIABLE);
        var cote_serveur = fr.blueprint.core.net.ScreenBindings.updates(hud(),
                name -> "or".equals(name) ? 250.0 : 75.0);

        assertEquals(cote_serveur, cote_client);
    }

    /** Une valeur jamais reçue laisse la liaison sur son repli, sans lever. */
    @Test
    void uneValeurAbsenteNeLevePas() {
        var updates = fr.blueprint.core.net.ScreenBindings.updates(hud(),
                ReplicatedVars.lookup(BOUTIQUE), ElementBinding.Source.VARIABLE);

        assertEquals(2, updates.size(), "les liaisons produisent leur repli, pas une exception");
    }

    // -------------------------------------------------------------------- via le HUD

    @Test
    void leHudRafraichitSesLiaisonsDeVariable() {
        HudView view = new HudView();
        view.show(BOUTIQUE, hud());
        ReplicatedVars.put(BOUTIQUE, "or", 250.0);

        assertTrue(view.refreshVariableBindings() > 0);
        assertEquals("250 pièces", view.get("fiche").element("solde").text().value());
    }

    /** Ce qui n'a pas bougé ne se réapplique pas : la discipline de {@code lastClient}. */
    @Test
    void uneValeurInchangeeNeReappliqueRien() {
        HudView view = new HudView();
        view.show(BOUTIQUE, hud());
        ReplicatedVars.put(BOUTIQUE, "or", 250.0);

        assertTrue(view.refreshVariableBindings() > 0);
        assertEquals(0, view.refreshVariableBindings(),
                "le chemin d'application recrée un écran : l'appeler pour rien alloue pour rien");
    }

    /**
     * Un HUD venu d'un autre blueprint ne lit pas les valeurs de celui-ci. Sans le
     * propriétaire, le HUD aurait pioché dans n'importe quel casier portant le bon nom.
     *
     * <p>Il produit bien des modifications — une liaison sans valeur rend son <b>repli</b>,
     * ce n'est pas un cas d'erreur — mais elles ne portent pas la valeur du voisin.
     */
    @Test
    void unHudNeLitQueLesValeursDeSonBlueprint() {
        HudView view = new HudView();
        view.show(BANQUE, hud());
        ReplicatedVars.put(BOUTIQUE, "or", 250.0);

        view.refreshVariableBindings();

        assertNotEquals("250 pièces", view.get("fiche").element("solde").text().value(),
                "l'or de la boutique n'est pas celui de la banque");
    }
}
