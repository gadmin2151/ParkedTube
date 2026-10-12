package dev.codex.aaparkedyoutube;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.res.Configuration;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String YOUTUBE_URL = "https://www.youtube.com/";
    private static final int COLOR_BACKGROUND = Color.rgb(15, 17, 20);
    private static final int COLOR_SURFACE = Color.rgb(27, 30, 35);
    private static final int COLOR_KEY = Color.rgb(43, 43, 48);
    private static final int COLOR_TEXT = Color.WHITE;
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(167, 176, 188);
    private static final int COLOR_RED = Color.rgb(255, 67, 83);
    private static final int COLOR_BORDER = Color.rgb(66, 66, 74);

    private static final String[] RU_ROWS = {
            "йцукенгшщзх",
            "фывапролджэ",
            "ячсмитьбю"
    };
    private static final String[] EN_ROWS = {
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
    };
    private static final String[] SYMBOL_ROWS = {"1234567890", "-/:;()&@\"", ".,?!'_%+="};

    private FrameLayout root;
    private ParkedTubeWebView webView;
    private BrowserPreferences preferences;
    private BrowserControls controls;
    private FrameLayout browserContent;
    private View loadingOverlay;
    private View errorOverlay;
    private LinearLayout keyboardOverlay;
    private LinearLayout keyboardKeysContainer;
    private TextView keyboardPreview;
    private ProgressBar progressBar;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String keyboardText = "";
    private boolean keyboardEnglish;
    private boolean keyboardSymbols;
    private boolean nativeSearch;
    private String lastRequestedUrl = YOUTUBE_URL;
    private boolean hasLoadedPage;
    private boolean pageFailed;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateRequestedOrientation();
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(attrs);

        root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BACKGROUND);
        preferences = new BrowserPreferences(this);
        keyboardEnglish = preferences.englishKeyboard();
        browserContent = new FrameLayout(this);

        webView = new ParkedTubeWebView(this);
        webView.setBackgroundColor(COLOR_BACKGROUND);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setSuppressIme(shouldUseCarKeyboard());
        webView.addJavascriptInterface(new KeyboardBridge(), "ParkedTubeKeyboardBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldBlockUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldBlockUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (TrustedUrlPolicy.isTrustedUrl(url)) lastRequestedUrl = url;
                hideCarKeyboard();
                pageFailed = false;
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(8);
                if (!hasLoadedPage) {
                    showLoading();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!pageFailed) {
                    if (TrustedUrlPolicy.isYouTubeUrl(url)) {
                        injectKeyboardBridgeScript();
                    }
                    hasLoadedPage = true;
                    hideOverlays();
                    progressBar.setVisibility(View.GONE);
                    updateBrowserPage();
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                updateBrowserPage();
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (request.isForMainFrame()) {
                    pageFailed = true;
                    showError();
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceResponse errorResponse
            ) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                    pageFailed = true;
                    showError();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                updateBrowserPage();
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                hideCarKeyboard();
                controls.closePanel();
                fullscreenView = view;
                fullscreenCallback = callback;
                browserContent.setVisibility(View.GONE);
                controls.toolbar().setVisibility(View.GONE);
                root.addView(fullscreenView, fullScreenLayoutParams());
                hideSystemUi();
            }

            @Override
            public void onHideCustomView() {
                closeFullscreenVideo();
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(preferences.textZoom());
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(isDebuggable);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        controls = new BrowserControls(this, preferences, new BrowserControls.Actions() {
            @Override public void back() { onBackPressed(); }
            @Override public void home() { navigateTo(YOUTUBE_URL); }
            @Override public void navigate(String url) { navigateTo(url); }
            @Override public void search(String query) { searchYouTube(query); }
            @Override public void carInput(String query) {
                showCarKeyboard(query);
                nativeSearch = true;
            }
            @Override public void reload() { retryPage(); }
            @Override public void textZoom(int zoom) { webView.getSettings().setTextZoom(zoom); }
            @Override public boolean carDisplay() { return shouldUseCarKeyboard(); }
        });
        FrameLayout.LayoutParams contentParams = fullScreenLayoutParams();
        contentParams.topMargin = dp(56);
        root.addView(browserContent, contentParams);
        browserContent.addView(webView, fullScreenLayoutParams());
        loadingOverlay = buildLoadingOverlay();
        errorOverlay = buildErrorOverlay();
        keyboardOverlay = buildKeyboardOverlay();
        progressBar = buildProgressBar();
        browserContent.addView(loadingOverlay, fullScreenLayoutParams());
        browserContent.addView(errorOverlay, fullScreenLayoutParams());
        browserContent.addView(progressBar, progressLayoutParams());
        root.addView(controls.toolbar(), new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP));
        root.addView(controls.panel(), contentParams);
        root.addView(keyboardOverlay, keyboardLayoutParams());
        errorOverlay.setVisibility(View.GONE);
        keyboardOverlay.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.view.DisplayCutout cutout = insets.getDisplayCutout();
            view.setPadding(cutout == null ? 0 : cutout.getSafeInsetLeft(),
                    cutout == null ? 0 : cutout.getSafeInsetTop(),
                    cutout == null ? 0 : cutout.getSafeInsetRight(),
                    cutout == null ? 0 : cutout.getSafeInsetBottom());
            return insets;
        });
        hideSystemUi();

        if (savedInstanceState == null) {
            showLoading();
            webView.loadUrl(preferences.startUrl(YOUTUBE_URL));
        } else {
            if (webView.restoreState(savedInstanceState) == null) {
                showLoading();
                webView.loadUrl(preferences.startUrl(YOUTUBE_URL));
            } else {
                hasLoadedPage = true;
                hideOverlays();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            preferences.rememberPage(webView.getUrl());
            webView.onPause();
            CookieManager.getInstance().flush();
        }
        hideCarKeyboard();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        closeFullscreenVideo();
        if (webView != null) {
            browserContent.removeView(webView);
            webView.removeJavascriptInterface("ParkedTubeKeyboardBridge");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            updateRequestedOrientation();
            if (webView != null) {
                webView.setSuppressIme(shouldUseCarKeyboard());
            }
            hideSystemUi();
        }
    }

    @Override
    public void onBackPressed() {
        if (keyboardOverlay != null && keyboardOverlay.getVisibility() == View.VISIBLE) {
            hideCarKeyboard();
            return;
        }

        if (fullscreenView != null) {
            closeFullscreenVideo();
            return;
        }

        if (controls != null && controls.isPanelOpen()) {
            controls.closePanel();
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    private void closeFullscreenVideo() {
        if (fullscreenView == null) {
            return;
        }

        root.removeView(fullscreenView);
        fullscreenView = null;
        browserContent.setVisibility(View.VISIBLE);
        controls.toolbar().setVisibility(View.VISIBLE);

        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }

        hideSystemUi();
    }

    private FrameLayout.LayoutParams fullScreenLayoutParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private FrameLayout.LayoutParams progressLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        params.gravity = Gravity.TOP;
        return params;
    }

    private FrameLayout.LayoutParams keyboardLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM;
        return params;
    }

    private LinearLayout buildKeyboardOverlay() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(buildPanelBackground());
        panel.setClickable(true);
        panel.setFocusable(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        keyboardPreview = new TextView(this);
        keyboardPreview.setTextColor(COLOR_TEXT);
        keyboardPreview.setTextSize(22);
        keyboardPreview.setSingleLine(true);
        keyboardPreview.setGravity(Gravity.CENTER_VERTICAL);
        keyboardPreview.setPadding(dp(16), 0, dp(16), 0);
        keyboardPreview.setBackground(buildPreviewBackground());
        keyboardPreview.setText(R.string.search_placeholder);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
        );
        header.addView(keyboardPreview, previewParams);

        Button close = makeKeyboardButton("×", 24, COLOR_KEY, 1f);
        close.setContentDescription(getString(R.string.close));
        close.setOnClickListener(view -> hideCarKeyboard());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(64), dp(52));
        closeParams.leftMargin = dp(8);
        header.addView(close, closeParams);

        panel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        keyboardKeysContainer = new LinearLayout(this);
        keyboardKeysContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams keysParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        keysParams.topMargin = dp(8);
        ScrollView keysScroll = new ScrollView(this);
        keysScroll.setFillViewport(true);
        keysScroll.addView(keyboardKeysContainer);
        keysParams.height = Math.min(dp(220), Math.max(dp(150),
                getResources().getDisplayMetrics().heightPixels - dp(150)));
        panel.addView(keysScroll, keysParams);
        renderKeyboardKeys();

        return panel;
    }

    private void renderKeyboardKeys() {
        if (keyboardKeysContainer == null) {
            return;
        }

        keyboardKeysContainer.removeAllViews();
        String[] rows = keyboardSymbols ? SYMBOL_ROWS : keyboardEnglish ? EN_ROWS : RU_ROWS;

        for (String row : rows) {
            LinearLayout keyRow = buildKeyboardRow();
            for (int i = 0; i < row.length(); i++) {
                String value = row.substring(i, i + 1);
                Button key = makeKeyboardButton(value, 22, COLOR_KEY, 1f);
                key.setOnClickListener(view -> appendKeyboardText(value));
                addKeyboardKey(keyRow, key, 1f);
            }
            keyboardKeysContainer.addView(keyRow);
        }

        LinearLayout bottomRow = buildKeyboardRow();

        Button language = makeKeyboardButton(keyboardEnglish ? "РУ" : "EN", 18, COLOR_KEY, 1f);
        language.setOnClickListener(view -> {
            keyboardEnglish = !keyboardEnglish;
            keyboardSymbols = false;
            preferences.setEnglishKeyboard(keyboardEnglish);
            renderKeyboardKeys();
            hideSystemUi();
        });
        language.setContentDescription(getString(R.string.switch_language));
        addKeyboardKey(bottomRow, language, 1.1f);

        Button symbols = makeKeyboardButton(keyboardSymbols ? "ABC" : "123", 18, COLOR_KEY, 1f);
        symbols.setContentDescription(getString(R.string.switch_symbols));
        symbols.setOnClickListener(view -> {
            keyboardSymbols = !keyboardSymbols;
            renderKeyboardKeys();
        });
        addKeyboardKey(bottomRow, symbols, 1.1f);

        Button backspace = makeKeyboardButton("⌫", 22, COLOR_KEY, 1f);
        backspace.setOnClickListener(view -> backspaceKeyboardText());
        backspace.setContentDescription(getString(R.string.backspace));
        backspace.setOnLongClickListener(view -> {
            keyboardText = "";
            updateKeyboardPreview();
            if (!nativeSearch) pushKeyboardTextToPage();
            return true;
        });
        addKeyboardKey(bottomRow, backspace, 1.1f);

        Button space = makeKeyboardButton(getString(R.string.space), 18, COLOR_KEY, 1f);
        space.setOnClickListener(view -> appendKeyboardText(" "));
        addKeyboardKey(bottomRow, space, 3.2f);

        Button search = makeKeyboardButton(getString(R.string.search), 19, COLOR_RED, 1f);
        search.setTypeface(Typeface.DEFAULT_BOLD);
        search.setOnClickListener(view -> submitKeyboardSearch());
        addKeyboardKey(bottomRow, search, 2f);

        keyboardKeysContainer.addView(bottomRow);
    }

    private LinearLayout buildKeyboardRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        rowParams.topMargin = dp(5);
        row.setLayoutParams(rowParams);
        return row;
    }

    private void addKeyboardKey(LinearLayout row, Button key, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), weight);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        row.addView(key, params);
    }

    private Button makeKeyboardButton(String text, int textSizeSp, int color, float weight) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(textSizeSp);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(buildKeyBackground(color));
        button.setStateListAnimator(null);
        return button;
    }

    private GradientDrawable buildPanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_SURFACE);
        drawable.setStroke(dp(1), COLOR_BORDER);
        drawable.setCornerRadii(new float[]{
                dp(10), dp(10),
                dp(10), dp(10),
                0, 0,
                0, 0
        });
        return drawable;
    }

    private GradientDrawable buildPreviewBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(14, 14, 16));
        drawable.setStroke(dp(1), COLOR_BORDER);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private android.graphics.drawable.Drawable buildKeyBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(dp(1), COLOR_BORDER);
        drawable.setCornerRadius(dp(8));
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), drawable, null);
    }

    private View buildLoadingOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(40), dp(32), dp(40), dp(32));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_aa_parked_youtube);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(92), dp(92));
        content.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(18);
        content.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.loading_youtube);
        subtitle.setTextColor(COLOR_TEXT_SECONDARY);
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(10);
        content.addView(subtitle, subtitleParams);

        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(COLOR_RED));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        spinnerParams.topMargin = dp(26);
        content.addView(spinner, spinnerParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        overlay.addView(scroll, fullScreenLayoutParams());
        return overlay;
    }

    private View buildErrorOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(32), dp(48), dp(32));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_aa_parked_youtube);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        content.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText(R.string.error_title);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(20);
        content.addView(title, titleParams);

        TextView message = new TextView(this);
        message.setText(R.string.error_message);
        message.setTextColor(COLOR_TEXT_SECONDARY);
        message.setTextSize(17);
        message.setGravity(Gravity.CENTER);
        message.setMaxWidth(dp(560));
        message.setIncludeFontPadding(false);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dp(12);
        content.addView(message, messageParams);

        Button retryButton = new Button(this);
        retryButton.setText(R.string.retry);
        retryButton.setTextColor(Color.WHITE);
        retryButton.setTextSize(18);
        retryButton.setAllCaps(false);
        retryButton.setMinWidth(dp(180));
        retryButton.setMinHeight(dp(54));
        retryButton.setBackground(buildButtonBackground());
        retryButton.setOnClickListener(view -> {
            retryPage();
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(56)
        );
        buttonParams.topMargin = dp(28);
        content.addView(retryButton, buttonParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        overlay.addView(scroll, fullScreenLayoutParams());
        return overlay;
    }

    private ProgressBar buildProgressBar() {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        bar.setProgress(0);
        bar.getProgressDrawable().setTint(COLOR_RED);
        bar.getIndeterminateDrawable().setTint(COLOR_RED);
        return bar;
    }

    private GradientDrawable buildButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_RED);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private void showLoading() {
        loadingOverlay.setVisibility(View.VISIBLE);
        errorOverlay.setVisibility(View.GONE);
        hideSystemUi();
    }

    private void showError() {
        hideCarKeyboard();
        loadingOverlay.setVisibility(View.GONE);
        errorOverlay.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        hideSystemUi();
    }

    private void hideOverlays() {
        loadingOverlay.setVisibility(View.GONE);
        errorOverlay.setVisibility(View.GONE);
        hideSystemUi();
    }

    private void showCarKeyboard(String text) {
        if (!shouldUseCarKeyboard() || keyboardOverlay == null || fullscreenView != null) {
            return;
        }

        keyboardText = text == null ? "" : text;
        if (keyboardText.length() > BrowserLibrary.MAX_QUERY_LENGTH) {
            keyboardText = BrowserLibrary.cleanQuery(keyboardText);
        }
        nativeSearch = false;
        updateKeyboardPreview();
        keyboardOverlay.setVisibility(View.VISIBLE);
        hidePhoneKeyboard();
        hideSystemUi();
    }

    private void hideCarKeyboard() {
        if (keyboardOverlay != null) {
            keyboardOverlay.setVisibility(View.GONE);
        }
        nativeSearch = false;
        hidePhoneKeyboard();
        hideSystemUi();
    }

    private void updateKeyboardTextFromPage(String text) {
        if (keyboardOverlay == null || keyboardOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        keyboardText = text == null ? "" : text;
        if (keyboardText.length() > BrowserLibrary.MAX_QUERY_LENGTH) {
            keyboardText = BrowserLibrary.cleanQuery(keyboardText);
        }
        updateKeyboardPreview();
    }

    private void appendKeyboardText(String value) {
        if (keyboardText.length() + value.length() > BrowserLibrary.MAX_QUERY_LENGTH) return;
        keyboardText = keyboardText + value;
        updateKeyboardPreview();
        if (!nativeSearch) pushKeyboardTextToPage();
    }

    private void backspaceKeyboardText() {
        if (keyboardText.isEmpty()) {
            return;
        }

        int end = keyboardText.offsetByCodePoints(keyboardText.length(), -1);
        keyboardText = keyboardText.substring(0, end);
        updateKeyboardPreview();
        if (!nativeSearch) pushKeyboardTextToPage();
    }

    private void submitKeyboardSearch() {
        if (nativeSearch) {
            searchYouTube(keyboardText);
            return;
        }
        pushKeyboardTextToPage();
        evaluateKeyboardScript("window.ParkedTubeKeyboard && window.ParkedTubeKeyboard.enter();");
        hideCarKeyboard();
    }

    private void updateKeyboardPreview() {
        if (nativeSearch && controls != null) controls.updateSearchText(keyboardText);
        if (keyboardPreview == null) {
            return;
        }

        if (keyboardText == null || keyboardText.trim().isEmpty()) {
            keyboardPreview.setText(R.string.search_placeholder);
            keyboardPreview.setTextColor(COLOR_TEXT_SECONDARY);
        } else {
            keyboardPreview.setText(keyboardText);
            keyboardPreview.setTextColor(COLOR_TEXT);
        }
    }

    private void pushKeyboardTextToPage() {
        evaluateKeyboardScript(
                "window.ParkedTubeKeyboard && window.ParkedTubeKeyboard.setText("
                        + JSONObject.quote(keyboardText)
                        + ");"
        );
    }

    private void injectKeyboardBridgeScript() {
        evaluateKeyboardScript(
                "(function(){"
                        + "if(window.__parkedTubeKeyboardInstalled){return;}"
                        + "window.__parkedTubeKeyboardInstalled=true;"
                        + "window.__parkedTubeActiveInput=null;"
                        + "function editable(el){"
                        + "if(!el){return false;}"
                        + "var tag=(el.tagName||'').toLowerCase();"
                        + "var type=(el.type||'').toLowerCase();"
                        + "return el.isContentEditable||tag==='textarea'||"
                        + "(tag==='input'&&['','text','search','url','email','tel'].indexOf(type)!==-1);"
                        + "}"
                        + "function textOf(el){return el?(el.isContentEditable?el.innerText:(el.value||'')):'';}"
                        + "function setNativeValue(el,value){"
                        + "if(!el){return;}"
                        + "if(el.isContentEditable){el.innerText=value;}"
                        + "else{"
                        + "var proto=Object.getPrototypeOf(el);"
                        + "var desc=Object.getOwnPropertyDescriptor(proto,'value');"
                        + "if(desc&&desc.set){desc.set.call(el,value);}else{el.value=value;}"
                        + "}"
                        + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "}"
                        + "function searchButton(){"
                        + "return document.querySelector('button[aria-label=\"Search\"],button[aria-label=\"Поиск\"],button[title=\"Search\"],button[title=\"Поиск\"],#search-icon-legacy');"
                        + "}"
                        + "function submit(el){"
                        + "if(el){el.focus();}"
                        + "['keydown','keypress','keyup'].forEach(function(type){"
                        + "var ev=new KeyboardEvent(type,{key:'Enter',code:'Enter',which:13,keyCode:13,bubbles:true,cancelable:true});"
                        + "(el||document.activeElement||document.body).dispatchEvent(ev);"
                        + "});"
                        + "var form=el&&el.form;"
                        + "if(form&&form.requestSubmit){form.requestSubmit();return;}"
                        + "var button=searchButton();"
                        + "if(button){button.click();}"
                        + "}"
                        + "document.addEventListener('focusin',function(e){"
                        + "if(editable(e.target)){"
                        + "window.__parkedTubeActiveInput=e.target;"
                        + "setTimeout(function(){ParkedTubeKeyboardBridge.onInputFocus(textOf(window.__parkedTubeActiveInput));},50);"
                        + "}"
                        + "},true);"
                        + "document.addEventListener('input',function(e){"
                        + "if(editable(e.target)){"
                        + "window.__parkedTubeActiveInput=e.target;"
                        + "ParkedTubeKeyboardBridge.onInputText(textOf(e.target));"
                        + "}"
                        + "},true);"
                        + "window.ParkedTubeKeyboard={"
                        + "setText:function(value){var el=window.__parkedTubeActiveInput||document.activeElement;if(editable(el)){setNativeValue(el,value);}},"
                        + "enter:function(){var el=window.__parkedTubeActiveInput||document.activeElement;submit(el);},"
                        + "hide:function(){var el=window.__parkedTubeActiveInput||document.activeElement;if(el){el.blur();}}"
                        + "};"
                        + "})();"
        );
    }

    private void evaluateKeyboardScript(String script) {
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript(script, null);
    }

    private void hidePhoneKeyboard() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null && webView != null) {
            inputMethodManager.hideSoftInputFromWindow(webView.getWindowToken(), 0);
        }
    }

    private boolean shouldBlockUrl(Uri uri) {
        boolean blocked = uri == null || !TrustedUrlPolicy.isTrustedUrl(uri.toString());
        if (blocked) Toast.makeText(this, R.string.blocked_link, Toast.LENGTH_SHORT).show();
        return blocked;
    }

    private void updateBrowserPage() {
        if (controls == null || webView == null) return;
        controls.updatePage(webView.getUrl(), webView.getTitle(), webView.canGoBack());
        if (!pageFailed) preferences.rememberPage(webView.getUrl());
    }

    private void navigateTo(String url) {
        if (!TrustedUrlPolicy.isYouTubeUrl(url)) return;
        controls.closePanel();
        hideCarKeyboard();
        closeFullscreenVideo();
        webView.loadUrl(url);
    }

    private void searchYouTube(String query) {
        String clean = BrowserLibrary.cleanQuery(query);
        if (clean.isEmpty()) return;
        preferences.library.recordSearch(clean);
        preferences.saveLibrary();
        navigateTo("https://www.youtube.com/results?search_query=" + Uri.encode(clean));
    }

    private void retryPage() {
        showLoading();
        webView.loadUrl(TrustedUrlPolicy.isTrustedUrl(lastRequestedUrl) ? lastRequestedUrl : YOUTUBE_URL);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        hideCarKeyboard();
        controls.closePanel();
        updateRequestedOrientation();
        webView.setSuppressIme(shouldUseCarKeyboard());
        ViewGroup.LayoutParams keys = keyboardKeysContainer.getParent() instanceof View
                ? ((View) keyboardKeysContainer.getParent()).getLayoutParams() : null;
        if (keys != null) {
            keys.height = Math.min(dp(220), Math.max(dp(150), getResources().getDisplayMetrics().heightPixels - dp(150)));
            ((View) keyboardKeysContainer.getParent()).setLayoutParams(keys);
        }
    }

    private void updateRequestedOrientation() {
        if (isRunningOnExternalDisplay()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private boolean isRunningOnExternalDisplay() {
        Display display = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display = getDisplay();
        }

        if (display == null && getWindow() != null) {
            display = getWindow().getDecorView().getDisplay();
        }

        if (display == null) {
            display = getWindowManager().getDefaultDisplay();
        }

        return display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY;
    }

    private boolean shouldUseCarKeyboard() {
        return isRunningOnExternalDisplay();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private class KeyboardBridge {
        @JavascriptInterface
        public void onInputFocus(String text) {
            runOnUiThread(() -> {
                if (webView != null && TrustedUrlPolicy.isYouTubeUrl(webView.getUrl())
                        && !controls.isPanelOpen()) showCarKeyboard(text);
            });
        }

        @JavascriptInterface
        public void onInputText(String text) {
            runOnUiThread(() -> {
                if (webView != null && TrustedUrlPolicy.isYouTubeUrl(webView.getUrl()) && !nativeSearch) {
                    updateKeyboardTextFromPage(text);
                }
            });
        }
    }

    private static class ParkedTubeWebView extends WebView {
        private boolean suppressIme;

        ParkedTubeWebView(Context context) {
            super(context);
        }

        void setSuppressIme(boolean suppressIme) {
            this.suppressIme = suppressIme;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            if (suppressIme) {
                return null;
            }
            return super.onCreateInputConnection(outAttrs);
        }
    }
}
