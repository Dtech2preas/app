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
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private final IBinder binder = new LocalBinder();
    
    // Notification
    private static final String CHANNEL_ID = "music_channel";
    private static final int NOTIFICATION_ID = 1;
    
    // Playback states
    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";
    private int currentPosition = 0;
    private int totalDuration = 0;
    
    // Repeat modes: 0 = no repeat, 1 = repeat all, 2 = repeat one
    private int repeatMode = 0;
    private boolean shuffleMode = false;
    
    // Media session for notifications
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;
    
    // Handler for updating progress
    private Handler progressHandler = new Handler();
    private Runnable progressRunnable;

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
        setupProgressUpdater();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle media button actions
        if (intent != null && intent.getAction() != null) {
            handleMediaAction(intent.getAction());
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                              MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set playback state with all actions including shuffle and repeat
        updateMediaSessionState();

        mediaSession.setActive(true);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                Log.d(TAG, "MediaSession: Play");
                sendBroadcast(new Intent("PLAY"));
                startPlayback();
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.d(TAG, "MediaSession: Pause");
                sendBroadcast(new Intent("PAUSE"));
                pausePlayback();
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
                stopPlayback();
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
                Log.d(TAG, "MediaSession: Seek to " + pos);
                currentPosition = (int) pos;
                Intent seekIntent = new Intent("SEEK_TO");
                seekIntent.putExtra("position", (int) pos);
                sendBroadcast(seekIntent);
                updateNotification();
            }

            @Override
            public void onSetRepeatMode(int repeatMode) {
                super.onSetRepeatMode(repeatMode);
                Log.d(TAG, "MediaSession: Repeat mode " + repeatMode);
                setRepeatMode(repeatMode);
            }

            @Override
            public void onSetShuffleMode(int shuffleMode) {
                super.onSetShuffleMode(shuffleMode);
                boolean shuffle = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL;
                Log.d(TAG, "MediaSession: Shuffle mode " + shuffle);
                setShuffleMode(shuffle);
            }
        });
    }

    private void setupProgressUpdater() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying && totalDuration > 0) {
                    currentPosition += 1000; // Update every second
                    if (currentPosition >= totalDuration) {
                        // Song finished
                        currentPosition = 0;
                        sendBroadcast(new Intent("SONG_FINISHED"));
                    }
                    
                    // Update media session and notification
                    updateMediaSessionState();
                    updateNotification();
                    
                    progressHandler.postDelayed(this, 1000);
                }
            }
        };
    }

    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
        if (isPlaying) {
            progressHandler.post(progressRunnable);
        }
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

        // Build actions
        long actions = PlaybackStateCompat.ACTION_PLAY |
                      PlaybackStateCompat.ACTION_PAUSE |
                      PlaybackStateCompat.ACTION_PLAY_PAUSE |
                      PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                      PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                      PlaybackStateCompat.ACTION_STOP |
                      PlaybackStateCompat.ACTION_SEEK_TO;

        // Add shuffle and repeat actions
        actions |= PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
        actions |= PlaybackStateCompat.ACTION_SET_REPEAT_MODE;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, currentPosition, 1.0f);

        // Set shuffle mode
        int shuffleModeCompat = shuffleMode ? 
            PlaybackStateCompat.SHUFFLE_MODE_ALL : PlaybackStateCompat.SHUFFLE_MODE_NONE;
        stateBuilder.setShuffleMode(shuffleModeCompat);

        // Set repeat mode
        int repeatModeCompat;
        switch (repeatMode) {
            case 1: repeatModeCompat = PlaybackStateCompat.REPEAT_MODE_ALL; break;
            case 2: repeatModeCompat = PlaybackStateCompat.REPEAT_MODE_ONE; break;
            default: repeatModeCompat = PlaybackStateCompat.REPEAT_MODE_NONE;
        }
        stateBuilder.setRepeatMode(repeatModeCompat);

        mediaSession.setPlaybackState(stateBuilder.build());

        // Update metadata
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                                (currentSongName != null && !currentSongName.isEmpty()) ? 
                                currentSongName : "D-TECH Music");
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "D-TECH MUSIC");
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "D-TECH MUSIC");
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, totalDuration);

        // Set D-TECH logo
        Bitmap dtechLogo = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, dtechLogo);

        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void updateNotification() {
        if (mediaSession == null) return;

        // Create intent for opening the app
        Intent intent = new Intent(this, MainActivity.class); // Replace with your main activity
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build notification using MediaStyle
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(currentSongName.isEmpty() ? "D-TECH Music" : currentSongName)
                .setContentText("D-TECH MUSIC")
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOngoing(isPlaying);

        // Use MediaStyle for media controls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.support.v4.media.app.NotificationCompat.MediaStyle style = 
                new android.support.v4.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2); // Play/pause, previous, next

            builder.setStyle(style);
        }

        Notification notification = builder.build();

        // Start foreground service
        startForeground(NOTIFICATION_ID, notification);
    }

    private void handleMediaAction(String action) {
        switch (action) {
            case "PLAY":
                startPlayback();
                break;
            case "PAUSE":
                pausePlayback();
                break;
            case "TOGGLE_PLAYBACK":
                if (isPlaying) {
                    pausePlayback();
                } else {
                    startPlayback();
                }
                break;
            case "NEXT":
                sendBroadcast(new Intent("NEXT"));
                break;
            case "PREVIOUS":
                sendBroadcast(new Intent("PREVIOUS"));
                break;
            case "STOP":
                stopPlayback();
                break;
        }
    }

    public void startPlayback() {
        isPlaying = true;
        updateMediaSessionState();
        updateNotification();
        startProgressUpdates();
        
        // Start playback in your app
        Intent playIntent = new Intent("PLAY");
        sendBroadcast(playIntent);
    }

    public void pausePlayback() {
        isPlaying = false;
        updateMediaSessionState();
        updateNotification();
        stopProgressUpdates();
        
        // Pause playback in your app
        Intent pauseIntent = new Intent("PAUSE");
        sendBroadcast(pauseIntent);
    }

    public void stopPlayback() {
        isPlaying = false;
        currentPosition = 0;
        updateMediaSessionState();
        stopProgressUpdates();
        stopForeground(true);
        
        // Stop playback in your app
        Intent stopIntent = new Intent("STOP");
        sendBroadcast(stopIntent);
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
        if (repeatMode > 2) repeatMode = 0; // Cycle through 0,1,2
        
        updateMediaSessionState();
        
        // Broadcast repeat mode change
        Intent repeatIntent = new Intent("REPEAT_MODE_CHANGED");
        repeatIntent.putExtra("repeat_mode", repeatMode);
        sendBroadcast(repeatIntent);
    }

    public void setShuffleMode(boolean shuffle) {
        this.shuffleMode = shuffle;
        updateMediaSessionState();
        
        // Broadcast shuffle mode change
        Intent shuffleIntent = new Intent("SHUFFLE_MODE_CHANGED");
        shuffleIntent.putExtra("shuffle_mode", shuffle);
        sendBroadcast(shuffleIntent);
    }

    public void updatePlaybackState(boolean playing, String songName, String songUri, int position, int duration) {
        this.isPlaying = playing;
        this.currentSongName = songName;
        this.currentSongUri = songUri;
        this.currentPosition = position;
        this.totalDuration = duration;

        updateMediaSessionState();
        updateNotification();

        if (isPlaying) {
            startProgressUpdates();
        } else {
            stopProgressUpdates();
        }

        Log.d(TAG, "Playback state updated - Playing: " + playing + ", Song: " + songName + 
              ", Position: " + position + ", Duration: " + duration);
    }

    // Getters
    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public String getCurrentSongName() {
        return currentSongName;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public boolean isShuffleMode() {
        return shuffleMode;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProgressUpdates();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        stopForeground(true);
        Log.d(TAG, "MusicService destroyed");
    }
}