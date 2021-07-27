package com.mister_chan.ytmusic;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.*;
import android.webkit.*;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

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
        public void onPause() {
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            if (visibility != View.GONE) {
                super.onWindowVisibilityChanged(View.VISIBLE);
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
    private boolean isPlaying = false, shouldSkipBeginning = false;
    private float beginningDuration = 0, endingDuration = 0;
    private LinearLayout ll;
    private Button bPlayPause;
    private final Skipping[] skippingBeginnings = new Skipping[] {
            new Skipping("l2nRYRiEY6Y", 4),
            new Skipping("wrczjnLqfZk", 28)
    };
    private final Skipping[] skippingEndings = new Skipping[] {
            new Skipping("49tpIMDy9BE", 291)
    };
    private String nowPlaying = "";
    private String[] lyrics;
    private TextView tvLyrics, tvTitle;
    private WebView player, webView;
    private WindowManager windowManager;

    private boolean isNumeric(String s) {
        return Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s).find();
    }

    private String isVideo(String url) {
        Matcher m = Pattern.compile("(?<=watch\\?v=)[-0-9A-Za-z]+").matcher(url);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private void initialWebView(WebView wv) {
        ll.addView(wv);
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

    public void nextVideo() {
        player.evaluateJavascript(PLAYER + ".nextVideo()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
            }
        });
    }

    @SuppressLint({"RemoteViewLayout", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bPlayPause = findViewById(R.id.b_play_pause);
        ll = findViewById(R.id.ll);
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
        ll.setOnClickListener(new View.OnClickListener() {
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
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                view.getSettings().setBlockNetworkImage(true);
                super.onPageStarted(view, url, favicon);
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
                    player.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    shouldSkipBeginning = true;
                } else if (!v.equals(nowPlaying)) {
                    readLyrics(v);
                    nowPlaying = v;
                    isPlaying = true;
                }
                title = title.replace(" - YouTube", "");
                tvTitle.setText(title);
                sendNotification(title);
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
        };
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
        bPlayPause.setText(data == 1 ? "⏸" : "⏵");
        switch (data) {
            case 0: // ended
                isPlaying = false;
                break;
        }
    }

    public void pauseVideo() {
        player.evaluateJavascript(PLAYER + ".pauseVideo()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
            }
        });
    }

    public void playVideo() {
        player.evaluateJavascript(PLAYER + ".playVideo()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
            }
        });
    }

    public void previousVideo() {
        player.evaluateJavascript(PLAYER + ".previousVideo()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
            }
        });
    }

    private void readLyrics(String v) {
        lyrics = new String[0x20000];
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File("/sdcard/YTMusic/" + v + ".lrc")
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