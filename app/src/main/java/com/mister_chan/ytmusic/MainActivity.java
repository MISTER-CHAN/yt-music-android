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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int MAX_NUM_OF_LYRICS_LINES = 42 * 1024; // Range: 00:00.00 ~ 7:10.07

    static final int
            PLAYER_STATE_UNSTARTED = -1,
            PLAYER_STATE_ENDED = 0,
            PLAYER_STATE_PLAYING = 1,
            PLAYER_STATE_PAUSED = 2,
            PLAYER_STATE_BUFFERING = 3,
            PLAYER_STATE_CUED = 5;

    static final String
            ACTION_LYRICS = "com.mister_chan.ytmusic.action.LYRICS",
            ACTION_NEXT = "com.mister_chan.ytmusic.action.NEXT",
            ACTION_PAUSE = "com.mister_chan.ytmusic.action.PAUSE",
            ACTION_PLAY = "com.mister_chan.ytmusic.action.PLAY";

    private static final String PLAYER = "document.getElementById(\"movie_player\")";
    private static final String YOUTUBE_MUSIC = "YouTube Music";

    private static final String JS_ADD_ON_STATE_CHANGE_LISTENER = "javascript:" +
            "var addOnStateChangeListenerTimer = setInterval(function () {" +
            "    if (typeof player != \"undefined\" && player != null) {" +
            "        clearInterval(addOnStateChangeListenerTimer);" +
            "        player.addEventListener(\"onStateChange\", function (data) {" +
            "            mainActivity.onStateChange(data)" +
            "        });" +
            "    }" +
            "}, 100);";

    private static final String JS_GET_CURRENT_TIME = "" +
            "if (typeof player != \"undefined\" && player != null) {" +
            "    var currentTime = player.getCurrentTime();" +
            "    currentTime" +
            "}";

    private static final String JS_NEXT_VIDEO = "javascript:" + PLAYER + ".seekTo(" + PLAYER + ".getDuration())";
    private static final String JS_PAUSE_VIDEO = "javascript:" + PLAYER + ".pauseVideo()";
    private static final String JS_PLAY_VIDEO = "javascript:" + PLAYER + ".playVideo()";

    private static final String JS_SET_PLAYER = "javascript:" +
            "var player;" +
            "var settingPlayerTimer = setInterval(function () {" +
            "    player = " + PLAYER + ";" +
            "    if (player != null) {" +
            "        clearInterval(settingPlayerTimer);" +
            "    }" +
            "}, 100);";

    private static final String JS_SET_SKIPPINGS = "javascript: var skippings = [%s];";
    private static final String JS_SET_NO_SKIPPINGS = String.format(JS_SET_SKIPPINGS, "");

    private static final String JS_SKIP = "javascript:" +
            "var skippingTimer = setInterval(function () {" +
            "    if (typeof skippings != \"undefined\") {" +
            "        for (let i = 0; i < skippings.length; ++i) {" +
            "            let skipping = skippings[i];" +
            "            if (currentTime >= skipping.from) {" +
            "                if (currentTime < skipping.to) {" +
            "                    player.seekTo(skipping.to);" +
            "                } else if (skipping.to <= 0) {" +
            "                    player.seekTo(player.getDuration());" +
            "                }" +
            "            }" +
            "            return;" +
            "        }" +
            "    }" +
            "}, 100);";

    private static final String JS_SKIP_AD = "javascript:" +
            "setInterval(function () {" +
            "    if (typeof player != \"undefined\" && player != null) {" +
            "        let $cross = document.getElementsByClassName(\"ytp-ad-overlay-close-container\")[0];" +
            "        let $skip = document.getElementsByClassName(\"ytp-ad-skip-button\")[0];" +
            "        if ($cross != null) {" +
            "            $cross.click();" +
            "        }" +
            "        if ($skip != null) {" +
            "             $skip.click();" +
            "        }" +
            "    }" +
            "}, 100);";

    private static final String JS_TOGGLE_STATE = "javascript:" +
            "if (typeof player != \"undefined\" && player != null) {" +
            "    if (player.getPlayerState() == " + PLAYER_STATE_PLAYING + ")" +
            "        player.pauseVideo();" +
            "    else" +
            "        player.playVideo()" +
            "}";

    private static final String JS_UNMUTE = "javascript:" +
            "var unmutingTimer = setInterval(function () {" +
            "    if (typeof player != \"undefined\" && player != null) {" +
            "        clearInterval(unmutingTimer);" +
            "        player.unMute();" +
            "    }" +
            "}, 100);";

    private static final Map<String, Integer> colorMap = new HashMap<String, Integer>() {{
        put("RED", Color.RED);
        put("GREEN", Color.GREEN);
        put("BLUE", Color.BLUE);
    }};

    private boolean isPlaying = false;
    private boolean isScreenOff = false;
    private boolean shouldSetSkippings = false;
    private boolean shouldGetDuration = false;
    private boolean shouldSeekToLastPosition = false;
    private Button bPlayPause, bReload;
    float lastPosition = 0F;
    int playerState = 0;
    private LinearLayout llCustom, llFullScreen, llWebView;
    long duration = 0L;
    MediaSessionCompat mediaSession;
    private MediaWebView player;
    NotificationCompat.Action lyricsAction, nextAction;
    private NotificationService notificationService;
    String title = YOUTUBE_MUSIC;
    private String jsSetSkippings = JS_SET_NO_SKIPPINGS;
    private String lyricsLine = YOUTUBE_MUSIC;
    private String nowPlaying = "";
    private String stylelessLyricsLine = YOUTUBE_MUSIC;
    private String[] lyrics;
    private TextView tvLyrics, tvTitle;
    private Timer lyricsTimer;
    private TimerTask lyricsTimerTask;
    private View frontView;
    private WebView webView;
    private WindowManager windowManager;

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
        public void onServiceDisconnected(ComponentName name) {}
    };

    private class LyricsTimerTask extends TimerTask {

        @Override
        public void run() {
            runOnUiThread(() -> player.evaluateJavascript(JS_GET_CURRENT_TIME, value -> {
                // value - current time in seconds
                if ("null".equals(value)) {
                    return;
                }
                float currentTime = Float.parseFloat(value);
                if (currentTime <= 0) {
                    return;
                }

                // Show lyrics
                int centiseconds = (int) (currentTime * 100) * 20 / 1000 * 1000 / 20;
                if (centiseconds >= MAX_NUM_OF_LYRICS_LINES) {
                    return;
                }
                String line = lyrics[centiseconds];
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
                if (shouldSeekToLastPosition) {
                    shouldSeekToLastPosition = false;
                    if (currentTime < lastPosition) {
                        player.loadUrl("javascript:" +
                                "player.seekTo(" + lastPosition + ")");
                    }
                } else if (shouldSetSkippings) {
                    shouldSetSkippings = false;
                    player.loadUrl(jsSetSkippings);
                    player.loadUrl(JS_SKIP);
                }
                if (shouldGetDuration) {
                    player.evaluateJavascript("player.getDuration()", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if ("null".equals(value)) {
                                return;
                            }
                            shouldGetDuration = false;
                            duration = (long) Float.parseFloat(value);
                            if (!isScreenOff) {
                                sendNotification();
                            }
                        }
                    });
                }

                lastPosition = currentTime;
            }));
        }
    }

    private synchronized void bringToFront(View view) {
        webView.setVisibility(View.GONE);
        player.setVisibility(View.GONE);
        llFullScreen.setVisibility(View.GONE);
        view.setVisibility(View.VISIBLE);
        frontView = view;
    }

    private String isVideo(String url) {
        Matcher m = Pattern.compile("^https?://(?:www|m)\\.youtube\\.com/.*[?&]v=(?<v>[-0-9A-Z_a-z]+)").matcher(url);
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
        ws.setBlockNetworkImage(true);
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        ws.setDatabaseEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setJavaScriptEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setSupportMultipleWindows(true);
        ws.setUseWideViewPort(true);
    }

    @SuppressLint({"RemoteViewLayout", "JavascriptInterface", "InvalidWakeLockTag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bPlayPause = findViewById(R.id.b_play_pause);
        bReload = findViewById(R.id.b_reload);
        llWebView = findViewById(R.id.ll_webview);
        llCustom = findViewById(R.id.ll_custom);
        llFullScreen = findViewById(R.id.ll_full_screen);
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
                if (isPlaying && frontView == webView) {
                    bringToFront(player);
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
                    prepareNewVideo(v);
                    prepareTodoList();
                    bringToFront(player);
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
                String v = isVideo(url);
                if (v != null) {
                    view.loadUrl(JS_SET_PLAYER);
                    view.loadUrl(JS_ADD_ON_STATE_CHANGE_LISTENER);
                    view.loadUrl(JS_SKIP_AD);
                }
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
                    if (v == null) { // Loads feed
                        view.goBack();
                        shouldSeekToLastPosition = true;
                        webView.loadUrl(url);
                        bringToFront(webView);
                    } else if (!nowPlaying.equals(v)) { // Loads another video
                        lastPosition = 0;
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

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                llCustom.removeAllViews();
                bringToFront(player);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                llCustom.addView(view);
                bringToFront(llFullScreen);
            }
        });
        bringToFront(webView);
        webView.loadUrl("https://m.youtube.com");

        // Initial lyrics window
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
                        player.loadUrl(JS_PLAY_VIDEO);
                        break;
                    case ACTION_PAUSE:
                        player.loadUrl(JS_PAUSE_VIDEO);
                        break;
                    case ACTION_NEXT:
                        player.loadUrl(JS_NEXT_VIDEO);
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
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, NotificationService.class));
        lyricsTimer.cancel();
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (frontView == webView) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    moveTaskToBack(false);
                }
            } else if (frontView == player) {
                bringToFront(webView);
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
        player.loadUrl("javascript: clearInterval(skippingTimer);");
        jsSetSkippings = JS_SET_NO_SKIPPINGS;
        player.loadUrl(jsSetSkippings);
        readLyrics(v);
        nowPlaying = v;
        isPlaying = true;
        player.loadUrl("javascript: player.seekTo(0);");
    }

    private void prepareTodoList() {
        shouldGetDuration = true;
        shouldSeekToLastPosition = false;
        shouldSetSkippings = true;
    }

    private void readLyrics(String v) {
        lyrics = new String[MAX_NUM_OF_LYRICS_LINES];
        lyricsLine = YOUTUBE_MUSIC;
        stylelessLyricsLine = YOUTUBE_MUSIC;
        tvLyrics.setTextColor(Color.RED);
        StringBuilder skippings = new StringBuilder();
        File file = new File("/sdcard" + "/YTMusic/lyrics/" + v + ".lrc");
        if (!file.exists()) {
            tvLyrics.setText(YOUTUBE_MUSIC);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int offset = 0;
            while ((line = br.readLine()) != null) {
                Matcher matcher;
                if ((matcher = Pattern.compile(
                        "\\[(?<min>\\d{2}):(?<sec>\\d{2})\\.(?<centisec>\\d{2})\\](?:\\[\\d{2}:\\d{2}\\.\\d{2}\\])*(?<lrc>[^\\[\\]]+)$")
                        .matcher(line)).find()) {
                    for (int i = 0; matcher.find(i); i += 10) {
                        int centiseconds = Integer.parseInt(matcher.group("min")) * 6000
                                + Integer.parseInt(matcher.group("sec")) * 100
                                + Integer.parseInt(matcher.group("centisec"))
                                + offset / 10;
                        if (centiseconds < MAX_NUM_OF_LYRICS_LINES) {
                            lyrics[centiseconds] = matcher.group("lrc");
                        }
                    }
                } else if ((matcher = Pattern.compile("\\[ti:(?<ti>.*)\\]").matcher(line)).find()) {
                    tvLyrics.setText(stylizeLyrics(matcher.group("ti")).toUpperCase(Locale.ROOT));
                } else if ((matcher = Pattern.compile("\\[offset:(?<offset>.*)\\]").matcher(line)).find()) {
                    offset = Integer.parseInt(matcher.group("offset"));
                } else if ((matcher = Pattern.compile("\\[skipping:(?<from>.*),(?<to>.*)\\]").matcher(line)).find()) {
                    skippings.append(", {from: ").append(matcher.group("from")).append(", to: ").append(matcher.group("to")).append("}");
                }
            }
            if (!"".equals(skippings.toString())) {
                jsSetSkippings = String.format(JS_SET_SKIPPINGS, skippings.substring(2));
            }
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
        if (playerState == PLAYER_STATE_PLAYING) {
            intent.setAction(ACTION_PAUSE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_pause, "Pause", pendingIntent).build();
        } else {
            intent.setAction(ACTION_PLAY);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            playPauseAction = new NotificationCompat.Action.Builder(R.drawable.ic_play, "Play", pendingIntent).build();
        }
        notificationService.sendScreenNotification(playPauseAction, nextAction, title, stylelessLyricsLine);
    }

    private void setLyricsStyle(String style) {
        if (colorMap.containsKey(style)) {
            tvLyrics.setTextColor(colorMap.get(style));
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
        player.loadUrl(JS_TOGGLE_STATE);
    }
}