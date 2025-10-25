package com.yrum.ppmyvr;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private final IBinder binder = new LocalBinder();
    
    private boolean isPlaying = false;
    private String currentSongName = "";
    private String currentSongUri = "";

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
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

    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        this.currentSongName = songName;
        this.currentSongUri = songUri;
        Log.d(TAG, "Playback state updated - Playing: " + playing + ", Song: " + songName);
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
        Log.d(TAG, "MusicService destroyed");
    }
}