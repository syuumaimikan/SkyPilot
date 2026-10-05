package dev.skypilot.mod.network;

import java.util.function.LongSupplier;

/**
 * Stops calling a failing service for a cool-down period so a down backend never causes a
 * stream of slow requests (and never blocks the game).
 */
public final class CircuitBreaker {
	public enum State {
		CLOSED, OPEN, HALF_OPEN
	}

	private final int threshold;
	private final long openMillis;
	private final LongSupplier clock;
	private int failures;
	private long openedAt;
	private State state = State.CLOSED;

	public CircuitBreaker(int threshold, long openMillis, LongSupplier clock) {
		this.threshold = Math.max(1, threshold);
		this.openMillis = openMillis;
		this.clock = clock;
	}

	public synchronized boolean allow() {
		if (state == State.OPEN && clock.getAsLong() - openedAt >= openMillis) {
			state = State.HALF_OPEN;
		}
		return state != State.OPEN;
	}

	public synchronized void success() {
		failures = 0;
		state = State.CLOSED;
	}

	public synchronized void failure() {
		failures++;
		if (state == State.HALF_OPEN || failures >= threshold) {
			state = State.OPEN;
			openedAt = clock.getAsLong();
		}
	}

	public synchronized State state() {
		allow();
		return state;
	}

	public synchronized long retryInMillis() {
		return state == State.OPEN ? Math.max(0, openMillis - (clock.getAsLong() - openedAt)) : 0;
	}
}
