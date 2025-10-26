package com.yrum.ppmyvr;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private final IBinder binder = new LocalBinder();

    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = off, 1 = all, 2 = one

    private MediaSessionCompat mediaSession;
    private MediaPlayer player;

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeMediaSession();
        initializePlayer();
    }

    private void initializePlayer() {
        player = new MediaPlayer();
        player.setOnCompletionListener(mp -> {
            Log.d(TAG, "Song completed");
            if (repeatMode == 2) {
                startPlayback(); // repeat same song
            } else if (repeatMode == 1) {
                sendBroadcast(new Intent("NEXT")); // repeat all
            } else {
                updatePlaybackState(false);
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);
        }
        return START_STICKY;
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                              MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                startPlayback();
            }

            @Override
            public void onPause() {
                pausePlayback();
            }

            @Override
            public void onStop() {
                stopPlayback();
            }

            @Override
            public void onSkipToNext() {
                sendBroadcast(new Intent("NEXT"));
            }

            @Override
            public void onSkipToPrevious() {
                sendBroadcast(new Intent("PREVIOUS"));
            }

            @Override
            public void onCustomAction(String action, android.os.Bundle extras) {
                switch (action) {
                    case "SHUFFLE":
                        isShuffle = !isShuffle;
                        Log.d(TAG, "Shuffle toggled: " + isShuffle);
                        break;
                    case "REPEAT":
                        repeatMode = (repeatMode + 1) % 3;
                        Log.d(TAG, "Repeat mode: " + repeatMode);
                        break;
                }
            }
        });

        updatePlaybackState(false);
    }

    // 🔊 Start playback
    public void startPlayback() {
        try {
            if (currentSongUri == null || currentSongUri.isEmpty()) {
                Log.w(TAG, "No song set to play.");
                return;
            }

            if (player == null) initializePlayer();

            if (player.isPlaying()) player.stop();
            player.reset();

            player.setDataSource(currentSongUri);
            player.prepare();
            player.start();

            isPlaying = true;
            updateMetadata();
            updatePlaybackState(true);
            startForegroundNotification();

            Log.d(TAG, "Playback started: " + currentSongName);
        } catch (Exception e) {
            Log.e(TAG, "Error starting playback", e);
        }
    }

    // ⏸ Pause playback
    public void pausePlayback() {
        if (player != null && player.isPlaying()) {
            player.pause();
            isPlaying = false;
            updatePlaybackState(false);
            stopForeground(false);
        }
    }

    // 🛑 Stop playback completely
    public void stopPlayback() {
        if (player != null) {
            player.stop();
            player.reset();
            isPlaying = false;
            updatePlaybackState(false);
            stopForeground(true);
        }
    }

    private void updateMetadata() {
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                currentSongName != null ? currentSongName : "D-TECH Music");
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "D-TECH MUSIC");
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "D-TECH MUSIC");

        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, logo);

        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void updatePlaybackState() {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(state,
                        player != null ? player.getCurrentPosition() : 0,
                        1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        this.currentSongName = songName;
        this.currentSongUri = songUri;
        if (playing) startPlayback();
        else pausePlayback();
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    private void startForegroundNotification() {
        Notification notification = new NotificationCompat.Builder(this, "music_channel")
                .setContentTitle(currentSongName != null ? currentSongName : "D-TECH Music")
                .setContentText("Now playing")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_previous, "Prev",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))
                .addAction(new NotificationCompat.Action(
                        isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_PLAY_PAUSE)))
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_next, "Next",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
                .build();

        startForeground(1, notification);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public String getCurrentSongName() {
        return currentSongName;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        stopForeground(true);
        Log.d(TAG, "MusicService destroyed");
    }
}