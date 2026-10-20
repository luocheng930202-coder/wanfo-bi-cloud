package com.wanfo.bi.security;

import java.util.Set;

/** Binds a verified actor and capability snapshot to a worker thread. Always clear in finally. */
public final class ActorScope {
    private static final ThreadLocal<Bound> CURRENT = new ThreadLocal<>();
    private record Bound(BiActor actor, Set<String> capabilities) { }
    private ActorScope() { }
    public static void run(BiActor actor, Set<String> capabilities, Runnable task) {
        if (actor == null || capabilities == null || task == null) throw new IllegalArgumentException("导出执行缺少身份上下文");
        Bound previous = CURRENT.get();
        CURRENT.set(new Bound(actor, Set.copyOf(capabilities)));
        try { task.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
    public static BiActor actor() {
        Bound bound = CURRENT.get();
        return bound == null ? null : bound.actor();
    }
    public static boolean active() { return CURRENT.get() != null; }
    public static boolean allows(String capability) {
        Bound bound = CURRENT.get();
        if (bound == null || capability == null || capability.isBlank()) return false;
        for (String granted : bound.capabilities()) {
            if (granted == null || granted.isBlank()) continue;
            if ("*:*:*".equals(granted) || granted.equals(capability)) return true;
            if (granted.endsWith(":*") && capability.startsWith(granted.substring(0, granted.length() - 1))) return true;
        }
        return false;
    }
}
