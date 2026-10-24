# スマホでSkyBlockをプレイ

SkyPilotのホーム・メニューから「スマホでプレイ」(`/play`)を開きます。Android 0.5.0では右上メニューにも専用ショートカットがあります。

PCでMinecraft Java版を動かし、スマホのRustDeskから映像を見て操作します。PCとスマホは同じTailscaleアカウントへ接続します。Android側に公式RustDeskが必要です。接続先は既存の[プライベート接続設定](rustdesk.md)を使います。SkyPilotのWeb接続とRustDeskの映像接続は別の接続です。

1. PCでSkyPilot Launcherを開き、所有しているMinecraft Java版のアカウントでログインします。
2. 「プレイ」を押します。「起動したらHypixelに接続」がオンなら、ランチャーの既存Quick PlayからHypixelに接続します。
3. スマホでTailscaleをオンにし、SkyPilotの「スマホでプレイ」から「RustDeskでプレイ画面を開く」を押します。
4. RustDeskへPCの既存パスワードを入力します。SkyPilotにパスワードを保存しません。
5. PCのMinecraftでマルチプレイから `mc.hypixel.net` に接続し、ゲームメニューでSkyBlockを選ぶか、チャットで `/skyblock` を実行します。

PCとMinecraftを起動したままにしてください。PCをスリープさせると映像を操作できません。PCが再起動した場合は、RustDeskとランチャーを起動し直します。再起動・シャットダウン・Wake-on-LANはこの画面から行いません。

スマホは横向きでRustDeskのマウスモードを使います。キーボード入力はRustDesk側から行います。外付けキーボード・マウスがあると移動を操作しやすくなります。視点が動かない場合は、Escでカーソルを解除し、ウィンドウモードとRustDeskで利用できる相対入力設定を確認してください。タッチだけでの移動・視点操作や遅延の良否は実機で確認が必要です。

画面には手動チェックリスト、サーバーIP／コマンドのコピー、初期キー検索、画質の案内、ビットレートと時間からの通信量概算を用意しています。チェックや画質案内は接続診断や設定変更ではありません。映像・入力はRustDeskに表示され、SkyPilot内にMinecraftを組み込むものではありません。

参照: [Hypixel公式接続案内](https://support.hypixel.net/hc/en-us/articles/360019495360-How-to-Join-the-Hypixel-Server)、[RustDesk公式Android操作説明](https://rustdesk.com/docs/en/client/android/)。
