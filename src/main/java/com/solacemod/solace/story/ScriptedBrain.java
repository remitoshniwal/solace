package com.solacemod.solace.story;

import com.google.gson.JsonObject;
import com.solacemod.solace.ai.SolaceReply;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Solace without an AI: picks pre-written lines and understands simple commands like
 * "follow me", "stay", "mine 10 iron", "give me cobblestone", "build a shelter", "craft torches".
 */
public final class ScriptedBrain {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,3})\\b");
    private static final Pattern MINE = Pattern.compile("\\b(?:mine|dig|get|gather|collect|chop|find)\\s+(?:me\\s+)?(?:some\\s+|\\d+\\s+)?([a-z_ ]+?)(?:\\s+(?:for|please|now)\\b|$)");
    private static final Pattern GIVE = Pattern.compile("\\bgive\\s+(?:me\\s+)?(?:your\\s+|the\\s+|some\\s+|\\d+\\s+)?([a-z_ ]+?)(?:\\s+(?:please|now)\\b|$)");
    private static final Pattern CRAFT = Pattern.compile("\\b(?:craft|make)\\s+(?:me\\s+)?(?:a\\s+|an\\s+|some\\s+|\\d+\\s+)?([a-z_ ]+?)(?:\\s+(?:please|now)\\b|$)");
    private static final Pattern BUILD = Pattern.compile("\\bbuild\\s+(?:me\\s+)?(?:a\\s+|an\\s+|some\\s+)?([a-z]+)");
    private static final Pattern ATTACK = Pattern.compile("\\b(?:kill|attack|fight|hunt)\\s+(?:the\\s+|that\\s+|those\\s+|some\\s+|\\d+\\s+)?([a-z_ ]+?)(?:s)?(?:\\s+(?:please|now)\\b|$)");
    private static final Pattern MARK = Pattern.compile("\\b(?:remember this (?:place|spot) as|mark (?:this|here) as|mark)\\s+([a-z0-9_ ]+)");
    private static final Pattern GOTO = Pattern.compile("\\bgo (?:to|back to)\\s+(?:the\\s+|my\\s+)?([a-z0-9_ ]+)");

    private ScriptedBrain() {
    }

    public static String pick(List<String> lines) {
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    public static String chatterLine(PlayerStory story) {
        return pick(StoryText.CHATTER.get(Math.max(0, Math.min(3, story.act - 1))));
    }

    /** A reply to something the player said. */
    public static SolaceReply reply(PlayerStory story, String text) {
        List<JsonObject> actions = parseCommands(text);
        int a = Math.max(0, Math.min(3, story.act - 1));
        if (story.ending == Ending.TOGETHER) a = 0;
        String say;
        if (!actions.isEmpty()) say = pick(a >= 2 ? StoryText.OK_LATE : StoryText.OK);
        else say = pick(StoryText.REPLIES.get(a));
        return SolaceReply.of(say, a >= 3 ? "sad" : "calm", actions);
    }

    /** Turns a sentence into actions using simple keyword rules. */
    public static List<JsonObject> parseCommands(String raw) {
        String t = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_ ]", " ").replaceAll("\\s+", " ").trim();
        List<JsonObject> out = new ArrayList<>();
        int count = 0;
        Matcher num = NUMBER.matcher(t);
        if (num.find()) count = Integer.parseInt(num.group(1));

        if (t.matches(".*\\b(stop|cancel|nevermind|never mind)\\b.*") && !t.contains("stop following") && !t.contains("stop guarding")) {
            out.add(action("stop"));
            return out;
        }
        if (t.contains("stop guarding") || t.contains("stop fighting") || t.contains("stop protecting")) {
            JsonObject g = action("guard");
            g.addProperty("on", false);
            out.add(g);
        } else if (t.matches(".*\\b(guard|protect|defend)\\b.*")) {
            JsonObject g = action("guard");
            g.addProperty("on", true);
            out.add(g);
        }
        if (t.matches(".*\\b(stay|wait|stop following|stand still|don t move)\\b.*")) out.add(action("stay"));
        else if (t.matches(".*\\b(follow|come with|with me)\\b.*")) out.add(action("follow"));
        else if (t.matches(".*\\b(come here|come back|over here|come)\\b.*")) out.add(action("come"));
        else if (t.matches(".*\\b(wander|explore|go play)\\b.*")) out.add(action("wander"));

        Matcher m;
        if ((m = MARK.matcher(t)).find()) {
            JsonObject a = action("mark");
            a.addProperty("name", m.group(1).trim());
            out.add(a);
        } else if (t.contains("go home")) {
            JsonObject a = action("goto");
            a.addProperty("name", "home");
            out.add(a);
        } else if ((m = GOTO.matcher(t)).find()) {
            JsonObject a = action("goto");
            a.addProperty("name", m.group(1).trim());
            out.add(a);
        }

        boolean lightUp = t.matches(".*\\blight\\b.*\\bup\\b.*") || t.matches(".*\\b(place|put|use) (some |a few )?torch(es)?\\b.*");
        if (lightUp || (t.matches(".*\\btorch(es)?\\b.*") && !t.matches(".*\\b(craft|make|give)\\b.*"))) {
            out.add(action("torches"));
        } else if ((m = BUILD.matcher(t)).find()) {
            JsonObject a = action("build");
            a.addProperty("structure", m.group(1));
            out.add(a);
        } else if ((m = CRAFT.matcher(t)).find()) {
            JsonObject a = action("craft");
            a.addProperty("item", m.group(1).trim().replace(' ', '_'));
            a.addProperty("count", Math.max(1, count));
            out.add(a);
        } else if ((m = GIVE.matcher(t)).find()) {
            JsonObject a = action("give");
            String item = m.group(1).trim();
            boolean all = item.equals("everything") || item.equals("it all") || item.equals("all");
            a.addProperty("item", all ? "all" : item.replace(' ', '_'));
            a.addProperty("count", count > 0 ? count : all ? 9999 : 64);
            out.add(a);
        } else if (t.matches(".*\\b(pick up|pick it up|grab the|collect items|loot)\\b.*")) {
            out.add(action("collect"));
        } else if ((m = ATTACK.matcher(t)).find()) {
            JsonObject a = action("attack");
            String target = m.group(1).trim();
            a.addProperty("target", target.matches("(monster|mob|enem(y|ie)|hostile)") ? "hostile" : target.replace(' ', '_'));
            boolean plural = t.contains(target + "s") || t.contains("those") || t.contains("all");
            a.addProperty("count", count > 0 ? count : plural ? 5 : 1);
            out.add(a);
        } else if ((m = MINE.matcher(t)).find()) {
            String block = m.group(1).trim();
            if (t.contains("chop") || block.equals("wood")) block = "logs";
            if (!block.isEmpty() && !block.equals("items") && !block.equals("it")) {
                JsonObject a = action("mine");
                a.addProperty("block", block.replace(' ', '_'));
                a.addProperty("count", count > 0 ? count : 8);
                out.add(a);
            }
        }
        return out;
    }

    private static JsonObject action(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }
}
