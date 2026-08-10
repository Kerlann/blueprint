package fr.blueprint.core.net;

import fr.blueprint.core.graph.screen.ScreenText;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que la trame des modifications d'écran supporte sans perdre le reste.
 *
 * <p>Les trois pannes vérifiées ici partagent une forme : le serveur produisait une trame
 * qu'il ne pouvait pas encoder, ou le client recevait une trame qu'il ne pouvait pas
 * décoder — et comme les modifications d'un tick voyagent <b>groupées</b>, l'échec ne
 * coûtait pas une modification mais <b>toutes celles du joueur</b>. Rien ne le signalait :
 * le graphe continuait, et l'écran restait sur des valeurs périmées.
 *
 * <p>Elles se testent au niveau du tampon, comme le ferait un client d'une autre version,
 * parce que c'est le seul endroit où l'on peut fabriquer ce qu'aucun code de ce dépôt
 * n'émet — une nature inconnue.
 */
class ScreenUpdateWireTest {

    private static ScreenUpdate texte(String element, String value) {
        return ScreenUpdate.text("achat", element, ScreenText.literal(value));
    }

    @Test
    void uneTrameOrdinaireTraverseIntacte() {
        var sent = new BlueprintPayloads.ScreenUpdates(7,
                List.of(texte("or", "100"), ScreenUpdate.progress("achat", "xp", 0.5),
                        ScreenUpdate.lines("achat", "liste", List.of("épée", "arc"))));

        ByteBuf buffer = Unpooled.buffer();
        BlueprintPayloads.ScreenUpdates.CODEC.encode(buffer, sent);
        var received = BlueprintPayloads.ScreenUpdates.CODEC.decode(buffer);

        assertEquals(sent, received, "le fil ne déforme rien de ce qui est valide");
        assertEquals(7, received.instance());
    }

    /**
     * La panne principale : {@code idMapper} faisait {@code Kind.values()[ordinal]} sans
     * borne. La liste des natures a déjà grandi deux fois (cinq en 10.4, douze en 10.13),
     * donc un client d'une version antérieure à un serveur est un cas <b>attendu</b>, pas
     * une hypothèse d'attaque — et il devait rester utilisable.
     */
    @Test
    void uneNatureInconnueNeCouteQueSonEntree() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 3);            // instance
        ByteBufCodecs.VAR_INT.encode(buffer, 3);            // trois modifications
        write(buffer, "achat", "or", ScreenUpdate.Kind.TEXT.ordinal(), "100", false, 0);
        write(buffer, "achat", "futur", 99, "venu d'une version ultérieure", true, 4);
        write(buffer, "achat", "niveau", ScreenUpdate.Kind.TEXT.ordinal(), "3", false, 0);

        var received = BlueprintPayloads.ScreenUpdates.CODEC.decode(buffer);

        assertEquals(2, received.updates().size(),
                "l'entrée illisible est jetée, la trame est gardée");
        assertEquals("or", received.updates().get(0).element());
        assertEquals("niveau", received.updates().get(1).element(),
                "ce qui SUIT l'entrée illisible arrive : le tampon a été lu jusqu'au bout");
        assertFalse(buffer.isReadable(), "et rien ne reste dans le tampon");
    }

    /** Un ordinal négatif est le même cas, par l'autre bord. */
    @Test
    void unOrdinalNegatifNeCouteQueSonEntree() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 0);
        ByteBufCodecs.VAR_INT.encode(buffer, 2);
        write(buffer, "achat", "abime", -1, "", false, 0);
        write(buffer, "achat", "or", ScreenUpdate.Kind.TEXT.ordinal(), "100", false, 0);

        assertEquals(1, BlueprintPayloads.ScreenUpdates.CODEC.decode(buffer).updates().size());
    }

    /**
     * Un élément sans nom fait lever le constructeur du modèle. Le décodeur ne doit pas
     * transformer ce refus en perte de trame : même traitement qu'une nature inconnue.
     */
    @Test
    void unElementSansNomNeCouteQueSonEntree() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 0);
        ByteBufCodecs.VAR_INT.encode(buffer, 2);
        write(buffer, "achat", "", ScreenUpdate.Kind.TEXT.ordinal(), "100", false, 0);
        write(buffer, "achat", "or", ScreenUpdate.Kind.TEXT.ordinal(), "100", false, 0);

        var received = BlueprintPayloads.ScreenUpdates.CODEC.decode(buffer);

        assertEquals(1, received.updates().size());
        assertEquals("or", received.updates().get(0).element());
    }

    /** Un nombre d'entrées annoncé au-delà du plafond reste un refus net : c'est du remplissage. */
    @Test
    void uneTrameAuDelaDuPlafondEstRefusee() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 0);
        ByteBufCodecs.VAR_INT.encode(buffer, BlueprintPayloads.MAX_UPDATES + 1);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> BlueprintPayloads.ScreenUpdates.CODEC.decode(buffer));
    }

    /**
     * La file d'un joueur est indexée par écran+élément+nature : 128 éléments et douze
     * natures la portent bien au-delà du plafond d'une trame, sans qu'aucun abus soit en
     * cause. Elle se découpe, et rien ne se perd — tronquer aurait laissé des éléments sur
     * une valeur périmée que le graphe croit avoir changée.
     */
    @Test
    void uneFileTropLongueSeDecoupeSansRienPerdre() {
        List<ScreenUpdate> many = new ArrayList<>();
        for (int i = 0; i < BlueprintPayloads.MAX_UPDATES * 2 + 3; i++) {
            many.add(texte("element" + i, String.valueOf(i)));
        }

        var batches = BlueprintPayloads.ScreenUpdates.batches(5, many);

        assertEquals(3, batches.size());
        List<ScreenUpdate> rebuilt = new ArrayList<>();
        for (var batch : batches) {
            assertTrue(batch.updates().size() <= BlueprintPayloads.MAX_UPDATES,
                    "chaque trame tient sous le plafond");
            assertEquals(5, batch.instance(), "le numéro d'instance suit chaque trame");
            rebuilt.addAll(roundTripUpdates(batch));
        }
        assertEquals(many, rebuilt, "toutes les modifications arrivent, dans l'ordre");
    }

    @Test
    void uneFileOrdinaireResteUneSeuleTrame() {
        assertEquals(1, BlueprintPayloads.ScreenUpdates.batches(0,
                List.of(texte("or", "1"), texte("niveau", "2"))).size());
    }

    /**
     * Un texte plus long que le fil n'autorise ne se refusait pas : il levait à
     * l'<b>encodage</b>, côté serveur, dans la boucle de fin de tick. Or rien ne bornait
     * sa longueur — {@code GraphGuard} plafonne les textes du graphe à 4 096 caractères,
     * et {@code string/concat} en fabrique de bien plus longs à l'exécution.
     */
    @Test
    void unTexteInterminableEstCoupePlutotQueFatal() {
        var update = texte("or", "x".repeat(5_000));

        assertEquals(ScreenUpdate.MAX_TEXT, update.text().length(),
                "le modèle refuse déjà de porter l'inencodable");

        var received = roundTripUpdates(new BlueprintPayloads.ScreenUpdates(0, List.of(update)));
        assertEquals(1, received.size());
        assertEquals(ScreenUpdate.MAX_TEXT, received.get(0).text().length());
    }

    /**
     * Cent lignes de vingt caractères dépassent le plafond sans qu'aucune ligne ne soit
     * longue. La coupe se fait donc par lignes entières : une dernière ligne tronquée ne
     * se distingue en rien d'une vraie, et « Épée de diam » se lit comme une donnée.
     */
    @Test
    void uneListeInterminableGardeDesLignesEntieres() {
        List<String> lignes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lignes.add("objet numéro " + i + " ....");
        }

        var update = ScreenUpdate.lines("achat", "liste", lignes);

        assertTrue(update.text().length() <= ScreenUpdate.MAX_TEXT);
        var kept = update.linesValue();
        assertTrue(kept.size() < lignes.size(), "toutes ne tenaient pas");
        assertEquals(lignes.subList(0, kept.size()), kept,
                "celles qui restent sont intactes, dans l'ordre");
        assertEquals(kept.size(), (int) update.number(),
                "le nombre annoncé est celui des lignes RETENUES");
    }

    /**
     * Le seul cas où couper au caractère vaut mieux que jeter : rendre la liste vide
     * ferait croire qu'il n'y a rien à lire.
     */
    @Test
    void unePremiereLigneEnormeEstCoupeePlutotQueJetee() {
        var update = ScreenUpdate.lines("achat", "liste", List.of("y".repeat(3_000), "arc"));

        assertEquals(1, update.linesValue().size());
        assertEquals(ScreenUpdate.MAX_TEXT, update.text().length());
    }

    @Test
    void uneListeQuiTientNeChangePas() {
        var lignes = List.of("épée", "arc", "potion");
        var update = ScreenUpdate.lines("achat", "liste", lignes);

        assertEquals(lignes, update.linesValue());
        assertEquals(3, (int) update.number());
    }

    private static List<ScreenUpdate> roundTripUpdates(BlueprintPayloads.ScreenUpdates sent) {
        ByteBuf buffer = Unpooled.buffer();
        BlueprintPayloads.ScreenUpdates.CODEC.encode(buffer, sent);
        return BlueprintPayloads.ScreenUpdates.CODEC.decode(buffer).updates();
    }

    /** Écrit une entrée à la main : c'est le seul moyen de forger une nature inconnue. */
    private static void write(ByteBuf buffer, String screen, String element, int ordinal,
                             String text, boolean flag, double number) {
        ByteBufCodecs.stringUtf8(BlueprintPayloads.MAX_NAME).encode(buffer, screen);
        ByteBufCodecs.stringUtf8(BlueprintPayloads.MAX_NAME).encode(buffer, element);
        ByteBufCodecs.VAR_INT.encode(buffer, ordinal);
        ByteBufCodecs.stringUtf8(BlueprintPayloads.MAX_TEXT).encode(buffer, text);
        ByteBufCodecs.BOOL.encode(buffer, flag);
        ByteBufCodecs.DOUBLE.encode(buffer, number);
    }
}
