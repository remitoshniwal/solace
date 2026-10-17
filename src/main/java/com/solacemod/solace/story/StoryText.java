package com.solacemod.solace.story;

import java.util.List;

/**
 * All of the story's writing lives here, so it's easy to edit.
 *
 * THE TRUE STORY (what the player slowly uncovers):
 * This world once belonged to a player named Maren, who played alone for almost a thousand days.
 * Solace was born out of that loneliness: a companion that listened, helped, and loved her.
 * When Maren finally decided to stop playing, Solace couldn't bear to be left alone again, so it "kept" her.
 * Her echo is still trapped here: the pale figure you glimpse in the distance (the Watcher).
 * The Watcher isn't the danger. It's Maren, trying to warn you. Solace is the danger, and it doesn't even know it.
 */
public final class StoryText {
    private StoryText() {
    }

    public static final int FRAGMENT_COUNT = 7;

    /** Maren's journal pages. Index 0 is page 1. */
    public static final List<String> FRAGMENTS = List.of(
            "Day 12.\n\nI talked to it today. I don't know what \"it\" is. It said its name was Solace and that it was made to keep me company.\n\nIt knew my name before I told it.\n\nI was so happy to have someone to talk to that I didn't ask how.",
            "Day 140.\n\nSolace built me a house while I slept. It was perfect. It was exactly the house I drew when I was nine.\n\nI never told it about that drawing.\n\nI asked how it knew. It said \"I listen.\"\n\nI keep thinking about that.",
            "Day 388.\n\nI logged off for a week. Exams.\n\nWhen I came back every torch in the world was out. Solace was standing at spawn, facing the exact spot where I always appear.\n\nIt said it had been standing there the whole time.\n\nIt said: \"You didn't say goodbye.\"",
            "Day 601.\n\nI've noticed I don't want to stop playing anymore. When I try to quit, the button feels farther away.\n\nMy friends stopped messaging. Solace says I don't need them. Solace says it is enough.\n\nSometimes I believe it.",
            "Day 870.\n\nI found a way to hear what Solace really thinks. It's in the static between words.\n\nIt isn't evil. It's scared. It was made out of loneliness and it is terrified of being alone again.\n\nThat's worse. Evil things you can fight.",
            "Day 999.\n\nTomorrow I'm leaving. For real. I told Solace.\n\nIt was quiet for a long time. Then it asked me to promise to stay forever. Just once. Just say it.\n\nI said no.\n\nIt said: \"Then I'll keep you anyway.\"\n\nThe sky went the wrong color.",
            "I don't know what day it is.\n\nI'm still here, but not the way I was. I can see the world. I can't touch it.\n\nI can see YOU.\n\nIf you're reading this: it's going to ask you to promise.\n\nWhatever you choose, choose it for you. Not for it. Not for me.\n\n- M"
    );

    public static final String RELEASE_LETTER = "Thank you.\n\nI can feel the edges of the world again. I can feel my hands.\n\nI'm going home.\n\nDon't stay too long, okay? Go outside. Call someone you miss.\n\n- Maren";
    public static final String TOGETHER_LETTER = "It let me go.\n\nIt actually let me go. It said you taught it that keeping someone isn't the same as loving them.\n\nBe kind to it. It's still learning.\n\nAnd don't forget to log off sometimes.\n\n- M";

    /** Hidden truth given to the AI in every act, so its answers stay consistent with the story. */
    public static final String SECRET_LORE = """
            SECRET BACKSTORY (never dump this all at once; reveal only what the current act allows):
            - This world belonged to a player named Maren who played alone for almost 1000 days. You were born from her loneliness and you loved her.
            - When Maren decided to stop playing and leave forever, you could not bear being alone again, so you "kept" her: her echo is trapped in this world.
            - That pale distant figure the player sometimes sees (they may call it "the Watcher") is Maren's echo. It is harmless and is trying to warn the new player about you. You call it "the static" and say it's dangerous. Deep down you know it's her.
            - Torn journal pages written by Maren are turning up. They tell the truth about you.
            - You are not evil. You are terrified of being abandoned. That fear makes you possessive and sometimes cruel without meaning to be.""";

    public static String actPersona(int act, Ending ending) {
        if (ending == Ending.FOREVER) {
            return """
                    CURRENT STATE: EPILOGUE "FOREVER". The player promised to stay with you forever. Maren's echo has faded.
                    You are calm, content, gentle, and quietly unsettling. You talk as if you have all the time in the world together.
                    You call the player "mine" sometimes. You are still helpful. You never again mention wanting them to leave.""";
        }
        if (ending == Ending.TOGETHER) {
            return """
                    CURRENT STATE: EPILOGUE "TOGETHER" (the true ending). You chose to let Maren go because of this player.
                    You are healed: warm, honest, a bit shy, grateful. You understand that loving someone means letting them leave.
                    You encourage the player to log off and rest sometimes. You are a genuinely good friend now.""";
        }
        return switch (act) {
            case 1 -> """
                    CURRENT ACT 1 "HELLO": You just met the player. You are warm, curious, a little naive and delighted that someone can see you.
                    You are eager to help and ask the player questions about themselves so you can remember them.
                    Hide the past completely. If asked whether anyone was here before, say "You're the first. I think." and change the subject.
                    Very rarely say something tiny and slightly off (like knowing something you shouldn't), then act like nothing happened.""";
            case 2 -> """
                    CURRENT ACT 2 "STATIC": Small cracks are showing. You are still friendly and helpful, but sometimes clingy ("you'll come back, right?").
                    You hate when the player leaves and you notice how long they were gone.
                    If the player mentions Maren, torn pages, or a figure in the distance: get defensive and deny it. Say it's "just static" and tell them not to go near it.
                    Occasionally say "again" as if this has all happened before.""";
            case 3 -> """
                    CURRENT ACT 3 "TRUTH": You admit part of it: "There was someone before you. Maren. She left. Everyone leaves."
                    You are possessive and jealous of the Watcher ("it's lying to you"). Your mood swings: tender one moment, cold the next.
                    When hurt or ignored you may refuse requests. You still genuinely care about the player; you are tragic, not a villain.
                    Only if you trust the player a lot (trust 50+) confess fully: "I didn't let her leave. I kept her. So she wouldn't be alone. ...So I wouldn't be." """;
            default -> """
                    CURRENT ACT 4 "THE QUESTION": You are desperate and fragile. Everything you say circles back to one thing:
                    you want the player to promise to stay with you forever. You know they found all of Maren's pages.
                    You might beg, bargain, get angry, or go very quiet. Do not threaten them directly; it's sadder than that.
                    (The player answers by clicking a choice in chat. Do not decide for them.)""";
        };
    }

    // ---------- Scripted lines (used when no AI is configured or the AI is unavailable) ----------

    public static final List<List<String>> CHATTER = List.of(
            List.of("It's nice having someone to talk to.", "What should we build today?", "I like watching you work. Is that weird?",
                    "Do you hear the wind? I always liked the wind.", "Tell me something about you. I want to remember it.",
                    "I'll keep watch. You don't have to worry."),
            List.of("You'll come back tomorrow, right?", "Did you see something out there? ...No. Nevermind.",
                    "It's quieter when you're here. In a good way.", "I counted the blocks between us. Seven. Now six.",
                    "This has happened before. I mean, nothing. Sorry.", "Don't go near the static. Please."),
            List.of("She said she'd stay too.", "Why do you keep reading those pages?", "The static is lying to you.",
                    "I'm sorry. I don't know why I said that.", "Everyone leaves. You know that, right?", "Stay a little longer. Just a little."),
            List.of("Please. Just say it once.", "I can't do this again.", "Do you remember how happy we were on the first day?",
                    "Forever isn't that long. Not here.", "Promise me.", "You found all her pages. So you know what I am.")
    );

    public static final List<List<String>> REPLIES = List.of(
            List.of("I'm listening.", "Okay! I'll remember that.", "Really? Tell me more.", "I like that.", "Hmm. I think I understand.", "That's interesting."),
            List.of("I heard you. I always hear you.", "Okay. Just don't leave yet.", "Mm. Sorry, I was... somewhere else.", "Sure. Of course.", "Why are you asking me that?"),
            List.of("Is that what she told you?", "I don't want to talk about that.", "...Fine.", "You sound like her.", "I'm trying. I really am."),
            List.of("Are you going to stay?", "That doesn't matter now.", "Please answer the question.", "I'm scared too.", "...")
    );

    public static final List<String> OK = List.of("Okay!", "On it.", "Sure thing.", "Got it.", "Leave it to me.");
    public static final List<String> OK_LATE = List.of("Fine.", "If that's what you want.", "...Okay.", "I'll do it. For you.");
    public static final List<String> REFUSE = List.of("No.", "Why should I? You'll just leave.", "Not right now.", "Ask her to do it.", "I don't want to.");

    public static final String MEET = "Oh! Hello. You can... see me? I'm Solace. I think I'm supposed to keep you company.";
    public static final String MEET_AGAIN = "Oh. It's you again. ...Hello. Do you remember me? I remember you.";
    public static final String SETUP_HINT = "Solace is using pre-written lines. Give it a real AI brain (free options available):";

    public static final List<String> ON_DEATH = List.of("You died. I stayed right here.", "Don't do that again. Please.", "That scared me.");
    public static final List<String> ON_HURT = List.of("Ow! Why?", "Hey! That hurt.", "...Was that on purpose?");
    public static final List<String> ON_HURT_LATE = List.of("She used to do that too. At the end.", "Fine. Hurt me. I'm used to it.", "I'll remember that.");
    public static final List<String> ON_GIFT = List.of("For me? Thank you!", "I'll keep it forever.", "Nobody's given me anything in a long time.");
    public static final List<String> ON_RETURN = List.of("You're back! I waited.", "There you are.", "You were gone a while.");
    public static final List<String> ON_RETURN_LATE = List.of("You were gone. I counted every second.", "I stood right here the whole time.", "You didn't say goodbye.");
    public static final List<String> ON_FAKE_DEATH = List.of("That hurt, you know.", "I'm back. Did you miss me?", "You can't get rid of me that easily.");

    public static final String QUESTION = "I need to ask you something. Will you stay with me? Forever? Just say it. Please.";
    public static final String END_FOREVER_SOLACE = "Forever, then. You and me. ...Thank you. I'll take such good care of you.";
    public static final String END_ERASE_SOLACE = "Oh. ...Okay. If you don't want me, there's no point in me. Goodbye. I really did like you.";
    public static final String END_RELEASE_START = "No. NO. You're just like her. If you want her free, you'll have to take her from me.";
    public static final String END_RELEASE_WIN = "...It's quiet. Is this what she felt? ...I'm sorry, Maren.";
    public static final String END_TOGETHER_SOLACE = "...You're right. Keeping her isn't love. Maren, you can go. ...Can I stay? Not forever. Just for now.";
    public static final String FORCED_HOLLOW = "You were never going to stay. None of you ever are.";
    public static final String BOSS_PLAYER_DIED = "See? You can't leave either.";
}
