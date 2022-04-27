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
import android.util.Log;
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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    static final int
            PLAYER_STATE_UNSTARTED = -1,
            PLAYER_STATE_ENDED = 0,
            PLAYER_STATE_PLAYING = 1,
            PLAYER_STATE_PAUSED = 2,
            PLAYER_STATE_BUFFERING = 3,
            PLAYER_STATE_CUED = 5;

    private static final Pattern PATTERN_LYRICS = Pattern.compile("\\[(?<min>\\d{2}):(?<sec>\\d{2})\\.(?<centisec>\\d{2})\\](?:\\[\\d{2}:\\d{2}\\.\\d{2}\\])*(?<lrc>[^\\[\\]]+)$");
    private static final Pattern PATTERN_VIDEO_URL = Pattern.compile("^https?://(?:www|m)\\.youtube\\.com/.*[?&]v=(?<v>[-0-9A-Z_a-z]+)");

    static final String
            ACTION_LYRICS = "com.mister_chan.ytmusic.action.LYRICS",
            ACTION_NEXT = "com.mister_chan.ytmusic.action.NEXT",
            ACTION_PAUSE = "com.mister_chan.ytmusic.action.PAUSE",
            ACTION_PLAY = "com.mister_chan.ytmusic.action.PLAY";

    private static final String HOME_PAGE_URL = "https://www.youtube.com/";
    private static final String PLAYER = "document.getElementById(\"movie_player\")";
    private static final String YOUTUBE_MUSIC = "YouTube Music";

    private static final String JS_ADD_ON_STATE_CHANGE_LISTENER = "javascript:" +
            "var cancelPauses = false;" +
            "var onStateChange = (data) => {" +
            "    if (cancelPauses && data == 2) {" +
            "        player.playVideo();" +
            "    }" +
            "    mainActivity.onStateChange(data);" +
            "};" +
            "var addOnStateChangeListenerTimer = setInterval(() => {" +
            "    if (typeof player != \"undefined\" && player != null) {" +
            "        clearInterval(addOnStateChangeListenerTimer);" +
            "        player.addEventListener(\"onStateChange\", data => onStateChange(data));" +
            "    }" +
            "}, 100);";

    private static final String JS_EXIT_FULLSCREEN = "javascript:" +
            "var isExitingFullscreen = false;" +
            "var exitFullscreenTimer = setInterval(() => {" +
            "    if ((typeof isEnteringFullscreen == \"undefined\" || !isEnteringFullscreen) && typeof player != \"undefined\" && player != null) {" +
            "        if (!isExitingFullscreen) {" +
            "            player.toggleFullscreen();" +
            "            isExitingFullscreen = true;" +
            "        } else if (!player.isFullscreen()) {" +
            "            clearInterval(exitFullscreenTimer);" +
            "            isExitingFullscreen = false;" +
            "        }" +
            "    }" +
            "}, 100);";

    private static final String JS_FULLSCREEN = "javascript:" +
            "var isEnteringFullscreen = false;" +
            "var fullscreenTimer = setInterval(() => {" +
            "    if ((typeof isExitingFullscreen == \"undefined\" || !isExitingFullscreen) && typeof player != \"undefined\" && player != null) {" +
            "        if (!isEnteringFullscreen) {" +
            "            player.toggleFullscreen();" +
            "            isEnteringFullscreen = true;" +
            "        } else if (player.isFullscreen()) {" +
            "            clearInterval(fullscreenTimer);" +
            "            isEnteringFullscreen = false;" +
            "        }" +
            "    }" +
            "}, 100);";

    private static final String JS_GET_CURRENT_TIME = "" +
            "if (typeof player != \"undefined\" && player != null) {" +
            "    var currentTime = 0;" +
            "    try {" +
            "        currentTime = player.getCurrentTime();" +
            "    } catch (e) {}" +
            "    currentTime;" +
            "}";

    private static final String JS_NEXT_VIDEO = "javascript:" + PLAYER + ".seekTo(" + PLAYER + ".getDuration())";
    private static final String JS_PAUSE_VIDEO = "javascript:" + PLAYER + ".pauseVideo()";
    private static final String JS_PLAY_VIDEO = "javascript:" + PLAYER + ".playVideo()";

    private static final String JS_REMOVE_FULLSCREEN_BUTTONS = "javascript:" +
            "document.getElementsByClassName(\"ytp-fullscreen-button\")[0].setAttribute(\"style\", \"display: none;\");" +
            "document.getElementsByClassName(\"ytp-size-button\")[0].setAttribute(\"style\", \"display: none;\");" +
            "document.getElementsByClassName(\"ytp-miniplayer-button\")[0].setAttribute(\"style\", \"display: none;\");";

    private static final String JS_SET_PLAYER = "javascript:" +
            "var player;" +
            "var settingPlayerTimer = setInterval(() => {" +
            "    player = " + PLAYER + ";" +
            "    if (player != null) {" +
            "        clearInterval(settingPlayerTimer);" +
            "    }" +
            "}, 100);";

    private static final String JS_SET_SKIPPINGS = "javascript:var skippings = [%s];";
    private static final String JS_SET_NO_SKIPPINGS = String.format(JS_SET_SKIPPINGS, "");

    private static final String JS_SKIP = "javascript:" +
            "var skippingTimer = setInterval(() => {" +
            "    if (typeof skippings != \"undefined\") {" +
            "        for (const skipping of skippings) {" +
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
            "var isPlayingAd = false;" +
            "setInterval(() => {" +
            "    if (typeof player != \"undefined\" && player != null) {" +
            "        if (document.getElementsByClassName(\"ytp-ad-player-overlay\").length > 0) {" +
            "            let skip = document.getElementsByClassName(\"ytp-ad-skip-button\")[0];" +
            "            if (skip != null) {" +
            "                skip.click();" +
            "            } else if (!player.isMuted()) {" +
            "                player.mute();" +
            "                isPlayingAd = true;" +
            "                mainActivity.setPlayingAd(true);" +
            "                mainActivity.sendNotification();" +
            "            }" +
            "        } else if (isPlayingAd) {" +
            "            isPlayingAd = false;" +
            "            player.unMute();" +
            "            mainActivity.setPlayingAd(false);" +
            "            mainActivity.sendNotification();" +
            "        }" +
            "    }" +
            "}, 100);";

    private static final String JS_START_CANCELLING_PAUSES = "javascript:" +
            "cancelPauses = true;" +
            "setTimeout(() => cancelPauses = false, 1000);";

    private static final String JS_TOGGLE_FULLSCREEN = "javascript:" +
            "if (typeof player != \"undefined\" && player != null) {" +
            "    player.toggleFullscreen();" +
            "} else {" +
            "    var fullscreenTimer = setInterval(() => {" +
            "        if (typeof player != \"undefined\" && player != null) {" +
            "            clearInterval(fullscreenTimer);" +
            "            player.toggleFullscreen();" +
            "        }" +
            "    }, 100);" +
            "}";

    private static final String JS_TOGGLE_STATE = "javascript:" +
            "if (typeof player != \"undefined\" && player != null) {" +
            "    if (player.getPlayerState() == " + PLAYER_STATE_PLAYING + ")" +
            "        player.pauseVideo();" +
            "    else" +
            "        player.playVideo()" +
            "}";

    private static final String JS_UNMUTE = "javascript:" +
            "var unmutingTimer = setInterval(() => {" +
            "    if (typeof player != \"undefined\" && player != null) {" +
            "        clearInterval(unmutingTimer);" +
            "        player.unMute();" +
            "    }" +
            "}, 100);";

    private static final Typeface TYPEFACE_DEFAULT_ITALIC = Typeface.defaultFromStyle(Typeface.ITALIC);
    private static final Typeface TYPEFACE_DEFAULT_BOLD_ITALIC = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC);

    private boolean floatingLyrics = true;
    private boolean hasEverPlayed = false;
    private boolean isCustomViewShowed = false;
    private boolean isPlayingAd = false;
    private boolean isScreenOff = false;
    private boolean shouldSetSkippings = false;
    private boolean shouldGetDuration = false;
    private boolean shouldSeekToLastPosition = false;
    private boolean shouldFullScreen = false;
    private boolean shouldUpdateIndexOfLyricsLineFromCurrentTime = false;
    private Button bFullscreenNextVideo;
    private Button bFullscreenPlayPause;
    private Button bNextVideo;
    private Button bPlayPause;
    float lastPosition = 0f;
    int indexOfHighlightedLyricsLine = -1;
    private int indexOfNextLyricsLine = 1;
    int playerState = 0;
    private LinearLayout llCustom;
    private LinearLayout llFullscreen;
    private LinearLayout llMain;
    private LinearLayout llNoLyricsWarning;
    private LinearLayout llWebView;
    private ListView lvLyrics;
    long duration = 0L;
    private LyricsLine[] lyrics = new LyricsLine[0];
    private LyricsLine nextLyricsLine;
    MediaSessionCompat mediaSession;
    private MediaWebView player;
    NotificationCompat.Action lyricsAction, nextAction;
    private NotificationService notificationService;
    private ProgressBar pbFullscreenProgress;
    private ProgressBar pbProgress;
    private String title = YOUTUBE_MUSIC;
    private String jsSetSkippings = JS_SET_NO_SKIPPINGS;
    private String nowPlaying = "";
    String lyricsLinePure = YOUTUBE_MUSIC;
    TextView[] tvLyricsLines;
    private TextView tvFloatingLyrics;
    private TextView tvFullscreenTitle;
    private TextView tvTitle;
    private Timer lyricsTimer;
    private View frontView;
    private WebView webView;
    private WindowManager windowManager;

    private final BroadcastReceiver mediaControlsReceiver = new BroadcastReceiver() {
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
                    floatingLyrics = !floatingLyrics;
                    tvFloatingLyrics.setVisibility(floatingLyrics ? View.VISIBLE : View.GONE);
                    break;
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case Intent.ACTION_SCREEN_OFF:
                    notificationService.sendScreenNotification(MainActivity.this, getTitleForNotification());
                    isScreenOff = true;
                    break;
                case Intent.ACTION_USER_PRESENT:
                    notificationService.sendNotification(MainActivity.this, getTitleForNotification());
                    isScreenOff = false;
                    break;
            }
        }
    };

    private final AdapterView.OnItemClickListener onLyricsItemClickListener = (parent, view, position, id) -> {
        if (0 < position && position < lyrics.length - 1) {
            seekTo(lyrics[position].time);
            scrollToLyricsLine(position);
            highlightLyricsLine(position);
            tvFloatingLyrics.setText(stylizeLyrics(lyrics[position].lyrics));
        }
    };

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

    private final TimerTask lyricsTimerTask = new TimerTask() {
        @Override
        public void run() {

            runOnUiThread(() -> player.evaluateJavascript(JS_GET_CURRENT_TIME, value -> {
                // value - current time in seconds
                if ("null".equals(value)) {
                    return;
                }
                float currentTime = Float.parseFloat(value);
                pbFullscreenProgress.setProgress((int) currentTime);
                pbProgress.setProgress((int) currentTime);
                if (currentTime <= 0) {
                    return;
                }

                // Show lyrics
                if (lyrics.length > 0 && playerState == PLAYER_STATE_PLAYING) {
                    if (shouldUpdateIndexOfLyricsLineFromCurrentTime) {
                        shouldUpdateIndexOfLyricsLineFromCurrentTime = false;
                        int earlier = 0, later = lyrics.length - 1, mid;
                        while (earlier < later) {
                            mid = earlier + ((later - earlier) >> 1);
                            float time = lyrics[mid].time;
                            if (time < currentTime) {
                                earlier = mid + 1;
                            } else if (time > currentTime) {
                                later = mid - 1;
                            } else {
                                // earlier = mid;
                                later = mid;
                                break;
                            }
                        }
                        indexOfNextLyricsLine = Math.max(later, 0);
                        nextLyricsLine = lyrics[indexOfNextLyricsLine];
                    }
                    if (currentTime >= nextLyricsLine.time) {
                        String line = nextLyricsLine.lyrics;
                        line = stylizeLyrics(line);
                        lyricsLinePure = line;
                        tvFloatingLyrics.setText(line);
                        if (isScreenOff) {
                            notificationService.sendScreenNotification(MainActivity.this, getTitleForNotification());
                        } else {
                            scrollToLyricsLine(indexOfNextLyricsLine);
                            highlightLyricsLine(indexOfNextLyricsLine);
                        }
                        if (++indexOfNextLyricsLine < lyrics.length) {
                            nextLyricsLine = lyrics[indexOfNextLyricsLine];
                        } else {
                            nextLyricsLine = new LyricsLine(Float.MAX_VALUE, null);
                        }
                    }
                }

                // Should-dos
                if (shouldSeekToLastPosition) {
                    shouldSeekToLastPosition = false;
                    if (currentTime < lastPosition) {
                        seekTo(lastPosition);
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
                            pbFullscreenProgress.setMax((int) duration);
                            pbProgress.setMax((int) duration);
                            if (!isScreenOff) {
                                sendNotification();
                            }
                        }
                    });
                }
                if (shouldFullScreen) {
                    shouldFullScreen = false;
                    setFullscreen(true);
                    player.loadUrl(JS_REMOVE_FULLSCREEN_BUTTONS);
                }

                lastPosition = currentTime;
            }));
        }
    };

    private final WebChromeClient playerChromeClient = new WebChromeClient() {
        @Override
        public void onReceivedTitle(WebView view, String title) {
            title = title.replace(" - YouTube", "");
            MainActivity.this.title = title;
            tvFullscreenTitle.setText(title);
            tvTitle.setText(title);
            sendNotification();
            super.onReceivedTitle(view, title);
        }

        @Override
        public void onHideCustomView() {
            llCustom.removeAllViews();
            if (frontView == llFullscreen) {
                bringToFront(player);
            }
            isCustomViewShowed = false;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            llCustom.addView(view);
            if (frontView == player) {
                bringToFront(llFullscreen);
            }
            isCustomViewShowed = true;
        }
    };

    private final WebViewClient playerViewClient = new WebViewClient() {
        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (url.startsWith(HOME_PAGE_URL) && !HOME_PAGE_URL.equals(url)) {
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
            }
            super.doUpdateVisitedHistory(view, url, isReload);
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
    };

    private final WebViewClient viewClient = new WebViewClient() {
        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            String v = isVideo(url);
            if (v != null) {
                view.goBack();
                player.loadUrl(url.replace("&pbj=1", "").replace("://m.", "://www."));
                bringToFront(player);
            }
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
    };

    private synchronized void bringToFront(View view) {
        if (view == webView) {
            llFullscreen.setVisibility(View.GONE);
            player.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            llMain.setVisibility(View.VISIBLE);
            if (floatingLyrics && lyrics.length > 0) {
                tvFloatingLyrics.setVisibility(View.VISIBLE);
            }
        } else if (view == player) {
            llFullscreen.setVisibility(View.GONE);
            webView.setVisibility(View.GONE);
            player.setVisibility(View.VISIBLE);
            llMain.setVisibility(View.VISIBLE);
            if (floatingLyrics && lyrics.length > 0) {
                tvFloatingLyrics.setVisibility(View.VISIBLE);
            }
        } else if (view == llFullscreen) {
            player.setVisibility(View.GONE);
            llMain.setVisibility(View.GONE);
            llFullscreen.setVisibility(View.VISIBLE);
            if (floatingLyrics && lyrics.length > 0) {
                tvFloatingLyrics.setVisibility(View.GONE);
            }
        }
        frontView = view;
    }

    private String getTitleForNotification() {
        return isPlayingAd ? "廣告"
                : playerState == PLAYER_STATE_BUFFERING ? "緩衝中……"
                : title;
    }

    private void highlightLyricsLine(int index) {
        if ((0 < indexOfHighlightedLyricsLine && indexOfHighlightedLyricsLine < lyrics.length - 1)
                && tvLyricsLines[indexOfHighlightedLyricsLine] != null) {
            tvLyricsLines[indexOfHighlightedLyricsLine].setTextSize(20.0f);
            tvLyricsLines[indexOfHighlightedLyricsLine].setTextColor(Color.LTGRAY);
            tvLyricsLines[indexOfHighlightedLyricsLine].setTypeface(TYPEFACE_DEFAULT_ITALIC);
        }
        indexOfHighlightedLyricsLine = index;
        if ((0 < indexOfHighlightedLyricsLine && indexOfHighlightedLyricsLine < lyrics.length - 1)
                && tvLyricsLines[indexOfHighlightedLyricsLine] != null) {
            tvLyricsLines[indexOfHighlightedLyricsLine].setTextSize(24.0f);
            tvLyricsLines[indexOfHighlightedLyricsLine].setTextColor(Color.WHITE);
            tvLyricsLines[indexOfHighlightedLyricsLine].setTypeface(TYPEFACE_DEFAULT_BOLD_ITALIC);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initialWebView(WebView wv) {
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

    private static String isVideo(String url) {
        Matcher m = PATTERN_VIDEO_URL.matcher(url);
        if (m.find()) {
            return m.group("v");
        }
        return null;
    }

    @SuppressLint({"RemoteViewLayout", "JavascriptInterface", "InvalidWakeLockTag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bFullscreenNextVideo = findViewById(R.id.b_fullscreen_next_video);
        bFullscreenPlayPause = findViewById(R.id.b_fullscreen_play_pause);
        bNextVideo = findViewById(R.id.b_next_video);
        bPlayPause = findViewById(R.id.b_play_pause);
        llFullscreen = findViewById(R.id.ll_full_screen);
        llCustom = findViewById(R.id.ll_custom);
        llMain = findViewById(R.id.ll_main);
        llNoLyricsWarning = findViewById(R.id.ll_no_lyrics_warning);
        llWebView = findViewById(R.id.ll_webview);
        lvLyrics = findViewById(R.id.lv_lyrics);
        pbFullscreenProgress = findViewById(R.id.pb_fullscreen_progress);
        pbProgress = findViewById(R.id.pb_progress);
        tvFullscreenTitle = findViewById(R.id.tv_fullscreen_title);
        tvTitle = findViewById(R.id.tv_title);

        // Initial views
        bFullscreenNextVideo.setTypeface(Typeface.createFromAsset(getAssets(), "Player.ttf"));
        bFullscreenNextVideo.setOnClickListener(v -> player.loadUrl(JS_NEXT_VIDEO));
        bFullscreenPlayPause.setTypeface(Typeface.createFromAsset(getAssets(), "Player.ttf"));
        bFullscreenPlayPause.setOnClickListener(v -> toggleState());
        bNextVideo.setTypeface(Typeface.createFromAsset(getAssets(), "Player.ttf"));
        bNextVideo.setOnClickListener(v -> player.loadUrl(JS_NEXT_VIDEO));
        bPlayPause.setTypeface(Typeface.createFromAsset(getAssets(), "Player.ttf"));
        bPlayPause.setOnClickListener(v -> toggleState());
        lvLyrics.setOnItemClickListener(onLyricsItemClickListener);
        findViewById(R.id.tv_ignore_lyrics).setOnClickListener(v -> llNoLyricsWarning.setVisibility(View.GONE));

        // Initial WebViews
        webView = new WebView(this);
        player = new MediaWebView(this);
        llWebView.addView(webView);
        llWebView.addView(player);
        initialWebView(webView);
        initialWebView(player);
        WebSettings ws = player.getSettings();
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUserAgentString("Chrome");
        player.addJavascriptInterface(this, "mainActivity");
        webView.setWebViewClient(viewClient);
        player.setWebViewClient(playerViewClient);
        player.setWebChromeClient(playerChromeClient);
        bringToFront(webView);
        webView.loadUrl("https://m.youtube.com/");

        // Initial floating lyrics window
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams();
        wmlp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        wmlp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        wmlp.format = PixelFormat.RGBA_8888;
        wmlp.gravity = Gravity.CENTER_VERTICAL | Gravity.TOP;
        wmlp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        tvFloatingLyrics = new TextView(this);
        tvFloatingLyrics.setPadding(0, 0x100, 0, 0);
        tvFloatingLyrics.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvFloatingLyrics.setTextColor(Color.RED);
        tvFloatingLyrics.setTextSize(24);
        tvFloatingLyrics.setTypeface(TYPEFACE_DEFAULT_BOLD_ITALIC);
        windowManager.addView(tvFloatingLyrics, wmlp);

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
        registerReceiver(mediaControlsReceiver, notificationIntentFilter);
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
        registerReceiver(screenReceiver, screenIntentFilter);

        // Start timer
        lyricsTimer = new Timer();
        lyricsTimer.schedule(lyricsTimerTask, 1000, 100);
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, NotificationService.class));
        lyricsTimer.cancel();
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void onFullscreenTitleClick(View view) {
        if (lyrics.length > 0) {
            setLyricsViewVisibility(lvLyrics.getVisibility() != View.VISIBLE);
        }
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
            } else if (frontView == player || frontView == llFullscreen) {
                bringToFront(webView);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        player.loadUrl(JS_START_CANCELLING_PAUSES);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @JavascriptInterface
    public void onStateChange(int data) {
        playerState = data;

        if (data == PLAYER_STATE_PLAYING) {
            bFullscreenPlayPause.setText("⏸");
            bPlayPause.setText("⏸");
            shouldUpdateIndexOfLyricsLineFromCurrentTime = true;
        } else {
            bFullscreenPlayPause.setText("⏵");
            bPlayPause.setText("⏵");
        }

        sendNotification();

    }

    public void onTitleClick(View view) {
        if (hasEverPlayed) {
            if (frontView == webView) {
                bringToFront(isCustomViewShowed ? llFullscreen : player);
            }
        }
    }

    private void prepareNewVideo(String v) {
        player.loadUrl("javascript:clearInterval(skippingTimer);");
        jsSetSkippings = JS_SET_NO_SKIPPINGS;
        player.loadUrl(jsSetSkippings);
        isPlayingAd = false;
        readLyrics(v);
        nowPlaying = v;
        hasEverPlayed = true;
        seekTo(0.0f);
    }

    private void prepareTodoList() {
        shouldGetDuration = true;
        shouldSeekToLastPosition = false;
        shouldSetSkippings = true;
        shouldFullScreen = !isCustomViewShowed;
    }

    private String purifyLyrics(String lyrics) {
        char c = '\0';
        if (lyrics.length() >= 3 && lyrics.charAt(1) == ':' && lyrics.charAt(2) == ' '
                && ((c = lyrics.charAt(0)) == 'M') || c == 'F' || c == 'D') {
            return lyrics.substring(3);
        }
        return lyrics;
    }

    private void readLyrics(String v) {
        lyrics = new LyricsLine[0];
        List<LyricsLine> lyricsMap = new ArrayList<>();
        lyricsMap.add(new LyricsLine(0.0f, ""));
        lyricsLinePure = YOUTUBE_MUSIC;
        tvFloatingLyrics.setTextColor(Color.RED);
        indexOfNextLyricsLine = 0;
        indexOfHighlightedLyricsLine = -1;
        nextLyricsLine = new LyricsLine(0.0f, "");
        lvLyrics.setVisibility(View.GONE);
        lvLyrics.setAdapter(null);
        tvLyricsLines = new TextView[0];
        llNoLyricsWarning.setVisibility(View.GONE);
        StringBuilder skippings = new StringBuilder();
        File file = new File("/sdcard" + "/YTMusic/lyrics/" + v + ".lrc");
        if (!file.exists()) {
            tvFloatingLyrics.setText("");
            llNoLyricsWarning.setVisibility(View.VISIBLE);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            float offset = 0.0f;
            while ((line = br.readLine()) != null) {
                Matcher matcher;
                if ((matcher = PATTERN_LYRICS.matcher(line)).find()) {
                    for (int i = 0; matcher.find(i); i += 10) {
                        float time = Float.parseFloat(matcher.group("min")) * 60f
                                + Float.parseFloat(matcher.group("sec"))
                                + Float.parseFloat(matcher.group("centisec")) * 0.01f
                                + offset * 0.001f;
                        lyricsMap.add(new LyricsLine(time, matcher.group("lrc").toUpperCase(Locale.ROOT)));
                    }
                } else if ((matcher = Pattern.compile("\\[ti:(?<ti>.*)\\]").matcher(line)).find()) {
                    tvFloatingLyrics.setText(stylizeLyrics(matcher.group("ti")).toUpperCase(Locale.ROOT));
                } else if ((matcher = Pattern.compile("\\[offset:(?<offset>.*)\\]").matcher(line)).find()) {
                    offset = Float.parseFloat(matcher.group("offset"));
                } else if ((matcher = Pattern.compile("\\[skipping:(?<from>.*),(?<to>.*)\\]").matcher(line)).find()) {
                    skippings.append(", {from: ").append(matcher.group("from")).append(", to: ").append(matcher.group("to")).append("}");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (lyricsMap.size() > 1) {
            lyricsMap.sort(Comparator.comparingDouble(l -> l.time));
            lyricsMap.add(new LyricsLine(Float.MAX_VALUE, ""));
            lyrics = lyricsMap.toArray(lyrics);
            nextLyricsLine = lyrics[0];

            tvLyricsLines = new TextView[lyrics.length];
            lvLyrics.setAdapter(new LyricsAdapter(this,
                    lyricsMap.stream().map(ll -> purifyLyrics(ll.lyrics)).collect(Collectors.toList())));

            lvLyrics.scrollTo(0, 0);
            setLyricsViewVisibility(true);
        }
        if (!"".equals(skippings.toString())) {
            jsSetSkippings = String.format(JS_SET_SKIPPINGS, skippings.substring(2));
        }
    }

    private void scrollToLyricsLine(int index) {
        if ((0 < index && index < lyrics.length - 1) && tvLyricsLines[index] != null) {
            lvLyrics.smoothScrollToPositionFromTop(index,
                    (lvLyrics.getHeight() >> 1) - (tvLyricsLines[index].getHeight() >> 1));
        }
    }

    private void seekTo(float seconds) {
        player.loadUrl("javascript:player.seekTo(" + seconds + ")");
    }

    @JavascriptInterface
    public void sendNotification() {
        sendNotification(getTitleForNotification());
    }

    private void sendNotification(String title) {
        if (isScreenOff) {
            notificationService.sendScreenNotification(this, title);
        } else {
            notificationService.sendNotification(this, title);
        }
    }

    private void setFullscreen(boolean fullscreen) {
        player.loadUrl(fullscreen ? JS_FULLSCREEN : JS_EXIT_FULLSCREEN);
    }

    private void setLyricsViewVisibility(boolean visible) {
        lvLyrics.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (floatingLyrics) {
            tvFloatingLyrics.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
    }

    @JavascriptInterface
    public void setPlayingAd(boolean playingAd) {
        isPlayingAd = playingAd;
    }

    private String stylizeLyrics(String lyrics) {
        if (lyrics.length() >= 3 && lyrics.charAt(1) == ':' && lyrics.charAt(2) == ' ') {
            switch (lyrics.charAt(0)) {
                case 'M':
                    tvFloatingLyrics.setTextColor(Color.BLUE);
                    return lyrics.substring(3);
                case 'F':
                    tvFloatingLyrics.setTextColor(Color.RED);
                    return lyrics.substring(3);
                case 'D':
                    tvFloatingLyrics.setTextColor(Color.GREEN);
                    return lyrics.substring(3);
                default:
                    return lyrics;
            }
        }
        return lyrics;
    }

    private void toggleFullscreen() {
        player.loadUrl(JS_TOGGLE_FULLSCREEN);
    }

    private void toggleState() {
        player.loadUrl(JS_TOGGLE_STATE);
    }
}