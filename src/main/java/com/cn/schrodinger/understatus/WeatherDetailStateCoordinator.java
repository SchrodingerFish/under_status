package com.cn.schrodinger.understatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks independent lazy-load state for weather detail sections. */
public final class WeatherDetailStateCoordinator {
    public enum State { IDLE, LOADING, READY, EMPTY, UNSUPPORTED, ERROR, STALE }
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<String, String> statuses = new ConcurrentHashMap<>();
    private volatile String activeKey = "";

    public State state(String key) { return states.getOrDefault(key, State.IDLE); }
    public boolean shouldLoad(String key) { return state(key) == State.IDLE; }
    public void set(String key, State state) { states.put(key, state); }
    public void set(String key, State state, String status) {
        states.put(key, state);
        statuses.put(key, status);
    }
    public void activate(String key) { activeKey = key; }
    public String activeKey() { return activeKey; }
    public boolean isActive(String key) { return key.equals(activeKey); }
    public String activeStatus() { return statuses.getOrDefault(activeKey, " "); }
    public void clear(String key) {
        states.remove(key);
        statuses.remove(key);
    }
    public void clearAll() {
        states.clear();
        statuses.clear();
    }
}
