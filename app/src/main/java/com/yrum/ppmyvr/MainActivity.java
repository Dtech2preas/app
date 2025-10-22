package com.yrum.ppmyvr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1002;
    private static final String PREFS_NAME = "spotifydl_prefs";
    private static final String PREFS_KEY_TREE_URI = "default_tree_uri";

    private WebView mWebView;
    private FrameLayout rootLayout;
    private Button localBtn;

    // the website you browse for searching & server downloads
    private final String mainUrl = "https://dtech.preasx24.co.za";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // your existing layout

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        // WebView setup
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);

        mWebView.setWebViewClient(new WebViewClient());
        mWebView.addJavascriptInterface(new AndroidBridge(this), "Android");

        // Download listener (if user clicks a raw file link in website we intercept and save into chosen folder)
        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                String guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
                // delegate to bridge to download into chosen folder
                new AndroidBridge(MainActivity.this).downloadSong(url, guessed);
            }
        });

        // Add floating button to open the local player (assets HTML)
        addLocalLibraryButton();

        // Load website
        mWebView.loadUrl(mainUrl);

        // If no folder selected yet, prompt (deferred to avoid blocking UI)
        String treeUri = getSavedTreeUri();
        if (treeUri == null || treeUri.isEmpty()) {
            // prompt user to pick folder
            promptUserToChooseFolder();
        }
    }

    private void addLocalLibraryButton() {
        // Create a simple floating button programmatically and add to root layout
        localBtn = new Button(this);
        localBtn.setText("Local Library");
        localBtn.setAllCaps(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dpToPx(this, 12);
        lp.rightMargin = dpToPx(this, 12);
        rootLayout.addView(localBtn, lp);

        localBtn.setOnClickListener(v -> {
            // Load the local player HTML from assets
            mWebView.loadUrl("file:///android_asset/local_player.html");
        });
    }

    private static int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    // Launch SAF folder picker
    private void promptUserToChooseFolder() {
        try {
            Intent intent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                // optional: start at Downloads
                // intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
            } else {
                Toast.makeText(this, "Folder picking requires Android 5.0+", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching folder picker: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    Uri treeUri = data.getData();
                    if (treeUri != null) {
                        // Persist permission
                        final int takeFlags = data.getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        try {
                            getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                        } catch (SecurityException se) {
                            Log.w(TAG, "takePersistableUriPermission failed: " + se.getMessage());
                        }

                        saveTreeUri(treeUri.toString());
                        Toast.makeText(this, "Folder saved for downloads.", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "You must choose a folder to store songs.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveTreeUri(String treeUri) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREFS_KEY_TREE_URI, treeUri).apply();
    }

    private String getSavedTreeUri() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREFS_KEY_TREE_URI, null);
    }

    // JavaScript bridge
    public class AndroidBridge {
        private final Context ctx;

        public AndroidBridge(Context ctx) {
            this.ctx = ctx;
        }

        @JavascriptInterface
        public void chooseFolder() {
            // Called from JS to re-open folder chooser
            runOnUiThread(() -> promptUserToChooseFolder());
        }

        @JavascriptInterface
        public String getSavedFolderUri() {
            String uri = getSavedTreeUri();
            return uri == null ? "" : uri;
        }

        // Returns JSON array of { name: "...", uri: "document://..." }
        @JavascriptInterface
        public String getDownloadedSongs() {
            try {
                String tree = getSavedTreeUri();
                JSONArray arr = new JSONArray();
                if (tree == null || tree.isEmpty()) {
                    return arr.toString();
                }
                Uri treeUri = Uri.parse(tree);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                if (pickedDir == null) return arr.toString();
                for (DocumentFile file : pickedDir.listFiles()) {
                    if (file.isFile()) {
                        String name = file.getName();
                        // optional: filter audio extensions
                        if (name != null && (name.toLowerCase().endsWith(".mp3") || name.toLowerCase().endsWith(".m4a") ||
                                name.toLowerCase().endsWith(".wav") || name.toLowerCase().endsWith(".ogg"))) {
                            JSONObject o = new JSONObject();
                            o.put("name", name);
                            o.put("uri", file.getUri().toString());
                            arr.put(o);
                        }
                    }
                }
                return arr.toString();
            } catch (Exception e) {
                Log.e(TAG, "getDownloadedSongs error: " + e.getMessage());
                return "[]";
            }
        }

        // Returns a data URL (base64) for the given document Uri string so the HTML audio tag can play it
        @JavascriptInterface
        public String getSongDataUrl(String docUriString) {
            InputStream in = null;
            ByteArrayOutputStream baos = null;
            try {
                Uri uri = Uri.parse(docUriString);
                ContentResolver resolver = getContentResolver();
                in = resolver.openInputStream(uri);
                if (in == null) return "";

                baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                byte[] bytes = baos.toByteArray();
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                // We return a data URL; set mime to audio/mpeg (best-effort)
                String dataUrl = "data:audio/mpeg;base64," + base64;
                return dataUrl;
            } catch (Exception e) {
                Log.e(TAG, "getSongDataUrl error: " + e.getMessage());
                return "";
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (baos != null) baos.close(); } catch (Exception ignored) {}
            }
        }

        // Download a remote URL and save into chosen folder with given filename
        @JavascriptInterface
        public void downloadSong(final String urlString, final String suggestedFileName) {
            // Run in background
            AsyncTask.execute(() -> {
                try {
                    String tree = getSavedTreeUri();
                    if (tree == null || tree.isEmpty()) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Please select a folder first", Toast.LENGTH_LONG).show());
                        return;
                    }
                    Uri treeUri = Uri.parse(tree);
                    DocumentFile pickedDir = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                    if (pickedDir == null) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Chosen folder not available", Toast.LENGTH_LONG).show());
                        return;
                    }

                    // determine a safe file name
                    String safeName = suggestedFileName;
                    if (safeName == null || safeName.trim().isEmpty()) {
                        safeName = URLUtil.guessFileName(urlString, null, null);
                    }

                    // create file in folder (overwrite if exists: delete then create)
                    DocumentFile existing = pickedDir.findFile(safeName);
                    if (existing != null) existing.delete();

                    // create new file (audio/*)
                    DocumentFile newFile = pickedDir.createFile("audio/mpeg", safeName);

                    if (newFile == null) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to create file in folder", Toast.LENGTH_LONG).show());
                        return;
                    }

                    // download from network and write to the DocumentFile's OutputStream
                    HttpURLConnection conn = null;
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        URL url = new URL(urlString);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);
                        conn.connect();

                        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Download failed: HTTP " + conn.getResponseCode(), Toast.LENGTH_LONG).show());
                            return;
                        }

                        in = conn.getInputStream();
                        out = getContentResolver().openOutputStream(newFile.getUri());

                        if (out == null) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Can't open output stream to chosen folder", Toast.LENGTH_LONG).show());
                            return;
                        }

                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        out.flush();

                        // Notify UI / webview that download is done by reloading local player if loaded
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Saved: " + safeName, Toast.LENGTH_LONG).show();
                            // If local player is loaded, call its JS refresh function (if present)
                            try {
                                mWebView.evaluateJavascript("if(window.reloadLocalLibrary) reloadLocalLibrary();", null);
                            } catch (Exception ignored) {}
                        });

                    } finally {
                        try { if (in != null) in.close(); } catch (Exception ignored) {}
                        try { if (out != null) out.close(); } catch (Exception ignored) {}
                        if (conn != null) conn.disconnect();
                    }

                } catch (final Exception e) {
                    Log.e(TAG, "downloadSong error: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    // back-button behavior unchanged
    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}