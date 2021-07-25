package com.mister_chan.ytmusic;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends AppCompatActivity {

    public class MediaWebView extends WebView {

        public MediaWebView(Context context) {
            super(context);
        }

        public MediaWebView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MediaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            if (visibility != View.GONE)
                super.onWindowVisibilityChanged(View.VISIBLE);
        }
    }

    private String title = "";
    private String[] lyrics;
    private TextView tvLyrics, tvTitle;
    private WebSettings ws;
    private WebView wv;
    private WindowManager windowManager;
    private WindowManager.LayoutParams wmlp;

    @SuppressLint("RemoteViewLayout")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wv = new MediaWebView(this);
        ((androidx.constraintlayout.widget.ConstraintLayout)findViewById(R.id.cl)).addView(wv);
        ViewGroup.LayoutParams lp = wv.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        wv.setLayoutParams(lp);

        ws = wv.getSettings();

        ws.setJavaScriptEnabled(true);
        ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
        ws.setBlockNetworkImage(true);
        ws.setAppCacheEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                Matcher m = Pattern.compile("(?<=watch\\?v=)\\w+").matcher(url);
                if (m.find()) {
                    Log.d("Lyrics", m.group());
                    readLyrics(m.group());
                }
                super.onLoadResource(view, url);
            }
        });
        wv.loadUrl("https://www.youtube.com");

        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        wmlp = new WindowManager.LayoutParams();
        wmlp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        wmlp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wmlp.format = PixelFormat.RGBA_8888;
        wmlp.gravity = Gravity.CENTER_VERTICAL | Gravity.TOP;
        wmlp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        tvLyrics = new TextView(this);
        tvLyrics.setPadding(0, 0x100, 0, 0);
        tvLyrics.setText("YouTube Music");
        tvLyrics.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvLyrics.setTextColor(0xffff0000);
        tvLyrics.setTextSize(30);
        tvLyrics.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
        windowManager.addView(tvLyrics, wmlp);

        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel nc = new NotificationChannel("channel", "Channel", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(nc);
        sendNotification("YouTube Music");

        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wv.evaluateJavascript("document.getElementById(\"movie_player\").getCurrentTime()", new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    try {
                                        int centisec = (int)(Float.parseFloat(value) * 100) * 20 / 1000 * 1000 / 20;
                                        String lrc = lyrics[centisec];
                                        if (lrc != null)
                                            tvLyrics.setText(lrc);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            wv.evaluateJavascript("document.getElementsByClassName(\"slim-video-metadata-title\")[0].textContent", new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    Log.d("Title", value);
                                    if (value.length() > 4)
                                        sendNotification(value);
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        };
        timer.schedule(timerTask, 1000, 500);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (wv.canGoBack()) {
                wv.goBack();
            } else {
                moveTaskToBack(false);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void readLyrics(String v) {
        lyrics = new String[0x20000];
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File("/sdcard/YTMusic/" + v + ".lrc")
            ),"UTF-8"));
            String line = "";
            while ((line = br.readLine()) != null) {
                Matcher m = Pattern.compile(
                    "\\[(?<min>\\d{2}):(?<sec>\\d{2})\\.(?<centisec>\\d{2})\\](?:\\[\\d{2}:\\d{2}\\.\\d{2}\\])*(?<lrc>[^\\[\\]]+)$"
                ).matcher(line);
                for (int i = 0; m.find(i); i += 10) {
                    lyrics[
                        Integer.parseInt(m.group("min")) * 6000 +
                        Integer.parseInt(m.group("sec")) * 100 +
                        Integer.parseInt(m.group("centisec"))
                    ] = m.group("lrc");
                }
            }
            br.close();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendNotification(String title) {
        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification);
        rv.setTextViewText(R.id.tv_title, title);
        Notification n = new Notification.Builder(this, "channel")
            .setCustomContentView(rv)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build();
        n.flags |= Notification.FLAG_NO_CLEAR;
        NotificationManagerCompat.from(this).notify(0, n);
    }
}