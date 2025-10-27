package com.yrum.ppmyvr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private final IBinder binder = new LocalBinder();

    private static final String CHANNEL_ID = "dtech_music_channel";
    private static final int NOTIFICATION_ID = 101;

    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

    // Media session for system media controls
    private MediaSessionCompat mediaSession;

    // Callback interface to communicate with MainActivity
    public interface MediaControllerCallback {
        void onPlay();
        void onPause();
        void onStop();
        void onNext();
        void onPrevious();
        void onPlaySong(String songUri, String songName);
    }
    
    private MediaControllerCallback mediaControllerCallback;

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MusicService created");
        initializeMediaSession();
        createNotificationChannel();
        // Start as foreground service immediately to prevent being killed
        startForegroundService();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MusicService started with intent: " + intent);
        
        // Handle media actions from system media controls
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "Received media action: " + action);
            handleMediaAction(action);
        }
        
        // Return STICKY to keep service running even if app is killed
        return START_STICKY;
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                              MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set media buttons that we handle
        long playbackActions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_SEEK_TO;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(playbackActions)
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setActive(true);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                Log.d(TAG, "MediaSession: Play received");
                if (mediaControllerCallback != null) {
                    mediaControllerCallback.onPlay();
                } else {
                    // If no callback, try to start app
                    startAppAndPlay();
                }
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.d(TAG, "MediaSession: Pause received");
                if (mediaControllerCallback != null) {
                    mediaControllerCallback.onPause();
                }
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.d(TAG, "MediaSession: Next received");
                if (mediaControllerCallback != null) {
                    mediaControllerCallback.onNext();
                }
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.d(TAG, "MediaSession: Previous received");
                if (mediaControllerCallback != null) {
                    mediaControllerCallback.onPrevious();
                }
            }

            @Override
            public void onStop() {
                super.onStop();
                Log.d(TAG, "MediaSession: Stop received");
                if (mediaControllerCallback != null) {
                    mediaControllerCallback.onStop();
                }
            }
        });
    }

    private void startAppAndPlay() {
        // Start the app when play is pressed from notification
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setAction("PLAY_FROM_NOTIFICATION");
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(launchIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "D-TECH Music Player",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("D-TECH Music Player is running");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setSound(null, null);
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH);

            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        // Create intent to open app when notification is clicked
        Intent appIntent = new Intent(this, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, appIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Build the notification
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        String notificationText = isPlaying ? 
                (currentSongName.isEmpty() ? "Playing music" : "Now playing: " + currentSongName) :
                "D-TECH Music Player is active";

        builder.setContentTitle("D-TECH Music Player")
               .setContentText(notificationText)
               .setSmallIcon(android.R.drawable.ic_media_play)
               .setContentIntent(contentIntent)
               .setOngoing(true)
               .setVisibility(Notification.VISIBILITY_PUBLIC)
               .setPriority(Notification.PRIORITY_HIGH)
               .setShowWhen(false)
               .setOnlyAlertOnce(true);

        return builder.build();
    }

    public void startForegroundService() {
        // Start as foreground service to prevent being killed
        startForeground(NOTIFICATION_ID, createNotification());
        Log.d(TAG, "Music service started as foreground with high priority");
    }

    public void updateNotification() {
        // Update the notification with current state
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        this.currentSongName = songName != null ? songName : "";
        this.currentSongUri = songUri != null ? songUri : "";

        if (mediaSession != null) {
            int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_STOP)
                    .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);

            mediaSession.setPlaybackState(stateBuilder.build());

            // Update metadata for lock screen and notification
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                                      currentSongName.isEmpty() ? "D-TECH Music" : currentSongName);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "D-TECH MUSIC");
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "D-TECH MUSIC");

            // Use system default media play icon for album art
            Bitmap albumArt = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play);
            if (albumArt != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
            }

            mediaSession.setMetadata(metadataBuilder.build());
        }

        // Update notification with current state
        updateNotification();

        Log.d(TAG, "Playback state updated - Playing: " + playing + ", Song: " + currentSongName);
    }

    // Set callback for media control
    public void setMediaControllerCallback(MediaControllerCallback callback) {
        this.mediaControllerCallback = callback;
        Log.d(TAG, "Media controller callback set");
    }

    // Handle media actions directly in service
    private void handleMediaAction(String action) {
        Log.d(TAG, "Handling media action: " + action);
        if (mediaControllerCallback != null) {
            switch (action) {
                case "PLAY":
                    mediaControllerCallback.onPlay();
                    break;
                case "PAUSE":
                    mediaControllerCallback.onPause();
                    break;
                case "STOP":
                    mediaControllerCallback.onStop();
                    break;
                case "NEXT":
                    mediaControllerCallback.onNext();
                    break;
                case "PREVIOUS":
                    mediaControllerCallback.onPrevious();
                    break;
            }
        } else {
            Log.d(TAG, "No media controller callback available, starting app...");
            // If no callback, start the app to handle the action
            startAppAndPlay();
        }
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public String getCurrentSongName() {
        return currentSongName;
    }

    public String getCurrentSongUri() {
        return currentSongUri;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MusicService being destroyed");
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        // Stop foreground service and remove notification
        stopForeground(true);
        Log.d(TAG, "MusicService destroyed");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed, but keeping service alive");
        // Don't stop the service when app is removed from recent apps
    }
}