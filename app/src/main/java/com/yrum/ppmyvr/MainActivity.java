package com.yrum.ppmyvr2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo; // Import added
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager; // Import added
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private WebView mWebView;
    private FrameLayout rootLayout;
    private View mCustomView; // Moved to class level to handle back button properly
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    // Your anime website URL
    private final String mainUrl = "https://anime.preasx24.co.za";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. FORCE PORTRAIT DEFAULT: Prevents rotation just by tilting phone
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        // WebView setup
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        // Critical for video playback and fullscreen
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // Set WebChromeClient for fullscreen video support
        mWebView.setWebChromeClient(new WebChromeClient() {
            
            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                // If a view already exists then immediately terminate the new one
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                
                mCustomView = view;
                mCustomViewCallback = callback;
                
                // Hide the WebView and show the custom view (video)
                mWebView.setVisibility(View.GONE);
                rootLayout.addView(mCustomView);
                
                // 2. FORCE LANDSCAPE: When fullscreen button is clicked
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                
                // Enter immersive fullscreen mode (Hide bars)
                setFullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                // Exit fullscreen
                if (mCustomView == null) {
                    return;
                }
                
                // Show WebView and hide custom view
                mWebView.setVisibility(View.VISIBLE);
                rootLayout.removeView(mCustomView);
                
                if (mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                }
                
                mCustomView = null;
                mCustomViewCallback = null;
                
                // 3. FORCE PORTRAIT BACK: When exiting video
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                
                // Exit immersive fullscreen mode
                setFullscreen(false);
            }
        });

        // Set WebView client to handle page navigation
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Handle your main domain - load in WebView
                if (url.contains("anime.preasx24.co.za")) {
                    return false; 
                }
                
                // Handle external links
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

        // Load the main URL
        mWebView.loadUrl(mainUrl);
    }

    private void setFullscreen(boolean fullscreen) {
        View decorView = getWindow().getDecorView();
        if (fullscreen) {
            // Hide Status Bar and Navigation Bar
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN // Hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // Keep them hidden
            );
            // Keep screen on during video
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            // Show everything again
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            // Allow screen to turn off again
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onBackPressed() {
        // Check if video is playing in fullscreen
        if (mCustomView != null) {
            // If in video fullscreen, exit video fullscreen first
            mWebView.getWebChromeClient().onHideCustomView();
        } else if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}