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
    private static final String CHANNEL_ID = "dtech_music_channel";

    // Binder (kept as LocalBinder so MainActivity's existing cast works)
    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
    private final IBinder binder = new LocalBinder();

    // Playback & session
    private MediaPlayer player;
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder stateBuilder;

    // Playlist state
    private final List<String> playlist = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = off, 1 = all, 2 = one

    @Override
    public void onCreate() {
        super.onCreate();

        // MediaPlayer
        player = new MediaPlayer();
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnCompletionListener(mp -> onTrackEnd());

        // MediaSession + PlaybackState builder
        mediaSession = new MediaSessionCompat(this, "D-TECH MUSIC");
        mediaSession.setActive(true);
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
        mediaSession.setPlaybackState(stateBuilder.build());

        createNotificationChannel();
        // Start minimal foreground (low priority) so the system associates the session with our service.
        startForegroundIfNeeded(false);

        Log.d(TAG, "MusicService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Forward media button intents to the MediaSession
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);

            // handle custom actions via service PendingIntents (SHUFFLE / REPEAT)
            String action = intent.getAction();
            if ("SHUFFLE".equals(action)) {
                isShuffle = !isShuffle;
                updatePlaybackState(isPlaying);
            } else if ("REPEAT".equals(action)) {
                repeatMode = (repeatMode + 1) % 3;
                updatePlaybackState(isPlaying);
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ----------------- Public API (call from MainActivity) -----------------

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
            currentIndex = (int)(Math.random() * playlist.size());
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
            currentIndex = (int)(Math.random() * playlist.size());
        } else {
            currentIndex--;
            if (currentIndex < 0) {
                if (repeatMode == 1) currentIndex = playlist.size() - 1;
                else currentIndex = 0;
            }
        }
        playSongAt(currentIndex);
    }

    // Keep compatibility with existing MainActivity calls:
    // boolean-only helper (many call sites in your logs used updatePlaybackState(false);)
    private void updatePlaybackState(boolean playing) {
        this.isPlaying = playing;
        updatePlaybackState();
    }

    // no-arg update (reads current state)
    private void updatePlaybackState() {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long position = (player != null) ? player.getCurrentPosition() : 0;
        stateBuilder.setState(state, position, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());

        // Refresh notification display
        String title = playlist.isEmpty() ? "D-TECH Music" : getSongName(playlist.get(currentIndex));
        startForegroundIfNeeded(isPlaying);
    }

    // full signature kept for direct MainActivity compatibility
    public void updatePlaybackState(boolean playing, String songName, String songUri) {
        this.isPlaying = playing;
        // update metadata if provided
        if (songName != null && !songName.isEmpty()) updateMetadata(songName);
        long pos = (player != null) ? player.getCurrentPosition() : 0;
        stateBuilder.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                pos, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
        startForegroundIfNeeded(playing);
    }

    // Expose MediaSession for MainActivity (it called getMediaSession())
    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    // ----------------- Internal playback implementation -----------------

    private void startPlaybackWithUri(String uri, String prettyName) {
        try {
            if (player == null) player = new MediaPlayer();
            if (player.isPlaying()) player.stop();
            player.reset();
            player.setDataSource(uri);
            player.prepare(); // synchronous; acceptable for local files - adapt to prepareAsync if needed
            player.start();
            isPlaying = true;
            updateMetadata(prettyName);
            updatePlaybackState(true, prettyName, uri);
            startForegroundIfNeeded(true);
            Log.d(TAG, "Started playback: " + prettyName + " -> " + uri);
        } catch (IOException e) {
            Log.e(TAG, "Error playing uri: " + uri, e);
        }
    }

    private void resumePlayback() {
        if (player == null) return;
        player.start();
        isPlaying = true;
        updatePlaybackState(isPlaying);
    }

    private void pausePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        isPlaying = false;
        updatePlaybackState(isPlaying);
    }

    private void stopPlayback() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.stop();
            player.reset();
        } catch (Exception ignored) {}
        isPlaying = false;
        updatePlaybackState(isPlaying);
        stopForeground(true);
    }

    private void onTrackEnd() {
        if (repeatMode == 2) {
            // repeat same song
            playSongAt(currentIndex);
            return;
        }

        if (isShuffle) {
            currentIndex = (int)(Math.random() * Math.max(1, playlist.size()));
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
                        BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)); // fallback logo
        mediaSession.setMetadata(meta.build());
    }

    // ----------------- Notification & Foreground logic -----------------

    // Build and start foreground notification that integrates with system media panel (swipe-down)
    private void startForegroundIfNeeded(boolean playing) {
        // create base intent to open the app
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(playlist.isEmpty() ? "D-TECH Music" : getSongName(playlist.get(currentIndex)))
                .setContentText(isShuffle ? "Shuffle On" : (playing ? "Playing" : "Paused"))
                .setSmallIcon(R.mipmap.ic_launcher) // use launcher so drawable exists
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)); // shows prev/play/next compact

        // Prev
        builder.addAction(android.R.drawable.ic_media_previous, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        // Play / Pause
        builder.addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE));
        // Next
        builder.addAction(android.R.drawable.ic_media_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        // Shuffle custom action --> send intent to this service
        Intent shuffleIntent = new Intent(this, MusicService.class);
        shuffleIntent.setAction("SHUFFLE");
        PendingIntent shufflePending = PendingIntent.getService(this, 1, shuffleIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(android.R.drawable.ic_menu_rotate, "Shuffle", shufflePending);

        // Repeat custom action
        Intent repeatIntent = new Intent(this, MusicService.class);
        repeatIntent.setAction("REPEAT");
        PendingIntent repeatPending = PendingIntent.getService(this, 2, repeatIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(android.R.drawable.ic_menu_revert, "Repeat", repeatPending);

        Notification notification = builder.build();

        // start foreground with id 1
        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "D-TECH Music Player";
            String description = "Media playback controls";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
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