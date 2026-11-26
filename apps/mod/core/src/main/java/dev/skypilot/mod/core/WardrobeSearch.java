package dev.skypilot.mod.core;

import java.util.*;

/**
 * Smart bilingual search, rarity/attribute filtering, and sorting engine for the Wardrobe catalog,
 * shared across both the standalone in-game Wardrobe mod and the SkyPilot Launcher.
 *
 * <p>Supports:
 * <ul>
 *   <li>Multi-keyword AND search across half-width and full-width spaces</li>
 *   <li>Full-width ASCII to half-width normalization</li>
 *   <li>Hiragana ↔ Katakana script folding (e.g. {@code うーぱー} matches {@code ウーパールーパー})</li>
 *   <li>Bilingual semantic tag matching (e.g. {@code かわいい}, {@code 炎}, {@code 氷}, {@code 桜},
 *       {@code 猫}, {@code 犬}, {@code 龍}, {@code 天使}, {@code アニメ}, {@code 発光})</li>
 *   <li>Rarity filtering &amp; 6 sort orders</li>
 * </ul>
 */
public final class WardrobeSearch {
    public enum SortOrder {
        DEFAULT("Default", "おすすめ順"),
        RARITY_DESC("Rarity ↓", "レア度順"),
        NAME_ASC("Name A-Z", "名前順"),
        EQUIPPED_FIRST("Equipped", "装備中優先"),
        FAVORITES_FIRST("Favorites", "お気に入り順"),
        ANIMATED_FIRST("Animated", "アニメ優先");

        public final String en;
        public final String ja;

        SortOrder(String en, String ja) {
            this.en = en;
            this.ja = ja;
        }

        public String label(boolean japanese) {
            return japanese ? ja : en;
        }

        public SortOrder next() {
            SortOrder[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public static final List<String> RARITY_FILTERS = List.of("all", "mythic", "legendary", "epic", "rare", "custom");

    public static String rarityLabel(String filter, boolean ja) {
        return switch (filter == null ? "all" : filter) {
            case "mythic" -> "MYTHIC";
            case "legendary" -> "LEGEND";
            case "epic" -> "EPIC";
            case "rare" -> "RARE";
            case "custom" -> ja ? "自作" : "CUSTOM";
            default -> ja ? "全レア" : "All Rarity";
        };
    }

    public static String nextRarity(String current) {
        int idx = RARITY_FILTERS.indexOf(current);
        return RARITY_FILTERS.get((idx + 1) % RARITY_FILTERS.size());
    }

    /** Semantic bilingual synonym groups. Each group shares search discoverability without leaking unrelated tokens. */
    private static final List<String[]> TAG_GROUPS = List.of(
            new String[]{"kawaii", "cute", "pastel", "ribbon", "strawberry", "mochi", "plush", "candy", "macaron", "sweet", "ゆめかわ", "かわいい", "可愛い", "パステル", "リボン", "いちご", "苺", "もち", "ぬいぐるみ", "お菓子", "スイーツ"},
            new String[]{"cool", "dark", "shadow", "abyss", "reaper", "obsidian", "gothic", "vampire", "phantom", "かっこいい", "ダーク", "闇", "漆黒", "死神", "ゴシック", "吸血鬼"},
            new String[]{"fire", "flame", "ember", "inferno", "blaze", "phoenix", "magma", "volcanic", "solar", "sun", "炎", "火", "灼熱", "紅蓮", "不死鳥", "フェニックス", "溶岩", "太陽"},
            new String[]{"ice", "frost", "glacier", "snow", "winter", "arctic", "crystal", "氷", "雪", "氷河", "冬", "極寒", "結晶", "フロスト"},
            new String[]{"sakura", "cherry", "blossom", "spring", "flower", "floral", "petal", "桜", "さくら", "春", "花", "花びら"},
            new String[]{"cat", "kitten", "neko", "calico", "kitty", "tiger", "leopard", "猫", "ねこ", "ネコ", "子猫", "三毛猫", "虎", "トラ"},
            new String[]{"dog", "puppy", "inu", "shiba", "corgi", "wolf", "fenrir", "husky", "samoyed", "pomeranian", "retriever", "terrier", "collie", "犬", "いぬ", "イヌ", "柴犬", "コーギー", "狼", "オオカミ", "ハスキー", "ポメラニアン"},
            new String[]{"dragon", "drake", "wyvern", "serpent", "龍", "竜", "ドラゴン", "ワイバーン", "蛇"},
            new String[]{"angel", "seraph", "halo", "holy", "celestial", "heaven", "divine", "天使", "熾天使", "光輪", "聖", "天界"},
            new String[]{"demon", "devil", "oni", "imp", "horn", "cerberus", "悪魔", "鬼", "角", "冥界", "ケルベロス"},
            new String[]{"star", "astral", "galaxy", "cosmic", "nebula", "moon", "lunar", "constellation", "planet", "space", "星", "宇宙", "銀河", "月", "星座", "惑星", "天体"},
            new String[]{"rainbow", "prism", "aurora", "chromatic", "iridescent", "虹", "プリズム", "オーロラ", "極彩"},
            new String[]{"fox", "kitsune", "fennec", "狐", "きつね", "キツネ", "フェネック"},
            new String[]{"bunny", "rabbit", "hare", "兎", "うさぎ", "ウサギ", "ラビット"},
            new String[]{"butterfly", "moth", "fairy", "bee", "蝶", "ちょう", "チョウ", "妖精", "フェアリー", "蜂"},
            new String[]{"ocean", "water", "tide", "hydro", "wave", "bubble", "koi", "whale", "jellyfish", "axolotl", "海", "水", "波", "泡", "鯉", "クジラ", "クラゲ", "ウーパールーパー"},
            new String[]{"japanese", "samurai", "katana", "torii", "kimono", "origami", "fuji", "raijin", "和風", "侍", "刀", "鳥居", "着物", "折り紙", "富士", "雷神"},
            new String[]{"cyber", "neon", "mech", "mecha", "tech", "holo", "drone", "ufo", "digital", "サイバー", "ネオン", "メカ", "近未来", "ホログラム", "ドローン"},
            new String[]{"crown", "royal", "monarch", "tiara", "diadem", "king", "queen", "empress", "王冠", "ロイヤル", "ティアラ", "王者", "姫"},
            new String[]{"sword", "blade", "scythe", "katana", "剣", "刃", "大鎌"},
            new String[]{"bear", "panda", "polar", "grizzly", "熊", "くま", "クマ", "パンダ", "シロクマ"},
            new String[]{"bird", "owl", "duck", "chick", "penguin", "crane", "raven", "鳥", "フクロウ", "ひよこ", "ペンギン", "鶴", "カラス"},
            new String[]{"cape", "cloak", "mantle", "マント", "クローク"},
            new String[]{"wing", "wings", "back", "backwear", "翼", "羽", "ウィング", "背面"},
            new String[]{"hat", "head", "headwear", "hood", "beret", "beanie", "頭", "帽子", "ヘッド", "ベレー"},
            new String[]{"face", "mask", "glasses", "visor", "monocle", "eyepatch", "顔", "仮面", "眼鏡", "めがね", "メガネ", "眼帯", "バイザー", "マスク"},
            new String[]{"shoulder", "scarf", "muffler", "pauldron", "collar", "mantle", "stole", "肩", "マフラー", "スカーフ", "肩当て", "首飾り", "羽衣"},
            new String[]{"waist", "tail", "tails", "obi", "belt", "nine", "腰", "尻尾", "しっぽ", "シッポ", "九尾", "帯", "ベルト"},
            new String[]{"feet", "foot", "ankle", "anklet", "boots", "足", "足元", "足羽根", "アンクレット", "足輪", "フット"},
            new String[]{"aura", "orbit", "ring", "オーラ", "リング", "輪"},
            new String[]{"pet", "floating", "ペット", "浮遊"},
            new String[]{"companion", "ground", "相棒", "コンパニオン"}
    );

    private WardrobeSearch() {}

    public static List<CosmeticCatalog.Item> filterAndSort(
            List<CosmeticCatalog.Item> source,
            SkyPilotConfig.Appearance appearance,
            String category,
            String rarityFilter,
            boolean favoritesOnly,
            boolean customOnly,
            boolean animatedOnly,
            String rawQuery,
            SortOrder sortOrder
    ) {
        String[] tokens = tokenize(rawQuery);
        List<CosmeticCatalog.Item> out = new ArrayList<>();
        for (var item : source) {
            if (!matchesCategory(item, appearance, category)) continue;
            if (favoritesOnly && (appearance == null || !appearance.favorites.contains(item.id()))) continue;
            if (customOnly && !item.custom()) continue;
            if (animatedOnly && !item.animated()) continue;
            if (!matchesRarity(item, rarityFilter)) continue;
            if (tokens.length > 0 && !matchesQuery(item, tokens)) continue;
            out.add(item);
        }

        SortOrder order = sortOrder == null ? SortOrder.DEFAULT : sortOrder;
        if (order != SortOrder.DEFAULT) {
            out.sort(comparatorFor(order, appearance));
        }
        return out;
    }

    private static boolean matchesCategory(CosmeticCatalog.Item item, SkyPilotConfig.Appearance a, String category) {
        if (category == null || category.isEmpty() || "all".equals(category)) return true;
        if ("favorites".equals(category)) return a != null && a.favorites.contains(item.id());
        if ("custom".equals(category)) return item.custom();
        return item.slot().equals(category);
    }

    private static boolean matchesRarity(CosmeticCatalog.Item item, String rarityFilter) {
        if (rarityFilter == null || rarityFilter.isEmpty() || "all".equals(rarityFilter)) return true;
        if ("custom".equals(rarityFilter)) return item.custom();
        return rarityFilter.equalsIgnoreCase(item.rarity());
    }

    private static boolean matchesQuery(CosmeticCatalog.Item item, String[] tokens) {
        String haystack = buildSearchIndex(item);
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            if (haystack.contains(tok)) continue;
            // Check semantic bilingual alias match ONLY if the query token matches an alias group that this item belongs to
            if (!matchesSemanticGroup(haystack, tok)) return false;
        }
        return true;
    }

    private static boolean matchesSemanticGroup(String haystack, String tok) {
        if (tok.length() < 2) return false;
        for (String[] group : TAG_GROUPS) {
            boolean queryInGroup = false;
            for (String alias : group) {
                String normAlias = normalize(alias);
                if (normAlias.equals(tok) || (tok.length() >= 2 && normAlias.startsWith(tok))) {
                    queryInGroup = true;
                    break;
                }
            }
            if (!queryInGroup) continue;
            for (String alias : group) {
                if (haystack.contains(normalize(alias))) return true;
            }
        }
        return false;
    }

    private static String buildSearchIndex(CosmeticCatalog.Item item) {
        StringBuilder sb = new StringBuilder(160);
        sb.append(item.id()).append(' ')
                .append(item.name()).append(' ')
                .append(item.ja()).append(' ')
                .append(item.slot()).append(' ')
                .append(item.rarity()).append(' ');
        if (item.description() != null) sb.append(item.description()).append(' ');
        if (item.descriptionJa() != null) sb.append(item.descriptionJa()).append(' ');
        if (item.animated()) sb.append("animated アニメ 動く flow ");
        if (item.emissive()) sb.append("glow emissive 発光 光る ");
        if (item.custom()) sb.append("custom 自作 ");
        return normalize(sb.toString());
    }

    private static String[] tokenize(String raw) {
        if (raw == null || raw.isBlank()) return new String[0];
        String norm = normalize(raw).trim();
        if (norm.isEmpty()) return new String[0];
        return norm.split("[\\s,、・/]+");
    }

    /**
     * Normalizes full-width ASCII to half-width, lowercases, and folds Hiragana (U+3041..U+3096)
     * into Katakana (U+30A1..U+30F6) so Japanese kana searches match seamlessly either way.
     */
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\u3000') {
                out.append(' ');
            } else if (ch >= '\uFF01' && ch <= '\uFF5E') {
                out.append(Character.toLowerCase((char) (ch - 0xFEE0)));
            } else if (ch >= '\u3041' && ch <= '\u3096') {
                out.append((char) (ch + 0x60));
            } else {
                out.append(Character.toLowerCase(ch));
            }
        }
        return out.toString();
    }

    private static Comparator<CosmeticCatalog.Item> comparatorFor(SortOrder order, SkyPilotConfig.Appearance a) {
        return switch (order) {
            case RARITY_DESC -> Comparator.comparingInt((CosmeticCatalog.Item i) -> -rarityRank(i))
                    .thenComparing(CosmeticCatalog.Item::name, String.CASE_INSENSITIVE_ORDER);
            case NAME_ASC -> Comparator.comparing(CosmeticCatalog.Item::name, String.CASE_INSENSITIVE_ORDER);
            case EQUIPPED_FIRST -> Comparator.comparingInt((CosmeticCatalog.Item i) -> (a != null && i.equipped(a)) ? 0 : 1)
                    .thenComparingInt(i -> -rarityRank(i));
            case FAVORITES_FIRST -> Comparator.comparingInt((CosmeticCatalog.Item i) -> (a != null && a.favorites.contains(i.id())) ? 0 : 1)
                    .thenComparingInt(i -> -rarityRank(i));
            case ANIMATED_FIRST -> Comparator.comparingInt((CosmeticCatalog.Item i) -> i.animated() ? 0 : 1)
                    .thenComparingInt(i -> -rarityRank(i));
            default -> (x, y) -> 0;
        };
    }

    private static int rarityRank(CosmeticCatalog.Item item) {
        if (item.custom()) return 5;
        return switch (item.rarity() == null ? "rare" : item.rarity()) {
            case "mythic" -> 4;
            case "legendary" -> 3;
            case "epic" -> 2;
            case "rare" -> 1;
            default -> 0;
        };
    }
}

