package com.mister_chan.ytmusic;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private class LyricsTimerTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    player.loadUrl("javascript:" +
                            "var player = " + PLAYER + ";");
                    player.evaluateJavascript("" +
                                    "if (player != null)" +
                                    "    player.getCurrentTime()",
                            new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            // value: current time in seconds
                            if (isNumeric(value)) {
                                float currentTime = Float.parseFloat(value);
                                if (currentTime > 0) {

                                    // Show lyrics
                                    int centisec = (int) (currentTime * 100) * 20 / 1000 * 1000 / 20;
                                    String line = lyrics[centisec];
                                    if (line != null && !lyricsLine.equals(line)) {
                                        lyricsLine = line;
                                        line = stylizeLyrics(line);
                                        line = line.toUpperCase(Locale.ROOT);
                                        stylelessLyricsLine = line;
                                        tvLyrics.setText(line);
                                        if (isScreenOff) {
                                            sendScreenNotification();
                                        }
                                    }

                                    // Should-dos
                                    if (shouldAddOnStateChangeListener) {
                                        shouldAddOnStateChangeListener = false;
                                        onStateChange(PLAYER_STATE_PLAYING);
                                        player.loadUrl("javascript:" +
                                                "player.addEventListener(\"onStateChange\", function (data) {" +
                                                "    mainActivity.onStateChange(data)" +
                                                "})");
                                    }
                                    if (shouldSeekToLastPosition) {
                                        shouldSeekToLastPosition = false;
                                        if (currentTime < lastPosition) {
                                            player.loadUrl("javascript:" +
                                                    "player.seekTo(" + lastPosition + ")");
                                        }
                                    } else {
                                        for (Skipping skipping : skippings) {
                                            if (currentTime >= skipping.from) {
                                                player.loadUrl("javascript:" +
                                                        "(function () {" +
                                                        "    var to = " + skipping.to + ";" +
                                                        "    if (to <= 0) {" +
                                                        "        to = player.getDuration();" +
                                                        "    }" +
                                                        "    player.seekTo(to)" +
                                                        "})()");
                                                break;
                                            }
                                        }
                                    }
                                    if (shouldGetDuration) {
                                        player.evaluateJavascript("player.getDuration()", new ValueCallback<String>() {
                                            @Override
                                            public void onReceiveValue(String value) {
                                                if (!"null".equals(value)) {
                                                    shouldGetDuration = false;
                                                    duration = (long) Float.parseFloat(value);
                                                    if (!isScreenOff) {
                                                        sendNotification();
                                                    }
                                                }
                                            }
                                        });
                                    }
                                    if (shouldUnmuteVideo) {
                                        shouldUnmuteVideo = false;
                                        player.loadUrl("javascript:" +
                                                "player.unMute()");
                                    }
                                    lastPosition = currentTime;
                                }
                            }
                        }
                    });
                }
            });
        }
    }

    private class SkippingAdTimerTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    player.loadUrl("javascript:" +
                            "if (player != null) {" +
                            "    (function () {" +
                            "        var $cross = document.getElementsByClassName(\"ytp-ad-overlay-close-container\")[0];" +
                            "        var $skip = document.getElementsByClassName(\"ytp-ad-skip-button\")[0];" +
                            "        if ($cross != null) {" +
                            "            $cross.click();" +
                            "        }" +
                            "        if ($skip != null) {" +
                            "             $skip.click();" +
                            "        }" +
                            "    })()" +
                            "}");
                }
            });
        }
    }

    private static class Skipping {
        public float from, to;

        public Skipping(float from, float to) {
            this.from = from;
            this.to = to;
        }
    }

    static final int PLAYER_STATE_UNSTARTED = -1,
            PLAYER_STATE_ENDED = 0,
            PLAYER_STATE_PLAYING = 1,
            PLAYER_STATE_PAUSED = 2,
            PLAYER_STATE_BUFFERING = 3,
            PLAYER_STATE_CUED = 5;
    static final String ACTION_LYRICS = "com.mister_chan.ytmusic.action.LYRICS",
            ACTION_NEXT = "com.mister_chan.ytmusic.action.NEXT",
            ACTION_PAUSE = "com.mister_chan.ytmusic.action.PAUSE",
            ACTION_PLAY = "com.mister_chan.ytmusic.action.PLAY";
    private static final String PLAYER = "document.getElementById(\"movie_player\")", YOUTUBE_MUSIC = "YouTube Music";

    private boolean isPlaying = false, isScreenOff = false, shouldAddOnStateChangeListener = false, shouldGetDuration = false, shouldSeekToLastPosition = false, shouldUnmuteVideo = false;
    private Button bPlayPause, bReload;
    float lastPosition = 0;
    int playerState = 0;
    private LinearLayout llWebView;
    private final List<Skipping> skippings = new ArrayList<>();
    long duration = 0;
    MediaSessionCompat mediaSession;
    private MediaWebView player;
    NotificationCompat.Action lyricsAction, nextAction;
    private NotificationService notificationService;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            NotificationService.NotificationBinder binder = (NotificationService.NotificationBinder) service;
            notificationService = binder.getService();
            title = YOUTUBE_MUSIC;
            playerState = 0;
            sendNotification();
            binder.startForeground();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    String title = YOUTUBE_MUSIC;
    private String lyricsLine = YOUTUBE_MUSIC, nowPlaying = "", stylelessLyricsLine = YOUTUBE_MUSIC;
    private String[] lyrics;
    private TextView tvLyrics, tvTitle;
    private Timer lyricsTimer, skippingAdTimer;
    private TimerTask lyricsTimerTask, skippingAdTimerTask;
    private WebView webView;
    private WindowManager windowManager;

    private boolean isNumeric(String s) {
        return Pattern.compile("^-?\\d+(\\.\\d+)?$").matcher(s).find();
    }

    private String isVideo(String url) {
        Matcher m = Pattern.compile("^https?://(www|m)\\.youtube\\.com/.*[?&]v=(?<v>[-0-9A-Z_a-z]+)").matcher(url);
        if (m.find()) {
            return m.group("v");
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
        ws.setAllowFileAccess(true);
        ws.setAppCacheEnabled(true);
        ws.setBlockNetworkImage(true);
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        ws.setDatabaseEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setJavaScriptEnabled(true);
        ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
        ws.setSupportMultipleWindows(true);
    }

    @SuppressLint({"RemoteViewLayout", "JavascriptInterface", "InvalidWakeLockTag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bPlayPause = findViewById(R.id.b_play_pause);
        bReload = findViewById(R.id.b_reload);
        llWebView = findViewById(R.id.ll_webview);
        tvTitle = findViewById(R.id.tv_title);

        // Initial views
        bPlayPause.setTypeface(Typeface.createFromAsset(getAssets(), "Player.ttf"));
        bPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
            }
        });
        bReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bReload.setVisibility(View.GONE);
                player.reload();
            }
        });
        findViewById(R.id.ll_title).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    webView.setVisibility(View.GONE);
                    player.setVisibility(View.VISIBLE);
                }
            }
        });

        // Initial WebViews
        webView = new WebView(this);
        player = new MediaWebView(this);
        initialWebView(webView);
        initialWebView(player);
        WebSettings ws = player.getSettings();
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUserAgentString("Chrome");
        player.addJavascriptInterface(this, "mainActivity");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                String v = isVideo(url);
                if (v != null) {
                    view.goBack();
                    player.loadUrl(url.replace("&pbj=1", "").replace("://m.", "://www."));
                    lastPosition = 0;
                    skippings.clear();
                    prepareNewVideo(v);
                    prepareTodoList();
                    webView.setVisibility(View.GONE);
                    player.setVisibility(View.VISIBLE);
                }
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.getSettings().setBlockNetworkImage(false);
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                view.getSettings().setBlockNetworkImage(true);
                super.onPageStarted(view, url, favicon);
            }
        });
        player.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.getSettings().setBlockNetworkImage(false);
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
                if (url.startsWith("https://www.youtube.com")) {
                    String v = isVideo(url);
                    if (v == null) { // When loading feed
                        view.goBack();
                        webView.loadUrl(url);
                        player.setVisibility(View.GONE);
                        webView.setVisibility(View.VISIBLE);
                    } else if (!nowPlaying.equals(v)) { // When loading another video
                        lastPosition = 0;
                        skippings.clear();
                        prepareNewVideo(v);
                    }
                    prepareTodoList();
                    title = title.replace(" - YouTube", "");
                    MainActivity.this.title = title;
                    tvTitle.setText(title);
                    if (isScreenOff) {
                        sendScreenNotification();
                    } else {
                        sendNotification();
                    }
                }
                super.onReceivedTitle(view, title);
            }
        });
        player.setVisibility(View.GONE);
        webView.loadUrl("https://m.youtube.com");

        // Initial lyrics window
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams();
        wmlp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        wmlp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        wmlp.format = PixelFormat.RGBA_8888;
        wmlp.gravity = Gravity.CENTER_VERTICAL | Gravity.TOP;
        wmlp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        tvLyrics = new TextView(this);
        tvLyrics.setPadding(0, 0x100, 0, 0);
        tvLyrics.setText(YOUTUBE_MUSIC);
        tvLyrics.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvLyrics.setTextColor(Color.RED);
        tvLyrics.setTextSize(24);
        tvLyrics.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
        windowManager.addView(tvLyrics, wmlp);

        // Initial notification
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel nc = new NotificationChannel("channel", "Media Controls", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(nc);
        IntentFilter notificationIntentFilter = new IntentFilter();
        notificationIntentFilter.addAction(ACTION_LYRICS);
        notificationIntentFilter.addAction(ACTION_NEXT);
        notificationIntentFilter.addAction(ACTION_PLAY);
        notificationIntentFilter.addAction(ACTION_PAUSE);
        lyricsAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_lyrics,
                "Lyrics",
                PendingIntent.getBroadcast(
                        this,
                        0,
                        new Intent(ACTION_LYRICS),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        nextAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_next,
                "Next",
                PendingIntent.getBroadcast(
                        this,
                        0,
                        new Intent(ACTION_NEXT),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action) {
                    case ACTION_PLAY:
                        player.loadUrl("javascript:" + PLAYER + ".playVideo()");
                        break;
                    case ACTION_PAUSE:
                        player.loadUrl("javascript:" + PLAYER + ".pauseVideo()");
                        break;
                    case ACTION_NEXT:
                        player.loadUrl("javascript:" + PLAYER + ".seekTo(" + PLAYER + ".getDuration())");
                        break;
                    case ACTION_LYRICS:
                        int visibility = tvLyrics.getVisibility();
                        switch (visibility) {
                            case View.VISIBLE:
                                tvLyrics.setVisibility(View.GONE);
                                break;
                            case View.INVISIBLE:
                            case View.GONE:
                                tvLyrics.setVisibility(View.VISIBLE);
                                break;
                        }
                        break;
                }
                abortBroadcast();
            }
        }, notificationIntentFilter);
        mediaSession = new MediaSessionCompat(this, "PlayService");
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, YOUTUBE_MUSIC)
                .build()
        );
        Intent notificationIntent = new Intent(this, NotificationService.class);
        bindService(notificationIntent, connection, Context.BIND_AUTO_CREATE);

        // Show when screen locked
        IntentFilter screenIntentFilter = new IntentFilter();
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenIntentFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action) {
                    case Intent.ACTION_SCREEN_OFF:
                        sendScreenNotification();
                        isScreenOff = true;
                        break;
                    case Intent.ACTION_USER_PRESENT:
                        sendNotification();
                        isScreenOff = false;
                        break;
                }
                abortBroadcast();
            }
        }, screenIntentFilter);

        // Start timer
        lyricsTimer = new Timer();
        lyricsTimerTask = new LyricsTimerTask();
        lyricsTimer.schedule(lyricsTimerTask, 1000, 100);
        skippingAdTimer = new Timer();
        skippingAdTimerTask = new SkippingAdTimerTask();
        skippingAdTimer.schedule(skippingAdTimerTask, 1000, 1000);
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, NotificationService.class));
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
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
        bPlayPause.setText(data == PLAYER_STATE_PLAYING ? "⏸" : "⏵");
        playerState = data;
        if (isScreenOff) {
            sendScreenNotification();
        } else {
            sendNotification();
        }
    }

    private void prepareNewVideo(String v) {
        readLyrics(v);
        nowPlaying = v;
        isPlaying = true;
    }

    private void prepareTodoList() {
        shouldAddOnStateChangeListener = true;
        shouldGetDuration = true;
        shouldSeekToLastPosition = true;
        shouldUnmuteVideo = true;
    }

    private void readLyrics(String v) {
        lyrics = new String[0x20000];
        lyricsLine = YOUTUBE_MUSIC;
        stylelessLyricsLine = YOUTUBE_MUSIC;
        tvLyrics.setTextColor(Color.RED);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                    "/sdcard/YTMusic/lyrics/" + v + ".lrc"),
                    StandardCharsets.UTF_8));
            String line;
            int offset = 0;
            while ((line = br.readLine()) != null) {
                Matcher matcher;
                if ((matcher = Pattern.compile(
                        "\\[(?<min>\\d{2}):(?<sec>\\d{2})\\.(?<centisec>\\d{2})\\](?:\\[\\d{2}:\\d{2}\\.\\d{2}\\])*(?<lrc>[^\\[\\]]+)$")
                        .matcher(line)).find()) {
                    for (int i = 0; matcher.find(i); i += 10) {
                        lyrics[Integer.parseInt(matcher.group("min")) * 6000
                                + Integer.parseInt(matcher.group("sec")) * 100
                                + Integer.parseInt(matcher.group("centisec"))
                                + offset / 10] =
                                matcher.group("lrc");
                    }
                } else if ((matcher = Pattern.compile("\\[ti:(?<ti>.*)\\]").matcher(line)).find()) {
                    tvLyrics.setText(stylizeLyrics(matcher.group("ti")).toUpperCase(Locale.ROOT));
                } else if ((matcher = Pattern.compile("\\[offset:(?<offset>.*)\\]").matcher(line)).find()) {
                    offset = Integer.parseInt(matcher.group("offset"));
                } else if ((matcher = Pattern.compile("\\[skipping:(?<from>.*),(?<to>.*)\\]").matcher(line)).find()) {
                    skippings.add(new Skipping(
                            Float.parseFloat(matcher.group("from")),
                            Float.parseFloat(matcher.group("to"))));
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            tvLyrics.setText(YOUTUBE_MUSIC);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendNotification() {
        notificationService.sendNotification(this);
    }

    private void sendScreenNotification() {
        Intent intent = new Intent();
        NotificationCompat.Action playPauseAction;
        if (playerState == MainActivity.PLAYER_STATE_PLAYING) {
            intent.setAction(MainActivity.ACTION_PAUSE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_pause, "Pause", pendingIntent).build();
        } else {
            intent.setAction(MainActivity.ACTION_PLAY);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_play, "Play", pendingIntent).build();
        }
        notificationService.sendScreenNotification(playPauseAction, nextAction, title, stylelessLyricsLine);
    }

    private void setLyricsStyle(String style) {
        switch (style) {
            case "BLACK":
                tvLyrics.setTextColor(Color.BLACK);
                break;
            case "BLUE":
                tvLyrics.setTextColor(Color.BLUE);
                break;
            case "GREEN":
                tvLyrics.setTextColor(Color.GREEN);
                break;
            case "CYAN":
                tvLyrics.setTextColor(Color.CYAN);
                break;
            case "RED":
                tvLyrics.setTextColor(Color.RED);
                break;
            case "MAGENTA":
                tvLyrics.setTextColor(Color.MAGENTA);
                break;
            case "YELLOW":
                tvLyrics.setTextColor(Color.YELLOW);
                break;
            case "WHITE":
                tvLyrics.setTextColor(Color.WHITE);
                break;
        }
    }

    private String stylizeLyrics(String lyrics) {
        Matcher matcher = Pattern.compile("\\{(?<style>[^{}]*)\\}").matcher(lyrics);
        while (matcher.find()) {
            setLyricsStyle(matcher.group("style"));
        }
        return lyrics.replaceAll("\\{.*\\}", "");
    }

    private void toggleState() {
        player.loadUrl("javascript:" +
                "var player = " + PLAYER + ";" +
                "if (player != null) {" +
                "    if (player.getPlayerState() == 1)" +
                "        player.pauseVideo();" +
                "    else" +
                "        player.playVideo()" +
                "}");
    }
}