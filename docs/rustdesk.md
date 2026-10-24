# RustDesk連携

SkyPilotの「その他 → PCリモート操作 · RustDesk」、またはAndroid 0.4.0以降の右上メニュー「RustDeskでPCに接続」から、Android版RustDeskを開き、PCを遠隔操作できます。操作画面はRustDeskに表示されます。

## スマホから使う

1. PCとスマホを同じTailscaleアカウントで接続します。
2. スマホへ[公式Android版RustDesk](https://github.com/rustdesk/rustdesk/releases/latest)をインストールします。
3. SkyPilot Androidを0.4.0以降へ更新します。0.3.1以降では右上の「アプリ更新・自動更新 → 今すぐ更新を確認」が使えます。
4. 「PCリモート操作 · RustDesk」を開き、PCのTailscale IPv4と直接アクセスのポートを入力します。例: `100.x.x.x:21118`。`PC名.tailnet.ts.net:21118`も利用できます。Webページでは現在のSkyPilot接続先のTailscale DNS名を初期表示します。
5. 「保存」、続いて「RustDeskでPCに接続」を押し、RustDesk側でPCの既存パスワードを入力するか接続承認を行います。SkyPilotに戻る場合はAndroidのアプリ切り替えを使います。

PCの接続先をコピーし、RustDeskへ手動で貼り付ける方法もあります。AndroidアプリにRustDeskがない場合は公式ダウンロード案内を表示します。ブラウザから使う場合はOS側にRustDeskのURLハンドラーが必要です。

接続先の保存は端末内です。Webページのブラウザ保存とAndroid上部メニューの保存は独立しています。RustDeskのパスワードは保存・転送しません。入力できる接続先はTailscaleのIPv4範囲と完全な`.ts.net`名に限定し、通常の公開RustDesk IDによる接続は扱いません。

## PC側の準備

既に設定済みのRustDeskを使用する場合は、PCの「設定 → セキュリティ」で直接IPアクセスを有効にし、IPホワイトリストを自分の端末のTailscale IPv4だけに限定します。標準ポートは21118です。

このリポジトリでは、管理者PowerShellから次の設定スクリプトを使えます。

```powershell
.\scripts\rustdesk.ps1 -Action Start
.\scripts\rustdesk.ps1 -Action Status
.\scripts\rustdesk.ps1 -Action Stop
```

`Start`は稼働中のTailscaleからPC所有者と同じUserIDの端末だけを取得し、RustDeskのIPホワイトリストを設定します。PC自身のTailscale IPv4も診断用に含めます。直接IPアクセスを有効にし、既存のパスワード・認証方式を維持したまま、RustDeskのバックグラウンドサーバーを起動します。Windows Firewallの追加許可もPCのTailscale IPv4・21118/TCP・所有者端末のIPだけに限定します。

設定変更前にRustDeskを閉じてください。稼働中のRustDeskや接続中のセッションをスクリプトから自動終了しません。復旧用の設定と起動情報は、Git対象外かつWeb配信対象外の`.runtime/rustdesk/`へ保存します。`Stop`はスクリプトが起動したプロセスを終了し、変更した4項目と追加したFirewallルールを元に戻します。

許可されるIPは`Start`時点の一覧です。新しい端末をTailscaleへ追加した場合は、`Stop`・`Start`で更新します。PC再起動後は`Start`を実行してください。Windowsのログイン前やUAC画面の操作には別途RustDeskのサービス構成が必要です。

## 検証と実装

- Android・Webとも、公開IP、資格情報、URLのパス・クエリ・フラグメント、無効なポートを拒否します。
- Android WebViewからの起動は、SkyPilotと同じオリジンのページ上で、利用者がタップしたメインフレームの接続リンクだけに限定します。起動先パッケージは`com.carriez.flutter_hbb`固定です。
- RustDeskのパスワード変更・サーバー設定用URIやJavaScriptからのネイティブ権限呼び出しは許可しません。
- テスト: `node scripts/test-rustdesk.mjs`、Androidの`:app:testDebugUnitTest`・`:app:lintDebug`。
- PCポート到達とAPK配信は検証できます。スマホ実機のRustDesk起動・画面操作は実機での確認が必要です。

参照: [RustDeskの直接IPアクセス・ホワイトリスト設定](https://rustdesk.com/docs/en/self-host/client-configuration/advanced-settings/#direct-server)、[Android URLハンドラー](https://github.com/rustdesk/rustdesk/blob/master/flutter/android/app/src/main/AndroidManifest.xml)、[接続URIの実装](https://github.com/rustdesk/rustdesk/blob/master/flutter/lib/common.dart)。
