package dev.skypilot.launcher;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The Microsoft account used to start the game from this launcher. The game session (Minecraft
 * access token) is kept in memory for this run only; the saved refresh token (DPAPI-protected,
 * see {@link AccountStore}) renews it without a browser.
 */
public final class Account {
	private static volatile MicrosoftAuth.Session session;

	private Account() {
	}

	public static AccountStore.Saved saved(Path launcherDir) {
		return AccountStore.load(launcherDir);
	}

	/** Signed in: a session in memory, or a saved sign-in that can be renewed. */
	public static boolean signedIn(Path launcherDir) {
		return saved(launcherDir) != null && (session != null || AccountStore.refreshToken(launcherDir) != null);
	}

	/** After a browser sign-in. Returns false when the sign-in could not be remembered (sign in again next time). */
	public static boolean signedIn(Path launcherDir, MicrosoftAuth.Session s, MicrosoftAuth.MsTokens t) throws IOException {
		session = s;
		return AccountStore.save(launcherDir, s.name(), s.uuid(), t.refreshToken());
	}

	/** A valid game session: the one in memory, or a fresh one from the saved sign-in. */
	public static synchronized MicrosoftAuth.Session session(Path launcherDir, String clientId, MicrosoftAuth auth) throws IOException {
		MicrosoftAuth.Session s = session;
		if (s != null && s.expiresAtMs() > System.currentTimeMillis() + 10 * 60_000L) return s;
		String refresh = AccountStore.refreshToken(launcherDir);
		if (refresh == null) throw new MicrosoftAuth.AuthException("Sign in with Microsoft again.", "Microsoft でもう一度サインインしてください。");
		MicrosoftAuth.MsTokens t = auth.refresh(clientId, refresh);
		s = auth.minecraft(t.accessToken());
		AccountStore.save(launcherDir, s.name(), s.uuid(), t.refreshToken().isEmpty() ? refresh : t.refreshToken());
		session = s;
		return s;
	}

	public static void signOut(Path launcherDir) throws IOException {
		session = null;
		AccountStore.clear(launcherDir);
	}

	/** For tests. */
	static void forget() {
		session = null;
	}
}
