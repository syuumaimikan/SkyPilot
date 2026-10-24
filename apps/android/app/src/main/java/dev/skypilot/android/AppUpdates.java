package dev.skypilot.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppUpdates {
    static final String ACTION_SETTINGS = "dev.skypilot.android.UPDATE_SETTINGS";
    static final String CHANNEL = "app-updates";
    static final int NOTICE = 73, JOB = 73;
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static java.lang.ref.WeakReference<Activity> foreground = new java.lang.ref.WeakReference<>(null);
    private static Intent pendingConfirmation;
    private static volatile boolean backgroundStopped;
    static void stopBackground() { backgroundStopped = true; }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("skypilot", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("auto_updates", true); }
    static void schedule(Context c) {
        JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (!enabled(c)) { scheduler.cancel(JOB); return; }
        JobInfo job = new JobInfo.Builder(JOB, new ComponentName(c, UpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresBatteryNotLow(true)
                .setPeriodic(6 * 60 * 60 * 1000L).setPersisted(true).build();
        scheduler.schedule(job);
    }
    static void onOpen(Activity activity) {
        foreground = new java.lang.ref.WeakReference<>(activity);
        if (pendingConfirmation != null) {
            Intent intent = pendingConfirmation; pendingConfirmation = null;
            try { activity.startActivity(intent); } catch (Exception ignored) { }
        }
        schedule(activity);
        if (enabled(activity) && System.currentTimeMillis() - prefs(activity).getLong("update_checked_at", 0) > 6 * 60 * 60 * 1000L) {
            check(activity.getApplicationContext(), null, false, null);
        }
    }
    static void onPause(Activity activity) { if (foreground.get() == activity) foreground.clear(); }
    static void confirm(Context context, Intent intent) {
        Activity activity = foreground.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            try { activity.startActivity(intent); return; } catch (Exception ignored) { }
        }
        pendingConfirmation = intent;
        notify(context, "更新の確認をタップしてください", intent);
    }
    static void settings(Activity activity) {
        boolean canInstall = activity.getPackageManager().canRequestPackageInstalls();
        String last = prefs(activity).getString("update_status", "まだ確認していません");
        String[] items = { "今すぐ更新を確認", enabled(activity) ? "自動更新をオフにする" : "自動更新をオンにする",
                canInstall ? "APKの更新許可：設定済み" : "APKの更新を許可する", "更新通知を許可する" };
        new AlertDialog.Builder(activity).setTitle("アプリ更新 · " + BuildConfig.VERSION_NAME)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) check(activity.getApplicationContext(), activity, true, null);
                    if (which == 1) { prefs(activity).edit().putBoolean("auto_updates", !enabled(activity)).apply(); schedule(activity); settings(activity); }
                    if (which == 2) activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
                    if (which == 3) {
                        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 73);
                        else activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
                    }
                }).setNeutralButton("状態", (d, w) -> message(activity, last + "\n\n自動確認は約6時間ごと。自動ダウンロードはWi-Fiなどの定額回線で動作します。Androidが確認を求めた場合は通知から続行できます。"))
                .setNegativeButton("閉じる", null).show();
    }
    private static void message(Activity a, String text) {
        if (a != null && !a.isFinishing() && !a.isDestroyed()) new AlertDialog.Builder(a).setTitle("アプリ更新").setMessage(text).setPositiveButton("OK", null).show();
    }
    static void check(Context context, Activity activity, boolean manual, Runnable done) {
        if (!busy.compareAndSet(false, true)) { if (manual) message(activity, "更新を確認中です。"); if (done != null) done.run(); return; }
        if (!manual) backgroundStopped = false;
        worker.execute(() -> {
            String result;
            try {
                String server = ServerUrl.normalize(prefs(context).getString("server", ""), false);
                if (server == null) throw new Exception("更新にはHTTPSの接続先を設定してください。");
                JSONObject json = new JSONObject(new String(read(server + "/api/android/latest.json", 16 * 1024), java.nio.charset.StandardCharsets.UTF_8));
                UpdateSpec release = new UpdateSpec(json.getInt("versionCode"), json.getString("versionName"), json.getString("packageName"), json.getString("path"), json.getString("sha256"), json.getLong("bytes"), json.getInt("minSdk"));
                prefs(context).edit().putLong("update_checked_at", System.currentTimeMillis()).apply();
                if (!release.newerThan(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) {
                    result = release.versionCode <= BuildConfig.VERSION_CODE ? "最新バージョンです（" + BuildConfig.VERSION_NAME + "）。" : "この更新には新しいAndroidバージョンが必要です。";
                } else if (!manual && ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).isActiveNetworkMetered()) {
                    result = "新しいバージョン " + release.versionName + " があります。Wi-Fi接続後に自動更新します。";
                    notify(context, result, new Intent(context, MainActivity.class).setAction(ACTION_SETTINGS));
                } else {
                    if (manual) {
                        main.post(() -> { if (activity.isFinishing() || activity.isDestroyed()) return; new AlertDialog.Builder(activity).setTitle("SkyPilot " + release.versionName)
                                .setMessage("新しいAPKをダウンロードして更新します。接続先とアプリ内データは引き継がれます。")
                                .setPositiveButton("更新する", (d, w) -> download(context, activity, release, server, false))
                                .setNegativeButton("あとで", null).show(); });
                        result = "新しいバージョン " + release.versionName + " があります。";
                    } else {
                        result = fetchAndInstall(context, release, server, true);
                    }
                }
            } catch (Exception e) { result = "更新を確認できませんでした。TailscaleとPCの接続を確認してください。"; }
            prefs(context).edit().putString("update_status", result).apply();
            final String text = result;
            if (manual && (!text.startsWith("新しいバージョン"))) main.post(() -> message(activity, text));
            busy.set(false);
            if (done != null) main.post(done);
        });
    }
    private static void download(Context context, Activity activity, UpdateSpec release, String server, boolean automatic) {
        if (!busy.compareAndSet(false, true)) return;
        android.widget.Toast.makeText(activity, "更新をダウンロードしています…", android.widget.Toast.LENGTH_LONG).show();
        worker.execute(() -> {
            try { String status = fetchAndInstall(context, release, server, automatic); prefs(context).edit().putString("update_status", status).apply(); if (status.startsWith("APKを検証しました")) main.post(() -> message(activity, status)); }
            catch (Exception e) { main.post(() -> message(activity, "更新できませんでした。接続先・APKの署名・更新許可を確認してください。")); }
            finally { busy.set(false); }
        });
    }
    private static HttpURLConnection connection(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000); c.setReadTimeout(30_000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept-Encoding", "identity");
        if (c.getResponseCode() != 200) { c.disconnect(); throw new Exception("Release unavailable"); }
        return c;
    }
    private static byte[] read(String url, int limit) throws Exception {
        HttpURLConnection c = connection(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) { if (out.size() + n > limit) throw new Exception("Oversized manifest"); out.write(buffer, 0, n); }
            return out.toByteArray();
        } finally { c.disconnect(); }
    }
    private static String fetchAndInstall(Context context, UpdateSpec release, String server, boolean automatic) throws Exception {
        File directory = new File(context.getFilesDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new Exception("Update storage unavailable");
        File apk = new File(directory, "release.apk");
        HttpURLConnection c = connection(release.downloadUrl(server));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long bytes = 0;
        try {
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
            byte[] buffer = new byte[64 * 1024]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (automatic && (backgroundStopped || !enabled(context) || ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).isActiveNetworkMetered())) throw new Exception("Automatic update paused");
                bytes += n; if (bytes > release.bytes || bytes > UpdateSpec.MAX_BYTES) throw new Exception("Oversized APK"); digest.update(buffer, 0, n); out.write(buffer, 0, n);
            }
        } finally { c.disconnect(); }
            if (bytes != release.bytes || !hex(digest.digest()).equals(release.sha256)) throw new Exception("APK checksum mismatch");
            validateApk(context, apk, release);
            // A connection change during a download must not switch its trust origin.
            if (!server.equals(ServerUrl.normalize(prefs(context).getString("server", ""), false))) throw new Exception("Server changed");
            if (automatic && (backgroundStopped || !enabled(context))) throw new Exception("Automatic update paused");
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                prefs(context).edit().putString("update_status", "APKを検証しました。アプリ更新のメニューでAPKの更新を許可し、もう一度更新してください。").apply();
                notify(context, "APKの更新許可が必要です", new Intent(context, MainActivity.class).setAction(ACTION_SETTINGS));
                return "APKを検証しました。アプリ更新のメニューでAPKの更新を許可し、もう一度更新してください。";
            }
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(context.getPackageName()); params.setSize(bytes);
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(automatic ? PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED : PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            int sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId); InputStream in = new FileInputStream(apk); java.io.OutputStream out = session.openWrite("base.apk", 0, bytes)) {
                byte[] buffer = new byte[64 * 1024]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                session.fsync(out);
                // Session writes must be closed before commit.
            } catch (Exception e) { installer.abandonSession(sessionId); throw e; }
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                Intent callback = new Intent(context, UpdateResult.class).setAction("dev.skypilot.android.INSTALL_RESULT");
                PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, callback, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
                session.commit(pending.getIntentSender());
            } catch (Exception e) { installer.abandonSession(sessionId); throw e; }
            return "検証済みのAPKをAndroidに渡しました。確認が必要な場合は更新通知から続行できます。";
        } finally { if (apk.exists()) apk.delete(); }
    }
    @SuppressWarnings("deprecation")
    private static void validateApk(Context context, File apk, UpdateSpec release) throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageManager pm = context.getPackageManager();
        PackageInfo next = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags), installed = pm.getPackageInfo(context.getPackageName(), flags);
        if (next == null || !release.packageName.equals(next.packageName) || !release.versionName.equals(next.versionName)
                || (Build.VERSION.SDK_INT >= 28 ? next.getLongVersionCode() : next.versionCode) != release.versionCode
                || release.versionCode <= BuildConfig.VERSION_CODE || next.applicationInfo == null || next.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new Exception("Wrong APK identity");
        Signature[] nextSigners = Build.VERSION.SDK_INT >= 28 && next.signingInfo != null ? next.signingInfo.getApkContentsSigners() : next.signatures;
        Signature[] ownSigners = Build.VERSION.SDK_INT >= 28 && installed.signingInfo != null ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        if (nextSigners == null || ownSigners == null || !certificates(nextSigners).equals(certificates(ownSigners))) throw new Exception("Different signing key");
    }
    private static Set<String> certificates(Signature[] signers) throws Exception {
        Set<String> result = new HashSet<>();
        for (Signature signer : signers) result.add(hex(MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())));
        if (result.isEmpty()) throw new Exception("Unsigned APK");
        return result;
    }
    private static String hex(byte[] data) {
        StringBuilder out = new StringBuilder(); for (byte b : data) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
    static void notify(Context context, String message, Intent action) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "SkyPilotアプリ更新", NotificationManager.IMPORTANCE_DEFAULT));
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        PendingIntent pending = PendingIntent.getActivity(context, NOTICE, action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(NOTICE, new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_skypilot)
                .setContentTitle("SkyPilotの更新").setContentText(message).setContentIntent(pending).setAutoCancel(true).build());
    }
}
