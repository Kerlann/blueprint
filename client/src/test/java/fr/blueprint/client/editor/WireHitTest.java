package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Géométrie du clic sur un fil (story 5.12). Le clic doit viser la courbe
 * <b>effectivement dessinée</b> : c'est le seul endroit où rendu et interaction
 * peuvent diverger sans que rien ne le signale.
 */
class WireHitTest {

    private static final double TOLERANCE = 5;

    @Test
    void surLesDeuxExtremitesLaDistanceEstNulle() {
        // La polyligne part de (x0,y0) : l'échantillonnage commence à i=1.
        assertTrue(WireLayer.distanceToCurve(0, 0, 1, 200, 60, -1, 0, 0) < 1);
        assertTrue(WireLayer.distanceToCurve(0, 0, 1, 200, 60, -1, 200, 60) < 1);
    }

    /**
     * Un fil horizontal : le milieu de la courbe est à mi-hauteur, pas sur la corde —
     * mais pour deux points de même ordonnée les deux coïncident.
     */
    @Test
    void unFilDroitSAttrapeEnSonMilieu() {
        assertTrue(WireLayer.distanceToCurve(0, 50, 1, 300, 50, -1, 150, 50) < TOLERANCE);
        assertTrue(WireLayer.distanceToCurve(0, 50, 1, 300, 50, -1, 150, 80) > TOLERANCE,
                "trente pixels plus bas, on ne l'attrape plus");
    }

    /**
     * Le point qui compte : au milieu d'un fil qui monte, la courbe passe VISIBLEMENT
     * loin de la corde (tangentes horizontales). Cliquer sur la corde ne doit pas
     * sélectionner le fil, sinon on attrape des liens là où on ne voit rien.
     */
    @Test
    void laCourbeNestPasLaCorde() {
        // Départ (0,0), arrivée (100,200) : très vertical, tangentes horizontales.
        double surCourbe = WireLayer.distanceToCurve(0, 0, 1, 100, 200, -1, 50, 100);
        assertTrue(surCourbe < TOLERANCE, "le milieu de la courbe est bien à mi-chemin");

        double loin = WireLayer.distanceToCurve(0, 0, 1, 100, 200, -1, 5, 190);
        assertTrue(loin > TOLERANCE,
                "sous le départ, là où la courbe ne passe pas : " + loin);
    }

    /** Un point franchement à l'écart n'attrape rien, quelle que soit la forme. */
    @Test
    void loinDeToutOnNattrapeRien() {
        assertTrue(WireLayer.distanceToCurve(0, 0, 1, 200, 0, -1, 100, 400) > 300);
    }

    /**
     * Les points de contrôle sont bornés : sur un fil très long, la courbe ne part pas
     * à l'infini vers la droite — sans quoi le clic viserait une courbe imaginaire.
     */
    @Test
    void laTensionEstBornee() {
        assertTrue(WireLayer.controlOffset(0, 10_000) <= 160);
        assertTrue(WireLayer.controlOffset(0, 2) >= 16, "et jamais nulle sur un fil court");
    }
}
