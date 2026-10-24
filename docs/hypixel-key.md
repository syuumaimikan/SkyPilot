# Hypixel API キーの更新

`HYPIXEL_KEY_INVALID` は、SkyPilotサーバーが使用するキーをHypixelが拒否した状態です。スマホからSkyPilotへの接続はできています。Tailscale、Wi-Fi、Minecraftユーザー名を変更しても解消しません。ゲーム内のAPI公開設定とは別の問題です。

申請したアプリに`Review of this application is pending`と表示されている場合は、Hypixelの審査待ちです。[公式の申請案内](https://developer.hypixel.net/create/)では、アプリは審査・承認されてから利用できるとされています。キーの再生成で審査待ちは解消しません。動作確認には、[ダッシュボードのHome](https://developer.hypixel.net/dashboard/)にある`Development Key`を使用します。これは開発用で、継続利用には申請の承認が必要です。拒否応答だけでは、失効と未承認を区別できません。

1. PCで[Hypixel Developer Dashboard](https://developer.hypixel.net/)にログインし、有効なキーを取得します。開発用キーは動作確認用です。継続して使用するアプリはダッシュボードで登録してください。
2. リポジトリ直下のPowerShellで実行します。

   ```powershell
   .\scripts\hypixel-key.ps1
   ```

3. キーをPCの入力欄に貼り付け、Enterを押します。入力文字は表示されません。キーをチャットやスマホに入力する必要はありません。
4. APIの起動が終わったらスマホのSkyPilotを再読み込みし、「プロフィールを選択」を押します。APIは起動時のキャッシュ読み込みに時間がかかることがあります。

スクリプトはHTTPSの`API-Key`ヘッダーでキーを検証し、Hypixelが承認した場合だけ、Git対象外の`.env`を書き換えてAPIコンテナを作り直します。現在のAPIがTailscale用HTTPSアドレスを使用している場合は、保存済みの接続設定を使ってSecure Cookieや接続先URLを維持します。Web、入口のCaddy、Tailscale Serve、データベースの再設定は行いません。新しいキーはサーバーのローカル設定ファイルに保存され、ブラウザへは配信されません。

現在の`.env`のキーを検証するだけの場合:

```powershell
.\scripts\hypixel-key.ps1 -Check
```

この操作は設定の保存・コンテナ再起動を行いません。Hypixelの拒否や一時的な通信障害も区別して表示します。

キーを更新できるまで、相場・バザーなどのマーケット機能は引き続き使えます。
