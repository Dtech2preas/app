package com.yrum.ppmyvr;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private final IBinder binder = new LocalBinder();
    
    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";
    
    // Media session for Spotify-like notifications
    private MediaSession mediaSession;
    
    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeMediaSession();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Return STICKY to keep service running even if app is killed
        return START_STICKY;
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSession(this, "D-TECH MUSIC");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | 
                            MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        // Set playback state
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | 
                          PlaybackState.ACTION_PAUSE |
                          PlaybackState.ACTION_PLAY_PAUSE |
                          PlaybackState.ACTION_SKIP_TO_NEXT |
                          PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                          PlaybackState.ACTION_STOP);
        
        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setActive(true);
        
        // Set media session callback
        mediaSession.setCallback(new MediaSession.Callback() {
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
            }
        });
    }

    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        this.currentSongName = songName;
        this.currentSongUri = songUri;
        
        // Update media session state
        if (mediaSession != null) {
            int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | 
                              PlaybackState.ACTION_PAUSE |
                              PlaybackState.ACTION_PLAY_PAUSE |
                              PlaybackState.ACTION_SKIP_TO_NEXT |
                              PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                              PlaybackState.ACTION_STOP)
                    .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            
            mediaSession.setPlaybackState(stateBuilder.build());
            
            // Update metadata
            MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
            if (songName != null && !songName.isEmpty()) {
                metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, songName);
                metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, "D-TECH MUSIC");
                metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, "D-TECH MUSIC");
                
                // Create a simple bitmap (you can replace this with actual album art)
                Bitmap icon = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play);
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, icon);
            }
            
            mediaSession.setMetadata(metadataBuilder.build());
        }
        
        Log.d(TAG, "Playback state updated - Playing: " + playing + ", Song: " + songName);
    }

    public MediaSession getMediaSession() {
        return mediaSession;
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
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        Log.d(TAG, "MusicService destroyed");
    }
}