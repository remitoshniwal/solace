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
import com.solacemod.solace.story.PlaytestLog;
import com.solacemod.solace.story.SolaceBrain;
import com.solacemod.solace.story.SolaceManager;
import com.solacemod.solace.story.StoryData;
import com.solacemod.solace.story.StoryDirector;
import com.solacemod.solace.story.StoryText;
import com.solacemod.solace.util.ModSounds;
import com.solacemod.solace.util.Sounds;
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
                .then(Commands.literal("help").executes(SolaceCommand::help))
                .then(Commands.literal("say").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            SolaceBrain.talk(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "message"));
                            return 1;
                        })))
                .then(Commands.literal("setup").executes(ctx -> {
                    // First what this server allows for players' own AI, so the screen can say so.
                    com.solacemod.solace.network.ModNetwork.sendServerAi(ctx.getSource().getPlayerOrException());
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ctx.getSource().getPlayerOrException(),
                            new com.solacemod.solace.network.ModNetwork.OpenSetupPayload(false));
                    return 1;
                }))
                .then(Commands.literal("note").then(Commands.argument("text", StringArgumentType.greedyString()).executes(SolaceCommand::note)))
                .then(Commands.literal("come").executes(SolaceCommand::come))
                .then(Commands.literal("status").executes(SolaceCommand::status))
                .then(Commands.literal("pockets").executes(SolaceCommand::pockets))
                // Operators and the single player owner only: each test is a real request on the server owner's key, outside the daily limit.
                .then(Commands.literal("test").requires(SolaceCommand::ownerOrOperator).executes(SolaceCommand::test))
                .then(Commands.literal("choose").then(Commands.argument("option", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            // Only what's on offer right now: Tab must never list an ending the player hasn't been shown.
                            if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
                                PlayerStory s = StoryData.get(ctx.getSource().getServer()).get(p.getUUID());
                                for (String o : StoryDirector.choicesNow(s)) b.suggest(o);
                                for (String o : com.solacemod.solace.story.Afterward.choicesNow(s)) b.suggest(o);
                            }
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String option = StringArgumentType.getString(ctx, "option");
                            ServerPlayer chooser = ctx.getSource().getPlayerOrException();
                            String err = com.solacemod.solace.story.Afterward.isChoice(option)
                                    ? com.solacemod.solace.story.Afterward.choose(chooser, option)
                                    : StoryDirector.choose(chooser, option);
                            if (err != null) ctx.getSource().sendFailure(Component.literal(err));
                            return err == null ? 1 : 0;
                        })))
                .then(Commands.literal("join").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    PlayerStory s = story(ctx, p);
                    if (!"opt_in".equals(com.solacemod.solace.config.SolaceConfig.WHO_GETS_SOLACE.get())) {
                        return msg(ctx, "Everyone gets a Solace on this server. " + (s.met ? "Yours is already here." : "Yours will find you soon."));
                    }
                    if (s.joined) return msg(ctx, "You already joined. " + (s.met ? "Solace is with you." : "It will find you soon."));
                    s.joined = true;
                    return msg(ctx, "You joined. In about 30 seconds, something will find you.");
                }))
                .then(Commands.literal("leave")
                        .executes(ctx -> msg(ctx, "This sends your Solace away (your story is kept, /solace join brings it back). Type /solace leave confirm"))
                        .then(Commands.literal("confirm").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (!"opt_in".equals(com.solacemod.solace.config.SolaceConfig.WHO_GETS_SOLACE.get())) {
                                return fail(ctx, "On this server everyone has a Solace. The server owner can change that in Solace setup.");
                            }
                            PlayerStory s = story(ctx, p);
                            s.joined = false;
                            SolaceManager.remove(ctx.getSource().getServer(), s);
                            return msg(ctx, "Solace is gone. For now.");
                        })))
                .then(Commands.literal("forget")
                        .executes(ctx -> msg(ctx, "This makes Solace forget everything it knows about you, in every world. Type /solace forget confirm"))
                        .then(Commands.literal("confirm").executes(SolaceCommand::forget)))
                .then(Commands.literal("reset")
                        .executes(ctx -> msg(ctx, StoryText.resetWarning(story(ctx, ctx.getSource().getPlayerOrException()).ending)))
                        .then(Commands.literal("confirm").executes(SolaceCommand::reset)))
                .then(Commands.literal("admin").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("usage").executes(SolaceCommand::usage))
                        .then(Commands.literal("playtest")
                                .then(Commands.literal("on").executes(ctx -> playtest(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> playtest(ctx, false))))
                        .then(Commands.literal("chapter").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("chapter", IntegerArgumentType.integer(1, 8)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    PlayerStory story = story(ctx, p);
                                    if (!story.met) return fail(ctx, p.getGameProfile().getName() + " hasn't met Solace yet (it appears 30 seconds after joining).");
                                    StoryDirector.setChapter(p, story, IntegerArgumentType.getInteger(ctx, "chapter"));
                                    return msg(ctx, "Moved " + p.getGameProfile().getName() + " to chapter " + story.chapter + ".");
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
                                })
                                        // "ground": dropped behind the player like the story does, so the page hints can be tested.
                                        .then(Commands.literal("ground").executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            StoryDirector.deliverFragment(p, story(ctx, p), IntegerArgumentType.getInteger(ctx, "page") - 1, StoryDirector.Delivery.GROUND);
                                            return msg(ctx, "Page dropped behind them.");
                                        })))))
                        .then(Commands.literal("power").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("power", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String s : com.solacemod.solace.story.Powers.COSTS.keySet()) b.suggest(s);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            // Uses a power as if Solace chose to (it costs Tether and adds debt), for testing the price.
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            String power = StringArgumentType.getString(ctx, "power");
                                            if (!com.solacemod.solace.story.Powers.isPower(power)) return fail(ctx, "Unknown power. Try: heal, feed, weather, smite, find...");
                                            PlayerStory s = story(ctx, p);
                                            SolaceEntity solace = SolaceManager.find(ctx.getSource().getServer(), s);
                                            String result = com.solacemod.solace.story.Powers.use(p, solace, s, new com.google.gson.JsonObject(), power);
                                            return msg(ctx, result == null ? "Done." : result);
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
                        .then(Commands.literal("negative").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            SolaceEntity solace = SolaceManager.find(ctx.getSource().getServer(), story(ctx, p));
                            if (solace == null) return fail(ctx, "Solace isn't around.");
                            solace.goNegative(20 * 10);
                            return msg(ctx, "Solace shows his negative side for 10 seconds.");
                        })))
                        .then(Commands.literal("sound").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String s : ModSounds.NAMES) b.suggest(s);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (!ModSounds.NAMES.contains(name)) return fail(ctx, "Unknown sound. Options: " + String.join(" ", ModSounds.NAMES));
                                            // Plays it once, right where the player is, so it can be listened to.
                                            Sounds.playFor(p, p.position(), Sounds.get("solace:" + name), 1.0F, 1.0F);
                                            return msg(ctx, "Playing solace:" + name + ".");
                                        }))))
                        .then(Commands.literal("together").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("moment", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String o : com.solacemod.solace.story.Together.ADMIN_OPTIONS) b.suggest(o);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> together(ctx, ""))
                                        .then(Commands.argument("detail", StringArgumentType.word())
                                                .executes(ctx -> together(ctx, StringArgumentType.getString(ctx, "detail")))))))
                        .then(Commands.literal("ending").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("ending", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String o : List.of("forever", "release", "erase", "together", "collected")) b.suggest(o);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            PlayerStory s = story(ctx, p);
                                            com.solacemod.solace.story.Ending e = com.solacemod.solace.story.Ending.parse(StringArgumentType.getString(ctx, "ending").toUpperCase(java.util.Locale.ROOT));
                                            if (e == com.solacemod.solace.story.Ending.NONE) return fail(ctx, "Pick an ending: forever release erase together collected");
                                            if (!s.met) return fail(ctx, p.getGameProfile().getName() + " hasn't met Solace yet.");
                                            StoryDirector.adminEnding(p, s, e);
                                            return msg(ctx, "Jumped to the ending " + e.name() + ".");
                                        }))))
                        .then(Commands.literal("memory").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String o : List.of("show", "ending", "all", "clear")) b.suggest(o);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> memory(ctx, ""))
                                        .then(Commands.argument("detail", StringArgumentType.word())
                                                .executes(ctx -> memory(ctx, StringArgumentType.getString(ctx, "detail")))))))
                        .then(Commands.literal("afterward").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("moment", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String o : com.solacemod.solace.story.Afterward.ADMIN_OPTIONS) b.suggest(o);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            try {
                                                return msg(ctx, com.solacemod.solace.story.Afterward.admin(p, story(ctx, p), StringArgumentType.getString(ctx, "moment")));
                                            } catch (IllegalStateException e) {
                                                return fail(ctx, e.getMessage());
                                            }
                                        }))))
                        .then(Commands.literal("date").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String o : com.solacemod.solace.story.RealDates.ADMIN_OPTIONS) b.suggest(o);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> date(ctx, ""))
                                        .then(Commands.argument("detail", StringArgumentType.greedyString())
                                                .executes(ctx -> date(ctx, StringArgumentType.getString(ctx, "detail")))))))
                        .then(Commands.literal("build").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (String o : com.solacemod.solace.story.Builds.ADMIN_OPTIONS) b.suggest(o);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> build(ctx, ""))
                                        .then(Commands.argument("detail", StringArgumentType.greedyString())
                                                .executes(ctx -> build(ctx, StringArgumentType.getString(ctx, "detail")))))))
                        .then(Commands.literal("question").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            PlayerStory s = story(ctx, p);
                            StoryDirector.setChapter(p, s, 8);
                            for (int i = 0; i < StoryText.FRAGMENT_COUNT; i++) s.fragmentsFound.add(i);
                            return msg(ctx, "Solace will ask its question in about a minute.");
                        })))));
    }

    /** /solace admin date: fakes a player's clock, to test the real-date moments (story/RealDates.java). */
    private static int date(CommandContext<CommandSourceStack> ctx, String detail) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        try {
            return msg(ctx, com.solacemod.solace.story.RealDates.admin(p, story(ctx, p), StringArgumentType.getString(ctx, "what"), detail));
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    /** /solace admin build: what Solace noticed you build, and pretend builds for testing (story/Builds.java). */
    private static int build(CommandContext<CommandSourceStack> ctx, String detail) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        try {
            return msg(ctx, com.solacemod.solace.story.Builds.admin(p, story(ctx, p), StringArgumentType.getString(ctx, "what"), detail));
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    /** Server operators, the console, or whoever owns this single player world (not friends joining it over LAN). */
    private static boolean ownerOrOperator(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        ServerPlayer p = src.getPlayer();
        return p != null && src.getServer().isSingleplayerOwner(p.getGameProfile());
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
        boolean owner = ownerOrOperator(ctx.getSource());
        List<String> lines = new java.util.ArrayList<>(List.of(
                "Just chat near Solace (or say its name) to talk to it.",
                "  /solace say <message>   talk to Solace from anywhere",
                "  /solace come            call Solace to you",
                "  /solace pockets         open its inventory (or right-click it with an empty hand)",
                "  /solace status          your story so far, and the AI",
                "  /solace setup           settings: AI, voice, scares, sounds"));
        if (owner) lines.add("  /solace test            check that each AI brain answers");
        lines.addAll(List.of(
                "  /solace note <text>     playtesting: save a note about this moment",
                "  /solace forget          make Solace forget you (in every world)",
                "  /solace reset           start the story in this world again"));
        if ("opt_in".equals(com.solacemod.solace.config.SolaceConfig.WHO_GETS_SOLACE.get())) lines.add("  /solace join / leave    get your own Solace, or send it away");
        if (ctx.getSource().hasPermission(2)) lines.add("  /solace admin ...       testing tools for operators (spoilers!)");
        lines.add("Try asking: \"follow me\", \"mine 10 iron\", \"build a shelter\", \"craft a stone pickaxe\", \"guard me\"");
        line(ctx, "Solace commands", ChatFormatting.AQUA);
        for (String l : lines) line(ctx, l, ChatFormatting.GRAY);
        return 1;
    }

    private static int note(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        java.nio.file.Path f = PlaytestLog.note(story(ctx, player), player, StringArgumentType.getString(ctx, "text"));
        if (f == null) return fail(ctx, "Couldn't save the note.");
        ctx.getSource().sendSuccess(() -> Component.literal("Noted.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** /solace admin memory: shows, fakes or clears the endings Solace remembers across worlds. */
    private static int memory(CommandContext<CommandSourceStack> ctx, String detail) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        try {
            return msg(ctx, com.solacemod.solace.story.Afterward.adminMemory(p, StringArgumentType.getString(ctx, "what"), detail));
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    /** /solace admin together: tries one multiplayer moment now (with a stand-in when nobody else is online). */
    private static int together(CommandContext<CommandSourceStack> ctx, String detail) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        try {
            return msg(ctx, com.solacemod.solace.story.Together.admin(p, story(ctx, p), StringArgumentType.getString(ctx, "moment"), detail));
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    /** Who used how many AI requests today (all paid from the server owner's key). */
    private static int usage(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        StoryData data = StoryData.get(server);
        String today = StoryData.today();
        int share = AiService.perPlayerLimit(server);
        msg(ctx, "AI requests today: " + data.budget.totalToday(today) + " (server limit " + com.solacemod.solace.config.SolaceConfig.DAILY_REQUEST_LIMIT.get()
                + (share > 0 ? ", each player " + share : ", no per-player limit here") + ")");
        java.util.Map<String, Integer> players = data.budget.playersToday(today);
        // Players on their own AI pay for themselves: their requests aren't counted above. Names only.
        java.util.List<String> ownNames = new java.util.ArrayList<>();
        for (java.util.UUID id : AiService.ownUsers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) ownNames.add(p.getGameProfile().getName());
        }
        if (!ownNames.isEmpty()) msg(ctx, "  Using their own AI (not counted): " + String.join(", ", ownNames));
        if (players.isEmpty()) return msg(ctx, "  Nobody has used any yet.");
        players.forEach((id, used) -> {
            String name = id;
            try {
                PlayerStory s = data.getIfPresent(java.util.UUID.fromString(id));
                if (s != null && !s.playerName.isEmpty()) name = s.playerName;
            } catch (IllegalArgumentException ignored) {
                // not a player id: show it as it is
            }
            msg(ctx, "  " + name + ": " + used);
        });
        return 1;
    }

    private static int playtest(CommandContext<CommandSourceStack> ctx, boolean on) {
        com.solacemod.solace.config.SolaceConfig.PLAYTEST_LOG.set(on);
        com.solacemod.solace.config.SolaceConfig.SPEC.save();
        java.nio.file.Path f = PlaytestLog.file();
        String where = f == null ? "" : " File: " + net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().relativize(f);
        return msg(ctx, on ? "Playtest log on. Everything that happens is written down." + where
                : "Playtest log off. Notes from /solace note are still saved." + where);
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
        boolean over = com.solacemod.solace.story.Afterward.gone(s);
        line(ctx, "Solace status", ChatFormatting.AQUA);
        // The story first: it's what players want to know.
        if (!s.met) line(ctx, "You haven't met Solace yet.", ChatFormatting.WHITE);
        else if (s.companion) line(ctx, "No story this time: just Solace. (/solace reset confirm to change your mind.)", ChatFormatting.WHITE);
        else if (!com.solacemod.solace.config.SolaceConfig.STORY_ENABLED.get()) line(ctx, "The story is off on this server: Solace is just a companion.", ChatFormatting.WHITE);
        else {
            if (over) line(ctx, StoryText.STATUS_OVER, ChatFormatting.WHITE);
            line(ctx, "Chapter " + s.chapter + " of 8, " + minutes(Math.round(s.playMinutes())) + " together, pages found " + s.fragmentsFound.size() + "/" + StoryText.FRAGMENT_COUNT
                    + (s.ending == com.solacemod.solace.story.Ending.NONE ? "" : ". Ending: " + StoryText.endingTitle(s.ending)), ChatFormatting.WHITE);
        }
        if (s.met && !over && !s.companion) {
            line(ctx, "Its strength (Tether): " + (int) s.tether + "/" + (int) com.solacemod.solace.story.Bond.maxTether(s)
                    + ". What you owe it (Debt): " + (int) s.debt + (s.request != null ? ". It asked you: \"" + s.request.text + "\"" : ""), ChatFormatting.GRAY);
        }
        if (s.met) line(ctx, "Trust: " + s.trust + ". Things it remembers about you: " + s.facts.size()
                + ". Endings discovered: " + GlobalMemory.distinctEndings(player.getUUID()) + "/5", ChatFormatting.GRAY);
        // Then the AI. The brain details are for whoever sets it up.
        int share = AiService.perPlayerLimit(server);
        String today = StoryData.today();
        int limit = com.solacemod.solace.config.SolaceConfig.DAILY_REQUEST_LIMIT.get();
        boolean owner = ownerOrOperator(ctx.getSource());
        String lend = com.solacemod.solace.config.SolaceConfig.LEND_AI.get();
        if (AiService.hasOwn(player.getUUID())) {
            // Their own brains: they may see these (names and models only, never the key).
            line(ctx, "AI: your own. " + (lend.equals("always") && AiService.configured() ? "If it fails, the server owner's AI takes over."
                    : "The server owner's AI isn't used for you."), ChatFormatting.GRAY);
            for (String l : AiService.ownStatus(player.getUUID())) line(ctx, "  " + l, ChatFormatting.DARK_GRAY);
            return 1;
        }
        if (lend.equals("never") && !owner) {
            line(ctx, "AI: this server doesn't share its AI, so Solace only follows simple orders"
                    + (com.solacemod.solace.config.SolaceConfig.PLAYER_AI.get() ? " (unless you use your own: /solace setup)." : "."), ChatFormatting.GRAY);
            return 1;
        }
        if (!AiService.configured()) {
            // The long "how to get a key" text is for whoever can set it up.
            line(ctx, owner ? "AI: not set up yet, so Solace can't talk. Open /solace setup to give it a brain." : "AI: not set up on this server, so Solace can't talk.", ChatFormatting.GRAY);
            return 1;
        }
        line(ctx, "AI: " + AiService.describe() + ". Requests today: " + data.budget.totalToday(today) + (limit > 0 ? " of " + limit : " (no daily limit)")
                + (share > 0 ? " (yours: " + data.budget.usedToday(player.getUUID().toString(), today) + " of " + share + ")" : ""), ChatFormatting.GRAY);
        if (owner) for (String l : AiService.brainStatus()) line(ctx, "  " + l, ChatFormatting.DARK_GRAY);
        return 1;
    }

    private static String minutes(long n) {
        return n + (n == 1 ? " minute" : " minutes");
    }

    private static void line(CommandContext<CommandSourceStack> ctx, String text, ChatFormatting color) {
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    private static int test(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        String problem = AiService.setupProblem();
        if (problem != null && !AiService.configured()) {
            ctx.getSource().sendSystemMessage(SolaceBrain.setupMessage("Not set up: " + problem));
            return 0;
        }
        LlmRequest req = new LlmRequest("You are a test. Reply with exactly: {\"say\":\"I can hear you.\"}",
                List.of(ChatMessage.user("Test")), 200, 0.0, true);
        // Test every brain on its own, so you can see which ones work.
        int tested = 0;
        for (int slot = 1; slot <= com.solacemod.solace.config.SolaceConfig.EXTRA_BRAINS + 1; slot++) {
            com.solacemod.solace.ai.ProviderSettings s = AiService.settingsFor(slot);
            if (s == null) continue;
            final int n = slot;
            if (s.problem() != null) {
                src.sendFailure(Component.literal("Brain " + n + " (" + s.name() + "): " + s.problem()));
                continue;
            }
            tested++;
            msg(ctx, "Testing brain " + n + ": " + s + "...");
            long start = System.currentTimeMillis();
            com.solacemod.solace.ai.LlmProvider.create(s).complete(req).whenComplete((text, err) -> server.execute(() -> {
                long ms = System.currentTimeMillis() - start;
                if (err != null) src.sendFailure(Component.literal("Brain " + n + " failed: " + AiService.hintFor(err)));
                else src.sendSuccess(() -> Component.literal("Brain " + n + " works (" + ms + " ms).").withStyle(ChatFormatting.GREEN), false);
            }));
        }
        return tested > 0 ? 1 : 0;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerStory s = story(ctx, player);
        s.facts.clear();
        s.history.clear();
        s.summary = "";
        s.recentEvents.clear();
        s.remembered = com.solacemod.solace.story.Ending.NONE;
        s.rememberedAll = false;
        StoryData.get(ctx.getSource().getServer()).endedHere.remove(player.getUUID());
        GlobalMemory.forget(player.getUUID());
        return msg(ctx, "Solace's memories of you are gone. (It still knows your name. You're standing right there.)");
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        StoryData data = StoryData.get(server);
        PlayerStory s = data.get(player.getUUID());
        SolaceEntity solace = SolaceManager.find(server, s);
        if (solace != null) com.solacemod.solace.story.Afterward.onReset(player, s);
        if (solace != null) {
            if (solace.isHollow()) solace.revertFromHollow();
            solace.discard();
        }
        com.solacemod.solace.story.StoryAdvancements.revokeStory(player);
        data.reset(player.getUUID());
        StoryDirector.forgetSession(player.getUUID());
        StoryDirector.onLogin(player, data.get(player.getUUID()));
        return msg(ctx, "Your story has been reset. Solace will find you again soon.");
    }
}
