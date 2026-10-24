package dev.skypilot.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private String server;
    private String currentUrl;
    private WebView web;
    private View setup;
    private View errorPanel;
    private FrameLayout webContainer;
    private ProgressBar progress;
    private EditText serverInput;
    private TextView connectionStatus;
    private RadioGroup connectionMode;
    private ConnectivityManager connectivity;
    private boolean watchingNetwork;
    private boolean loadFailed;
    private boolean resetHistory;
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) {
            handler.post(() -> {
                if (!isFinishing() && !isDestroyed() && loadFailed && server != null && setup.getVisibility() != View.VISIBLE) retry();
            });
        }
    };
    private OnBackInvokedCallback backCallback;
    private final Runnable timeout = () -> {
        if (web != null) web.stopLoading();
        showError();
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("skypilot", MODE_PRIVATE);
        server = ServerUrl.normalize(preferences.getString("server", ""), BuildConfig.DEBUG);
        setup = findViewById(R.id.setup);
        errorPanel = findViewById(R.id.error_panel);
        webContainer = findViewById(R.id.web_container);
        progress = findViewById(R.id.progress);
        serverInput = findViewById(R.id.server_url);
        connectionStatus = findViewById(R.id.connection_status);
        connectionMode = findViewById(R.id.connection_mode);
        connectionMode.setOnCheckedChangeListener((group, id) -> updateModeHelp());
        findViewById(R.id.tailscale_guide).setOnClickListener(v -> openExternal("https://tailscale.com/docs/install/android"));
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        connectivity.registerDefaultNetworkCallback(networkCallback);
        watchingNetwork = true;
        findViewById(R.id.debug_help).setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        findViewById(R.id.connect).setOnClickListener(v -> connect());
        findViewById(R.id.cancel_setup).setOnClickListener(v -> hideSetup());
        findViewById(R.id.error_settings).setOnClickListener(v -> showSetup());
        findViewById(R.id.retry).setOnClickListener(v -> retry());
        findViewById(R.id.refresh).setOnClickListener(v -> retry());
        findViewById(R.id.menu).setOnClickListener(this::showMenu);
        serverInput.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) { connect(); return true; }
            return false;
        });
        applyInsets();
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        if (server == null) {
            showSetup();
        } else {
            createWebView();
            hideSetup();
            Bundle webState = state != null ? state.getBundle("web") : null;
            if (webState != null && server.equals(state.getString("server")) && web.restoreState(webState) != null
                    && ServerUrl.sameOrigin(server, web.getUrl())) {
                currentUrl = web.getUrl();
                connectionStatus.setText(Uri.parse(server).getHost());
            } else {
                currentUrl = server + "/";
                web.loadUrl(currentUrl);
            }
            if (state != null && state.getBoolean("setup")) {
                showSetup();
                serverInput.setText(state.getString("input", server));
            }
        }
        if (server == null && !BuildConfig.DEFAULT_SERVER_URL.isEmpty()) serverInput.setText(BuildConfig.DEFAULT_SERVER_URL);
    }

    private void applyInsets() {
        View root = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        } else {
            root.setFitsSystemWindows(true);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        if (web != null) {
            web.stopLoading();
            webContainer.removeView(web);
            web.destroy();
        }
        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.background));
        webContainer.addView(web, new FrameLayout.LayoutParams(-1, -1));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); // The existing SkyPilot Next.js application needs JS.
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + " SkyPilotAndroid/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        // No JavascriptInterface: web content never receives native app privileges.
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String remote = RustDeskTarget.fromLink(url);
                if (remote != null && request.isForMainFrame() && request.hasGesture()
                        && ServerUrl.sameOrigin(server, view.getUrl())) {
                    RustDeskIntegration.open(MainActivity.this, remote);
                    return true;
                }
                if (ServerUrl.sameOrigin(server, url)) return false;
                if (request.isForMainFrame() && request.hasGesture()) openExternal(url);
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!ServerUrl.sameOrigin(server, url)) {
                    view.stopLoading();
                    showError();
                    return;
                }
                currentUrl = url;
                connectionStatus.setText(R.string.connecting);
                loadFailed = false;
                errorPanel.setVisibility(View.GONE);
                webContainer.setVisibility(View.VISIBLE);
                progress.setVisibility(setup.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                handler.removeCallbacks(timeout);
                handler.postDelayed(timeout, 30_000);
            }

            @Override public void onPageFinished(WebView view, String url) {
                handler.removeCallbacks(timeout);
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                if (!loadFailed) connectionStatus.setText(Uri.parse(server).getHost());
                if (!loadFailed && resetHistory) { view.clearHistory(); resetHistory = false; }
            }

            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                if (ServerUrl.sameOrigin(server, url)) currentUrl = url;
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showError();
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                sslHandler.cancel();
                showError();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
        });
        web.setDownloadListener((url, userAgent, disposition, mimeType, length) -> openExternal(url));
    }

    private void connect() {
        String next = ServerUrl.normalize(serverInput.getText().toString(), BuildConfig.DEBUG);
        if (next == null) {
            serverInput.setError(getString(BuildConfig.DEBUG ? R.string.invalid_server_debug : R.string.invalid_server));
            return;
        }
        if (connectionMode.getCheckedRadioButtonId() == R.id.mode_tailscale && !ServerUrl.isTailnetOrigin(next)) {
            serverInput.setError(getString(R.string.tailscale_required));
            return;
        }
        handler.removeCallbacks(timeout);
        server = next;
        preferences.edit().putString("server", server).apply();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(serverInput.getWindowToken(), 0);
        createWebView();
        currentUrl = server + "/";
        resetHistory = true;
        hideSetup();
        web.loadUrl(currentUrl);
        AppUpdates.onOpen(this);
    }

    private void showSetup() {
        setup.setVisibility(View.VISIBLE);
        webContainer.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        serverInput.setText(server == null ? "" : server);
        findViewById(R.id.cancel_setup).setVisibility(server == null ? View.GONE : View.VISIBLE);
        findViewById(R.id.refresh).setEnabled(false);
        connectionStatus.setText(R.string.companion);
        connectionMode.check(server != null && !ServerUrl.isTailnetOrigin(server) ? R.id.mode_local : R.id.mode_tailscale);
        updateModeHelp();
    }

    private void updateModeHelp() {
        boolean tailnet = connectionMode.getCheckedRadioButtonId() == R.id.mode_tailscale;
        ((TextView) findViewById(R.id.mode_help)).setText(tailnet ? R.string.server_help : R.string.local_help);
        findViewById(R.id.tailscale_guide).setVisibility(tailnet ? View.VISIBLE : View.GONE);
        findViewById(R.id.debug_help).setVisibility(!tailnet && BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        serverInput.setError(null);
    }

    private void hideSetup() {
        setup.setVisibility(View.GONE);
        webContainer.setVisibility(loadFailed ? View.GONE : View.VISIBLE);
        findViewById(R.id.refresh).setEnabled(true);
    }

    private void retry() {
        if (server == null || web == null) { showSetup(); return; }
        loadFailed = false;
        errorPanel.setVisibility(View.GONE);
        webContainer.setVisibility(View.VISIBLE);
        web.loadUrl(ServerUrl.sameOrigin(server, currentUrl) ? currentUrl : server + "/");
    }

    private void showError() {
        handler.removeCallbacks(timeout);
        loadFailed = true;
        connectionStatus.setText(R.string.offline);
        progress.setVisibility(View.GONE);
        webContainer.setVisibility(View.GONE);
        errorPanel.setVisibility(View.VISIBLE);
        findViewById(R.id.error_title).announceForAccessibility(getString(R.string.connection_failed));
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.connection_settings);
        menu.getMenu().add(0, 2, 1, R.string.open_browser).setEnabled(server != null);
        menu.getMenu().add(0, 3, 2, "アプリ更新・自動更新").setEnabled(server != null);
        menu.getMenu().add(0, 4, 3, "RustDeskでPCに接続").setEnabled(server != null);
        menu.getMenu().add(0, 5, 4, "スマホでSkyBlockをプレイ").setEnabled(server != null);
        menu.getMenu().add(0, 6, 5, "プレイラボ").setEnabled(server != null);
        menu.getMenu().add(0, 7, 6, "冒険ノート").setEnabled(server != null);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showSetup();
            else if (item.getItemId() == 3) AppUpdates.settings(this);
            else if (item.getItemId() == 4) RustDeskIntegration.settings(this, server);
            else if (item.getItemId() >= 5 && item.getItemId() <= 7 && server != null && web != null) {
                hideSetup();
                currentUrl = server + (item.getItemId() == 5 ? "/play" : item.getItemId() == 6 ? "/tools" : "/planner");
                loadFailed = false;
                errorPanel.setVisibility(View.GONE);
                webContainer.setVisibility(View.VISIBLE);
                web.loadUrl(currentUrl);
            }
            else openExternal(ServerUrl.sameOrigin(server, currentUrl) ? currentUrl : server);
            return true;
        });
        menu.show();
    }

    private void openExternal(String url) {
        if (url == null) return;
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show(); }
    }

    private void handleBack() {
        if (setup.getVisibility() == View.VISIBLE && server != null) hideSetup();
        else if (setup.getVisibility() != View.VISIBLE && web != null && !loadFailed) {
            // Let the responsive Web menu close before navigating away from its page.
            web.evaluateJavascript("(function(){if(!document.getElementById('mobile-menu'))return false;"
                    + "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));return true;})()",
                    result -> {
                        if (isFinishing() || web == null || "true".equals(result)) return;
                        if (web.canGoBack()) web.goBack(); else finish();
                    });
        }
        else if (web != null && setup.getVisibility() != View.VISIBLE && web.canGoBack()) web.goBack();
        else finish();
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("GestureBackNavigation") // Only the pre-33 fallback; 33+ registers OnBackInvokedCallback above.
    @Override public void onBackPressed() { handleBack(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("server", server);
        state.putBoolean("setup", setup.getVisibility() == View.VISIBLE);
        state.putString("input", serverInput.getText().toString());
        if (web != null && !loadFailed) {
            Bundle webState = new Bundle();
            web.saveState(webState);
            state.putBundle("web", webState);
        }
    }

    @Override protected void onPause() {
        AppUpdates.onPause(this);
        if (web != null) web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        if (server != null) AppUpdates.onOpen(this);
        if (AppUpdates.ACTION_SETTINGS.equals(getIntent().getAction())) { getIntent().setAction(null); AppUpdates.settings(this); }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(timeout);
        if (watchingNetwork) { connectivity.unregisterNetworkCallback(networkCallback); watchingNetwork = false; }
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        if (web != null) { webContainer.removeView(web); web.destroy(); web = null; }
        super.onDestroy();
    }
}
