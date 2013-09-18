/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.manhdev.vernazza;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ImageAdapter extends BaseAdapter {

    private final FrameExtractor frameExtractor;
    private static final String TAG = "ImageAdapter";
    private static final int DEFAULT_FRAME_INTERVAL = 10000;
    
    private int interval;
    
    ImageAdapter(String path) throws FileNotFoundException, IOException {
        frameExtractor = new FrameExtractor(path);
        setInterval(DEFAULT_FRAME_INTERVAL);
    }
    
    public int getCount() {
        return frameExtractor.getCount();
    }

    public Object getItem(int position) {
        return (Long)frameExtractor.getFrameTimeOffset(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View view, ViewGroup parent) {
        ImageView imageView = null;
        TextView text = null;

        if (view == null) {
            RelativeLayout rLayout = new RelativeLayout(parent.getContext());

            rLayout.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.FILL_PARENT
                    ,LayoutParams.FILL_PARENT));
            view = rLayout;

            imageView = new ImageView(rLayout.getContext());
            imageView.setPadding(0,1,0,1);
            LayoutParams rlParams = new LayoutParams(LayoutParams.FILL_PARENT
                    ,LayoutParams.FILL_PARENT); 
            imageView.setLayoutParams(rlParams);

            RelativeLayout.LayoutParams tParams = new RelativeLayout.LayoutParams
                    (LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
            tParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
            tParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            text = new TextView(rLayout.getContext()); 
            text.setLayoutParams(tParams);

            rLayout.addView(imageView);
            rLayout.addView(text);
        } else {
            imageView = (ImageView)((ViewGroup)view).getChildAt(0);
            text = (TextView)((ViewGroup)view).getChildAt(1);
        }

        if (imageView == null) {
            Log.w(TAG, "null imageView");
            return view;
        }

        if (text == null) {
            Log.w(TAG, "null text");
            return text;
        }

        frameExtractor.extract(position, imageView);

        long millis = frameExtractor.getFrameTimeOffset(position) / 1000;
        long mins = TimeUnit.MILLISECONDS.toMinutes(millis);
        long secs = TimeUnit.MILLISECONDS.toSeconds(millis) - 
                TimeUnit.MINUTES.toSeconds(mins);
        String formatted = null;
        
        if (interval >= 1000) {
            // Resolution is in seconds
            formatted = String.format("%02d:%02d", mins, secs);
        } else {
            // Resolution is in milliseconds
            long msecs = millis - TimeUnit.MINUTES.toMillis(mins) - TimeUnit.SECONDS.toMillis(secs);
            formatted = String.format("%02d:%02d.%03d", mins, secs, msecs);
        }
        text.setText(formatted); 

        return view;
    }

    /**
     * Set interval between frames in list.
     * @param millis        Interval in milliseconds.
     */
    public void setInterval(int millis) {
        interval = millis;
        frameExtractor.setInterval(millis * 1000);
    }

    public int getInterval() {
        return interval;
    }
}
