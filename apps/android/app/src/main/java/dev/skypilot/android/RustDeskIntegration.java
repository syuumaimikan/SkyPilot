package dev.skypilot.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class RustDeskIntegration {
    private static final String PACKAGE = "com.carriez.flutter_hbb";
    private RustDeskIntegration() {}

    static void settings(Activity activity, String server) {
        SharedPreferences prefs = activity.getSharedPreferences("skypilot", Context.MODE_PRIVATE);
        String key = "rustdesk_target:" + server;
        String defaultTarget = server != null && ServerUrl.isTailnetOrigin(server)
                ? Uri.parse(server).getHost() + ":21118" : "";
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad / 2, pad, 0);
        TextView help = new TextView(activity);
        help.setText("Tailscale接続中にPCを遠隔操作します。PCのRustDeskと直接IPアクセスが必要です。パスワードはRustDeskで入力してください。");
        content.addView(help);
        EditText target = new EditText(activity);
        target.setSingleLine(true);
        target.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        target.setHint("100.x.x.x:21118 / PC名.tailnet.ts.net:21118");
        target.setText(prefs.getString(key, defaultTarget));
        content.addView(target);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("RustDeskでPCに接続")
                .setView(content).setPositiveButton("保存して接続", null)
                .setNeutralButton("接続先をコピー", null).setNegativeButton("閉じる", null).create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String normalized = RustDeskTarget.normalize(target.getText().toString());
                if (normalized == null) { target.setError("TailscaleのIPv4または完全な.ts.net名とポートを入力してください"); return; }
                prefs.edit().putString(key, normalized).apply();
                dialog.dismiss();
                open(activity, normalized);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
                String normalized = RustDeskTarget.normalize(target.getText().toString());
                if (normalized == null) { target.setError("接続先を確認してください"); return; }
                ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("RustDesk接続先", normalized));
                Toast.makeText(activity, "接続先をコピーしました", Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }

    static void open(Activity activity, String target) {
        String url = RustDeskTarget.link(target);
        if (url == null) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(PACKAGE);
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(activity).setTitle("Android版RustDeskが必要です")
                    .setMessage("公式Android版をインストールしてから接続してください。接続先は保存されています。")
                    .setPositiveButton("公式ダウンロード", (dialog, which) -> {
                        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rustdesk/rustdesk/releases/latest"))); }
                        catch (ActivityNotFoundException missing) { Toast.makeText(activity, "ブラウザが見つかりません", Toast.LENGTH_SHORT).show(); }
                    }).setNegativeButton("閉じる", null).show();
        }
    }
}
