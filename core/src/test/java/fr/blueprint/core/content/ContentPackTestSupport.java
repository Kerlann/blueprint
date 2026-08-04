package fr.blueprint.core.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Un PNG minimal <b>valide</b> de la taille demandée, partagé par les tests de l'épic 11.
 *
 * <p>On n'écrit pas une image : on écrit ce que le lecteur d'en-tête lit — signature, puis
 * un bloc {@code IHDR} dont les deux entiers sont la largeur et la hauteur. C'est
 * exactement le contrat testé, et cela évite de faire dépendre une dizaine de tests d'un
 * encodeur d'images.
 */
final class ContentPackTestSupport {

    private ContentPackTestSupport() {
    }

    static Path png(Path directory, String name, int width, int height) throws IOException {
        byte[] bytes = new byte[32];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(signature, 0, bytes, 0, 8);
        bytes[12] = 'I';
        bytes[13] = 'H';
        bytes[14] = 'D';
        bytes[15] = 'R';
        write(bytes, 16, width);
        write(bytes, 20, height);
        Path file = directory.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    private static void write(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
