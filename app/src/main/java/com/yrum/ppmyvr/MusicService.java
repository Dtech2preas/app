package com.yrum.ppmyvr;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private final IBinder binder = new LocalBinder();

    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

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
            updatePlaybackState(false);
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                              MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);

        // Setup callback for controls from notification / lockscreen / Bluetooth
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                Log.d(TAG, "onPlay called from system controls");
                startPlayback();
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.d(TAG, "onPause called from system controls");
                pausePlayback();
            }

            @Override
            public void onStop() {
                super.onStop();
                Log.d(TAG, "onStop called from system controls");
                stopPlayback();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.d(TAG, "Next pressed");
                sendBroadcast(new Intent("NEXT"));
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.d(TAG, "Previous pressed");
                sendBroadcast(new Intent("PREVIOUS"));
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
        }
    }

    // 🛑 Stop playback completely
    public void stopPlayback() {
        if (player != null) {
            player.stop();
            player.reset();
            isPlaying = false;
            updatePlaybackState(false);
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

    private void updatePlaybackState(boolean playing) {
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
    }

    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.currentSongName = songName;
        this.currentSongUri = songUri;
        if (playing) startPlayback();
        else pausePlayback();
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
        Log.d(TAG, "MusicService destroyed");
    }
}