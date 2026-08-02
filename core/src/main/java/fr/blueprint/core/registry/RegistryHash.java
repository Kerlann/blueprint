package fr.blueprint.core.registry;

import fr.blueprint.api.node.NodeType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Hash stable du registre de nœuds (FR35) : au login, le serveur envoie ce hash ;
 * si le client a le même, la synchro des descripteurs est évitée. Indépendant de
 * l'ordre d'enregistrement (tri par identifiant), identique entre deux chargements
 * identiques, différent au moindre changement de surface.
 */
public final class RegistryHash {

    private RegistryHash() {
    }

    public static String of(NodeRegistryImpl registry) {
        List<String> canonicals = new ArrayList<>();
        for (NodeType type : registry.all()) {
            canonicals.add(NodeDescriptor.of(type).canonical());
        }
        canonicals.sort(String::compareTo);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String canonical : canonicals) {
                digest.update(canonical.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
