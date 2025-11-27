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
import android.util.DisplayMetrics; // Needed for the math
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window; // Needed for window control
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

    // UI Elements
    private LinearLayout optionsPanel;
    private Button toggleButton;
    private View invisibleHitBox;
    private Handler hideHandler = new Handler(Looper.getMainLooper());

    private final String mainUrl = "https://anime.preasx24.co.za";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Lock Portrait initially
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

                // Create Overlay
                createOverlayControls();

                // Force Landscape
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                // Activate the "Nuclear" Fullscreen
                setFullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                if (mCustomView == null) return;

                removeOverlayControls();
                mCustomView.setScaleX(1.0f); 

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

    // --- DYNAMIC MATH LOGIC ---
    
    // This calculates the EXACT scale needed for YOUR specific phone
    private float calculateAutoFitScale() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        
        // In landscape, width is the longer side
        float realWidth = Math.max(metrics.widthPixels, metrics.heightPixels);
        float realHeight = Math.min(metrics.widthPixels, metrics.heightPixels);
        
        float screenRatio = realWidth / realHeight;
        float videoRatio = 16.0f / 9.0f; // Standard Anime Ratio (1.77)
        
        float scale = screenRatio / videoRatio;
        
        Log.d(TAG, "Auto-Fit Calc: Screen=" + screenRatio + " Video=" + videoRatio + " Scale=" + scale);
        return scale;
    }

    // --- OVERLAY LOGIC ---

    private void createOverlayControls() {
        // 1. Invisible Hit Box
        invisibleHitBox = new View(this);
        invisibleHitBox.setBackgroundColor(Color.TRANSPARENT);
        invisibleHitBox.setOnClickListener(v -> showControls());
        FrameLayout.LayoutParams hitBoxParams = new FrameLayout.LayoutParams(250, 250);
        hitBoxParams.gravity = Gravity.TOP | Gravity.END;
        invisibleHitBox.setLayoutParams(hitBoxParams);

        // 2. Toggle Button
        toggleButton = new Button(this);
        toggleButton.setText("Fit Screen");
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
            showControls();
            if (optionsPanel.getVisibility() == View.VISIBLE) {
                optionsPanel.setVisibility(View.GONE);
            } else {
                optionsPanel.setVisibility(View.VISIBLE);
            }
        });

        // 3. Options Panel
        optionsPanel = new LinearLayout(this);
        optionsPanel.setOrientation(LinearLayout.HORIZONTAL);
        optionsPanel.setBackgroundColor(Color.parseColor("#CC000000")); // Darker background
        optionsPanel.setGravity(Gravity.CENTER);
        optionsPanel.setPadding(10, 10, 10, 10);
        
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER;
        optionsPanel.setLayoutParams(panelParams);
        
        // Standard Option
        addOptionButton("Original", 1.0f);
        
        // THE MAGIC BUTTON: Auto-Fit
        // We calculate the scale specifically for this phone right now
        float perfectScale = calculateAutoFitScale();
        addOptionButton("Auto-Fit", perfectScale);
        
        // Extreme Fill (Just in case)
        addOptionButton("Max", perfectScale + 0.05f);

        rootLayout.addView(invisibleHitBox); 
        rootLayout.addView(optionsPanel);
        rootLayout.addView(toggleButton);

        // Load Saved Preference
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // If no preference, default to the calculated Auto-Fit immediately
        float savedScale = prefs.getFloat(PREF_SCALE, perfectScale); 
        applyScale(savedScale);

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
            Toast.makeText(MainActivity.this, text + " applied", Toast.LENGTH_SHORT).show();
            showControls(); 
        });
        optionsPanel.addView(btn);
    }

    private void removeOverlayControls() {
        if (invisibleHitBox != null) rootLayout.removeView(invisibleHitBox);
        if (toggleButton != null) rootLayout.removeView(toggleButton);
        if (optionsPanel != null) rootLayout.removeView(optionsPanel);
        hideHandler.removeCallbacksAndMessages(null);
    }

    private void showControls() {
        if (toggleButton != null) toggleButton.setVisibility(View.VISIBLE);
        if (optionsPanel != null) optionsPanel.setVisibility(View.VISIBLE);
        
        hideHandler.removeCallbacksAndMessages(null);
        hideHandler.postDelayed(() -> {
            if (toggleButton != null) toggleButton.setVisibility(View.GONE);
            if (optionsPanel != null) optionsPanel.setVisibility(View.GONE);
        }, 3000); // 3 Seconds
    }

    private void applyScale(float scale) {
        if (mCustomView != null) mCustomView.setScaleX(scale);
    }

    private void savePreference(float scale) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(PREF_SCALE, scale);
        editor.apply();
    }

    // --- ULTIMATE FULLSCREEN LOGIC ---

    private void setFullscreen(boolean fullscreen) {
        Window window = getWindow();
        View decorView = window.getDecorView();
        
        if (fullscreen) {
            // 1. The flags to hide UI
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
            
            // 2. Allow drawing outside the safe area (Status bar strip removal)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );

            // 3. Keep screen awake
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // 4. CRITICAL: Force content into the "Notch" area
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(lp);
            }

        } else {
            // Reset UI flags
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            
            // Clear No Limits
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // Reset Cutout mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                window.setAttributes(lp);
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