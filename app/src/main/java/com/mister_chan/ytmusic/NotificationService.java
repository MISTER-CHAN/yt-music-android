package com.mister_chan.ytmusic;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationService extends Service {

    public class NotificationBinder extends android.os.Binder {
        NotificationService getService() {
            return NotificationService.this;
        }

        void startForeground() {
            NotificationService.this.startForeground(1, notification);
        }
    }

    private static final Long MEDIA_SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;

    private static final String CHANNEL = "channel";
    private static final String PAUSE = "Pause";
    private static final String PLAY = "Play";

    private final IBinder binder = new NotificationBinder();
    private Notification notification;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public NotificationService() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        stopService(new Intent(this, NotificationService.class));
    }

    void sendNotification(MainActivity ma, String title) {
        int playbackState;
        float playbackSpeed;
        Intent intent = new Intent();
        NotificationCompat.Action playPauseAction;
        if (ma.playerState == MainActivity.PLAYER_STATE_PLAYING) {
            playbackState = PlaybackStateCompat.STATE_PLAYING;
            playbackSpeed = 1.0f;
            intent.setAction(MainActivity.ACTION_PAUSE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(ma, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_pause, PAUSE, pendingIntent).build();
        } else {
            playbackState = PlaybackStateCompat.STATE_PAUSED;
            playbackSpeed = 0.0f;
            intent.setAction(MainActivity.ACTION_PLAY);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(ma, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_play, PLAY, pendingIntent).build();
        }
        ma.mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, ma.duration * 1000L)
                .build()
        );
        ma.mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(playbackState, (long) ma.lastPosition * 1000L, playbackSpeed)
                .setActions(MEDIA_SESSION_ACTIONS)
                .build()
        );
        notification = new NotificationCompat.Builder(this, CHANNEL)
                .addAction(playPauseAction)
                .addAction(ma.nextAction)
                .addAction(ma.lyricsAction)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(ma.mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        NotificationManagerCompat.from(this).notify(1, notification);
    }

    void sendScreenNotification(MainActivity ma, String title) {
        int playbackState;
        float playbackSpeed;
        Intent intent = new Intent();
        NotificationCompat.Action playPauseAction;
        if (ma.playerState == MainActivity.PLAYER_STATE_PLAYING) {
            playbackState = PlaybackStateCompat.STATE_PLAYING;
            playbackSpeed = 1.0f;
            intent.setAction(MainActivity.ACTION_PAUSE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_pause, PAUSE, pendingIntent).build();
        } else {
            playbackState = PlaybackStateCompat.STATE_PAUSED;
            playbackSpeed = 0.0f;
            intent.setAction(MainActivity.ACTION_PLAY);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_play, PLAY, pendingIntent).build();
        }
        ma.mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(playbackState, (long) ma.lastPosition * 1000L, playbackSpeed)
                .setActions(MEDIA_SESSION_ACTIONS)
                .build()
        );
        notification = new NotificationCompat.Builder(this, CHANNEL)
                .addAction(playPauseAction)
                .addAction(ma.nextAction)
                .setContentText(ma.lyricsLinePure)
                .setContentTitle(title)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        NotificationManagerCompat.from(this).notify(1, notification);
    }
}
