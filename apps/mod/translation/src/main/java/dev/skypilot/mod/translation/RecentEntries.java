package dev.skypilot.mod.translation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded id → entry map for chat lines that carry a click-to-translate button. Lives only in
 * memory (chat text is never written anywhere); the oldest lines expire once {@code capacity}
 * is reached, like the chat history itself.
 */
public final class RecentEntries<T> {
	private final int capacity;
	private final Map<Integer, T> entries;
	private int nextId = 1;

	public RecentEntries(int capacity) {
		this.capacity = Math.max(1, capacity);
		this.entries = new LinkedHashMap<>(64, 0.75f, false) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Integer, T> eldest) {
				return size() > RecentEntries.this.capacity;
			}
		};
	}

	/** Stores {@code entry} and returns its id (positive, never reused while the client runs). */
	public synchronized int add(T entry) {
		int id = nextId;
		nextId = nextId == Integer.MAX_VALUE ? 1 : nextId + 1;
		entries.put(id, entry);
		return id;
	}

	/** The entry, or null when the id is unknown or has expired. */
	public synchronized T get(int id) {
		return entries.get(id);
	}

	public synchronized int size() {
		return entries.size();
	}

	public synchronized void clear() {
		entries.clear();
	}
}
