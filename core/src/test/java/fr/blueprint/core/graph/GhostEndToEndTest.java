package fr.blueprint.core.graph;

import com.mojang.serialization.Codec;
import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.api.registry.PinTypeRegistry;
import fr.blueprint.core.compile.Compiler;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nœuds fantômes de bout en bout (story 8.3) : un blueprint enregistré avec un mod,
 * rechargé <b>sans</b> lui, puis rechargé <b>avec</b> lui à nouveau. Le scénario complet
 * — pas seulement le round-trip NBT (story 1.4) : registres réels, validation,
 * compilation, et le message qui nomme le mod manquant.
 */
class GhostEndToEndTest {

    private static final Identifier DRAIN = Identifier.fromNamespaceAndPath("manamod", "drain");
    private static final Identifier MANA_TYPE = Identifier.fromNamespaceAndPath("manamod", "mana");

    private static final List<String> TRACE = new ArrayList<>();

    /** Une seule instance : l'identité des types de pins compte (comparaison par identité). */
    private static final PinType MANA = PinType.builder(MANA_TYPE)
            .javaType(Integer.class).codec(Codec.INT).streamCodec(ByteBufCodecs.VAR_INT)
            .build();

    /** Le mod tiers : un type de pin à lui, un nœud à lui. */
    private static final BlueprintPlugin MANAMOD = new BlueprintPlugin() {
        @Override
        public void registerTypes(PinTypeRegistry registry) {
            registry.register(MANA);
        }

        @Override
        public void registerNodes(NodeRegistry registry) {
            registry.register(NodeType.builder(DRAIN)
                    .exec()
                    .in("cost", MANA, 42)
                    .in("raison", PinTypes.STRING, "rituel")
                    .out("reste", PinTypes.INT)
                    .action(ctx -> {
                        TRACE.add("drain:" + ctx.<Integer>in("cost") + ":" + ctx.<String>in("raison"));
                        ctx.out("reste", 100 - ctx.<Integer>in("cost"));
                    })
                    .build());
        }
    };

    private static PluginLoader.LoadedRegistries withMod() {
        return PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("manamod", MANAMOD)), true);
    }

    private static PluginLoader.LoadedRegistries withoutMod() {
        return PluginLoader.load(List.of(), true);
    }

    private static Function<Identifier, PinType> types(PluginLoader.LoadedRegistries registries) {
        return id -> registries.pinTypes().get(id).orElse(null);
    }

    /** Le graphe de départ : un tick déclenche le nœud du mod, sa sortie part au log. */
    private static Blueprint authored(PluginLoader.LoadedRegistries registries) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("mypack", "rituel"));
        UUID tick = add(bp, registries, "tick", StandardEvents.SERVER_TICK.id());
        UUID drain = add(bp, registries, "drain", DRAIN);
        UUID toString = add(bp, registries, "ts",
                Identifier.fromNamespaceAndPath("blueprint", "convert/to_string"));
        UUID log = add(bp, registries, "log",
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"));

        apply(bp, registries, new EditOperation.AddLink(new Link(tick, "exec_out", drain, "exec_in")));
        apply(bp, registries, new EditOperation.AddLink(new Link(drain, "exec_out", log, "exec_in")));
        apply(bp, registries, new EditOperation.AddLink(new Link(drain, "reste", toString, "value")));
        apply(bp, registries, new EditOperation.AddLink(new Link(toString, "result", log, "value")));
        apply(bp, registries, new EditOperation.SetLiteral(drain, "raison",
                LiteralValue.of(PinTypes.STRING, "invocation")));
        // Un littéral d'un TYPE du mod : c'est lui qui devra survivre en brut.
        apply(bp, registries, new EditOperation.SetLiteral(drain, "cost",
                LiteralValue.of(MANA, 7)));
        // Configuration propre au mod : opaque pour Blueprint, elle doit survivre.
        CompoundTag config = new CompoundTag();
        config.putString("ecole", "evocation");
        bp.node(drain).setConfig(config);
        return bp;
    }

    private static UUID add(Blueprint bp, PluginLoader.LoadedRegistries registries, String seed,
                            Identifier type) {
        UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, registries, new EditOperation.AddNode(uuid, type, new Vec2d(0, 0)));
        return uuid;
    }

    private static void apply(Blueprint bp, PluginLoader.LoadedRegistries registries,
                              EditOperation op) {
        var result = op.apply(bp, registries.nodes());
        if (!result.applied()) {
            throw new AssertionError("opération refusée : " + result.refusal());
        }
    }

    @Test
    void modRemovedThenReinstalled() {
        // --- 1. Le mod est là : le graphe est valide et compile.
        var installed = withMod();
        Blueprint original = authored(installed);
        assertTrue(GraphValidator.validate(original, installed.nodes()).executable(),
                "le graphe d'origine est exécutable");
        CompoundTag saved = GraphNbt.encode(original);

        // --- 2. Le mod disparaît : rien ne se perd (FR40).
        var removed = withoutMod();
        Blueprint ghosted = GraphNbt.decode(saved, types(removed));
        UUID drain = UUID.nameUUIDFromBytes("drain".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Node ghost = ghosted.node(drain);
        assertNotNull(ghost, "le nœud du mod absent reste dans le graphe");
        assertEquals(DRAIN, ghost.typeId());
        assertEquals(4, ghosted.nodes().size());
        assertEquals(4, ghosted.links().size(), "les liens vers le fantôme survivent");
        assertEquals("invocation", ghost.literal("raison").value(),
                "un littéral de type standard reste lisible");
        assertTrue(ghost.hasPreservedLiterals(),
                "le littéral de type « mana » est gardé en brut, pas jeté");
        assertEquals("evocation", ghost.config().getStringOr("ecole", ""),
                "la configuration opaque du mod survit");

        // La forme est déduite des liens et des littéraux : l'éditeur peut l'afficher.
        NodeShape deduced = GhostNode.deduceShape(ghosted, removed.nodes(), ghost);
        assertEquals(List.of("exec_in", "raison"),
                deduced.inputs().stream().map(NodeShape.PinDef::name).toList());
        assertEquals(List.of("exec_out", "reste"),
                deduced.outputs().stream().map(NodeShape.PinDef::name).toList());
        assertEquals(fr.blueprint.api.pin.PinKind.EXEC, deduced.inputs().get(0).kind(),
                "le pin d'en face étant exec, le pin déduit l'est aussi");

        // --- 3. Le blueprint refuse de s'exécuter et NOMME le mod (FR41).
        var validation = GraphValidator.validate(ghosted, removed.nodes());
        assertFalse(validation.executable());
        Diagnostic unknown = validation.errors().stream()
                .filter(d -> d.code() == DiagnosticCode.UNKNOWN_NODE_TYPE)
                .findFirst().orElseThrow();
        assertTrue(unknown.args().contains("manamod"),
                "le diagnostic nomme le mod manquant : " + unknown.args());
        assertEquals(Map.of("manamod", 1), GhostNode.missingProviders(ghosted, removed.nodes()));
        assertEquals("manamod (1 nœud)",
                GhostNode.describeMissing(GhostNode.missingProviders(ghosted, removed.nodes())));

        UUID tick = UUID.nameUUIDFromBytes("tick".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(Compiler.compile(ghosted, removed.nodes(), tick).success(),
                "la compilation refuse : rien ne s'exécutera à moitié");

        // --- 4. Le graphe fantôme se ré-enregistre sans s'abîmer (le monde a été sauvegardé
        // pendant l'absence du mod : c'est LE moment où l'on perd des données, ou pas).
        CompoundTag resaved = GraphNbt.encode(ghosted);

        // --- 5. Le mod revient : tout est restauré à l'identique.
        var reinstalled = withMod();
        Blueprint restored = GraphNbt.decode(resaved, types(reinstalled));
        Node back = restored.node(drain);
        assertNotNull(back);
        assertFalse(back.hasPreservedLiterals(), "le littéral brut redevient un vrai littéral");
        assertEquals(7, back.literal("cost").value(), "…avec sa valeur d'origine");
        assertEquals("invocation", back.literal("raison").value());
        assertEquals("evocation", back.config().getStringOr("ecole", ""));
        assertTrue(original.contentEquals(restored),
                "après l'aller-retour complet, le graphe est identique à l'original");

        assertTrue(GraphValidator.validate(restored, reinstalled.nodes()).executable(),
                "et il redevient exécutable sans la moindre retouche");
        assertTrue(Compiler.compile(restored, reinstalled.nodes(), tick).success());
    }

    /** Deux mods absents : le message les nomme tous les deux, avec leur compte. */
    @Test
    void severalMissingModsAreAllNamed() {
        var registries = withoutMod();
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("mypack", "deux"));
        bp.putNode(new Node(UUID.randomUUID(), DRAIN, new Vec2d(0, 0)));
        bp.putNode(new Node(UUID.randomUUID(), DRAIN, new Vec2d(50, 0)));
        bp.putNode(new Node(UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("autremod", "chose"), new Vec2d(100, 0)));

        assertEquals(Map.of("manamod", 2, "autremod", 1),
                GhostNode.missingProviders(bp, registries.nodes()));
        assertEquals("autremod (1 nœud), manamod (2 nœuds)",
                GhostNode.describeMissing(GhostNode.missingProviders(bp, registries.nodes())),
                "ordre stable (alphabétique) et pluriel correct");
    }
}
