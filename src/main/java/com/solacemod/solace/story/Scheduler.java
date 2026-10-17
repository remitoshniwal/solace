package com.solacemod.solace.story;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs code a few ticks later on the server thread (20 ticks = 1 second). Cleared when the server stops. */
public final class Scheduler {
    private record Job(long dueTick, Runnable action) {
    }

    private static final List<Job> JOBS = new ArrayList<>();
    private static long now;

    private Scheduler() {
    }

    public static void later(int ticks, Runnable action) {
        JOBS.add(new Job(now + Math.max(1, ticks), action));
    }

    public static void tick() {
        now++;
        if (JOBS.isEmpty()) return;
        List<Job> due = new ArrayList<>();
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (job.dueTick() <= now) {
                due.add(job);
                it.remove();
            }
        }
        for (Job job : due) {
            try {
                job.action().run();
            } catch (RuntimeException e) {
                com.solacemod.solace.Solace.LOGGER.error("Solace scheduled task failed", e);
            }
        }
    }

    public static void clear() {
        JOBS.clear();
    }
}
