package dev.skypilot.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class UpdateResult extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation") Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            AppUpdates.prefs(context).edit().putString("update_status", "Androidのインストール確認を待っています。更新通知をタップしてください。").apply();
            if (confirmation != null) AppUpdates.confirm(context, confirmation);
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            AppUpdates.prefs(context).edit().putString("update_status", "更新が完了しました。").apply();
            AppUpdates.notify(context, "アプリの更新が完了しました", new Intent(context, MainActivity.class));
        } else {
            AppUpdates.prefs(context).edit().putString("update_status", "更新が完了しませんでした。アプリ更新のメニューから再試行できます。").apply();
            AppUpdates.notify(context, "更新を再試行してください", new Intent(context, MainActivity.class).setAction(AppUpdates.ACTION_SETTINGS));
        }
    }
}
