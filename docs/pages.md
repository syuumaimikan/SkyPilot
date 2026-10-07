# GitHub Pages

公開するサイトのルートは `docs/` です。ビルド不要の静的HTML・CSS・JavaScriptで、外部フォントや外部スクリプトに依存しません。

`.github/workflows/docs-pages.yml` は `docs/` をPages用アーティファクトとしてアップロードします。`main` と `feat/client-wardrobe-tool-desk` への関連ファイルのpush、および手動実行に対応します。

リポジトリの **Settings → Pages → Build and deployment → Source → GitHub Actions** を選択してください。GitHub側のPages権限や環境保護設定によって、管理者による初回設定が必要です。ソースのpushだけでは公開完了を意味しません。成功したworkflowの `github-pages` environment URLで公開を確認できます。

標準の想定URL: https://syuumaimikan.github.io/SkyPilot/

ローカル確認:

```sh
python3 -m http.server 8001 --directory docs
# http://localhost:8001/
```

機能候補は [roadmap.md](roadmap.md)、演出の対応範囲は [client-effects.md](client-effects.md) を参照。Markdown資料は原文として配信し、Webホームに主要な導入手順を掲載しています。
