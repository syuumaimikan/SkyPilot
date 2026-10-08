# クライアント拡張（SkyPilot 0.1.1 / Minecraft 26.2 / Fabric）

## ランチャーの拡張パック

先にホームの「修復」でSkyPilotとFabric APIを導入し、Minecraftを終了してから
MODページの「拡張パック」を開きます。

| パック | 内容 |
|---|---|
| 軽量化 | Sodium 0.9.2（描画）、FerriteCore 9.0.0（メモリ）、Lithium 0.25.3（ゲーム処理） |
| 軽量化＋シェーダー | 軽量化パック＋Iris 1.11.4 |
| SkyBlock補助 | Skyblocker 6.10.4＋Fabric Language Kotlin 1.14.1 |
| すべて | 上記6個 |

26.2用の固定リリースをModrinthのCDNから取得します。ファイル名・サイズ・SHA-512・
公開ページ・ライセンスは `apps/launcher/src/main/resources/companion-mods.json` に記録しています。
各jarのFabricメタデータと、既存MODを含めた依存関係も検査します。
全ダウンロードの検証が終わるまで追加せず、途中で失敗した場合は今回追加したファイルを戻します。
別バージョンや無効化済みの同じMODがある場合は上書きせず停止します。
導入済みの同一ファイルは再ダウンロードしません。無効化は既存のMOD管理画面から行えます。

Iris用のシェーダーパックは含めていません。Irisのビデオ設定から別途選択します。
外部シェーダーが有効な間、SkyPilotの画面効果は休止します。
Skyblockerの機能はその設定画面で管理します。価格表示やHUDが重複する場合は
SkyPilotまたはSkyblocker側の該当機能をオフにしてください。

Shine 3.0の公式配布は1.21.11用、Patcherは旧Forge向けです。
Shine・Smooth Boot・NEU・Patcherを26.2対応と偽って同梱することはしていません。
新しい演出・比較・HUDはSkyPilotの独自実装です。他MODの全機能を再実装したものではありません。

## SkyPilot内蔵の追加機能

追加機能は初期状態ではオフです。

| 分野 | 機能と入口 |
|---|---|
| 軽量化 | `/skypilot perf`: 自動粒子予算、遠距離の粒子を省く、フレーム安定性HUD |
| 画面効果 | ビジュアルスタジオ: bloom（明るい画素の光彩）、sharp（シャープ化）、vibrant（彩度）、sunset（暖色）を追加。キー設定の画面効果切替にも対応 |
| 環境演出 | ビジュアルスタジオ → 環境: 着地の砂埃、雨の水しぶき、葉の近くの夜の蛍、洞窟の塵、水中の泡、疾走の軌跡。密度1～4 |
| 装備・持ち物 | 便利HUD → 便利: 防具4部位の耐久値、効果と残り時間（最大3個）、空き3枠以下の警告、装備の耐久値10%以下の警告 |
| 診断 | 便利HUD → 便利: ヒープ使用量、起動からの累計GC回数・処理時間。HUDデータの更新は最大毎秒4回 |
| 装備比較 | 便利HUD → 便利 → 装備の数値比較: 武器は手持ち、防具は同じ部位と比較。両方の英語説明欄にある18種類の数値から差分を表示 |

自動粒子予算は目標FPS（30～240）を下回ると生成上限を下げ、回復すると徐々に戻します。
バックグラウンドやメニュー中はFPSの低下を負荷と判断しません。
設定した粒子数を超えて増やすことはありません。遠距離の制限はカメラから8～128ブロックです。
環境演出も粒子制限に従い、読み込まれている近くの地形だけを参照します。

フレーム安定性は直近最大300フレームのp95・p99時間と、p99時間から計算したFPSです。
一般的な「1% low平均」とは別の指標です。メニュー・非フォーカス中は測定をリセットします。
画面効果のbloomは画面上の明るい画素をぼかす効果で、動的な世界照明や影のエンジンではありません。

装備比較はDamage、Strength、Crit Chance、Crit Damage、Health、Defense、Intelligence、Speed、
Magic Find、Pet Luck、Mining Speed、Mining Fortune、Farming Fortune、Foraging Fortune、
Sea Creature Chance、Fishing Speed、Vitality、Ferocityに対応します。
不明な数値を0で補わず、単位が異なるものも比較しません。割合の差はパーセントポイントです。
最終ダメージや装備セットの効果を予測する機能ではありません。

## 起動時クラッシュの修正

26.2ではタイトル画面のアイテムコンポーネントがまだ結び付いていない場合があります。
Fire Sale通知がこの段階で `ItemStack` を作り、`Components not bound yet` で落ちる問題を修正しました。
ワールドが準備されるまでイベント通知とリマインダーの処理を待ちます。
タイトル画面・切断後に届く他の通知は空のアイコンで表示します。
価格ウォッチの非同期応答も、ワールドを離れた後は通知に使いません。

更新版ランチャーを起動し、ホームの「修復」を実行して、ゲームフォルダのSkyPilotを更新してください。

## 検証

`apps/mod` から:

```powershell
./gradlew.bat :core:test :launcher:test :platform:mc26_2:runClientGameTest
```

ダウンロード済みの固定jarを使う導入・併用テスト:

```powershell
$env:SKYPILOT_COMPANION_TEST_DIR = '<固定jarを置いたフォルダの絶対パス>'
./gradlew.bat :launcher:test :platform:mc26_2:runClientGameTest -PcompanionSmokeDir=$env:SKYPILOT_COMPANION_TEST_DIR
```

タイトル画面のFire Saleとリマインダー、装備比較、全画面効果の描画、HUDをゲーム内テストで検証します。
通常の自動テストはネットワークからMODを取得しません。

公式情報: [Sodium](https://github.com/CaffeineMC/sodium)、
[Iris](https://github.com/IrisShaders/Iris)、
[FerriteCore](https://github.com/malte0811/FerriteCore)、
[Lithium](https://github.com/CaffeineMC/lithium-fabric)、
[Skyblocker](https://github.com/SkyblockerMod/Skyblocker)、
[Shineの配布](https://shine-wiki.pages.dev/download)、
[Smooth Boot](https://github.com/UltimateBoomer/mc-smoothboot)、
[Patcher](https://sk1er.club/mods/patcher)。
