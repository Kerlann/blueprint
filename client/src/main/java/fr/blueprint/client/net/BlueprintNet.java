package fr.blueprint.client.net;

import fr.blueprint.client.editor.BlueprintEditorScreen;
import fr.blueprint.client.editor.EditorSession;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.net.BlueprintPayloads;
import fr.blueprint.core.net.GraphSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Côté client de l'ouverture et de l'enregistrement réseau (story 6.3). Le même
 * chemin sert en solo et en multi : plus aucun accès direct au gestionnaire du
 * serveur depuis le fil de rendu.
 *
 * <p>L'enregistrement est <b>optimiste</b> : l'instantané part avec la révision
 * servie à l'ouverture, l'indicateur ● s'efface tout de suite, et un refus le fait
 * revenir sans jamais toucher au graphe local (AC3).
 */
public final class BlueprintNet {

    private BlueprintNet() {
    }

    /** Derniers identifiants annoncés par le serveur (suggestions et /blueprint-edit). */
    private static volatile List<Identifier> known = List.of();
    private static volatile boolean writable;
    /** Session ouverte, pour lui remettre le verdict d'un enregistrement. */
    private static @Nullable EditorSession active;
    private static @Nullable Identifier activeId;
    /**
     * Dernier instantané envoyé, gardé jusqu'à l'acquittement (QA NET-004) : un refus
     * qui arrive après la fermeture de l'éditeur ne doit pas emporter le travail —
     * il rouvre l'éditeur dessus.
     */
    private static @Nullable Blueprint pendingSnapshot;
    private static @Nullable Identifier pendingId;
    /** Une liste demandée par la commande attend son affichage dans le chat. */
    private static volatile boolean listPending;

    public static List<Identifier> known() {
        return known;
    }

    public static boolean connected() {
        return ClientPlayNetworking.canSend(BlueprintPayloads.OpenRequest.TYPE);
    }

    /**
     * Les bornes que le SERVEUR applique (story 10.6). Tant qu'il ne les a pas
     * annoncées — solo, ou serveur d'une version antérieure — ce sont celles du modèle :
     * exactement ce que l'éditeur utilisait avant qu'elles ne voyagent.
     */
    private static volatile fr.blueprint.core.graph.GraphLimits limits =
            fr.blueprint.core.graph.GraphLimits.DEFAULT;

    public static fr.blueprint.core.graph.GraphLimits limits() {
        return limits;
    }

    public static void register() {
        // Les bornes arrivent au join, avant toute ouverture d'éditeur : l'auteur voit
        // donc dès son premier geste ce que ce serveur-ci accepte, plutôt que de le
        // découvrir au refus de l'enregistrement.
        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ServerLimits.TYPE,
                (payload, context) -> limits = payload.toGraphLimits());

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) ->
                        limits = fr.blueprint.core.graph.GraphLimits.DEFAULT);

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ListData.TYPE,
                (payload, context) -> {
                    known = List.copyOf(payload.ids());
                    writable = payload.writable();
                    if (listPending) {
                        listPending = false;
                        StringBuilder ids = new StringBuilder();
                        for (Identifier id : known) {
                            if (!ids.isEmpty()) {
                                ids.append(", ");
                            }
                            ids.append(id);
                        }
                        say(Component.translatable("blueprint.editor.cmd.list",
                                ids.isEmpty() ? "—" : ids.toString()));
                    }
                });

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.OpenBrowser.TYPE,
                (payload, context) -> context.client().setScreen(
                        new fr.blueprint.client.browser.BlueprintBrowserScreen()));

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.FileList.TYPE,
                (payload, context) -> files = List.copyOf(payload.files()));

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.GraphData.TYPE,
                (payload, context) -> {
                    Blueprint graph = GraphSync.fromBytes(payload.data(),
                            typeId -> BlueprintMod.registries().pinTypes()
                                    .get(typeId).orElse(null));
                    if (graph == null) {
                        say(Component.translatable("blueprint.editor.net.unreadable",
                                payload.blueprint().toString()));
                        return;
                    }
                    graph.adoptRevision(payload.revision());
                    // Resynchro ciblée après un conflit : la session OUVERTE reçoit la
                    // nouvelle base de verrou, son graphe n'est PAS remplacé.
                    EditorSession current = openSession();
                    if (current != null && payload.blueprint().equals(activeId)) {
                        current.saveRefused(payload.revision());
                        return;
                    }
                    openEditor(payload.blueprint(), graph, payload.writable());
                });

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.SaveAck.TYPE,
                (payload, context) -> {
                    boolean accepted = payload.status() == BlueprintPayloads.SaveStatus.SAVED;
                    if (accepted && payload.blueprint().equals(pendingId)) {
                        pendingSnapshot = null;
                        pendingId = null;
                    }
                    EditorSession session = openSession();
                    if (session != null && payload.blueprint().equals(activeId)) {
                        if (accepted) {
                            session.saveAccepted(payload.revision());
                        } else {
                            session.saveRefused(payload.revision());
                            say(message(payload));
                        }
                        return;
                    }
                    if (accepted) {
                        return;
                    }
                    say(message(payload));
                    // NET-004 : l'éditeur est déjà fermé et le serveur refuse — le
                    // travail ne vit plus que dans l'instantané envoyé. On le rouvre
                    // plutôt que de le laisser disparaître.
                    Blueprint rescued = pendingSnapshot;
                    if (rescued != null && payload.blueprint().equals(pendingId)
                            && payload.status() != BlueprintPayloads.SaveStatus.DENIED) {
                        pendingSnapshot = null;
                        pendingId = null;
                        openEditor(payload.blueprint(), rescued, true)
                                .saveRefused(payload.revision());
                    }
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            known = List.of();
            files = List.of();
            writable = false;
            active = null;
            activeId = null;
            pendingSnapshot = null;
            pendingId = null;
            listPending = false;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(BlueprintPayloads.ListRequest.TYPE)) {
                sender.sendPacket(new BlueprintPayloads.ListRequest(0));
            }
        });
    }

    private static Component message(BlueprintPayloads.SaveAck ack) {
        String id = ack.blueprint().toString();
        return switch (ack.status()) {
            case CONFLICT -> Component.translatable("blueprint.editor.net.conflict",
                    id, ack.revision());
            case DENIED -> Component.translatable("blueprint.editor.net.denied", id);
            case UNKNOWN -> Component.translatable("blueprint.editor.net.gone", id);
            default -> Component.translatable("blueprint.editor.net.refused", id);
        };
    }

    // ------------------------------------------------------------------ demandes

    /** Demande la liste et l'affiche à l'arrivée. */
    public static void requestList(boolean show) {
        if (!connected()) {
            return;
        }
        listPending = show;
        ClientPlayNetworking.send(new BlueprintPayloads.ListRequest(0));
    }

    /** Le serveur laisse-t-il ce joueur écrire ? Annoncé avec la liste. */
    public static boolean writable() {
        return writable;
    }

    /** Les fichiers importables annoncés par le serveur (navigateur F6). */
    private static List<String> files = List.of();

    public static List<String> files() {
        return files;
    }

    public static void requestFiles() {
        if (connected() && ClientPlayNetworking.canSend(
                BlueprintPayloads.FileListRequest.TYPE)) {
            ClientPlayNetworking.send(new BlueprintPayloads.FileListRequest());
        }
    }

    public static void requestImport(String file) {
        if (connected() && ClientPlayNetworking.canSend(BlueprintPayloads.ImportRequest.TYPE)) {
            ClientPlayNetworking.send(new BlueprintPayloads.ImportRequest(file));
        }
    }

    public static void requestOpen(Identifier id) {
        if (connected()) {
            ClientPlayNetworking.send(new BlueprintPayloads.OpenRequest(id));
        }
    }

    public static void requestCreate(Identifier id) {
        if (connected()) {
            ClientPlayNetworking.send(new BlueprintPayloads.CreateRequest(id));
        }
    }

    /** L'éditeur se ferme : plus de session à recaler (l'instantané en vol reste gardé). */
    public static void closed(EditorSession session) {
        if (active == session) {
            active = null;
            activeId = null;
        }
    }

    /** Fermeture SANS enregistrer : l'abandon est explicite, rien à récupérer. */
    public static void discarded(EditorSession session) {
        closed(session);
        pendingSnapshot = null;
        pendingId = null;
    }

    /** La session réellement à l'écran, ou null — un drapeau collant mentirait (QA NET-001). */
    private static @Nullable EditorSession openSession() {
        EditorSession current = active;
        if (current == null) {
            return null;
        }
        if (Minecraft.getInstance().screen instanceof BlueprintEditorScreen editor
                && editor.session() == current) {
            return current;
        }
        active = null;
        activeId = null;
        return null;
    }

    // ------------------------------------------------------------------ ouverture

    private static EditorSession openEditor(Identifier id, Blueprint graph, boolean canWrite) {
        Minecraft mc = Minecraft.getInstance();
        EditorSession session = EditorSession.of(graph, (snapshot, baseRevision) -> {
            if (!connected()) {
                return false;
            }
            pendingSnapshot = snapshot;
            pendingId = id;
            ClientPlayNetworking.send(new BlueprintPayloads.SaveRequest(
                    id, baseRevision, GraphSync.toBytes(snapshot)));
            return true;
        });
        // Le droit d'écriture vient du paquet, seule source qui fasse autorité —
        // la liste peut n'être pas encore arrivée (QA NET-002).
        session.setWritable(canWrite);
        session.setTestHandler(() -> {
            if (connected()) {
                ClientPlayNetworking.send(new BlueprintPayloads.SetEnabled(id, true));
            }
        });
        active = session;
        activeId = id;
        mc.schedule(() -> mc.setScreen(new BlueprintEditorScreen(session,
                BlueprintMod.registries(), RegistrySync.descriptors(), RegistrySync.lookup())));
        return session;
    }

    private static void say(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(message, false);
            }
        });
    }

    /** Vue de test : identifiants connus, sous forme de liste modifiable. */
    static List<Identifier> knownCopy() {
        return new ArrayList<>(known);
    }
}
