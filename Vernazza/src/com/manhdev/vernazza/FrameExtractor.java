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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

/**
 * This helper class extracts frames and binds those with the provided ImageView.
 *
 * <p>It requires the INTERNET permission, which should be added to your application's manifest
 * file.</p>
 *
 * A local cache of extracted frames is maintained internally to improve performance.
 */
public class FrameExtractor {

    private static final String LOG_TAG = "FrameExtractor";
    public static final int DEFAULT_COUNT = 16;
    private static final long DEFAULT_FRAME_INTERVAL = 10*1000*1000; //10 seconds in usec units

    private long duration;
    MediaMetadataRetriever mmr;
    FileInputStream input;
    private long frameCadence = DEFAULT_FRAME_INTERVAL;

    FrameExtractor(String path) throws FileNotFoundException, IOException {
        mmr = new MediaMetadataRetriever();
        input = new FileInputStream(path);
        mmr.setDataSource(input.getFD());

        try {
            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = Integer.parseInt(durationStr);
        } catch (NumberFormatException e) {
            duration = 0;
        }
        Log.i(LOG_TAG, "duration " + Long.toString(duration) + " count " + Integer.toString(getCount()));
    }

    public int getCount() {
        return (int) (duration / (frameCadence/1000));
    }
    
    /**
     * Returns time offset of specified frame in usecs.
     * @param index     Index of frame.
     * @return          Time offset in usecs.
     */
    public long getFrameTimeOffset(int index) {
        return index * frameCadence;
    }

    /**
     * Set interval between key frames in list
     * @param usecs     Interval in usecs
     */
    public void setInterval(long usecs) {
        frameCadence = usecs;
    }
    
    /**
     * Returns fixed time interval between frames in usecs.
     * @return          Time interval in usecs.
     */
    public long getInterval() {
        return frameCadence;
    }

    /**
     * Extract the specified frame and binds it to the provided ImageView. The
     * binding is immediate if the image is found in the cache and will be done asynchronously
     * otherwise. A null bitmap will be associated to the ImageView if an error occurs.
     *
     * @param url The URL of the source video to extract frames from.
     * @param imageView The ImageView to bind the extracted frame to.
     */
    public void extract(int index, ImageView imageView) {
        resetPurgeTimer();
        Bitmap bitmap = getBitmapFromCache(index);

        if (bitmap == null) {
            forceExtract(index, imageView);
        } else {
            cancelPotentialDownload(index, imageView);
            imageView.setImageBitmap(bitmap);
        }
    }

    /*
     * Same as extract but the image is always extracted and the cache is not used.
     * Kept private at the moment as its interest is not clear.
       private void forceDownload(int index, ImageView view) {
          forceDownload(url, view, null);
       }
     */

    /**
     * Same as extract but the image is always extracted and the cache is not used.
     * Kept private at the moment as its interest is not clear.
     */
    private void forceExtract(int index, ImageView imageView) {
        if (cancelPotentialDownload(index, imageView)) {
            FrameExtractorTask task;
            task = new FrameExtractorTask(imageView);
            PendingDrawable downloadedDrawable = new PendingDrawable(task);
            imageView.setImageDrawable(downloadedDrawable);
            imageView.setMinimumHeight(156);
            task.execute(index);
        }
    }

    /**
     * Returns true if the current extraction has been canceled or if there was no extraction in
     * progress on this image view.
     * Returns false if the extraction in progress deals with the same url. The extraction is not
     * stopped in that case.
     */
    private static boolean cancelPotentialDownload(int index, ImageView imageView) {
        FrameExtractorTask frameExtractorTask = getFrameExtractorTask(imageView);

        if (frameExtractorTask != null) {
            if (frameExtractorTask.index != index) {
                frameExtractorTask.cancel(true);
            } else {
                // The same URL is already being extracted.
                return false;
            }
        }
        return true;
    }

    /**
     * @param imageView Any imageView
     * @return Retrieve the currently active extraction task (if any) associated with this imageView.
     * null if there is no such task.
     */
    private static FrameExtractorTask getFrameExtractorTask(ImageView imageView) {
        if (imageView != null) {
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof PendingDrawable) {
                PendingDrawable downloadedDrawable = (PendingDrawable)drawable;
                return downloadedDrawable.getFrameExtractorTask();
            }
        }
        return null;
    }


    Bitmap extractFrame(int index) {
        try {
            long timeOffset = 0;
            timeOffset = getFrameTimeOffset(index);
            Log.i(LOG_TAG, "getFrameAtTime timeOffset " + Long.toString(timeOffset));
            Bitmap b = mmr.getFrameAtTime(timeOffset, MediaMetadataRetriever.OPTION_CLOSEST);
            if (b != null) {
                Log.i(LOG_TAG, "scaling ");
                return Bitmap.createScaledBitmap(b, 320, 240, true);
            }
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, e.toString());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, e.toString());
                }
            }
        }
        return null;
    } 

    /**
     * The actual AsyncTask that will asynchronously extract the frame.
     */
    class FrameExtractorTask extends AsyncTask<Integer, Void, Bitmap> {
        private int index;
        private final WeakReference<ImageView> imageViewReference;

        public FrameExtractorTask(ImageView imageView) {
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        /**
         * Actual extraction method.
         */
        @Override
        protected Bitmap doInBackground(Integer... params) {
            index = params[0];
            return extractFrame(index);
        }

        /**
         * Once the frame is extracted, associates it to the imageView
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            addBitmapToCache(index, bitmap);

            if (imageViewReference != null) {
                ImageView imageView = imageViewReference.get();
                FrameExtractorTask frameExtractorTask = getFrameExtractorTask(imageView);
                // Change bitmap only if this process is still associated with it
                // Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
                if (this == frameExtractorTask) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }


    /**
     * A fake Drawable that will be attached to the imageView while the extraction is in progress.
     *
     * <p>Contains a reference to the actual extraction task, so that a extraction task can be stopped
     * if a new binding is required, and makes sure that only the last started extraction process can
     * bind its result, independently of the extraction finish order.</p>
     */
    static class PendingDrawable extends ColorDrawable {
        private final WeakReference<FrameExtractorTask> frameExtractorTaskReference;

        public PendingDrawable(FrameExtractorTask frameExtractorTask) {
            super(Color.BLACK);
            frameExtractorTaskReference =
                new WeakReference<FrameExtractorTask>(frameExtractorTask);
        }

        public FrameExtractorTask getFrameExtractorTask() {
            return frameExtractorTaskReference.get();
        }
    }

    /*
     * Cache-related fields and methods.
     * 
     * We use a hard and a soft cache. A soft reference cache is too aggressively cleared by the
     * Garbage Collector.
     */
    
    private static final int HARD_CACHE_CAPACITY = 10;
    private static final int DELAY_BEFORE_PURGE = 10 * 1000; // in milliseconds

    // Hard cache, with a fixed maximum capacity and a life duration
    private final HashMap<Integer, Bitmap> sHardBitmapCache =
        new LinkedHashMap<Integer, Bitmap>(HARD_CACHE_CAPACITY / 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(LinkedHashMap.Entry<Integer, Bitmap> eldest) {
            if (size() > HARD_CACHE_CAPACITY) {
                // Entries push-out of hard reference cache are transferred to soft reference cache
                sSoftBitmapCache.put(eldest.getKey(), new SoftReference<Bitmap>(eldest.getValue()));
                return true;
            } else
                return false;
        }
    };

    // Soft cache for bitmaps kicked out of hard cache
    private final static ConcurrentHashMap<Integer, SoftReference<Bitmap>> sSoftBitmapCache =
        new ConcurrentHashMap<Integer, SoftReference<Bitmap>>(HARD_CACHE_CAPACITY / 2);

    private final Handler purgeHandler = new Handler();

    private final Runnable purger = new Runnable() {
        public void run() {
            clearCache();
        }
    };

    /**
     * Adds this bitmap to the cache.
     * @param bitmap The newly extracted frame.
     */
    private void addBitmapToCache(int index, Bitmap bitmap) {
        if (bitmap != null) {
            synchronized (sHardBitmapCache) {
                sHardBitmapCache.put(index, bitmap);
            }
        }
    }

    /**
     * @param url The URL of the image that will be retrieved from the cache.
     * @return The cached bitmap or null if it was not found.
     */
    private Bitmap getBitmapFromCache(int index) {
        // First try the hard reference cache
        synchronized (sHardBitmapCache) {
            final Bitmap bitmap = sHardBitmapCache.get(index);
            if (bitmap != null) {
                // Bitmap found in hard cache
                // Move element to first position, so that it is removed last
                sHardBitmapCache.remove(index);
                sHardBitmapCache.put(index, bitmap);
                return bitmap;
            }
        }

        // Then try the soft reference cache
        SoftReference<Bitmap> bitmapReference = sSoftBitmapCache.get(index);
        if (bitmapReference != null) {
            final Bitmap bitmap = bitmapReference.get();
            if (bitmap != null) {
                // Bitmap found in soft cache
                return bitmap;
            } else {
                // Soft reference has been Garbage Collected
                sSoftBitmapCache.remove(index);
            }
        }

        return null;
    }
 
    /**
     * Clears the image cache used internally to improve performance. Note that for memory
     * efficiency reasons, the cache will automatically be cleared after a certain inactivity delay.
     */
    public void clearCache() {
        sHardBitmapCache.clear();
        sSoftBitmapCache.clear();
    }

    /**
     * Allow a new delay before the automatic cache clear is done.
     */
    private void resetPurgeTimer() {
        purgeHandler.removeCallbacks(purger);
        purgeHandler.postDelayed(purger, DELAY_BEFORE_PURGE);
    }
}
