package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mod manager: every installed mod with its details, its own config screen (through Mod Menu's
 * API when the mod provides one), links, and switching mod files on / off in the mods folder
 * (applies after a restart). Optional update check shows Modrinth links only - SkyPilot never
 * downloads or installs anything.
 */
final class ModManagerScreen extends Screen {
	enum Filter {
		MODS("Mods", "MOD"), ALL("All", "すべて"), LIBRARIES("Libraries", "ライブラリ"), DISABLED("Disabled", "無効"), UPDATES("Updates", "更新あり");

		final String en;
		final String ja;

		Filter(String en, String ja) {
			this.en = en;
			this.ja = ja;
		}
	}

	private static final int ROW = 22;
	private static Filter filter = Filter.MODS;
	private static String query = "";
	/** File SHA-1 → latest version on Modrinth (from the last check). */
	private static Map<String, Dto.ModUpdate> updates = Map.of();
	private static final Map<String, String> HASHES = new HashMap<>();

	private final Screen parent;
	private List<ModManager.Entry> all = List.of();
	private ModManager.Entry selected;
	private double scroll;
	private String message;
	private boolean messageError;
	private boolean confirmDisable;
	private boolean checking;
	private int px;
	private int pw;
	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private int detailX;
	private int detailW;

	ModManagerScreen(Screen parent) {
		super(Component.literal("SkyPilot · Mods"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Lang l = SkyPilot.get().lang();
		if (all.isEmpty()) all = ModManager.scan();
		pw = Math.min(width - 16, 560);
		px = (width - pw) / 2;
		listX = px;
		listW = Math.max(150, pw * 2 / 5);
		listY = 46;
		listH = Math.max(40, height - listY - 34);
		detailX = listX + listW + 6;
		detailW = px + pw - detailX;
		EditBox search = new EditBox(font, listX, 22, listW - 76, 18, Component.literal(l.t("Search mods", "MODを検索")));
		search.setMaxLength(40);
		search.setValue(query);
		search.setHint(Component.literal(l.t("Search mods…", "MODを検索…")));
		search.setResponder(v -> {
			query = v;
			scroll = 0;
		});
		addRenderableWidget(search);
		addRenderableWidget(ClientButton.create(Component.literal(l.t(filter.en, filter.ja)), b -> {
			Filter[] fs = Filter.values();
			filter = fs[(filter.ordinal() + 1) % fs.length];
			scroll = 0;
			rebuildWidgets();
		}).bounds(listX + listW - 72, 21, 72, 20).build());
		int bw = (detailW - 4) / 2;
		addRenderableWidget(ClientButton.create(Component.literal(l.t("Open mods folder", "modsフォルダを開く")), b -> {
			try {
				Files.createDirectories(ModManager.modsDir());
				Util.getPlatform().openPath(ModManager.modsDir());
			} catch (Exception ex) {
				setMessage(ex.getMessage(), true);
			}
		}).bounds(detailX, 21, bw, 20).build());
		Button check = ClientButton.create(Component.literal(checking ? l.t("Checking…", "確認中…") : l.t("Check updates", "更新を確認")), b -> checkUpdates(l)).bounds(detailX + bw + 4, 21, bw, 20)
				.build();
		check.active = !checking;
		addRenderableWidget(check);
		if (selected != null) detailButtons(l);
		addRenderableWidget(ClientButton.create(Component.literal(l.t("Done", "完了")), b -> onClose()).bounds(px, height - 26, pw, 20).build());
	}

	private void detailButtons(Lang l) {
		ModManager.Entry e = selected;
		int y = listY + listH - 44;
		int bw = (detailW - 4) / 2;
		boolean own = e.id().equals("skypilot");
		Button config = ClientButton.create(Component.literal(l.t("Settings", "設定")), b -> {
			Screen s = own ? new SettingsScreen(this) : ModManager.configScreen(e.id(), this);
			if (s == null) setMessage(l.t("This mod has no settings screen (via Mod Menu).", "このMODには設定画面がありません（Mod Menu経由）。"), false);
			else minecraft.gui.setScreen(s);
		}).bounds(detailX, y, bw, 20).build();
		config.active = own || ModManager.hasConfig(e.id());
		addRenderableWidget(config);
		Button toggle = ClientButton.create(Component.literal(e.kind() == ModManager.Kind.DISABLED ? l.t("Enable", "有効にする") : confirmDisable ? l.t("Disable anyway", "それでも無効にする") : l.t("Disable", "無効にする")),
				b -> toggle(l)).bounds(detailX + bw + 4, y, bw, 20).build();
		toggle.active = e.toggleable();
		addRenderableWidget(toggle);
		int ly = y + 22;
		int n = 0;
		int lw = (detailW - 8) / 3;
		for (String key : List.of("homepage", "sources", "issues")) {
			String url = e.contact().get(key);
			if (url == null || !url.startsWith("https://")) continue;
			String label = switch (key) {
				case "homepage" -> l.t("Homepage", "ホームページ");
				case "sources" -> l.t("Source", "ソース");
				default -> l.t("Issues", "不具合報告");
			};
			addRenderableWidget(ClientButton.create(Component.literal(label), b -> openLink(url)).bounds(detailX + n * (lw + 4), ly, lw, 20).build());
			n++;
		}
		Dto.ModUpdate u = updateOf(e);
		if (u != null && !u.upToDate()) {
			addRenderableWidget(ClientButton.create(Component.literal(l.t("Open update page", "更新ページを開く")), b -> openLink(u.url())).bounds(detailX, y - 22, detailW, 20).build());
		}
	}

	private void openLink(String url) {
		minecraft.gui.setScreen(new ConfirmLinkScreen(yes -> {
			if (yes) Util.getPlatform().openUri(url);
			minecraft.gui.setScreen(this);
		}, url, false));
	}

	private void setMessage(String text, boolean error) {
		message = text;
		messageError = error;
	}

	private void toggle(Lang l) {
		ModManager.Entry e = selected;
		if (e.kind() != ModManager.Kind.DISABLED && !e.dependents().isEmpty() && !confirmDisable) {
			confirmDisable = true;
			setMessage(l.t("Needed by: ", "これが必要なMOD: ") + String.join(", ", e.dependents()) + l.t(" - they will not load without it.", " - 無効にするとこれらも読み込めなくなります。"), true);
			rebuildWidgets();
			return;
		}
		confirmDisable = false;
		String err = ModManager.toggle(e);
		if (err != null) {
			setMessage(l.t("Could not rename the file (" + err + "). Close the game and rename it in the mods folder.",
					"ファイル名を変更できませんでした（" + err + "）。ゲームを閉じてmodsフォルダで変更してください。"), true);
		} else {
			setMessage(l.t("Done - restart Minecraft to apply.", "完了。反映するにはMinecraftを再起動してください。"), false);
			String id = e.id();
			all = ModManager.scan();
			selected = all.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
		}
		rebuildWidgets();
	}

	private void checkUpdates(Lang l) {
		SkyPilot sp = SkyPilot.get();
		if (!sp.config().on(Features.TOOLS_MOD_UPDATES)) {
			setMessage(l.t("Turn on \"Mod update check\" in Settings → Features first (it sends your mod files' SHA-1 to your SkyPilot server, which asks Modrinth).",
					"先に 設定 → 機能 で「MODの更新確認」をオンにしてください（MODファイルのSHA-1をSkyPilotサーバーへ送り、Modrinthに問い合わせます）。"), true);
			return;
		}
		if (sp.api() == null || !sp.linked()) {
			setMessage(l.t("Link with SkyPilot first: /skypilot link", "先にSkyPilotと連携してください: /skypilot link"), true);
			return;
		}
		List<String> hashes = new ArrayList<>();
		for (ModManager.Entry e : all) {
			if (e.file() == null || e.kind() != ModManager.Kind.MOD && e.kind() != ModManager.Kind.DISABLED) continue;
			String h = HASHES.computeIfAbsent(e.file().toString(), k -> ModManager.sha1(e.file()));
			if (h != null) hashes.add(h);
		}
		if (hashes.isEmpty()) {
			setMessage(l.t("No mod files to check.", "確認するMODファイルがありません。"), false);
			return;
		}
		checking = true;
		rebuildWidgets();
		sp.api().modUpdates(hashes, ModManager.gameVersion()).whenComplete((r, err) -> minecraft.execute(() -> {
			checking = false;
			if (r == null) {
				setMessage(l.t("Update check failed: ", "更新確認に失敗: ") + SkyPilot.shortError(err), true);
			} else {
				Map<String, Dto.ModUpdate> m = new HashMap<>();
				for (Dto.ModUpdate u : r.updates()) m.put(u.hash(), u);
				updates = m;
				long n = r.updates().stream().filter(u -> !u.upToDate()).count();
				setMessage(l.t(n + " update(s) on Modrinth. Updating is up to you - SkyPilot does not download mods.", "Modrinthに " + n + " 件の更新があります。更新はご自身で行ってください（SkyPilotはダウンロードしません）。"), false);
			}
			if (minecraft.gui.screen() == this) rebuildWidgets();
		}));
	}

	private static Dto.ModUpdate updateOf(ModManager.Entry e) {
		if (e.file() == null) return null;
		String h = HASHES.get(e.file().toString());
		return h == null ? null : updates.get(h);
	}

	private List<ModManager.Entry> visible() {
		String q = query.strip().toLowerCase(Locale.ROOT);
		List<ModManager.Entry> out = new ArrayList<>();
		for (ModManager.Entry e : all) {
			boolean pass = switch (filter) {
				case MODS -> e.kind() == ModManager.Kind.MOD || e.kind() == ModManager.Kind.DEV;
				case ALL -> true;
				case LIBRARIES -> e.kind() == ModManager.Kind.LIBRARY || e.kind() == ModManager.Kind.BUILTIN;
				case DISABLED -> e.kind() == ModManager.Kind.DISABLED || e.kind() == ModManager.Kind.NOT_LOADED;
				case UPDATES -> {
					Dto.ModUpdate u = updateOf(e);
					yield u != null && !u.upToDate();
				}
			};
			if (pass && (q.isEmpty() || (e.name() + " " + e.id() + " " + e.description()).toLowerCase(Locale.ROOT).contains(q))) out.add(e);
		}
		return out;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (SkyPilot.get().config().appearance.richMenus) ClientStyle.backdrop(g,width,height);
		super.extractRenderState(g, mouseX, mouseY, delta);
		Lang l = SkyPilot.get().lang();
		g.centeredText(font, Component.literal(l.t("SkyPilot · Mods", "SkyPilot · MOD管理") + " (" + all.size() + ")"), width / 2, 8, 0xFFFFFFFF);
		List<ModManager.Entry> rows = visible();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() * ROW - listH)));
		g.fill(listX, listY, listX + listW, listY + listH, 0x90000000);
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		int y = listY - (int) scroll;
		for (ModManager.Entry e : rows) {
			if (y + ROW >= listY && y <= listY + listH) {
				boolean sel = e == selected;
				boolean over = mouseX >= listX && mouseX < listX + listW && mouseY >= Math.max(y, listY) && mouseY < Math.min(y + ROW, listY + listH);
				if (sel) g.fill(listX, y, listX + listW, y + ROW, 0x50B07CFF);
				else if (over) g.fill(listX, y, listX + listW, y + ROW, 0x22FFFFFF);
				icon(g, e, listX + 3, y + 3);
				int color = e.kind() == ModManager.Kind.DISABLED || e.kind() == ModManager.Kind.NOT_LOADED ? 0xFF888888 : 0xFFFFFFFF;
				g.text(font, font.plainSubstrByWidth(e.name(), listW - 30), listX + 23, y + 2, color, false);
				String sub = e.version() + badge(e, l);
				g.text(font, font.plainSubstrByWidth(sub, listW - 30), listX + 23, y + 12, badgeColor(e), false);
			}
			y += ROW;
		}
		g.disableScissor();
		if (rows.isEmpty()) g.text(font, l.t("No mods match.", "該当するMODはありません。"), listX + 6, listY + 6, 0xFF888888, false);
		// detail panel
		g.fill(detailX, listY, detailX + detailW, listY + listH, 0x90000000);
		if (selected == null) {
			g.textWithWordWrap(font, Component.literal(l.t(
					"Pick a mod to see its details, open its settings, its links, or switch it off. Changes to mod files apply after a restart. SkyPilot never downloads or installs mods.",
					"MODを選ぶと詳細・設定・リンク・無効化ができます。MODファイルの変更は再起動後に反映されます。SkyPilotがMODをダウンロード・インストールすることはありません。")), detailX + 6, listY + 6,
					detailW - 12, 0xFFAAAAAA);
		} else {
			details(g, selected, l);
		}
		int footY = listY + listH + 3;
		if (message != null) {
			g.text(font, font.plainSubstrByWidth(message, pw), px, footY, messageError ? 0xFFFF7777 : 0xFF55FF55, false);
		} else if (!ModManager.PENDING.isEmpty()) {
			g.text(font, l.t("Restart Minecraft to apply " + ModManager.PENDING.size() + " change(s).", ModManager.PENDING.size() + " 件の変更は再起動後に反映されます。"), px, footY, 0xFFFFAA00, false);
		}
	}

	private void details(GuiGraphicsExtractor g, ModManager.Entry e, Lang l) {
		int x = detailX + 6;
		int y = listY + 6;
		int w = detailW - 12;
		icon(g, e, x, y);
		g.text(font, font.plainSubstrByWidth(e.name(), w - 22), x + 20, y, 0xFFFFDD55, false);
		g.text(font, font.plainSubstrByWidth(e.id() + " · " + e.version(), w - 22), x + 20, y + 10, 0xFF888888, false);
		y += 24;
		List<String> lines = new ArrayList<>();
		if (!e.authors().isEmpty()) lines.add(l.t("By ", "作者: ") + String.join(", ", e.authors()));
		if (!e.license().isEmpty()) lines.add(l.t("License: ", "ライセンス: ") + String.join(", ", e.license()));
		lines.add(l.t("Type: ", "種類: ") + kindName(e, l) + (e.parent() != null ? l.t(" (inside ", "（") + e.parent() + l.t(")", " に同梱）") : ""));
		if (e.file() != null) lines.add(l.t("File: ", "ファイル: ") + e.file().getFileName());
		if (!e.depends().isEmpty()) lines.add(l.t("Needs: ", "必要: ") + String.join(", ", e.depends()));
		if (!e.dependents().isEmpty()) lines.add(l.t("Needed by: ", "必要としているMOD: ") + String.join(", ", e.dependents()));
		Dto.ModUpdate u = updateOf(e);
		if (u != null) lines.add(u.upToDate() ? l.t("Up to date (Modrinth)", "最新版です（Modrinth）") : l.t("Update: ", "更新: ") + u.versionNumber() + " (Modrinth)");
		for (String s : lines) {
			for (FormattedCharSequence seq : font.split(Component.literal(s), w)) {
				g.text(font, seq, x, y, 0xFFCCCCCC, false);
				y += 10;
			}
		}
		y += 4;
		int maxY = listY + listH - 70;
		if (e.description() != null && !e.description().isBlank()) {
			for (FormattedCharSequence seq : font.split(Component.literal(e.description()), w)) {
				if (y > maxY) break;
				g.text(font, seq, x, y, 0xFF999999, false);
				y += 10;
			}
		}
	}

	private void icon(GuiGraphicsExtractor g, ModManager.Entry e, int x, int y) {
		ModIcons.Icon icon = e.kind() == ModManager.Kind.DISABLED || e.kind() == ModManager.Kind.NOT_LOADED ? null : ModIcons.get(e);
		if (icon != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, icon.id(), x, y, 0f, 0f, 16, 16, icon.width(), icon.height(), icon.width(), icon.height());
		} else {
			g.fill(x, y, x + 16, y + 16, 0xFF3A3A4A);
			g.centeredText(font, e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase(Locale.ROOT), x + 8, y + 4, 0xFFDDDDDD);
		}
	}

	private static String kindName(ModManager.Entry e, Lang l) {
		return switch (e.kind()) {
			case MOD -> l.t("mod file", "MODファイル");
			case LIBRARY -> l.t("bundled library", "同梱ライブラリ");
			case BUILTIN -> l.t("built in", "組み込み");
			case DISABLED -> l.t("disabled file", "無効化されたファイル");
			case NOT_LOADED -> l.t("not loaded (applies after a restart)", "未読み込み（再起動で反映）");
			case DEV -> l.t("development", "開発環境");
		};
	}

	private static String badge(ModManager.Entry e, Lang l) {
		if (e.file() != null && ModManager.PENDING.contains(e.file())) return l.t("  · restart to apply", "  · 再起動で反映");
		Dto.ModUpdate u = updateOf(e);
		if (u != null && !u.upToDate()) return l.t("  · update ", "  · 更新 ") + u.versionNumber();
		return switch (e.kind()) {
			case DISABLED -> l.t("  · disabled", "  · 無効");
			case NOT_LOADED -> l.t("  · not loaded", "  · 未読み込み");
			case LIBRARY -> l.t("  · library", "  · ライブラリ");
			case BUILTIN -> l.t("  · built in", "  · 組み込み");
			default -> "";
		};
	}

	private static int badgeColor(ModManager.Entry e) {
		Dto.ModUpdate u = updateOf(e);
		if (e.file() != null && ModManager.PENDING.contains(e.file())) return 0xFFFFAA00;
		if (u != null && !u.upToDate()) return 0xFF55FF55;
		return 0xFF888888;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == 0 && mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
			int i = (int) Math.floor((my - listY + scroll) / ROW);
			List<ModManager.Entry> rows = visible();
			if (i >= 0 && i < rows.size()) {
				selected = rows.get(i);
				confirmDisable = false;
				message = null;
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
			scroll = Math.max(0, scroll - sy * ROW * 2);
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	/** For tests. */
	void select(String id) {
		selected = all.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
		if (minecraft != null) rebuildWidgets();
	}

	static void setFilter(Filter f) {
		filter = f;
		query = "";
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
