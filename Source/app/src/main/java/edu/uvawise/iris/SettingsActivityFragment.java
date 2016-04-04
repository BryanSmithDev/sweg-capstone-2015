package edu.uvawise.iris;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;

import edu.uvawise.iris.sync.SyncUtils;
import edu.uvawise.iris.utils.PrefUtils;

/**
 * A fragment that lists all user customizable settings and lets the user change them easily.
 */
public class SettingsActivityFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = SettingsActivityFragment.class.getSimpleName();
    Context context; //Store the application context


    public SettingsActivityFragment() {
    }


    /**
     * Called when the fragment is created. We will setup most of our objects here.
     *
     * @param savedInstanceState The saved state of the fragment if available.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getActivity().getApplicationContext(); //Store the application context

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(PrefUtils.PREFS_NAME);
        prefMgr.setSharedPreferencesMode(Context.MODE_PRIVATE);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        initSummary(getPreferenceScreen());

        //When our add account preference is clicked, we need to go back to the Main Activity and add an account.
        findPreference("addAccount").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent logoutIntent = new Intent(context, MainActivity.class);
                logoutIntent.putExtra(MainActivity.METHOD_TO_CALL, MainActivity.ADDACCOUNT);
                logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(logoutIntent);
                return true;
            }
        });

        //When our logout preference is clicked, we need to go back to the Main Activity and logout.
        findPreference("logout").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent logoutIntent = new Intent(context, MainActivity.class);
                logoutIntent.putExtra(MainActivity.METHOD_TO_CALL, MainActivity.LOGOUT);
                logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(logoutIntent);
                return true;
            }
        });
    }


    /**
     * Make sure to re-register the shared preferences change listener when resuming.
     */
    @Override
    public void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }


    /**
     * Make sure to un-register the shared preference change listener on pause.
     * It will cause a leak otherwise.
     */
    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }


    /**
     * Called when shared preferences are changed.
     *
     * @param sharedPreferences The shared preferences group that where changed.
     * @param key               The key of the preference changed
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        updatePrefSummary(findPreference(key));
        if (key.equals(getString(R.string.pref_sync_freq_key))) {
            Log.d(TAG, "Sync Frequency Changed");
            SyncUtils.updateSyncFrequency(getActivity().getApplicationContext(),
                    SyncUtils.getSyncFrequency(getActivity().getApplicationContext()));
        } else if (key.equals(getString(R.string.service_pref_sync_freq_key))) {
            Log.d(TAG, "Service Sync Frequency Changed");
            SyncUtils.updateSyncFrequency(getActivity().getApplicationContext(),
                    SyncUtils.getServiceSyncFrequency(getActivity().getApplicationContext()));
        }
    }


    /**
     * Setup the initial summary of each preference based on saved preference value.
     *
     * @param p The preference to set the summary of.
     */
    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }


    /**
     * Sets the summary of the preference based on the selected value. Customized per preference
     * type
     *
     * @param p The preference to set the summary of.
     */
    private void updatePrefSummary(Preference p) {
        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            p.setSummary(listPref.getEntry());
        }
        if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            if (p.getTitle().toString().contains("assword")) {
                p.setSummary("******");
            } else {
                p.setSummary(editTextPref.getText());
            }
        }
        if (p instanceof MultiSelectListPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            p.setSummary(editTextPref.getText());
        }
    }

}
