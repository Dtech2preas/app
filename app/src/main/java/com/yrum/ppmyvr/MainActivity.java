package com.yrum.ppmyvr2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private WebView mWebView;
    private FrameLayout rootLayout;
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    // Your anime website URL
    private final String mainUrl = "https://anime.preasx24.co.za";

    // --- STRETCH CONTROL ---
    // 1.0f = Normal (No stretch)
    // 1.18f = Approx fit for 20:9 phones (Samsung S20+, etc)
    // 1.25f = Extreme stretch
    // Try 1.15f or 1.2f to see what looks best on your specific phone
    private final float VIDEO_STRETCH_FACTOR = 1.18f; 

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lock portrait when browsing
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

                // Add the video view
                rootLayout.addView(mCustomView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                // --- HERE IS THE STRETCH LOGIC ---
                // This grabs the video view and physically stretches it horizontally
                mCustomView.setScaleX(VIDEO_STRETCH_FACTOR);
                
                // Optional: If you want to zoom in (crop top/bottom) instead of stretch, 
                // uncomment the line below:
                // mCustomView.setScaleY(VIDEO_STRETCH_FACTOR); 

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

                // Reset the scale before removing (good practice)
                mCustomView.setScaleX(1.0f);
                mCustomView.setScaleY(1.0f);

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