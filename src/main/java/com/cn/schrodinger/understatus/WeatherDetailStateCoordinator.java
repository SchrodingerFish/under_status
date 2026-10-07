package com.cn.schrodinger.understatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

/** Tracks independent lazy-load state for weather detail sections. */
public final class WeatherDetailStateCoordinator {
    public enum State { IDLE, LOADING, READY, EMPTY, UNSUPPORTED, ERROR, STALE }
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<String, String> statuses = new ConcurrentHashMap<>();
    private volatile String activeKey = "";
    private final Map<String, Ticket> requests = new HashMap<>();
    private final Map<String, String> selections = new HashMap<>();
    private long sequence;
    private boolean closed;

    public record Ticket(String view, String key, long generation) {}

    /** Selecting a different range invalidates work that targets the same panel. */
    public synchronized boolean select(String view, String key) {
        if (closed) return false;
        String previous = selections.put(view, key);
        if (!key.equals(previous)) {
            requests.remove(view);
            if (previous != null && state(previous) == State.LOADING) clear(previous);
            return true;
        }
        return false;
    }

    public synchronized Ticket begin(String view, String key) {
        if (closed) throw new IllegalStateException("Weather view is closed");
        select(view, key);
        Ticket ticket = new Ticket(view, key, ++sequence);
        requests.put(view, ticket);
        set(key, State.LOADING, "正在获取和风天气数据…");
        return ticket;
    }

    public synchronized boolean accepts(Ticket ticket) {
        return !closed && ticket.equals(requests.get(ticket.view()))
                && ticket.key().equals(selections.get(ticket.view()));
    }

    public synchronized boolean complete(Ticket ticket, State state, String status) {
        if (!accepts(ticket)) return false;
        set(ticket.key(), state, status);
        return true;
    }

    public synchronized boolean isClosed() { return closed; }

    public synchronized void close() {
        closed = true;
        requests.clear();
        selections.clear();
    }

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
    public synchronized void clear(String key) {
        requests.values().removeIf(ticket -> ticket.key().equals(key));
        states.remove(key);
        statuses.remove(key);
    }
    public synchronized void clearAll() {
        requests.clear();
        selections.clear();
        states.clear();
        statuses.clear();
    }
}
