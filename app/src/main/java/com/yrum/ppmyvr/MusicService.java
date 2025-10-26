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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

public class MusicService extends Service {

    private static final String CHANNEL_ID = "music_channel";
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private boolean isPlaying = false;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = no repeat, 1 = repeat all, 2 = repeat one

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mediaSession = new MediaSessionCompat(this, "DTECHMusicSession");

        // Dummy player (replace with your actual player)
        mediaPlayer = MediaPlayer.create(this, R.raw.sample_music);
        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(mp -> {
                if (repeatMode == 2) {
                    mp.seekTo(0);
                    mp.start();
                } else if (repeatMode == 1) {
                    // Loop playlist logic can go here
                } else {
                    stopForeground(false);
                }
            });
        }

        showNotification();
    }

    private void showNotification() {
        Bitmap artwork = BitmapFactory.decodeResource(getResources(), R.drawable.dtech_logo);

        Intent playIntent = new Intent(this, MusicService.class).setAction("PLAY_PAUSE");
        PendingIntent playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent prevIntent = new Intent(this, MusicService.class).setAction("PREVIOUS");
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent nextIntent = new Intent(this, MusicService.class).setAction("NEXT");
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent shuffleIntent = new Intent(this, MusicService.class).setAction("SHUFFLE");
        PendingIntent shufflePendingIntent = PendingIntent.getService(this, 0, shuffleIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent repeatIntent = new Intent(this, MusicService.class).setAction("REPEAT");
        PendingIntent repeatPendingIntent = PendingIntent.getService(this, 0, repeatIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DTECH Music Player")
                .setContentText(isPlaying ? "Now Playing" : "Paused")
                .setLargeIcon(artwork)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(new NotificationCompat.Action(IconCompat.createWithResource(this, android.R.drawable.ic_media_previous),
                        "Previous", prevPendingIntent))
                .addAction(new NotificationCompat.Action(IconCompat.createWithResource(this, isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play),
                        isPlaying ? "Pause" : "Play", playPendingIntent))
                .addAction(new NotificationCompat.Action(IconCompat.createWithResource(this, android.R.drawable.ic_media_next),
                        "Next", nextPendingIntent))
                .addAction(new NotificationCompat.Action(IconCompat.createWithResource(this, android.R.drawable.ic_menu_sort_by_size),
                        "Shuffle", shufflePendingIntent))
                .addAction(new NotificationCompat.Action(IconCompat.createWithResource(this, android.R.drawable.ic_menu_revert),
                        "Repeat", repeatPendingIntent))
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        startForeground(1, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_STICKY;

        switch (intent.getAction()) {
            case "PLAY_PAUSE":
                togglePlayPause();
                break;
            case "NEXT":
                Log.d("MusicService", "Next pressed");
                break;
            case "PREVIOUS":
                Log.d("MusicService", "Previous pressed");
                break;
            case "SHUFFLE":
                isShuffle = !isShuffle;
                showNotification();
                break;
            case "REPEAT":
                repeatMode = (repeatMode + 1) % 3;
                showNotification();
                break;
        }

        return START_STICKY;
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
        } else {
            mediaPlayer.start();
            isPlaying = true;
        }
        showNotification();
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaSession.release();
    }
}