package fr.blueprint.core.content;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Les dimensions d'un PNG, lues dans son <b>en-tête</b> — sans le décoder.
 *
 * <p>Un PNG commence par une signature de huit octets, puis un bloc {@code IHDR} dont les
 * deux premiers entiers sont la largeur et la hauteur : vingt-quatre octets suffisent. Ce
 * détail n'est pas de la micro-optimisation, c'est ce qui permet de refuser une image de
 * vingt mille pixels de côté <b>avant</b> qu'elle n'atteigne le décodeur et la carte
 * graphique. Refuser après l'avoir décodée serait refuser trop tard.
 *
 * <p>Écrit pour les packs d'images (10.5), et repris tel quel par le contenu déclaré
 * (11.2). Les deux ont exactement le même besoin : dire non à une image avant de
 * l'allouer. Une seconde copie aurait divergé au premier correctif.
 */
public final class PngHeader {

    private PngHeader() {
    }

    /** Largeur et hauteur, ou {@code null} si le fichier n'est pas un PNG lisible. */
    public static int @Nullable [] size(Path file) {
        byte[] header = new byte[24];
        try (var in = Files.newInputStream(file)) {
            if (in.readNBytes(header, 0, 24) < 24) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return null;
            }
        }
        if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
            return null;
        }
        return new int[]{intAt(header, 16), intAt(header, 20)};
    }

    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
