package dev.skypilot.launcher;

import dev.skypilot.mod.core.Lang;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * What "Set up / Repair" does and what the status list checks. Nothing here touches Microsoft or
 * Minecraft accounts: signing in and starting the game stay in the official Minecraft Launcher.
 */
public final class Setup {
	public record Check(String id, boolean ok, String label, String detail) {
	}

	private Setup() {
	}

	public static List<Check> status(LauncherSettings s, Pins pins, Lang l, boolean checkServer) {
		Path mc = s.minecraft();
		Path inst = s.instance();
		List<Check> out = new ArrayList<>();
		List<Path> profileFiles = LauncherProfiles.files(mc);
		out.add(new Check("launcher", !profileFiles.isEmpty(), l.t("Official Minecraft Launcher found", "公式Minecraftランチャーを検出"),
				profileFiles.isEmpty() ? l.t("Install and start the official launcher once (" + mc + ").", "公式ランチャーをインストールして一度起動してください（" + mc + "）。")
						: profileFiles.get(0).getFileName().toString()));
		out.add(new Check("fabric", FabricInstaller.installed(mc, pins), "Fabric Loader " + pins.loader() + " / Minecraft " + pins.minecraft(), pins.versionId()));
		boolean profile = !profileFiles.isEmpty() && profileFiles.stream().allMatch(f -> LauncherProfiles.upToDate(f, pins.versionId(), inst));
		out.add(new Check("profile", profile, l.t("\"" + LauncherProfiles.NAME + "\" profile", "「" + LauncherProfiles.NAME + "」プロファイル"), inst.toString()));
		out.add(new Check("mod", Mods.skyPilotInstalled(s.mods(), pins), l.t("SkyPilot mod ", "SkyPilot MOD ") + pins.modVersion(), pins.modFile()));
		out.add(new Check("fabric-api", Mods.fabricApiInstalled(s.mods(), pins), "Fabric API " + pins.fabricApi(), l.t("SHA-512 checked", "SHA-512確認済み")));
		String server = Instance.config(inst).apiBaseUrl;
		if (checkServer) {
			out.add(new Check("server", Net.healthy(server), l.t("SkyPilot server reachable", "SkyPilotサーバーに接続"), server));
		}
		out.add(new Check("linked", Instance.linked(inst), l.t("Mod linked to your SkyPilot account", "MODとSkyPilotアカウントの連携"),
				l.t("In game: /skypilot link", "ゲーム内で /skypilot link")));
		return out;
	}

	/** Everything needed to start SkyPilot from the official launcher. */
	public static void run(LauncherSettings s, Pins pins, Lang l, FabricInstaller.Fetch fetch, Consumer<String> progress) throws IOException {
		Path mc = s.minecraft();
		List<Path> profileFiles = LauncherProfiles.files(mc);
		if (profileFiles.isEmpty()) {
			throw new IOException(l.t("The official Minecraft Launcher's profile file was not found in " + mc + ". Install the launcher and start it once, then try again.",
					mc + " に公式Minecraftランチャーのプロファイルファイルがありません。ランチャーをインストールして一度起動してから、もう一度お試しください。"));
		}
		progress.accept(l.t("Installing Fabric Loader " + pins.loader() + "…", "Fabric Loader " + pins.loader() + " をインストール中…"));
		FabricInstaller.install(mc, pins, fetch);
		progress.accept(l.t("Installing the SkyPilot mod…", "SkyPilot MODをインストール中…"));
		Mods.installSkyPilot(s.mods(), pins, Pins.bundledMod());
		progress.accept(l.t("Downloading Fabric API " + pins.fabricApi() + " (Modrinth, SHA-512 checked)…", "Fabric API " + pins.fabricApi() + " をダウンロード中（Modrinth・SHA-512確認）…"));
		Mods.installFabricApi(s.mods(), pins, fetch);
		progress.accept(l.t("Adding the profile to the official launcher…", "公式ランチャーにプロファイルを追加中…"));
		Files.createDirectories(s.instance());
		String icon = iconDataUri();
		for (Path f : profileFiles) LauncherProfiles.upsert(f, pins.versionId(), s.instance(), s.memoryMb, icon);
		progress.accept(l.t("Done. Start the official launcher, pick \"" + LauncherProfiles.NAME + "\" and press Play.",
				"完了しました。公式ランチャーで「" + LauncherProfiles.NAME + "」を選んでプレイを押してください。"));
	}

	/** The profile icon (the mod's icon as a data URI, as Fabric's installer does). */
	static String iconDataUri() {
		try (InputStream in = Setup.class.getResourceAsStream("/bundled/icon.png")) {
			if (in == null) return null;
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
		} catch (IOException e) {
			return null;
		}
	}
}
