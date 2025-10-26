package com.yrum.ppmyvr;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    
    // Media session for Spotify-like notifications
    private MediaSessionCompat mediaSession;
    
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
        mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | 
                            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        // Set playback state
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | 
                          PlaybackStateCompat.ACTION_PAUSE |
                          PlaybackStateCompat.ACTION_PLAY_PAUSE |
                          PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                          PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                          PlaybackStateCompat.ACTION_STOP);
        
        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setActive(true);
        
        // Set media session callback
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
            }
        });
    }

    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        this.currentSongName = songName;
        this.currentSongUri = songUri;
        
        // Update media session state
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
            
            // Update metadata
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
            if (songName != null && !songName.isEmpty()) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, songName);
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "D-TECH MUSIC");
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "D-TECH MUSIC");
                
                // Create a simple bitmap (you can replace this with actual album art)
                Bitmap icon = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play);
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon);
            }
            
            mediaSession.setMetadata(metadataBuilder.build());
        }
        
        Log.d(TAG, "Playback state updated - Playing: " + playing + ", Song: " + songName);
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