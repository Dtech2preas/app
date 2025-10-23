package com.yrum.ppmyvr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
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
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1002;
    private static final String PREFS_NAME = "spotifydl_prefs";
    private static final String PREFS_KEY_TREE_URI = "default_tree_uri";
    private static final String PREFS_KEY_DEFAULT_FOLDER_CREATED = "default_folder_created";

    // Notification
    private static final String CHANNEL_ID = "music_player_channel";
    private static final int NOTIFICATION_ID = 1;
    private NotificationManager notificationManager;

    // Action constants
    private static final String ACTION_PLAY = "PLAY";
    private static final String ACTION_PAUSE = "PAUSE";
    private static final String ACTION_STOP = "STOP";
    private static final String ACTION_NEXT = "NEXT";
    private static final String ACTION_PREVIOUS = "PREVIOUS";

    private WebView mWebView;
    private FrameLayout rootLayout;
    private Button localBtn;

    // Media playback state
    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

    // the website you browse for searching & server downloads
    private final String mainUrl = "https://dtech.preasx24.co.za";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        // Initialize notification
        createNotificationChannel();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Handle media actions from notification when app is launched
        handleIntent(getIntent());

        // WebView setup
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);

        mWebView.setWebViewClient(new WebViewClient());
        mWebView.addJavascriptInterface(new AndroidBridge(this), "Android");

        // Download listener
        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                String guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
                new AndroidBridge(MainActivity.this).downloadSong(url, guessed);
            }
        });

        // Add floating button to open the local player (assets HTML)
        addLocalLibraryButton();

        // Load website
        mWebView.loadUrl(mainUrl);

        // Check and create default folder if needed
        checkAndCreateDefaultFolder();

        // If no folder selected yet, prompt (deferred to avoid blocking UI)
        String treeUri = getSavedTreeUri();
        if (treeUri == null || treeUri.isEmpty()) {
            promptUserToChooseFolder();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PLAY:
                    resumeSong();
                    break;
                case ACTION_PAUSE:
                    pauseSong();
                    break;
                case ACTION_STOP:
                    stopSong();
                    break;
                case ACTION_NEXT:
                    Toast.makeText(this, "Next song", Toast.LENGTH_SHORT).show();
                    break;
                case ACTION_PREVIOUS:
                    Toast.makeText(this, "Previous song", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    private void checkAndCreateDefaultFolder() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean defaultFolderCreated = prefs.getBoolean(PREFS_KEY_DEFAULT_FOLDER_CREATED, false);
        
        if (!defaultFolderCreated) {
            File musicDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "SpotifyDL");
            if (!musicDir.exists()) {
                musicDir.mkdirs();
                Log.d(TAG, "Default folder created: " + musicDir.getAbsolutePath());
            }
            prefs.edit().putBoolean(PREFS_KEY_DEFAULT_FOLDER_CREATED, true).apply();
        }
    }

    private void addLocalLibraryButton() {
        localBtn = new Button(this);
        localBtn.setText("Local Library");
        localBtn.setAllCaps(false);
        localBtn.setBackgroundResource(android.R.drawable.btn_default);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dpToPx(this, 12);
        lp.rightMargin = dpToPx(this, 12);
        rootLayout.addView(localBtn, lp);

        localBtn.setOnClickListener(v -> {
            mWebView.loadUrl("file:///android_asset/local_player.html");
        });
    }

    private static int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void promptUserToChooseFolder() {
        try {
            Intent intent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
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
                        int takeFlags = data.getFlags();
                        int desiredFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        
                        takeFlags = takeFlags & desiredFlags;
                        
                        if (takeFlags == 0) {
                            takeFlags = desiredFlags;
                        }
                        
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

    // Media playback methods
    private void playSong(String songUri, String songName) {
        try {
            currentSongName = songName;
            currentSongUri = songUri;
            isPlaying = true;
            
            updateNotification();
            
            Toast.makeText(this, "Now playing: " + songName, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing song: " + e.getMessage());
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show();
        }
    }

    private void pauseSong() {
        isPlaying = false;
        updateNotification();
        Toast.makeText(this, "Playback paused", Toast.LENGTH_SHORT).show();
    }

    private void resumeSong() {
        isPlaying = true;
        updateNotification();
        Toast.makeText(this, "Playback resumed", Toast.LENGTH_SHORT).show();
    }

    private void stopSong() {
        isPlaying = false;
        currentSongName = "";
        currentSongUri = "";
        updateNotification();
        Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show();
    }

    // Notification methods
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private void updateNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Create media action intents that launch the activity directly
        Intent playIntent = new Intent(this, MainActivity.class);
        playIntent.setAction(ACTION_PLAY);
        PendingIntent playPendingIntent = PendingIntent.getActivity(this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(this, MainActivity.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pausePendingIntent = PendingIntent.getActivity(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, MainActivity.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getActivity(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent nextIntent = new Intent(this, MainActivity.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getActivity(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent prevIntent = new Intent(this, MainActivity.class);
        prevIntent.setAction(ACTION_PREVIOUS);
        PendingIntent prevPendingIntent = PendingIntent.getActivity(this, 5, prevIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(isPlaying ? "Now Playing" : "Music Player")
                .setContentText(isPlaying ? currentSongName : "No song playing")
                .setContentIntent(pendingIntent)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent);

        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent);
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent);
        }

        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
               .addAction(android.R.drawable.ic_media_stop, "Stop", stopPendingIntent);

        Notification notification = builder.build();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void removeNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    // JavaScript bridge
    public class AndroidBridge {
        private final Context ctx;

        public AndroidBridge(Context ctx) {
            this.ctx = ctx;
        }

        @JavascriptInterface
        public void chooseFolder() {
            runOnUiThread(() -> promptUserToChooseFolder());
        }

        @JavascriptInterface
        public String getSavedFolderUri() {
            String uri = getSavedTreeUri();
            return uri == null ? "" : uri;
        }

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

        // Media control methods
        @JavascriptInterface
        public void playSong(String songUri, String songName) {
            runOnUiThread(() -> MainActivity.this.playSong(songUri, songName));
        }

        @JavascriptInterface
        public void pauseSong() {
            runOnUiThread(() -> MainActivity.this.pauseSong());
        }

        @JavascriptInterface
        public void resumeSong() {
            runOnUiThread(() -> MainActivity.this.resumeSong());
        }

        @JavascriptInterface
        public void stopSong() {
            runOnUiThread(() -> MainActivity.this.stopSong());
        }

        @JavascriptInterface
        public boolean isPlaying() {
            return isPlaying;
        }

        @JavascriptInterface
        public String getCurrentSong() {
            return currentSongName;
        }

        @JavascriptInterface
        public void downloadSong(final String urlString, final String suggestedFileName) {
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

                    String safeName = suggestedFileName;
                    if (safeName == null || safeName.trim().isEmpty()) {
                        safeName = URLUtil.guessFileName(urlString, null, null);
                    }

                    DocumentFile existing = pickedDir.findFile(safeName);
                    if (existing != null) existing.delete();

                    DocumentFile newFile = pickedDir.createFile("audio/mpeg", safeName);

                    if (newFile == null) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to create file in folder", Toast.LENGTH_LONG).show());
                        return;
                    }

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

                        final int responseCode = conn.getResponseCode();

                        if (responseCode != HttpURLConnection.HTTP_OK) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Download failed: HTTP " + responseCode, Toast.LENGTH_LONG).show());
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

                        final String finalSafeName = safeName;
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Saved: " + finalSafeName, Toast.LENGTH_LONG).show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeNotification();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isPlaying) {
            updateNotification();
        }
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