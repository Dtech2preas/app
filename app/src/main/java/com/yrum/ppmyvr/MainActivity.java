package com.yrum.ppmyvr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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
    
    // the website you browse for searching & server downloads
    private final String mainUrl = "https://music.preasx24.co.za";
    
    // Our predetermined download folder
    private File downloadFolder;

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

        // Create and attach bridge
        bridge = new AndroidBridge(this);
        mWebView.addJavascriptInterface(bridge, "Android");

        // CRITICAL: Enhanced WebViewClient to intercept ALL download requests
        mWebView.setWebViewClient(new CustomWebViewClient());

        // Set up WebChromeClient for file downloads
        mWebView.setWebChromeClient(new WebChromeClient());

        // Load the main website
        mWebView.loadUrl(mainUrl);
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
        } else {
            Log.d(TAG, "Download folder already exists: " + downloadFolder.getAbsolutePath());
        }
        
        // Save the folder path
        saveDownloadFolderPath(downloadFolder.getAbsolutePath());
        Toast.makeText(this, "Downloads will be saved to: " + downloadFolder.getName(), Toast.LENGTH_LONG).show();
    }

    // CRITICAL: Enhanced WebViewClient that intercepts ALL file download requests
    private class CustomWebViewClient extends WebViewClient {
        private Map<String, Boolean> interceptedUrls = new HashMap<>();

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "shouldOverrideUrlLoading: " + url);
            
            if (isDownloadUrl(url) && !interceptedUrls.containsKey(url)) {
                interceptedUrls.put(url, true);
                String fileName = extractFileNameFromUrl(url);
                bridge.downloadSong(url, fileName);
                return true; // Prevent WebView from loading the URL
            }
            
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String url = request.getUrl().toString();
                Log.d(TAG, "shouldOverrideUrlLoading (request): " + url);
                
                if (isDownloadUrl(url) && !interceptedUrls.containsKey(url)) {
                    interceptedUrls.put(url, true);
                    String fileName = extractFileNameFromUrl(url);
                    bridge.downloadSong(url, fileName);
                    return true;
                }
            }
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            // This intercepts ALL requests including API calls and file downloads
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String url = request.getUrl().toString();
                
                // Log download-related requests for debugging
                if (url.contains("/download/") || url.contains("/file/")) {
                    Log.d(TAG, "Intercepting request: " + url);
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        private boolean isDownloadUrl(String url) {
            if (url == null) return false;
            
            String lowerUrl = url.toLowerCase();
            return lowerUrl.contains("/download/") ||
                   lowerUrl.contains("/file/") ||
                   lowerUrl.endsWith(".mp3") ||
                   lowerUrl.endsWith(".m4a") ||
                   lowerUrl.endsWith(".wav") ||
                   lowerUrl.endsWith(".ogg") ||
                   lowerUrl.endsWith(".opus") ||
                   lowerUrl.contains("?download=true") ||
                   lowerUrl.contains("&download=true") ||
                   (lowerUrl.contains("download") && (lowerUrl.contains("file") || lowerUrl.contains("blob")));
        }

        private String extractFileNameFromUrl(String url) {
            try {
                // Try to extract filename from URL
                if (url.contains("/download/file/")) {
                    // Extract song index and create filename
                    String[] parts = url.split("/");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("file") && i + 1 < parts.length) {
                            return "song_" + parts[i + 1] + ".opus";
                        }
                    }
                } else if (url.contains("/download/")) {
                    // Extract song index from download URL
                    String[] parts = url.split("/");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("download") && i + 1 < parts.length) {
                            return "song_" + parts[i + 1] + ".opus";
                        }
                    }
                }
                
                // Fallback: use last part of URL as filename
                String[] pathSegments = url.split("/");
                String lastSegment = pathSegments[pathSegments.length - 1];
                if (lastSegment.contains("?")) {
                    lastSegment = lastSegment.split("\\?")[0];
                }
                if (!lastSegment.contains(".")) {
                    lastSegment += ".opus";
                }
                return lastSegment;
                
            } catch (Exception e) {
                Log.e(TAG, "Error extracting filename from URL: " + e.getMessage());
                return "download_" + System.currentTimeMillis() + ".opus";
            }
        }
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
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Now playing: " + currentSongName, Toast.LENGTH_SHORT).show());
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error playing song", Toast.LENGTH_SHORT).show());
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

    private void saveDownloadFolderPath(String path) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREFS_KEY_DOWNLOAD_FOLDER, path).apply();
    }

    private String getSavedDownloadFolderPath() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREFS_KEY_DOWNLOAD_FOLDER, null);
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

        // Build notification
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

        @android.webkit.JavascriptInterface
        public String getDownloadFolderPath() {
            return downloadFolder != null ? downloadFolder.getAbsolutePath() : "";
        }

        @android.webkit.JavascriptInterface
        public String getDownloadedSongs() {
            try {
                JSONArray arr = new JSONArray();
                if (downloadFolder == null || !downloadFolder.exists()) {
                    return arr.toString();
                }

                File[] files = downloadFolder.listFiles();
                if (files != null) {
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
                            }
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

        // CRITICAL: Enhanced download method that handles Flask server downloads
        @android.webkit.JavascriptInterface
        public void downloadSong(final String urlString, final String suggestedFileName) {
            Log.d(TAG, "downloadSong called from JS: " + urlString + " -> " + suggestedFileName);
            new DownloadTask().execute(urlString, suggestedFileName);
        }

        private class DownloadTask extends AsyncTask<String, Integer, Boolean> {
            private String fileName;
            private String errorMessage = "";

            @Override
            protected Boolean doInBackground(String... params) {
                String urlString = params[0];
                String suggestedFileName = params[1];
                
                // Ensure download folder exists
                if (downloadFolder == null || !downloadFolder.exists()) {
                    initializeDownloadFolder();
                }

                // Clean filename - prioritize the suggested filename
                if (suggestedFileName != null && !suggestedFileName.isEmpty()) {
                    fileName = cleanFileName(suggestedFileName);
                } else {
                    // Extract from URL if no suggested filename
                    fileName = extractFileNameFromUrl(urlString);
                }
                
                File outputFile = new File(downloadFolder, fileName);

                Log.d(TAG, "Starting download: " + fileName);
                Log.d(TAG, "From URL: " + urlString);
                Log.d(TAG, "Saving to: " + outputFile.getAbsolutePath());

                HttpURLConnection connection = null;
                InputStream inputStream = null;
                FileOutputStream outputStream = null;

                try {
                    // Create URL connection
                    URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                    
                    // Add cookies and headers for Flask server
                    connection.setRequestProperty("Accept", "*/*");
                    connection.setRequestProperty("Connection", "keep-alive");

                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "HTTP Response Code: " + responseCode);
                    
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        errorMessage = "HTTP " + responseCode;
                        return false;
                    }

                    // Check content type
                    String contentType = connection.getContentType();
                    Log.d(TAG, "Content-Type: " + contentType);
                    
                    // Check if this is a file download (not JSON)
                    if (contentType != null && contentType.contains("application/json")) {
                        errorMessage = "Server returned JSON instead of file - download may be queued";
                        return false;
                    }

                    // Get input stream
                    inputStream = connection.getInputStream();
                    
                    // Create output file
                    outputStream = new FileOutputStream(outputFile);

                    // Copy data
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    long contentLength = connection.getContentLength();
                    
                    Log.d(TAG, "Content-Length: " + contentLength);

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;

                        // Update progress if needed
                        if (contentLength > 0) {
                            int progress = (int) ((totalBytes * 100) / contentLength);
                            publishProgress(progress);
                        }
                    }

                    outputStream.flush();
                    Log.d(TAG, "Download completed: " + totalBytes + " bytes");

                    // Verify file was created
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "File verified: " + outputFile.getAbsolutePath() + " size: " + outputFile.length());
                        return true;
                    } else {
                        errorMessage = "File was not created properly";
                        return false;
                    }

                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    Log.e(TAG, "Download error: " + e.getMessage(), e);
                    return false;
                } finally {
                    // Clean up
                    try {
                        if (inputStream != null) inputStream.close();
                        if (outputStream != null) outputStream.close();
                        if (connection != null) connection.disconnect();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing streams: " + e.getMessage());
                    }
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                int progress = values[0];
                Log.d(TAG, "Download progress: " + progress + "%");
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    Toast.makeText(MainActivity.this, "Download saved: " + fileName, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "SUCCESS: File saved to " + downloadFolder.getAbsolutePath() + "/" + fileName);
                    
                    // Refresh the library in the web page
                    mWebView.evaluateJavascript("if(window.loadLocalSongs) loadLocalSongs();", null);
                } else {
                    Toast.makeText(MainActivity.this, "Download failed: " + errorMessage, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "FAILED: " + errorMessage);
                }
            }

            private String cleanFileName(String fileName) {
                if (fileName == null || fileName.trim().isEmpty()) {
                    return "download_" + System.currentTimeMillis() + ".opus";
                }
                
                // Remove invalid characters
                String cleanName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                cleanName = cleanName.replaceAll("_{2,}", "_");
                cleanName = cleanName.trim();
                
                if (cleanName.isEmpty()) {
                    cleanName = "download_" + System.currentTimeMillis() + ".opus";
                }
                
                // Ensure extension (your server uses .opus files)
                if (!cleanName.contains(".")) {
                    cleanName += ".opus";
                }
                
                return cleanName;
            }

            private String extractFileNameFromUrl(String url) {
                try {
                    if (url.contains("/download/file/")) {
                        String[] parts = url.split("/");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("file") && i + 1 < parts.length) {
                                return "song_" + parts[i + 1] + ".opus";
                            }
                        }
                    } else if (url.contains("/download/")) {
                        String[] parts = url.split("/");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("download") && i + 1 < parts.length) {
                                return "song_" + parts[i + 1] + ".opus";
                            }
                        }
                    }
                    
                    String[] pathSegments = url.split("/");
                    String lastSegment = pathSegments[pathSegments.length - 1];
                    if (lastSegment.contains("?")) {
                        lastSegment = lastSegment.split("\\?")[0];
                    }
                    if (!lastSegment.contains(".")) {
                        lastSegment += ".opus";
                    }
                    return lastSegment;
                    
                } catch (Exception e) {
                    return "download_" + System.currentTimeMillis() + ".opus";
                }
            }
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