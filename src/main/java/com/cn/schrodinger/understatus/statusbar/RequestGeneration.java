package com.cn.schrodinger.understatus.statusbar;

import java.util.concurrent.atomic.AtomicLong;

/** Invalidates all callbacks belonging to an earlier user action. */
public final class RequestGeneration {
    private final AtomicLong value = new AtomicLong();
    public long next() { return value.incrementAndGet(); }
    public boolean isCurrent(long request) { return request == value.get(); }
}
