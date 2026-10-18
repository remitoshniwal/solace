package com.solacemod.solace.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solacemod.solace.ai.ChatMessage;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.horror.HorrorDirector;
import com.solacemod.solace.story.AiService;
import com.solacemod.solace.story.Fragments;
import com.solacemod.solace.story.GlobalMemory;
import com.solacemod.solace.story.PlayerStory;
import com.solacemod.solace.story.SolaceBrain;
import com.solacemod.solace.story.SolaceManager;
import com.solacemod.solace.story.StoryData;
import com.solacemod.solace.story.StoryDirector;
import com.solacemod.solace.story.StoryText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** The /solace command. Type /solace in game for help. */
public final class SolaceCommand {
    private SolaceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("solace")
                .executes(SolaceCommand::help)
                .then(Commands.literal("say").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            SolaceBrain.talk(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "message"));
                            return 1;
                        })))
                .then(Commands.literal("setup").executes(ctx -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ctx.getSource().getPlayerOrException(),
                            new com.solacemod.solace.network.ModNetwork.OpenSetupPayload(false));
                    return 1;
                }))
                .then(Commands.literal("come").executes(SolaceCommand::come))
                .then(Commands.literal("status").executes(SolaceCommand::status))
                .then(Commands.literal("pockets").executes(SolaceCommand::pockets))
                .then(Commands.literal("test").executes(SolaceCommand::test))
                .then(Commands.literal("choose").then(Commands.argument("option", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            for (String s : List.of("stay", "release", "erase", "together")) b.suggest(s);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String err = StoryDirector.choose(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "option"));
                            if (err != null) ctx.getSource().sendFailure(Component.literal(err));
                            return err == null ? 1 : 0;
                        })))
                .then(Commands.literal("forget")
                        .executes(ctx -> msg(ctx, "This makes Solace forget everything it knows about you, in every world. Type /solace forget confirm"))
                        .then(Commands.literal("confirm").executes(SolaceCommand::forget)))
                .then(Commands.literal("reset")
                        .executes(ctx -> msg(ctx, "This restarts your Solace story in this world from the beginning. Type /solace reset confirm"))
                        .then(Commands.literal("confirm").executes(SolaceCommand::reset)))
                .then(Commands.literal("admin").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("act").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("act", IntegerArgumentType.integer(1, 4)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    PlayerStory story = story(ctx, p);
                                    if (!story.met) return fail(ctx, p.getGameProfile().getName() + " hasn't met Solace yet (it appears 30 seconds after joining).");
                                    StoryDirector.setAct(p, story, IntegerArgumentType.getInteger(ctx, "act"));
                                    return msg(ctx, "Moved " + p.getGameProfile().getName() + " to act " + story.act + ".");
                                }))))
                        .then(Commands.literal("trust").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    story(ctx, p).trust = IntegerArgumentType.getInteger(ctx, "value");
                                    return msg(ctx, "Trust set.");
                                }))))
                        .then(Commands.literal("bond").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("tether", IntegerArgumentType.integer(0, 1000))
                                        .then(Commands.argument("debt", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
                                            PlayerStory s = story(ctx, EntityArgument.getPlayer(ctx, "player"));
                                            s.tether = Math.min(IntegerArgumentType.getInteger(ctx, "tether"), com.solacemod.solace.story.Bond.maxTether(s));
                                            s.debt = IntegerArgumentType.getInteger(ctx, "debt");
                                            return msg(ctx, "Tether " + (int) s.tether + ", debt " + (int) s.debt + ".");
                                        })))))
                        .then(Commands.literal("fragment").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, StoryText.FRAGMENT_COUNT)).executes(ctx -> {
                                    Fragments.giveTo(EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "page") - 1);
                                    return msg(ctx, "Page given.");
                                }))))
                        .then(Commands.literal("horror").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("event", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String s : HorrorDirector.kinds().split(" ")) b.suggest(s);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            boolean ok = HorrorDirector.run(p, story(ctx, p), StringArgumentType.getString(ctx, "event"));
                                            return ok ? msg(ctx, "Done.") : fail(ctx, "Unknown event. Options: " + HorrorDirector.kinds());
                                        }))))
                        .then(Commands.literal("presence").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("behaviour", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            b.suggest("blink");
                                            for (String s : com.solacemod.solace.entity.Presence.NAMES) b.suggest(s);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            int kind = com.solacemod.solace.entity.Presence.byName(StringArgumentType.getString(ctx, "behaviour"));
                                            if (kind == -2) return fail(ctx, "Unknown. Options: blink " + String.join(" ", com.solacemod.solace.entity.Presence.NAMES));
                                            SolaceEntity solace = SolaceManager.find(ctx.getSource().getServer(), story(ctx, p));
                                            if (solace == null || solace.level() != p.level()) return fail(ctx, "Solace isn't near " + p.getGameProfile().getName() + ".");
                                            solace.presence.end();
                                            solace.presence.start(p, kind);
                                            return msg(ctx, "Done.");
                                        }))))
                        .then(Commands.literal("lantern").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            SolaceEntity solace = SolaceManager.find(ctx.getSource().getServer(), story(ctx, p));
                            if (solace == null) return fail(ctx, "Solace isn't around.");
                            solace.showLantern(20 * 10);
                            return msg(ctx, "Showing the Lantern form for 10 seconds.");
                        })))
                        .then(Commands.literal("question").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            PlayerStory s = story(ctx, p);
                            StoryDirector.setAct(p, s, 4);
                            for (int i = 0; i < StoryText.FRAGMENT_COUNT; i++) s.fragmentsFound.add(i);
                            return msg(ctx, "Solace will ask its question in about a minute.");
                        })))));
    }

    private static PlayerStory story(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        StoryData data = StoryData.get(ctx.getSource().getServer());
        data.setDirty();
        return data.get(p.getUUID());
    }

    private static int msg(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendFailure(Component.literal(text));
        return 0;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        String[] lines = {
                "Solace commands:",
                "  Just chat near Solace (or say its name) to talk to it.",
                "  /solace setup           open Solace's settings (AI, voice, scares)",
                "  /solace say <message>   talk to Solace from anywhere",
                "  /solace come            call Solace to you",
                "  /solace pockets         open Solace's inventory (or right-click it with an empty hand)",
                "  /solace status          story progress and AI connection",
                "  /solace test            test your AI provider settings",
                "  /solace forget          make Solace forget you (memory wipe)",
                "  /solace reset           restart the story in this world",
                "Try asking: \"follow me\", \"mine 10 iron\", \"build a shelter\", \"craft a stone pickaxe\", \"guard me\", \"give me your cobblestone\""
        };
        for (String l : lines) ctx.getSource().sendSuccess(() -> Component.literal(l).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int come(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerStory story = story(ctx, player);
        if (!story.met) return fail(ctx, "You haven't met Solace yet.");
        if (!story.solaceShouldExist()) return fail(ctx, "Solace is gone.");
        if (story.respawnAtTick >= 0) return fail(ctx, "Solace isn't ready to come back yet...");
        SolaceEntity solace = SolaceManager.summon(player, story);
        return solace == null ? fail(ctx, "Solace couldn't come.") : 1;
    }

    private static int pockets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerStory story = story(ctx, player);
        SolaceEntity solace = SolaceManager.find(ctx.getSource().getServer(), story);
        if (solace == null || solace.level() != player.level() || solace.distanceTo(player) > 8) return fail(ctx, "Solace needs to be next to you.");
        solace.openInventory(player);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerStory s = story(ctx, player);
        MinecraftServer server = ctx.getSource().getServer();
        StoryData data = StoryData.get(server);
        msg(ctx, "AI brain: " + AiService.describe());
        msg(ctx, "AI requests today: " + data.requestsToday + " (limit " + com.solacemod.solace.config.SolaceConfig.DAILY_REQUEST_LIMIT.get() + ")");
        if (!s.met) return msg(ctx, "You haven't met Solace yet.");
        msg(ctx, "Story: act " + s.act + ", " + Math.round(s.playMinutes()) + " minutes together, pages found " + s.fragmentsFound.size() + "/" + StoryText.FRAGMENT_COUNT
                + (s.ending.name().equals("NONE") ? "" : ", ending: " + s.ending));
        msg(ctx, "Solace's strength (Tether): " + (int) s.tether + "/" + (int) com.solacemod.solace.story.Bond.maxTether(s)
                + ". Your debt to Solace: " + (int) s.debt + (s.request != null ? ". It asked you: \"" + s.request.text + "\"" : ""));
        msg(ctx, "Trust: " + s.trust + ". Things Solace remembers about you: " + s.facts.size()
                + ". Endings discovered: " + GlobalMemory.distinctEndings(player.getUUID()) + "/4");
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        String problem = AiService.setupProblem();
        if (problem != null && !AiService.configured()) {
            ctx.getSource().sendSystemMessage(SolaceBrain.setupMessage("Not set up: " + problem));
            return 0;
        }
        msg(ctx, "Testing " + AiService.describe() + "...");
        long start = System.currentTimeMillis();
        LlmRequest req = new LlmRequest("You are a test. Reply with exactly: {\"say\":\"I can hear you.\"}",
                List.of(ChatMessage.user("Test")), 200, 0.0, true);
        AiService.complete(req).whenComplete((text, err) -> server.execute(() -> {
            long ms = System.currentTimeMillis() - start;
            if (err != null) src.sendFailure(Component.literal("Failed: " + AiService.hintFor(err)));
            else src.sendSuccess(() -> Component.literal("Success in " + ms + " ms. Reply: " + (text.length() > 120 ? text.substring(0, 120) + "..." : text))
                    .withStyle(ChatFormatting.GREEN), false);
        }));
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerStory s = story(ctx, player);
        s.facts.clear();
        s.history.clear();
        s.summary = "";
        s.recentEvents.clear();
        GlobalMemory.forget(player.getUUID());
        return msg(ctx, "Solace's memories of you are gone. (It still knows your name. You're standing right there.)");
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        StoryData data = StoryData.get(server);
        PlayerStory s = data.get(player.getUUID());
        SolaceEntity solace = SolaceManager.find(server, s);
        if (solace != null) {
            if (solace.isHollow()) solace.revertFromHollow();
            solace.discard();
        }
        data.reset(player.getUUID());
        StoryDirector.forgetSession(player.getUUID());
        StoryDirector.onLogin(player, data.get(player.getUUID()));
        return msg(ctx, "Your story has been reset. Solace will find you again soon.");
    }
}
