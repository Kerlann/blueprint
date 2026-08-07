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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * {@code /blueprint list|create|delete|enable|disable|info} — gestion du cycle de vie
 * sans éditeur (FR20). {@code list} et {@code info} sont libres ; le reste est soumis
 * au niveau configuré ({@link BlueprintConfig}, défaut : gamemasters).
 */
public final class BlueprintCommand {

    private BlueprintCommand() {
    }

    /**
     * Suggestions d'import : les {@code .bp} réellement présents dans le dossier.
     *
     * <p>Sans elles, l'auteur devait deviner le nom exact d'un fichier qu'il ne voit
     * pas depuis le jeu — et se tromper ne rendait qu'un « fichier introuvable » qui ne
     * disait même pas où l'on avait cherché.
     */
    private static final SuggestionProvider<CommandSourceStack> EXPORT_FILES = (ctx, builder) -> {
        try (var files = java.nio.file.Files.list(exportsDir())) {
            files.map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".bp"))
                    .map(fileName -> fileName.substring(0, fileName.length() - 3))
                    .sorted()
                    .forEach(builder::suggest);
        } catch (java.io.IOException e) {
            // Un dossier illisible ne doit pas casser l'autocomplétion : aucune
            // suggestion vaut mieux qu'une commande qui refuse de s'écrire.
            fr.blueprint.core.BlueprintMod.LOGGER.debug("Dossier d'exports illisible", e);
        }
        return builder.buildFuture();
    };

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
                // « edit » vit dans le MÊME arbre que le reste (AC : relier les deux
                // commandes). Il partage donc les suggestions de `EXISTING_IDS`, qui
                // lisent le gestionnaire du serveur — là où /blueprint-edit affichait
                // une liste reçue au join, périmée dès la première création.
                //
                // Pas de `requires(admin)` : lire est ouvert, comme `info`. Le serveur
                // décide seul si l'ouverture est modifiable ou en lecture seule.
                .then(literal("edit")
                        .executes(BlueprintCommand::openBrowser)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(BlueprintCommand::edit)))
                .then(literal("create")
                        .requires(admin)
                        .then(idArgument()
                                .executes(BlueprintCommand::create)))
                .then(literal("delete")
                        .requires(admin)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(BlueprintCommand::delete)))
                // « all » AVANT l'argument identifiant : Brigadier essaie les littéraux
                // d'abord, donc « all » ne peut pas être confondu avec un identifiant.
                .then(literal("enable")
                        .requires(admin)
                        .then(literal("all").executes(ctx -> setAllEnabled(ctx, true)))
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(ctx -> setEnabled(ctx, true))))
                .then(literal("disable")
                        .requires(admin)
                        .then(literal("all").executes(ctx -> setAllEnabled(ctx, false)))
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
                                .suggests(EXPORT_FILES)
                                .executes(BlueprintCommand::importFile)))
                // Le banc de performance : le seul blueprint livré. Il ne s'active pas
                // tout seul — il se déclenche à la commande /bpc bench.
                .then(literal("bench")
                        .requires(admin)
                        .executes(BlueprintCommand::bench))
                // La vitrine : les douze types d'éléments d'écran, tous câblés.
                .then(literal("showcase")
                        .requires(admin)
                        .executes(BlueprintCommand::showcase))
                // Le serveur de jeu de rôle : création de personnage et fiche permanente.
                .then(literal("rp")
                        .requires(admin)
                        .executes(BlueprintCommand::roleplay))
                // Le contenu déclaré (épic 11). Sans cette commande, un fichier écarté
                // ne se saurait que dans le journal du serveur — c'est-à-dire nulle part,
                // pour qui vient de déposer un JSON et se demande où est son item.
                .then(literal("content")
                        .requires(admin)
                        .executes(BlueprintCommand::content))
                // Signal (batch 1) : émettre depuis l'extérieur — une autre commande,
                // un bloc de commande, un mod. Sans permission d'admin : un signal ne
                // peut rien faire que le blueprint qui l'écoute n'ait déjà le droit
                // de faire, et son plafond de permission reste le sien.
                .then(literal("signal")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<CommandSourceStack, String>argument("name",
                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> signal(ctx, ""))
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                        .<CommandSourceStack, String>argument("payload",
                                                com.mojang.brigadier.arguments.StringArgumentType
                                                        .greedyString())
                                        .executes(ctx -> signal(ctx,
                                                com.mojang.brigadier.arguments.StringArgumentType
                                                        .getString(ctx, "payload"))))))
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
                                                .executes(BlueprintCommand::debugValues)))))
                // Profileur (9.2) : où part le temps, nœud par nœud.
                .then(literal("profile")
                        .requires(admin)
                        .then(idArgument()
                                .suggests(EXISTING_IDS)
                                .executes(ctx -> profile(ctx, "show"))
                                .then(literal("on").executes(ctx -> profile(ctx, "on")))
                                .then(literal("off").executes(ctx -> profile(ctx, "off")))
                                .then(literal("show").executes(ctx -> profile(ctx, "show")))
                                .then(literal("reset").executes(ctx -> profile(ctx, "reset")))
                                .then(literal("export").executes(ctx -> profile(ctx, "export")))));
    }

    // ------------------------------------------------------------------ profileur

    /**
     * L'« identifiant » reçu est-il en réalité une action tapée dans le mauvais ordre ?
     *
     * <p>L'espace de nom sert de garde : un vrai blueprint nommé {@code show} existerait
     * sous {@code blueprint:show} ou celui d'un mod, jamais sous {@code minecraft:}, que
     * Brigadier ajoute quand on ne met pas de préfixe.
     */
    private static boolean looksLikeAnAction(Identifier id) {
        return "minecraft".equals(id.getNamespace())
                && java.util.List.of("on", "off", "show", "reset", "export")
                        .contains(id.getPath());
    }

    private static int profile(CommandContext<CommandSourceStack> ctx, String action) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        if ("on".equals(action)) {
            fr.blueprint.core.debug.Profiler.enable(id);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "blueprint.cmd.profile_on", id.toString()), true);
            return 1;
        }
        if ("off".equals(action)) {
            fr.blueprint.core.debug.Profiler.disable(id);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "blueprint.cmd.profile_off", id.toString()), true);
            return 1;
        }
        var profiler = fr.blueprint.core.debug.Profiler.of(id);
        if (profiler == null) {
            // « /blueprint profile show » se lit comme un IDENTIFIANT nommé « show », que
            // Brigadier complète en « minecraft:show ». Répondre « le profilage n'est pas
            // actif pour minecraft:show » est exact et parfaitement inutile : celui qui
            // vient de taper cela cherche une syntaxe, pas un blueprint. L'ordre attendu
            // est <id> PUIS l'action, ce qui n'est pas devinable et se tape à l'envers
            // une fois sur deux.
            ctx.getSource().sendFailure(Component.translatable(
                    looksLikeAnAction(id) ? "blueprint.cmd.profile_wrong_order"
                            : "blueprint.cmd.profile_not_on", id.toString()));
            return 0;
        }
        if ("reset".equals(action)) {
            profiler.reset();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "blueprint.cmd.profile_reset", id.toString()), false);
            return 1;
        }

        // Le coût par blueprint vient de l'ordonnanceur, le coût par nœud du profileur :
        // les deux dans le même rapport, c'est là qu'ils se comparent.
        var stats = fr.blueprint.core.BlueprintMod
                .schedulerOf(ctx.getSource().getServer()).stats(id);
        // « \n » et non « %n » : ce texte part dans le chat du jeu, qui affiche le retour
        // chariot que « %n » ajoute sous Windows. C'est lui qu'on voyait en fin de la
        // première ligne du rapport.
        String report = String.format("%s\n%s",
                String.format("Exécutions %d, terminées %d, fautes %d, fuel %d, "
                                + "temps total %d µs, pic %d µs",
                        stats.runs(), stats.completed(), stats.faults(), stats.fuel(),
                        stats.totalNanos() / 1_000, stats.peakNanos() / 1_000),
                profiler.report(id, 10));

        if ("export".equals(action)) {
            java.nio.file.Path file = exportsDir().resolveSibling("profiles")
                    .resolve(id.getNamespace() + "-" + id.getPath().replace('/', '_')
                            + "-" + System.currentTimeMillis() + ".txt");
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, report,
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                fr.blueprint.core.BlueprintMod.LOGGER.error("Export du profil de « {} » impossible",
                        id, e);
                ctx.getSource().sendFailure(Component.translatable(
                        "blueprint.cmd.profile_export_failed", id.toString()));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "blueprint.cmd.profile_exported", file.toString()), false);
            return 1;
        }

        // Affichage : une ligne par ligne du rapport, le chat n'aime pas les blocs.
        for (String line : report.split("\n")) {
            if (!line.isBlank()) {
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
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

    /** Résout un préfixe d'UUID (la logique est dans {@code DebugSession}, testée à part). */
    private static @org.jetbrains.annotations.Nullable java.util.UUID resolveNode(
            CommandContext<CommandSourceStack> ctx, fr.blueprint.core.debug.DebugSession session,
            String prefix) {
        var match = session.resolve(prefix);
        if (match.ambiguous()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "blueprint.cmd.debug_ambiguous", prefix));
            return null;
        }
        if (!match.found()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "blueprint.cmd.debug_unknown_node", prefix));
            return null;
        }
        return match.node();
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
        return fr.blueprint.core.BlueprintPaths.exports();
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
            // Dire OÙ l'on a cherché : « fichier introuvable » sans chemin laisse le
            // joueur deviner entre le dossier du jeu, celui du monde et celui du mod.
            ctx.getSource().sendFailure(Component.translatable(
                    "blueprint.cmd.import_failed", name, exportsDir().toString()));
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

    /**
     * Crée les blueprints d'exemple, <b>désactivés</b>. Un exemple qui se met à tourner
     * dès sa création changerait le monde du joueur avant qu'il ait pu le lire — et
     * l'un d'eux pose des blocs. Il les active quand il a compris ce qu'ils font.
     */
    /**
     * Ce que le contenu déclaré a enregistré, et ce qu'il a écarté (épic 11).
     *
     * <p>La liste des <b>refus</b> est l'essentiel. Elle est décidée à l'initialisation du
     * mod, longtemps avant qu'un joueur puisse lire quoi que ce soit : sans cette commande,
     * un fichier écarté n'existerait que dans le journal du serveur — c'est-à-dire nulle
     * part, pour qui vient de déposer un JSON et cherche son item dans le créatif.
     */
    private static int content(CommandContext<CommandSourceStack> ctx) {
        var registered = fr.blueprint.core.content.ContentRegistrar.registered();
        var rejected = fr.blueprint.core.BlueprintMod.contentRejected();

        var declared = fr.blueprint.core.BlueprintMod.contentDeclared();
        var blocks = fr.blueprint.core.BlueprintMod.contentBlocks();
        // Un bloc pose son propre item, du même identifiant : le compter dans les items
        // ferait annoncer deux déclarations là où l'auteur n'en a écrit qu'une.
        int itemCount = registered.size() - blocks.size();
        // Rien de déclaré : dire OÙ déposer les fichiers, pas « 0, 0, 0 ». Le dossier
        // n'est pas créé tant qu'on n'y écrit pas (11.1), si bien que personne ne peut
        // le trouver en explorant — et un compteur à zéro ressemble à une panne autant
        // qu'à un dossier vide. C'est ce que blueprint.pack.none fait déjà pour la 10.5.
        if (registered.isEmpty() && rejected.isEmpty()) {
            String where = fr.blueprint.core.BlueprintPaths.content().toString();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "blueprint.cmd.content_none", where, where), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "blueprint.cmd.content", itemCount, blocks.size(), rejected.size()), false);
        registered.keySet().forEach(id -> {
            var block = blocks.get(id);
            if (block != null) {
                // Un bloc apparaît une seule fois, comme bloc : son item porte le même
                // identifiant, et le lister deux fois donnerait à croire qu'on a déclaré
                // deux choses là où il n'y en a qu'une.
                ctx.getSource().sendSuccess(() -> block.hasTexture()
                        ? Component.translatable("blueprint.cmd.content_block", id.toString(),
                                block.hardness(), block.tool().name().toLowerCase(Locale.ROOT))
                        : Component.translatable("blueprint.cmd.content_no_texture",
                                id.toString()), false);
                return;
            }
            var definition = declared.get(id);
            // « sans image » est dit ICI, à côté de l'item, et non dans une seconde
            // liste : un item enregistré qui s'affiche en damier est le cas le plus
            // déroutant du contenu déclaré — il a l'air cassé alors qu'il ne manque
            // qu'un PNG, et rien d'autre dans le jeu ne le dira.
            boolean bare = definition != null && !definition.hasTexture();
            ctx.getSource().sendSuccess(() -> bare
                    ? Component.translatable("blueprint.cmd.content_no_texture", id.toString())
                    : Component.literal("- " + id), false);
        });
        // Le refus part en ÉCHEC : il se voit en rouge, et une console le distingue du
        // reste. Un item manquant se cherche longtemps quand la raison est en gris.
        rejected.forEach(reason -> ctx.getSource().sendFailure(
                Component.translatable("blueprint.cmd.content_rejected", reason)));
        return registered.size();
    }

    private static int signal(CommandContext<CommandSourceStack> ctx, String payload) {
        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
        var server = ctx.getSource().getServer();
        int listeners = fr.blueprint.core.BlueprintMod.signalListeners(server, name);
        if (listeners == 0) {
            // Dire « personne n'écoute » plutôt que « émis » : sans cela, un nom mal
            // orthographié se comporte exactement comme un signal qui fonctionne.
            ctx.getSource().sendFailure(Component.translatable(
                    "blueprint.cmd.signal_unheard", name));
            return 0;
        }
        if (!fr.blueprint.core.BlueprintMod.emitSignal(server, name, payload)) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.signal_budget"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "blueprint.cmd.signal_sent", name, listeners), false);
        return listeners;
    }

    private static int showcase(CommandContext<CommandSourceStack> ctx) {
        return install(ctx, fr.blueprint.core.ShowcaseBlueprint.build(
                fr.blueprint.core.BlueprintMod.registries().nodes()),
                "blueprint.cmd.showcase_created", "blueprint.cmd.showcase_created_no_file");
    }

    /**
     * Installe le blueprint de serveur RP et l'ACTIVE.
     *
     * <p>L'activation compte double ici : il travaille à la <b>connexion</b>, pas à la
     * commande. Installé sans être actif, il n'aurait l'air de rien faire, et la seule
     * façon de s'en apercevoir serait de se déconnecter pour revenir.
     */
    private static int roleplay(CommandContext<CommandSourceStack> ctx) {
        return install(ctx, fr.blueprint.core.RoleplayBlueprint.build(
                fr.blueprint.core.BlueprintMod.registries().nodes()),
                "blueprint.cmd.rp_created", "blueprint.cmd.rp_created_no_file");
    }

    /**
     * Installe le banc de performance et l'ACTIVE — sans quoi {@code /bpc bench} ne
     * trouverait rien à déclencher, et l'on chercherait longtemps pourquoi.
     */
    private static int bench(CommandContext<CommandSourceStack> ctx) {
        return install(ctx, fr.blueprint.core.BenchBlueprint.build(
                fr.blueprint.core.BlueprintMod.registries().nodes()),
                "blueprint.cmd.bench_created", "blueprint.cmd.bench_created_no_file");
    }

    /** Adopte un blueprint livré, l'active, et l'exporte au mieux. */
    private static int install(CommandContext<CommandSourceStack> ctx, Blueprint bp,
                               String okKey, String noFileKey) {
        var manager = BlueprintManager.of(ctx.getSource().getServer());
        if (!manager.adopt(bp)) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.exists", bp.id().toString()));
            return 0;
        }
        manager.setEnabled(bp.id(), true);
        // Le blueprint existe en mémoire quoi qu'il arrive ; l'export sur disque, lui,
        // peut échouer (droits, disque plein). Annoncer « créé et exporté » dans ce cas
        // envoyait le joueur chercher un fichier absent, l'échec n'étant qu'au log.
        var file = fr.blueprint.core.BlueprintFiles.export(bp, exportsDir(),
                fr.blueprint.core.BlueprintMod.registries());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                file != null ? okKey : noFileKey, bp.id().toString()), true);
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

    /**
     * {@code enable all} / {@code disable all}. Une commande par blueprint devient vite
     * pénible dès qu'on en a une dizaine — et c'est le cas dès qu'on charge les
     * exemples.
     *
     * <p>Le compte rendu dit combien ont <b>changé</b>, pas combien existent : relancer
     * la commande doit répondre « 0 » plutôt que de laisser croire qu'elle a agi.
     */
    private static int setAllEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        BlueprintManager manager = BlueprintManager.of(ctx.getSource().getServer());
        List<Identifier> changed = new ArrayList<>();
        // Copie de la liste : setEnabled(false) referme des écrans, ce qui touche
        // d'autres tables — on ne parcourt pas une collection qu'on fait bouger.
        for (Blueprint bp : List.copyOf(manager.all())) {
            if (bp.enabled() != enabled) {
                manager.setEnabled(bp.id(), enabled);
                changed.add(bp.id());
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "blueprint.cmd.enabled_all" : "blueprint.cmd.disabled_all",
                changed.size()), true);
        return changed.size();
    }

    /**
     * {@code /blueprint edit} — ouvre le navigateur chez le joueur.
     *
     * <p>Une commande de <b>joueur</b> : sans lui, il n'y a pas d'écran où ouvrir quoi
     * que ce soit. Une console ou un bloc de commande reçoit donc un refus explicite
     * plutôt qu'un silence.
     */
    private static int openBrowser(CommandContext<CommandSourceStack> ctx) {
        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.player_only"));
            return 0;
        }
        fr.blueprint.core.net.ServerBlueprintNet.openBrowser(player);
        return 1;
    }

    /** {@code /blueprint edit <id>} — envoie le graphe, le client ouvre l'éditeur. */
    private static int edit(CommandContext<CommandSourceStack> ctx) {
        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("blueprint.cmd.player_only"));
            return 0;
        }
        Identifier id = IdentifierArgument.getId(ctx, "id");
        if (!fr.blueprint.core.net.ServerBlueprintNet.sendForEditing(player, id)) {
            ctx.getSource().sendFailure(
                    Component.translatable("blueprint.cmd.not_found", id.toString()));
            return 0;
        }
        return 1;
    }

    private static Component state(Blueprint bp) {
        return Component.translatable(bp.enabled() ? "blueprint.cmd.state.on" : "blueprint.cmd.state.off");
    }
}
