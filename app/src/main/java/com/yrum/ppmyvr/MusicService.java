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
import android.view.KeyEvent; // ADD THIS IMPORT

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

// Use the correct imports for MediaSessionCompat
import androidx.media.session.MediaButtonReceiver;
import androidx.core.content.ContextCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_player_channel";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();

    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

    // Media session for notifications
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initializeMediaSession();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle media button events - FIXED VERSION
        if (intent != null) {
            KeyEvent keyEvent = MediaButtonReceiver.handleIntent(mediaSession, intent);
            if (keyEvent != null) {
                // Media button was handled, return early
                return START_STICKY;
            }
        }

        // Start as foreground service for Android 14 compatibility
        startForegroundServiceWithNotification();
        return START_STICKY;
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
            channel.setSound(null, null);

            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private void initializeMediaSession() {
        try {
            mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                                  MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

            // Set initial playback state
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_STOP |
                                PlaybackStateCompat.ACTION_SEEK_TO);

            mediaSession.setPlaybackState(stateBuilder.build());
            mediaSession.setActive(true);

            mediaSession.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    super.onPlay();
                    Log.d(TAG, "MediaSession: Play");
                    sendBroadcast(new Intent("PLAY"));
                }

                @Override
                public void onPause() {
                    super.onPause();
                    Log.d(TAG, "MediaSession: Pause");
                    sendBroadcast(new Intent("PAUSE"));
                }

                @Override
                public void onSkipToNext() {
                    super.onSkipToNext();
                    Log.d(TAG, "MediaSession: Next");
                    sendBroadcast(new Intent("NEXT"));
                }

                @Override
                public void onSkipToPrevious() {
                    super.onSkipToPrevious();
                    Log.d(TAG, "MediaSession: Previous");
                    sendBroadcast(new Intent("PREVIOUS"));
                }

                @Override
                public void onStop() {
                    super.onStop();
                    Log.d(TAG, "MediaSession: Stop");
                    sendBroadcast(new Intent("STOP"));
                    stopSelf();
                }

                @Override
                public void onSeekTo(long pos) {
                    super.onSeekTo(pos);
                    Log.d(TAG, "MediaSession: Seek to " + pos);
                    // You can implement seek functionality here if needed
                }
            });

            Log.d(TAG, "MediaSession initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MediaSession: " + e.getMessage(), e);
        }
    }

    private void startForegroundServiceWithNotification() {
        Notification notification = createNotification();
        
        // For Android 14+, use the correct foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int foregroundServiceType = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Use reflection to access the constant for Android 14
                try {
                    Class<?> serviceClass = Service.class;
                    java.lang.reflect.Field field = serviceClass.getField("FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK");
                    foregroundServiceType = field.getInt(null);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting foreground service type: " + e.getMessage());
                    // Fallback to 0 if we can't get the constant
                    foregroundServiceType = 0;
                }
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        Log.d(TAG, "Foreground service started");
    }

    private Notification createNotification() {
        // Create album art bitmap
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play);

        // Create media style notification
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
                .setMediaSession(mediaSession.getSessionToken())
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
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, appIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        // For Android 13+, set foreground service behavior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
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
                                PlaybackStateCompat.ACTION_STOP |
                                PlaybackStateCompat.ACTION_SEEK_TO)
                    .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);

            mediaSession.setPlaybackState(stateBuilder.build());

            // Update metadata
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
            
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                currentSongName.isEmpty() ? "D-TECH Music" : currentSongName
            );
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "D-TECH MUSIC");
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "D-TECH MUSIC");

            // Set D-TECH logo
            try {
                Bitmap dtechLogo = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
                if (dtechLogo != null) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, dtechLogo);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not load D-TECH logo: " + e.getMessage());
            }

            mediaSession.setMetadata(metadataBuilder.build());
        }

        // Update the notification
        updateNotification();

        Log.d(TAG, "Playback state updated - Playing: " + playing + ", Song: " + currentSongName);
    }

    private void updateNotification() {
        if (notificationManager != null) {
            Notification notification = createNotification();
            notificationManager.notify(NOTIFICATION_ID, notification);
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
        Log.d(TAG, "MusicService onDestroy called");
        
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        
        // Stop foreground service and remove notification
        stopForeground(true);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        
        Log.d(TAG, "MusicService destroyed");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed, stopping service");
        stopSelf();
    }
}