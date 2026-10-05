import {
  Activity,
  BookOpen,
  Boxes,
  Calendar,
  Coins,
  Factory,
  Fish,
  Gem,
  Hammer,
  Landmark,
  LayoutDashboard,
  Link,
  Map,
  MessageSquare,
  Package,
  PawPrint,
  Pickaxe,
  Search,
  Settings,
  Shield,
  Skull,
  Sparkles,
  Sprout,
  Store,
  Swords,
  TreePine,
  TrendingUp,
  User,
  Wallet,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  href: string;
  en: string;
  ja: string;
  icon: LucideIcon;
}

export interface NavGroup {
  en: string;
  ja: string;
  items: NavItem[];
}

export const NAV: NavGroup[] = [
  {
    en: "Overview",
    ja: "概要",
    items: [
      { href: "/", en: "Dashboard", ja: "ダッシュボード", icon: LayoutDashboard },
      { href: "/roadmap", en: "Roadmap", ja: "ロードマップ", icon: Map },
      { href: "/money", en: "Money Making", ja: "金策", icon: Coins },
      { href: "/daily", en: "Daily & Events", ja: "デイリー・イベント", icon: Calendar },
    ],
  },
  {
    en: "Market",
    ja: "マーケット",
    items: [
      { href: "/market/flips", en: "Live Flips", ja: "ライブフリップ", icon: TrendingUp },
      { href: "/market/bazaar", en: "Bazaar", ja: "バザー", icon: Store },
      { href: "/market/craft", en: "Craft & Forge", ja: "クラフト・フォージ", icon: Hammer },
      { href: "/market/price", en: "Price Explorer", ja: "価格エクスプローラー", icon: Search },
      { href: "/networth", en: "Networth", ja: "資産", icon: Wallet },
    ],
  },
  {
    en: "Combat",
    ja: "戦闘",
    items: [
      { href: "/combat/slayer", en: "Slayer", ja: "スレイヤー", icon: Skull },
      { href: "/combat/dungeons", en: "Dungeons", ja: "ダンジョン", icon: Landmark },
      { href: "/combat/kuudra", en: "Kuudra & Crimson", ja: "クードラ・クリムゾン", icon: Swords },
      { href: "/combat/gear", en: "Gear", ja: "装備", icon: Shield },
    ],
  },
  {
    en: "Skills",
    ja: "スキル",
    items: [
      { href: "/mining", en: "Mining", ja: "採掘", icon: Pickaxe },
      { href: "/farming", en: "Farming", ja: "農業", icon: Sprout },
      { href: "/farming/builder", en: "Farm Builder", ja: "ファームビルダー", icon: Factory },
      { href: "/fishing", en: "Fishing", ja: "釣り", icon: Fish },
      { href: "/foraging", en: "Foraging", ja: "伐採", icon: TreePine },
      { href: "/rift", en: "Rift", ja: "リフト", icon: Sparkles },
    ],
  },
  {
    en: "Collection",
    ja: "コレクション",
    items: [
      { href: "/accessories", en: "Accessories", ja: "アクセサリー", icon: Gem },
      { href: "/pets", en: "Pets", ja: "ペット", icon: PawPrint },
      { href: "/minions", en: "Minions", ja: "ミニオン", icon: Boxes },
      { href: "/collections", en: "Collections", ja: "コレクション", icon: Package },
      { href: "/museum", en: "Museum", ja: "博物館", icon: BookOpen },
    ],
  },
  {
    en: "You",
    ja: "あなた",
    items: [
      { href: "/profile", en: "Profile", ja: "プロフィール", icon: User },
      { href: "/assistant", en: "Assistant", ja: "アシスタント", icon: MessageSquare },
      { href: "/link", en: "Link Mod", ja: "MOD連携", icon: Link },
      { href: "/status", en: "System Status", ja: "システム状態", icon: Activity },
      { href: "/settings", en: "Settings", ja: "設定", icon: Settings },
    ],
  },
];
