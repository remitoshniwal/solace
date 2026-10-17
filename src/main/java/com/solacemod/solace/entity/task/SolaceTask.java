package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;

/** A job Solace is doing, like mining or building. tick() runs 20 times a second until it returns true (done). */
public interface SolaceTask {
    boolean tick(SolaceEntity solace);

    default void stop(SolaceEntity solace) {
    }

    /** Should Solace stop this job to fight when something attacks? */
    default boolean pausesForCombat() {
        return true;
    }

    /** Combat jobs run alongside the normal attack behaviour instead of replacing it. */
    default boolean isCombat() {
        return false;
    }

    /** Short description for the AI, e.g. "mining iron ore (2/5)". */
    String describe();
}
