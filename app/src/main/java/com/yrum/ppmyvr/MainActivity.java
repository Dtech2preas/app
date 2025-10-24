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
import android.media.MediaPlayer;
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

    // Media Player
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;
    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

    private WebView mWebView;
    private FrameLayout rootLayout;

    // the website you browse for searching & server downloads
    private final String mainUrl = "https://music.preasx24.co.za";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        // Initialize MediaPlayer
        initializeMediaPlayer();

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
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        mWebView.setWebViewClient(new WebViewClient());
        mWebView.addJavascriptInterface(new AndroidBridge(this), "Android");

        // Download listener - intercept file downloads and save to selected folder
        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                Log.d(TAG, "Download started: " + url);
                String guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
                new AndroidBridge(MainActivity.this).downloadSong(url, guessed);
            }
        });

        // Load the main website (D-TECH MUSIC player)
        mWebView.loadUrl(mainUrl);

        // Check and create default folder if needed
        checkAndCreateDefaultFolder();

        // If no folder selected yet, prompt
        String treeUri = getSavedTreeUri();
        if (treeUri == null || treeUri.isEmpty()) {
            promptUserToChooseFolder();
        }
    }

    private void initializeMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            runOnUiThread(() -> {
                // Auto-play next song when current finishes
                mWebView.evaluateJavascript("if(window.playNextSong) playNextSong();", null);
            });
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            mediaPlayer.start();
            isPlaying = true;
            updateNotification();
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Now playing: " + currentSongName, Toast.LENGTH_SHORT).show();
            });
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Error playing song", Toast.LENGTH_SHORT).show();
            });
            return false;
        });
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
                case "PLAY":
                    playCurrentSong();
                    break;
                case "PAUSE":
                    pauseSong();
                    break;
                case "STOP":
                    stopSong();
                    break;
                case "NEXT":
                    mWebView.evaluateJavascript("if(window.playNextSong) playNextSong();", null);
                    break;
                case "PREVIOUS":
                    mWebView.evaluateJavascript("if(window.playPreviousSong) playPreviousSong();", null);
                    break;
            }
        }
    }

    private void checkAndCreateDefaultFolder() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean defaultFolderCreated = prefs.getBoolean(PREFS_KEY_DEFAULT_FOLDER_CREATED, false);
        
        if (!defaultFolderCreated) {
            File musicDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "D-TECH MUSIC");
            if (!musicDir.exists()) {
                musicDir.mkdirs();
                Log.d(TAG, "Default folder created: " + musicDir.getAbsolutePath());
            }
            prefs.edit().putBoolean(PREFS_KEY_DEFAULT_FOLDER_CREATED, true).apply();
        }
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
                        
                        // Notify the web page that folder has been selected
                        mWebView.evaluateJavascript("if(window.onFolderSelected) onFolderSelected();", null);
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

    // Media playback methods using Android MediaPlayer
    private void playSong(String songUri, String songName) {
        try {
            // Stop current playback if any
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            
            currentSongName = songName;
            currentSongUri = songUri;
            isPrepared = false;

            // Set data source and prepare async
            mediaPlayer.setDataSource(this, Uri.parse(songUri));
            mediaPlayer.prepareAsync();
            
            updateNotification();
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing song: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error playing song", Toast.LENGTH_SHORT).show());
        }
    }

    private void playCurrentSong() {
        if (isPrepared && !isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            updateNotification();
        }
    }

    private void pauseSong() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            updateNotification();
        }
    }

    private void stopSong() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.reset();
        isPlaying = false;
        isPrepared = false;
        currentSongName = "";
        currentSongUri = "";
        updateNotification();
    }

    // Notification methods - Professional music player notification
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "D-TECH MUSIC Player",
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

        // Create media action intents
        Intent playIntent = new Intent(this, MainActivity.class);
        playIntent.setAction("PLAY");
        PendingIntent playPendingIntent = PendingIntent.getActivity(this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(this, MainActivity.class);
        pauseIntent.setAction("PAUSE");
        PendingIntent pausePendingIntent = PendingIntent.getActivity(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, MainActivity.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getActivity(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent nextIntent = new Intent(this, MainActivity.class);
        nextIntent.setAction("NEXT");
        PendingIntent nextPendingIntent = PendingIntent.getActivity(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent prevIntent = new Intent(this, MainActivity.class);
        prevIntent.setAction("PREVIOUS");
        PendingIntent prevPendingIntent = PendingIntent.getActivity(this, 5, prevIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Build professional music player notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(isPlaying ? "Now Playing" : "D-TECH MUSIC")
                .setContentText(isPlaying ? currentSongName : "No song playing")
                .setContentIntent(pendingIntent)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent);

        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent);
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent);
        }

        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
               .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent);

        // For Android 13+, we need to request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void removeNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    // JavaScript bridge for communication with the web app
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
            runOnUiThread(() -> MainActivity.this.playCurrentSong());
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
        public int getCurrentPosition() {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }

        @JavascriptInterface
        public int getDuration() {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }

        @JavascriptInterface
        public void seekTo(int position) {
            try {
                if (isPrepared) {
                    mediaPlayer.seekTo(position);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error seeking: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void downloadSong(final String urlString, final String suggestedFileName) {
            AsyncTask.execute(() -> {
                try {
                    String tree = getSavedTreeUri();
                    if (tree == null || tree.isEmpty()) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Please select a folder first", Toast.LENGTH_LONG).show();
                            mWebView.evaluateJavascript("if(window.showFolderSelectionRequired) showFolderSelectionRequired();", null);
                        });
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

                    // Clean filename
                    safeName = safeName.replaceAll("[^a-zA-Z0-9.\\-\\s]", "_");
                    safeName = safeName.replaceAll("_{2,}", "_");

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
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Download failed: HTTP " + responseCode, Toast.LENGTH_LONG).show();
                                mWebView.evaluateJavascript("if(window.onDownloadFailed) onDownloadFailed('HTTP " + responseCode + "');", null);
                            });
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
                        long totalRead = 0;
                        long contentLength = conn.getContentLength();
                        
                        // Create final copy for use in lambda
                        final String finalSafeName = safeName;
                        
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                            totalRead += len;
                            
                            // Update progress if content length is known
                            if (contentLength > 0) {
                                final int progress = (int) ((totalRead * 100) / contentLength);
                                runOnUiThread(() -> {
                                    mWebView.evaluateJavascript("if(window.onDownloadProgress) onDownloadProgress('" + finalSafeName + "', " + progress + ");", null);
                                });
                            }
                        }
                        out.flush();

                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Saved: " + finalSafeName, Toast.LENGTH_LONG).show();
                            try {
                                mWebView.evaluateJavascript("if(window.onDownloadComplete) onDownloadComplete('" + finalSafeName + "');", null);
                                // Reload the library to show new songs
                                mWebView.evaluateJavascript("if(window.reloadLibrary) reloadLibrary();", null);
                                mWebView.evaluateJavascript("if(window.loadLocalSongs) loadLocalSongs();", null);
                            } catch (Exception ignored) {}
                        });

                    } finally {
                        try { if (in != null) in.close(); } catch (Exception ignored) {}
                        try { if (out != null) out.close(); } catch (Exception ignored) {}
                        if (conn != null) conn.disconnect();
                    }

                } catch (final Exception e) {
                    Log.e(TAG, "downloadSong error: " + e.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        mWebView.evaluateJavascript("if(window.onDownloadFailed) onDownloadFailed('" + e.getMessage() + "');", null);
                    });
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
        }
        removeNotification();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Keep music playing and notification alive when app goes to background
        if (isPlaying) {
            updateNotification();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update notification when app comes to foreground
        updateNotification();
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            // Don't stop music when going back - just minimize the app
            moveTaskToBack(true);
        }
    }
}