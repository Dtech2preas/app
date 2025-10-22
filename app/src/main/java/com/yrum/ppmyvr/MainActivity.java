package com.yrum.ppmyvr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://dtech.preasx24.co.za";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleCustomUrlLogic(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleCustomUrlLogic(request.getUrl().toString());
            }

            private boolean handleCustomUrlLogic(String url) {
                try {
                    Uri mainUri = Uri.parse(mainUrl);
                    Uri currentUri = Uri.parse(url);

                    // Allow all downloads to open externally
                    if (isDownloadableFile(url)) {
                        openUrlExternally(url);
                        return true;
                    }

                    // Allow navigation within main domain
                    if (currentUri.getHost() != null && currentUri.getHost().equals(mainUri.getHost())) {
                        return false;
                    }

                    // Block external links that aren't downloads
                    Toast.makeText(MainActivity.this, "Blocked external link", Toast.LENGTH_SHORT).show();
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }

            private boolean isDownloadableFile(String url) {
                String[] downloadExtensions = {
                    // Documents
                    ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf",
                    // Archives
                    ".zip", ".rar", ".7z", ".tar", ".gz",
                    // Media
                    ".mp3", ".wav", ".ogg", ".mp4", ".avi", ".mkv", ".mov", ".flv",
                    // Images
                    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg",
                    // Executables
                    ".apk", ".exe", ".dmg", ".pkg", ".deb", ".rpm",
                    // Other common files
                    ".csv", ".json", ".xml", ".html", ".htm", ".epub", ".mobi"
                };
                
                String lowerCaseUrl = url.toLowerCase();
                for (String extension : downloadExtensions) {
                    if (lowerCaseUrl.contains(extension + "?") || lowerCaseUrl.endsWith(extension)) {
                        return true;
                    }
                }
                
                // Also check for common download patterns
                if (url.contains("download=true") || url.contains("download=1") || 
                    url.contains("download.php") || url.contains("download/file")) {
                    return true;
                }
                
                return false;
            }
        });

        // Handle downloads triggered by JavaScript
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            openUrlExternally(url);
        });

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
        } else {
            showOfflineDialog();
        }
    }

    private void openUrlExternally(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No application can handle this download", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            if (!mWebView.getUrl().equals(mainUrl)) {
                mWebView.loadUrl(mainUrl);
            } else {
                super.onBackPressed();
            }
        }
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
            .setTitle("No Internet Connection")
            .setMessage("Please check your internet connection and restart the app.")
            .setCancelable(false)
            .setPositiveButton("Exit", (dialog, which) -> finish())
            .show();
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }
}