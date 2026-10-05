# SkyPilot

**Hypixel SkyBlock のための「個人成長オペレーティングシステム」。**
Minecraft ユーザー名と目標（日本語・英語の自然文）を入力すると、プロフィールを分析し、目標までの必要条件グラフとロードマップを作り、「今やるべきこと」を順位付けして提示します。市場（AH / バザー）の分析、各スキルのアドバイザー、ファームビルダー、Minecraft 26.2 用の Fabric MOD（ロードマップ HUD・価格チェック・セッション記録・EN→JA チャット翻訳）を含みます。

> SkyPilot は**情報ツール**です。プレイ・クリック・移動・売買・出品を代行する機能は一切ありません（[安全方針](#安全方針hypixel-ルール)）。Hypixel / Mojang / Microsoft とは無関係です。

---

## 目次

- [できること](#できること)
- [クイックスタート（Docker）](#クイックスタートdocker)
- [開発環境での起動](#開発環境での起動)
- [Minecraft MOD](#minecraft-mod)
- [テストとビルド確認](#テストとビルド確認)
- [リポジトリ構成](#リポジトリ構成)
- [安全方針（Hypixel ルール）](#安全方針hypixel-ルール)
- [プライバシーとセキュリティ](#プライバシーとセキュリティ)
- [データの出典と「不明」の扱い](#データの出典と不明の扱い)
- [前提・仮定（実装時に置いたもの）](#前提仮定実装時に置いたもの)
- [ライセンス](#ライセンス)

## できること

| 分野 | 内容 |
|---|---|
| プロフィール分析 | スキル・カタコンベ・スレイヤー・HOTM/パウダー・ガーデン・アクセサリー/MP・ペット・装備・コレクション・博物館・クードラ・リフト・チョコレート工場・ミニオン。資産だけでなく分野ごとの**進行度スコア**。API 設定で無効な項目は推測せず「不明 / Not available from API」と表示 |
| 目標 → グラフ → ロードマップ | 「Enderman Slayer 7とTerminatorが欲しい」のような自然文を解析し、ゲーム仕様上の必須条件（Hypixel items リソースの requirements）と SkyPilot の推奨（準備度など）を区別したグラフを生成。達成済みの条件は自動で除外。複数目標・優先度対応 |
| 今やるべきこと | 目標への影響・費用・時間・難易度・リスク・プレイスタイル（最短 / 安全重視 / 金策重視 / 戦闘中心 / Farming中心 / Mining中心）で順位付け。すべての推奨に信頼度と出典 |
| マーケット | AH 全ページ巡回 → NBT/Data Component 解析 → アイテム指紋（星・HPB・エンチャント・宝石・属性・ペットLv など）→ **最安BINだけに頼らない適正価格**（売買中央値・トリム平均・外れ値除去・流動性・ボラティリティ）→ フリップ（手数料・最大購入価格・売却時間・信頼度・リスク）。相場操作の疑いは **DO NOT FLIP**。バザー注文フリップ（約定時間推定）、クラフト / フォージ / Kat フリップ、価格履歴（1H〜90D）、バックテスト、通知ルール（タブ内通知 + Web Push）。**自動購入・自動出品は一切なし** |
| アドバイザー | 採掘（HOTM プランナー・パウダー計画・時給計算・装備/ドリル）、スレイヤー（ティア別期待値・必要装備 Required/Recommended/Luxury）、ダンジョン（フロア準備度）、クードラ、装備オプティマイザー（予算内でコスト効率順）、農業（作物別収入）+ **ファームビルダー**（上面図・側面図・3D・材料・手順）、釣り、伐採、リフト、アクセサリー（1MPあたりコスト順）、ペット、ミニオン（回収期間）、コレクション、博物館、デイリー、イベント/市長、資産内訳と売却アドバイザー |
| アシスタント | 構造化データ → 決定的な計算 → 回答。LLM（任意、Claude）は**言い換えのみ**で、価格・条件・レシピ・ドロップ率・XP を記憶から答えさせません。データにない場合は「確認できません」 |
| Fabric MOD (MC 26.2) | ロードマップ/進捗 HUD（位置・サイズ・不透明度を編集可）、状況アドバイザー（エリア連動）、価格チェック（ツールチップ・`/skypilot price`）、セッション記録、EN→JA チャット翻訳（初期値 OFF。自動モード、または各行の **[訳] ボタンをクリックしてその場で翻訳**・[原文] で戻す）、**ゲーム内メニュー（J キー）**（今やること・ロードマップ・進め方ガイド・アドバイス・推奨装備と作り方・AH/バザーフリップ・通知）、AH/バザー画面横のフリップパネルとフリップ通知（トースト）、**JEI 風アイテム一覧**（インベントリ右側・検索・レシピ/ドロップ/用途・説明欄に価値）、**チェスト/バックパックの価値合計**（高い順、名前クリックでスロットを縁取り）、ドワーフ鉱山・クリムゾン島・リフトの**進め方ガイド**（公開されている場所へのウェイポイントと直線ガイド）、農場の**建築ガイド**（未設置ブロックを表示）、ページ分けされた**設定画面**（翻訳プロバイダ・自分の API キー・接続テストなど）、`/skypilot` コマンド群。すべて表示のみで自動操作なし。連携は Device Authorization 方式 |

## クイックスタート（Docker）

必要なもの: Docker（Compose v2）

```bash
cp .env.example .env
```

`.env` に自分の Hypixel API キー（`HYPIXEL_API_KEY`）を設定するとプロフィール分析が使えます（マーケット・ファームビルダーなどはキーなしで動作します）。

```bash
docker compose up --build
```

http://localhost:3000 を開きます。構成: TimescaleDB（PostgreSQL 17）・Redis・Rust API（市場コレクター内蔵）・Next.js・Caddy（`/api` と WebSocket を API へ、それ以外を Web へ）。初回起動時に API が Hypixel リソースと NEU-REPO を `data/cache` に同期します（数十秒）。

## 開発環境での起動

必要なもの: Rust 1.88 以上（1.98 で確認）、Node.js 20.9+ と pnpm 12、（MOD 用）JDK 21 以上 — MOD のビルドに必要な JDK 25 は Gradle が自動で取得します。

```bash
cargo run --release -p skypilot-api
```

API は http://localhost:8080 で起動します。リポジトリ直下に `.env` があれば起動時に読み込みます（既に設定済みの環境変数が優先）。`DATABASE_URL` が未設定ならインメモリストア（再起動で消えます）、`REDIS_URL` が未設定ならプロセス内イベントバスを使います。

```bash
pnpm install
```

```bash
pnpm dev
```

Web は http://localhost:3000 （`/api` は Next.js のリライトで API へ転送）。

API のサブコマンド:

| コマンド | 内容 |
|---|---|
| `skypilot-api` | サーバー起動（既定） |
| `skypilot-api migrate` | DB マイグレーションのみ実行 |
| `skypilot-api sync-data` | Hypixel リソース + NEU-REPO を同期して終了 |
| `skypilot-api vapid-keys` | Web Push 用 VAPID 鍵を生成して表示 |
| `skypilot-api healthcheck` | コンテナ用ヘルスチェック |

市場コレクター単体の動作確認（1回だけ巡回して統計を表示）:

```bash
cargo run --release -p skypilot-collector -- --once
```

開発用サンプル: `SKYPILOT_DEV_SAMPLE_PROFILE=1` で起動すると、ユーザー名 `SampleUser` で匿名化済みテスト用プロフィール（`fixtures/`）を分析できます（API キーなしで UI を確認するため。初期値 OFF、画面に「開発用サンプル」と表示）。

環境変数の一覧は [.env.example](.env.example) を参照してください。

## Minecraft MOD

- 対象: **Minecraft Java Edition 26.2** + Fabric Loader 0.19.5 + Fabric API 0.161.0+26.2、Java 25
- ビルド（Windows は `.\gradlew.bat build`）:

```bash
cd apps/mod && ./gradlew build
```

成果物: `apps/mod/platform/mc26_2/build/libs/skypilot-<version>.jar`（共通モジュールを Jar-in-Jar で同梱）と SHA-256 チェックサム `.jar.sha256`。署名鍵（`SKYPILOT_SIGN_KEYSTORE` / `SKYPILOT_SIGN_ALIAS` / `SKYPILOT_SIGN_STOREPASS`）がある場合は `jarsigner` で署名され、`jarsigner -verify -strict` で検証できます。

- 導入: Fabric Loader と Fabric API を入れた 26.2 の `mods` フォルダに jar を置く
- 連携: ゲーム内で `/skypilot link` → 表示された URL（Web の `/link`）でコードを入力して承認。MOD が受け取るのは SkyPilot 専用の失効可能なトークンだけです（Microsoft / Minecraft のパスワードやセッショントークンは扱いません）。Web の「設定」から連携解除できます
- 接続先の変更: `/skypilot server https://your-skypilot.example`（http は localhost のみ許可）

| コマンド | 内容 |
|---|---|
| `/skypilot` / `help` / `status` | ヘルプ・状態 |
| `/skypilot link` / `unlink` | 連携 / 解除 |
| `/skypilot goal` / `next` / `roadmap` | 目標・次の行動・ロードマップ |
| `/skypilot market` / `mining` / `slayer` / `farming` | 各アドバイザー |
| `/skypilot price` | 手に持っているアイテムの価格 |
| `/skypilot translate <text>` / `translate mode <off\|manual\|npc_only\|party\|guild\|auto>` / `translate click on\|off` / `translate consent` | 翻訳（`click on` でチャット各行に [訳] ボタン。設定画面の「クリック翻訳」でも切替） |
| `/skypilot hud edit\|on\|off` | HUD の移動・サイズ変更（ドラッグ/スクロール）・表示切替 |
| `/skypilot session [reset \| upload on\|off]` | セッション統計 |
| `/skypilot settings` | 設定画面（キー割り当ても可能） |

MOD の構成はバージョン分離しています: `core` / `network` / `translation` / `session` / `hud`（Minecraft 非依存・単体テスト済み）と `platform/mc26_2`（26.2 固有の Fabric 層）。新しい Minecraft バージョンは `platform/mcXX_Y` を追加するだけで共通部分を再利用できます。詳しくは [docs/mod.md](docs/mod.md)。

## テストとビルド確認

| 対象 | コマンド |
|---|---|
| Rust（全クレート、プロパティテスト含む） | `cargo test --workspace` |
| Web（型チェック・Lint・本番ビルド） | `pnpm typecheck` / `pnpm lint` / `pnpm build` |
| MOD（単体テスト + jar） | `cd apps/mod && ./gradlew build` |
| MOD（実際の Minecraft 26.2 を起動してワールド内で検証） | `cd apps/mod && ./gradlew :platform:mc26_2:runClientGameTest` |
| MOD ↔ API 実通信（API 起動中に） | `cd apps/mod && SKYPILOT_E2E_URL=http://localhost:3000 ./gradlew :network:test` |
| スタック全体の E2E スモークテスト | `node tests/e2e/smoke.mjs http://localhost:3000` |
| TypeScript 型の再生成（Rust → TS） | `pnpm gen:types` |

`runClientGameTest` はウィンドウを開いて 26.2 クライアントを起動し、シングルプレイのワールドで SkyBlock 形式のサイドバー読取り・HUD 描画・コマンド・設定/HUD 編集画面・Text Component の保持・パーティーチャットの翻訳（元の行はそのまま、プレフィックスの色とクリックイベントを保持した翻訳行を追加、パーティー以外は無視）・クリック翻訳（[訳] ボタンをチャットのクリックと同じ経路で押すと、その行が翻訳に置き換わり、サーバー側のクリックイベントを保持、[原文] で元に戻る）・アイテムの custom_data 解析・ゲーム内メニュー/フリップパネル/通知・アイテム一覧とレシピ・チェスト価値・ガイドのウェイポイント・農場建築ガイド・設定画面（翻訳ページのキー入力欄が伏せ字になること）を検証し、スクリーンショットを `apps/mod/platform/mc26_2/build/run/clientGameTest/screenshots` に保存します。

Web Push はローカルのプッシュサービスを立てて送信し、VAPID JWT の署名検証と RFC 8291（aes128gcm）ペイロードの復号まで確認するテストがあります（`cargo test -p skypilot-api`）。

## リポジトリ構成

```
apps/
  api/          Rust (Axum/Tokio/SQLx) HTTP + WebSocket API
  web/          Next.js 16 / React 19 / Tailwind CSS v4 / shadcn 風 UI
  mod/          Fabric MOD (core, network, translation, session, hud, platform/mc26_2)
services/
  market-collector/      AH・終了オークション・バザーの巡回（429 を尊重）
  pricing-engine/        正規化・適正価格・相場操作検出・フリップ・バザー・クラフト・バックテスト・資産評価
  recommendation-engine/ 目標解析・目標グラフ・ロードマップ・行動最適化・各アドバイザー（Provider）
  profile-analyzer/      プロフィール/インベントリ解析・進行度スコア
  translation/           翻訳（辞書 → キャッシュ → 外部プロバイダ、サーキットブレーカー）・Claude クライアント
packages/
  protocol/     API の型（Rust）と自動生成 TypeScript 型（protocol/ts）
  calculations/ 統計・XP 表・手数料・採掘/農業/スレイヤー/ミニオン計算・ROI
  items/        NBT/Data Component パーサー・アイテム指紋
  skyblock-data/ データレジストリ（Hypixel リソース + NEU-REPO + 監修 TOML）と同期
  hypixel-client/ Hypixel API クライアント（キャッシュ・レート制限・リトライ・ブレーカー・メトリクス）
  ui/           Web 用 UI コンポーネント
data/           registry/*.toml（出典付き）、translation/dictionary.toml、farms/*.json
migrations/     PostgreSQL / TimescaleDB マイグレーション
fixtures/       実データを匿名化したテスト用データ（出典は fixtures/README.md）
tests/e2e/      スタック全体のスモークテスト
docker/         Dockerfile と Caddyfile
docs/           設計・API・データ出典・安全方針・MOD
```

## 安全方針（Hypixel ルール）

SkyPilot は次の機能を**実装していません**（今後も実装しません）: Auto Mine / Auto Farm / Auto Slayer / Auto Attack / Auto Aim / Auto Click / Auto Fishing / Auto Dungeon / Auto Puzzle / Auto Pathfinding / Auto Walking / Auto Bazaar Buy・Sell / Auto AH Buy・Sell / Auto Warp / Auto GUI / マクロ / パケット操作 / 移動・戦闘の自動化。ゲーム内の操作はすべてプレイヤー自身が行います。

- MOD はクライアントが既に表示している情報（サイドバー、タブリスト、チャット、アイテムのツールチップ）だけを読み、独自の HUD を描画します。パケット送信・入力のシミュレーション・隠しブロックの検出・通常見えない情報の取得は行いません。採掘ルートは Web で「どこを掘るか」を説明するだけです
- フリップ・バザーは表示のみ。`/viewauction <id>` はコピー用の文字列で、SkyPilot が実行することはありません
- Hypixel で許可が確認できない可能性がある機能は初期値 OFF: **チャット翻訳（OFF）**、**セッション統計のアップロード（OFF）**。HUD とツールチップの価格表示は表示専用の情報機能です
- Hypixel API: プロフィールはユーザーが分析を実行したときだけ取得（バックグラウンドでの定期取得なし）。キャッシュ、`RateLimit-*` ヘッダーの尊重、**429 は再送せず待機**、指数バックオフ（5xx/ネットワークのみ）、サーキットブレーカー、メトリクス。**API キーは 1 つ・ローテーションなし**・サーバー側のみ（ブラウザや MOD には渡しません）
- Hypixel 障害時は最後の有効なスナップショットを「Hypixel API temporarily unavailable. Last valid snapshot: Xm Ys ago」と明示して表示します

詳細は [docs/safety-and-compliance.md](docs/safety-and-compliance.md)。

## プライバシーとセキュリティ

- 要求しない情報: Microsoft パスワード、Minecraft パスワード、Microsoft リフレッシュトークン、Minecraft セッショントークン
- Web: HttpOnly / SameSite=Lax の Cookie（https では Secure）、ダブルサブミット CSRF トークン、nonce ベースの CSP（`strict-dynamic`）、`X-Frame-Options: DENY`、入力検証、IP ごとのレート制限、パスワードは argon2id。MOD トークンは SHA-256 で保存し、Web から失効可能
- チャット翻訳: 翻訳した行だけが SkyPilot サーバー（設定によりその翻訳プロバイダ）に送られます。クリック翻訳ではクリックした行だけが送られ、[訳] ボタンはクライアント内のコマンドを実行するだけでゲームサーバーには何も送りません。初回有効化時に MOD・Web の両方で明示し、同意が必要です。翻訳内容とアシスタントの会話はサーバーに保存しません
- 翻訳プロバイダはユーザーごとに選べます（サーバー既定 / 自分の DeepL キー / 自分の Claude キー / 辞書のみ）。自分のキーは SkyPilot サーバーにだけ送られ、AES-256-GCM で暗号化して保存（`SKYPILOT_SECRET_KEY`、未設定時は `data/cache/secret.key` を自動生成）、画面には末尾 4 文字しか表示しません。自分のキーの失敗は他のユーザーの翻訳（共有サーキットブレーカー）に影響しません
- 秘密情報は環境変数（本番ではシークレットマネージャー）からのみ読み込み、リポジトリには含めません
- MOD はコードをダウンロードしたり実行したりしません。リリースは SHA-256 と署名で検証します

## データの出典と「不明」の扱い

- Hypixel API（items / skills / collections / election / auctions / bazaar / profiles など）、[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)（MIT: XP 表、HOTM コスト式、レシピ、ドロップ率）、Hypixel SkyBlock Wiki（監修 TOML に URL と確認日を記載）、[EliteFarmers](https://github.com/EliteFarmers)（作物定数）、[SkyHanni-REPO](https://github.com/hannibal2/SkyHanni-REPO)（MIT: ガイドのウェイポイントに使う公開場所の名前と座標。収集物の位置は除外）
- 監修値には出典を付け、確認できない値（例: 一部フロアのカタコンベ XP）は**載せずに「不明」**として扱います。SkyPilot 独自の推定（準備度・収益の幅など）は UI 上で「推定（Heuristic）」と表示します
- 金策の時給は固定値ではなく、プロフィールと最新価格からの**幅のある推定**です。スレイヤーのドロップ利益は**期待値**であり保証ではありません

詳細は [docs/data-sources.md](docs/data-sources.md)。

## 前提・仮定（実装時に置いたもの）

不明点は質問で止めず、以下の仮定で実装しています（Hypixel ルールに関わるものは安全側に倒しています）。

1. **Minecraft 26.2 / Fabric**: 2026-10-05 時点の公式テンプレート（fabric-example-mod の 26.2 ブランチ）と、Loom が生成した 26.2 の jar を `javap` で確認した API 名（`GuiGraphicsExtractor` など）に基づいています
2. **アカウント**: Web は登録なしで使える匿名ワークスペース。複数ブラウザで使う場合だけ SkyPilot 用のログイン名/パスワードを追加します（Microsoft アカウントとは無関係）
3. **プロフィール取得**: ユーザー操作時のみ。進捗スナップショットは分析のたびに 1 件保存（自動ポーリングなし）
4. **市場データ**: 適正価格は売買履歴が必要なため、起動直後はフリップが少なく信頼度も低く表示されます。PostgreSQL 使用時は履歴が保存され、再起動後も引き継がれます
5. **ファームデザイン**: バニラのブロック配置ルール（サトウキビは水に隣接、サボテンは周囲にブロックなし、カカオはジャングルの原木 など）から生成した SkyPilot の参考レイアウトです。コミュニティの特定デザインの複製ではありません
6. **翻訳**: 外部翻訳プロバイダは任意（DeepL / LibreTranslate / Claude）。未設定でも SkyBlock 用語辞書のフレーズは翻訳できます
7. **通知**: Web Push は VAPID 鍵を自動生成（`data/cache`）。ブラウザのタブ内通知も利用可能。通知は情報のみで、売買は行いません

## ライセンス

MIT（[LICENSE](LICENSE)）。同梱する第三者データのライセンスは [fixtures/README.md](fixtures/README.md) と各 LICENSE ファイルを参照してください。
