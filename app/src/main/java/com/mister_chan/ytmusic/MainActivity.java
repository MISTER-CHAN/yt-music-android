package com.mister_chan.ytmusic;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private class MediaWebView extends WebView {

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
            if (visibility != View.GONE) {
                super.onWindowVisibilityChanged(View.VISIBLE);
            }
        }
    }

    private class LyricsTimerTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    player.evaluateJavascript("document.getElementById(\"movie_player\").getCurrentTime()", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (isNumeric(value)) {
                                float f = Float.parseFloat(value);
                                if (f > 0) {
                                    int centisec = (int) (f * 100) * 20 / 1000 * 1000 / 20;
                                    String lrc = lyrics[centisec];
                                    if (lrc != null) {
                                        tvLyrics.setText(lrc);
                                    }
                                    if (shouldGetPlaylist) {
                                        llShuffle.setVisibility(View.VISIBLE);
                                        player.evaluateJavascript(
                                                "  var elements = document.getElementsByClassName(\"compact-media-item-metadata-content\"), value = \"\";" +
                                                        "for (var i = 0; i < elements.length; i++) {" +
                                                        "    var href = elements[i].href, r = /(?<=v=)[-0-9A-Z_a-z]+?/;" +
                                                        "    if (r.test(href)) {" +
                                                        "        if (href.match(r)[0] != \"" + nowPlaying + "\") {" +
                                                        "            value += \",\" + href" +
                                                        "        }" +
                                                        "    }" +
                                                        "}" +
                                                        "value.slice(1)",
                                                new ValueCallback<String>() {
                                                    public void onReceiveValue(String value) {
                                                        String[] hrefs = value.split(",");
                                                        playlistVideos.addAll(Arrays.asList(hrefs));
                                                        if (playlistVideos.size() > 1) {
                                                            shouldGetPlaylist = false;
                                                            isShuffled = false;
                                                        }
                                                    }
                                                });
                                    }
                                    if (shouldSkipBeginning) {
                                        if (f < beginningDuration) {
                                            player.evaluateJavascript(PLAYER + ".seekTo(" + beginningDuration + ")", new ValueCallback<String>() {
                                                @Override
                                                public void onReceiveValue(String value) {
                                                }
                                            });
                                        } else {
                                            shouldSkipBeginning = false;
                                        }
                                    } else {
                                        beginningDuration = f;
                                    }
                                    if (endingDuration > 0) {
                                        if (f >= endingDuration) {
                                            player.evaluateJavascript("var player = " + PLAYER + "; player.seekTo(player.getDuration())", new ValueCallback<String>() {
                                                @Override
                                                public void onReceiveValue(String value) {
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            });
        }
    }

    private class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case "com.mister_chan.ytmusic.play":
                    player.loadUrl("javascript:" + PLAYER + ".playVideo()");
                    break;
                case "com.mister_chan.ytmusic.pause":
                    player.loadUrl("javascript:" + PLAYER + ".pauseVideo()");
                    break;
                case "com.mister_chan.ytmusic.next":
                    player.loadUrl("javascript:" + PLAYER + ".seekTo(" + PLAYER + ".getDuration())");
                    break;
                case "com.mister_chan.ytmusic.lyrics":
                    int visibility = tvLyrics.getVisibility();
                    switch (visibility) {
                        case View.VISIBLE:
                            timer.cancel();
                            tvLyrics.setVisibility(View.GONE);
                            break;
                        case View.GONE:
                            timer = new Timer();
                            timer.schedule(new LyricsTimerTask(), 1000, 100);
                            tvLyrics.setVisibility(View.VISIBLE);
                            break;
                    }
                    break;
            }
        }
    }

    private static class Skipping {
        public String v = "";
        public float when;

        public Skipping(String v, float when) {
            this.v = v;
            this.when = when;
        }
    }

    private static final String HOME = "https://m.youtube.com", PLAYER = "document.getElementById(\"movie_player\")";
    private boolean isPlaying = false, isShuffled = false, shouldGetPlaylist = false, shouldSkipBeginning = false;
    private Button bPlayPause, bReload;
    private float beginningDuration = 0, endingDuration = 0;
    private int playerState = 0;
    private LinearLayout llWebView, llShuffle, llTitle;
    private List<String> playlistVideos = new ArrayList<>();
    private NotificationCompat.Action lyricsAction, nextAction;
    private RemoteViews rv;
    private final Skipping[] skippingBeginnings = new Skipping[] {
            new Skipping("l2nRYRiEY6Y", 4),
            new Skipping("wrczjnLqfZk", 28)
    };
    private final Skipping[] skippingEndings = new Skipping[] {
            new Skipping("49tpIMDy9BE", 291)
    };
    private String nowPlaying = "", playlist = "", title = "";
    private String[] lyrics;
    private TextView tvLyrics, tvTitle;
    private Timer timer;
    private TimerTask timerTask;
    private PowerManager.WakeLock wakeLock;
    private WebView player, webView;
    private WindowManager windowManager;

    private boolean isNumeric(String s) {
        return Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s).find();
    }

    private String isVideo(String url) {
        Matcher m = Pattern.compile("(?<=watch\\?v=)[-0-9A-Z_a-z]+").matcher(url);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initialWebView(WebView wv) {
        llWebView.addView(wv);
        ViewGroup.LayoutParams lp = wv.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        wv.setLayoutParams(lp);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
        ws.setBlockNetworkImage(true);
        ws.setAppCacheEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        ws.setSupportMultipleWindows(true);
    }

    @SuppressLint({"RemoteViewLayout", "JavascriptInterface", "InvalidWakeLockTag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bPlayPause = findViewById(R.id.b_play_pause);
        bReload = findViewById(R.id.b_reload);
        llShuffle = findViewById(R.id.ll_shuffle);
        llTitle = findViewById(R.id.ll_title);
        llWebView = findViewById(R.id.ll_webview);
        tvTitle = findViewById(R.id.tv_title);

        bPlayPause.setTypeface(Typeface.createFromAsset(getAssets(), "Player.ttf"));
        bPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.evaluateJavascript("var player = " + PLAYER + "; if (player.getPlayerState() == 1) player.pauseVideo(); else player.playVideo()", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                    }
                });
            }
        });
        bReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bReload.setVisibility(View.GONE);
                player.reload();
            }
        });
        findViewById(R.id.tv_shuffle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                llShuffle.setVisibility(View.GONE);
                Collections.shuffle(playlistVideos);
                isShuffled = true;
            }
        });
        findViewById(R.id.tv_dont_shuffle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                llShuffle.setVisibility(View.GONE);
            }
        });
        llTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    webView.setVisibility(View.GONE);
                    player.setVisibility(View.VISIBLE);
                }
            }
        });

        webView = new MediaWebView(this);
        player = new MediaWebView(this);
        initialWebView(webView);
        initialWebView(player);
        player.addJavascriptInterface(this, "MainActivity");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                String v = isVideo(url);
                if (v != null) {
                    view.goBack();
                    readLyrics(v);
                    player.loadUrl(url.replace("&pbj=1", ""));
                    nowPlaying = v;
                    webView.setVisibility(View.GONE);
                    player.setVisibility(View.VISIBLE);
                    isPlaying = true;
                }
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.getSettings().setBlockNetworkImage(false);
                view.loadUrl("javascript:(function() {" +
                        "var parent = document.getElementsByTagName('head')[0];" +
                        "var style = document.createElement('style');" +
                        "var body = document.getElementsByTagName('body')[0];" +
                        "style.type = 'text/css';" +
                        "style.innerHTML = \"@font-face{font-family:MISTER_CHAN;src:url('file:///storage/emulated/0/Fonts/MISTER_CHAN_CJK.ttf');}*{font-family:MISTER_CHAN !important}\";" +
                        "parent.appendChild(style);" +
                        "body.style.fontFamily = \"MISTER_CHAN\"" +
                        "})()");
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                view.getSettings().setBlockNetworkImage(true);
                super.onPageStarted(view, url, favicon);
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().contains("MISTER_CHAN")) {
                    try {
                        InputStream is = new FileInputStream(
                                "/storage/emulated/0/Fonts/MISTER_CHAN_CJK.ttf"
                        );
                        return new WebResourceResponse("application/x-font-ttf", "UTF-8", is);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        player.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.getSettings().setBlockNetworkImage(false);
                player.evaluateJavascript(PLAYER + ".addEventListener(\"onStateChange\", function (data) { MainActivity.onStateChange(data) })", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                    }
                });
                if (!shouldSkipBeginning) {
                    for (Skipping s : skippingBeginnings) {
                        if (nowPlaying.equals(s.v)) {
                            beginningDuration = s.when;
                            shouldSkipBeginning = true;
                        }
                    }
                }
                endingDuration = 0;
                for (Skipping s : skippingEndings) {
                    if (nowPlaying.equals(s.v)) {
                        endingDuration = s.when;
                    }
                }
                Matcher matcherList = Pattern.compile("(?<=list=)[-0-9A-Z_a-z]+").matcher(url);
                if (matcherList.find()) {
                    String group = matcherList.group();
                    if (!group.equals(playlist)) {
                        playlist = group;
                        playlistVideos = new ArrayList<>();
                        shouldGetPlaylist = true;
                    }
                } else {
                    llShuffle.setVisibility(View.GONE);
                    playlistVideos = new ArrayList<>();
                    playlist = "";
                }
                player.loadUrl("javascript:" + PLAYER + ".unMute()");
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                view.getSettings().setBlockNetworkImage(true);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
             // bReload.setVisibility(View.VISIBLE);
                super.onReceivedError(view, request, error);
            }
        });
        player.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                String url = view.getUrl();
                String v = isVideo(url);
                if (v == null) {
                    view.goBack();
                    webView.loadUrl(url);
                    shouldSkipBeginning = true;
                    player.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                } else if (!v.equals(nowPlaying)) {
                    readLyrics(v);
                    nowPlaying = v;
                    isPlaying = true;
                }
                title = title.replace(" - YouTube", "");
                tvTitle.setText(title);
                sendNotification(title);
                bReload.setVisibility(title.equals("網頁無法使用") ? View.VISIBLE : View.GONE);
                super.onReceivedTitle(view, title);
            }
        });
        player.setVisibility(View.GONE);
        webView.loadUrl(HOME);

        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams();
        wmlp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        wmlp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        wmlp.format = PixelFormat.RGBA_8888;
        wmlp.gravity = Gravity.CENTER_VERTICAL | Gravity.TOP;
        wmlp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        tvLyrics = new TextView(this);
        tvLyrics.setMaxLines(1);
        tvLyrics.setPadding(0, 0x100, 0, 0);
        tvLyrics.setText("YouTube Music");
        tvLyrics.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvLyrics.setTextColor(Color.RED);
        tvLyrics.setTextSize(24);
        tvLyrics.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
        windowManager.addView(tvLyrics, wmlp);

        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel nc = new NotificationChannel("channel", "Channel", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(nc);
        /*
         * rv = new RemoteViews(getPackageName(), R.layout.notification);
         * rv.setTextViewText(R.id.tv_title, "YouTube Music");
         */
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.mister_chan.ytmusic.lyrics");
        intentFilter.addAction("com.mister_chan.ytmusic.next");
        intentFilter.addAction("com.mister_chan.ytmusic.play");
        intentFilter.addAction("com.mister_chan.ytmusic.pause");
        intentFilter.addAction("com.mister_chan.ytmusic.lyrics");
        Intent intent = new Intent();
        intent.setAction("com.mister_chan.ytmusic.lyrics");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        lyricsAction = new NotificationCompat.Action.Builder(R.drawable.ic_lyrics, "Lyrics", pendingIntent).build();
        intent = new Intent();
        intent.setAction("com.mister_chan.ytmusic.next");
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        nextAction = new NotificationCompat.Action.Builder(R.drawable.ic_next, "Next", pendingIntent).build();
        registerReceiver(new NotificationReceiver(), intentFilter);
        sendNotification("YouTube Music");

        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "WakeLock");
        wakeLock.acquire(7200000);

        timer = new Timer();
        timerTask = new LyricsTimerTask();
        timer.schedule(timerTask, 1000, 100);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.getVisibility() == View.VISIBLE) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    moveTaskToBack(false);
                }
            } else if (player.getVisibility() == View.VISIBLE) {
                player.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @JavascriptInterface
    public void onStateChange(int data) {
        bPlayPause.setText(data == 1 /* playing */ ? "⏸" : "⏵");
        sendNotification(data);
        switch (data) {
            case 0: // ended
                if (playlistVideos.size() > 1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isShuffled) {
                                player.loadUrl(playlistVideos.get(0));
                            }
                            playlistVideos.remove(0);
                        }
                    });
                } else {
                    isPlaying = false;
                }
                break;
            case 1:
                break;
        }
    }

    private void readLyrics(String v) {
        lyrics = new String[0x20000];
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                    "/sdcard/YTMusic/" + v + ".lrc"
            ), StandardCharsets.UTF_8));
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
            tvLyrics.setText("YouTube Music");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendNotification(String contentText) {
        sendNotification(contentText, playerState);
    }

    private void sendNotification(int playerState) {
        sendNotification(title, playerState);
    }

    private void sendNotification(String contentText, int playerState) {
        title = contentText;
        this.playerState = playerState;
        Intent intent = new Intent();
        NotificationCompat.Action playPauseAction;
        if (playerState == 1) {
            intent.setAction("com.mister_chan.ytmusic.pause");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_pause, "Pause", pendingIntent).build();
        } else {
            intent.setAction("com.mister_chan.ytmusic.play");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_play, "Play", pendingIntent).build();
        }
        Notification notification = new NotificationCompat.Builder(this, "channel")
                .addAction(playPauseAction)
                .addAction(nextAction)
                .addAction(lyricsAction)
             // .setCustomContentView(rv)
                .setContentText(contentText)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        NotificationManagerCompat.from(this).notify(0, notification);
    }
}