package com.yrum.ppmyvr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "dtech_music_channel_v2";
    private static final int NOTIF_ID = 1;

    // Action strings for service intents (notification actions)
    private static final String ACTION_PLAY = "ACTION_PLAY";
    private static final String ACTION_PAUSE = "ACTION_PAUSE";
    private static final String ACTION_TOGGLE_PLAY = "ACTION_TOGGLE_PLAY";
    private static final String ACTION_NEXT = "ACTION_NEXT";
    private static final String ACTION_PREV = "ACTION_PREV";
    private static final String ACTION_SHUFFLE = "SHUFFLE";
    private static final String ACTION_REPEAT = "REPEAT";
    private static final String ACTION_SEEK_TO = "SEEK_TO";

    // Binder so MainActivity's existing cast (MusicService.LocalBinder) works
    public class LocalBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }
    private final IBinder binder = new LocalBinder();

    // Playback
    private MediaPlayer player;
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder stateBuilder;

    // Playlist + state
    private final List<String> playlist = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0=off,1=all,2=one

    // Progress updater
    private final Handler handler = new Handler();
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updatePlaybackState(); // updates media session with current position & notification
            if (isPlaying) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // Create player
        player = new MediaPlayer();
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnCompletionListener(mp -> onTrackEnd());

        // MediaSession
        mediaSession = new MediaSessionCompat(this, "D-TECH-MEDIA-SESSION");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // playback state builder (support seek)
        stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SEEK_TO
                );

        // callback — central place where remote controls/notification buttons route
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                Log.d(TAG, "MediaSession onPlay");
                resumePlayback();
            }

            @Override
            public void onPause() {
                Log.d(TAG, "MediaSession onPause");
                pausePlayback();
            }

            @Override
            public void onStop() {
                Log.d(TAG, "MediaSession onStop");
                stopPlayback();
            }

            @Override
            public void onSkipToNext() {
                Log.d(TAG, "MediaSession onSkipToNext");
                next();
            }

            @Override
            public void onSkipToPrevious() {
                Log.d(TAG, "MediaSession onSkipToPrevious");
                previous();
            }

            @Override
            public void onSeekTo(long pos) {
                Log.d(TAG, "MediaSession onSeekTo: " + pos);
                if (player != null) {
                    player.seekTo((int) pos);
                    updatePlaybackState();
                }
            }
        });

        mediaSession.setActive(true);

        createNotificationChannel();
        startForegroundIfNeeded(false);
        Log.d(TAG, "MusicService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Accept both MediaButtonReceiver intents AND our custom service intents
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand action=" + action);

            // Let MediaButtonReceiver attempt to route the intent to the session (useful for external buttons)
            try {
                MediaButtonReceiver.handleIntent(mediaSession, intent);
            } catch (Exception e) {
                // ignore
            }

            // Handle explicit service actions (these come from notification PendingIntents we create)
            switch (action) {
                case ACTION_PLAY:
                case ACTION_TOGGLE_PLAY:
                    if (!isPlaying) resumePlayback(); else pausePlayback();
                    break;
                case ACTION_PAUSE:
                    pausePlayback();
                    break;
                case ACTION_NEXT:
                    next();
                    break;
                case ACTION_PREV:
                    previous();
                    break;
                case ACTION_SHUFFLE:
                    isShuffle = !isShuffle;
                    updatePlaybackState(isPlaying);
                    break;
                case ACTION_REPEAT:
                    repeatMode = (repeatMode + 1) % 3;
                    updatePlaybackState(isPlaying);
                    break;
                case ACTION_SEEK_TO:
                    // optional: expected to have extra "seek_position" (long)
                    if (intent.hasExtra("seek_position") && player != null) {
                        long pos = intent.getLongExtra("seek_position", 0L);
                        player.seekTo((int) pos);
                        updatePlaybackState();
                    }
                    break;
                default:
                    // also accept direct PlaybackStateCompat constants if present
                    if (PlaybackStateCompat.ACTION_PLAY == action.hashCode()) {
                        // noop - not reliable; kept for compatibility
                    }
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(android.content.Intent intent) {
        return binder;
    }

    // ------------------ Public API used by MainActivity ------------------

    public void setPlaylist(List<String> songs) {
        playlist.clear();
        if (songs != null) playlist.addAll(songs);
        if (!playlist.isEmpty() && currentIndex >= playlist.size()) currentIndex = 0;
        updatePlaybackState(isPlaying);
    }

    public void playSongAt(int index) {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        String uri = playlist.get(currentIndex);
        startPlaybackWithUri(uri, getSongName(uri));
    }

    public void playPauseToggle() {
        if (player == null) return;
        if (player.isPlaying()) pausePlayback();
        else resumePlayback();
    }

    public void next() {
        if (playlist.isEmpty()) return;
        if (isShuffle) {
            currentIndex = (int) (Math.random() * playlist.size());
        } else {
            currentIndex++;
            if (currentIndex >= playlist.size()) {
                if (repeatMode == 1) currentIndex = 0;
                else { stopPlayback(); return; }
            }
        }
        playSongAt(currentIndex);
    }

    public void previous() {
        if (playlist.isEmpty()) return;
        if (isShuffle) {
            currentIndex = (int) (Math.random() * playlist.size());
        } else {
            currentIndex--;
            if (currentIndex < 0) {
                if (repeatMode == 1) currentIndex = playlist.size() - 1;
                else currentIndex = 0;
            }
        }
        playSongAt(currentIndex);
    }

    // Compatibility helpers used by MainActivity (per your logs)
    // boolean-only helper
    private void updatePlaybackState(boolean playing) {
        this.isPlaying = playing;
        updatePlaybackState();
    }

    // no-arg update to refresh MediaSession + notification + progress
    private void updatePlaybackState() {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long pos = (player != null) ? player.getCurrentPosition() : 0L;

        stateBuilder.setState(state, pos, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());

        // schedule/stop progress updates
        handler.removeCallbacks(progressRunnable);
        if (isPlaying) handler.post(progressRunnable);

        // update notification so system media area shows accurate info/progress
        startForegroundIfNeeded(isPlaying);
    }

    // Full signature expected by MainActivity in your previous logs
    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        if (songName != null && !songName.isEmpty()) updateMetadata(songName);
        long pos = (player != null) ? player.getCurrentPosition() : 0L;
        stateBuilder.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                pos, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
        if (isPlaying) handler.post(progressRunnable);
        startForegroundIfNeeded(playing);
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    // ------------------ Internal playback helpers ------------------

    private void startPlaybackWithUri(String uri, String prettyName) {
        try {
            if (player == null) player = new MediaPlayer();
            if (player.isPlaying()) player.stop();
            player.reset();
            player.setDataSource(uri);
            // for local files, synchronous prepare is okay; if remote, change to prepareAsync
            player.prepare();
            player.start();
            isPlaying = true;
            updateMetadata(prettyName);
            updatePlaybackState(true, prettyName, uri);
            Log.d(TAG, "startPlaybackWithUri: " + uri);
        } catch (IOException e) {
            Log.e(TAG, "Error starting playback", e);
        }
    }

    private void resumePlayback() {
        if (player == null) return;
        player.start();
        isPlaying = true;
        updatePlaybackState();
    }

    private void pausePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        isPlaying = false;
        updatePlaybackState();
    }

    private void stopPlayback() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.stop();
            player.reset();
        } catch (Exception ignored) {}
        isPlaying = false;
        updatePlaybackState();
        stopForeground(true);
    }

    private void onTrackEnd() {
        if (repeatMode == 2) { // repeat single
            playSongAt(currentIndex);
            return;
        }
        if (isShuffle) {
            currentIndex = (int) (Math.random() * Math.max(1, playlist.size()));
            playSongAt(currentIndex);
            return;
        }
        currentIndex++;
        if (currentIndex >= playlist.size()) {
            if (repeatMode == 1) currentIndex = 0;
            else { stopPlayback(); return; }
        }
        playSongAt(currentIndex);
    }

    private String getSongName(String uri) {
        if (uri == null) return "D-TECH Music";
        String[] parts = uri.split("/");
        return parts[parts.length - 1];
    }

    private void updateMetadata(String title) {
        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title != null ? title : "D-TECH Music")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "D-TECH MUSIC")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "D-TECH MUSIC")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)); // fallback
        mediaSession.setMetadata(meta.build());
    }

    // ------------------ Notification + foreground (media style) ------------------

    private void startForegroundIfNeeded(boolean playing) {
        createNotificationChannel(); // safe to call repeatedly

        // main intent opens the app
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(playlist.isEmpty() ? "D-TECH Music" : getSongName(playlist.get(currentIndex)))
                .setContentText(isShuffle ? "Shuffle On" : (playing ? "Playing" : "Paused"))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        // Previous action -> SERVICE
        Intent prev = new Intent(this, MusicService.class);
        prev.setAction(ACTION_PREV);
        PendingIntent prevPI = PendingIntent.getService(this, 10, prev,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPI);

        // Play/Pause (toggle) -> SERVICE (also include MediaButtonReceiver fallback)
        Intent toggle = new Intent(this, MusicService.class);
        toggle.setAction(ACTION_TOGGLE_PLAY);
        PendingIntent togglePI = PendingIntent.getService(this, 11, toggle,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction( isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play", togglePI);

        // Next
        Intent next = new Intent(this, MusicService.class);
        next.setAction(ACTION_NEXT);
        PendingIntent nextPI = PendingIntent.getService(this, 12, next,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPI);

        // Shuffle (custom)
        Intent shuffle = new Intent(this, MusicService.class);
        shuffle.setAction(ACTION_SHUFFLE);
        PendingIntent shufflePI = PendingIntent.getService(this, 13, shuffle,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(android.R.drawable.ic_menu_rotate,
                isShuffle ? "Shuffle On" : "Shuffle Off", shufflePI);

        // Repeat (custom) - show textual state in contentText
        Intent repeat = new Intent(this, MusicService.class);
        repeat.setAction(ACTION_REPEAT);
        PendingIntent repeatPI = PendingIntent.getService(this, 14, repeat,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String repeatLabel = repeatMode == 0 ? "Repeat Off" : (repeatMode == 1 ? "Repeat All" : "Repeat One");
        builder.addAction(android.R.drawable.ic_menu_revert, repeatLabel, repeatPI);

        // Progress: if we have a duration, show progress bar
        if (player != null && player.getDuration() > 0) {
            int dur = player.getDuration();
            int pos = player.getCurrentPosition();
            int percent = (int) ((pos / (float) dur) * 100);
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(0, 0, false);
        }

        Notification n = builder.build();
        startForeground(NOTIF_ID, n);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "D-TECH Music Player", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Playback controls");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            if (player != null) {
                try { player.stop(); } catch (Exception ignored) {}
                player.release();
                player = null;
            }
            if (mediaSession != null) {
                mediaSession.release();
                mediaSession = null;
            }
        } catch (Exception ignored) {}
        stopForeground(true);
        super.onDestroy();
    }
}