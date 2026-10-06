package dev.skypilot.mod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every optional SkyPilot feature with its own on/off switch.
 *
 * <p>All features only read what the client already shows (chat, sidebar, tab list, action bar,
 * items in an open window) and draw SkyPilot's own text, outlines and sounds. None of them
 * clicks, moves, mines, attacks, buys, sells, cancels a vanilla action, shows invisible
 * entities or sends anything to the game server (Hypixel's disallowed list, Fr0z3n 2021-03-26).
 * Hypixel publishes no list of explicitly allowed mods, so every feature added here is
 * <b>off by default</b>; "recommended" only marks what the preset button turns on.
 */
public final class Features {
	public enum Category {
		CHAT("Chat", "チャット"), TOOLTIP("Tooltips", "説明欄"), INVENTORY("Inventory", "インベントリ"), HUD("HUD", "HUD"), MINING("Mining", "採掘"),
		FARMING("Farming", "農業"), FISHING("Fishing", "釣り"), COMBAT("Combat", "戦闘"), DUNGEONS("Dungeons", "ダンジョン"), RIFT("Rift", "リフト"),
		GUIDES("Guides", "ガイド"), MARKET("Market", "マーケット"), TOOLS("Tools", "ツール"), PERFORMANCE("Performance", "軽量化");

		public final String en;
		public final String ja;

		Category(String en, String ja) {
			this.en = en;
			this.ja = ja;
		}
	}

	/**
	 * @param network     contacts the player's SkyPilot server (never Hypixel)
	 * @param consent     sends chat text, so the translation disclosure must be accepted first
	 * @param recommended turned on by the "recommended" preset
	 */
	public record Feature(String id, Category category, String en, String ja, String descEn, String descJa, boolean network, boolean consent, boolean recommended) {
		public String name(Lang l) {
			return l.t(en, ja);
		}

		public String description(Lang l) {
			return l.t(descEn, descJa);
		}
	}

	private static final Map<String, Feature> BY_ID = new LinkedHashMap<>();
	private static final List<Feature> LIST = new ArrayList<>();

	private static Feature f(String id, Category c, String en, String ja, String descEn, String descJa, String flags) {
		Feature feature = new Feature(id, c, en, ja, descEn, descJa, flags.contains("n"), flags.contains("c"), flags.contains("r"));
		if (BY_ID.put(id, feature) != null) throw new IllegalStateException("duplicate feature " + id);
		LIST.add(feature);
		return feature;
	}

	// ---------------------------------------------------------------- chat
	public static final Feature CHAT_CLICK_TRANSLATE = f("chat.click_translate", Category.CHAT, "Click to translate", "クリック翻訳",
			"A [→JA] button on chat lines; click to show that line in Japanese.", "チャットの各行に[訳]ボタンを付け、クリックした行だけ日本語にします。", "nc");
	public static final Feature CHAT_TIMESTAMPS = f("chat.timestamps", Category.CHAT, "Timestamps", "タイムスタンプ",
			"Shows the time [HH:mm] before every chat line.", "チャットの各行の先頭に時刻 [HH:mm] を表示します。", "");
	public static final Feature CHAT_COMPACT = f("chat.compact", Category.CHAT, "Stack repeated messages", "重複メッセージをまとめる",
			"The same message again within a minute replaces the previous line and shows (x2), (x3)…", "1分以内の同じメッセージは前の行を置き換え、(x2) のようにまとめます。", "r");
	public static final Feature CHAT_COPY = f("chat.copy", Category.CHAT, "Click to copy", "クリックでコピー",
			"Clicking chat text that has no link or button of its own copies the line.", "リンクやボタンのないチャット文字をクリックすると、その行をコピーします。", "r");
	public static final Feature CHAT_MENTION = f("chat.mention", Category.CHAT, "Mention highlight", "名前の強調",
			"Lines from other players that contain your name are highlighted with a ping.", "他のプレイヤーの発言にあなたの名前があると強調し、音で知らせます。", "");
	public static final Feature CHAT_COORDS = f("chat.coords", Category.CHAT, "Coordinates to waypoint", "座標をウェイポイントに",
			"Chat lines with \"x: 1, y: 2, z: 3\" get a [Waypoint] button that marks the spot.", "\"x: 1, y: 2, z: 3\" 形式の座標を含む行に[ウェイポイント]ボタンを付けます。", "r");
	public static final Feature CHAT_HIDE_LOBBY = f("chat.hide_lobby", Category.CHAT, "Hide lobby joins", "ロビー参加通知を隠す",
			"Hides \"… joined the lobby!\" and Mystery Box/Dust lines in lobbies.", "ロビーの「… joined the lobby!」やミステリーボックスの通知を隠します。", "r");
	public static final Feature CHAT_HIDE_WARPING = f("chat.hide_warping", Category.CHAT, "Hide warp messages", "ワープ表示を隠す",
			"Hides \"Sending to server …\", \"Warping…\" and \"Request join for Hub …\".", "「Sending to server …」「Warping…」などを隠します。", "r");
	public static final Feature CHAT_HIDE_PROFILE = f("chat.hide_profile", Category.CHAT, "Hide profile ID lines", "プロフィールID表示を隠す",
			"Hides \"You are playing on profile: …\" and \"Profile ID: …\".", "「You are playing on profile」「Profile ID」を隠します。", "r");
	public static final Feature CHAT_HIDE_COMBO = f("chat.hide_combo", Category.CHAT, "Hide kill combo", "キルコンボ表示を隠す",
			"Hides \"+50 Kill Combo\" and \"Your Kill Combo has expired!\" lines.", "「+50 Kill Combo」などを隠します。", "");
	public static final Feature CHAT_HIDE_ABILITY = f("chat.hide_ability", Category.CHAT, "Hide ability spam", "アビリティのスパムを隠す",
			"Hides \"Your Implosion hit …\", Molten Wave / Spirit Sceptre hits and \"There are blocks in the way!\".", "「Your Implosion hit …」や「There are blocks in the way!」を隠します。", "");
	public static final Feature CHAT_HIDE_GEXP = f("chat.hide_gexp", Category.CHAT, "Hide GEXP messages", "GEXP獲得表示を隠す",
			"Hides \"You earned … GEXP from playing SkyBlock!\".", "「You earned … GEXP from playing SkyBlock!」を隠します。", "");
	public static final Feature CHAT_REPLY_TRANSLATE = f("chat.reply_translate", Category.CHAT, "Write in Japanese (/sp en)", "日本語で書く（/sp en）",
			"/skypilot en <日本語> translates your text into English and puts it into the chat box; you send it yourself.",
			"/skypilot en <日本語> で英語に翻訳してチャット欄に入れます。送信は自分で行います。", "nc");

	public static final Feature CHAT_HIDE_SACKS = f("chat.hide_sacks", Category.CHAT, "Hide sack messages", "サック通知を隠す",
			"Hides the \"[Sacks] +123 items.\" summaries.", "「[Sacks] +123 items.」の集計メッセージを隠します。", "r");
	public static final Feature CHAT_HIDE_SEPARATORS = f("chat.hide_separators", Category.CHAT, "Hide separator lines", "区切り線を隠す",
			"Hides lines made only of ▬▬▬ / ----- / ===== .", "▬▬▬ や ----- だけの区切り線を隠します。", "");
	public static final Feature CHAT_KEYWORDS = f("chat.keywords", Category.CHAT, "Keyword alerts", "キーワード通知",
			"Lines containing your keywords (/skypilot keyword add <word>) are marked and ping.", "登録した語（/skypilot keyword add <語>）を含む行に印を付けて音で知らせます。", "");
	public static final Feature CHAT_SEARCH = f("chat.search", Category.CHAT, "Chat search", "チャット検索",
			"/skypilot find <text> lists matching lines of the last 500 chat lines (kept in memory only).", "/skypilot find <文字> で直近500行から検索（メモリ内のみ）。", "r");

	public static final Feature CHAT_HIDE_WELCOME = f("chat.hide_welcome", Category.CHAT, "Hide welcome line", "ようこそ表示を隠す",
			"Hides \"Welcome to Hypixel SkyBlock!\".", "「Welcome to Hypixel SkyBlock!」を隠します。", "r");
	public static final Feature CHAT_HIDE_USELESS_DROPS = f("chat.hide_useless_drops", Category.CHAT, "Hide cheap rare drops", "安いレアドロップ表示を隠す",
			"Hides RARE DROP lines for Enchanted Ender Pearl, Enchanted End Stone and Crystal Fragment.", "Enchanted Ender Pearl等の安いRARE DROP表示を隠します。", "r");
	public static final Feature CHAT_HIDE_NOTICES = f("chat.hide_notices", Category.CHAT, "Hide minor notices", "細かい通知を隠す",
			"Hides 0-coin interest, power orb replaced, Mining Speed Boost used/expired and tip summaries.", "0コインの利息、パワーオーブ置換、Mining Speed Boostの使用/終了、チップ集計を隠します。", "r");
	public static final Feature CHAT_HIDE_WARNINGS = f("chat.hide_warnings", Category.CHAT, "Hide repeated warnings", "繰り返し警告を隠す",
			"Hides \"Whoa! Slow down there!\", \"You can't use this while in combat!\" and similar repeated warnings.", "「Whoa! Slow down there!」などの繰り返し警告を隠します。", "");
	public static final Feature CHAT_HIDE_SKYMALL = f("chat.hide_skymall", Category.CHAT, "Hide Sky Mall / Lottery lines", "Sky Mall/Lottery表示を隠す",
			"Hides the daily \"Your Sky Mall / Lottery buff changed!\" lines.", "毎日の「Sky Mall / Lottery buff changed!」を隠します。", "r");
	public static final Feature CHAT_HIDE_FIRE_SALE = f("chat.hide_fire_sale", Category.CHAT, "Hide fire sale banners", "ファイアセール告知を隠す",
			"Hides the FIRE SALE announcement lines (the fire sale toast can still tell you).", "ファイアセールの告知行を隠します（通知機能は別）。", "");
	public static final Feature CHAT_HIDE_ACHIEVEMENTS = f("chat.hide_achievements", Category.CHAT, "Hide achievement lines", "実績解除表示を隠す",
			"Hides \">> Achievement Unlocked: … <<\".", "「Achievement Unlocked」を隠します。", "");
	public static final Feature CHAT_HIDE_PARKOUR = f("chat.hide_parkour", Category.CHAT, "Hide parkour lines", "パルクール表示を隠す",
			"Hides \"Started parkour\" / \"Cancelled parkour!\".", "「Started parkour」「Cancelled parkour!」を隠します。", "");
	public static final Feature CHAT_HIDE_TELEPORT_PADS = f("chat.hide_teleport_pads", Category.CHAT, "Hide teleport pad lines", "テレポートパッド表示を隠す",
			"Hides \"Warped from the … to the …\".", "「Warped from the …」を隠します。", "r");

	// ---------------------------------------------------------------- tooltips
	public static final Feature TOOLTIP_PRICE = f("tooltip.price", Category.TOOLTIP, "Item value", "アイテムの価値",
			"SkyPilot's estimated value, Bazaar or lowest BIN / median and confidence.", "推定価値・バザー価格または最安BIN/中央値と信頼度を表示します。", "n");
	public static final Feature TOOLTIP_NPC = f("tooltip.npc_price", Category.TOOLTIP, "NPC sell price", "NPC売却価格",
			"Adds the NPC sell price (from Hypixel's item list).", "NPCへの売却価格（Hypixelのアイテム一覧）を追加します。", "nr");
	public static final Feature TOOLTIP_CRAFT = f("tooltip.craft_cost", Category.TOOLTIP, "Craft cost", "クラフト費用",
			"Adds the cost to craft the item from Bazaar/NPC-priced ingredients.", "材料のバザー/NPC価格からクラフト費用を追加します。", "n");
	public static final Feature TOOLTIP_STACK = f("tooltip.stack_value", Category.TOOLTIP, "Stack value", "スタックの価値",
			"For stacks, adds the value of the whole stack.", "複数個のスタックでは合計の価値を追加します。", "nr");
	public static final Feature TOOLTIP_ID = f("tooltip.item_id", Category.TOOLTIP, "SkyBlock item ID", "アイテムID",
			"Adds the item's SkyBlock ID (e.g. HYPERION).", "SkyBlockのアイテムID（例: HYPERION）を表示します。", "");
	public static final Feature TOOLTIP_AH_COMPARE = f("tooltip.ah_compare", Category.TOOLTIP, "BIN vs value", "BINと価値の比較",
			"On auction listings, compares \"Buy it now\" with SkyPilot's value (below / above).", "オークションの「Buy it now」価格を推定価値と比べて表示します。", "nr");
	public static final Feature TOOLTIP_HIDE_FILLER = f("tooltip.hide_filler", Category.TOOLTIP, "Hide filler tooltips", "飾りガラスの説明を隠す",
			"No tooltip for the blank glass panes that fill menus.", "メニューを埋める名前のないガラス板の説明欄を出しません。", "r");

	public static final Feature TOOLTIP_BAZAAR_MARGIN = f("tooltip.bazaar_margin", Category.TOOLTIP, "Bazaar flip margin", "バザーの差益",
			"For Bazaar products: buy-order → sell-offer margin per item after the Bazaar tax.", "バザー商品で、買い注文→売り注文の1個あたりの差益（税引後）。", "nr");

	// ---------------------------------------------------------------- inventory
	public static final Feature INV_ITEM_BROWSER = f("inventory.item_browser", Category.INVENTORY, "Item list (JEI style)", "アイテム一覧（JEI風）",
			"Every SkyBlock item right of inventory windows: search, value, recipes, drops and uses.", "インベントリ右側に全アイテム。検索・価値・レシピ・ドロップ・用途。", "n");
	public static final Feature INV_CHEST_VALUE = f("inventory.chest_value", Category.INVENTORY, "Chest value", "チェストの価値",
			"Total value of chests, backpacks and the ender chest, highest first; click a name to outline it.", "チェスト等の合計価値を高い順に表示。名前クリックで縁取り。", "n");
	public static final Feature INV_PLAYER_VALUE = f("inventory.player_value", Category.INVENTORY, "Inventory value", "所持品の価値",
			"The same value list for your own inventory (E).", "自分のインベントリ（E）にも価値一覧を表示します。", "n");
	public static final Feature INV_RARITY = f("inventory.rarity_background", Category.INVENTORY, "Rarity background", "レアリティ背景",
			"Colours the slot behind each SkyBlock item by its rarity.", "アイテムのレアリティの色をスロットの背景に付けます。", "r");
	public static final Feature INV_NUMBERS = f("inventory.item_numbers", Category.INVENTORY, "Item numbers", "アイテムの数字表示",
			"Small numbers on slots: book level, pet level, minion tier, dungeon stars, skill/collection levels.", "本のレベル・ペットLv・ミニオンTier・スター数・スキルLvをスロットに表示。", "r");
	public static final Feature INV_SEARCH_HIGHLIGHT = f("inventory.search_highlight", Category.INVENTORY, "Search highlight", "検索で強調",
			"While the item list search has text, items in the open window that do not match are dimmed.", "アイテム一覧で検索中、開いている画面の一致しないアイテムを暗くします。", "r");
	public static final Feature INV_RECIPE_KEYS = f("inventory.recipe_keys", Category.INVENTORY, "R / U lookup keys", "R / U キーで調べる",
			"Hover an item and press R (recipe) or U (uses) to open its page.", "アイテムにカーソルを合わせて R（レシピ）/ U（用途）でページを開きます。", "nr");

	public static final Feature INV_AH_HIGHLIGHT = f("inventory.ah_highlight", Category.INVENTORY, "Auction deal outlines", "オークションのお得枠",
			"In the auction browser: green outline when Buy it now is 10%+ below SkyPilot's value, red when 50%+ above.", "オークション一覧で、BINが推定価値より10%以上安いと緑、50%以上高いと赤で縁取り。", "nr");
	public static final Feature INV_VALUABLE = f("inventory.valuable_highlight", Category.INVENTORY, "Valuable item outlines", "高価なアイテムの縁取り",
			"With the chest value list, outlines items worth 1M+ (/skypilot highlight <price> to change).", "チェストの価値一覧と一緒に、1M以上のアイテムを縁取り（/skypilot highlight <価格> で変更）。", "nr");
	public static final Feature INV_COPY_ID = f("inventory.copy_id", Category.INVENTORY, "Ctrl+C copies item ID", "Ctrl+CでIDをコピー",
			"Hover an item and press Ctrl+C to copy its SkyBlock ID.", "アイテムにカーソルを合わせて Ctrl+C でSkyBlock IDをコピー。", "r");

	public static final Feature INV_SACK_VALUE = f("inventory.sack_value", Category.INVENTORY, "Sack value", "サックの価値",
			"In a sack window, the value of everything stored (\"Stored:\" lines × Bazaar prices), highest first.", "サックの画面で、保管中アイテムの価値（Stored:×バザー価格）を高い順に表示。", "nr");

	// ---------------------------------------------------------------- HUD
	public static final Feature HUD_MAIN = f("hud.main", Category.HUD, "SkyPilot HUD", "SkyPilot HUD",
			"Goal step, next action and progress box.", "目標ステップ・次の行動・進捗のボックス。", "n");
	public static final Feature HUD_CONTEXT = f("hud.context", Category.HUD, "Area advice in HUD", "HUDの現在地アドバイス",
			"Adds advice for the area you are in to the HUD.", "現在地に応じたアドバイスをHUDに追加します。", "n");
	public static final Feature HUD_SESSION = f("hud.session", Category.HUD, "Session in HUD", "HUDのセッション表示",
			"Adds coins/h and XP/h of this session to the HUD.", "このセッションのコイン/時・XP/時をHUDに追加します。", "");
	public static final Feature HUD_COORDS = f("hud.coords", Category.HUD, "Coordinates", "座標",
			"Your X / Y / Z and facing direction.", "現在の X / Y / Z と向き。", "");
	public static final Feature HUD_FPS = f("hud.fps", Category.HUD, "FPS and ping", "FPS・Ping",
			"Frames per second and your ping.", "フレームレートとPing。", "");
	public static final Feature HUD_CLOCK = f("hud.clock", Category.HUD, "Clock and SkyBlock date", "時計・SkyBlock日付",
			"Real time plus the SkyBlock date and time.", "現実の時刻とSkyBlockの日付・時刻。", "n");
	public static final Feature HUD_EVENTS = f("hud.events", Category.HUD, "Event timers", "イベントタイマー",
			"Time until the next Dark Auction, Jacob's contest and calendar events.", "次のダークオークション・ジェイコブコンテスト・イベントまでの時間。", "nr");
	public static final Feature HUD_STATS = f("hud.player_stats", Category.HUD, "Health / mana bars", "体力・マナバー",
			"Health, defense and mana from the action bar as bars.", "アクションバーの体力・防御・マナをバーで表示。", "");
	public static final Feature HUD_SKILL = f("hud.skill_progress", Category.HUD, "Skill progress", "スキル進捗",
			"Last skill XP gain: level progress, XP per hour and time to the next level.", "直近のスキルXP: 進捗・XP/時・次レベルまでの時間。", "r");
	public static final Feature HUD_INV_FULL = f("hud.inventory_full", Category.HUD, "Full inventory warning", "インベントリ満杯の警告",
			"Warns when every inventory slot is used.", "インベントリが満杯になると警告します。", "r");
	public static final Feature HUD_LOW_HEALTH = f("hud.low_health", Category.HUD, "Low health border", "低体力の赤枠",
			"Red screen border below 25% health.", "体力25%未満で画面の縁を赤くします。", "");

	public static final Feature HUD_SPEED = f("hud.speed", Category.HUD, "Speed", "移動速度",
			"Your horizontal speed in blocks per second.", "水平方向の移動速度（ブロック/秒）。", "");
	public static final Feature HUD_COMPASS = f("hud.compass", Category.HUD, "Compass bar", "コンパスバー",
			"A compass strip at the top of the screen.", "画面上部に方角のバーを表示。", "");
	public static final Feature HUD_PLAYTIME = f("hud.playtime", Category.HUD, "Play time", "プレイ時間",
			"How long you have been on the server this session.", "このセッションでサーバーにいる時間。", "r");
	public static final Feature HUD_PURSE = f("hud.purse_change", Category.HUD, "Purse changes", "パースの増減",
			"Shows +/- coins for a few seconds when your purse (sidebar) changes.", "サイドバーのパースが変わると増減を数秒表示。", "r");
	public static final Feature HUD_PET = f("hud.pet", Category.HUD, "Active pet", "使用中のペット",
			"The tab list's \"Pet:\" widget (pet, level, XP).", "タブリストの「Pet:」欄（ペット・Lv・XP）。", "r");
	public static final Feature HUD_PROFILE = f("hud.profile_info", Category.HUD, "Profile info", "プロフィール情報",
			"Tab list lines: profile, SkyBlock level, bank, interest, fairy souls, skill average, soulflow.", "タブリストの プロフィール・SBレベル・銀行・利息・フェアリーソウル・スキル平均・ソウルフロー。", "");

	public static final Feature HUD_MAYOR = f("hud.mayor", Category.HUD, "Mayor and minister", "市長と大臣",
			"Current mayor, minister and their perks (Hypixel election data).", "現在の市長・大臣とパーク（Hypixelの選挙データ）。", "nr");
	public static final Feature HUD_EFFECTS = f("hud.effects", Category.HUD, "Active effects", "有効な効果",
			"The tab list's \"Active Effects\" widget.", "タブリストの「Active Effects」欄。", "");
	public static final Feature HUD_TIMERS = f("hud.timers", Category.HUD, "Timers widget", "タイマー欄",
			"The tab list's \"Timers:\" widget.", "タブリストの「Timers:」欄。", "r");
	public static final Feature HUD_DAILY = f("hud.daily_quests", Category.HUD, "Daily quests", "デイリークエスト",
			"The tab list's \"Daily Quests:\" widget.", "タブリストの「Daily Quests:」欄。", "");
	public static final Feature HUD_ESSENCE = f("hud.essence", Category.HUD, "Essence", "エッセンス",
			"The tab list's \"Essence\" widget.", "タブリストの「Essence」欄。", "");
	public static final Feature HUD_COLLECTION = f("hud.collection", Category.HUD, "Collection", "コレクション",
			"The tab list's \"Collection:\" widget.", "タブリストの「Collection:」欄。", "");
	public static final Feature HUD_SKILLS_TAB = f("hud.skills_tab", Category.HUD, "Skills widget", "スキル欄",
			"The tab list's \"Skills\" widget (skill average and progress).", "タブリストの「Skills」欄（スキル平均と進捗）。", "");

	// ---------------------------------------------------------------- mining
	public static final Feature MINING_COMMISSIONS = f("mining.commissions", Category.MINING, "Commissions", "コミッション",
			"Your commissions and their progress from the tab list.", "タブリストのコミッションと進捗を表示。", "r");
	public static final Feature MINING_POWDER = f("mining.powder", Category.MINING, "Powder tracker", "パウダートラッカー",
			"Powder amounts from the tab list and powder gained per hour.", "タブリストのパウダー量と1時間あたりの獲得量。", "r");
	public static final Feature MINING_ABILITY = f("mining.ability_alert", Category.MINING, "Ability ready alert", "アビリティ準備完了通知",
			"Title and sound when \"… is now available!\" (e.g. Mining Speed Boost).", "「… is now available!」（Mining Speed Boost等）でタイトルと音。", "r");

	public static final Feature MINING_FORGES = f("mining.forges", Category.MINING, "Forge timers", "フォージのタイマー",
			"The tab list's \"Forges:\" widget (what is forging, time left).", "タブリストの「Forges:」欄（鍛造中のアイテムと残り時間）。", "r");
	public static final Feature MINING_CRYSTALS = f("mining.crystals", Category.MINING, "Crystals", "クリスタル",
			"The tab list's \"Crystals:\" widget (Crystal Hollows).", "タブリストの「Crystals:」欄（クリスタルホロウズ）。", "r");
	public static final Feature MINING_HIDE_CHESTS = f("mining.hide_chest_spam", Category.MINING, "Hide treasure chest spam", "宝箱メッセージを隠す",
			"Hides \"You uncovered a treasure chest!\", lock-pick and \"Breaking Power\" lines.", "「You uncovered a treasure chest!」やBreaking Power不足の行を隠します。", "r");
	public static final Feature MINING_CHEST_TRACKER = f("mining.chest_tracker", Category.MINING, "Treasure chest tracker", "宝箱トラッカー",
			"Treasure chests opened and powder from them this session.", "このセッションで開けた宝箱の数と得たパウダー。", "r");

	// ---------------------------------------------------------------- farming
	public static final Feature FARMING_BPS = f("farming.bps", Category.FARMING, "Blocks per second", "BPS（毎秒破壊数）",
			"How many crops you break per second while farming.", "農業中に1秒あたり何ブロック壊しているか。", "r");
	public static final Feature FARMING_VISITOR = f("farming.visitor_cost", Category.FARMING, "Visitor cost", "訪問者のコスト",
			"In a Garden visitor's menu, the Bazaar cost of the requested items.", "ガーデン訪問者の画面で、要求アイテムのバザー費用を表示。", "nr");
	public static final Feature FARMING_CONTEST = f("farming.contest_alert", Category.FARMING, "Jacob's contest alert", "ジェイコブコンテスト通知",
			"Toast one minute before a Jacob's Farming Contest starts.", "ジェイコブの農業コンテスト開始1分前に通知。", "n");

	public static final Feature FARMING_GARDEN = f("farming.garden_info", Category.FARMING, "Garden info", "ガーデン情報",
			"Tab list: Garden level, copper, sowdust, composter and pests.", "タブリストのガーデンLv・コッパー・ソウダスト・コンポスター・害虫。", "r");
	public static final Feature FARMING_CONTEST_TAB = f("farming.contest_tab", Category.FARMING, "Jacob's contest info", "ジェイコブコンテスト情報",
			"The tab list's \"Jacob's Contest:\" widget (crops, your rank).", "タブリストの「Jacob's Contest:」欄（作物・順位）。", "r");

	public static final Feature FARMING_PEST_ALERT = f("farming.pest_alert", Category.FARMING, "Pest alert", "害虫の通知",
			"Title and sound when pests appear, with the plot (\"GROSS!\" / \"YUCK!\" lines).", "害虫が出たらプロットを添えてタイトルと音（GROSS!/YUCK!の行）。", "r");
	public static final Feature FARMING_VISITOR_ALERT = f("farming.visitor_alert", Category.FARMING, "Visitor arrival alert", "訪問者到着の通知",
			"Toast when the tab list's visitor count goes up, with the new names.", "タブリストの訪問者数が増えたら名前を添えて通知。", "r");
	public static final Feature FARMING_TRAPS = f("farming.pest_traps", Category.FARMING, "Pest traps", "害虫トラップ",
			"Pest trap count, full traps and traps without bait (tab list); toast when a trap fills up.", "トラップ数・満杯・エサなし（タブリスト）。満杯になったら通知。", "r");
	public static final Feature FARMING_MILESTONES = f("farming.crop_milestones", Category.FARMING, "Crop milestones", "作物マイルストーン",
			"The tab list's \"Crop Milestones:\" widget.", "タブリストの「Crop Milestones:」欄。", "");

	// ---------------------------------------------------------------- fishing
	public static final Feature FISHING_SEA_CREATURE = f("fishing.sea_creature_alert", Category.FISHING, "Rare sea creature alert", "レア海の生物の通知",
			"Title and sound when a Legendary or Mythic sea creature appears (names from SkyHanni-REPO).", "レジェンダリー/ミシックの海の生物が出たらタイトルと音（SkyHanni-REPOのデータ）。", "nr");
	public static final Feature FISHING_TRACKER = f("fishing.tracker", Category.FISHING, "Fishing tracker", "釣りトラッカー",
			"Sea creatures (by name) and treasure catches this session, per hour.", "このセッションの海の生物（名前別）とお宝の数・1時間あたり。", "nr");
	public static final Feature FISHING_BOBBER = f("fishing.bobber_timer", Category.FISHING, "Bobber timer", "ウキのタイマー",
			"Seconds since you cast your fishing rod.", "釣り竿を投げてからの秒数。", "");
	public static final Feature FISHING_TROPHY_ALERT = f("fishing.trophy_alert", Category.FISHING, "Trophy fish alert", "トロフィーフィッシュ通知",
			"Title for GOLD and DIAMOND trophy fish; all trophy fish are counted by the tracker.", "GOLD/DIAMONDのトロフィーフィッシュでタイトル表示（全種類をトラッカーで集計）。", "r");
	public static final Feature FISHING_TROPHY_TAB = f("fishing.trophy_tab", Category.FISHING, "Trophy fish info", "トロフィーフィッシュ情報",
			"The tab list's \"Trophy Fish:\" widget.", "タブリストの「Trophy Fish:」欄。", "");

	// ---------------------------------------------------------------- combat
	public static final Feature COMBAT_SLAYER_TAB = f("combat.slayer_tab", Category.COMBAT, "Slayer widget", "スレイヤー欄",
			"The tab list's \"Slayer:\" widget.", "タブリストの「Slayer:」欄。", "");
	public static final Feature COMBAT_BESTIARY = f("combat.bestiary", Category.COMBAT, "Bestiary widget", "モンスター図鑑欄",
			"The tab list's \"Bestiary:\" widget.", "タブリストの「Bestiary:」欄。", "");
	public static final Feature COMBAT_SLAYER_TRACKER = f("combat.slayer_tracker", Category.COMBAT, "Slayer tracker", "スレイヤートラッカー",
			"Bosses killed this session, average quest time and bosses per hour.", "このセッションのボス討伐数・平均時間・1時間あたり。", "r");
	public static final Feature COMBAT_SLAYER_ALERT = f("combat.slayer_alert", Category.COMBAT, "Boss spawned alert", "ボス出現通知",
			"Title when the sidebar switches to \"Slay the boss!\".", "サイドバーが「Slay the boss!」になったらタイトル表示。", "");
	public static final Feature COMBAT_RARE_DROP = f("combat.rare_drop_alert", Category.COMBAT, "Rare drop alert", "レアドロップ通知",
			"Title and sound for RARE / VERY RARE / CRAZY RARE / PET DROP messages.", "RARE DROP! などのメッセージでタイトルと音。", "r");
	public static final Feature COMBAT_DROP_TRACKER = f("combat.drop_tracker", Category.COMBAT, "Drop tracker", "ドロップトラッカー",
			"Counts the rare drops of this session.", "このセッションのレアドロップを集計します。", "");

	// ---------------------------------------------------------------- dungeons
	public static final Feature DUNGEONS_FILTER = f("dungeons.chat_filter", Category.DUNGEONS, "Dungeon chat filter", "ダンジョンのチャット整理",
			"Hides healing, damage-taken, orb, tether and cooldown spam (never player chat).", "回復・被ダメージ・オーブ・クールダウン等のスパムを隠します（プレイヤーの発言は対象外）。", "r");
	public static final Feature DUNGEONS_TIMER = f("dungeons.timer", Category.DUNGEONS, "Run timer", "ランタイマー",
			"Floor, time elapsed and cleared % from the sidebar, plus when the boss room started.", "サイドバーの階層・経過時間・攻略率と、ボス部屋に入った時刻。", "r");
	public static final Feature DUNGEONS_BOSS = f("dungeons.boss_alert", Category.DUNGEONS, "Boss room alert", "ボス部屋の通知",
			"Title when the first [BOSS] line of the run appears.", "そのランで最初の[BOSS]の発言でタイトル表示。", "");

	public static final Feature DUNGEONS_PUZZLES = f("dungeons.puzzles", Category.DUNGEONS, "Puzzle list with rules", "パズル一覧とルール",
			"The tab list's \"Puzzles:\" widget with each puzzle's rules from the wiki (no solver: the room is never read).", "タブリストの「Puzzles:」欄に各パズルのルール（Wiki）を添えて表示。部屋は読み取りません（ソルバーではありません）。", "nr");
	public static final Feature DUNGEONS_CHEST_PROFIT = f("dungeons.chest_profit", Category.DUNGEONS, "Reward chest profit", "報酬チェストの収支",
			"In dungeon (Wood … Bedrock) and Kuudra (Paid / Free) reward chests: contents value, opening cost and profit.", "ダンジョン/クードラの報酬チェストで、中身の価値・開封費用・収支を表示。", "nr");
	public static final Feature DUNGEONS_HIDE_SOLO = f("dungeons.hide_solo_class", Category.DUNGEONS, "Hide solo class line", "ソロクラス表示を隠す",
			"Hides \"Your Mage stats are doubled because you are the only player using this class!\".", "「…stats are doubled because you are the only player…」を隠します。", "r");
	public static final Feature DUNGEONS_HIDE_FAIRY = f("dungeons.hide_fairy", Category.DUNGEONS, "Hide fairy revive line", "フェアリーの復活石表示を隠す",
			"Hides \"… the Fairy: You killed me! Take this Revive Stone …\".", "「… the Fairy: You killed me! …」を隠します。", "r");
	public static final Feature DUNGEONS_SPLITS = f("dungeons.boss_splits", Category.DUNGEONS, "Boss phase splits", "ボスのフェーズ別タイム",
			"Time of each boss / phase from its first [BOSS] line (e.g. Maxor → Storm → Goldor → Necron).", "[BOSS]の最初の発言から各ボス・フェーズの時刻を表示（例: Maxor → Storm → Goldor → Necron）。", "r");

	// ---------------------------------------------------------------- rift
	public static final Feature RIFT_TIMER = f("rift.timer", Category.RIFT, "Rift time and Motes", "リフト時間とモート",
			"Rift time left (action bar) and Motes (sidebar) on the info HUD.", "残りリフト時間（アクションバー）とモート（サイドバー）を情報HUDに表示。", "r");
	public static final Feature RIFT_ALERT = f("rift.time_alert", Category.RIFT, "Low Rift time alert", "リフト時間が少ない通知",
			"Title when less than 60 and 20 seconds of Rift time are left.", "リフト時間が残り60秒・20秒を切るとタイトル表示。", "r");
	public static final Feature RIFT_AUTO_GUIDE = f("rift.auto_guide", Category.RIFT, "Rift guide on entry", "リフトに入るとガイド開始",
			"Entering the Rift starts the Rift guide (wiki progression) at your first unfinished area.", "リフトに入ると、まだ終えていない最初のエリアからリフトガイド（Wikiの進行順）を開始。", "nr");

	// ---------------------------------------------------------------- guides
	public static final Feature GUIDE_NEXT = f("guide.next_action", Category.GUIDES, "Next action on HUD", "次にやることをHUDに",
			"Your top \"what to do now\" action and where it is done, on the info HUD.", "「今やること」の1位と、その場所を情報HUDに表示。", "nr");
	public static final Feature GUIDE_AUTO_LINE = f("guide.auto_line", Category.GUIDES, "Guide line to next action", "次にやることへのガイド線",
			"When no other guide runs, the guide line points at where your top action is done (public places only).", "他のガイドがないとき、1位の行動をする場所へガイド線を表示（公開されている場所のみ）。", "n");

	// ---------------------------------------------------------------- market
	public static final Feature MARKET_SIDE_PANEL = f("market.side_panel", Category.MARKET, "AH / Bazaar flip panel", "AH・バザーのフリップ欄",
			"Flips beside Hypixel's Auction House / Bazaar windows.", "AH・バザー画面の横にフリップを表示。", "n");
	public static final Feature MARKET_FLIP_ALERTS = f("market.flip_alerts", Category.MARKET, "Flip alerts", "フリップ通知",
			"Toast and chat line for new flips matching your rules.", "ルールに合う新しいフリップをトーストとチャットで通知。", "n");
	public static final Feature MARKET_ALERT_SOUND = f("market.alert_sound", Category.MARKET, "Alert sound", "通知音",
			"Plays a sound with flip and price alerts.", "フリップ・価格通知で音を鳴らします。", "");
	public static final Feature MARKET_WATCHLIST = f("market.watchlist", Category.MARKET, "Price watchlist", "価格ウォッチ",
			"/skypilot watch <item> below|above <price>: alert when the price crosses it.", "/skypilot watch で指定価格を下回る/上回ると通知。", "nr");
	public static final Feature MARKET_FIRE_SALE = f("market.fire_sale_alert", Category.MARKET, "Fire sale alert", "ファイアセール通知",
			"Toast when a fire sale is running (Hypixel firesales data), once per sale.", "ファイアセール開催中に通知（Hypixelのデータ・1回ずつ）。", "nr");
	public static final Feature MARKET_DARK_AUCTION = f("market.dark_auction_alert", Category.MARKET, "Dark Auction alert", "ダークオークション通知",
			"Toast one minute before the Dark Auction.", "ダークオークション開始1分前に通知。", "n");

	// ---------------------------------------------------------------- tools
	public static final Feature TOOLS_CALC = f("tools.calculator", Category.TOOLS, "Calculator", "電卓",
			"/skypilot calc 1.5m*3+200k (k / m / b suffixes); click the result to copy it.", "/skypilot calc 1.5m*3+200k（k/m/b対応）。結果はクリックでコピー。", "r");
	public static final Feature TOOLS_REMINDERS = f("tools.reminders", Category.TOOLS, "Reminders", "リマインダー",
			"/skypilot remind 30m <text>: toast and sound when the time is up.", "/skypilot remind 30m <内容> で時間になったら通知。", "r");
	public static final Feature TOOLS_WAYPOINTS = f("tools.custom_waypoints", Category.TOOLS, "Your waypoints", "自分のウェイポイント",
			"/skypilot wp add <name> saves where you stand; wp show <name> draws a marker and a line to it.", "/skypilot wp add <名前> で現在地を保存、wp show で目印と線を表示。", "r");
	public static final Feature TOOLS_TODO = f("tools.todo", Category.TOOLS, "To-do list", "やることリスト",
			"/skypilot todo add <text> | done <n> | list | clear; open items show on the info HUD.", "/skypilot todo add <内容> など。未完了の項目を情報HUDに表示。", "r");
	public static final Feature TOOLS_STOPWATCH = f("tools.stopwatch", Category.TOOLS, "Stopwatch", "ストップウォッチ",
			"/skypilot timer start | lap | stop | reset, shown on the info HUD.", "/skypilot timer start / lap / stop / reset。情報HUDに表示。", "r");
	public static final Feature TOOLS_LOOKUP = f("tools.lookup", Category.TOOLS, "Item lookup", "アイテム検索",
			"/skypilot lookup <name> opens the item's page (value, recipes, drops).", "/skypilot lookup <名前> でアイテムのページ（価値・レシピ・ドロップ）を開く。", "nr");
	public static final Feature TOOLS_MOD_UPDATES = f("tools.mod_updates", Category.TOOLS, "Mod update check", "MODの更新確認",
			"In the mod manager (/skypilot mods), \"Check updates\" sends the SHA-1 of your mod files to your SkyPilot server, which asks Modrinth for newer versions. Shows a link only; nothing is downloaded.",
			"MOD管理画面（/skypilot mods）の「更新確認」で、MODファイルのSHA-1をSkyPilotサーバーへ送り、Modrinthに新しい版を問い合わせます。リンク表示のみで、ダウンロードはしません。", "n");
	public static final Feature TOOLS_SESSION = f("tools.session_tracking", Category.TOOLS, "Session tracking", "セッション記録",
			"Coins and XP gained this session (kept on this computer).", "このセッションのコインとXPを記録（このPCのみ）。", "");
	public static final Feature TOOLS_SESSION_UPLOAD = f("tools.session_upload", Category.TOOLS, "Upload sessions", "セッション送信",
			"Sends finished session statistics to your linked SkyPilot account.", "終了したセッション統計を連携中のSkyPilotへ送信します。", "n");

	// ---------------------------------------------------------------- performance
	// Drawing less is a client-side display choice: hidden entities still exist and react to
	// clicks and hits exactly as before; nothing the player does is cancelled.
	public static final Feature PERF_BACKGROUND_FPS = f("perf.background_fps", Category.PERFORMANCE, "Background FPS limit", "非アクティブ時のFPS制限",
			"While the game window is not focused, frames are capped (default 15 FPS; /skypilot perf).", "ゲーム画面が選択されていない間はFPSを抑えます（既定 15 FPS、/skypilot perf で変更）。", "r");
	public static final Feature PERF_MENU_FPS = f("perf.menu_fps", Category.PERFORMANCE, "Menu FPS limit", "メニュー表示中のFPS制限",
			"While a menu or inventory is open in a world, frames are capped (default 60 FPS).", "ワールド内でメニューやインベントリを開いている間はFPSを抑えます（既定 60 FPS）。", "r");
	public static final Feature PERF_FAR_ENTITIES = f("perf.hide_far_entities", Category.PERFORMANCE, "Hide far mobs and items", "遠くのモブ・アイテムを描画しない",
			"Mobs, items, armor stands and other entities (not players) farther than the set distance (default 48 blocks) are not drawn. They are still there and react as usual.",
			"設定距離（既定 48 ブロック）より遠いモブ・アイテム・防具立てなど（プレイヤー以外）を描画しません。存在や当たり判定はそのままです。", "");
	public static final Feature PERF_FAR_PLAYERS = f("perf.hide_far_players", Category.PERFORMANCE, "Hide far players", "遠くのプレイヤーを描画しない",
			"Other players farther than the set distance (default 32 blocks) are not drawn. Useful in busy hubs.", "設定距離（既定 32 ブロック）より遠い他のプレイヤーを描画しません。混雑したハブで効果的です。", "");
	public static final Feature PERF_DAMAGE_SPLASH = f("perf.hide_damage_splash", Category.PERFORMANCE, "Hide damage numbers", "ダメージ数値を描画しない",
			"The floating damage numbers (✧1,234✧ and similar) are not drawn.", "攻撃時に浮かぶダメージ数値（✧1,234✧ など）を描画しません。", "");
	public static final Feature PERF_FALLING_BLOCKS = f("perf.hide_falling_blocks", Category.PERFORMANCE, "Hide falling blocks", "落下ブロックを描画しない",
			"Falling sand, gravel and other falling-block entities are not drawn.", "落下中の砂・砂利などのブロックを描画しません。", "");
	public static final Feature PERF_FIREWORKS = f("perf.hide_fireworks", Category.PERFORMANCE, "Hide fireworks", "花火を描画しない",
			"Firework rockets and their sparks and flashes are not drawn.", "花火のロケットと火花・閃光を描画しません。", "");
	public static final Feature PERF_LIGHTNING = f("perf.hide_lightning", Category.PERFORMANCE, "Hide lightning bolts", "雷を描画しない",
			"Lightning bolts are not drawn (Minecraft's own \"Hide Lightning Flashes\" option removes the sky flash).", "雷の稲妻を描画しません（空の光はMinecraft標準の「雷の閃光を非表示」で消せます）。", "");
	public static final Feature PERF_EXPLOSIONS = f("perf.hide_explosions", Category.PERFORMANCE, "Hide explosion particles", "爆発パーティクルを描画しない",
			"Explosion clouds are not drawn.", "爆発の煙を描画しません。", "");
	public static final Feature PERF_HIT_PARTICLES = f("perf.hide_hit_particles", Category.PERFORMANCE, "Hide hit particles", "攻撃パーティクルを描画しない",
			"Critical-hit, enchanted-hit, damage, sweep and heart particles are not drawn.", "クリティカル・エンチャント・ダメージ・なぎ払い・ハートのパーティクルを描画しません。", "");
	public static final Feature PERF_BREAK_PARTICLES = f("perf.hide_break_particles", Category.PERFORMANCE, "Hide block break particles", "ブロック破壊パーティクルを描画しない",
			"The crumbs when blocks are mined or broken are not drawn (mining still works as usual).", "ブロックを掘る・壊すときの破片を描画しません（採掘自体はそのままです）。", "");
	public static final Feature PERF_PARTICLE_CAP = f("perf.particle_cap", Category.PERFORMANCE, "Particle limit", "パーティクル数の制限",
			"At most the set number of new particles per tick (default 150); the rest are skipped.", "1ティックに新しく出るパーティクルを設定数（既定 150）までにします。", "");
	public static final Feature PERF_LITE = f("perf.lite_mode", Category.PERFORMANCE, "SkyPilot lite mode", "SkyPilot軽量モード",
			"SkyPilot itself works less: the info HUD is rebuilt 4 times a second instead of every frame and movement tracking runs every 5 ticks.",
			"SkyPilot自身の処理を減らします。情報HUDの更新を毎フレームから1秒4回に、移動の記録を5ティックごとにします。", "");
	public static final Feature PERF_HUD_MEMORY = f("perf.hud_memory", Category.PERFORMANCE, "Memory on the info HUD", "メモリ使用量をHUDに表示",
			"Shows the game's memory use, e.g. \"RAM 1.8 / 4.0 GB\".", "ゲームのメモリ使用量（例: RAM 1.8 / 4.0 GB）を表示します。", "");
	public static final Feature PERF_HUD_COUNTS = f("perf.hud_counts", Category.PERFORMANCE, "Entities and particles on the info HUD", "エンティティ数・パーティクル数をHUDに表示",
			"Shows how many entities and particles the client is handling right now.", "今処理しているエンティティとパーティクルの数を表示します。", "");

	private Features() {
	}

	public static List<Feature> all() {
		return Collections.unmodifiableList(LIST);
	}

	public static Feature byId(String id) {
		return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
	}

	public static List<Feature> in(Category c) {
		List<Feature> out = new ArrayList<>();
		for (Feature f : LIST) if (f.category() == c) out.add(f);
		return out;
	}

	/** Matches the name, description or id in either language. */
	public static boolean matches(Feature f, String query) {
		if (query == null || query.isBlank()) return true;
		String q = query.strip().toLowerCase(Locale.ROOT);
		return (f.id() + " " + f.en() + " " + f.ja() + " " + f.descEn() + " " + f.descJa()).toLowerCase(Locale.ROOT).contains(q);
	}

	/** Whether a feature is on; features that existed before the registry keep their own config fields. */
	public static boolean isOn(SkyPilotConfig c, Feature f) {
		return switch (f.id()) {
			case "chat.click_translate" -> c.translation.clickToTranslate;
			case "tooltip.price" -> c.priceTooltip;
			case "inventory.item_browser" -> c.inventory.itemBrowser;
			case "inventory.chest_value" -> c.inventory.chestValue;
			case "hud.main" -> c.hud.enabled;
			case "hud.context" -> c.hud.showContext;
			case "hud.session" -> c.hud.showSession;
			case "market.side_panel" -> c.market.containerPanel;
			case "market.flip_alerts" -> c.market.alerts;
			case "market.alert_sound" -> c.market.alertSound;
			case "tools.session_tracking" -> c.session.enabled;
			case "tools.session_upload" -> c.session.upload;
			default -> Boolean.TRUE.equals(c.features.get(f.id()));
		};
	}

	public static void set(SkyPilotConfig c, Feature f, boolean on) {
		switch (f.id()) {
			case "chat.click_translate" -> c.translation.clickToTranslate = on;
			case "tooltip.price" -> c.priceTooltip = on;
			case "inventory.item_browser" -> c.inventory.itemBrowser = on;
			case "inventory.chest_value" -> c.inventory.chestValue = on;
			case "hud.main" -> c.hud.enabled = on;
			case "hud.context" -> c.hud.showContext = on;
			case "hud.session" -> c.hud.showSession = on;
			case "market.side_panel" -> c.market.containerPanel = on;
			case "market.flip_alerts" -> c.market.alerts = on;
			case "market.alert_sound" -> c.market.alertSound = on;
			case "tools.session_tracking" -> c.session.enabled = on;
			case "tools.session_upload" -> c.session.upload = on;
			default -> {
				if (on) c.features.put(f.id(), true);
				else c.features.remove(f.id());
			}
		}
	}

	/** Turns on the recommended features (never ones that need the translation consent). */
	public static int applyRecommended(SkyPilotConfig c) {
		int n = 0;
		for (Feature f : LIST) {
			if (f.recommended() && !f.consent() && !isOn(c, f)) {
				set(c, f, true);
				n++;
			}
		}
		return n;
	}

	/** Turns off every feature of a category (or all when {@code category} is null). */
	public static void disableAll(SkyPilotConfig c, Category category) {
		for (Feature f : LIST) if (category == null || f.category() == category) set(c, f, false);
	}

	public static long countOn(SkyPilotConfig c) {
		return LIST.stream().filter(f -> isOn(c, f)).count();
	}
}
