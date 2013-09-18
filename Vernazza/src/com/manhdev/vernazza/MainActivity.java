package com.manhdev.vernazza;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

    protected static final int REQUEST_PLAY = 1;
    protected static final int REQUEST_ANALYZE = 2;
    
    public static final String PLAYER_LAST_PATH = "PLAYER_LAST_PATH";
    private static final String PLAYER_DEFAULT_PATH =
            Environment.getExternalStorageDirectory().getPath();
    private String lastPath = PLAYER_DEFAULT_PATH;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        restoreState(savedInstanceState);

        ((Button) findViewById(R.id.buttonSelectToPlay))
        .setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                launchFileDialog();
            }
        });

        launchFileDialog();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PLAYER_LAST_PATH, lastPath);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        restoreState(savedInstanceState);
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            lastPath = savedInstanceState.getString(PLAYER_LAST_PATH);
        }
        if (lastPath == null || !(new File(lastPath).exists())) {
            lastPath = PLAYER_DEFAULT_PATH;
        }
    }

    protected void launchFileDialog() {
        Intent intent = new Intent(getBaseContext(), FileDialog.class);
        String favPaths[] = null;
        if (lastPath.equals(PLAYER_DEFAULT_PATH)) {
            favPaths = new String[1];
            favPaths[0] = lastPath;
        } else {
            favPaths = new String[2];
            favPaths[0] = PLAYER_DEFAULT_PATH;
            favPaths[1] = lastPath;
        }
        intent.putExtra(FileDialog.FAV_PATHS, favPaths);
        startActivityForResult(intent, REQUEST_PLAY);  
    }
    
    @Override
    public synchronized void onActivityResult(final int requestCode, int resultCode,
            final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_PLAY) {
                Intent intent = null;
                File f = new File(data.getStringExtra(FileDialog.RESULT_PATH));
                if (f.exists()) {
                    lastPath = f.getParent();
                }
                intent = new Intent(getBaseContext(), VideoPlayer.class);
                intent.putExtra(VideoPlayer.FILE_PATH, data.getStringExtra(FileDialog.RESULT_PATH));
                startActivity(intent);
            }
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}
