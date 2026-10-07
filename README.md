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
- [ランチャー](#ランチャー)
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
| Fabric MOD (MC 26.2) | **MOD管理**（`/skypilot mods`: 導入済みMODの一覧と詳細・依存関係、各MODの設定画面（Mod Menu API経由）、有効/無効の切り替え（再起動で反映）、リンク、任意の更新確認（Modrinthの情報とリンクのみ。ダウンロードはしません））、**適性**（ダンジョンのクラス適性・フロア準備度・スレイヤー準備度・目標までの道のり）、**ビルド**（スレイヤーごとの必須/推奨/最上位の装備構成と完成までの費用）、**金策**（あなたに合う金策と時給の幅・場所）、今やることの**ガイド線**（公開されている場所へ・ワープはチャット欄に入れるだけ）、リフト（残り時間・モート・時間切れ通知・入場時にガイド開始）、ダンジョン補助（パズル一覧とルール・ボスのフェーズ別タイム。ソルバーや隠し部屋表示はありません）、ロードマップ/進捗 HUD（位置・サイズ・不透明度を編集可）、状況アドバイザー（エリア連動）、価格チェック（ツールチップ・`/skypilot price`）、セッション記録、EN→JA チャット翻訳（初期値 OFF。自動モード、または各行の **[訳] ボタンをクリックしてその場で翻訳**・[原文] で戻す）、**ゲーム内メニュー（J キー）**（今やること・**状態・予定**（Hypixel サーバー状態・メンテナンス予定と通知・SkyBlock イベント予定。`/skypilot hypixel`）・ロードマップ・進め方ガイド・アドバイス・推奨装備と作り方・AH/バザーフリップ・通知）、AH/バザー画面横のフリップパネルとフリップ通知（トースト）、**JEI 風アイテム一覧**（インベントリ右側・検索・レシピ/ドロップ/用途・説明欄に価値）、**チェスト/バックパックの価値合計**（高い順、名前クリックでスロットを縁取り）、ドワーフ鉱山・クリムゾン島・リフトの**進め方ガイド**（公開されている場所へのウェイポイントと直線ガイド）、農場の**建築ガイド**（未設置ブロックを表示）、ページ分けされた**設定画面**（翻訳プロバイダ・自分の API キー・接続テストなど）、**機能ごとにオン/オフできる約130の機能**（ガーデン: 害虫通知・訪問者到着通知・害虫トラップ・作物マイルストーン、市長/大臣とパーク、ファイアセール通知、サックの価値、ダンジョン/クードラ報酬チェストの収支、タブリストの効果・タイマー・デイリー・エッセンス・コレクション・スキル・スレイヤー・図鑑の表示、ようこそ/安いドロップ/細かい通知/警告/Sky Mall/実績/パルクール/テレポート等の非表示、釣り: レア海の生物通知・釣りトラッカー・ウキのタイマー・トロフィーフィッシュ通知、ダンジョン: チャット整理・ランタイマー・ボス部屋通知、採掘: フォージ/クリスタル表示・宝箱スパム非表示・宝箱トラッカー、ガーデン情報、オークションのお得枠・高価アイテム枠・Ctrl+C で ID コピー、バザー差益、速度・コンパス・プレイ時間・パース増減・ペット・プロフィール情報、キーワード通知・チャット検索・サック通知/区切り線の非表示、やることリスト・ストップウォッチ・アイテム検索、チャットのタイムスタンプ・重複まとめ・クリックでコピー・座標→ウェイポイント・スパム非表示、説明欄の NPC 価格・クラフト費用・BIN 比較、レアリティ背景・スロット数字・検索ハイライト・R/U キー、座標・FPS・SkyBlock 時刻・イベントタイマー・体力/マナバー・スキル進捗、コミッション・パウダー、BPS・訪問者コスト、スレイヤー/ドロップトラッカー、価格ウォッチ、電卓・リマインダー・自分のウェイポイント、`/skypilot en` で日本語→英語など。新機能は初期状態ですべてオフ）、`/skypilot` コマンド群。すべて表示のみで自動操作なし。連携は Device Authorization 方式 |

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

- 導入: [ランチャー](#ランチャー)のプレイ（または「修復」）で自動的に用意できます。手動の場合は Fabric Loader を入れた 26.2 の `mods` フォルダに SkyPilot の jar を置きます（Fabric API は jar に同梱）
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
| `/skypilot perf` | 軽量化（非アクティブ時・メニュー中の FPS 制限、遠くのエンティティ・パーティクルを描画しない、軽量ビデオ設定と元に戻す） |
| `/skypilot afk [minutes <n> \| fps <n>]` | AFK ガード（離席中の通知・戻ったときのまとめ・放置中の FPS 低下。自動操作はしません） |

インベントリ右側の**アイテム一覧**は画面いっぱいに並び、カテゴリで絞り込み、マウスを重ねると説明文と価格（バザー・最安 BIN・NPC・クラフト費用）を表示します。左端をドラッグすると幅を変えたり画面端に収納したりできます（Ctrl+F で検索、R / U でレシピ・使い道）。AH・バザーのフリップ欄は並び替え・所持金内だけの表示・クリックでコマンドをチャット欄へ入力でき、「Your Bazaar Orders」では自分の注文が最良価格か上回られているかを色で示します（表示のみ）。

MOD の構成はバージョン分離しています: `core` / `network` / `translation` / `session` / `hud`（Minecraft 非依存・単体テスト済み）と `platform/mc26_2`（26.2 固有の Fabric 層）。新しい Minecraft バージョンは `platform/mcXX_Y` を追加するだけで共通部分を再利用できます。詳しくは [docs/mod.md](docs/mod.md)。

## ランチャー

**SkyPilot Launcher**（`apps/launcher`）は、SkyPilot（Fabric 26.2）を入れて Minecraft を起動するデスクトップアプリです。起動方法は 2 通りです。

| 方法 | サインイン | 起動するもの |
|---|---|---|
| **このランチャー**（サインイン時の既定） | ブラウザで Microsoft のデバイスコード方式 | SkyPilot Launcher（Mojang の本体・Java ランタイムを SHA-1 確認して使用） |
| **公式 Minecraft ランチャー** | 公式ランチャー | 公式ランチャー（このランチャーが「SkyPilot (SkyBlock)」プロファイルを用意） |

- **Microsoft サインイン**: パスワードは Microsoft のページにだけ入力します。更新用のサインイン情報はこの PC に Windows DPAPI で暗号化して保存し、ゲームのセッションはメモリ上だけで扱います。SkyPilot サーバーには送りません。
  - 初回だけ、自分で登録した Azure アプリの**クライアント ID**（Mojang の承認済み: <https://aka.ms/mce-reviewappid>）が必要です。SkyPilot は他のランチャーの ID を借用しません。未設定の間は公式ランチャー経由で起動します。
- **プレイ**: 足りないファイル（Fabric・SkyPilot MOD・Fabric API・ゲーム本体・アセット・Java 25）を確認・取得して起動。「起動したら Hypixel に接続」（Quick Play）にも対応。
- **インストーラーと自動更新**: ユーザー単位のインストーラー（管理者権限不要、スタートメニュー・デスクトップのショートカット）。更新は SkyPilot サーバーから取得し、**Ed25519 署名とインストーラーの SHA-256 を確認できたものだけ**を、クリックしたときにインストールします。
- 画面: ホーム（市長・イベント・ニュース・インストール状態）、MOD（互換性チェック・プリセット・トラブルシュート）、機能、スクリーンショット、ログとクラッシュ解析、ツール（取り込み・バックアップ・サーバー一覧）、設定（アカウントと起動・ゲーム・更新）、このランチャーについて
- ファイルは削除しません（置き換えた MOD は `.jar.disabled` に変えるだけ）

```bash
cd apps/mod
./gradlew :launcher:run                 # ソースから起動
./gradlew :launcher:jpackageImage       # Java 同梱のポータブル版 → apps/launcher/build/jpackage/SkyPilot Launcher/
./gradlew :launcher:jpackageInstaller   # Windows インストーラー（WiX 5 が必要）→ apps/launcher/build/installer/
./gradlew :launcher:launcherRelease     # 署名付きの更新ファイル（SKYPILOT_UPDATE_KEY が必要）→ apps/launcher/build/release/
```

詳しくは [docs/launcher.md](docs/launcher.md)。

## クライアントの外観・無料コスメ・パズルラボ

ランチャーと SkyPilot のゲーム内画面は、ネイビーを基調としたダークテーマです。起動時のホームからシングルプレイ、マルチプレイ、設定、ワードローブ、パズルラボへ移動できます。ワードローブでダークメニューをオフにすると標準のタイトル画面に戻せます。

- **ワードローブ**: ランチャーのサイドバー、ゲーム内ホーム、一般設定、または `/skypilot wardrobe`。検索・カテゴリ・装備カードと回転／ズーム可能な3D試着スタジオを備えます。6種類のマント、4種類のヘッドコスメ、3種類の翼を無料で選べ、3つのスロットを同時に装備できます。ランチャーとゲーム内ホームではオリジナルのプレビュー用モデルを使い、ワールド内のスタジオでは実際の自分のスキンとコスメを表示します。自分のクライアントの三人称視点にだけ表示し、サーバーや他のプレイヤーへ送信しません。
- **3D スキン**: ワードローブで有効化。Minecraft が読み込んだ 64×64 スキンの外側レイヤーを不透明なピクセルごとに立体化します。通常体型・スリム体型に対応し、頭・腕・脚のポーズに追従します。初期値はオフで、他プレイヤーの表示は変えません。
- **画質・軽量化**: 一般設定または `/skypilot perf` から開けます。既存の FPS・パーティクル・エンティティ描画の軽量化に加え、Minecraft 標準の画質をまとめて上げる「リッチ画質」を用意しています。変更前の値を保存し、「元に戻す」で復元できます。外部シェーダーパック用の描画エンジンは含みません。
- **手動入力ソルバー**: `/skypilot puzzle` またはゲーム内ホーム。三目並べは行順に `X` / `O` / `.` の9文字と手番を入力すると最善手の行・列を表示します。迷路は `#`（壁）、`.`（通路）、`S`（始点）、`G`（終点）を使い、行を `/` で区切ります。例: `S../##./..G`。最短経路を矢印で表示します。ワールドの読み取りやクリック・移動の代行は行いません。

Fabric API も SkyPilot の jar に同梱しているため、Fabric Loader 上では SkyPilot の jar だけでこれらの機能を使えます。課金やコスメ専用アカウントは不要です。ランチャーで変更する場合はゲームを終了して保存し、次回起動で反映してください。

## テストとビルド確認

| 対象 | コマンド |
|---|---|
| Rust（全クレート、プロパティテスト含む） | `cargo test --workspace` |
| Web（型チェック・Lint・本番ビルド） | `pnpm typecheck` / `pnpm lint` / `pnpm build` |
| MOD・ランチャー（単体テスト + jar） | `cd apps/mod && ./gradlew build` |
| ランチャーのアプリ化（Java 同梱）・インストーラー | `cd apps/mod && ./gradlew :launcher:jpackageImage :launcher:jpackageInstaller` |
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
  launcher/     SkyPilot Launcher（Microsoft サインインで直接起動 / 公式ランチャー用プロファイル。Swing + FlatLaf）
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
docs/           設計・API・データ出典・安全方針・MOD・ランチャー
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
- ランチャーは Microsoft のサインインを扱わず（公式ランチャーに任せます）、ダウンロードは Fabric のメタデータと SHA-512 固定の Fabric API だけです

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

## ツールデスクと小さな楽しみ

ランチャーの **ツールデスク**、ゲーム内ホーム、または `/skypilot desk` から利用できます。バックエンドや追加MODは不要です。

- **集中タイマー**: 1〜180分、一時停止／再開、リセット、終了通知。ゲーム内では画面を閉じても継続し、完了セッション数を表示します。ランチャーでも他ページに移動して継続します。アプリ終了後のタイマー再開には対応しません。
- **メモ**: 1400文字まで端末に保存。ランチャーとゲーム内で同じ設定ファイルを共有します。ゲーム内の入力欄では `\n` で改行を入力できます。競合を避けるため、ゲーム実行中はランチャー側から保存できません。
- **電卓**: GUIから `1.5m * 3 + 200k` のような式を計算し、結果をコピーできます。
- **抽選**: カンマ区切りの候補からひとつを選ぶ、コイントス、D6／D20のサイコロ。
- **ToDo**: GUIから追加・完了切り替え・完了分の削除。既存の `/skypilot todo` と同じリストです。ゲーム内はページ送りにも対応します。
- **2048**: オフラインのミニゲーム。矢印キー／ボタンで操作し、一手戻す・新しいゲームに対応。ゲーム内ではWASDも使え、ベストスコアを端末に保存します。盤面はそのアプリのセッション中に保持されます。
- **数独**: `/skypilot puzzle` に追加。81マスを手動入力し、空欄は `0` または `.`。探索量を制限して解をひとつ表示します。三目並べ・迷路・数独の入力を画面内で別々に保持し、長い結果はスクロール・コピーできます。

**ワードローブの新機能**: お気に入り登録と絞り込み、ランダムコーデ、6色の配色切り替え、名前付きコーデの保存／着用／削除（最大8件）をランチャー・ゲーム内に追加。ランチャーでは保存済みの状態に戻す、試着プレビューをPNGに保存する操作も用意しています。新しいコーデで既存の保存コーデを自動削除することはありません。

### Visual Studio とドキュメント

ホームの Visual Studio または `/skypilot visual` から、ズーム、4種類のキル演出、ヒットマーカー、コンボ、低体力枠、カスタム照準、方位HUD、5種類の内蔵GPU画面シェーダーを設定できます。初期状態ではオフです。内蔵シェーダーは画面後処理で、影・水面反射や外部パックの読み込みは含みません。キル判定は受信した攻撃者情報に依存します。

- [画面演出の詳細](docs/client-effects.md)
- [追加機能候補・完了条件](docs/roadmap.md)
- [GitHub Pages設定とローカル確認](docs/pages.md) — サイトの公開ルートは `docs/`
