package fr.blueprint.core.i18n;

import fr.blueprint.api.pin.PinShape;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR11 : « la palette des types de pins reste lisible en deutéranopie et protanopie ;
 * les pins portent aussi un code de forme ».
 *
 * <p>La règle vérifiée ici est la seule qui tienne : <b>deux types que le daltonisme
 * rapproche doivent se distinguer par la forme</b>. Une palette « jolie » ne prouve
 * rien ; deux pins indiscernables en jeu se câblent de travers.
 *
 * <p>Les simulations sont les approximations linéaires classiques (Viénot/Color Oracle)
 * appliquées au sRGB. Approximatives, mais suffisantes pour une garde de non-régression :
 * elles capturent le rapprochement rouge/vert qui est tout le sujet.
 */
class ColorBlindPaletteTest {

    /** En deçà de cette distance RGB, deux couleurs simulées sont « la même ». */
    private static final double CONFUSABLE = 60.0;

    private static final List<PinType> PALETTE = List.of(
            PinTypes.EXEC, PinTypes.BOOL, PinTypes.INT, PinTypes.LONG, PinTypes.DOUBLE,
            PinTypes.STRING, PinTypes.VEC3, PinTypes.BLOCKPOS, PinTypes.DIRECTION,
            PinTypes.ITEMSTACK, PinTypes.PLAYER, PinTypes.ENTITY, PinTypes.BLOCKSTATE,
            PinTypes.RESOURCE_LOCATION, PinTypes.TEXT, PinTypes.ANY);

    @Test
    void everyPinTypeCarriesAShape() {
        for (PinType type : PALETTE) {
            assertNotNull(type.shape(), type.id() + " sans forme");
        }
    }

    @Test
    void confusableColoursNeverShareAShape() {
        assertDistinguishable("deutéranopie", ColorBlindPaletteTest::deuteranope);
        assertDistinguishable("protanopie", ColorBlindPaletteTest::protanope);
        // Vision normale : la même règle, plus lâche — deux types identiques de couleur
        // ET de forme seraient indiscernables pour tout le monde.
        assertDistinguishable("vision normale", rgb -> rgb);
    }

    private static void assertDistinguishable(String vision, java.util.function.IntUnaryOperator sim) {
        List<String> clashes = new ArrayList<>();
        for (int i = 0; i < PALETTE.size(); i++) {
            for (int j = i + 1; j < PALETTE.size(); j++) {
                PinType a = PALETTE.get(i);
                PinType b = PALETTE.get(j);
                double distance = distance(sim.applyAsInt(a.color()), sim.applyAsInt(b.color()));
                if (distance < CONFUSABLE && a.shape() == b.shape()) {
                    clashes.add(String.format("%s et %s (distance %.0f, même forme %s)",
                            a.id(), b.id(), distance, a.shape()));
                }
            }
        }
        assertTrue(clashes.isEmpty(), "en " + vision + ", indiscernables :\n  "
                + String.join("\n  ", clashes));
    }

    /** Les pins d'exécution se distinguent des données par la forme, pas par la teinte. */
    @Test
    void execIsShapedApartFromData() {
        for (PinType type : PALETTE) {
            if (type == PinTypes.EXEC) {
                continue;
            }
            assertTrue(PinTypes.EXEC.shape() != type.shape()
                            || distance(PinTypes.EXEC.color(), type.color()) >= CONFUSABLE,
                    "exec se confond avec " + type.id());
        }
    }

    // ------------------------------------------------------------------ simulation

    private static int deuteranope(int argb) {
        double r = red(argb);
        double g = green(argb);
        double b = blue(argb);
        return pack(0.625 * r + 0.375 * g,
                0.700 * r + 0.300 * g,
                0.300 * g + 0.700 * b);
    }

    private static int protanope(int argb) {
        double r = red(argb);
        double g = green(argb);
        double b = blue(argb);
        return pack(0.567 * r + 0.433 * g,
                0.558 * r + 0.442 * g,
                0.242 * g + 0.758 * b);
    }

    private static double distance(int a, int b) {
        double dr = red(a) - red(b);
        double dg = green(a) - green(b);
        double db = blue(a) - blue(b);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static double red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static double green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static double blue(int argb) {
        return argb & 0xFF;
    }

    private static int pack(double r, double g, double b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }
}
