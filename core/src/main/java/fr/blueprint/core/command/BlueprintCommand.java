package fr.blueprint.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.config.BlueprintConfig;
import fr.blueprint.core.graph.Blueprint;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permission;

import java.util.function.Predicate;

/**
 * {@code /blueprint list|create|delete|enable|disable|info} — gestion du cycle de vie
 * sans éditeur (FR20). {@code list} et {@code info} sont libres ; le reste est soumis
 * au niveau configuré ({@link BlueprintConfig}, défaut : gamemasters).
 */
public final class BlueprintCommand {

    private BlueprintCommand() {
    }

    /** Suggestions : les identifiants des blueprints existants du serveur (AC1). */
    private static final SuggestionProvider<CommandSourceStack> EXISTING_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(
                    BlueprintManager.of(ctx.getSource().getServer()).all().stream().map(Blueprint::id),
                    builder);

    public static void register(BlueprintConfig config) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(build(config)));
    }

    /**
     * Arbre séparé de l'enregistrement : testable headless. Brigadier brut plutôt que
     * {@code Commands.literal/hasPermission} — l'init statique de {@code Commands} exige
     * le bootstrap des registres, ces fabriques non.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build(BlueprintConfig config) {
        Permission required = config.adminPermission();
        Predicate<CommandSourceStack> admin = required == null
                ? source -> true
                : source -> source.permissions().hasPermission(required);
        return literal("blueprint")
                .then(literal("list")
                        .executes(BlueprintCommand::list))
                .then(literal("info")
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(BlueprintCommand::info)))
                .then(literal("create")
                        .requires(admin)
                        .then(idArgument()
                                .executes(BlueprintCommand::create)))
                .then(literal("delete")
                        .requires(admin)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(BlueprintCommand::delete)))
                .then(literal("enable")
                        .requires(admin)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(ctx -> setEnabled(ctx, true))))
                .then(literal("disable")
                        .requires(admin)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(ctx -> setEnabled(ctx, false))))
                .then(literal("export")
                        .requires(admin)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(BlueprintCommand::export)))
                .then(literal("import")
                        .requires(admin)
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("file",
                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(BlueprintCommand::importFile)))
                .then(literal("demo")
                        .requires(admin)
                        .executes(BlueprintCommand::demo))
                // Débogage (9.1a) : réservé aux administrateurs — voir les valeurs qui
                // circulent, c'est voir ce que fait le graphe d'un autre joueur.
                .then(literal("debug")
                        .requires(admin)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .then(literal("on").executes(ctx -> debugOn(ctx, true)))
                                .then(literal("off").executes(ctx -> debugOn(ctx, false)))
                                .then(literal("status").executes(BlueprintCommand::debugStatus))
                                .then(literal("step").executes(ctx -> debugFlow(ctx, true)))
                                .then(literal("continue").executes(ctx -> debugFlow(ctx, false)))
                                .then(literal("clear").executes(BlueprintCommand::debugClear))
                                .then(literal("break")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(
                                                        "node", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests(TRACED_NODES)
                                                .executes(ctx -> debugBreak(ctx, true))))
                                .then(literal("unbreak")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(
                                                        "node", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests(TRACED_NODES)
                                                .executes(ctx -> debugBreak(ctx, false))))
                                .then(literal("values")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(
                                                        "node", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests(TRACED_NODES)
                                                .executes(BlueprintCommand::debugValues)))));
    }

    // ------------------------------------------------------------------ débogage

    /**
     * Suggestions de nœuds : ceux qu'on vient de voir passer. Taper un UUID complet dans
     * le chat est impraticable — un préfixe suffit, et la trace donne les candidats.
     */
    private static final SuggestionProvider<CommandSourceStack> TRACED_NODES = (ctx, builder) -> {
        Identifier id = tryId(ctx);
        var session = id == null ? null : fr.blueprint.core.debug.DebugSessions.of(id);
        if (session != null) {
            session.trace().forEach(node -> builder.suggest(node.toString().substring(0, 8)));
            session.breakpoints().forEach(node -> builder.suggest(node.toString().substring(0, 8)));
        }
        return builder.buildFuture();
    };

    private static @org.jetbrains.annotations.Nullable Identifier tryId(
            CommandContext<CommandSourceStack> ctx) {
        try {
            return IdentifierArgument.getId(ctx, "id");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int debugOn(CommandContext<CommandSourceStack> ctx, boolean on) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        if (BlueprintManager.of(ctx.getSource().getServer()).get(id).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.not_found", id.toString()));
            return 0;
        }
        if (on) {
            fr.blueprint.core.debug.DebugSessions.open(id);
        } else {
            fr.blueprint.core.debug.DebugSessions.close(id);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                on ? "blueprint.cmd.debug_on" : "blueprint.cmd.debug_off", id.toString()), true);
        return 1;
    }

    private static int debugStatus(CommandContext<CommandSourceStack> ctx) {
        var session = requireSession(ctx);
        if (session == null) {
            return 0;
        }
        var paused = session.pausedAt();
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.debug_status",
                session.blueprint().toString(),
                paused == null ? "—" : shortId(paused),
                session.breakpoints().size(),
                session.trace().size()), false);
        return 1;
    }

    private static int debugFlow(CommandContext<CommandSourceStack> ctx, boolean step) {
        var session = requireSession(ctx);
        if (session == null) {
            return 0;
        }
        if (step) {
            session.step();
        } else {
            session.resume();
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                step ? "blueprint.cmd.debug_step" : "blueprint.cmd.debug_continue"), false);
        return 1;
    }

    private static int debugClear(CommandContext<CommandSourceStack> ctx) {
        var session = requireSession(ctx);
        if (session == null) {
            return 0;
        }
        session.clearBreakpoints();
        session.clearValues();
        session.resume();
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.debug_cleared"), false);
        return 1;
    }

    private static int debugBreak(CommandContext<CommandSourceStack> ctx, boolean add) {
        var session = requireSession(ctx);
        if (session == null) {
            return 0;
        }
        String prefix = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "node");
        java.util.UUID node = resolveNode(ctx, session, prefix);
        if (node == null) {
            return 0;
        }
        if (add) {
            session.breakOn(node);
        } else {
            session.unbreak(node);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                add ? "blueprint.cmd.debug_break" : "blueprint.cmd.debug_unbreak",
                shortId(node)), false);
        return 1;
    }

    private static int debugValues(CommandContext<CommandSourceStack> ctx) {
        var session = requireSession(ctx);
        if (session == null) {
            return 0;
        }
        String prefix = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "node");
        java.util.UUID node = resolveNode(ctx, session, prefix);
        if (node == null) {
            return 0;
        }
        var values = session.valuesOf(node);
        if (values.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "blueprint.cmd.debug_no_values", shortId(node)), false);
            return 1;
        }
        StringBuilder text = new StringBuilder();
        values.forEach((pin, value) -> {
            if (!text.isEmpty()) {
                text.append("  ");
            }
            text.append(pin).append('=').append(value);
        });
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.debug_values",
                shortId(node), session.hits(node), text.toString()), false);
        return 1;
    }

    /** Résout un préfixe d'UUID parmi les nœuds vus et les points d'arrêt posés. */
    private static @org.jetbrains.annotations.Nullable java.util.UUID resolveNode(
            CommandContext<CommandSourceStack> ctx, fr.blueprint.core.debug.DebugSession session,
            String prefix) {
        java.util.Set<java.util.UUID> candidates = new java.util.LinkedHashSet<>(session.trace());
        candidates.addAll(session.breakpoints());
        candidates.addAll(session.allValues().keySet());
        java.util.UUID found = null;
        for (java.util.UUID candidate : candidates) {
            if (candidate.toString().startsWith(prefix)) {
                if (found != null) {
                    ctx.getSource().sendFailure(Component.translatable(
                            "blueprint.cmd.debug_ambiguous", prefix));
                    return null;
                }
                found = candidate;
            }
        }
        if (found == null) {
            // Un UUID complet reste accepté : l'éditeur, lui, les connaît tous.
            try {
                return java.util.UUID.fromString(prefix);
            } catch (IllegalArgumentException e) {
                ctx.getSource().sendFailure(Component.translatable(
                        "blueprint.cmd.debug_unknown_node", prefix));
                return null;
            }
        }
        return found;
    }

    private static @org.jetbrains.annotations.Nullable fr.blueprint.core.debug.DebugSession
            requireSession(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        var session = fr.blueprint.core.debug.DebugSessions.of(id);
        if (session == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "blueprint.cmd.debug_not_open", id.toString()));
        }
        return session;
    }

    private static String shortId(java.util.UUID node) {
        return node.toString().substring(0, 8);
    }

    private static java.nio.file.Path exportsDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("blueprint").resolve("exports");
    }

    private static int export(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        var bp = BlueprintManager.of(ctx.getSource().getServer()).get(id).orElse(null);
        if (bp == null) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.not_found", id.toString()));
            return 0;
        }
        var path = fr.blueprint.core.BlueprintFiles.export(bp, exportsDir(),
                fr.blueprint.core.BlueprintMod.registries());
        if (path == null) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.export_failed", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.exported",
                id.toString(), path.getFileName().toString()), false);
        return 1;
    }

    private static int importFile(CommandContext<CommandSourceStack> ctx) {
        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "file");
        var bp = fr.blueprint.core.BlueprintFiles.importFile(exportsDir(), name,
                fr.blueprint.core.BlueprintMod.registries());
        if (bp == null) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.import_failed", name));
            return 0;
        }
        if (!BlueprintManager.of(ctx.getSource().getServer()).adopt(bp)) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.exists", bp.id().toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.imported",
                bp.id().toString(), bp.nodes().size()), true);
        return 1;
    }

    private static int demo(CommandContext<CommandSourceStack> ctx) {
        var registries = fr.blueprint.core.BlueprintMod.registries();
        var manager = BlueprintManager.of(ctx.getSource().getServer());
        var bp = fr.blueprint.core.DemoBlueprint.build(registries.nodes());
        if (!manager.adopt(bp)) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.exists", bp.id().toString()));
            return 0;
        }
        fr.blueprint.core.BlueprintFiles.export(bp, exportsDir(),
                fr.blueprint.core.BlueprintMod.registries());
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.demo_created",
                bp.id().toString()), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> idArgument() {
        return RequiredArgumentBuilder.argument("id", IdentifierArgument.id());
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var manager = BlueprintManager.of(ctx.getSource().getServer());
        var all = manager.all();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.list.empty"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.list.header", all.size()), false);
        for (Blueprint bp : all) {
            ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.list.entry",
                    bp.id().toString(), state(bp)), false);
        }
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        var bp = BlueprintManager.of(ctx.getSource().getServer()).get(id).orElse(null);
        if (bp == null) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.not_found", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.info",
                bp.id().toString(), state(bp),
                bp.nodes().size(), bp.links().size(), bp.variables().size(),
                bp.meta().version()), false);
        // FR41 : dire tout de suite POURQUOI un blueprint ne tourne pas, et quoi
        // réinstaller — c'est la première question posée quand un mod disparaît.
        var missing = fr.blueprint.core.graph.GhostNode.missingProviders(
                bp, fr.blueprint.core.BlueprintMod.registries().nodes());
        if (!missing.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.info_ghosts",
                    fr.blueprint.core.graph.GhostNode.describeMissing(missing)), false);
        }
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        var created = BlueprintManager.of(ctx.getSource().getServer()).create(id);
        if (created.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.exists", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.created", id.toString()), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        if (!BlueprintManager.of(ctx.getSource().getServer()).delete(id)) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.not_found", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("blueprint.cmd.deleted", id.toString()), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        if (!BlueprintManager.of(ctx.getSource().getServer()).setEnabled(id, enabled)) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.not_found", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "blueprint.cmd.enabled" : "blueprint.cmd.disabled", id.toString()), true);
        return 1;
    }

    private static Component state(Blueprint bp) {
        return Component.translatable(bp.enabled() ? "blueprint.cmd.state.on" : "blueprint.cmd.state.off");
    }
}
