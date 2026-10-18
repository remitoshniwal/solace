package com.solacemod.solace.story;

/** How the story ended for a player. */
public enum Ending {
    NONE,
    /** "Stay": you promised to stay forever. Solace keeps you, like it kept Maren. */
    FOREVER,
    /** "Free Maren": you fought the Hollow and won. Maren is free. Solace is gone. */
    RELEASE,
    /** "Erase": Solace let itself be deleted. Maren is still trapped. */
    ERASE,
    /** Secret: trust Solace enough, read every page and never take more than you need, and it lets Maren go by itself. */
    TOGETHER,
    /** Secret, bad: owe Solace far too much when the question comes, and it doesn't ask. It takes you. */
    COLLECTED;

    public static Ending parse(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
