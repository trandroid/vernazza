package com.manhdev.vernazza;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class UrlDialog extends Activity {

    public static final String DEFAULT_URL = "DEFAULT_URL";

    public static final String RESULT_URL = "RESULT_URL";

    private String url;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.url_dialog);

        url = getIntent().getStringExtra(DEFAULT_URL);

        ((EditText)findViewById(R.id.editUrl)).setText(url);
        
        ((Button)findViewById(R.id.buttonUrlOk))
        .setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                url = ((EditText)findViewById(R.id.editUrl)).getText().toString();
                getIntent().putExtra(RESULT_URL, url);
                setResult(RESULT_OK, getIntent());
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.url_dialog, menu);
        return true;
    }
}
