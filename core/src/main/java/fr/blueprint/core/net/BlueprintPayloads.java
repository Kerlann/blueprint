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

    /**
     * S2C au join : les <b>bornes que ce serveur applique</b> (story 10.6, AC2).
     *
     * <p>Sans elles, l'éditeur validait avec les défauts du modèle. Sur un serveur aux
     * quotas resserrés, l'auteur dessinait donc un écran que rien ne signalait, et
     * découvrait le refus à l'enregistrement — après le travail, pas pendant. C'est
     * exactement ce que « diagnostic à l'édition » veut éviter.
     *
     * <p>Un paquet à part plutôt qu'un champ ajouté à un autre : ces bornes sont ce que
     * le serveur DÉCLARE une fois, comme son registre de nœuds, et non une réponse à une
     * demande. Les glisser dans la liste des blueprints les aurait liées à un moment qui
     * n'a rien à voir.
     */
    public record ServerLimits(int maxNodes, int maxScreens, int maxElementsPerScreen)
            implements CustomPacketPayload {
        public static final Type<ServerLimits> TYPE = new Type<>(id("server_limits"));
        public static final StreamCodec<ByteBuf, ServerLimits> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ServerLimits::maxNodes,
                ByteBufCodecs.VAR_INT, ServerLimits::maxScreens,
                ByteBufCodecs.VAR_INT, ServerLimits::maxElementsPerScreen,
                ServerLimits::new);

        public fr.blueprint.core.graph.GraphLimits toGraphLimits() {
            return new fr.blueprint.core.graph.GraphLimits(
                    Math.max(1, maxNodes), Math.max(1, maxScreens),
                    Math.max(1, maxElementsPerScreen));
        }

        @Override
        public Type<ServerLimits> type() {
            return TYPE;
        }
    }

    /** C2S : la liste des blueprints du serveur (alimente le navigateur et les suggestions). */
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

    /**
     * S2C : ouvre le navigateur (F6) chez le joueur. C'est ce que rend
     * {@code /blueprint edit} sans argument — une seule commande décide, côté serveur,
     * et le client obéit.
     */
    public record OpenBrowser() implements CustomPacketPayload {
        public static final Type<OpenBrowser> TYPE = new Type<>(id("bp_open_browser"));
        public static final StreamCodec<ByteBuf, OpenBrowser> CODEC =
                StreamCodec.unit(new OpenBrowser());

        @Override
        public Type<OpenBrowser> type() {
            return TYPE;
        }
    }

    /**
     * C2S : une touche de Blueprint a été pressée chez ce joueur (story 11.4).
     *
     * <p>Un <b>emplacement</b> de 1 à 8, jamais un code de touche. Le serveur n'a que
     * faire de savoir quelle touche physique le joueur a choisie, et le graphe encore
     * moins : un code varie d'un clavier à l'autre, si bien qu'un script écrit en AZERTY
     * cesserait de fonctionner en QWERTY. Le numéro est en outre le seul des deux qui ne
     * puisse pas servir à deviner ce que le joueur tape.
     */
    public record KeyPress(int slot) implements CustomPacketPayload {
        public static final Type<KeyPress> TYPE = new Type<>(id("bp_key"));
        public static final StreamCodec<ByteBuf, KeyPress> CODEC =
                ByteBufCodecs.VAR_INT.map(KeyPress::new, KeyPress::slot);

        @Override
        public Type<KeyPress> type() {
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

    /** C2S : création puis ouverture immédiate (/blueprint create). */
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
    public record ScreenClose(String screen) implements CustomPacketPayload {
        public static final Type<ScreenClose> TYPE = new Type<>(id("screen_close"));
        public static final StreamCodec<ByteBuf, ScreenClose> CODEC =
                ByteBufCodecs.stringUtf8(MAX_NAME).map(ScreenClose::new, ScreenClose::screen);

        /** Ferme le modal — le cas d'origine, et celui d'Échap. */
        public static ScreenClose modal() {
            return new ScreenClose("");
        }

        @Override
        public Type<ScreenClose> type() {
            return TYPE;
        }
    }

    /** Ferme TOUS les HUD : la garde de sécurité, quand le joueur n'a plus de recours. */
    public static final String ALL_HUDS = "*";

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
     * C2S : une interaction <b>portant une valeur</b> — la ligne cliquée d'une liste,
     * le texte d'une saisie, la position d'un curseur, l'état d'une case (story 10.8).
     *
     * <p>Un second paquet plutôt qu'un {@link ScreenInteraction} élargi : le clic simple
     * est de loin le plus fréquent, et lui faire porter trois champs vides à chaque
     * bouton pressé serait payer pour ce qui ne sert presque jamais.
     *
     * <p>Le serveur ne croit <b>rien</b> de ce paquet non plus (FR52). Il rejoue le type
     * de l'élément, le filtre et la longueur du champ, et les bornes du curseur : un
     * client modifié qui envoie dix mille caractères dans un champ limité à seize est
     * <b>ignoré</b>, jamais tronqué — tronquer laisserait croire à une saisie que le
     * joueur n'a pas faite.
     */
    public record ScreenValue(Identifier blueprint, String screen, String element,
                              int instance, int index, String text, double number,
                              boolean flag) implements CustomPacketPayload {
        public static final Type<ScreenValue> TYPE = new Type<>(id("screen_value"));
        public static final StreamCodec<ByteBuf, ScreenValue> CODEC =
                StreamCodec.of(ScreenValue::write, ScreenValue::read);

        // À la main plutôt que par composite : celui-ci s'arrête à huit champs, et il en
        // faut huit ici — la limite serait atteinte au premier ajout.
        private static void write(ByteBuf buffer, ScreenValue value) {
            Identifier.STREAM_CODEC.encode(buffer, value.blueprint());
            ByteBufCodecs.stringUtf8(MAX_NAME).encode(buffer, value.screen());
            ByteBufCodecs.stringUtf8(MAX_NAME).encode(buffer, value.element());
            ByteBufCodecs.VAR_INT.encode(buffer, value.instance());
            ByteBufCodecs.VAR_INT.encode(buffer, value.index());
            ByteBufCodecs.stringUtf8(MAX_VALUE_TEXT).encode(buffer, value.text());
            ByteBufCodecs.DOUBLE.encode(buffer, value.number());
            ByteBufCodecs.BOOL.encode(buffer, value.flag());
        }

        private static ScreenValue read(ByteBuf buffer) {
            return new ScreenValue(
                    Identifier.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.stringUtf8(MAX_NAME).decode(buffer),
                    ByteBufCodecs.stringUtf8(MAX_NAME).decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.stringUtf8(MAX_VALUE_TEXT).decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer));
        }

        @Override
        public Type<ScreenValue> type() {
            return TYPE;
        }
    }

    /**
     * Longueur maximale d'un texte de saisie <b>sur le fil</b>. Le décodeur s'arrête là ;
     * le filtre de l'élément, plus strict, s'applique ensuite côté serveur.
     */
    public static final int MAX_VALUE_TEXT =
            fr.blueprint.core.graph.screen.ElementOptions.MAX_INPUT_LENGTH;

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

        public static final StreamCodec<ByteBuf, ScreenUpdates> CODEC =
                StreamCodec.of(ScreenUpdates::write, ScreenUpdates::read);

        /**
         * Découpe des modifications en trames encodables. Le plafond n'est pas décoratif :
         * la file d'un joueur est indexée par {@code écran+élément+nature}, donc bornée par
         * 128 éléments × 12 natures × plusieurs HUD — très au-dessus de
         * {@link #MAX_UPDATES}. Dépasser ne produisait pas un envoi partiel mais une
         * <b>exception d'encodage</b>, qui emportait la trame entière du joueur.
         *
         * <p>Découper plutôt que tronquer : une modification jetée en silence laisse un
         * élément affichant une valeur périmée, et le graphe n'a aucun moyen de l'apprendre.
         */
        public static java.util.List<ScreenUpdates> batches(int instance,
                java.util.List<fr.blueprint.core.graph.screen.ScreenUpdate> updates) {
            if (updates.size() <= MAX_UPDATES) {
                return java.util.List.of(new ScreenUpdates(instance, updates));
            }
            java.util.List<ScreenUpdates> out = new java.util.ArrayList<>();
            for (int from = 0; from < updates.size(); from += MAX_UPDATES) {
                out.add(new ScreenUpdates(instance,
                        updates.subList(from, Math.min(from + MAX_UPDATES, updates.size()))));
            }
            return out;
        }

        private static void write(ByteBuf buffer, ScreenUpdates value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.instance());
            ByteBufCodecs.VAR_INT.encode(buffer, value.updates().size());
            for (var update : value.updates()) {
                ByteBufCodecs.stringUtf8(MAX_NAME).encode(buffer, update.screen());
                ByteBufCodecs.stringUtf8(MAX_NAME).encode(buffer, update.element());
                // L'ordinal voyage, jamais un nom de classe.
                ByteBufCodecs.VAR_INT.encode(buffer, update.kind().ordinal());
                ByteBufCodecs.stringUtf8(MAX_TEXT).encode(buffer, update.text());
                ByteBufCodecs.BOOL.encode(buffer, update.flag());
                ByteBufCodecs.DOUBLE.encode(buffer, update.number());
            }
        }

        /**
         * À la main plutôt que par {@code composite} + {@code idMapper}, pour une raison
         * qui n'est pas de style : {@code idMapper} faisait {@code Kind.values()[i]} sans
         * borne. Un ordinal hors plage — serveur d'une version où la liste a grandi, ce
         * qui est déjà arrivé deux fois (5 natures en 10.4, 12 en 10.13) — levait une
         * {@code ArrayIndexOutOfBoundsException} qui emportait <b>toute la trame</b>, y
         * compris les modifications que ce client comprenait parfaitement.
         *
         * <p>Une entrée illisible est donc <b>lue jusqu'au bout puis jetée</b> : lire est
         * obligatoire pour retrouver le début de la suivante, jeter est ce qui permet aux
         * autres d'arriver. C'est le seul point du protocole où un client plus ancien
         * qu'un serveur reste utilisable.
         */
        private static ScreenUpdates read(ByteBuf buffer) {
            int instance = ByteBufCodecs.VAR_INT.decode(buffer);
            int count = ByteBufCodecs.VAR_INT.decode(buffer);
            if (count < 0 || count > MAX_UPDATES) {
                throw new io.netty.handler.codec.DecoderException(
                        count + " modifications dans une trame, maximum " + MAX_UPDATES);
            }
            var updates =
                    new java.util.ArrayList<fr.blueprint.core.graph.screen.ScreenUpdate>(count);
            var kinds = fr.blueprint.core.graph.screen.ScreenUpdate.Kind.values();
            for (int i = 0; i < count; i++) {
                String screen = ByteBufCodecs.stringUtf8(MAX_NAME).decode(buffer);
                String element = ByteBufCodecs.stringUtf8(MAX_NAME).decode(buffer);
                int ordinal = ByteBufCodecs.VAR_INT.decode(buffer);
                String text = ByteBufCodecs.stringUtf8(MAX_TEXT).decode(buffer);
                boolean flag = ByteBufCodecs.BOOL.decode(buffer);
                double number = ByteBufCodecs.DOUBLE.decode(buffer);
                // Un élément sans nom ferait lever le constructeur du modèle : même
                // traitement qu'une nature inconnue, l'entrée est jetée, pas la trame.
                if (ordinal < 0 || ordinal >= kinds.length || element.isBlank()) {
                    continue;
                }
                updates.add(new fr.blueprint.core.graph.screen.ScreenUpdate(
                        screen, element, kinds[ordinal], text, flag, number));
            }
            return new ScreenUpdates(instance, java.util.List.copyOf(updates));
        }

        @Override
        public Type<ScreenUpdates> type() {
            return TYPE;
        }
    }

    /**
     * Plafond de modifications par trame. Au-delà, ce n'est plus un rafraîchissement,
     * c'est une inondation — mais la file d'un joueur peut légitimement le dépasser, et
     * l'envoi se découpe alors en plusieurs trames ({@link ScreenUpdates#batches}).
     */
    public static final int MAX_UPDATES = 256;

    /**
     * Un libellé d'élément peut être une phrase, pas une page. Le nombre est celui du
     * modèle : c'est lui qui garantit qu'aucune modification inencodable n'est produite,
     * et le codec ne fait que le refléter.
     */
    public static final int MAX_TEXT = fr.blueprint.core.graph.screen.ScreenUpdate.MAX_TEXT;

    /**
     * S2C : les valeurs des variables {@code @replicated} qui ont changé (épic 21).
     *
     * <p><b>Descendant seulement.</b> Il n'existe pas de paquet montant équivalent, et il
     * n'en existera pas : FR52 — « le serveur ne fait jamais confiance à ce qu'un client
     * déclare ». La réplication donne au client de quoi <i>afficher</i>, jamais de quoi
     * décider.
     *
     * <p>Les valeurs voyagent dans le <b>même format étiqueté</b> que la sauvegarde du monde
     * ({@link fr.blueprint.core.vm.VarValueNbt}), sur un {@code ByteBuf} nu. Ce n'est pas un
     * raccourci : l'ensemble des types qui voyagent est ainsi <b>exactement</b> celui des
     * types qui survivent à un redémarrage. Un {@code StreamCodec} par type aurait été plus
     * direct et aurait ouvert la porte à une variable qui arrive chez un client sans pouvoir
     * être sauvegardée — un état que rien n'aurait rattrapé.
     *
     * <p>La clé est {@code (blueprint, nom)} et <b>la portée ne voyage pas</b>. C'est le client
     * qui l'impose : une liaison d'écran ne nomme qu'une variable, et c'est la déclaration du
     * blueprint qui dit sa portée — déclaration que le client n'a pas. Envoyer la portée l'aurait
     * obligé à deviner laquelle une liaison visait, et un nom de variable est unique dans un
     * blueprint, donc {@code (blueprint, nom)} désigne exactement une valeur.
     *
     * <p>Conséquence : une variable {@code WORLD} déclarée répliquée par deux blueprints part
     * <b>deux fois</b>, une par blueprint. C'est un peu de redondance sur le fil contre la
     * garantie que les écrans de chacun trouvent leur valeur — et le cas est rare, parce que
     * partager un nom entre graphes se déclare des deux côtés.
     *
     * @param values les changements, jamais plus de {@link #MAX_VALUES}
     */
    public record VarValues(java.util.List<VarValue> values) implements CustomPacketPayload {
        public static final Type<VarValues> TYPE = new Type<>(id("var_values"));

        public static final StreamCodec<ByteBuf, VarValues> CODEC =
                VarValue.CODEC.apply(ByteBufCodecs.list(MAX_VALUES))
                        .map(VarValues::new, VarValues::values);

        @Override
        public Type<VarValues> type() {
            return TYPE;
        }
    }

    /**
     * Une valeur répliquée sur le fil : le blueprint sous lequel le client la range, son nom, et
     * sa valeur encodée.
     *
     * <p>{@code value} vide signifie <b>effacée</b> — la variable n'a plus de valeur chez le
     * serveur. Un tag vide plutôt qu'un {@code Optional} : le format étiqueté rend déjà
     * {@code null} sur tout ce qu'il ne comprend pas, donc le client a un seul cas à traiter
     * au lieu de deux qui se ressemblent.
     */
    public record VarValue(Identifier blueprint, String name,
                           net.minecraft.nbt.CompoundTag value) {

        public static final StreamCodec<ByteBuf, VarValue> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, VarValue::blueprint,
                ByteBufCodecs.stringUtf8(MAX_NAME), VarValue::name,
                VALUE_CODEC, VarValue::value,
                VarValue::new);
    }

    /**
     * Plafond de valeurs par trame, et de valeurs répliquées par graphe.
     *
     * <p>Aligné sur {@code NetLimits.maxReplicatedVariables} : un graphe ne peut pas déclarer
     * plus de valeurs répliquées qu'une trame ne peut en porter, donc un tick qui les change
     * toutes tient dans un seul envoi. Laisser les deux nombres diverger aurait demandé un
     * découpage en trames, c'est-à-dire du code pour un cas que le garde réseau refuse déjà.
     */
    public static final int MAX_VALUES = NetLimits.DEFAULT.maxReplicatedVariables();

    /**
     * Le tag d'une valeur, borné à 8 Kio décompressés.
     *
     * <p>Une valeur de variable est une chaîne, un nombre, un vecteur, ou une collection de
     * ceux-là. Huit kibioctets laissent passer une liste de plusieurs centaines d'entrées et
     * refusent ce qui n'a rien à faire sur un canal poussé à chaque changement. Le plafond
     * de 64 Ko par joueur (NFR14) borne le total ; celui-ci borne une valeur, et c'est lui
     * qui protège le décodeur.
     */
    private static final StreamCodec<ByteBuf, net.minecraft.nbt.CompoundTag> VALUE_CODEC =
            ByteBufCodecs.compoundTagCodec(() -> net.minecraft.nbt.NbtAccounter.create(8 * 1024));

    /**
     * S2C : le client liste ou recharge ses packs (10.5).
     *
     * <p>Un paquet plutôt qu'une commande cliente, parce que les packs vivent sur le
     * <b>disque du joueur</b> et dans ses textures : le serveur n'a rien à en dire, il ne
     * fait que transmettre la demande. C'est ce qui permet de n'avoir qu'une seule racine
     * {@code /blueprint} — une racine cliente du même nom aurait avalé tout l'arbre serveur,
     * puisque Fabric ne renvoie au serveur que les commandes <i>inconnues</i>, pas celles
     * dont seul le sous-chemin manque.
     *
     * <p>Un booléen et non un ordinal d'énumération : il y a exactement deux gestes, et un
     * ordinal aurait été la troisième occasion de décoder une valeur hors plage.
     *
     * @param reload vrai pour relire le disque, faux pour seulement énumérer
     */
    public record PacksAction(boolean reload) implements CustomPacketPayload {
        public static final Type<PacksAction> TYPE = new Type<>(id("packs_action"));
        public static final StreamCodec<ByteBuf, PacksAction> CODEC =
                ByteBufCodecs.BOOL.map(PacksAction::new, PacksAction::reload);

        @Override
        public Type<PacksAction> type() {
            return TYPE;
        }
    }

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
