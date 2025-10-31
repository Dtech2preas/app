package com.yrum.ppmyvr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "spotifydl_prefs";
    private static final String PREFS_KEY_DOWNLOAD_FOLDER = "download_folder_path";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // --- Constants for Ad URL ---
    private static final String PREFS_KEY_AD_COUNT = "ad_open_count";
    private static final String PREFS_KEY_AD_TIMESTAMP = "last_ad_open_timestamp";
    private static final String AD_URL_TO_OPEN = "https://otieu.com/4/10119706";
    // --- NEW: Ad Timer ---
    private Handler adHandler = new Handler();
    private static final long AD_TIMER_INTERVAL = 3 * 60 * 60 * 1000; // 3 hours
    // ---------------------------------

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
    private AndroidBridge bridge;

    // Service for background playback
    private MusicService musicService;
    private boolean isServiceBound = false;

    // the website you browse for searching & server downloads
    private final String mainUrl = "https://music.preasx24.co.za";

    // Our predetermined download folder
    private File downloadFolder;
    private Handler downloadCheckHandler = new Handler();
    private Map<String, Runnable> downloadCheckRunnables = new HashMap<>();

    // Track if this is first app launch to show folder notification only once
    private boolean isFirstLaunch = true;

    // Broadcast receiver for media actions from MediaSession
    private BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Media action received: " + action);
            if (action != null) {
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
    };

    // Service Connection
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isServiceBound = true;

            // Sync current state with service
            if (isPlaying) {
                musicService.updatePlaybackState(true, currentSongName, currentSongUri);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    // --- NEW: Runnable for the ad timer ---
    private Runnable adRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // Try to trigger the ad
                triggerAdUrl();
            } finally {
                // Re-post the runnable to run again in 3 hours
                adHandler.postDelayed(this, AD_TIMER_INTERVAL);
            }
        }
    };
    // ------------------------------------

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        // Request necessary permissions for Android 14
        requestRequiredPermissions();

        // Register broadcast receiver for media actions
        IntentFilter filter = new IntentFilter();
        filter.addAction("PLAY");
        filter.addAction("PAUSE");
        filter.addAction("STOP");
        filter.addAction("NEXT");
        filter.addAction("PREVIOUS");
        registerReceiver(mediaActionReceiver, filter);

        // Start and bind to Music Service for background playback
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Initialize MediaPlayer
        initializeMediaPlayer();

        // Initialize notification
        createNotificationChannel();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Handle media actions from notification when app is launched
        handleIntent(getIntent());

        // Initialize download folder - CRITICAL!
        initializeDownloadFolder();

        // WebView setup
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Security settings
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);

        // Create and attach bridge
        bridge = new AndroidBridge(this);
        mWebView.addJavascriptInterface(bridge, "Android");

        // --- Use custom WebViewClient to handle intents ---
        mWebView.setWebViewClient(new DTechWebViewClient());
        // --------------------------------------------------

        // Load the main website
        mWebView.loadUrl(mainUrl);

        // --- NEW: Start the ad timer ---
        adHandler.post(adRunnable);
        // -----------------------------
    }

    // --- MODIFIED: Custom WebViewClient to handle unknown URL schemes ---
    private class DTechWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "shouldOverrideUrlLoading: " + url);
            String mainHost = Uri.parse(mainUrl).getHost();

            if (url.startsWith("intent://")) {
                // Handle intent:// URLs -> Outside
                try {
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Log.d(TAG, "Handled intent:// URL");
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Could not parse intent URI: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Cannot open link", Toast.LENGTH_SHORT).show();
                    return true; // Don't load the error page
                }
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                // Handle http/https URLs
                String host = Uri.parse(url).getHost();
                if (mainHost != null && mainHost.equals(host)) {
                    // This is our main site. Load in WebView.
                    return false;
                } else {
                    // This is an external ad or link.
                    // Per user request, load it IN the WebView.
                    return false;
                }
            } else {
                // Other schemes (tg:, tel:, mailto:, etc.) -> Outside
                try {
                    Log.d(TAG, "Handling unknown scheme: " + url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Could not handle unknown scheme: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Cannot open link", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        }
    }
    
    // --- Method to trigger the ad URL 5 times per day (now called by timer) ---
    private void triggerAdUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        int openCount = prefs.getInt(PREFS_KEY_AD_COUNT, 0);
        long lastOpenTimestamp = prefs.getLong(PREFS_KEY_AD_TIMESTAMP, 0);
        long now = System.currentTimeMillis();

        // Check if 24 hours have passed to reset the count
        if (now - lastOpenTimestamp > 24 * 60 * 60 * 1000) {
            Log.d(TAG, "Resetting ad count for the day.");
            openCount = 0;
            lastOpenTimestamp = now; // Set new timestamp for the 24h window
        }

        if (openCount < 5) {
            Log.d(TAG, "Triggering ad URL, count: " + (openCount + 1));
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(AD_URL_TO_OPEN));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                // Increment count and save
                editor.putInt(PREFS_KEY_AD_COUNT, openCount + 1);
                editor.putLong(PREFS_KEY_AD_TIMESTAMP, lastOpenTimestamp);
                editor.apply();

            } catch (Exception e) {
                Log.e(TAG, "Could not open ad URL: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "Ad URL daily limit reached.");
        }
    }
    // -----------------------------------------------------

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }

            // For Android 14+ (API 34+) - READ_MEDIA_AUDIO permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.READ_MEDIA_AUDIO},
                            PERMISSION_REQUEST_CODE);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Handle permission results if needed
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission granted: " + permissions[i]);
                } else {
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                    // Show message for notification permission
                    if (android.Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])) {
                        Toast.makeText(this, "Notification permission is recommended for music controls", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    private void initializeDownloadFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, use scoped storage
            downloadFolder = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "D-TECH MUSIC");
        } else {
            // For older versions, use legacy storage
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                downloadFolder = new File(musicDir, "D-TECH MUSIC");
            } else {
                downloadFolder = new File(getFilesDir(), "D-TECH MUSIC");
            }
        }

        // Create folder if it doesn't exist
        if (!downloadFolder.exists()) {
            boolean created = downloadFolder.mkdirs();
            Log.d(TAG, "Download folder created: " + created + " at " + downloadFolder.getAbsolutePath());
            if (!created) {
                Log.e(TAG, "FAILED to create download folder!");
                if (isFirstLaunch) {
                    Toast.makeText(this, "Failed to create download folder!", Toast.LENGTH_LONG).show();
                }
                return;
            }
        } else {
            Log.d(TAG, "Download folder already exists: " + downloadFolder.getAbsolutePath());
        }

        // Save the folder path
        saveDownloadFolderPath(downloadFolder.getAbsolutePath());

        // Only show folder location on first launch
        if (isFirstLaunch) {
            Toast.makeText(this, "Downloads saved to: D-TECH MUSIC folder", Toast.LENGTH_SHORT).show();
            isFirstLaunch = false;
        }
    }

    private void initializeMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            runOnUiThread(() -> mWebView.evaluateJavascript("if(window.playNextSong) playNextSong();", null));

            // Update service
            if (isServiceBound) {
                musicService.updatePlaybackState(false, currentSongName, currentSongUri);
            }
            updateNotification();
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            mediaPlayer.start();
            isPlaying = true;
            updateNotification();

            // Update service
            if (isServiceBound) {
                musicService.updatePlaybackState(true, currentSongName, currentSongUri);
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
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
            Log.d(TAG, "Handle intent action: " + action);
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

    private void saveDownloadFolderPath(String path) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREFS_KEY_DOWNLOAD_FOLDER, path).apply();
    }

    private String getSavedDownloadFolderPath() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREFS_KEY_DOWNLOAD_FOLDER, null);
    }

    // Media playback methods using Android MediaPlayer
    public void playSong(String songUri, String songName) {
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

            // Update service
            if (isServiceBound) {
                musicService.updatePlaybackState(false, currentSongName, currentSongUri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing song: " + e.getMessage());
        }
    }

    private void playCurrentSong() {
        if (isPrepared && !isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            updateNotification();

            // Update service
            if (isServiceBound) {
                musicService.updatePlaybackState(true, currentSongName, currentSongUri);
            }
        }
    }

    private void pauseSong() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            updateNotification();

            // Update service
            if (isServiceBound) {
                musicService.updatePlaybackState(false, currentSongName, currentSongUri);
            }
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

        // Update service
        if (isServiceBound) {
            musicService.updatePlaybackState(false, "", "");
        }
    }

    // Notification methods
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
            channel.setSound(null, null); // No sound for notifications

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private void updateNotification() {
        if (musicService == null || musicService.getMediaSession() == null) {
            return;
        }

        // Create album art bitmap
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play);

        // Create Spotify-like media notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(albumArt)
            .setContentTitle(currentSongName.isEmpty() ? "D-TECH MUSIC" : currentSongName)
            .setContentText("D-TECH MUSIC")
            .setSubText("Now Playing")
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(new MediaStyle()
                .setMediaSession(musicService.getMediaSession().getSessionToken())
                .setShowActionsInCompactView(0, 1, 2) // Show prev, play/pause, next in compact view
            );

        // Add media actions
        builder.addAction(android.R.drawable.ic_media_previous, "Previous",
            createMediaActionIntent("PREVIOUS"));

        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause",
                createMediaActionIntent("PAUSE"));
        } else {
            // FIX: Changed android.r to android.R
            builder.addAction(android.R.drawable.ic_media_play, "Play",
                createMediaActionIntent("PLAY"));
        }

        builder.addAction(android.R.drawable.ic_media_next, "Next",
            createMediaActionIntent("NEXT"));

        // Set content intent to open app
        Intent appIntent = new Intent(this, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(contentIntent);

        // For Android 13+, set foreground service behavior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private PendingIntent createMediaActionIntent(String action) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(action);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, getRequestCode(action), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private int getRequestCode(String action) {
        switch (action) {
            case "PLAY": return 1;
            case "PAUSE": return 2;
            case "NEXT": return 3;
            case "PREVIOUS": return 4;
            case "STOP": return 5;
            default: return 0;
        }
    }

    private void removeNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    // Enhanced download method with progress tracking
    private void downloadSongWithProgress(String urlString, String fileName, String downloadId) {
        new DownloadTask(downloadId).execute(urlString, fileName);
    }

    private class DownloadTask extends AsyncTask<String, Integer, Boolean> {
        private String downloadId;

        public DownloadTask(String downloadId) {
            this.downloadId = downloadId;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String urlString = params[0];
            String fileName = params[1];

            try {
                // Ensure download folder exists
                if (downloadFolder == null || !downloadFolder.exists()) {
                    initializeDownloadFolder();
                }

                File outputFile = new File(downloadFolder, fileName);

                Log.d(TAG, "Downloading to: " + outputFile.getAbsolutePath());

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                int fileLength = connection.getContentLength();
                InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;

                    // Publish progress
                    if (fileLength > 0) {
                        int progress = (int) ((totalBytes * 100) / fileLength);
                        publishProgress(progress);
                    }
                }

                outputStream.flush();
                outputStream.close();
                inputStream.close();

                // Verify the file
                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.d(TAG, "DOWNLOAD SUCCESS: " + fileName + " (" + outputFile.length() + " bytes)");
                    return true;
                } else {
                    Log.e(TAG, "DOWNLOAD FAILED: File not created properly");
                    return false;
                }

            } catch (Exception e) {
                Log.e(TAG, "DOWNLOAD ERROR: " + e.getMessage(), e);
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            // Send progress to web view
            int progress = values[0];
            String js = String.format("if(window.updateDownloadProgress) updateDownloadProgress('%s', %d);", downloadId, progress);
            mWebView.evaluateJavascript(js, null);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Downloaded: " + downloadId, Toast.LENGTH_SHORT).show();
                    // Refresh the web page
                    mWebView.evaluateJavascript("if(window.loadLocalSongs) loadLocalSongs();", null);
                });
            } else {
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Download failed: " + downloadId, Toast.LENGTH_SHORT).show());
            }
        }
    }

    // JavaScript bridge for communication with the web app
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
        public String getDownloadedSongs() {
            try {
                JSONArray arr = new JSONArray();
                if (downloadFolder == null || !downloadFolder.exists()) {
                    Log.e(TAG, "Download folder is null or doesn't exist");
                    return arr.toString();
                }

                File[] files = downloadFolder.listFiles();
                if (files == null) {
                    Log.e(TAG, "Cannot list files in download folder");
                    return arr.toString();
                }

                Log.d(TAG, "Found " + files.length + " files in download folder");

                for (File file : files) {
                    if (file.isFile()) {
                        String name = file.getName();
                        if (name.toLowerCase().endsWith(".mp3") || name.toLowerCase().endsWith(".m4a") ||
                            name.toLowerCase().endsWith(".wav") || name.toLowerCase().endsWith(".ogg") ||
                            name.toLowerCase().endsWith(".opus")) {
                            JSONObject o = new JSONObject();
                            o.put("name", name);
                            o.put("uri", Uri.fromFile(file).toString());
                            arr.put(o);
                            Log.d(TAG, "Added song: " + name);
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
        @android.webkit.JavascriptInterface
        public void playSong(String songUri, String songName) {
            runOnUiThread(() -> MainActivity.this.playSong(songUri, songName));
        }

        @android.webkit.JavascriptInterface
        public void pauseSong() {
            runOnUiThread(() -> MainActivity.this.pauseSong());
        }

        @android.webkit.JavascriptInterface
        public void resumeSong() {
            runOnUiThread(() -> MainActivity.this.playCurrentSong());
        }

        @android.webkit.JavascriptInterface
        public void stopSong() {
            runOnUiThread(() -> MainActivity.this.stopSong());
        }

        @android.webkit.JavascriptInterface
        public boolean isPlaying() {
            return isPlaying;
        }

        @android.webkit.JavascriptInterface
        public String getCurrentSong() {
            return currentSongName;
        }

        @android.webkit.JavascriptInterface
        public int getCurrentPosition() {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }

        @android.webkit.JavascriptInterface
        public int getDuration() {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }

        @android.webkit.JavascriptInterface
        public void seekTo(int position) {
            try {
                if (isPrepared) {
                    mediaPlayer.seekTo(position);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error seeking: " + e.getMessage());
            }
        }

        // ENHANCED DOWNLOAD METHOD WITH PROGRESS TRACKING
        @android.webkit.JavascriptInterface
        public void downloadSong(final String urlString, final String fileName) {
            Log.d(TAG, "DOWNLOAD STARTED: " + fileName + " from: " + urlString);

            // Generate a unique download ID
            final String downloadId = "dl_" + System.currentTimeMillis();

            // Start download with progress tracking
            runOnUiThread(() -> downloadSongWithProgress(urlString, fileName, downloadId));
        }

        // Test method to check download folder
        @android.webkit.JavascriptInterface
        public String testDownloadFolder() {
            if (downloadFolder == null) {
                return "Download folder is null";
            }

            if (!downloadFolder.exists()) {
                return "Download folder doesn't exist: " + downloadFolder.getAbsolutePath();
            }

            File[] files = downloadFolder.listFiles();
            if (files == null) {
                return "Cannot list files in download folder";
            }

            return "Download folder OK: " + downloadFolder.getAbsolutePath() +
                   " | Files: " + files.length;
        }

        // Remove song from library
        @android.webkit.JavascriptInterface
        public boolean removeSong(String songUri) {
            try {
                Uri uri = Uri.parse(songUri);
                File file = new File(uri.getPath());
                if (file.exists()) {
                    boolean deleted = file.delete();
                    Log.d(TAG, "File deletion: " + deleted + " for " + file.getAbsolutePath());

                    // Refresh local songs
                    runOnUiThread(() -> mWebView.evaluateJavascript("if(window.loadLocalSongs) loadLocalSongs();", null));
                    return deleted;
                }
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error removing song: " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up download check handlers
        for (Runnable runnable : downloadCheckRunnables.values()) {
            downloadCheckHandler.removeCallbacks(runnable);
        }
        downloadCheckRunnables.clear();

        // --- NEW: Stop the ad timer ---
        adHandler.removeCallbacks(adRunnable);
        // ------------------------------

        // Unregister broadcast receiver
        if (mediaActionReceiver != null) {
            unregisterReceiver(mediaActionReceiver);
        }

        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }

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
        // Don't stop playback when app goes to background
        if (isPlaying) {
            updateNotification();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotification();
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
