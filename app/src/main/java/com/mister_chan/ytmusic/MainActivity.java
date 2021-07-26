package com.mister_chan.ytmusic;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationManagerCompat;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import android.widget.QuickContactBadge;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.io.*;
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
            if (visibility != View.GONE)
                super.onWindowVisibilityChanged(View.VISIBLE);
        }
    }

    private static String HOME = "https://m.youtube.com";
    private boolean isPlaying = false;
    private String[] lyrics;
    private TextView tvLyrics;
    private WebView currentPlaying, webView;
    private WebView[] wvs = new WebView[2];
    private WindowManager windowManager;

    private boolean isNumeric(String s) {
        return Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s).find();
    }

    public void nextVideo() {
        currentPlaying.evaluateJavascript("document.getElementById(\"movie_player\").nextVideo()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {

            }
        });
    }

    @SuppressLint("RemoteViewLayout")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ConstraintLayout cl = findViewById(R.id.cl);


        wvs[0] = new MediaWebView(this);
        wvs[1] = new MediaWebView(this);
        wvs[0].setTag(wvs[1]);
        wvs[1].setTag(wvs[0]);
        currentPlaying = wvs[1];
        for (WebView wv : wvs) {
            cl.addView(wv);
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
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onLoadResource(WebView view, String url) {
                    Matcher m = Pattern.compile("(?<=watch\\?v=)\\w+").matcher(url);
                    if (m.find()) {
                        readLyrics(m.group());
                        currentPlaying = view;
                        isPlaying = true;
                        WebView another = (WebView) view.getTag();
                        another.loadUrl(HOME);
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
            wv.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onReceivedTitle(WebView view, String title) {
                    if (view.equals(currentPlaying)) {
                        title = title.substring(0, title.length() - 10);
                        setTitle(title);
                        sendNotification(title);
                    }
                    super.onReceivedTitle(view, title);
                }
            });
        }
        wvs[1].setVisibility(View.GONE);
        webView = wvs[0];
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
        tvLyrics.setTextColor(0xffff0000);
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
                        currentPlaying.evaluateJavascript("document.getElementById(\"movie_player\").getCurrentTime()", new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                if (isNumeric(value)) {
                                    float f = Float.parseFloat(value);
                                    if (f > 0) {
                                        int centisec = (int) (f * 100) * 20 / 1000 * 1000 / 20;
                                        String lrc = lyrics[centisec];
                                        if (lrc != null)
                                            tvLyrics.setText(lrc);
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
            if (webView.canGoBack()) {
                if (webView.equals(currentPlaying)) {
                    WebView another = (WebView) webView.getTag();
                    WebBackForwardList wbfl = webView.copyBackForwardList();
                    another.clearHistory();
                    another.loadUrl(wbfl.getItemAtIndex(wbfl.getCurrentIndex() - 1).getUrl());
                    webView.setVisibility(View.GONE);
                    another.setVisibility(View.VISIBLE);
                    webView = another;
                } else
                    webView.goBack();
            } else {
                moveTaskToBack(false);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void pauseVideo() {
        currentPlaying.evaluateJavascript("document.getElementById(\"movie_player\").pauseVideo()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {

            }
        });
    }

    public void playVideo() {
        currentPlaying.evaluateJavascript("document.getElementById(\"movie_player\").playVideo()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {

            }
        });
    }

    public void previousVideo() {
        currentPlaying.evaluateJavascript("document.getElementById(\"movie_player\").previousVideo()", new ValueCallback<String>() {
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