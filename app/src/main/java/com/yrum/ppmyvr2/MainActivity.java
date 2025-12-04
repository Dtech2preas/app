package com.yrum.ppmyvr2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private WebView mWebView;
    private final String mainUrl = "https://register.hollywoodbets.net/south-africa/1?raf=27608460";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep navigation within the WebView if it's the target site
                if (url.contains("hollywoodbets.net")) {
                    return false;
                }
                // Otherwise open in external browser (optional, but good practice for external links)
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);

                // Check if we are on the 3rd page
                // The URL structure described was https://register.hollywoodbets.net/south-africa/3?
                if (url != null && url.contains("/south-africa/3")) {
                    Log.d(TAG, "Detected registration page 3. Injecting referral code...");
                    injectReferralCode();
                }
            }
        });

        mWebView.loadUrl(mainUrl);
    }

    private void injectReferralCode() {
        // Javascript to find the input by placeholder and set the value
        String js = "(function() {" +
                "    var inputs = document.getElementsByTagName('input');" +
                "    for (var i = 0; i < inputs.length; i++) {" +
                "        var input = inputs[i];" +
                "        var placeholder = input.getAttribute('placeholder');" +
                "        if (placeholder && placeholder.includes(\"Referral (Enter referrer's Hollywoodbets\")) {" +
                "            console.log('Found referral input');" +
                "            input.value = '21848381';" +
                "            input.dispatchEvent(new Event('input', { bubbles: true }));" +
                "            input.dispatchEvent(new Event('change', { bubbles: true }));" +
                "            break;" +
                "        }" +
                "    }" +
                "})();";
        
        mWebView.evaluateJavascript(js, null);
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
