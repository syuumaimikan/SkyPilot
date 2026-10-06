package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyBlockTime;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Clock / event timers (SkyBlock calendar from the SkyPilot server), event alerts, the player's
 * reminders and the price watchlist. Notifications only.
 */
final class Timers {
	record Upcoming(String id, String name, long startMs, long endMs) {
		boolean active(long now) {
			return startMs <= now && now < endMs;
		}
	}

	record Reminder(long dueAt, String text) {
	}

	private static Dto.Calendar calendar;
	private static long calendarAt;
	private static long calendarTriedAt;
	private static final Set<String> ALERTED = new HashSet<>();
	static final List<Reminder> REMINDERS = new ArrayList<>();
	/** Watches whose condition is currently met (alert once until it is no longer met). */
	private static final Set<String> TRIGGERED = new HashSet<>();
	private static int watchCursor;
	private static long lastWatchCheck;

	private Timers() {
	}

	static Dto.SkyblockNow skyblock;
	private static long skyblockAt;
	private static long skyblockTriedAt;
	private static final Set<String> SALES_SEEN = new HashSet<>();

	static SkyBlockTime time() {
		Dto.Calendar c = calendar;
		return c == null ? null : new SkyBlockTime(c.epochMs(), c.dayMs(), c.daysPerMonth(), c.months());
	}

	/** Next events, recomputed locally for recurring ones so they stay exact between refreshes. */
	static List<Upcoming> upcoming(long now) {
		Dto.Calendar c = calendar;
		if (c == null) return List.of();
		SkyBlockTime t = time();
		List<Upcoming> out = new ArrayList<>();
		for (Dto.CalendarEvent e : c.events()) {
			if (e.everyDays() != null) {
				long dur = (e.durationDays() == null ? 0 : e.durationDays()) * c.dayMs();
				long start = t.nextRecurring(now, e.everyDays(), e.offsetDays() == null ? 0 : e.offsetDays(), dur);
				out.add(new Upcoming(e.id(), e.name(), start, start + dur));
			} else if (e.endMs() > now) {
				out.add(new Upcoming(e.id(), e.name(), e.startMs(), e.endMs()));
			}
		}
		out.sort((a, b) -> Long.compare(a.startMs(), b.startMs()));
		return out;
	}

	static void setCalendar(Dto.Calendar c) {
		calendar = c;
		calendarAt = System.currentTimeMillis();
	}

	/** Once per second on the game thread. */
	static void tickSecond(Minecraft mc) {
		long now = System.currentTimeMillis();
		SkyPilot sp = SkyPilot.get();
		Lang l = sp.lang();
		boolean needCalendar = Feat.on(Features.HUD_CLOCK) || Feat.on(Features.HUD_EVENTS) || Feat.on(Features.FARMING_CONTEST) || Feat.on(Features.MARKET_DARK_AUCTION);
		ApiClient api = sp.api();
		if (needCalendar && api != null && now - calendarAt > 3_600_000 && now - calendarTriedAt > 60_000) {
			calendarTriedAt = now;
			api.calendar().whenComplete((c, err) -> mc.execute(() -> {
				if (c != null) setCalendar(c);
			}));
		}
		for (Upcoming u : upcoming(now)) {
			long in = u.startMs() - now;
			boolean contest = u.id().equals("jacobs_contest") && Feat.on(Features.FARMING_CONTEST);
			boolean auction = u.id().equals("dark_auction") && Feat.on(Features.MARKET_DARK_AUCTION);
			if ((contest || auction) && in > 0 && in <= 60_000 && ALERTED.add(u.id() + "@" + u.startMs())) {
				Feat.toast(new ItemStack(contest ? Items.WHEAT : Items.NETHER_STAR), u.name(), 0xFFFFAA00, l.t("starts in ", "開始まで ") + Text.duration(in));
				Feat.ping(1.4f);
			}
		}
		if (ALERTED.size() > 50) ALERTED.clear();
		// reminders
		List<Reminder> due = new ArrayList<>();
		for (Reminder r : REMINDERS) if (r.dueAt() <= now) due.add(r);
		if (!due.isEmpty()) {
			REMINDERS.removeAll(due);
			for (Reminder r : due) {
				Feat.toast(new ItemStack(Items.CLOCK), l.t("Reminder", "リマインダー"), 0xFF55FFFF, r.text());
				McText.chat(McText.prefix().append(Component.literal(l.t("Reminder: ", "リマインダー: ")).withStyle(ChatFormatting.AQUA))
						.append(Component.literal(r.text()).withStyle(ChatFormatting.WHITE)));
				Feat.ping(1.0f);
			}
		}
		checkWatchlist(sp, now);
		// mayor / minister / fire sales
		boolean wantSb = Feat.on(Features.HUD_MAYOR) || Feat.on(Features.MARKET_FIRE_SALE);
		if (wantSb && api != null && now - skyblockAt > 300_000 && now - skyblockTriedAt > 60_000) {
			skyblockTriedAt = now;
			api.skyblockNow().whenComplete((s, err) -> mc.execute(() -> {
				if (s != null) {
					skyblock = s;
					skyblockAt = System.currentTimeMillis();
				}
			}));
		}
		if (Feat.on(Features.MARKET_FIRE_SALE) && skyblock != null) {
			for (Dto.FireSale f : skyblock.fireSales()) {
				if (f.startMs() <= now && now < f.endMs() && SALES_SEEN.add(f.itemId() + "@" + f.startMs())) {
					String line = (f.price() != null ? f.price() + l.t(" gems", " ジェム") : "") + l.t(" · ends in ", " · 終了まで ") + InfoHud.eta(f.endMs() - now);
					Feat.toast(new ItemStack(Items.EMERALD), l.t("Fire sale: ", "ファイアセール: ") + f.name(), 0xFFFF5555, line);
				}
			}
		}
	}

	static void remind(long inMs, String text) {
		REMINDERS.add(new Reminder(System.currentTimeMillis() + inMs, text));
		REMINDERS.sort((a, b) -> Long.compare(a.dueAt(), b.dueAt()));
		while (REMINDERS.size() > 20) REMINDERS.remove(REMINDERS.size() - 1);
	}

	/** One watched item every 5 seconds (20 items → each about every 100 s). */
	private static void checkWatchlist(SkyPilot sp, long now) {
		List<SkyPilotConfig.Watch> list = sp.config().watchlist;
		if (!Feat.on(Features.MARKET_WATCHLIST) || list.isEmpty() || sp.api() == null || now - lastWatchCheck < 5_000) return;
		lastWatchCheck = now;
		SkyPilotConfig.Watch w = list.get(watchCursor++ % list.size());
		Minecraft mc = Minecraft.getInstance();
		sp.api().price(w.itemId, null).whenComplete((p, err) -> mc.execute(() -> {
			if (p == null) return;
			Double price = watchedPrice(p, w.below);
			if (price == null) return;
			String key = w.itemId + (w.below ? "<" : ">") + w.price;
			boolean met = w.below ? price <= w.price : price >= w.price;
			if (!met) {
				TRIGGERED.remove(key);
				return;
			}
			if (!TRIGGERED.add(key)) return;
			Lang l = sp.lang();
			String line = (w.below ? l.t("below ", "以下 ") : l.t("above ", "以上 ")) + Text.coins(w.price) + ": " + Text.coins(price);
			Feat.toast(new ItemStack(Items.GOLD_NUGGET), p.name(), 0xFF55FF55, line);
			McText.chat(McText.prefix().append(Component.literal(l.t("Price watch: ", "価格ウォッチ: ")).withStyle(ChatFormatting.GREEN))
					.append(Component.literal(p.name() + " " + line).withStyle(ChatFormatting.WHITE)));
			if (sp.config().market.alertSound) Feat.ping(1.6f);
		}));
	}

	/** Buying (below): Bazaar instant buy or lowest BIN; selling (above): Bazaar instant sell or value. */
	static Double watchedPrice(Dto.PriceResponse p, boolean below) {
		if (below) return p.bazaarBuy() != null ? p.bazaarBuy() : p.lowestBin() != null ? p.lowestBin() : p.estimatedValue();
		return p.bazaarSell() != null ? p.bazaarSell() : p.estimatedValue();
	}
}
