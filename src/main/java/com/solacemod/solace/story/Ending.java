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
    /** Secret: trust Solace enough and read every page, and it lets Maren go by itself. */
    TOGETHER;

    public static Ending parse(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
