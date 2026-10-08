package dev.skypilot.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/**
 * Microsoft sign-in for Minecraft, the way Microsoft documents it for game launchers: the
 * device code flow (you sign in on microsoft.com in your browser - the launcher never sees your
 * password), then Xbox Live, XSTS and Minecraft services turn the Microsoft token into a game
 * session. Endpoints and request shapes: minecraft.wiki "Microsoft authentication" (checked
 * 2026-10). A client ID must be registered in Azure and approved by Mojang
 * (https://aka.ms/mce-reviewappid); SkyPilot ships its approved public application ID.
 */
public final class MicrosoftAuth {
	static final String DEVICE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
	static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
	static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
	static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
	static final String LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
	static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
	static final String SCOPE = "XboxLive.signin offline_access";
	private static final String FORM = "application/x-www-form-urlencoded";
	private static final String JSON = "application/json";

	/** What to show the player: open {@code verificationUri} and enter {@code userCode}. */
	public record DeviceCode(String userCode, String deviceCode, String verificationUri, int intervalSeconds, long expiresAtMs) {
		@Override
		public String toString() {
			return "DeviceCode[" + userCode + "]";
		}
	}

	public record MsTokens(String accessToken, String refreshToken) {
		@Override
		public String toString() {
			return "MsTokens[hidden]";
		}
	}

	/** A Minecraft game session. The access token is kept in memory only and never logged. */
	public record Session(String name, String uuid, String accessToken, String xuid, long expiresAtMs) {
		@Override
		public String toString() {
			return "Session[" + name + "]";
		}
	}

	/** A sign-in problem with an explanation for the player (English / Japanese). */
	public static final class AuthException extends IOException {
		public final String en;
		public final String ja;

		public AuthException(String en, String ja) {
			super(en);
			this.en = en;
			this.ja = ja;
		}

		public String text(boolean japanese) {
			return japanese ? ja : en;
		}
	}

	/** The transport, replaceable in tests. */
	@FunctionalInterface
	public interface Transport {
		Net.Reply send(String url, String contentType, String body, String bearer) throws IOException;
	}

	private final Transport http;

	public MicrosoftAuth() {
		this(Net::auth);
	}

	public MicrosoftAuth(Transport http) {
		this.http = http;
	}

	public static boolean validClientId(String id) {
		return id != null && id.strip().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	}

	private static String form(String... kv) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < kv.length; i += 2) {
			if (i > 0) b.append('&');
			b.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
		}
		return b.toString();
	}

	private static JsonObject json(Net.Reply r) {
		try {
			JsonElement e = JsonParser.parseString(r.text());
			return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
		} catch (RuntimeException e) {
			return new JsonObject();
		}
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	/** Step 1: ask Microsoft for a code the player enters at {@code verificationUri}. */
	public DeviceCode start(String clientId) throws IOException {
		if (!validClientId(clientId)) throw new AuthException("Enter your Azure application (client) ID first (Settings → Microsoft sign-in).",
				"先に Azure のアプリケーション（クライアント）ID を入力してください（設定 → Microsoft サインイン）。");
		Net.Reply r = http.send(DEVICE_URL, FORM, form("client_id", clientId.strip(), "scope", SCOPE), null);
		JsonObject o = json(r);
		if (r.status() != 200) throw microsoftError(o);
		String user = str(o, "user_code");
		String device = str(o, "device_code");
		if (user.isEmpty() || device.isEmpty()) throw new AuthException("Microsoft sent an unexpected answer.", "Microsoft から想定外の応答がありました。");
		int interval = o.has("interval") ? Math.max(1, o.get("interval").getAsInt()) : 5;
		long expires = System.currentTimeMillis() + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 900) * 1000L;
		String uri = str(o, "verification_uri");
		if (!uri.startsWith("https://")) uri = "https://www.microsoft.com/link";
		return new DeviceCode(user, device, uri, interval, expires);
	}

	/** Step 2: wait until the player has signed in (or cancels / the code expires). */
	public MsTokens poll(String clientId, DeviceCode code, BooleanSupplier cancelled) throws IOException {
		int interval = code.intervalSeconds();
		while (true) {
			if (cancelled.getAsBoolean()) throw new AuthException("Sign-in cancelled.", "サインインを中止しました。");
			if (System.currentTimeMillis() > code.expiresAtMs()) throw new AuthException("The code expired. Start again.", "コードの有効期限が切れました。もう一度お試しください。");
			sleep(interval * 1000L, cancelled);
			Net.Reply r = http.send(TOKEN_URL, FORM, form("grant_type", "urn:ietf:params:oauth:grant-type:device_code", "client_id", clientId.strip(), "device_code", code.deviceCode()), null);
			JsonObject o = json(r);
			if (r.status() == 200) return tokens(o);
			switch (str(o, "error")) {
				case "authorization_pending" -> {
				}
				case "slow_down" -> interval += 5;
				case "authorization_declined" -> throw new AuthException("Sign-in was declined on the Microsoft page.", "Microsoft のページでサインインが拒否されました。");
				case "expired_token" -> throw new AuthException("The code expired. Start again.", "コードの有効期限が切れました。もう一度お試しください。");
				default -> throw microsoftError(o);
			}
		}
	}

	/** Use a saved refresh token (no browser needed). */
	public MsTokens refresh(String clientId, String refreshToken) throws IOException {
		Net.Reply r = http.send(TOKEN_URL, FORM, form("grant_type", "refresh_token", "client_id", clientId.strip(), "refresh_token", refreshToken, "scope", SCOPE), null);
		JsonObject o = json(r);
		if (r.status() != 200) {
			if ("invalid_grant".equals(str(o, "error"))) throw new AuthException("Your saved sign-in has expired. Sign in again.", "保存されたサインインの期限が切れました。もう一度サインインしてください。");
			throw microsoftError(o);
		}
		return tokens(o);
	}

	private static MsTokens tokens(JsonObject o) throws AuthException {
		String access = str(o, "access_token");
		if (access.isEmpty()) throw new AuthException("Microsoft sent no token.", "Microsoft からトークンが返されませんでした。");
		return new MsTokens(access, str(o, "refresh_token"));
	}

	/** Step 3: Xbox Live → XSTS → Minecraft services → game profile. */
	public Session minecraft(String msAccessToken) throws IOException {
		JsonObject xblBody = new JsonObject();
		JsonObject props = new JsonObject();
		props.addProperty("AuthMethod", "RPS");
		props.addProperty("SiteName", "user.auth.xboxlive.com");
		props.addProperty("RpsTicket", "d=" + msAccessToken);
		xblBody.add("Properties", props);
		xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
		xblBody.addProperty("TokenType", "JWT");
		Net.Reply xbl = http.send(XBL_URL, JSON, xblBody.toString(), null);
		if (xbl.status() != 200) throw new AuthException("Xbox Live sign-in failed (HTTP " + xbl.status() + ").", "Xbox Live へのサインインに失敗しました（HTTP " + xbl.status() + "）。");
		JsonObject xo = json(xbl);
		String xblToken = str(xo, "Token");
		String uhs = userHash(xo);

		JsonObject xstsBody = new JsonObject();
		JsonObject xp = new JsonObject();
		xp.addProperty("SandboxId", "RETAIL");
		JsonArray tokens = new JsonArray();
		tokens.add(xblToken);
		xp.add("UserTokens", tokens);
		xstsBody.add("Properties", xp);
		xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		xstsBody.addProperty("TokenType", "JWT");
		Net.Reply xsts = http.send(XSTS_URL, JSON, xstsBody.toString(), null);
		JsonObject so = json(xsts);
		if (xsts.status() != 200) throw xstsError(so, xsts.status());
		String xstsToken = str(so, "Token");
		if (uhs.isEmpty()) uhs = userHash(so);

		JsonObject login = new JsonObject();
		login.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
		Net.Reply mc = http.send(LOGIN_URL, JSON, login.toString(), null);
		if (mc.status() == 403 || mc.status() == 401) {
			throw new AuthException("Mojang has not approved this client ID for Minecraft sign-in yet. Apply at https://aka.ms/mce-reviewappid (or use the official launcher meanwhile).",
					"このクライアントIDは、まだ Mojang から Minecraft のサインインを許可されていません。https://aka.ms/mce-reviewappid で申請してください（それまでは公式ランチャーをお使いください）。");
		}
		if (mc.status() != 200) throw new AuthException("Minecraft sign-in failed (HTTP " + mc.status() + ").", "Minecraft へのサインインに失敗しました（HTTP " + mc.status() + "）。");
		JsonObject mo = json(mc);
		String mcToken = str(mo, "access_token");
		long expires = System.currentTimeMillis() + (mo.has("expires_in") ? mo.get("expires_in").getAsLong() : 86_400) * 1000L;

		Net.Reply profile = http.send(PROFILE_URL, null, null, mcToken);
		if (profile.status() == 404) {
			throw new AuthException("This Microsoft account has no Minecraft: Java Edition profile (does it own the game?).",
					"この Microsoft アカウントには Minecraft: Java Edition のプロフィールがありません（ゲームを所有していますか？）。");
		}
		if (profile.status() != 200) throw new AuthException("Could not read your Minecraft profile (HTTP " + profile.status() + ").", "Minecraft のプロフィールを取得できませんでした（HTTP " + profile.status() + "）。");
		JsonObject po = json(profile);
		String id = str(po, "id");
		String name = str(po, "name");
		if (!id.matches("[0-9a-fA-F]{32}") || !Avatar.validName(name)) throw new AuthException("Your Minecraft profile looks wrong.", "Minecraft のプロフィールが不正です。");
		return new Session(name, id, mcToken, uhs, expires);
	}

	private static String userHash(JsonObject o) {
		try {
			return o.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
		} catch (RuntimeException e) {
			return "";
		}
	}

	static AuthException xstsError(JsonObject o, int status) {
		long code = o.has("XErr") ? o.get("XErr").getAsLong() : 0;
		return switch ((int) (code - 2148916000L)) {
			case 233 -> new AuthException("This Microsoft account has no Xbox profile yet. Create one at xbox.com, then sign in again.",
					"この Microsoft アカウントにはまだ Xbox プロフィールがありません。xbox.com で作成してから、もう一度サインインしてください。");
			case 235 -> new AuthException("Xbox Live is not available in your country or region.", "お住まいの国・地域では Xbox Live を利用できません。");
			case 236, 237 -> new AuthException("Xbox needs adult verification for this account (South Korea). Complete it on xbox.com.",
					"このアカウントは Xbox の成人認証が必要です（韓国）。xbox.com で完了してください。");
			case 238 -> new AuthException("This is a child account: an adult must add it to a Microsoft family first.",
					"子ども用アカウントです。先に保護者が Microsoft ファミリーに追加する必要があります。");
			default -> new AuthException("Xbox sign-in failed (HTTP " + status + (code != 0 ? ", XErr " + code : "") + ").",
					"Xbox へのサインインに失敗しました（HTTP " + status + (code != 0 ? "、XErr " + code : "") + "）。");
		};
	}

	static AuthException microsoftError(JsonObject o) {
		String error = str(o, "error");
		String desc = str(o, "error_description");
		if (desc.contains("AADSTS7000218") || desc.contains("AADSTS70002")) {
			return new AuthException("Turn on \"Allow public client flows\" in your Azure app registration (Authentication page).",
					"Azure のアプリ登録で「パブリック クライアント フローを許可する」をオンにしてください（認証ページ）。");
		}
		if (desc.contains("AADSTS700016") || "unauthorized_client".equals(error) || "invalid_client".equals(error)) {
			return new AuthException("Microsoft does not know this client ID, or it does not allow personal Microsoft accounts.",
					"このクライアントIDは Microsoft に登録されていないか、個人用 Microsoft アカウントを許可していません。");
		}
		return new AuthException("Microsoft sign-in failed (" + (error.isEmpty() ? "unknown error" : error) + ").",
				"Microsoft のサインインに失敗しました（" + (error.isEmpty() ? "不明なエラー" : error) + "）。");
	}

	private static void sleep(long ms, BooleanSupplier cancelled) throws AuthException {
		long end = System.currentTimeMillis() + ms;
		try {
			while (System.currentTimeMillis() < end) {
				if (cancelled.getAsBoolean()) throw new AuthException("Sign-in cancelled.", "サインインを中止しました。");
				Thread.sleep(Math.min(200, Math.max(1, end - System.currentTimeMillis())));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("Sign-in cancelled.", "サインインを中止しました。");
		}
	}
}
