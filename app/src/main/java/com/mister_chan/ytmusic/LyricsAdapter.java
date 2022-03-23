package com.mister_chan.ytmusic;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class LyricsAdapter extends ArrayAdapter<String> {
    private final int pxOf512Dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 512, getContext().getResources().getDisplayMetrics());
    private final MainActivity mainActivity;
    private final List<String> lyricsLines;

    public LyricsAdapter(@NonNull MainActivity mainActivity, @NonNull List<String> lyricsLines) {
        super(mainActivity, R.layout.lyrics_line, lyricsLines);
        this.mainActivity = mainActivity;
        this.lyricsLines = lyricsLines;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (0 < position && position < lyricsLines.size() - 1) {
            LinearLayout llLyricsLine = (LinearLayout) LayoutInflater.from(mainActivity).inflate(R.layout.lyrics_line, parent, false);
            TextView tvLyricsLine = llLyricsLine.findViewById(R.id.tv_lyrics);
            mainActivity.tvLyricsLines[position] = tvLyricsLine;
            tvLyricsLine.setText(lyricsLines.get(position));
            return llLyricsLine;
        } else {
            View view = new View(mainActivity);
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pxOf512Dp));
            return view;
        }
    }
}
