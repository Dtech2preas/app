package com.yrum.ppmyvr2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "AnimePlayerPrefs";
    private static final String PREF_SCALE = "video_scale_factor";

    private WebView mWebView;
    private FrameLayout rootLayout;
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    // UI Elements for the overlay
    private LinearLayout optionsPanel;
    private Button toggleButton;
    private Handler hideHandler = new Handler(Looper.getMainLooper());

    // Your anime website URL
    private final String mainUrl = "https://anime.preasx24.co.za";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Lock Portrait by default
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        mWebView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                mCustomView = view;
                mCustomViewCallback = callback;

                mWebView.setVisibility(View.GONE);

                // Add the video view (Match Parent)
                rootLayout.addView(mCustomView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                // --- CREATE AND ADD THE OVERLAY MENU ---
                createAspectRatioOverlay();

                // Force Landscape
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                // Enter Immersive Mode
                setFullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                if (mCustomView == null) {
                    return;
                }

                // Remove overlay controls
                removeAspectRatioOverlay();

                // Reset scale
                mCustomView.setScaleX(1.0f);

                mWebView.setVisibility(View.VISIBLE);
                rootLayout.removeView(mCustomView);

                if (mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                }

                mCustomView = null;
                mCustomViewCallback = null;

                // Back to Portrait
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                setFullscreen(false);
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("anime.preasx24.co.za")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Could not handle URL: " + e.getMessage());
                    return true;
                }
            }
        });

        mWebView.loadUrl(mainUrl);
    }

    // --- OVERLAY LOGIC ---

    private void createAspectRatioOverlay() {
        // 1. Create the Toggle Button (The small gear/icon in corner)
        toggleButton = new Button(this);
        toggleButton.setText("Screen Size");
        toggleButton.setTextSize(10);
        toggleButton.setBackgroundColor(Color.parseColor("#40000000")); // Semi-transparent black
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setPadding(10, 10, 10, 10);
        
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.TOP | Gravity.END; // Top Right
        btnParams.setMargins(0, 50, 50, 0); // Little margin
        toggleButton.setLayoutParams(btnParams);

        toggleButton.setOnClickListener(v -> {
            showOptionsPanel();
        });

        // 2. Create the Panel holding the options (Hidden by default)
        optionsPanel = new LinearLayout(this);
        optionsPanel.setOrientation(LinearLayout.HORIZONTAL);
        optionsPanel.setBackgroundColor(Color.parseColor("#AA000000")); // Dark background
        optionsPanel.setGravity(Gravity.CENTER);
        optionsPanel.setPadding(20, 20, 20, 20);
        
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER;
        optionsPanel.setLayoutParams(panelParams);
        
        // Add buttons to the panel
        addOptionButton("Original", 1.0f);
        addOptionButton("18:9", 1.12f); // Slight stretch
        addOptionButton("20:9", 1.20f); // Modern phones
        addOptionButton("Full", 1.35f); // Max stretch

        // Add views to root
        rootLayout.addView(optionsPanel);
        rootLayout.addView(toggleButton);

        // 3. Apply saved preference immediately
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float savedScale = prefs.getFloat(PREF_SCALE, 1.0f);
        applyScale(savedScale);

        // Show panel for 3 seconds then hide
        showOptionsPanel();
    }

    private void addOptionButton(String text, final float scale) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setOnClickListener(v -> {
            applyScale(scale);
            savePreference(scale);
            Toast.makeText(MainActivity.this, "Scale: " + text, Toast.LENGTH_SHORT).show();
            // Reset the timer to hide
            showOptionsPanel(); 
        });
        optionsPanel.addView(btn);
    }

    private void removeAspectRatioOverlay() {
        if (toggleButton != null) rootLayout.removeView(toggleButton);
        if (optionsPanel != null) rootLayout.removeView(optionsPanel);
        hideHandler.removeCallbacksAndMessages(null);
    }

    private void showOptionsPanel() {
        if (optionsPanel != null) {
            optionsPanel.setVisibility(View.VISIBLE);
            
            // Cancel previous hide timer
            hideHandler.removeCallbacksAndMessages(null);
            
            // Set new timer to hide after 2.5 seconds
            hideHandler.postDelayed(() -> {
                if (optionsPanel != null) {
                    optionsPanel.setVisibility(View.GONE);
                }
            }, 2500);
        }
    }

    private void applyScale(float scale) {
        if (mCustomView != null) {
            mCustomView.setScaleX(scale);
        }
    }

    private void savePreference(float scale) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(PREF_SCALE, scale);
        editor.apply();
    }

    // --- END OVERLAY LOGIC ---

    private void setFullscreen(boolean fullscreen) {
        View decorView = getWindow().getDecorView();
        if (fullscreen) {
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().getAttributes().layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        } else {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().getAttributes().layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mCustomView != null) {
            mWebView.getWebChromeClient().onHideCustomView();
        } else if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}