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
                                .executes(ctx -> setEnabled(ctx, false))));
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
