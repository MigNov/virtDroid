package com.mig.virtdroid;

/**
 * VirtDroid Preferences Dialog
 *
 * Author: Michal Novotny <mignov@gmail.com>
 */

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PrefsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
    }
}