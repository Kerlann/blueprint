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
    /** Une liste demandée par la commande attend son affichage dans le chat. */
    private static volatile boolean listPending;

    public static List<Identifier> known() {
        return known;
    }

    public static boolean connected() {
        return ClientPlayNetworking.canSend(BlueprintPayloads.OpenRequest.TYPE);
    }

    public static void register() {
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
                    // Resynchro ciblée après un conflit : la session ouverte reçoit la
                    // nouvelle base de verrou, son graphe n'est PAS remplacé.
                    EditorSession current = active;
                    if (current != null && payload.blueprint().equals(activeId)) {
                        current.saveRefused(payload.revision());
                        return;
                    }
                    openEditor(payload.blueprint(), graph, payload.writable());
                });

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.SaveAck.TYPE,
                (payload, context) -> {
                    EditorSession session = active;
                    if (session == null || !payload.blueprint().equals(activeId)) {
                        // Un verdict sans session : seule la création peut être concernée.
                        if (payload.status() != BlueprintPayloads.SaveStatus.SAVED) {
                            say(message(payload));
                        }
                        return;
                    }
                    if (payload.status() == BlueprintPayloads.SaveStatus.SAVED) {
                        session.saveAccepted(payload.revision());
                        return;
                    }
                    session.saveRefused(payload.revision());
                    say(message(payload));
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            known = List.of();
            writable = false;
            active = null;
            activeId = null;
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

    /** L'éditeur se ferme : plus de session à recaler. */
    public static void closed(EditorSession session) {
        if (active == session) {
            active = null;
            activeId = null;
        }
    }

    // ------------------------------------------------------------------ ouverture

    private static void openEditor(Identifier id, Blueprint graph, boolean canWrite) {
        Minecraft mc = Minecraft.getInstance();
        EditorSession session = EditorSession.of(graph, (snapshot, baseRevision) -> {
            if (!connected()) {
                return false;
            }
            ClientPlayNetworking.send(new BlueprintPayloads.SaveRequest(
                    id, baseRevision, GraphSync.toBytes(snapshot)));
            return true;
        });
        session.setWritable(canWrite && writable);
        session.setTestHandler(() -> {
            if (connected()) {
                ClientPlayNetworking.send(new BlueprintPayloads.SetEnabled(id, true));
            }
        });
        active = session;
        activeId = id;
        mc.schedule(() -> mc.setScreen(new BlueprintEditorScreen(session,
                BlueprintMod.registries(), RegistrySync.descriptors(), RegistrySync.lookup())));
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
