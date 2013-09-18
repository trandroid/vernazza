package com.manhdev.vernazza;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import android.app.ListActivity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.VideoView;

public class VideoPlayer extends ListActivity {

    public static final String FILE_PATH = "FILE_PATH";
    private static final String TAG = "VideoPlayer";
    private enum ControlMode { HIDE, SHOW, FRAMES }
    private enum ResizeState { DEFAULT, FILL_STRETCH }

	private VideoView videoView;
	private ImageButton playButton;
	private ImageButton pauseButton;
	private ImageButton resetButton;
	private ImageButton resizeButton;
	private String current;
	private ControlMode controlMode = ControlMode.HIDE;
	private Map<ControlMode, ArrayList<View> > visibleControls;
	private boolean isPrepared;
	private BlockingQueue<Long> seekQueue;
	private SeekTask seekTask;
	private ResizeState resizeState;
	private VideoPositionTask videoPositionTask;

    private static final int FRAME_INTERVAL_COUNT = 8;
    private static final FrameInterval[] frameIntervals = new FrameInterval[FRAME_INTERVAL_COUNT]; 

    class FrameInterval {
        int interval;
        String title;
        FrameInterval(int i, String t) {
            interval = i;
            title = t;
        }
    }

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video_player);

        resizeState = ResizeState.DEFAULT;

        frameIntervals[0] = new FrameInterval(200, "200ms");
		frameIntervals[1] = new FrameInterval(500, "500ms");
        frameIntervals[2] = new FrameInterval(1000, "1s");
        frameIntervals[3] = new FrameInterval(2000, "2s");
        frameIntervals[4] = new FrameInterval(5000, "5s");
        frameIntervals[5] = new FrameInterval(10000, "10s");
        frameIntervals[6] = new FrameInterval(30000, "30s");
        frameIntervals[7] = new FrameInterval(60000, "1m");

        try {
            setListAdapter(new ImageAdapter(getIntent().getStringExtra(FILE_PATH)));
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        
        registerForContextMenu(getListView());

        seekQueue = new ArrayBlockingQueue<Long>(1);
        
		videoView = (VideoView) findViewById(R.id.surface_view);

		playButton = (ImageButton) findViewById(R.id.play);
		pauseButton = (ImageButton) findViewById(R.id.pause);
		resetButton = (ImageButton) findViewById(R.id.reset);
        resizeButton = (ImageButton) findViewById(R.id.resize);

		playButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
                videoView.start();
                videoView.requestFocus();
			}
		});
		pauseButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				if (videoView != null) {
					videoView.pause();
				}
			}
		});
		resetButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				if (videoView != null) {
					videoView.seekTo(0);
				}
			}
		});
		resizeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				if (videoView != null) {
				    RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) videoView.getLayoutParams();
				    int alignParent = 0;

				    if (resizeState == ResizeState.DEFAULT) {
				        resizeState = ResizeState.FILL_STRETCH;
                        alignParent = RelativeLayout.TRUE;
                        getWindow().setFlags(
                                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                WindowManager.LayoutParams.FLAG_FULLSCREEN);
				    } else if (resizeState == ResizeState.FILL_STRETCH) {
                        resizeState = ResizeState.DEFAULT;
                        alignParent = 0;
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				    }
				    // Update how view is aligned in parent container.
				    // This will adjust 'stretch' as necessary.
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, alignParent);
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, alignParent);
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, alignParent);
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, alignParent);

                    // Request for view's layout to be processed.
                    videoView.requestLayout();
				}
			}
		});

		visibleControls = new EnumMap<ControlMode, ArrayList<View> >(ControlMode.class);
		visibleControls.put(ControlMode.HIDE, null);
        ArrayList<View> playerControls = new ArrayList<View>();
        playerControls.add(playButton);
        playerControls.add(pauseButton);
        playerControls.add(resetButton);
        playerControls.add(resizeButton);
        playerControls.add((TextView)findViewById(R.id.textVideoHeader));
        visibleControls.put(ControlMode.SHOW, playerControls);
        ArrayList<View> frameListControls = new ArrayList<View>();
        frameListControls.add(getListView());
        visibleControls.put(ControlMode.FRAMES, frameListControls);
		
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isPrepared = true;
                playVideo();
            }
        });
        
        runOnUiThread(new Runnable(){
            public void run() {
                if (!"sdk".equals( Build.PRODUCT )) {
                    String path = getIntent().getStringExtra(FILE_PATH);
                    // If the path has not changed, just start the media player
                    if (path.equals(current) && videoView != null) {
                        videoView.start();
                        videoView.requestFocus();
                    } else {
                        prepareVideo();
                    }
                }
            }
        });
	}

    @SuppressWarnings("unchecked")
    @Override
    public void onResume() {
        super.onResume();
        isPrepared = false;
        seekQueue.clear();
        if (seekTask != null) {
            seekTask.cancel(true);
        }
        seekTask = new SeekTask();
        seekTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, seekQueue, null, null);
        if (videoPositionTask != null) {
            videoPositionTask.cancel(true);
        }
        videoPositionTask =  new VideoPositionTask();
        videoPositionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
        updateControls();
    }

    private void prepareVideo() {
        try {
            String path = getIntent().getStringExtra(FILE_PATH);
            Log.v(TAG, "path: " + path);
            
            current = path;

            Uri uri = Uri.parse(path);
            if (uri.isAbsolute() && uri.isHierarchical()) {
                videoView.setVideoURI(uri);
            } else {
                videoView.setVideoPath(getDataSource(path));
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
    
	private void playVideo() {
		videoView.start();
		videoView.requestFocus();
	}

    @Override
    public boolean onTouchEvent (MotionEvent ev){
        final ControlMode stateMachine[] = {
            ControlMode.SHOW,
            ControlMode.FRAMES,
            ControlMode.HIDE
        };
        if (ev.getAction() == MotionEvent.ACTION_DOWN){
            controlMode = stateMachine[controlMode.ordinal()];
            updateControls();
        }
        return true;
    }
    
	private String getDataSource(String path) throws IOException {
		if (!URLUtil.isNetworkUrl(path)) {
			return path;
		} else {
			URL url = new URL(path);
			URLConnection cn = url.openConnection();
			cn.connect();
			InputStream stream = cn.getInputStream();
			if (stream == null)
				throw new RuntimeException("stream is null");
			File temp = File.createTempFile("mediaplayertmp", "dat");
			temp.deleteOnExit();
			String tempPath = temp.getAbsolutePath();
			FileOutputStream out = new FileOutputStream(temp);
			byte buf[] = new byte[128];
			do {
				int numread = stream.read(buf);
				if (numread <= 0)
					break;
				out.write(buf, 0, numread);
			} while (true);
			try {
				stream.close();
			} catch (IOException ex) {
				Log.e(TAG, "error: " + ex.getMessage(), ex);
			}
			return tempPath;
		}
	}

	private void updateControls() {
	    // Hide all
	    for (ArrayList<View> controls : visibleControls.values()) {
	        if (controls != null) {
    	        for (View v : controls) {
    	            v.setVisibility(View.GONE);
    	        }
	        }
	    }
        // Show particular
	    ArrayList<View> controls = visibleControls.get(controlMode);
	    if (controls != null) {
            for (View v : controls) {
                v.setVisibility(View.VISIBLE);
            }
	    }
	}

	/**
	 * @brief
	 * Handles clicking of key frame in image list.
	 * 
	 * When a key frame is clicked then its time position in the video is seeked to.
	 * Seek only occurs after initial preparation of video. 
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
	    super.onListItemClick(l, v, position, id);
	    long timeOffset = (Long) l.getItemAtPosition(position);
        Log.i(TAG, "isPrepared " + Boolean.toString(isPrepared) + " timeOffset " + Long.toString(timeOffset));
	    if (timeOffset != -1) {
            if (isPrepared) {
	            if (!seekQueue.isEmpty()) {
	                seekQueue.clear();
	            }
                if (seekQueue.offer(timeOffset)) {
                    Log.i(TAG, "queued");
                } else {
                    Log.i(TAG, "dropped");
                }
            }
	    }
	}

	/**
	 * @brief
	 * Task to ensure VideoView only processe one seek command at a time. 
	 */
	private class SeekTask extends AsyncTask<BlockingQueue<Long>, Void, Void> {
        @Override
        protected Void doInBackground(BlockingQueue<Long>... params) {
            BlockingQueue<Long> seekQueue = params[0];
            Log.i(TAG, "q count " + Integer.toString(seekQueue.size()));
            while (true) {
                try {
                    long seekUsecs = seekQueue.take();
                    Log.i(TAG, "seek usecs " + Long.toString(seekUsecs));
                    videoView.seekTo((int) (seekUsecs / 1000));
                } catch (InterruptedException ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break;
                }
            }
            return null;
        }
	}

    /**
     * @brief
     * Task to update on-screen text with time of current video position. 
     */
    private class VideoPositionTask extends AsyncTask<Void, String, Void> {
        private final TextView textVideoHeader = (TextView)findViewById(R.id.textVideoHeader);

        @Override
        protected Void doInBackground(Void... params) {
            try {
                while (true) {
                    if (controlMode == ControlMode.SHOW) {
                        publishProgress(getVideoPositionText());
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, ex.getMessage());
            }
            return null;
        }
        
        @Override
        protected void onProgressUpdate(String... params) {
            String videoPositionText = params[0];
            if (controlMode == ControlMode.SHOW && videoPositionText != null) {
                textVideoHeader.setText(videoPositionText);
            }
        }
    }

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	    if (v.getId() == getListView().getId()) {
	        menu.setHeaderTitle(R.string.frame_interval);
	        for (int i = 0; i < frameIntervals.length; ++i) {
	            menu.add(Menu.NONE, i, i, frameIntervals[i].title);
	        }
	    }
	}

    @Override
	public boolean onMenuItemSelected(int featureId, MenuItem menuItem) {
	    int index = menuItem.getItemId();

        // Update underlying interval and notify observers of resulting change to dataset.
        ImageAdapter imageAdapter = (ImageAdapter)getListView().getAdapter();
        imageAdapter.setInterval(frameIntervals[index].interval);
        imageAdapter.notifyDataSetChanged();

	    return true;
	}

	protected String getVideoPositionText() {
        int millis = videoView.getCurrentPosition();
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
	}
}
