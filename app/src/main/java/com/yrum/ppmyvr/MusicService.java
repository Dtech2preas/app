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
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicService extends Service {

    private final IBinder binder = new MusicBinder();
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder stateBuilder;
    private List<String> playlist = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = off, 1 = all, 2 = one

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnCompletionListener(mp -> onTrackEnd());

        mediaSession = new MediaSessionCompat(this, "DTECH_MUSIC_SESSION");
        mediaSession.setActive(true);

        stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                );

        mediaSession.setPlaybackState(stateBuilder.build());
        updateNotification("No Song Playing", false);
    }

    public void setPlaylist(List<String> songs) {
        this.playlist = songs;
    }

    public void playSong(int index) {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size()) return;

        currentIndex = index;
        String songUri = playlist.get(index);

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(songUri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            updatePlaybackState(true, getSongName(songUri), songUri);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onTrackEnd() {
        if (repeatMode == 2) {
            playSong(currentIndex);
            return;
        }

        if (isShuffle) {
            currentIndex = (int) (Math.random() * playlist.size());
        } else {
            currentIndex++;
            if (currentIndex >= playlist.size()) {
                if (repeatMode == 1) currentIndex = 0;
                else {
                    stopSelf();
                    return;
                }
            }
        }

        playSong(currentIndex);
    }

    public void playPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
        } else {
            mediaPlayer.start();
            isPlaying = true;
        }
        updatePlaybackState(isPlaying, getSongName(playlist.get(currentIndex)), playlist.get(currentIndex));
    }

    public void next() {
        currentIndex++;
        if (currentIndex >= playlist.size()) currentIndex = 0;
        playSong(currentIndex);
    }

    public void previous() {
        currentIndex--;
        if (currentIndex < 0) currentIndex = playlist.size() - 1;
        playSong(currentIndex);
    }

    private String getSongName(String uri) {
        String[] parts = uri.split("/");
        return parts[parts.length - 1];
    }

    private void updateNotification(String title, boolean isPlaying) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Default logo as artwork
        Bitmap artwork = BitmapFactory.decodeResource(getResources(), R.drawable.dtech_logo);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "MUSIC_CHANNEL")
                .setContentTitle(title)
                .setContentText(isShuffle ? "Shuffle On" : "D-TECH Music")
                .setLargeIcon(artwork)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_previous, "Previous",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))
                .addAction(new NotificationCompat.Action(
                        isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                        isPlaying ? "Pause" : "Play",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_PLAY_PAUSE)))
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_next, "Next",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_shuffle, "Shuffle",
                        getShuffleIntent()))
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_repeat, "Repeat",
                        getRepeatIntent()))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(1, 2, 3))
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        startForeground(1, builder.build());
    }

    private PendingIntent getShuffleIntent() {
        Intent shuffleIntent = new Intent(this, MusicService.class);
        shuffleIntent.setAction("SHUFFLE");
        return PendingIntent.getService(this, 1, shuffleIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getRepeatIntent() {
        Intent repeatIntent = new Intent(this, MusicService.class);
        repeatIntent.setAction("REPEAT");
        return PendingIntent.getService(this, 2, repeatIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void handleCustomAction(String action) {
        if ("SHUFFLE".equals(action)) {
            isShuffle = !isShuffle;
        } else if ("REPEAT".equals(action)) {
            repeatMode = (repeatMode + 1) % 3;
        }
        updateNotification(getSongName(playlist.get(currentIndex)), isPlaying);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleCustomAction(intent.getAction());
        }
        return START_STICKY;
    }

    private void updatePlaybackState(boolean playing) {
        this.isPlaying = playing;
        updatePlaybackState();
    }

    private void updatePlaybackState() {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        stateBuilder.setState(state, mediaPlayer.getCurrentPosition(), 1f);
        mediaSession.setPlaybackState(stateBuilder.build());

        String title = playlist.isEmpty() ? "No Song" : getSongName(playlist.get(currentIndex));
        updateNotification(title, isPlaying);
    }

    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        stateBuilder.setState(state, mediaPlayer.getCurrentPosition(), 1f);
        mediaSession.setPlaybackState(stateBuilder.build());
        updateNotification(songName, playing);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        mediaSession.release();
        mediaPlayer.release();
        super.onDestroy();
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
}