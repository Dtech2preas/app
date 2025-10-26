package com.yrum.ppmyvr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Random;

public class MusicService extends Service {

    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "dtech_music_channel";
    private final IBinder binder = new LocalBinder();

    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;

    private boolean isPlaying = false;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = off, 1 = all, 2 = one

    private String currentSongName = "";
    private String currentSongUri = "";

    private final Handler progressHandler = new Handler();
    private final Random random = new Random();

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeMediaSession();
        createNotificationChannel();
        Log.d(TAG, "MusicService created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Ensure media session stays active
        mediaSession.setActive(true);
        startForegroundWithNotification();
        return START_STICKY;
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                Log.d(TAG, "MediaSession: Play pressed");
                play();
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.d(TAG, "MediaSession: Pause pressed");
                pause();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.d(TAG, "MediaSession: Next pressed");
                skipToNext();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.d(TAG, "MediaSession: Previous pressed");
                skipToPrevious();
            }

            @Override
            public void onStop() {
                super.onStop();
                stop();
            }

            @Override
            public void onCustomAction(String action, android.os.Bundle extras) {
                super.onCustomAction(action, extras);
                if (action.equals("SHUFFLE")) toggleShuffle();
                if (action.equals("REPEAT")) toggleRepeatMode();
            }
        });
    }

    private void play() {
        if (mediaPlayer == null) return;
        mediaPlayer.start();
        isPlaying = true;
        updatePlaybackState();
        startForegroundWithNotification();
    }

    private void pause() {
        if (mediaPlayer == null) return;
        mediaPlayer.pause();
        isPlaying = false;
        updatePlaybackState();
        startForegroundWithNotification();
    }

    private void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            isPlaying = false;
            updatePlaybackState();
            stopForeground(true);
        }
    }

    private void skipToNext() {
        // Add your actual song skip logic here (can connect to playlist)
        Log.d(TAG, "Next song triggered");
    }

    private void skipToPrevious() {
        Log.d(TAG, "Previous song triggered");
    }

    private void toggleShuffle() {
        isShuffle = !isShuffle;
        Log.d(TAG, "Shuffle: " + isShuffle);
        startForegroundWithNotification();
    }

    private void toggleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        String mode = repeatMode == 0 ? "Off" : repeatMode == 1 ? "All" : "One";
        Log.d(TAG, "Repeat Mode: " + mode);
        startForegroundWithNotification();
    }

    private void updatePlaybackState() {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long position = (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0;
        long duration = (mediaPlayer != null) ? mediaPlayer.getDuration() : 0;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, position, 1.0f);

        if (duration > 0)
            stateBuilder.setBufferedPosition(duration);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    public void updateMetadata(String songName) {
        this.currentSongName = songName;

        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, songName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "D-TECH MUSIC")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, logo);

        mediaSession.setMetadata(metadataBuilder.build());
        startForegroundWithNotification();
    }

    private void startForegroundWithNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action shuffleAction = new NotificationCompat.Action(
                android.R.drawable.ic_menu_rotate, "Shuffle",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, "SHUFFLE")
        );

        NotificationCompat.Action repeatAction = new NotificationCompat.Action(
                android.R.drawable.ic_menu_revert, "Repeat",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, "REPEAT")
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentSongName.isEmpty() ? "D-TECH MUSIC" : currentSongName)
                .setContentText(isShuffle ? "Shuffle On" : "Shuffle Off")
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(android.R.drawable.ic_media_previous, "Previous",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_PLAY_PAUSE))
                .addAction(android.R.drawable.ic_media_next, "Next",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
                .addAction(shuffleAction)
                .addAction(repeatAction)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setProgress(100,
                        mediaPlayer != null && mediaPlayer.getDuration() > 0
                                ? (int) ((float) mediaPlayer.getCurrentPosition() / mediaPlayer.getDuration() * 100)
                                : 0,
                        false)
                .build();

        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "D-TECH MUSIC PLAYER",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        progressHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "MusicService destroyed");
    }
}