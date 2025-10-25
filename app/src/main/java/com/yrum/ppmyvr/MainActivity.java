package com.yrum.ppmyvr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "spotifydl_prefs";
    private static final String PREFS_KEY_DOWNLOAD_FOLDER = "download_folder_path";

    private static final String CHANNEL_ID = "music_player_channel";
    private static final int NOTIFICATION_ID = 1;

    private static final int PERMISSION_REQUEST_CODE = 101;

    private NotificationManager notificationManager;
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;
    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

    private WebView mWebView;
    private FrameLayout rootLayout;
    private AndroidBridge bridge;

    private final String mainUrl = "https://music.preasx24.co.za"; // your backend HTML
    private File downloadFolder;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        // Check permissions
        checkAndRequestPermissions();

        initializeMediaPlayer();
        createNotificationChannel();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        handleIntent(getIntent());
        initializeDownloadFolder();

        // Configure WebView
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        bridge = new AndroidBridge(this);
        mWebView.addJavascriptInterface(bridge, "Android");
        mWebView.setWebViewClient(new WebViewClient());

        // Handle any downloads
        setupDownloadListener();

        // Load your backend HTML
        mWebView.loadUrl(mainUrl);
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void setupDownloadListener() {
        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype, long contentLength) {
                try {
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);

                    File dtechFolder = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MUSIC), "D-TECH MUSIC");
                    if (!dtechFolder.exists()) {
                        dtechFolder.mkdirs();
                    }

                    File destinationFile = new File(dtechFolder, fileName);
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                    request.setTitle(fileName);
                    request.setDescription("Downloading to D-TECH MUSIC");
                    request.setMimeType(mimetype);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.allowScanningByMediaScanner();
                    request.setDestinationUri(Uri.fromFile(destinationFile));

                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(request);

                    Toast.makeText(MainActivity.this, "Downloading: " + fileName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Download error: ", e);
                }
            }
        });
    }

    private void initializeDownloadFolder() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            downloadFolder = new File(musicDir, "D-TECH MUSIC");
        } else {
            downloadFolder = new File(getFilesDir(), "D-TECH MUSIC");
        }

        if (!downloadFolder.exists()) {
            boolean created = downloadFolder.mkdirs();
            Log.d(TAG, "Download folder created: " + created + " at " + downloadFolder.getAbsolutePath());
        } else {
            Log.d(TAG, "Download folder already exists: " + downloadFolder.getAbsolutePath());
        }

        saveDownloadFolderPath(downloadFolder.getAbsolutePath());
        Toast.makeText(this, "Downloads will be saved to: " + downloadFolder.getName(), Toast.LENGTH_LONG).show();
    }

    private void saveDownloadFolderPath(String path) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREFS_KEY_DOWNLOAD_FOLDER, path).apply();
    }

    private void initializeMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            runOnUiThread(() -> mWebView.evaluateJavascript("if(window.playNextSong) playNextSong();", null));
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            mediaPlayer.start();
            isPlaying = true;
            updateNotification();
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Now playing: " + currentSongName, Toast.LENGTH_SHORT).show());
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Error playing song", Toast.LENGTH_SHORT).show());
            return false;
        });
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PLAY":
                    playCurrentSong();
                    break;
                case "PAUSE":
                    pauseSong();
                    break;
                case "STOP":
                    stopSong();
                    break;
            }
        }
    }

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
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private void updateNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(isPlaying ? "Now Playing" : "D-TECH MUSIC")
                .setContentText(isPlaying ? currentSongName : "No song playing")
                .setContentIntent(pendingIntent)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    // ================== JS BRIDGE =====================
    public class AndroidBridge {
        private final Context ctx;

        public AndroidBridge(Context ctx) {
            this.ctx = ctx;
        }

        @android.webkit.JavascriptInterface
        public String getDownloadFolderPath() {
            return downloadFolder != null ? downloadFolder.getAbsolutePath() : "";
        }

        @android.webkit.JavascriptInterface
        public void playSong(String songUri, String songName) {
            runOnUiThread(() -> MainActivity.this.playSong(songUri, songName));
        }
    }

    // ================== MEDIA CONTROLS =====================
    private void playSong(String songUri, String songName) {
        try {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.reset();

            currentSongName = songName;
            currentSongUri = songUri;
            isPrepared = false;

            mediaPlayer.setDataSource(this, Uri.parse(songUri));
            mediaPlayer.prepareAsync();
            updateNotification();
        } catch (Exception e) {
            Log.e(TAG, "Error playing song: " + e.getMessage());
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
        if (mediaPlayer.isPlaying()) mediaPlayer.stop();
        mediaPlayer.reset();
        isPlaying = false;
        isPrepared = false;
        currentSongName = "";
        currentSongUri = "";
        updateNotification();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
        }
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            moveTaskToBack(true);
        }
    }
}