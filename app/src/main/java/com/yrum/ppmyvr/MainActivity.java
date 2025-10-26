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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
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

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity implements MusicService.MediaControllerCallback {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "spotifydl_prefs";
    private static final String PREFS_KEY_DOWNLOAD_FOLDER = "download_folder_path";

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
    private final String localUrl = "file:///android_asset/index.html";

    // Our predetermined download folder
    private File downloadFolder;
    private Handler downloadCheckHandler = new Handler();
    private Map<String, Runnable> downloadCheckRunnables = new HashMap<>();

    // Track if this is first app launch to show folder notification only once
    private boolean isFirstLaunch = true;

    // Network state tracking
    private boolean isOnline = true;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Broadcast receiver for media actions from MediaSession
    private BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Media action received: " + action);
            if (action != null) {
                handleMediaAction(action);
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

            // Set the callback for media control
            musicService.setMediaControllerCallback(MainActivity.this);

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

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_container);
        mWebView = findViewById(R.id.activity_main_webview);

        // Initialize network monitoring
        initializeNetworkMonitoring();

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

        // Custom WebViewClient to handle network state changes
        mWebView.setWebViewClient(new CustomWebViewClient());

        // Load initial URL based on network availability
        loadAppropriateUrl();
    }

    // Implement MediaControllerCallback methods
    @Override
    public void onPlay() {
        Log.d(TAG, "MediaControllerCallback: Play");
        playCurrentSong();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "MediaControllerCallback: Pause");
        pauseSong();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "MediaControllerCallback: Stop");
        stopSong();
    }

    @Override
    public void onNext() {
        Log.d(TAG, "MediaControllerCallback: Next");
        mWebView.evaluateJavascript("if(window.playNextSong) playNextSong();", null);
    }

    @Override
    public void onPrevious() {
        Log.d(TAG, "MediaControllerCallback: Previous");
        mWebView.evaluateJavascript("if(window.playPreviousSong) playPreviousSong();", null);
    }

    private void handleMediaAction(String action) {
        Log.d(TAG, "Handling media action: " + action);
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

    private void initializeNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    Log.d(TAG, "Network available");
                    if (!isOnline) {
                        isOnline = true;
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Internet connected - loading online content", Toast.LENGTH_SHORT).show();
                            loadAppropriateUrl();
                        });
                    }
                }

                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    Log.d(TAG, "Network lost");
                    if (isOnline) {
                        isOnline = false;
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "No internet - loading offline content", Toast.LENGTH_LONG).show();
                            loadAppropriateUrl();
                        });
                    }
                }
            };

            // Register the network callback
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            // For older devices, use BroadcastReceiver
            IntentFilter connectivityFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateConnectionStatus();
                }
            }, connectivityFilter);
        }

        // Initial network status check
        updateConnectionStatus();
    }

    private void updateConnectionStatus() {
        boolean previousStatus = isOnline;
        isOnline = isNetworkAvailable();

        if (previousStatus != isOnline) {
            runOnUiThread(() -> {
                if (isOnline) {
                    Toast.makeText(MainActivity.this, "Internet connected - loading online content", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "No internet - loading offline content", Toast.LENGTH_LONG).show();
                }
                loadAppropriateUrl();
            });
        }
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && 
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    private void loadAppropriateUrl() {
        if (isOnline) {
            Log.d(TAG, "Loading online URL: " + mainUrl);
            mWebView.loadUrl(mainUrl);
        } else {
            Log.d(TAG, "Loading offline URL: " + localUrl);
            mWebView.loadUrl(localUrl);
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.e(TAG, "WebView error: " + description + " Code: " + errorCode);

            // If online but failed to load, try offline
            if (isOnline) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to load online content, switching to offline", Toast.LENGTH_LONG).show();
                    isOnline = false;
                    loadAppropriateUrl();
                });
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Allow local file URLs when offline
            if (url.startsWith("file:///android_asset/") || url.startsWith("javascript:")) {
                return false;
            }

            // For external URLs when offline, show message
            if (!isOnline && !url.startsWith("file://")) {
                Toast.makeText(MainActivity.this, "Internet required for external links", Toast.LENGTH_SHORT).show();
                return true;
            }

            // For main domain URLs, load in WebView
            if (url.contains("music.preasx24.co.za")) {
                view.loadUrl(url);
                return true;
            }

            // For other external URLs, open in browser if online
            if (isOnline) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open external URL: " + e.getMessage());
                }
                return true;
            }

            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Log.d(TAG, "Page loaded: " + url);

            // Inject JavaScript to handle network state in web page
            injectNetworkStateScript();
        }
    }

    private void injectNetworkStateScript() {
        String networkScript = String.format(
            "javascript:(function() {" +
            "window.isOnline = %s;" +
            "if (typeof onNetworkStateChange === 'function') {" +
            "   onNetworkStateChange(%s);" +
            "}" +
            "})()", 
            isOnline ? "true" : "false", 
            isOnline ? "true" : "false"
        );
        mWebView.evaluateJavascript(networkScript, null);
    }

    private void initializeDownloadFolder() {
        // Create download folder in Music directory
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            downloadFolder = new File(musicDir, "D-TECH MUSIC");
        } else {
            downloadFolder = new File(getFilesDir(), "D-TECH MUSIC");
        }

        // Create folder if it doesn't exist
        if (!downloadFolder.exists()) {
            boolean created = downloadFolder.mkdirs();
            Log.d(TAG, "Download folder created: " + created + " at " + downloadFolder.getAbsolutePath());
            if (!created) {
                Log.e(TAG, "FAILED to create download folder!");
                // Don't show toast every time
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
            handleMediaAction(action);
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
        } else if (!isPrepared && currentSongUri != null && !currentSongUri.isEmpty()) {
            // If not prepared but we have a song URI, try to play it
            playSong(currentSongUri, currentSongName);
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
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        // Create Spotify-like media notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
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

        // Network state methods
        @android.webkit.JavascriptInterface
        public boolean isOnline() {
            return isOnline;
        }

        @android.webkit.JavascriptInterface
        public void checkNetworkAndReload() {
            runOnUiThread(() -> {
                updateConnectionStatus();
                loadAppropriateUrl();
            });
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

        // Unregister broadcast receiver
        if (mediaActionReceiver != null) {
            unregisterReceiver(mediaActionReceiver);
        }

        // Unregister network callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
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
    protected void onResume() {
        super.onResume();
        updateNotification();
        // Check network status on resume
        updateConnectionStatus();
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