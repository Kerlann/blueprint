package fr.blueprint.core.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquets de la synchro registre (story 6.2). Enregistrés dans {@code BlueprintMod}
 * (types) et gérés côté client dans {@code client/net} — toute entrée est bornée.
 */
public final class BlueprintPayloads {

    private BlueprintPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /** S2C au join : le hash du registre serveur — le client demande s'il diverge (FR35). */
    public record RegistryHash(String hash) implements CustomPacketPayload {
        public static final Type<RegistryHash> TYPE = new Type<>(id("registry_hash"));
        public static final StreamCodec<ByteBuf, RegistryHash> CODEC =
                ByteBufCodecs.STRING_UTF8.map(RegistryHash::new, RegistryHash::hash);

        @Override
        public Type<RegistryHash> type() {
            return TYPE;
        }
    }

    /** C2S : le client demande les descripteurs complets. */
    public record RegistryRequest(int nonce) implements CustomPacketPayload {
        public static final Type<RegistryRequest> TYPE = new Type<>(id("registry_request"));
        public static final StreamCodec<ByteBuf, RegistryRequest> CODEC =
                ByteBufCodecs.VAR_INT.map(RegistryRequest::new, RegistryRequest::nonce);

        @Override
        public Type<RegistryRequest> type() {
            return TYPE;
        }
    }

    /** C2S : la liste des blueprints du serveur (alimente /blueprint-edit en multi). */
    public record ListRequest(int nonce) implements CustomPacketPayload {
        public static final Type<ListRequest> TYPE = new Type<>(id("bp_list_request"));
        public static final StreamCodec<ByteBuf, ListRequest> CODEC =
                ByteBufCodecs.VAR_INT.map(ListRequest::new, ListRequest::nonce);

        @Override
        public Type<ListRequest> type() {
            return TYPE;
        }
    }

    /** S2C : identifiants des blueprints visibles, et si le joueur peut les modifier. */
    public record ListData(java.util.List<Identifier> ids, boolean writable)
            implements CustomPacketPayload {
        public static final Type<ListData> TYPE = new Type<>(id("bp_list"));
        public static final StreamCodec<ByteBuf, ListData> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LIST)), ListData::ids,
                ByteBufCodecs.BOOL, ListData::writable,
                ListData::new);

        @Override
        public Type<ListData> type() {
            return TYPE;
        }
    }

    /**
     * C2S : les fichiers importables du serveur. Le navigateur (F6) en a besoin —
     * sans eux, importer demandait de connaître par cœur le nom d'un fichier posé
     * dans un dossier qu'on ne voit pas depuis le jeu.
     */
    public record FileListRequest() implements CustomPacketPayload {
        public static final Type<FileListRequest> TYPE = new Type<>(id("bp_files_request"));
        public static final StreamCodec<ByteBuf, FileListRequest> CODEC =
                StreamCodec.unit(new FileListRequest());

        @Override
        public Type<FileListRequest> type() {
            return TYPE;
        }
    }

    /** S2C : les noms de fichiers {@code .bp} présents, sans leur extension. */
    public record FileList(java.util.List<String> files) implements CustomPacketPayload {
        public static final Type<FileList> TYPE = new Type<>(id("bp_files"));
        public static final StreamCodec<ByteBuf, FileList> CODEC =
                ByteBufCodecs.stringUtf8(MAX_NAME).apply(ByteBufCodecs.list(MAX_LIST))
                        .map(FileList::new, FileList::files);

        @Override
        public Type<FileList> type() {
            return TYPE;
        }
    }

    /**
     * C2S : importer un fichier puis l'ouvrir. Le nom n'est <b>pas</b> cru : le serveur
     * refuse tout ce qui n'est pas un nom de fichier de son propre dossier.
     */
    public record ImportRequest(String file) implements CustomPacketPayload {
        public static final Type<ImportRequest> TYPE = new Type<>(id("bp_import_request"));
        public static final StreamCodec<ByteBuf, ImportRequest> CODEC =
                ByteBufCodecs.stringUtf8(MAX_NAME).map(ImportRequest::new, ImportRequest::file);

        @Override
        public Type<ImportRequest> type() {
            return TYPE;
        }
    }

    /** C2S : demande d'ouverture d'un blueprint pour édition. */
    public record OpenRequest(Identifier blueprint) implements CustomPacketPayload {
        public static final Type<OpenRequest> TYPE = new Type<>(id("bp_open_request"));
        public static final StreamCodec<ByteBuf, OpenRequest> CODEC =
                Identifier.STREAM_CODEC.map(OpenRequest::new, OpenRequest::blueprint);

        @Override
        public Type<OpenRequest> type() {
            return TYPE;
        }
    }

    /** C2S : création puis ouverture immédiate (/blueprint-edit create). */
    public record CreateRequest(Identifier blueprint) implements CustomPacketPayload {
        public static final Type<CreateRequest> TYPE = new Type<>(id("bp_create_request"));
        public static final StreamCodec<ByteBuf, CreateRequest> CODEC =
                Identifier.STREAM_CODEC.map(CreateRequest::new, CreateRequest::blueprint);

        @Override
        public Type<CreateRequest> type() {
            return TYPE;
        }
    }

    /**
     * S2C : le graphe demandé (ou renvoyé après un refus — resynchro ciblée).
     * {@code revision} est le verrou optimiste : c'est lui que le client renverra.
     */
    public record GraphData(Identifier blueprint, int revision, boolean writable, byte[] data)
            implements CustomPacketPayload {
        public static final Type<GraphData> TYPE = new Type<>(id("bp_graph"));
        public static final StreamCodec<ByteBuf, GraphData> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, GraphData::blueprint,
                ByteBufCodecs.VAR_INT, GraphData::revision,
                ByteBufCodecs.BOOL, GraphData::writable,
                ByteBufCodecs.byteArray(MAX_GRAPH_BYTES), GraphData::data,
                GraphData::new);

        @Override
        public Type<GraphData> type() {
            return TYPE;
        }
    }

    /** C2S : enregistrement sous verrou optimiste — {@code baseRevision} = révision ouverte. */
    public record SaveRequest(Identifier blueprint, int baseRevision, byte[] data)
            implements CustomPacketPayload {
        public static final Type<SaveRequest> TYPE = new Type<>(id("bp_save_request"));
        public static final StreamCodec<ByteBuf, SaveRequest> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SaveRequest::blueprint,
                ByteBufCodecs.VAR_INT, SaveRequest::baseRevision,
                ByteBufCodecs.byteArray(MAX_GRAPH_BYTES), SaveRequest::data,
                SaveRequest::new);

        @Override
        public Type<SaveRequest> type() {
            return TYPE;
        }
    }

    /** Verdict d'un enregistrement. Ordinal transmis : jamais de nom de classe sur le fil. */
    public enum SaveStatus {
        SAVED, CONFLICT, UNKNOWN, DENIED, INVALID
    }

    /** S2C : verdict + révision courante du serveur (le client se recale dessus). */
    public record SaveAck(Identifier blueprint, SaveStatus status, int revision)
            implements CustomPacketPayload {
        public static final Type<SaveAck> TYPE = new Type<>(id("bp_save_ack"));
        public static final StreamCodec<ByteBuf, SaveAck> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SaveAck::blueprint,
                ByteBufCodecs.VAR_INT.map(SaveAck::statusOf, s -> s.ordinal()), SaveAck::status,
                ByteBufCodecs.VAR_INT, SaveAck::revision,
                SaveAck::new);

        /** Un ordinal inconnu (client plus récent, paquet forgé) vaut INVALID. */
        private static SaveStatus statusOf(int ordinal) {
            SaveStatus[] all = SaveStatus.values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SaveStatus.INVALID;
        }

        @Override
        public Type<SaveAck> type() {
            return TYPE;
        }
    }

    /** C2S : bouton Tester — active le blueprint côté serveur après enregistrement. */
    public record SetEnabled(Identifier blueprint, boolean enabled) implements CustomPacketPayload {
        public static final Type<SetEnabled> TYPE = new Type<>(id("bp_set_enabled"));
        public static final StreamCodec<ByteBuf, SetEnabled> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SetEnabled::blueprint,
                ByteBufCodecs.BOOL, SetEnabled::enabled,
                SetEnabled::new);

        @Override
        public Type<SetEnabled> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------ débogage

    /** Ce qu'un éditeur demande au débogueur (story 9.1b). */
    public enum DebugAction {
        ON, OFF, TOGGLE_BREAKPOINT, STEP, CONTINUE, CLEAR
    }

    /** C2S : une action de débogage sur un blueprint, éventuellement ciblant un nœud. */
    public record DebugCommand(Identifier blueprint, DebugAction action,
                               java.util.Optional<java.util.UUID> node)
            implements CustomPacketPayload {
        public static final Type<DebugCommand> TYPE = new Type<>(id("debug_command"));
        public static final StreamCodec<ByteBuf, DebugCommand> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, DebugCommand::blueprint,
                ByteBufCodecs.VAR_INT.map(DebugCommand::actionOf, action -> action.ordinal()),
                DebugCommand::action,
                ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC),
                DebugCommand::node,
                DebugCommand::new);

        /** Ordinal inconnu (paquet forgé) : on ne devine pas, on ignore. */
        private static DebugAction actionOf(int ordinal) {
            DebugAction[] all = DebugAction.values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : DebugAction.OFF;
        }

        @Override
        public Type<DebugCommand> type() {
            return TYPE;
        }
    }

    /** Valeurs vues sur un nœud, déjà rendues en texte par le serveur (jamais d'objet vivant). */
    public record NodeValues(java.util.UUID node, java.util.List<String> lines) {
        public static final StreamCodec<ByteBuf, NodeValues> CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, NodeValues::node,
                ByteBufCodecs.stringUtf8(160).apply(ByteBufCodecs.list(32)), NodeValues::lines,
                NodeValues::new);
    }

    /**
     * S2C : l'état du débogueur pour un blueprint — ce que l'éditeur dessine. Poussé
     * pendant que le joueur débogue, jamais autrement.
     */
    public record DebugSnapshot(Identifier blueprint, boolean debugging,
                                java.util.Optional<java.util.UUID> pausedAt,
                                java.util.List<java.util.UUID> breakpoints,
                                java.util.List<NodeValues> values)
            implements CustomPacketPayload {
        public static final Type<DebugSnapshot> TYPE = new Type<>(id("debug_snapshot"));
        public static final StreamCodec<ByteBuf, DebugSnapshot> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, DebugSnapshot::blueprint,
                ByteBufCodecs.BOOL, DebugSnapshot::debugging,
                ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC),
                DebugSnapshot::pausedAt,
                net.minecraft.core.UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DEBUG_NODES)),
                DebugSnapshot::breakpoints,
                NodeValues.CODEC.apply(ByteBufCodecs.list(MAX_DEBUG_NODES)), DebugSnapshot::values,
                DebugSnapshot::new);

        @Override
        public Type<DebugSnapshot> type() {
            return TYPE;
        }
    }

    /** Un instantané de débogage reste petit : au-delà, l'éditeur n'affiche plus rien d'utile. */
    public static final int MAX_DEBUG_NODES = 64;

    /** Bornes du fil : un paquet plus gros est rejeté par le décodeur, pas par nous. */
    public static final int MAX_GRAPH_BYTES = fr.blueprint.core.net.GraphSync.MAX_BYTES;
    public static final int MAX_LIST = 4_096;

    /**
     * S2C : ouvre un écran chez le joueur (story 10.3). Porte la <b>description</b> de
     * l'écran, pas des ordres de dessin — le client la compose lui-même et réagit seul
     * au redimensionnement de la fenêtre.
     *
     * <p>{@code blueprint} et {@code screen} identifient l'écran : c'est cette identité
     * que le client renverra sur un clic (10.4), et c'est elle que le serveur vérifiera
     * plutôt que de croire ce que le client annonce (FR52).
     */
    public record ScreenOpen(Identifier blueprint, String screen, int instance, byte[] data)
            implements CustomPacketPayload {
        public static final Type<ScreenOpen> TYPE = new Type<>(id("screen_open"));
        public static final StreamCodec<ByteBuf, ScreenOpen> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, ScreenOpen::blueprint,
                ByteBufCodecs.stringUtf8(MAX_NAME), ScreenOpen::screen,
                ByteBufCodecs.VAR_INT, ScreenOpen::instance,
                ByteBufCodecs.byteArray(fr.blueprint.core.net.ScreenSync.MAX_BYTES),
                ScreenOpen::data,
                ScreenOpen::new);

        @Override
        public Type<ScreenOpen> type() {
            return TYPE;
        }
    }

    /**
     * Ferme l'écran ouvert, <b>dans les deux sens</b> : le serveur peut le refermer
     * (blueprint désactivé, joueur qui change de dimension), et le client prévient
     * quand le joueur appuie sur Échap.
     *
     * <p>Sans le sens client→serveur, le serveur croirait un écran encore ouvert et
     * continuerait d'accepter des clics dessus longtemps après sa fermeture.
     */
    public record ScreenClose() implements CustomPacketPayload {
        public static final Type<ScreenClose> TYPE = new Type<>(id("screen_close"));
        public static final StreamCodec<ByteBuf, ScreenClose> CODEC =
                StreamCodec.unit(new ScreenClose());

        @Override
        public Type<ScreenClose> type() {
            return TYPE;
        }
    }

    /** Un nom d'écran ou d'élément reste court : le fil n'a pas à porter un roman. */
    public static final int MAX_NAME = 256;

    /**
     * C2S : « j'ai cliqué cet élément ». Le serveur ne croit <b>rien</b> de ce paquet
     * (FR52) : il vérifie que cet écran est bien celui qu'il a envoyé à ce joueur, que
     * l'élément existe, qu'il est visible et activé — et il borne la cadence.
     *
     * <p>{@code instance} évite qu'un clic parti juste avant une fermeture ne soit
     * appliqué à l'écran suivant.
     */
    public record ScreenInteraction(Identifier blueprint, String screen, String element,
                                    int instance) implements CustomPacketPayload {
        public static final Type<ScreenInteraction> TYPE = new Type<>(id("screen_click"));
        public static final StreamCodec<ByteBuf, ScreenInteraction> CODEC =
                StreamCodec.composite(
                        Identifier.STREAM_CODEC, ScreenInteraction::blueprint,
                        ByteBufCodecs.stringUtf8(MAX_NAME), ScreenInteraction::screen,
                        ByteBufCodecs.stringUtf8(MAX_NAME), ScreenInteraction::element,
                        ByteBufCodecs.VAR_INT, ScreenInteraction::instance,
                        ScreenInteraction::new);

        @Override
        public Type<ScreenInteraction> type() {
            return TYPE;
        }
    }

    /**
     * S2C : les modifications d'un tick, <b>en une seule trame</b> (AC3b). Un graphe qui
     * rafraîchit l'or, le niveau et une barre produit un paquet, pas trois.
     *
     * <p>{@code instance} est jeté par le client s'il ne correspond pas à l'écran qu'il
     * affiche : ce qui arrive en retard ne doit pas s'appliquer à autre chose.
     */
    public record ScreenUpdates(int instance,
                                java.util.List<fr.blueprint.core.graph.screen.ScreenUpdate> updates)
            implements CustomPacketPayload {
        public static final Type<ScreenUpdates> TYPE = new Type<>(id("screen_updates"));

        /** Une modification sur le fil. L'ordinal voyage, jamais un nom de classe. */
        public static final StreamCodec<ByteBuf, fr.blueprint.core.graph.screen.ScreenUpdate>
                UPDATE_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_NAME),
                fr.blueprint.core.graph.screen.ScreenUpdate::element,
                ByteBufCodecs.idMapper(
                        i -> fr.blueprint.core.graph.screen.ScreenUpdate.Kind.values()[i],
                        fr.blueprint.core.graph.screen.ScreenUpdate.Kind::ordinal),
                fr.blueprint.core.graph.screen.ScreenUpdate::kind,
                ByteBufCodecs.stringUtf8(MAX_TEXT),
                fr.blueprint.core.graph.screen.ScreenUpdate::text,
                ByteBufCodecs.BOOL, fr.blueprint.core.graph.screen.ScreenUpdate::flag,
                ByteBufCodecs.DOUBLE, fr.blueprint.core.graph.screen.ScreenUpdate::number,
                fr.blueprint.core.graph.screen.ScreenUpdate::new);

        public static final StreamCodec<ByteBuf, ScreenUpdates> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ScreenUpdates::instance,
                UPDATE_CODEC.apply(ByteBufCodecs.list(MAX_UPDATES)), ScreenUpdates::updates,
                ScreenUpdates::new);

        @Override
        public Type<ScreenUpdates> type() {
            return TYPE;
        }
    }

    /**
     * Plafond de modifications par trame. Un écran plafonne à 128 éléments et cinq
     * natures : au-delà, ce n'est plus un rafraîchissement, c'est une inondation.
     */
    public static final int MAX_UPDATES = 256;

    /** Un libellé d'élément peut être une phrase, pas une page. */
    public static final int MAX_TEXT = 1_024;

    /** S2C : un fragment du flux de descripteurs compressé. */
    public record DescriptorChunk(int index, int total, byte[] data)
            implements CustomPacketPayload {
        public static final Type<DescriptorChunk> TYPE = new Type<>(id("descriptor_chunk"));
        public static final StreamCodec<ByteBuf, DescriptorChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DescriptorChunk::index,
                ByteBufCodecs.VAR_INT, DescriptorChunk::total,
                ByteBufCodecs.BYTE_ARRAY, DescriptorChunk::data,
                DescriptorChunk::new);

        @Override
        public Type<DescriptorChunk> type() {
            return TYPE;
        }
    }
}
