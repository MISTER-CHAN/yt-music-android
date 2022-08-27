package com.mister_chan.ytmusic;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MediaWebView extends WebView {

    boolean b = true;

    public MediaWebView(@NonNull Context context) {
        super(context);
    }

    public MediaWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MediaWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onPause() {
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (b && visibility != View.GONE) {
            super.onWindowVisibilityChanged(visibility);
            b = false;
        }
    }
}
