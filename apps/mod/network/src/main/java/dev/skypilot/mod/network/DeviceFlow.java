package dev.skypilot.mod.network;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * OAuth-style device authorization (RFC 8628): the mod shows a short code, the player approves
 * it on the SkyPilot website, and the mod receives a revocable SkyPilot token. No password or
 * Minecraft session is involved.
 */
public final class DeviceFlow {
	public interface Listener {
		/** Show this to the player: open {@code uri} and enter {@code userCode}. */
		void onCode(String userCode, String uri, String uriComplete);

		void onLinked();

		/** Terminal failure: "expired_token", "access_denied" or a network/API error message. */
		void onFailed(String reason);
	}

	private final ApiClient api;
	private final TokenStore tokens;
	private final ScheduledExecutorService scheduler;
	private final LongSupplier clock;
	private final TimeUnit unit;
	private ScheduledFuture<?> pending;
	private volatile boolean active;

	public DeviceFlow(ApiClient api, TokenStore tokens, ScheduledExecutorService scheduler, LongSupplier clock) {
		this(api, tokens, scheduler, clock, TimeUnit.SECONDS);
	}

	/** {@code unit} is the unit of the server's poll interval (tests use milliseconds). */
	DeviceFlow(ApiClient api, TokenStore tokens, ScheduledExecutorService scheduler, LongSupplier clock, TimeUnit unit) {
		this.api = api;
		this.tokens = tokens;
		this.scheduler = scheduler;
		this.clock = clock;
		this.unit = unit;
	}

	public boolean active() {
		return active;
	}

	public synchronized void start(String deviceName, Listener listener) {
		cancel();
		active = true;
		api.deviceCode(deviceName).whenComplete((code, err) -> {
			if (err != null) {
				active = false;
				listener.onFailed(message(err));
				return;
			}
			try {
				listener.onCode(code.userCode(), code.verificationUri(), code.verificationUriComplete());
			} catch (RuntimeException e) {
				// A display problem must not stop the flow; the code can still be approved.
			}
			long deadline = clock.getAsLong() + code.expiresIn() * 1000L;
			schedulePoll(code.deviceCode(), Math.max(5, code.interval()), deadline, listener);
		});
	}

	public synchronized void cancel() {
		active = false;
		if (pending != null) pending.cancel(false);
		pending = null;
	}

	private synchronized void schedulePoll(String deviceCode, int intervalSeconds, long deadline, Listener listener) {
		if (!active) return;
		pending = scheduler.schedule(() -> poll(deviceCode, intervalSeconds, deadline, listener), intervalSeconds, unit);
	}

	private void poll(String deviceCode, int intervalSeconds, long deadline, Listener listener) {
		if (!active) return;
		if (clock.getAsLong() > deadline) {
			active = false;
			listener.onFailed("expired_token");
			return;
		}
		api.deviceToken(deviceCode).whenComplete((r, err) -> {
			if (err != null) {
				// Transient network problem: keep polling until the code expires.
				schedulePoll(deviceCode, intervalSeconds * 2 > 60 ? 60 : intervalSeconds * 2, deadline, listener);
				return;
			}
			switch (r.status()) {
				case "ok" -> {
					active = false;
					try {
						tokens.set(r.accessToken());
						api.clearCache();
						listener.onLinked();
					} catch (IOException e) {
						listener.onFailed("could not save token: " + e.getMessage());
					}
				}
				case "authorization_pending" -> schedulePoll(deviceCode, intervalSeconds, deadline, listener);
				case "slow_down" -> schedulePoll(deviceCode, intervalSeconds + 5, deadline, listener);
				default -> {
					active = false;
					listener.onFailed(r.status());
				}
			}
		});
	}

	private static String message(Throwable err) {
		Throwable c = err.getCause() != null ? err.getCause() : err;
		return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
	}
}
