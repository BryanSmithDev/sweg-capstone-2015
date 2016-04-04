package edu.uvawise.iris;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

/**
 * The settings activity. This holds a preference view fragment. Most logic is located there.
 * This is here for consistency across Android versions.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Load our settings layout. This contains the fragment.
        setContentView(R.layout.activity_settings);

        // Get a support ActionBar corresponding to this toolbar
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
        setSupportActionBar(mToolbar);
        ActionBar ab = getSupportActionBar();


        // Enable the Up button
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
    }

}
