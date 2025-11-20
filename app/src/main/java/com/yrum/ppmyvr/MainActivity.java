package com.yrum.ppmyvr2;

import android.annotation.SuppressLint;
import android.app.Activity;
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
    private View invisibleHitBox; // The invisible trigger
    private Handler hideHandler = new Handler(Looper.getMainLooper());

    private final String mainUrl = "https://anime.preasx24.co.za";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Default to portrait
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

                // Add Video View
                rootLayout.addView(mCustomView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                // Create the fading controls
                createOverlayControls();

                // Rotate to Landscape
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                // Enable TRUE Fullscreen
                setFullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                if (mCustomView == null) return;

                removeOverlayControls();
                mCustomView.setScaleX(1.0f); // Reset scale

                mWebView.setVisibility(View.VISIBLE);
                rootLayout.removeView(mCustomView);
                
                if (mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                }
                mCustomView = null;
                mCustomViewCallback = null;

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                setFullscreen(false);
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("anime.preasx24.co.za")) return false;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                } catch (Exception e) { return true; }
            }
        });
        mWebView.loadUrl(mainUrl);
    }

    // --- OVERLAY LOGIC ---

    private void createOverlayControls() {
        // 1. Invisible Hit Box (Top Right Corner)
        // If you tap here when controls are hidden, they come back.
        invisibleHitBox = new View(this);
        invisibleHitBox.setBackgroundColor(Color.TRANSPARENT);
        invisibleHitBox.setOnClickListener(v -> showControls());
        
        FrameLayout.LayoutParams hitBoxParams = new FrameLayout.LayoutParams(200, 200); // 200px size
        hitBoxParams.gravity = Gravity.TOP | Gravity.END;
        invisibleHitBox.setLayoutParams(hitBoxParams);

        // 2. Toggle Button (Visible)
        toggleButton = new Button(this);
        toggleButton.setText("Screen Size");
        toggleButton.setTextSize(10);
        toggleButton.setBackgroundColor(Color.parseColor("#40000000")); 
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setPadding(10, 10, 10, 10);
        
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.TOP | Gravity.END; 
        btnParams.setMargins(0, 50, 50, 0); 
        toggleButton.setLayoutParams(btnParams);

        toggleButton.setOnClickListener(v -> {
            showControls(); // Reset timer
            if (optionsPanel.getVisibility() == View.VISIBLE) {
                optionsPanel.setVisibility(View.GONE);
            } else {
                optionsPanel.setVisibility(View.VISIBLE);
            }
        });

        // 3. Options Panel
        optionsPanel = new LinearLayout(this);
        optionsPanel.setOrientation(LinearLayout.HORIZONTAL);
        optionsPanel.setBackgroundColor(Color.parseColor("#AA000000"));
        optionsPanel.setGravity(Gravity.CENTER);
        optionsPanel.setPadding(20, 20, 20, 20);
        
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER;
        optionsPanel.setLayoutParams(panelParams);
        
        addOptionButton("Original", 1.0f);
        addOptionButton("18:9", 1.12f);
        addOptionButton("20:9", 1.20f);
        addOptionButton("Full", 1.35f);

        // Add to layout. Order matters!
        // Hitbox first (bottom layer of overlay), then visible buttons
        rootLayout.addView(invisibleHitBox); 
        rootLayout.addView(optionsPanel);
        rootLayout.addView(toggleButton);

        // Load saved preference
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyScale(prefs.getFloat(PREF_SCALE, 1.0f));

        // Start the hide timer
        showControls();
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
            showControls(); // Keep controls visible briefly after click
        });
        optionsPanel.addView(btn);
    }

    private void removeOverlayControls() {
        if (invisibleHitBox != null) rootLayout.removeView(invisibleHitBox);
        if (toggleButton != null) rootLayout.removeView(toggleButton);
        if (optionsPanel != null) rootLayout.removeView(optionsPanel);
        hideHandler.removeCallbacksAndMessages(null);
    }

    // Show everything, then hide after 3 seconds
    private void showControls() {
        if (toggleButton != null) toggleButton.setVisibility(View.VISIBLE);
        if (optionsPanel != null) optionsPanel.setVisibility(View.VISIBLE);
        
        hideHandler.removeCallbacksAndMessages(null);
        hideHandler.postDelayed(() -> {
            // Hide visible controls
            if (toggleButton != null) toggleButton.setVisibility(View.GONE);
            if (optionsPanel != null) optionsPanel.setVisibility(View.GONE);
            // Note: invisibleHitBox stays technically "visible" but transparent so you can tap it
        }, 3000);
    }

    private void applyScale(float scale) {
        if (mCustomView != null) mCustomView.setScaleX(scale);
    }

    private void savePreference(float scale) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(PREF_SCALE, scale);
        editor.apply();
    }

    // --- FULLSCREEN LOGIC ---

    private void setFullscreen(boolean fullscreen) {
        View decorView = getWindow().getDecorView();
        if (fullscreen) {
            // 1. Standard Immersive Flags
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            
            decorView.setSystemUiVisibility(flags);
            
            // 2. "Nuclear Option": FLAG_LAYOUT_NO_LIMITS
            // This allows drawing strictly everywhere (under status bar, nav bar, etc)
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // 3. Cutout Mode (Notch support)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().getAttributes().layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

        } else {
            // Reset everything
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            
            // Clear "No Limits" to bring back system bars properly
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
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