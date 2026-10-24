# SkyPilot Android

Android 8.0（API 26）以上向けのSkyPilotアプリです。既存のNext.js WebアプリをAndroid WebView内で開き、プロフィール分析・目標とロードマップ・市場・各アドバイザー・MOD連携を利用できます。[スマホでSkyBlockをプレイ](phone-play.md)はPCのMinecraft Java版へRustDeskで接続する方式です。

0.5.0では右上メニューに「スマホでSkyBlockをプレイ」「プレイラボ」「冒険ノート」を追加しました。[プレイラボ](play-lab.md)の計算設定と補給表は端末内に保存します。

## インストールと接続

1. `apps/android/app/build/outputs/apk/debug/app-debug.apk` をスマホに転送して開きます。Androidから求められた場合は、そのファイルを開くアプリに「不明なアプリのインストール」を許可します。
2. 初回画面で**SkyPilotのWebサイトのURL**を入力し、「接続する」を押します。例: `https://your-skypilot.example`。URLはルートのみ指定できます。Rust APIの8080番ポートではなく、Webと`/api`の両方にアクセスできる接続先を使います。
3. ダッシュボードでMinecraftユーザー名と目標を入力します。サーバー側でHypixel APIキーを設定しておく必要があります。市場など、キーなしで使える機能もあります。

上部の更新ボタンで再読み込み、右上のメニューで接続先変更・ブラウザ表示、下部のタブでホーム・目標・相場・プロフィールへ移動できます。「その他」で全機能を開きます。Androidの戻る操作で前のページに戻れます。

スマホには独立した匿名ワークスペースが作成されます。PCブラウザのセッションを自動共有しません。アプリを終了してもCookieと接続先は保存します。アプリデータを削除するとセッションが失われます。接続先を変更した場合は、そのサーバーのワークスペースを使います。

## PCの開発サーバーにつなぐ

デバッグAPKではHTTPでローカル接続できます。

- 実機: PCとスマホを同じWi-Fiに接続し、`http://<PCのLAN IPv4>:3000` を入力。PCのファイアウォールでプライベートネットワークから3000番ポートへの接続が許可され、WebサーバーがLANから接続可能な状態である必要があります。
- Androidエミュレーター: `http://10.0.2.2:3000`。
- USB接続した実機: `adb reverse tcp:3000 tcp:3000` を実行し、`http://localhost:3000`。

WebとAPIの起動は[README](../README.md#開発環境での起動)を参照してください。スマホ自身の`localhost`は、通常PCを指しません。

## 別のWi-Fi・モバイル回線から使う（Tailscale）

PCとAndroidにTailscaleを導入し、**同じアカウント**で接続します。AndroidのVPN接続はTailscaleの画面で有効にしてください。PC側ではDocker DesktopとSkyPilotを起動したままにします。

PCのPowerShellで、リポジトリ直下から実行します（Docker Compose 2.24.4以上）。

```powershell
docker compose up -d
.\scripts\tailscale.ps1
```

表示された`https://<PC名>.<tailnet名>.ts.net:9443`を、Androidアプリの「Tailscale」モードに入力します。この端末用にビルド時にURLを指定したAPKでは、初回にURLが入力されています。旧版から更新した場合は、右上の接続設定を開き、Tailscaleモードと新しいURLを選んでください。

設定スクリプトはPCの所有者アカウントを取得し、次の構成を用意します。

```text
Android → Tailscale Serve（HTTPS）→ localhost:3001のアカウント確認 → SkyPilot Web / API
```

Tailscale Serveが検証した`Tailscale-User-Login`ヘッダーを使い、PCの所有者と同じアカウントからのアクセスだけを許可します。他のアカウントと未認証アクセスは403で拒否します。標準Web入口の3000番ポートと確認用入口の3001番ポートはPCのループバックだけにバインドされます。この構成では、LANのIPでの直接アクセスも閉じます。SkyPilotのCookieには`Secure`を設定し、MOD連携URLとCORSもTailscaleのHTTPSアドレスに合わせます。

初回にTailscaleからHTTPS有効化のURLが表示された場合は、その案内を完了してからスクリプトを再実行します。使用するのは[Serve](https://tailscale.com/docs/features/tailscale-serve)です。接続はTailscaleのアクセス制御にも従います。Funnelによる一般公開は行いません。

```powershell
.\scripts\tailscale.ps1 -Action Status  # 状態とURL
.\scripts\tailscale.ps1 -Action Stop    # Tailscale経由の入口を停止し、通常のローカル設定へ戻す
```

標準のHTTPSポートは9443です。他のアプリと競合する場合は、`-HttpsPort`で別のポートを指定します。別ポートで設定を変更する前に、以前のSkyPilotの入口を`-Action Stop`で停止してください。設定スクリプトは他のServeサービスを上書きしません。接続情報はGit対象外の`.runtime/`に保存します。PCのTailscaleのDNS名を変更した場合は再設定が必要です。

アプリは回線の復帰・切り替え時に、接続エラーが出ているページを自動で再読み込みします。画面表示中にAPIだけが切断された場合は、Web側の再取得・市場WebSocketの再接続が動作します。

## アプリの自動更新

更新機能を持たない旧版からは、最初の1回だけ新しいAPKを手動で上書きインストールしてください。接続先・Cookie・ワークスペースはアプリデータを削除せずに引き継がれます。

右上メニューの「アプリ更新・自動更新」から、APKの更新許可と更新通知を設定します。自動更新は初期状態でオンです。アプリを開いたときと、Androidが実行を許可する約6時間ごとのジョブで確認します。自動ダウンロードは従量課金でない回線とバッテリー残量の条件に従います。手動の「今すぐ更新を確認」はモバイル回線でも利用できます。PCとTailscaleへの接続が必要です。

更新は同じHTTPS接続先の`/api/android/latest.json`とAPKを使います。サイズ・SHA-256・パッケージ名・新しいversionCode・インストール済みアプリと同じ署名を検証します。HTTP更新、別オリジンへのリダイレクト、別の署名、ダウングレードは受け入れません。

Android 12以降は、OSの条件を満たす場合に確認なしの自己更新を要求します。確認が必要な場合はAndroidの確認画面か通知から続行します。Android 8〜11ではインストール時の確認が必要です。通知を許可しない場合も、アプリを開いて手動更新できます。[Android公式の更新条件](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))を参照してください。実行間隔はAndroidの省電力設定などにより遅れる場合があります。

PCで新しいAPKを配信する手順:

```powershell
# apps/android/app/build.gradle の versionCode と versionName を増やしてビルド
cd apps/android
.\gradlew.bat :app:assembleDebug '-PdefaultServerUrl=https://my-pc.my-tailnet.ts.net:9443'
cd ../..
.\scripts\publish-android.ps1
```

配信用ファイルはGit対象外の`.runtime/android-releases/`に保存し、Caddyへ読み取り専用でマウントします。Tailscale構成ではAPKもPC所有者の同じアカウントだけが取得できます。署名鍵が変わるAPKと、同じversionCodeで内容を変えたAPKの公開はスクリプトが拒否します。別のPCやCIで生成したデバッグ署名は一致しない場合があるため、更新は同じ署名鍵でビルドしてください。

## RustDeskでPCを遠隔操作

Android 0.4.0以降では右上の「RustDeskでPCに接続」からAndroid版RustDeskを開けます。「その他 → PCリモート操作 · RustDesk」にも接続ページがあります。Tailscaleで自分の端末から接続する設定・PC側の起動手順は[RustDesk連携](rustdesk.md)を参照してください。

## ビルド

`apps/android` はMOD/ランチャーとは独立したGradleプロジェクトです。

必要: JDK 17、Android SDK Platform 36、Build Tools 35.0.0。Android Studioで`apps/android`を開くか、`ANDROID_HOME`にSDKのパスを設定します。ローカルの`local.properties`で`sdk.dir`を指定する方法も使えます（Gitには含めません）。

Windows PowerShell:

```powershell
cd apps/android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux/macOS:

```bash
cd apps/android
bash ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`。デバッグ用署名済みで、そのまま端末にインストールできます。`adb install -r app/build/outputs/apk/debug/app-debug.apk`でも導入できます。

接続先を初回画面に入力しておくビルド（HTTPS URLのみ）:

```powershell
.\gradlew.bat :app:assembleDebug '-PdefaultServerUrl=https://my-pc.my-tailnet.ts.net:9443'
```

配布用は`assembleRelease`で作成し、所有する鍵で署名します。署名鍵はリポジトリに保存しません。リリース版はHTTPSのみ許可します。Google Playへの公開やリリース署名の設定は含んでいません。

## 対応範囲

「Hypixel API キーが拒否されました」と表示された場合、アプリへの接続はできています。PC側のキー更新が必要です。[更新手順](hypixel-key.md)を参照してください。APKの再インストールは不要です。

- 画面は稼働中のSkyPilotサーバーから取得します。オフライン利用・サーバーの同梱には対応していません。
- 現在のアプリ内画面はWebViewです。バックグラウンドでのWeb Push通知には対応していません。市場のアプリ内通知は画面を開いている間に利用できます。
- 接続エラー・HTTPエラー・証明書エラー・30秒の読み込みタイムアウト時には再接続と設定変更の画面を表示します。APIのみのエラーは既存Web画面が表示します。
- Androidのシステムバーとキーボードの余白に対応し、画面回転時にはWebViewの履歴を復元します。
- 接続先と同じオリジンだけをアプリ内で開きます。外部HTTP/HTTPSリンクはブラウザへ渡します。ファイルアクセス・第三者Cookie・混在コンテンツ・JavaScriptからのネイティブ機能呼び出しは無効です。証明書エラーを無視しません。

Androidの[WebViewの公式ガイド](https://developer.android.com/develop/ui/views/layout/webapps/webview)と[AGP 8.13の互換性情報](https://developer.android.com/build/releases/agp-8-13-0-release-notes)を参照しています。
