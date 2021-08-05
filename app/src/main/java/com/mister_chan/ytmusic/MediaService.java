package com.mister_chan.ytmusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Timer;

public class MediaService extends Service {

    static final String PLAYER = "document.getElementById(\"movie_player\")";

    private static MediaService mInstance = null;
    Notification notification;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    MediaService(Notification notification) {
        super();
        this.notification = notification;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mInstance = null;
        stopForeground(true);
        stopService(new Intent(this, MediaService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mInstance == null) {
            mInstance = this;
        }
        startForeground(1, notification);
        return super.onStartCommand(intent, flags, startId);
    }
}
