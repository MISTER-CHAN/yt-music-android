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

    private final IBinder binder = new NotificationBinder();
    private Notification notification;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public NotificationService() {}

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        stopService(new Intent(this, NotificationService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    void sendNotification(MainActivity ma) {
        Intent intent = new Intent();
        NotificationCompat.Action playPauseAction;
        if (ma.playerState == 1) {
            intent.setAction("com.mister_chan.ytmusic.pause");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(ma, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_pause, "Pause", pendingIntent).build();
        } else {
            intent.setAction("com.mister_chan.ytmusic.play");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(ma, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_play, "Play", pendingIntent).build();
        }
        ma.mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, ma.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, ma.duration * 1000)
                .build()
        );
        ma.mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1)
                .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                .build()
        );
        notification = new NotificationCompat.Builder(this, "channel")
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
}
