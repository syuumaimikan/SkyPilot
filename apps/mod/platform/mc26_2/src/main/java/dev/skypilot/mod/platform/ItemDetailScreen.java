package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * One item, JEI style: value, recipes (crafting grid / Forge / Kat / NPC shop), where it drops,
 * what it is used in and its description. Clicking an ingredient opens that item. Closing
 * returns to the window it was opened from (an open chest stays open).
 */
final class ItemDetailScreen extends Screen {
	enum Tab {
		RECIPES("Recipes", "レシピ"), DROPS("Obtain", "入手方法"), USES("Used in", "使い道"), INFO("Info", "説明");

		final String en;
		final String ja;

		Tab(String en, String ja) {
			this.en = en;
			this.ja = ja;
		}
	}

	private record Hit(int x, int y, int w, int h, Runnable click, Supplier<Component> tip) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Screen parent;
	private final Deque<String> back = new ArrayDeque<>();
	private final List<Hit> hits = new ArrayList<>();
	private String id;
	private Dto.ItemDetail detail;
	private String error;
	private Tab tab = Tab.RECIPES;
	private double scroll;
	private int docHeight;
	private int panelX;
	private int panelW;
	private int contentY;
	private int contentH;

	ItemDetailScreen(Screen parent, String id) {
		super(Component.literal("SkyPilot"));
		this.parent = parent;
		this.id = id;
	}

	@Override
	protected void init() {
		Lang l = SkyPilot.get().lang();
		panelW = Math.min(width - 16, 380);
		panelX = (width - panelW) / 2;
		contentY = 66;
		contentH = Math.max(40, height - contentY - 30);
		Tab[] tabs = Tab.values();
		int tw = (panelW - (tabs.length - 1) * 2) / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			Button b = Button.builder(Component.literal(l.t(t.en, t.ja)), btn -> {
				tab = t;
				scroll = 0;
				rebuildWidgets();
			}).bounds(panelX + i * (tw + 2), 42, tw, 18).build();
			b.active = t != tab;
			addRenderableWidget(b);
		}
		int bw = (panelW - 2) / 2;
		Button backBtn = Button.builder(Component.literal(l.t("◀ Back", "◀ 戻る")), b -> goBack()).bounds(panelX, height - 24, bw, 20).build();
		backBtn.active = !back.isEmpty();
		addRenderableWidget(backBtn);
		addRenderableWidget(Button.builder(Component.literal(l.t("Close", "閉じる")), b -> onClose()).bounds(panelX + bw + 2, height - 24, bw, 20).build());
		if (detail == null) load();
	}

	private void load() {
		ApiClient api = SkyPilot.get().api();
		if (api == null) {
			error = SkyPilot.get().apiError();
			return;
		}
		String want = id;
		api.item(want).whenComplete((d, err) -> minecraft.execute(() -> {
			if (!want.equals(id)) return;
			if (d != null) {
				detail = d;
				error = null;
			} else {
				error = SkyPilot.shortError(err);
			}
		}));
	}

	private void open(String next) {
		if (next == null || next.equals("SKYBLOCK_COIN") || next.equals(id)) return;
		back.push(id);
		show(next);
	}

	private void goBack() {
		if (!back.isEmpty()) show(back.pop());
	}

	private void show(String next) {
		id = next;
		detail = null;
		error = null;
		scroll = 0;
		tab = Tab.RECIPES;
		rebuildWidgets();
	}

	/** For tests: show data without a server. */
	void setDetail(Dto.ItemDetail d) {
		detail = d;
		id = d.item().id();
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		ItemIcons.flush();
		hits.clear();
		Lang l = SkyPilot.get().lang();
		Font f = font;
		g.fill(panelX, 4, panelX + panelW, 38, Ui.PANEL);
		g.outline(panelX, 4, panelW, 34, Ui.PANEL_BORDER);
		Dto.ItemDetail d = detail;
		if (d == null) {
			g.text(f, error != null ? error : l.t("Loading…", "読み込み中…"), panelX + 8, 16, error != null ? Ui.RED : Ui.GRAY, false);
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(panelX + 5, 5);
		pose.scale(2f, 2f);
		g.item(ItemIcons.of(d.item().icon()), 0, 0);
		pose.popMatrix();
		g.text(f, f.plainSubstrByWidth(d.item().name(), panelW - 50), panelX + 44, 9, Ui.rarityColor(d.item().rarity()), false);
		g.text(f, f.plainSubstrByWidth(priceLine(d, l), panelW - 50), panelX + 44, 22, Ui.GOLD, false);

		int top = contentY - 2;
		g.fill(panelX, top, panelX + panelW, contentY + contentH + 2, Ui.PANEL);
		g.outline(panelX, top, panelW, contentH + 4, Ui.PANEL_BORDER);
		g.enableScissor(panelX + 1, contentY, panelX + panelW - 1, contentY + contentH);
		int x = panelX + 8;
		int y = contentY + 4 - (int) scroll;
		int end = switch (tab) {
			case RECIPES -> drawRecipes(g, d, l, x, y);
			case DROPS -> drawDrops(g, d, l, x, y);
			case USES -> drawUses(g, d, l, x, y, mouseX, mouseY);
			case INFO -> drawInfo(g, d, l, x, y);
		};
		docHeight = end - (contentY + 4 - (int) scroll);
		g.disableScissor();
		if (docHeight > contentH) {
			int trackX = panelX + panelW - 5;
			int thumbH = Math.max(12, contentH * contentH / docHeight);
			int thumbY = contentY + (int) ((contentH - thumbH) * (scroll / Math.max(1, docHeight - contentH)));
			g.fill(trackX, contentY, trackX + 3, contentY + contentH, 0x30FFFFFF);
			g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF9D8CFF);
		}
		for (Hit h : hits) {
			if (h.tip() != null && h.contains(mouseX, mouseY) && mouseY >= contentY && mouseY < contentY + contentH) {
				g.setTooltipForNextFrame(h.tip().get(), mouseX, mouseY);
				break;
			}
		}
	}

	private static String priceLine(Dto.ItemDetail d, Lang l) {
		List<String> parts = new ArrayList<>();
		parts.add(d.item().value() != null ? l.t("Value ", "価値 ") + Text.coins(d.item().value()) + (d.valueSource() != null ? " (" + d.valueSource() + ")" : "") : l.t("No market price", "市場価格なし"));
		if (d.lowestBin() != null) parts.add(l.t("lowest BIN ", "最安BIN ") + Text.coins(d.lowestBin()));
		if (d.bazaarBuy() != null || d.bazaarSell() != null) parts.add(l.t("Bazaar ", "バザー ") + coinsOr(d.bazaarBuy()) + "/" + coinsOr(d.bazaarSell()));
		if (d.npcSell() != null) parts.add("NPC " + Text.coins(d.npcSell()));
		if (d.craftCost() != null) parts.add(l.t("craft ", "作成 ") + Text.coins(d.craftCost()));
		return String.join(" · ", parts);
	}

	private static String coinsOr(Double v) {
		return v == null ? "?" : Text.coins(v);
	}

	/** Draws a slot with an item and its amount, registering a click that opens the item. */
	private void slot(GuiGraphicsExtractor g, Dto.ItemStackInfo s, int x, int y, Lang l) {
		g.fill(x, y, x + 18, y + 18, 0xFF2A2A36);
		g.outline(x, y, 18, 18, 0xFF44445A);
		if (s == null) return;
		ItemStack stack = ItemIcons.of(s.icon());
		g.item(stack, x + 1, y + 1);
		if (s.amount() != 1) g.itemDecorations(font, stack, x + 1, y + 1, amount(s.amount()));
		hits.add(new Hit(x, y, 18, 18, () -> open(s.id()), () -> {
			MutableComponent c = Component.literal(s.name()).withStyle(ChatFormatting.WHITE);
			c.append(Component.literal("\n× " + amount(s.amount())).withStyle(ChatFormatting.GRAY));
			if (s.unitPrice() != null) {
				c.append(Component.literal("\n" + l.t("each ", "単価 ") + Text.coins(s.unitPrice()) + " · " + l.t("total ", "合計 ") + Text.coins(s.unitPrice() * s.amount())).withStyle(ChatFormatting.GOLD));
			}
			if (!"SKYBLOCK_COIN".equals(s.id())) c.append(Component.literal("\n" + l.t("Click to open", "クリックで開く")).withStyle(ChatFormatting.DARK_GRAY));
			return c;
		}));
	}

	private static String amount(double a) {
		if (a >= 10_000) return Text.coins(a);
		return a == Math.rint(a) ? String.valueOf((long) a) : String.format(Locale.ROOT, "%.1f", a);
	}

	private int drawRecipes(GuiGraphicsExtractor g, Dto.ItemDetail d, Lang l, int x, int y) {
		if (d.recipes().isEmpty()) {
			g.text(font, l.t("No recipe - see “Obtain” for drops.", "レシピなし（入手方法タブでドロップを確認）"), x, y, Ui.GRAY, false);
			return y + 12;
		}
		for (Dto.Recipe r : d.recipes()) {
			String head = switch (r.kind()) {
				case "CRAFTING" -> l.t("Crafting", "クラフト");
				case "FORGE" -> l.t("Forge", "フォージ") + (r.durationSecs() != null ? " · " + Text.duration((long) (r.durationSecs() * 1000)) : "");
				case "KAT" -> "Kat" + (r.durationSecs() != null ? " · " + Text.duration((long) (r.durationSecs() * 1000)) : "") + (r.coins() != null ? " · " + Text.coins(r.coins()) : "");
				case "NPC_SHOP" -> l.t("Shop: ", "ショップ: ") + (r.npc() == null ? "NPC" : r.npc());
				default -> r.kind();
			};
			if (r.cost() != null) head += l.t(" · materials ", " · 材料費 ") + Text.coins(r.cost());
			g.text(font, font.plainSubstrByWidth(head, panelW - 24), x, y, Ui.PURPLE, false);
			y += 12;
			if ("CRAFTING".equals(r.kind()) && r.grid() != null && r.grid().size() == 9) {
				for (int i = 0; i < 9; i++) slot(g, r.grid().get(i), x + (i % 3) * 18, y + (i / 3) * 18, l);
				g.text(font, "→", x + 62, y + 23, Ui.GRAY, false);
				slot(g, r.output(), x + 76, y + 18, l);
				y += 58;
			} else {
				int perRow = Math.max(1, (panelW - 24 - 40) / 20);
				int cx = x;
				int i = 0;
				for (Dto.ItemStackInfo s : r.inputs()) {
					slot(g, s, cx, y, l);
					cx += 20;
					if (++i % perRow == 0) {
						cx = x;
						y += 20;
					}
				}
				g.text(font, "→", cx + 2, y + 5, Ui.GRAY, false);
				slot(g, r.output(), cx + 14, y, l);
				y += 24;
			}
			y += 4;
		}
		return y;
	}

	private int drawDrops(GuiGraphicsExtractor g, Dto.ItemDetail d, Lang l, int x, int y) {
		boolean any = false;
		for (Dto.Recipe r : d.recipes()) {
			if (!"NPC_SHOP".equals(r.kind())) continue;
			any = true;
			g.text(font, font.plainSubstrByWidth(l.t("Buy from ", "購入: ") + r.npc() + (r.cost() != null ? " · " + Text.coins(r.cost()) : ""), panelW - 24), x, y, Ui.AQUA, false);
			y += 12;
		}
		for (Dto.DropSource s : d.drops()) {
			any = true;
			String chance = s.chance() >= 1 ? "100%" : s.chance() >= 0.01 ? String.format(Locale.ROOT, "%.1f%%", s.chance() * 100) : "1/" + Math.round(1 / Math.max(s.chance(), 1e-9));
			g.text(font, font.plainSubstrByWidth(s.source() + " · " + chance + (s.amount() != 1 ? " · ×" + amount(s.amount()) : ""), panelW - 24), x, y, Ui.WHITE, false);
			y += 12;
		}
		if (d.bazaarBuy() != null) {
			any = true;
			g.text(font, l.t("Bazaar: instant buy ", "バザー: 即購入 ") + Text.coins(d.bazaarBuy()), x, y, Ui.GOLD, false);
			y += 12;
		} else if (d.lowestBin() != null) {
			any = true;
			g.text(font, l.t("Auction House: lowest BIN ", "オークション: 最安BIN ") + Text.coins(d.lowestBin()), x, y, Ui.GOLD, false);
			y += 12;
		}
		if (!d.recipes().isEmpty()) {
			g.text(font, l.t("Craftable - see the Recipes tab.", "クラフト可能（レシピタブ参照）"), x, y, Ui.GRAY, false);
			y += 12;
			any = true;
		}
		if (!any) {
			g.text(font, l.t("No drop source in NEU-REPO data.", "NEU-REPOのデータに入手元がありません。"), x, y, Ui.GRAY, false);
			y += 12;
		}
		return y;
	}

	private int drawUses(GuiGraphicsExtractor g, Dto.ItemDetail d, Lang l, int x, int y, int mouseX, int mouseY) {
		if (d.usedIn().isEmpty()) {
			g.text(font, l.t("Not used in any known recipe.", "既知のレシピでは使われていません。"), x, y, Ui.GRAY, false);
			return y + 12;
		}
		for (Dto.ItemEntry e : d.usedIn()) {
			boolean over = mouseX >= x - 2 && mouseX < panelX + panelW - 8 && mouseY >= y && mouseY < y + 19;
			if (over) g.fill(x - 2, y, panelX + panelW - 8, y + 19, 0x22FFFFFF);
			g.item(ItemIcons.of(e.icon()), x, y + 1);
			g.text(font, font.plainSubstrByWidth(e.name(), panelW - 120), x + 20, y + 5, Ui.rarityColor(e.rarity()), false);
			if (e.value() != null) {
				String v = Text.coins(e.value());
				g.text(font, v, panelX + panelW - 12 - font.width(v), y + 5, Ui.GOLD, false);
			}
			hits.add(new Hit(x - 2, y, panelW - 16, 19, () -> open(e.id()), null));
			y += 19;
		}
		return y;
	}

	private int drawInfo(GuiGraphicsExtractor g, Dto.ItemDetail d, Lang l, int x, int y) {
		if (d.category() != null) {
			g.text(font, l.t("Category: ", "カテゴリ: ") + d.category(), x, y, Ui.GRAY, false);
			y += 12;
		}
		for (String line : d.lore()) {
			for (FormattedCharSequence seq : font.split(Component.literal(line), panelW - 24)) {
				g.text(font, seq, x, y, Ui.WHITE, false);
				y += 10;
			}
		}
		if (d.salesPerDay() != null && d.salesPerDay() > 0) {
			y += 4;
			g.text(font, l.t("Auction sales/day: ", "オークション販売数/日: ") + String.format(Locale.ROOT, "%.1f", d.salesPerDay()), x, y, Ui.GRAY, false);
			y += 12;
		}
		if (d.wiki() != null) {
			y += 4;
			g.text(font, font.plainSubstrByWidth("Wiki: " + d.wiki(), panelW - 24), x, y, Ui.DARK_GRAY, false);
			y += 12;
		}
		g.text(font, l.t("Data: NEU-REPO (MIT), Hypixel API, SkyPilot market", "データ: NEU-REPO（MIT）・Hypixel API・SkyPilot市場データ"), x, y + 4, Ui.DARK_GRAY, false);
		return y + 16;
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.y() < contentY || event.y() >= contentY + contentH) return false;
		for (Hit h : hits) {
			if (h.click() != null && h.contains(event.x(), event.y())) {
				h.click().run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(Math.max(0, docHeight - contentH), scroll - scrollY * 20));
		return true;
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
